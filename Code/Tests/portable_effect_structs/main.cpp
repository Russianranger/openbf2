// [CHOSEN, not recovered] Compile production effect source and real HLSL bytecode, then record
// its ordinary ID3DXEffectStateManager uploads. The raster companion exercises a real D3D9 device.
#ifndef BF2_EFFECT_SOURCE
#define BF2_EFFECT_SOURCE "../../BF2/PortShim/D3dxEffectPortable.cpp"
#endif
#include BF2_EFFECT_SOURCE
#include <array>

namespace {
struct RecordingManager
{
    void** table;
    std::map<unsigned, std::array<float, 4>> vertex, pixel;
};
unsigned long retain(void*) { return 1; }
long vertexUpload(void* self, unsigned first, const float* values, unsigned count)
{
    auto& out = static_cast<RecordingManager*>(self)->vertex;
    for (unsigned n = 0; n < count; ++n) std::memcpy(out[first + n].data(), values + n * 4, 16);
    return 0;
}
long pixelUpload(void* self, unsigned first, const float* values, unsigned count)
{
    auto& out = static_cast<RecordingManager*>(self)->pixel;
    for (unsigned n = 0; n < count; ++n) std::memcpy(out[first + n].data(), values + n * 4, 16);
    return 0;
}
unsigned checks = 0, failures = 0;
void check(bool condition, const char* message)
{
    ++checks;
    if (!condition) { ++failures; std::fprintf(stderr, "FAIL: %s\n", message); }
}
template<class R, class... A> R call(void* object, unsigned offset, A... args)
{
    return reinterpret_cast<R (*)(void*, A...)>((*static_cast<void***>(object))[offset / 4])(object, args...);
}
bool matches(const std::array<float, 4>& a, float x, float y, float z, float w)
{ return a[0] == x && a[1] == y && a[2] == z && a[3] == w; }
}

int main()
{
    // Same packed sizes and HLSL shapes consumed by Ra's light record and BundledMesh UserData.
    const char source[] = R"FX(
struct Light { float3 pos; float3 dir; float4 color; float4 specularColor; float attenuation; };
Light Lights[1];
Light Pair[2];
struct { float4x4 uvMatrix[7] : UVMatrix; } UserData;
struct Nested { Light key; row_major float2x3 transform; float2 weights[2]; };
Nested Extra;
float4x4 ExistingMatrix;
float4 vertexMain(float4 p : POSITION) : POSITION {
    float4 q = float4(Lights[0].pos + Lights[0].dir, Lights[0].attenuation);
    q += Lights[0].color + Lights[0].specularColor;
    q += float4(Pair[1].dir, Pair[1].attenuation) + Pair[0].color;
    q += mul(p, UserData.uvMatrix[0]) + mul(p, UserData.uvMatrix[6]);
    q += Extra.key.color + float4(Extra.transform[0], Extra.weights[1].y);
    return q + mul(p, ExistingMatrix);
}
float4 pixelMain() : COLOR { return Lights[0].color + Lights[0].specularColor; }
technique test { pass p { } }
)FX";
    void* effect = nullptr;
    check(D3DXCreateEffect(nullptr, source, sizeof(source) - 1, nullptr, nullptr, 0,
                          nullptr, &effect, nullptr) == 0 && effect, "create production effect");
    if (!effect) return 1;
    void* vtable[21] = {};
    vtable[1] = vtable[2] = reinterpret_cast<void*>(&retain);
    vtable[14] = reinterpret_cast<void*>(&vertexUpload);
    vtable[18] = reinterpret_cast<void*>(&pixelUpload);
    RecordingManager manager{vtable};
    call<long>(effect, 0x11c, static_cast<void*>(&manager));

    auto parameter = [&](const char* name) { return call<void*>(effect, 0x24, static_cast<void*>(nullptr), name); };
    auto descriptor = [&](void* p, unsigned bytes, unsigned members, unsigned elements) {
        ParameterDesc desc = {};
        check(p && call<long>(effect, 0x10, p, &desc) == 0, "parameter description exists");
        check(desc.cls == PC_STRUCT && desc.bytes == bytes && desc.structMembers == members
              && desc.elements == elements, "structured parameter reflection matches packed payload");
    };
    void* lights = parameter("Lights");
    void* uv = parameter("UserData");
    descriptor(lights, 60, 5, 1);
    descriptor(parameter("Pair"), 120, 5, 2);
    descriptor(uv, 448, 1, 0);
    descriptor(parameter("Extra"), 100, 3, 0);
    float light[17]; for (unsigned n = 0; n < 17; ++n) light[n] = float(n + 1);
    check(call<long>(effect, 0x50, lights, static_cast<const void*>(light), sizeof(light)) == 0,
          "native 68-byte light record accepts shader's 60-byte prefix");
    float pair[30]; for (unsigned n = 0; n < 30; ++n) pair[n] = float(n + 21);
    call<long>(effect, 0x50, parameter("Pair"), static_cast<const void*>(pair), sizeof(pair));
    float matrices[112]; for (unsigned n = 0; n < 112; ++n) matrices[n] = float(n + 101);
    check(uv && call<long>(effect, 0x50, uv, static_cast<const void*>(matrices), sizeof(matrices)) == 0,
          "anonymous UV matrix block accepts all seven matrices");
    float extra[25]; for (unsigned n = 0; n < 25; ++n) extra[n] = float(n + 301);
    call<long>(effect, 0x50, parameter("Extra"), static_cast<const void*>(extra), sizeof(extra));
    call<long>(effect, 0x98, parameter("ExistingMatrix"), matrices);

    Effect* fx = self(effect);
    for (unsigned stage = 0; stage < 2; ++stage)
    {
        Shader shader;
        shader.pixel = stage == 1;
        std::vector<unsigned> bytecode;
        string error;
        const bool compiled = compileHlsl(fx->body, stage ? "pixelMain" : "vertexMain",
                                          stage ? "ps_3_0" : "vs_3_0", &bytecode, &error);
        check(compiled, "real HLSL compiler accepts structured shader");
        if (!compiled) { std::fprintf(stderr, "%s\n", error.c_str()); continue; }
        shader.constants = readConstants(bytecode.data(), bytecode.size());
        fx->uploadOne(&shader);
        const auto& registers = stage ? manager.pixel : manager.vertex;
        auto reg = [&](unsigned n) {
            const auto it = registers.find(n);
            return it == registers.end() ? std::array<float, 4>{} : it->second;
        };
        bool sawLights = false, sawUv = stage != 0;
        for (const auto& constant : shader.constants)
        {
            const unsigned r = constant.reg;
            if (constant.name == "Lights")
            {
                sawLights = true;
                check(matches(reg(r), 1, 2, 3, 0), "light position pads one register");
                check(matches(reg(r + 1), 4, 5, 6, 0), "light direction stays separate from position");
                check(matches(reg(r + 2), 7, 8, 9, 10), "sun RGB and ambient alpha reach shader");
                check(matches(reg(r + 3), 11, 12, 13, 14), "specular reaches shader");
                if (constant.count >= 5) check(matches(reg(r + 4), 15, 0, 0, 0), "attenuation reaches shader");
            }
            else if (constant.name == "UserData")
            {
                sawUv = true;
                check(constant.count == 28, "compiler reflects seven UV matrices");
                for (unsigned matrix = 0; matrix < 7; ++matrix)
                    for (unsigned column = 0; column < 4; ++column)
                    {
                        const float first = 101 + float(matrix * 16 + column);
                        check(matches(reg(r + matrix * 4 + column), first, first + 4, first + 8, first + 12),
                              "UV matrix row-major payload becomes column-major registers");
                    }
            }
            else if (constant.name == "Pair")
            {
                check(matches(reg(r + 5), 36, 37, 38, 0), "second light starts after first complete struct");
                check(matches(reg(r + 6), 39, 40, 41, 0), "array member direction is intact");
            }
            else if (constant.name == "Extra")
            {
                check(matches(reg(r + 2), 307, 308, 309, 310), "nested light keeps packed offsets");
                check(matches(reg(r + 5), 316, 317, 318, 0), "row-major matrix first row is preserved");
                check(matches(reg(r + 6), 319, 320, 321, 0), "row-major matrix second row is preserved");
                check(matches(reg(r + 8), 324, 325, 0, 0), "nested vector array pads each element");
            }
            else if (constant.name == "ExistingMatrix")
                check(matches(reg(r), 101, 105, 109, 113), "ordinary matrix upload remains unchanged");
        }
        check(sawLights && sawUv, "expected structured constants are reflected and uploaded");

        void* element = call<void*>(effect, 0x2c, lights, 0u);
        void* color = call<void*>(effect, 0x24, element, "color");
        const float replacement[4] = {0.75f, 0.5f, 0.25f, 1};
        check(color && call<long>(effect, 0x88, color, replacement) == 0,
              "array/member handles permit ordinary vector writes");
        fx->uploadOne(&shader);
        for (const auto& constant : shader.constants)
            if (constant.name == "Lights")
                check(matches(reg(constant.reg + 2), .75f, .5f, .25f, 1),
                      "member write updates parent storage used by shader");
        call<long>(effect, 0x50, lights, static_cast<const void*>(light), sizeof(light));
    }
    call<unsigned long>(effect, 0x08);
    std::printf("Portable effect structures: %u/%u checks passed\n", checks - failures, checks);
    return failures ? 1 : 0;
}

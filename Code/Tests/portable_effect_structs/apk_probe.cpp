// [CHOSEN, not recovered] Verify the exported D3DX effect ABI in an already-built renderer.
// This file contains no renderer/effect source, compiler replacement or engine fixture bodies.
#include <dlfcn.h>
#include <cstdio>
#include <cstring>
#include <vector>

namespace {
struct ParameterDesc
{
    const char* name;
    const char* semantic;
    unsigned cls, type, rows, columns, elements, annotations, members, flags, bytes;
};
unsigned checks = 0, failures = 0;
void check(bool ok, const char* message)
{
    ++checks;
    if (!ok) { ++failures; std::fprintf(stderr, "FAIL: %s\n", message); }
}
template<class R, class... A> R call(void* object, unsigned offset, A... args)
{
    auto* table = *static_cast<void***>(object);
    return reinterpret_cast<R (*)(void*, A...)>(table[offset / 4])(object, args...);
}
void descriptor(void* effect, void* handle, unsigned cls, unsigned bytes,
                unsigned members, unsigned elements, const char* message)
{
    ParameterDesc d = {};
    const bool ok = handle && call<long>(effect, 0x10, handle, &d) == 0;
    check(ok && d.cls == cls && d.bytes == bytes && d.members == members && d.elements == elements, message);
}
void floats(void* effect, void* handle, const float* wanted, unsigned count, const char* message)
{
    // GetValue is not implemented by the portable runtime. Integer-valued float fixtures can
    // round-trip losslessly through its public GetIntArray method on each numeric member.
    std::vector<int> actual(count, -99999);
    bool ok = handle && call<long>(effect, 0x74, handle, actual.data(), count) == 0;
    for (unsigned n = 0; n < count && ok; ++n) ok = actual[n] == static_cast<int>(wanted[n]);
    check(ok, message);
}
}

int main(int argc, char** argv)
{
    if (argc != 3) { std::fprintf(stderr, "usage: apk-effect-probe /path/libmain.so /path/libRendDX9.so\n"); return 2; }
    void* host = dlopen(argv[1], RTLD_NOW | RTLD_GLOBAL);
    if (!host) { std::fprintf(stderr, "host load: %s\n", dlerror()); return 2; }
    void* renderer = dlopen(argv[2], RTLD_NOW | RTLD_LOCAL);
    if (!renderer) { std::fprintf(stderr, "renderer load: %s\n", dlerror()); return 2; }
    using CreateEffect = long (*)(void*, const void*, unsigned, const void*, void*, unsigned long,
                                 void*, void**, void**);
    auto create = reinterpret_cast<CreateEffect>(dlsym(renderer, "D3DXCreateEffect"));
    check(create != nullptr, "renderer exports D3DXCreateEffect");
    if (!create) return 1;
    Dl_info symbol = {};
    check(dladdr(reinterpret_cast<void*>(create), &symbol) != 0 && symbol.dli_fname
              && std::strstr(symbol.dli_fname, "libRendDX9.so"), "effect export resolves to renderer library");
    std::printf("D3DXCreateEffect image: %s\n", symbol.dli_fname ? symbol.dli_fname : "unknown");
    const char source[] = R"FX(
struct Light { float3 pos; float3 dir; float4 color; float4 specularColor; float attenuation; };
Light Lights[1];
Light Pair[2];
struct ExpandedLight { float3 pos; float3 dir; float4 color; float4 specularColor;
    float attenuation; float coneAngleInner; float coneAngleOuter; };
ExpandedLight Expanded[1];
struct { float4x4 uvMatrix[7] : UVMatrix; } UserData;
technique probe { pass p { } }
)FX";
    void* effect = nullptr;
    check(create(nullptr, source, unsigned(sizeof(source) - 1), nullptr, nullptr, 0, nullptr,
                 &effect, nullptr) == 0 && effect, "create effect through loaded renderer export");
    if (!effect) return 1;
    auto parameter = [&](const char* name) {
        return call<void*>(effect, 0x24, static_cast<void*>(nullptr), name);
    };
    auto member = [&](void* parent, const char* name) { return call<void*>(effect, 0x24, parent, name); };
    auto element = [&](void* parent, unsigned n) { return call<void*>(effect, 0x2c, parent, n); };
    void* lights = parameter("Lights");
    void* pair = parameter("Pair");
    void* expanded = parameter("Expanded");
    void* user = parameter("UserData");
    descriptor(effect, lights, 5, 60, 5, 1, "named Light array descriptor is 60 bytes");
    descriptor(effect, pair, 5, 120, 5, 2, "two Light elements occupy 120 packed bytes");
    descriptor(effect, expanded, 5, 68, 7, 1, "xpack Light array descriptor is 68 bytes");
    descriptor(effect, user, 5, 448, 1, 0, "anonymous UserData descriptor is 448 bytes");
    if (!lights || !pair || !expanded || !user) return 1;
    float light[17], pairData[30], matrices[112];
    for (unsigned n = 0; n < 17; ++n) light[n] = float(n + 1);
    for (unsigned n = 0; n < 30; ++n) pairData[n] = float(n + 101);
    for (unsigned n = 0; n < 112; ++n) matrices[n] = float(n + 201);
    check(call<long>(effect, 0x50, lights, static_cast<const void*>(light), 68u) == 0, "68-byte input accepts vanilla Light prefix");
    check(call<long>(effect, 0x50, expanded, static_cast<const void*>(light), 68u) == 0, "complete xpack Light SetValue");
    check(call<long>(effect, 0x50, pair, static_cast<const void*>(pairData), 120u) == 0, "Light array SetValue");
    check(call<long>(effect, 0x50, user, static_cast<const void*>(matrices), 448u) == 0, "complete anonymous UV SetValue");
    const char* names[] = {"pos", "dir", "color", "specularColor", "attenuation", "coneAngleInner", "coneAngleOuter"};
    const unsigned sizes[] = {3,3,4,4,1,1,1};
    unsigned offset = 0;
    void* firstLight = element(lights, 0);
    void* xpackLight = element(expanded, 0);
    descriptor(effect, firstLight, 5, 60, 5, 0, "Light element descriptor shares the full structure");
    for (unsigned n = 0; n < 7; ++n)
    {
        if (n < 5)
        {
            void* field = member(firstLight, names[n]);
            check(field && field == call<void*>(effect, 0x20, firstLight, n), "named and indexed member handles agree");
            floats(effect, field, light + offset, sizes[n], "vanilla light field round-trip");
            floats(effect, member(element(pair, 0), names[n]), pairData + offset, sizes[n], "first array element field round-trip");
            floats(effect, member(element(pair, 1), names[n]), pairData + 15 + offset, sizes[n], "second array element field round-trip");
        }
        floats(effect, member(xpackLight, names[n]), light + offset, sizes[n], "xpack light field round-trip");
        offset += sizes[n];
    }
    void* uv = member(user, "uvMatrix");
    descriptor(effect, uv, 2, 448, 0, 7, "UV matrix array descriptor");
    floats(effect, uv, matrices, 112, "all seven UV matrices round-trip");
    for (unsigned n = 0; n < 7; ++n)
        floats(effect, element(uv, n), matrices + n * 16, 16, "individual UV matrix view round-trip");
    const float color[] = {501,502,503,504};
    check(call<long>(effect, 0x88, member(firstLight, "color"), color) == 0, "write color through element member handle");
    floats(effect, member(lights, "color"), color, 4, "member write visible through parent member view");
    float replacement[16]; for (unsigned n = 0; n < 16; ++n) replacement[n] = float(n + 601);
    check(call<long>(effect, 0x98, element(uv, 6), replacement) == 0, "write last UV matrix by public SetMatrix");
    std::memcpy(matrices + 96, replacement, sizeof(replacement));
    floats(effect, uv, matrices, 112, "last-matrix write shares parent storage without damaging neighbors");
    check(element(uv, 7) == nullptr && element(lights, 1) == nullptr, "array element bounds are respected");
    call<unsigned long>(effect, 0x08);
    dlclose(renderer); dlclose(host);
    std::printf("APK renderer effect ABI: %u/%u checks passed\n", checks - failures, checks);
    return failures ? 1 : 0;
}

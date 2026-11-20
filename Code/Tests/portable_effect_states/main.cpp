// [CHOSEN, not recovered] Exercise the production portable effect ABI with a recording
// ID3DXEffectStateManager. No game assets or substitute effect implementation are used.
#include <array>
#include <cstdio>
#include <cstring>
#include <map>
#include <tuple>
#include <vector>

extern "C" long D3DXCreateEffect(void*, const void*, unsigned, const void*, void*,
                                 unsigned long, void*, void**, void**);

template<class R, class... A> R call(void* object, unsigned offset, A... args)
{
    void** table = *static_cast<void***>(object);
    return reinterpret_cast<R (*)(void*, A...)>(table[offset / 4])(object, args...);
}

struct Manager
{
    void** vtable;
    unsigned calls = 0;
    std::map<unsigned, unsigned> render;
    std::map<std::pair<unsigned, unsigned>, unsigned> stage, sampler;
    std::map<unsigned, void*> textures;
    std::map<unsigned, std::array<float, 4>> vertex, pixel;
};

unsigned long retain(void*) { return 1; }
long render(void* p, unsigned state, unsigned value)
{
    auto* m = static_cast<Manager*>(p); ++m->calls; m->render[state] = value; return 0;
}
long stage(void* p, unsigned index, unsigned state, unsigned value)
{
    auto* m = static_cast<Manager*>(p); ++m->calls; m->stage[{index, state}] = value; return 0;
}
long sampler(void* p, unsigned index, unsigned state, unsigned value)
{
    auto* m = static_cast<Manager*>(p); ++m->calls; m->sampler[{index, state}] = value; return 0;
}
long texture(void* p, unsigned index, void* value)
{
    auto* m = static_cast<Manager*>(p); ++m->calls; m->textures[index] = value; return 0;
}
long constants(void* p, unsigned index, const float* data, unsigned count, bool pixel)
{
    auto* m = static_cast<Manager*>(p); ++m->calls;
    for (unsigned i = 0; i < count; ++i)
        std::memcpy((pixel ? m->pixel : m->vertex)[index + i].data(), data + i * 4, 16);
    return 0;
}
long vertex(void* p, unsigned index, const float* data, unsigned count)
{ return constants(p, index, data, count, false); }
long pixel(void* p, unsigned index, const float* data, unsigned count)
{ return constants(p, index, data, count, true); }

unsigned checks = 0, failed = 0;
void check(bool result, const char* message)
{
    ++checks;
    if (!result) { ++failed; std::fprintf(stderr, "FAIL: %s\n", message); }
}

int main()
{
    const char source[] = R"FX(
bool AlphaBlendEnable = false;
bool AlphaTest = false;
bool DepthWrite = true;
int AlphaTestRef = 0;
int Filter = 1;
int ColorOp = 2;
float4 Tint = float4(1, 2, 3, 4);
texture Diffuse;
sampler DiffuseSampler = sampler_state { Texture = <Diffuse>; MinFilter = (Filter); };
technique Variable {
    pass p0 {
        AlphaBlendEnable = (AlphaBlendEnable);
        AlphaTestEnable = (AlphaTest);
        AlphaRef = (AlphaTestRef);
        SrcBlend = SRCALPHA;
        DestBlend = INVSRCALPHA;
        ZWriteEnable = (DepthWrite);
        CullMode = NONE;
        Sampler[0] = (DiffuseSampler);
        Sampler[1] = (DiffuseSampler);
        Texture[2] = (Diffuse);
        ColorOp[2] = (ColorOp);
        PixelShaderConstantF[3] = (Tint);
        VertexShaderConstantF[7] = (Tint);
        FogEnable = (AlphaTest);
        FogEnable = false;
    }
    pass p1 { AlphaBlendEnable = false; CullMode = CW; }
}
)FX";
    void* effect = nullptr;
    check(D3DXCreateEffect(nullptr, source, sizeof(source) - 1, nullptr, nullptr, 0,
                          nullptr, &effect, nullptr) == 0 && effect, "create real effect");
    if (!effect) return 1;
    void* table[21] = {};
    table[1] = table[2] = reinterpret_cast<void*>(&retain);
    table[7] = reinterpret_cast<void*>(&render); table[8] = reinterpret_cast<void*>(&texture);
    table[9] = reinterpret_cast<void*>(&stage); table[10] = reinterpret_cast<void*>(&sampler);
    table[14] = reinterpret_cast<void*>(&vertex); table[18] = reinterpret_cast<void*>(&pixel);
    Manager m{table};
    call<long>(effect, 0x11c, static_cast<void*>(&m));
    unsigned passes = 0;
    check(call<long>(effect, 0xfc, &passes, 0u) == 0 && passes == 2, "begin two-pass effect");
    check(call<long>(effect, 0x100, 0u) == 0, "begin material pass");
    check(m.render[27] == 0 && m.render[14] == 1, "pass defaults applied");
    check(m.render[19] == 5 && m.render[20] == 6, "straight-alpha blend factors");
    auto setInt = [&](const char* name, int value) {
        check(call<long>(effect, 0x68, name, value) == 0, name);
    };
    auto commit = [&] { check(call<long>(effect, 0x104) == 0, "commit"); };
    unsigned calls = m.calls;
    setInt("AlphaBlendEnable", 1); setInt("AlphaTest", 1);
    setInt("AlphaTestRef", 128); setInt("DepthWrite", 0);
    check(m.calls == calls, "Set methods wait until commit");
    commit();
    check(m.render[27] == 1, "material alpha blend changes after BeginPass");
    check(m.render[15] == 1 && m.render[24] == 128, "material alpha test changes after BeginPass");
    check(m.render[14] == 0, "transparent material disables depth writes");
    check(m.render[28] == 0, "last repeated state assignment wins");
    setInt("AlphaBlendEnable", 0); setInt("AlphaTest", 0); setInt("DepthWrite", 1);
    commit();
    check(m.render[27] == 0 && m.render[15] == 0 && m.render[14] == 1,
          "next opaque material restores alpha and depth");
    // Match RenderQueues' transparent-pass overrides after material setters. An unchanged
    // effect default must not overwrite the renderer's direct device/state-manager changes.
    render(&m, 27, 1); render(&m, 19, 2); render(&m, 20, 2); render(&m, 22, 3);
    calls = m.calls;
    commit();
    check(m.calls == calls && m.render[27] == 1 && m.render[19] == 2
          && m.render[20] == 2 && m.render[22] == 3, "unchanged states preserve queue overrides");
    setInt("Filter", 2); setInt("ColorOp", 4);
    commit();
    check(m.sampler[{0, 6}] == 2 && m.sampler[{1, 6}] == 2, "sampler parameters update every binding");
    check(m.stage[{2, 1}] == 4, "texture stage parameter updates");
    void* textureVtable[3] = {nullptr, reinterpret_cast<void*>(&retain), reinterpret_cast<void*>(&retain)};
    void** textureObject = textureVtable;
    check(call<long>(effect, 0xd0, "Diffuse", static_cast<void*>(&textureObject)) == 0, "set texture");
    commit();
    check(m.textures[0] == &textureObject && m.textures[1] == &textureObject
          && m.textures[2] == &textureObject, "texture changes propagate to sampler and explicit stage");
    const float tint[4] = {5, 6, 7, 8};
    check(call<long>(effect, 0x88, "Tint", tint) == 0, "set vector");
    commit();
    check(m.pixel[3][0] == 5 && m.pixel[3][3] == 8 && m.vertex[7][2] == 7,
          "explicit pass constant registers update");
    check(call<long>(effect, 0xd0, "Diffuse", static_cast<void*>(nullptr)) == 0, "unbind texture");
    commit();
    check(!m.textures[0] && !m.textures[1] && !m.textures[2], "null textures propagate");
    call<long>(effect, 0x108);
    calls = m.calls;
    commit();
    check(m.calls == calls, "commit outside pass has no device writes");
    call<long>(effect, 0x100, 1u);
    check(m.render[27] == 0 && m.render[22] == 2, "next pass applies its literal states");
    call<long>(effect, 0x108);
    call<long>(effect, 0x100, 0u);
    check(m.render[27] == 0 && m.render[19] == 5 && m.render[20] == 6 && m.render[22] == 1,
          "new BeginPass reapplies defaults even after external overrides");
    call<long>(effect, 0x108); call<long>(effect, 0x10c); call<unsigned long>(effect, 0x08);
    std::printf("portable effect state checks: %u, failures: %u\n", checks, failed);
    return failed ? 1 : 0;
}

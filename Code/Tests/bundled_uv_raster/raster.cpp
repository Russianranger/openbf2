// Actual stock M1A2 vertices, indices, diffuse DDS and stock UV function.
// Camera and unlit output isolate UV binding from world/lighting simulation.
#include <SDL.h>
#include <cctype>
#include <cmath>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <d3d9.h>
#include <dirent.h>
#include <fstream>
#include <map>
#include <set>
#include <vector>
#ifndef BFU_EFFECT_SOURCE
#error Define BFU_EFFECT_SOURCE to the production D3dxEffectPortable.cpp being tested
#endif
#include BFU_EFFECT_SOURCE

static void require(bool ok, const char *why)
{
    if (!ok)
    {
        std::fprintf(stderr, "FAIL %s\n", why);
        std::exit(2);
    }
}
static void hr(HRESULT value, const char *why)
{
    if (FAILED(value))
    {
        std::fprintf(stderr, "FAIL %s HRESULT=%08x\n", why, unsigned(value));
        std::exit(2);
    }
}
static std::vector<unsigned char> bytes(const char *path)
{
    std::ifstream f(path, std::ios::binary);
    require(bool(f), path);
    return {std::istreambuf_iterator<char>(f), std::istreambuf_iterator<char>()};
}
static unsigned word(const std::vector<unsigned char> &a, unsigned off)
{
    unsigned v;
    std::memcpy(&v, a.data() + off, 4);
    return v;
}
static std::vector<unsigned> pixels(IDirect3DDevice9 *d, const char *path)
{
    IDirect3DSurface9 *rt = nullptr, *read = nullptr;
    hr(d->GetRenderTarget(0, &rt), "get RT");
    D3DSURFACE_DESC desc{};
    rt->GetDesc(&desc);
    hr(d->CreateOffscreenPlainSurface(desc.Width, desc.Height, desc.Format, D3DPOOL_SYSTEMMEM,
                                      &read, nullptr),
       "readback surface");
    hr(d->GetRenderTargetData(rt, read), "GPU readback");
    D3DLOCKED_RECT lock{};
    hr(read->LockRect(&lock, nullptr, D3DLOCK_READONLY), "readback lock");
    std::vector<unsigned> out(desc.Width * desc.Height);
    std::ofstream f(path, std::ios::binary);
    f << "P6\n" << desc.Width << " " << desc.Height << "\n255\n";
    for (unsigned y = 0; y < desc.Height; ++y)
        for (unsigned x = 0; x < desc.Width; ++x)
        {
            unsigned v;
            std::memcpy(&v, static_cast<char *>(lock.pBits) + y * lock.Pitch + x * 4, 4);
            out[y * desc.Width + x] = v;
            char rgb[3] = {char(v >> 16), char(v >> 8), char(v)};
            f.write(rgb, 3);
        }
    read->UnlockRect();
    read->Release();
    rt->Release();
    return out;
}
static std::map<std::string, std::string> shaderPaths;
static long openStock(void *, unsigned, const char *name, const void *, const void **output,
                      unsigned *size)
{
    std::string key = name;
    key = key.substr(key.find_last_of("/\\") + 1);
    for (char &c : key)
        c = std::tolower((unsigned char)c);
    auto it = shaderPaths.find(key);
    if (it == shaderPaths.end())
    {
        std::fprintf(stderr, "missing include %s\n", name);
        return -1;
    }
    auto data = bytes(it->second.c_str());
    char *copy = new char[data.size()];
    std::memcpy(copy, data.data(), data.size());
    *output = copy;
    *size = data.size();
    return 0;
}
static long closeStock(void *, const void *data)
{
    delete[] static_cast<const char *>(data);
    return 0;
}
struct Vertex
{
    float pos[3], normal[3];
    unsigned blend;
    float uv[2], center[2], tangent[3], reference[2];
};
static_assert(sizeof(Vertex) == 64, "extended stock vertex");
struct Range
{
    unsigned lod, start, index, count, vertices;
};
static IDirect3DTexture9 *texture(IDirect3DDevice9 *d, const char *path)
{
    auto a = bytes(path);
    require(a.size() >= 128 && word(a, 0) == 0x20534444, "DDS");
    unsigned w = word(a, 16), h = word(a, 12), fourcc = word(a, 84);
    require(fourcc == D3DFMT_DXT5, "stock DXT5");
    IDirect3DTexture9 *t = nullptr;
    hr(d->CreateTexture(w, h, 1, 0, D3DFMT_DXT5, D3DPOOL_MANAGED, &t, nullptr), "DDS texture");
    D3DLOCKED_RECT l{};
    hr(t->LockRect(0, &l, nullptr, 0), "DDS lock");
    unsigned pitch = ((w + 3) / 4) * 16;
    require(a.size() >= 128 + pitch * ((h + 3) / 4), "DDS length");
    for (unsigned y = 0; y < (h + 3) / 4; ++y)
        std::memcpy((char *)l.pBits + y * l.Pitch, a.data() + 128 + y * pitch, pitch);
    t->UnlockRect(0);
    return t;
}
int main(int argc, char **argv)
{
    require(argc == 8,
            "shaders vertices indices ranges diffuse.dds output-prefix prior-constants[0|1]");
    const bool priorConstants = std::atoi(argv[7]) != 0;
    auto raw = bytes(argv[2]), idx = bytes(argv[3]), rd = bytes(argv[4]);
    require(raw.size() % 56 == 0, "stock stride56");
    std::vector<Vertex> v(raw.size() / 56);
    for (unsigned i = 0; i < v.size(); ++i)
    {
        std::memcpy(&v[i], raw.data() + 56 * i, 56);
        v[i].reference[0] = v[i].reference[1] = 0;
    }
    require(rd.size() == 4 + word(rd, 0) * sizeof(Range), "ranges");
    std::vector<Range> ranges(word(rd, 0));
    std::memcpy(ranges.data(), rd.data() + 4, rd.size() - 4);
    DIR *folder = opendir(argv[1]);
    require(folder, "shader directory");
    while (auto *ent = readdir(folder))
    {
        std::string key = ent->d_name;
        for (char &c : key)
            c = std::tolower((unsigned char)c);
        shaderPaths[key] = std::string(argv[1]) + "/" + ent->d_name;
    }
    closedir(folder);
    // The Xvfb listener can open just before the video driver becomes ready.
    bool ready = false;
    std::string videoError;
    for (unsigned retry = 0; retry < 50; ++retry)
    {
        if (SDL_Init(SDL_INIT_VIDEO) == 0)
        {
            ready = true;
            break;
        }
        videoError = SDL_GetError();
        SDL_Quit();
        SDL_Delay(100);
    }
    require(ready, videoError.c_str());
    SDL_Window *window =
        SDL_CreateWindow("stock tank UV", 0, 0, 768, 512, SDL_WINDOW_VULKAN | SDL_WINDOW_HIDDEN);
    require(window, SDL_GetError());
    IDirect3D9 *api = Direct3DCreate9(D3D_SDK_VERSION);
    require(api, "D3D9");
    D3DPRESENT_PARAMETERS pp{};
    pp.BackBufferWidth = 768;
    pp.BackBufferHeight = 512;
    pp.BackBufferFormat = D3DFMT_A8R8G8B8;
    pp.BackBufferCount = 1;
    pp.SwapEffect = D3DSWAPEFFECT_DISCARD;
    pp.hDeviceWindow = (HWND)window;
    pp.Windowed = TRUE;
    pp.EnableAutoDepthStencil = TRUE;
    pp.AutoDepthStencilFormat = D3DFMT_D24S8;
    IDirect3DDevice9 *d = nullptr;
    hr(api->CreateDevice(0, D3DDEVTYPE_HAL, pp.hDeviceWindow, D3DCREATE_HARDWARE_VERTEXPROCESSING,
                         &pp, &d),
       "device");
    // The stock include supplies UserData, getSkinnedUVMatrix and calcUVRotation verbatim.
    const char *source = R"FX(
 #include "RaShaderBM.fx"
 bool TestReference=false;
 struct TestInput {float3 Pos:POSITION; float3 Normal:NORMAL;float4 BlendIndices:BLENDINDICES;float2 TexDiffuse:TEXCOORD0;float2 TexUVRotCenter:TEXCOORD1;float3 Tan:TANGENT;float2 Ref:TEXCOORD2;};
 struct TestOutput {float4 Pos:POSITION;float2 UV:TEXCOORD0;};
 TestOutput TestVS(TestInput i) {
 BMVariableVSInput s; s.Pos=float4(i.Pos,1);s.Normal=i.Normal;s.BlendIndices=i.BlendIndices;s.TexDiffuse=i.TexDiffuse;s.TexUVRotCenter=i.TexUVRotCenter;s.Tan=i.Tan;
 TestOutput o;float3 p=i.Pos;
 o.Pos=float4((0.86*p.x+0.5*p.z)/6.2,(-0.18*p.x+0.85*p.y+0.31*p.z-0.75)/4.2,(0.4*p.x-0.5*p.y-0.7*p.z+15)/30,1);
 o.UV=TestReference?i.Ref:calcUVRotation(s).xy;return o;}
 float4 TestPS(TestOutput i):COLOR0 {return float4(tex2D(DiffuseMapSampler,i.UV).rgb,1);}
 technique TestUV {pass P {CullMode=NONE;ZEnable=true;ZWriteEnable=true;ZFunc=LESSEQUAL;AlphaBlendEnable=false;AlphaTestEnable=false;VertexShader=compile vs_3_0 TestVS();PixelShader=compile ps_3_0 TestPS();}}
 )FX";
    PortableMacro macros[] = {{"NVIDIA", "0"},
                              {"ATI", "1"},
                              {"SM30", "1"},
                              {"IS_NV4X", "0"},
                              {"PS2_EXT", "ps_2_0"},
                              {"LOWPSMODEL", "ps_1_4"},
                              {"FILTER_BM_DIFF_MIN", "LINEAR"},
                              {"FILTER_BM_DIFF_MAG", "LINEAR"},
                              {"FILTER_BM_NORM_MIP", "LINEAR"},
                              {"RAPATH", "1"},
                              {"_HASUVANIMATION_", "1"},
                              {"_POINTLIGHT_", "0"},
                              {"_HASSHADOWOCCLUSION_", "0"},
                              {nullptr, nullptr}};
    void *table[] = {(void *)&openStock, (void *)&closeStock};
    void **include = table;
    void *e = nullptr;
    require(D3DXCreateEffect(d, source, std::strlen(source), macros, &include, 0, nullptr, &e,
                             nullptr) == 0,
            "stock UV effect");
    auto *tech = static_cast<Technique *>(fxGetTechniqueByName(e, "TestUV"));
    require(tech, "TestUV");
    fxSetTechnique(e, tech);
    fxSetFloat(e, (char *)"TexUnpack", 1);
    auto *t = texture(d, argv[5]);
    fxSetTexture(e, (char *)"DiffuseMap", t);
    D3DVERTEXELEMENT9 decl[] = {{0, 0, D3DDECLTYPE_FLOAT3, 0, D3DDECLUSAGE_POSITION, 0},
                                {0, 12, D3DDECLTYPE_FLOAT3, 0, D3DDECLUSAGE_NORMAL, 0},
                                {0, 24, D3DDECLTYPE_D3DCOLOR, 0, D3DDECLUSAGE_BLENDINDICES, 0},
                                {0, 28, D3DDECLTYPE_FLOAT2, 0, D3DDECLUSAGE_TEXCOORD, 0},
                                {0, 36, D3DDECLTYPE_FLOAT2, 0, D3DDECLUSAGE_TEXCOORD, 1},
                                {0, 44, D3DDECLTYPE_FLOAT3, 0, D3DDECLUSAGE_TANGENT, 0},
                                {0, 56, D3DDECLTYPE_FLOAT2, 0, D3DDECLUSAGE_TEXCOORD, 2},
                                D3DDECL_END()};
    IDirect3DVertexDeclaration9 *vd = nullptr;
    hr(d->CreateVertexDeclaration(decl, &vd), "stock declaration plus referenceUV");
    d->SetVertexDeclaration(vd);
    std::printf("UserData_registered=%d vertices=%zu ranges=%zu prior_constants=%d\n",
                fxGetParameterByName(e, nullptr, "UserData") != nullptr, v.size(), ranges.size(),
                priorConstants);
    unsigned failures = 0;
    std::vector<unsigned> phase0;
    for (unsigned phase = 0; phase < 2; ++phase)
    {
        float matrices[7][16]{};
        for (unsigned m = 0; m < 7; ++m)
            for (unsigned i = 0; i < 4; ++i)
                matrices[m][i * 5] = 1;
        if (phase)
        {
            for (unsigned m = 1; m < 5; ++m)
            {
                float a = .7f * (m % 2 ? -1 : 1);
                matrices[m][0] = std::cos(a);
                matrices[m][1] = -std::sin(a);
                matrices[m][4] = std::sin(a);
                matrices[m][5] = std::cos(a);
            }
            matrices[5][8] = .12f;
            matrices[6][8] = -.09f;
        }
        auto result = fxSetValue(e, (char *)"UserData", matrices, sizeof(matrices));
        for (auto &vertex : v)
        {
            unsigned index = vertex.blend >> 24;
            require(index < 7, "stock UV index");
            float *x = matrices[index];
            vertex.reference[0] =
                vertex.center[0] * x[0] + vertex.center[1] * x[4] + x[8] + vertex.uv[0];
            vertex.reference[1] =
                vertex.center[0] * x[1] + vertex.center[1] * x[5] + x[9] + vertex.uv[1];
        }
        for (unsigned lod = 0; lod < 4; ++lod)
        {
            std::vector<unsigned> gpu, cpu;
            for (unsigned reference = 0; reference < 2; ++reference)
            {
                fxSetBool(e, (char *)"TestReference", reference);
                d->Clear(0, nullptr, D3DCLEAR_TARGET | D3DCLEAR_ZBUFFER, 0xff516479, 1, 0);
                d->BeginScene(); // Model a previous draw that used the shared vertex-constant bank.
                                 // The effect
                // must overwrite every constant it reads, regardless of these prior values.
                if (priorConstants)
                {
                    float prior[256 * 4];
                    const float transform[16] = {.3f, 0, .4f, 0, 0, .7f, .25f, 0,
                                                 0,   0, 1,   0, 0, 0,   0,    1};
                    for (unsigned k = 0; k < 256 * 4; ++k)
                        prior[k] = transform[k % 16];
                    hr(d->SetVertexShaderConstantF(0, prior, 256), "prior draw constants");
                }
                unsigned n = 0;
                fxBegin(e, &n, 0);
                fxBeginPass(e, 0);
                fxCommitChanges(e);
                if (phase == 0 && lod == 0 && reference == 0)
                {
                    auto *shader = self(e)->resolve(tech->passes[0]->vertex);
                    for (auto &k : shader->constants)
                    {
                        std::printf("VS constant %s reg=%u count=%u class=%u\n", k.name.c_str(),
                                    k.reg, k.count, k.cls);
                        if (k.name == "UserData")
                        {
                            float sample[16]{};
                            d->GetVertexShaderConstantF(k.reg, sample, 4);
                            for (unsigned z = 0; z < 16; ++z)
                                std::printf(" %g", sample[z]);
                            std::puts("");
                        }
                    }
                }

                for (const auto &r : ranges)
                    if (r.lod == lod)
                    {
                        require(r.start + r.vertices <= v.size() &&
                                    r.index + r.count <= idx.size() / 2,
                                "material range");
                        hr(d->DrawIndexedPrimitiveUP(D3DPT_TRIANGLELIST, 0, r.vertices, r.count / 3,
                                                     idx.data() + r.index * 2, D3DFMT_INDEX16,
                                                     v.data() + r.start, sizeof(Vertex)),
                           "stock tank draw");
                    }
                fxEndPass(e);
                fxEnd(e);
                d->EndScene();
                auto read =
                    pixels(d, (std::string(argv[6]) + "-p" + std::to_string(phase) + "-lod" +
                               std::to_string(lod) + (reference ? "-cpu" : "-gpu") + ".ppm")
                                  .c_str());
                if (reference)
                    cpu = std::move(read);
                else
                    gpu = std::move(read);
            }
            unsigned mismatched = 0, painted = 0, maxerr = 0;
            for (unsigned i = 0; i < gpu.size(); ++i)
            {
                painted += (cpu[i] & 0xffffff) != 0x516479;
                unsigned delta = 0;
                for (unsigned c = 0; c < 3; ++c)
                    delta = std::max(delta, unsigned(std::abs(int((cpu[i] >> (c * 8)) & 255) -
                                                              int((gpu[i] >> (c * 8)) & 255))));
                mismatched += delta > 2;
                maxerr = std::max(maxerr, delta);
            }
            std::printf("phase=%u lod=%u SetValue=%lx colored=%u pixel_mismatch_gt2=%u "
                        "max_channel_error=%u\n",
                        phase, lod, (unsigned long)result, painted, mismatched, maxerr);
            failures += mismatched > painted / 1000 || painted < 1000;
            if (lod == 0)
            {
                if (!phase)
                    phase0 = gpu;
                else
                {
                    unsigned changed = 0;
                    for (unsigned i = 0; i < gpu.size(); ++i)
                        changed += gpu[i] != phase0[i];
                    std::printf("animation_changed_pixels=%u\n", changed);
                    failures += changed < 100;
                }
            }
        }
    }
    std::printf("failures=%u\n", failures);
    fxRelease(e);
    t->Release();
    vd->Release();
    d->Release();
    api->Release();
    SDL_DestroyWindow(window);
    SDL_Quit();
    return failures ? 1 : 0;
}

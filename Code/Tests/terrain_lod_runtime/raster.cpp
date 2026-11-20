// Actual portable effect compiler -> native DXVK D3D9 -> Vulkan pixel test.
// A synthetic flat grid uses the stock terrain vertex format and actual loaded index ranges.
// Renders a four-leaf subdivision independently, like a culler-selected visible region.
// No production shader/effect method or state is replaced.
#include <SDL.h>
#include <cctype>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <d3d9.h>
#include <dirent.h>
#include <fstream>
#include <map>
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
struct TerrainVertex
{
    unsigned xy;
    float height, morph[3];
    unsigned normal;
};
int main(int argc, char **argv)
{
    require(argc == 4, "shader directory, output prefix, captured terrain ranges");
    auto ranges = bytes(argv[3]);
    unsigned indexCount = word(ranges, 0);
    require(ranges.size() == 4 + indexCount * 2 + 64 * 8, "captured ranges");
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
    require(SDL_Init(SDL_INIT_VIDEO) == 0, SDL_GetError());
    SDL_Window *window =
        SDL_CreateWindow("terrain depth", 0, 0, 256, 256, SDL_WINDOW_VULKAN | SDL_WINDOW_HIDDEN);
    require(window, SDL_GetError());
    IDirect3D9 *api = Direct3DCreate9(D3D_SDK_VERSION);
    require(api, "D3D9");
    D3DPRESENT_PARAMETERS pp{};
    pp.BackBufferWidth = 256;
    pp.BackBufferHeight = 256;
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
    PortableMacro macros[] = {{"NVIDIA", "0"},      {"ATI", "1"},          {"SM30", "1"},
                              {"IS_NV4X", "0"},     {"PS2_EXT", "ps_2_0"}, {"LOWPSMODEL", "ps_1_4"},
                              {"HIGHTERRAIN", "0"}, {"MIDTERRAIN", "1"},   {nullptr, nullptr}};
    void *includeTable[] = {reinterpret_cast<void *>(&openStock),
                            reinterpret_cast<void *>(&closeStock)};
    void **include = includeTable;
    auto data = bytes(shaderPaths["terrainshader.fx"].c_str());
    void *e = nullptr;
    require(D3DXCreateEffect(d, data.data(), data.size(), macros, &include, 0, nullptr, &e,
                             nullptr) == 0,
            "stock effect");
    auto *fx = self(e);
    auto *technique = static_cast<Technique *>(fxGetTechniqueByName(e, "Hi_Terrain"));
    require(technique, "Hi_Terrain");
    fxSetTechnique(e, technique);
    float projection[16] = {1, 0, 0, 0, 0, 0, 1, 0, 0, 1, 0, 0, 0, 0, 0, 1};
    fxSetMatrix(e, (char *)"mViewProj", projection);
    auto vec = [&](const char *name, float x, float y, float z, float w)
    {
        float v[] = {x, y, z, w};
        require(fxSetVector(e, (char *)name, v) == 0, name);
    };
    vec("vScaleTransXZ", 14.34375f, 14.34375f, -2.7f, -.9f);
    vec("vScaleTransY", 1, 1, 0, 0);
    vec("vNearFarMorphLimits", 0, 0, 0, 0);
    vec("vMorphDeltaSelector", 0, 0, 0, 0);
    vec("vGIColor", .25f, .5f, .75f, 1);
    vec("vColorLightTex", 1, 0, 0, 0);
    float morph[12] = {};
    fxSetFloatArray(e, (char *)"vMorphDeltaAdder", morph, 9);
    IDirect3DTexture9 *light = nullptr;
    hr(d->CreateTexture(1, 1, 1, 0, D3DFMT_A8R8G8B8, D3DPOOL_MANAGED, &light, nullptr),
       "light texture");
    D3DLOCKED_RECT lock{};
    light->LockRect(0, &lock, nullptr, 0);
    *static_cast<unsigned *>(lock.pBits) = 0xffffffff;
    light->UnlockRect(0);
    fxSetTexture(e, (char *)"texture0", light);
    D3DVERTEXELEMENT9 decl[] = {{0, 0, D3DDECLTYPE_D3DCOLOR, 0, D3DDECLUSAGE_POSITION, 0},
                                {0, 4, D3DDECLTYPE_FLOAT1, 0, D3DDECLUSAGE_POSITION, 1},
                                {0, 8, D3DDECLTYPE_FLOAT3, 0, D3DDECLUSAGE_POSITION, 2},
                                {0, 20, D3DDECLTYPE_D3DCOLOR, 0, D3DDECLUSAGE_NORMAL, 0},
                                D3DDECL_END()};
    IDirect3DVertexDeclaration9 *vd = nullptr;
    hr(d->CreateVertexDeclaration(decl, &vd), "terrain declaration");
    d->SetVertexDeclaration(vd);
    auto packed = [](unsigned x, unsigned y) { return (x << 16) | (y << 8); };
    std::vector<TerrainVertex> q;
    for (unsigned y = 0; y <= 128; ++y)
        for (unsigned x = 0; x <= 128; ++x)
            q.push_back({packed(x, y), .5f, {0, 0, 0}, 0xffffffff});
    d->Clear(0, nullptr, D3DCLEAR_TARGET | D3DCLEAR_ZBUFFER, 0xff110000, 1, 0);
    d->BeginScene();
    unsigned passes = 0;
    fxBegin(e, &passes, 0);
    fxBeginPass(e, 0);
    fxCommitChanges(e);
    DWORD z = 0, zw = 0, cull = 0;
    d->GetRenderState(D3DRS_ZENABLE, &z);
    d->GetRenderState(D3DRS_ZWRITEENABLE, &zw);
    d->GetRenderState(D3DRS_CULLMODE, &cull);
    std::printf("passes=%u depth=%u write=%u cull=%u VSstate=%d PSstate=%d\n", passes, unsigned(z),
                unsigned(zw), unsigned(cull), fx->resolve(technique->passes[0]->vertex)->state,
                fx->resolve(technique->passes[0]->pixel)->state);
    unsigned drawn = 0, empty = 0;
    // Four siblings cover stock grid x=32..64, z=0..32. This isolates their draw ranges
    // so erroneous, oversized ranges in unrelated leaves cannot conceal missing triangles.
    for (unsigned leaf = 4; leaf < 8; ++leaf)
    {
        unsigned start = word(ranges, 4 + indexCount * 2 + leaf * 8),
                 count = word(ranges, 8 + indexCount * 2 + leaf * 8);
        require(start + count <= indexCount, "valid index range");
        if (!count)
        {
            ++empty;
            continue;
        }
        hr(d->DrawIndexedPrimitiveUP(D3DPT_TRIANGLELIST, 0, q.size(), count / 3,
                                     ranges.data() + 4 + start * 2, D3DFMT_INDEX16, q.data(),
                                     sizeof(q[0])),
           "terrain indexed draw");
        drawn += count / 3;
    }
    std::printf("terrain drawn_triangles=%u empty_leaves=%u\n", drawn, empty);
    fxEndPass(e);
    d->EndScene();
    auto result = pixels(d, (std::string(argv[2]) + "-terrain.ppm").c_str());
    unsigned written = 0;
    for (auto v : result)
        written += (v & 0xffffff) != 0x110000;
    std::printf("terrain colored=%u center=%06x\n", written, result[128 * 256 + 128] & 0xffffff);
    fxRelease(e);
    light->Release();
    vd->Release();
    d->Release();
    api->Release();
    SDL_DestroyWindow(window);
    SDL_Quit();
    return written > 50000 && empty == 0 ? 0 : 1;
}

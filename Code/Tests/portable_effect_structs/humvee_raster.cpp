// [CHOSEN, not recovered] Private stock Humvee mesh + diffuse DDS, original RaShaderBM effect,
// production portable D3DX runtime and real DXVK/Vulkan readback. Assets remain external.
#include <SDL.h>
#include <dirent.h>
#include <fstream>
#include <cmath>
#ifndef BF2_EFFECT_SOURCE
#define BF2_EFFECT_SOURCE "../../BF2/PortShim/D3dxEffectPortable.cpp"
#endif
#include BF2_EFFECT_SOURCE

static void require(bool ok, const char* why)
{ if (!ok) { std::fprintf(stderr, "FAIL %s\n", why); std::exit(2); } }
static void hr(HRESULT result, const char* why)
{ if (FAILED(result)) { std::fprintf(stderr, "FAIL %s %08x\n", why, unsigned(result)); std::exit(2); } }
static std::vector<unsigned char> load(const string& path)
{
    std::ifstream f(path, std::ios::binary); require(bool(f), path.c_str());
    return {std::istreambuf_iterator<char>(f), std::istreambuf_iterator<char>()};
}
static unsigned word(const std::vector<unsigned char>& b, unsigned off)
{ unsigned n; std::memcpy(&n, b.data() + off, 4); return n; }
static std::map<string, string> includes;
static long openStock(void*, unsigned, const char* file, const void*, const void** out, unsigned* size)
{
    string key = file; key = lower(key.substr(key.find_last_of("/\\") + 1));
    auto it = includes.find(key); if (it == includes.end()) return -1;
    auto b = load(it->second); auto* copy = new unsigned char[b.size()];
    std::memcpy(copy, b.data(), b.size()); *out = copy; *size = unsigned(b.size()); return 0;
}
static long closeStock(void*, const void* p) { delete[] static_cast<const unsigned char*>(p); return 0; }

int main(int argc, char** argv)
{
    require(argc == 5, "shader directory, extracted Humvee directory, stock diffuse DDS, output PPM");
    DIR* dir = opendir(argv[1]); require(dir, "shader directory");
    while (auto* entry = readdir(dir)) includes[lower(entry->d_name)] = string(argv[1]) + "/" + entry->d_name;
    closedir(dir);
    auto vertices = load(string(argv[2]) + "/vertices.bin"), indices = load(string(argv[2]) + "/indices.bin");
    require(vertices.size() == 25330 * 48 && indices.size() >= (3 + 15459) * 2, "EA1.50 Humvee payload sizes");
    const int initialized = SDL_Init(SDL_INIT_VIDEO);
    require(initialized == 0, SDL_GetError());
    auto* window = SDL_CreateWindow("Humvee lighting", 0, 0, 512, 512, SDL_WINDOW_VULKAN | SDL_WINDOW_HIDDEN);
    require(window, SDL_GetError());
    IDirect3D9* api = Direct3DCreate9(D3D_SDK_VERSION); require(api, "D3D9");
    D3DPRESENT_PARAMETERS present = {};
    present.BackBufferWidth = present.BackBufferHeight = 512;
    present.BackBufferFormat = D3DFMT_A8R8G8B8; present.BackBufferCount = 1;
    present.SwapEffect = D3DSWAPEFFECT_DISCARD; present.hDeviceWindow = reinterpret_cast<HWND>(window);
    present.Windowed = TRUE; present.EnableAutoDepthStencil = TRUE; present.AutoDepthStencilFormat = D3DFMT_D24S8;
    IDirect3DDevice9* device = nullptr;
    hr(api->CreateDevice(0, D3DDEVTYPE_HAL, present.hDeviceWindow, D3DCREATE_HARDWARE_VERTEXPROCESSING,
                         &present, &device), "device");
    PortableMacro macros[] = {{"NVIDIA","0"},{"ATI","1"},{"SM30","1"},{"RAPATH","2"},
        {"VSMODEL","vs_2_0"},{"PSMODEL","ps_2_0"},{"PSVERSION","20"},
        {"FILTER_BM_DIFF_MIN","LINEAR"},{"FILTER_BM_DIFF_MAG","LINEAR"},{"FILTER_BM_NORM_MIP","LINEAR"},
        {"_HASSTATICGLOSS_","1"},{nullptr,nullptr}};
    void* includeTable[] = {reinterpret_cast<void*>(&openStock), reinterpret_cast<void*>(&closeStock)};
    void** include = includeTable;
    auto source = load(includes.at("rashaderbm.fx")); void* effect = nullptr;
    require(D3DXCreateEffect(device, source.data(), unsigned(source.size()), macros, &include, 0, nullptr,
                            &effect, nullptr) == 0, "original RaShaderBM effect");
    void* technique = fxGetTechniqueByName(effect, "defaultTechnique");
    if (!technique) technique = fxGetTechniqueByName(effect, "Variable");
    require(technique, "stock technique");
    fxSetTechnique(effect, technique); require(fxValidateTechnique(effect, technique) == 0, "stock shaders compile");
    auto vec = [&](const char* name, float x, float y, float z, float w) {
        const float v[] = {x,y,z,w}; require(fxSetVector(effect, const_cast<char*>(name), v) == 0, name);
    };
    float bones[26 * 16] = {};
    for (unsigned n = 0; n < 26; ++n) for (unsigned a = 0; a < 4; ++a) bones[n * 16 + a * 5] = 1;
    fxSetMatrixArray(effect, const_cast<char*>("GeomBones"), bones, 26);
    const float projection[] = {.43f,-.16f,.015f,0, 0,.58f,-.025f,0, .19f,.10f,.015f,0, 0,-.55f,.5f,1};
    fxSetMatrix(effect, const_cast<char*>("ViewProjection"), projection);
    vec("PosUnpack",1,1,1,1); vec("NormalUnpack",1,0,0,0); vec("WorldSpaceCamPos",4,4,7,1);
    vec("FogRange",0,0,0,1); vec("FogColor",0,0,0,0); vec("Transparency",1,1,1,1);
    if (self(effect)->param("AmbientIlluminationRange"))
    {
        vec("AmbientIlluminationRange",0,0,0,1); vec("AmbientIlluminationColor",0,0,0,0);
        vec("CameraPos",4,4,7,1);
    }
    vec("StaticGloss",0,0,0,0);
    fxSetFloat(effect, const_cast<char*>("TexUnpack"), 1);
    fxSetFloat(effect, const_cast<char*>("SpecularPower"), 16);
    // Light has the exact renderer's 17-float source record, including its ignored two-word tail.
    const float light[] = {0,0,0, -.4f,-.8f,-.4f, .7f,.65f,.6f,.35f, 0,0,0,0, 0,1,1};
    require(fxSetValue(effect, const_cast<char*>("Lights"), light, sizeof(light)) == 0, "light record");
    auto dds = load(argv[3]); require(word(dds,0) == 0x20534444 && word(dds,84) == 0x35545844, "stock DXT5 diffuse");
    const unsigned width = word(dds,16), height = word(dds,12), rows = (height + 3) / 4, rowBytes = (width + 3) / 4 * 16;
    require(dds.size() >= 128 + rows * rowBytes, "complete first DDS mip");
    IDirect3DTexture9* diffuse = nullptr;
    hr(device->CreateTexture(width,height,1,0,D3DFMT_DXT5,D3DPOOL_MANAGED,&diffuse,nullptr), "diffuse texture");
    D3DLOCKED_RECT lock = {}; hr(diffuse->LockRect(0,&lock,nullptr,0), "diffuse lock");
    for (unsigned y = 0; y < rows; ++y)
        std::memcpy(static_cast<char*>(lock.pBits) + y * lock.Pitch, dds.data() + 128 + y * rowBytes, rowBytes);
    diffuse->UnlockRect(0); fxSetTexture(effect, const_cast<char*>("DiffuseMap"), diffuse);
    D3DVERTEXELEMENT9 declaration[] = {{0,0,D3DDECLTYPE_FLOAT3,0,D3DDECLUSAGE_POSITION,0},
        {0,12,D3DDECLTYPE_FLOAT3,0,D3DDECLUSAGE_NORMAL,0}, {0,24,D3DDECLTYPE_D3DCOLOR,0,D3DDECLUSAGE_BLENDINDICES,0},
        {0,28,D3DDECLTYPE_FLOAT2,0,D3DDECLUSAGE_TEXCOORD,0}, {0,36,D3DDECLTYPE_FLOAT3,0,D3DDECLUSAGE_TANGENT,0}, D3DDECL_END()};
    IDirect3DVertexDeclaration9* decl = nullptr; hr(device->CreateVertexDeclaration(declaration,&decl),"declaration");
    device->SetVertexDeclaration(decl);
    IDirect3DVertexBuffer9* vb = nullptr; IDirect3DIndexBuffer9* ib = nullptr; void* target = nullptr;
    hr(device->CreateVertexBuffer(unsigned(vertices.size()),0,0,D3DPOOL_MANAGED,&vb,nullptr),"VB");
    hr(vb->Lock(0,0,&target,0),"VB lock"); std::memcpy(target,vertices.data(),vertices.size()); vb->Unlock();
    hr(device->CreateIndexBuffer(unsigned(indices.size()),0,D3DFMT_INDEX16,D3DPOOL_MANAGED,&ib,nullptr),"IB");
    hr(ib->Lock(0,0,&target,0),"IB lock"); std::memcpy(target,indices.data(),indices.size()); ib->Unlock();
    device->SetStreamSource(0,vb,0,48); device->SetIndices(ib);
    device->Clear(0,nullptr,D3DCLEAR_TARGET|D3DCLEAR_ZBUFFER,0xff204060,1,0);
    device->BeginScene(); unsigned passes = 0;
    fxBegin(effect,&passes,0); fxBeginPass(effect,0);
    device->SetRenderState(D3DRS_CULLMODE,D3DCULL_NONE);
    device->SetRenderState(D3DRS_ALPHABLENDENABLE,FALSE);
    hr(device->DrawIndexedPrimitive(D3DPT_TRIANGLELIST,3,0,7176,3,15459/3),"stock Humvee draw");
    fxEndPass(effect); fxEnd(effect); device->EndScene();
    IDirect3DSurface9* rt = nullptr; IDirect3DSurface9* readback = nullptr;
    hr(device->GetRenderTarget(0,&rt),"render target");
    hr(device->CreateOffscreenPlainSurface(512,512,D3DFMT_A8R8G8B8,D3DPOOL_SYSTEMMEM,&readback,nullptr),"readback");
    hr(device->GetRenderTargetData(rt,readback),"GPU pixels");
    hr(readback->LockRect(&lock,nullptr,D3DLOCK_READONLY),"readback lock");
    std::ofstream output(argv[4],std::ios::binary); output << "P6\n512 512\n255\n";
    unsigned covered = 0, lit = 0;
    for (unsigned y = 0; y < 512; ++y) for (unsigned x = 0; x < 512; ++x)
    {
        unsigned v; std::memcpy(&v,static_cast<char*>(lock.pBits)+y*lock.Pitch+x*4,4);
        const unsigned rgb = v & 0xffffff;
        if (rgb != 0x204060) { ++covered; if (rgb) ++lit; }
        const char bytes[] = {char(v>>16),char(v>>8),char(v)}; output.write(bytes,3);
    }
    readback->UnlockRect(); readback->Release(); rt->Release();
    fxRelease(effect); diffuse->Release(); vb->Release(); ib->Release(); decl->Release(); device->Release(); api->Release();
    SDL_DestroyWindow(window); SDL_Quit();
    std::printf("Humvee stock raster: covered=%u lit=%u black=%u\n",covered,lit,covered-lit);
    require(covered > 5000,"stock Humvee geometry reached render target");
    return lit > covered * .9 ? 0 : 1;
}

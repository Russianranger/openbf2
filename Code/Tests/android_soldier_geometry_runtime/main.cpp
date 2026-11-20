// [TEMP] Real Soldier.cpp dispatch into the shipped Android renderer's SkinnedMesh.
// Only the frame counter and the four scalar-call receivers are fixture providers.
#include "Geom/SkinnedMesh.h"
#include "Geom/SkinnedMeshTemplate.h"
#include "Geom/SkinnedMeshMaterialLayout.h"
#include "Animation/BoneAnimation/Skeleton.h"
#include "IO/Stream/IStream.h"
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <cmath>
#include <dlfcn.h>
#include <vector>

using namespace dice::hfe;
using namespace dice::hfe::geom;
extern "C" void fixtureSetBones(void*, const Mat4*, int);
extern "C" void fixtureSetFov(void*, float);
extern "C" void fixtureSetFirstPerson(void*, bool);
extern "C" void fixtureSetLod(void*, int);

static unsigned frameNumber = 1;
static unsigned frame(void*) { return frameNumber; }
static int checks, failures;
static void check(bool ok, const char* label)
{
    ++checks;
    if (!ok) ++failures;
    std::printf("%s: %s\n", ok ? "PASS" : "FAIL", label);
}

class FileStream final : public io::IStream
{
    std::FILE* file;
public:
    explicit FileStream(const char* path) : file(std::fopen(path, "rb"))
    { if (!file) std::abort(); }
    ~FileStream() { std::fclose(file); }
    void addRef() {} int getRef() { return 1; } int release() { return 1; }
    void* queryInterface(unsigned int) const { return nullptr; }
    unsigned int read(void* data, unsigned int size) { return std::fread(data, 1, size, file); }
    unsigned int write(const void*, unsigned int) { std::abort(); }
    int skip(unsigned int bytes) { return std::fseek(file, bytes, SEEK_CUR); }
    int getAvailableBytes() { std::abort(); }
    io::IStream* clone() { std::abort(); }
    bool canRead() { return true; } bool canWrite() { return false; }
};

struct ScalarReceiver { void** table; float fov; unsigned int firstPerson; int lod; };
static void setFov(ScalarReceiver* self, float value) { self->fov = value; }
static void setFirstPerson(ScalarReceiver* self, unsigned int value) { self->firstPerson = value; }
static void* setLod(ScalarReceiver* self, int value) { self->lod = value; return self; }

int main(int argc, char** argv)
{
    if (argc != 4) return 2;
    std::setbuf(stdout, nullptr);
    void* library = dlopen(argv[1], RTLD_NOW | RTLD_GLOBAL);
    if (!library) { std::fprintf(stderr, "%s\n", dlerror()); return 2; }
    void* frameSlots[22] = {};
    frameSlots[21] = reinterpret_cast<void*>(&frame);
    void** frameReceiver = frameSlots;
    void** frameGlobal = static_cast<void**>(dlsym(library, "_ZN4dice3hfe7dx9rend14g_rendererBaseE"));
    if (!frameGlobal) return 2;
    *frameGlobal = &frameReceiver;

    FileStream stream(argv[3]);
    bool loaded = false;
    dice::anim::Skeleton skeleton("stock kit fixture", &loaded, &stream);
    check(loaded && skeleton.m_boneCount == 80, "production Skeleton loads the stock 3p rig");
    if (!loaded) return 1;

    // Unmodified stock kit inverse-bind records; each native LOD vector owns its own bytes.
    alignas(void*) unsigned char lod[kSkinnedLodBytes] = {};
    std::vector<unsigned char> arrays[4];
    std::FILE* file = std::fopen(argv[2], "rb");
    if (!file) return 2;
    unsigned int count = 0, palettes = 0;
    while (std::fread(&count, 4, 1, file))
    {
        if (palettes >= 4 || count > 26) return 2;
        arrays[palettes].resize(count * 68);
        if (std::fread(arrays[palettes].data(), 68, count, file) != count) return 2;
        auto* vector = reinterpret_cast<vc71::vector_layout*>(lod + 64 + sizeof(vc71::vector_layout) * palettes);
        vector->first = reinterpret_cast<char*>(arrays[palettes].data());
        vector->last = vector->end = vector->first + count * 68;
        ++palettes;
    }
    std::fclose(file);
    if (!palettes) return 2;
    std::vector<unsigned char> materialBytes(palettes * kSkinnedMaterialRecordBytes);
    vc71::vector_layout materials = {};
    materials.first = reinterpret_cast<char*>(materialBytes.data());
    materials.last = materials.end = materials.first + materialBytes.size();
    // The real constructors allocate and initialize all 104 output matrices.
    // Ownership teardown is outside this component fixture: no template instance registration.
    auto* objectTemplate = new SkinnedMeshTemplate;
    auto* mesh = new SkinnedMesh(objectTemplate);
    mesh->m_lodRecord = lod;
    mesh->m_lodMaterials = &materials;

    fixtureSetBones(mesh, skeleton.m_transforms, skeleton.m_boneCount);
    check(mesh->m_boneTransforms == skeleton.m_transforms, "kit pickup host call preserves the borrowed bone pointer");
    check(mesh->m_boneTransformCount == static_cast<unsigned int>(skeleton.m_boneCount), "kit pickup host call preserves the bone count");
    // Move the live pose, as animation does, after the geometry has borrowed it.
    for (int bone = 0; bone < skeleton.m_boneCount; ++bone)
    {
        skeleton.m_transforms[bone].m[12] += 0.125f;
        skeleton.m_transforms[bone].m[13] += 0.25f;
    }
    mesh->updateBonePalettes();
    unsigned int matrixFailures = 0, matrixCount = 0;
    for (unsigned int p = 0; p < palettes; ++p)
    {
        const unsigned int size = arrays[p].size() / 68;
        check(mesh->m_boneCounts[p] == size, "stock kit palette retains every authored bone");
        for (unsigned int b = 0; b < size; ++b)
        {
            unsigned int id;
            Mat4 inverse, expected;
            std::memcpy(&id, arrays[p].data() + b * 68, 4);
            std::memcpy(&inverse, arrays[p].data() + b * 68 + 4, 64);
            if (id >= static_cast<unsigned int>(skeleton.m_boneCount)) return 2;
            expected.fullMult(inverse, skeleton.m_transforms[id]);
            if (std::memcmp(expected.m, mesh->m_bonePalettes[p][b]->m, 64)) ++matrixFailures;
            ++matrixCount;
        }
    }
    std::printf("Stock kit: %u matrices checked, %u mismatches\n", matrixCount, matrixFailures);
    check(matrixFailures == 0, "actual renderer poses the stock kit with the soldier's live matrices");
    fixtureSetBones(mesh, nullptr, 0);
    ++frameNumber;
    mesh->updateBonePalettes();
    check(mesh->m_boneTransforms == nullptr && mesh->m_boneTransformCount == 0,
          "resetWeaponAnimations clears pointer and count");
    check(std::memcmp(mesh->m_bonePalettes[0][0], &Mat4::getIdentity(), 64) == 0,
          "detached geometry keeps the existing identity fallback");

    void* scalarSlots[62] = {};
    scalarSlots[0x10 / 4] = reinterpret_cast<void*>(&setFov);
    scalarSlots[0xD4 / 4] = reinterpret_cast<void*>(&setFirstPerson);
    scalarSlots[0x78 / 4] = reinterpret_cast<void*>(&setLod);
    ScalarReceiver receiver = {scalarSlots, 0, 0, 0};
    fixtureSetFov(&receiver, 73.5f);
    check(receiver.fov == 73.5f, "FOV reaches the native scalar receiver");
    fixtureSetFirstPerson(&receiver, true);
    check(receiver.firstPerson == 1, "first-person enable reaches the native receiver");
    fixtureSetFirstPerson(&receiver, false);
    check(receiver.firstPerson == 0, "first-person disable reaches the native receiver");
    fixtureSetLod(&receiver, 3);
    check(receiver.lod == 3, "nonzero LOD reaches the native receiver");
    fixtureSetLod(&receiver, -1);
    check(receiver.lod == -1, "automatic LOD sentinel reaches the native receiver");
    std::printf("Soldier geometry ABI: %d checks, %d failures\n", checks, failures);
    return failures ? 1 : 0;
}

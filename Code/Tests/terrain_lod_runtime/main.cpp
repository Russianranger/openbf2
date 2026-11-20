// Tests the production terrain LOD loader. GPU allocation is captured in host memory;
// the Terrain storage is an isolated loader-state fixture, not a running render world.
#include "Geom/QuadPatch.h"
#include "Geom/Terrain.h"
#include <cstdint>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <type_traits>
#include <vector>
using namespace dice::hfe;
using namespace dice::hfe::geom;
namespace
{
struct Buffer
{
    std::vector<unsigned char> bytes;
};
struct Input : io::IStream
{
    std::vector<unsigned char> bytes;
    size_t position = 0;
    void addRef() override {}
    int getRef() override { return 1; }
    int release() override { return 1; }
    void *queryInterface(unsigned int) const override { return nullptr; }
    unsigned int read(void *p, unsigned int n) override
    {
        if (position + n > bytes.size())
        {
            std::fprintf(stderr, "short read\n");
            std::abort();
        }
        std::memcpy(p, bytes.data() + position, n);
        position += n;
        return n;
    }
    unsigned int write(const void *, unsigned int) override { return 0; }
    int skip(unsigned int n) override
    {
        position += n;
        return 0;
    }
    int getAvailableBytes() override { return bytes.size() - position; }
    io::IStream *clone() override { return nullptr; }
    bool canRead() override { return true; }
    bool canWrite() override { return false; }
};
void u32(std::vector<unsigned char> &b, unsigned int v)
{
    for (int n = 0; n < 4; ++n)
        b.push_back(v >> (8 * n));
}
unsigned int take(const std::vector<unsigned char> &b, size_t &o)
{
    unsigned int v;
    std::memcpy(&v, b.data() + o, 4);
    o += 4;
    return v;
}
} // namespace
namespace renddx9abi
{
// Match the production allocator's generous 64-bit capacity; this test diagnoses stride,
// not an artificial allocation failure that the Android adapter already prevents.
void *engineAlloc(unsigned int n) { return std::calloc(1, size_t(n) * sizeof(void *) / 4 + 16); }
void engineFree(void *p) { std::free(p); }
} // namespace renddx9abi
namespace dice
{
namespace hfe
{
namespace dx9rend
{
void *bufferManagerCreateIndexBuffer(unsigned int n, unsigned int, unsigned int)
{
    auto *b = new Buffer;
    b->bytes.resize(n * 2);
    return b;
}
void *indexBufferLock(void *b, unsigned int, unsigned int, unsigned int)
{
    return static_cast<Buffer *>(b)->bytes.data();
}
bool indexBufferUnlock(void *) { return true; }
void *bufferManagerCreateVertexBuffer(unsigned int n, unsigned int s, unsigned int, const char *)
{
    auto *b = new Buffer;
    b->bytes.resize(n * s);
    return b;
}
void *vertexBufferLock(void *b, unsigned int, unsigned int, unsigned int)
{
    return static_cast<Buffer *>(b)->bytes.data();
}
bool vertexBufferUnlock(void *) { return true; }
void *vertexDeclarationCreate(const void *) { return nullptr; }
} // namespace dx9rend
} // namespace hfe
} // namespace dice
int main(int argc, char **argv)
{
    Input input;
    if (argc > 1)
    {
        FILE *f = std::fopen(argv[1], "rb");
        if (!f)
            return 2;
        std::fseek(f, 0, SEEK_END);
        input.bytes.resize(std::ftell(f));
        std::rewind(f);
        std::fread(input.bytes.data(), 1, input.bytes.size(), f);
        std::fclose(f);
    }
    else
    {
        for (unsigned int node = 0; node < 85; ++node)
            for (unsigned int lod = 0; lod < 3; ++lod)
            {
                unsigned int masks = (node < 21 && lod) ? 9 : 1;
                for (unsigned int k = 0; k < masks; ++k)
                {
                    u32(input.bytes, 10000 + node * 100 + lod * 10 + k);
                    u32(input.bytes, 20000 + node * 100 + lod * 10 + k);
                }
            }
        u32(input.bytes, 3);
        input.bytes.insert(input.bytes.end(), {0, 0, 1, 0, 2, 0, 0});
    }
    unsigned int expected[85][96] = {};
    size_t cursor = 0;
    const unsigned int masks[9] = {0, 1, 2, 4, 8, 5, 9, 6, 10};
    for (unsigned int node = 0; node < 85; ++node)
        for (unsigned int lod = 0; lod < 3; ++lod)
        {
            unsigned int count = (node < 21 && lod) ? 9 : 1;
            for (unsigned int k = 0; k < count; ++k)
            {
                unsigned int m = masks[k];
                expected[node][lod * 16 + m] = take(input.bytes, cursor);
                expected[node][48 + lod * 16 + m] = take(input.bytes, cursor);
            }
        }
    unsigned int indexCount = take(input.bytes, cursor);
    typename std::aligned_storage<sizeof(Terrain), alignof(Terrain)>::type storage;
    std::memset(&storage, 0, sizeof(storage));
    Terrain *t = reinterpret_cast<Terrain *>(&storage);
    t->m_rawHasVersion = 1;
    t->m_rawVersion = 0x1a;
    t->m_patchSize = 128;
    TerrainRawReader reader;
    reader.m_stream = &input;
    t->loadLodTablesAndBaseGrid(reader);
    unsigned int wrong = 0, wrongLeaves = 0, wrongLinks = 0, zeroLeaves = 0;
    for (unsigned int node = 0; node < 85; ++node)
    {
        const unsigned int *row =
            reinterpret_cast<const unsigned int *>(t->m_lodTables + kTerrainLodRecordBytes * node);
        if (std::memcmp(row, expected[node], sizeof(expected[node])))
        {
            ++wrong;
            if (node >= 21)
                ++wrongLeaves;
        }
        if (node >= 21 && row[48] == 0)
            ++zeroLeaves;
        if (node < 21)
        {
            auto children = reinterpret_cast<unsigned char *const *>(row + 96);
            for (unsigned int c = 0; c < 4; ++c)
                if (children[c] != t->m_lodTables + kTerrainLodRecordBytes * (1 + 4 * node + c))
                    ++wrongLinks;
        }
    }
    auto *ib = static_cast<Buffer *>(t->m_lodIndexBuffer);
    bool indicesOk =
        t->m_lodIndexCount == indexCount && ib->bytes.size() == indexCount * 2 &&
        std::memcmp(ib->bytes.data(), input.bytes.data() + cursor, indexCount * 2) == 0;
    std::printf("record_bytes=%u records_wrong=%u leaf_records_wrong=%u zero_lod0_leaves=%u "
                "child_links_wrong=%u index_bytes_match=%d bytes_read=%zu\n",
                unsigned(kTerrainLodRecordBytes), wrong, wrongLeaves, zeroLeaves, wrongLinks,
                indicesOk, input.position);
    if (argc > 2)
    {
        FILE *f = std::fopen(argv[2], "wb");
        if (!f)
            return 2;
        std::fwrite(&indexCount, 4, 1, f);
        std::fwrite(ib->bytes.data(), 1, ib->bytes.size(), f);
        for (unsigned int node = 21; node < 85; ++node)
        {
            const unsigned int *r = reinterpret_cast<const unsigned int *>(
                t->m_lodTables + kTerrainLodRecordBytes * node);
            std::fwrite(r, 4, 1, f);
            std::fwrite(r + 48, 4, 1, f);
        }
        std::fclose(f);
    }
    delete ib;
    if (t->m_baseGridVertexBuffer)
        delete static_cast<Buffer *>(t->m_baseGridVertexBuffer);
    renddx9abi::engineFree(t->m_lodTables);
    return wrong || wrongLinks || !indicesOk ? 1 : 0;
}

// [TEMP] Calls the real map, island and polygon implementations linked from the AI objects.
#include "AI/AIDLL/AI/Main/AIPathfinding.h"
#include "AI/AIDLL/AI/AIPathFinding/Material/MaterialManager.h"
// Fixture data only: populate a one-leaf quad tree without needing proprietary map archives.
#define private public
#include "AI/AIDLL/AI/AIPathFinding/NavMesh/QuadTree.h"
#undef private
#include <cassert>
#include <cstdio>
#include <limits>
using namespace dice::hfe;
using namespace dice::hfe::ai;

static QuadTree* square(unsigned int island, float offset, unsigned char material)
{
    QuadTree* tree = new QuadTree(island);
    tree->m_min = {offset - 4, -4}; tree->m_max = {offset + 4, 4}; tree->m_size = 8;
    tree->m_numberOfNodes = tree->m_numberOfPolygons = tree->m_numberOfNodePolygons = 1;
    tree->m_vertexCount = tree->m_vertexIndexCount = tree->m_numberOfNeighbours = 4;
    tree->m_nodes = new QuadTreeNode[1];
    tree->m_nodes[0].m_isLeaf = 1; tree->m_nodes[0].m_polygonCount = 1;
    tree->m_nodes[0].m_firstPolygon = 0;
    tree->m_nodePolygons = new unsigned int[1]{0};
    tree->m_polygons = new Polygon[1];
    tree->m_polygons[0].vertexCount = 4; tree->m_polygons[0].vertexOffset = 0;
    tree->m_polygons[0].m_material = material; tree->m_polygons[0].m_normal = {0, 1, 0};
    tree->m_vertices = new Vec3[4]{{offset - 3, 0, -3}, {offset - 3, 0, 3},
                                 {offset + 3, 0, 3}, {offset + 3, 0, -3}};
    tree->m_vertexIndices = new unsigned int[4]{0, 1, 2, 3};
    tree->m_neighbours = new unsigned int[4]{~0u, ~0u, ~0u, ~0u};
    return tree;
}

int main()
{
    const unsigned int invalid = std::numeric_limits<unsigned int>::max();
    MaterialManager materials;
    AIPathfinding::MapContainer map("recycling-regression", 0);
    assert(!map.isValidPosition({0, 0, 0}, &materials, invalid, false));
    assert(!map.isValidPosition({0, 0, 0}, &materials, 0u, false));
    map.m_quadTreeManager->m_quadTrees.push_back({square(0, 0, 1), nullptr});
    map.m_quadTreeManager->m_quadTrees.push_back({square(1, 20, 5), nullptr});
    assert(map.isValidPosition({0, 0, 0}, &materials, 0u, false));
    for (unsigned int index : {2u, 65535u, 0x80000000u, invalid})
    {
        assert(map.isValidPosition({0, 0, 0}, &materials, index, false));
        assert(!map.isValidPosition({40, 0, 0}, &materials, index, false));
        assert(!map.isValidPosition({20, 0, 0}, &materials, index, false));
        assert(map.isValidPosition({20, 0, 0}, &materials, index, true));
    }
    assert(!map.isValidPosition({20, 0, 0}, &materials, 0u, true)); // valid hint stays restricted
    assert(map.isValidPosition({20, 0, 0}, &materials, 1u, true));
    std::puts("PASS: invalid and empty island indices, actual polygon fallback, material rejection, valid-island scope");
}

// [TEMP] Compile the production host translation unit with its real image/ABI flags.
// Only these wrappers are retained by --gc-sections; no production body is replaced.
#ifndef BF2_SOLDIER_SOURCE
#define BF2_SOLDIER_SOURCE "../../BF2/Game/Objects/Game/Soldier.cpp"
#endif
#include BF2_SOLDIER_SOURCE
extern "C" void fixtureSetBones(void* geometry, const dice::hfe::Mat4* bones, int count)
{ dice::hfe::world::geometrySetBoneTransforms(geometry, bones, count); }
extern "C" void fixtureSetFov(void* geometry, float value)
{ dice::hfe::world::geometrySlot10(geometry, value); }
extern "C" void fixtureSetFirstPerson(void* geometry, bool value)
{ dice::hfe::world::geometrySlotD8(geometry, value); }
extern "C" void fixtureSetLod(void* geometry, int value)
{ dice::hfe::world::geometrySlot7C(geometry, value); }

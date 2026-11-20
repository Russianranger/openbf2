# Infantry crosshair while aiming

The Thor client hides the ordinary infantry crosshair while the current primary
weapon reports a nonzero zoom camera. This is an intentional handheld behavior
requested during Thor testing, not a claim of recovered retail behavior.

`HudAmmo::update` applies this only when the local player's vehicle is their default
soldier. `updateInfantryCrosshair` first runs the existing kit crosshair update,
then queries the current control object's primary `IWeaponObject::getZoom()`.
`GenericFireArm::getZoom()` reads the zoom component's current camera, so the check
works with latched zoom and does not depend on the held aim button or screen FOV.
It also avoids the cached ammo-display zoom flag, which can remain stale when an
ammo update returns early. The kit's normal visibility is recomputed each frame,
restoring its authored crosshair on zoom-out or weapon changes.

Stock rifle crosshair arms use `GuiIndex`, not `CrosshairIconShow`. The generic rifle
uses GUI 5 for hip fire and 52 for ADS; the M4 uses 4 and 53. Its GUI 53 includes the
optic dot. The helper compares the weapon HUD component's current authored GUI with
the HUD owner's cached index and calls the existing `updateGuiIndex` selection when
they differ. Timed weapon overrides and feedback animations are allowed to finish.
This refresh also runs when the separate icon visibility flag is false.

The helper additionally suppresses `CrosshairIconShow` for content that uses that
binding. Hit feedback, the weapon's alternate optic GUI, and vehicle crosshairs
keep their existing paths. The helper adds no fields or
virtual methods and changes no asset or network format. Other client platforms keep
the original behavior.

The existing `DefaultZoomComp::changeZoom` client WeaponHud notification frontier
remains. Polling the existing typed GUI selection covers stale zoom selection on
Android without reconstructing that event or inventing a new scope GUI.

Validation:

```sh
python3 Code/Tests/android_ads_crosshair_runtime.py --out /tmp/ads-host-check \
  --menu-archive /path/to/mods/bf2/menu_server.zip
ANDROID_NDK_HOME=/path/to/ndk bash Code/Tests/android_ads_crosshair_native.sh \
  /path/to/arm64-native-libraries /tmp/ads-arm64-check
```

The first check compiles the actual crosshair method bodies with narrow world
observers and tests zoom transitions, stale cached state, weapon changes, hidden
HUDs, hit feedback, the vehicle path, and the non-Android behavior. It does not
validate object ABI. Compile the complete `HudAmmo.cpp` with the normal NDK client
flags for that gate.

The optional stock archive check reads the shipped generic-rifle/M4 node gates and
confirms the intended crosshair, optic-dot and hit-marker visibility at their actual
GUI indices. Game assets are not copied into the source tree.

The native fixture links against `libmain.so`, uses real HUD/weapon/component
constructors, and checks the actual alternate optic GUI alongside crosshair
visibility. Run its `android-ads-crosshair` executable in an Android ARM64 runtime.
An optional third script argument allows a candidate `HudAmmo.o` to be linked ahead
of an older host library for focused development. Final artifact checks should omit
that argument and use the rebuilt host library. Neither fixture draws a rendered
scope or establishes physical-device gameplay acceptance.

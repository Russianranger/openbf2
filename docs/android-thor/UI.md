# Thor menu and HUD integration

This port applies [kr4uzi #50](https://github.com/kr4uzi/OpenBF2/pull/50) and its
[widescreen HUD prerequisites](https://github.com/kr4uzi/OpenBF2/issues/33).
The intentional policy is discussed in [#42](https://github.com/kr4uzi/OpenBF2/issues/42)
and [#45](https://github.com/kr4uzi/OpenBF2/issues/45).

Flash menus use a centered 4:3 drawable rectangle. Vanilla BF2's in-game HUD uses
the full display with its authored proportions; custom mods retain their own HUD
layout. Font outlines from imported SWFs provide sharper matching glyphs while
keeping the original metrics. Unmatched glyphs retain their shipped bitmap.

The engine, RendDX9 and SwiffPlayer must be rebuilt and packaged together. The
viewport origin, scale, font service and input conversion span these images.
Replacing just `libmain.so` while retaining old Stage 3 UI plugins is unsupported.
The named glyph service uses the engine's existing class-manager singleton registry;
no class-manager slots, network protocol or game-content formats are added.

Android touch and controller positions stay in full SDL window coordinates. The
SDL shim converts these to full engine drawable coordinates, then SwiffPlayer
converts them to the centered movie once. Input in black margins remains outside
Flash, including button release. Spawn and commander HUD cursors retain the full
drawable range. Do not apply the 4:3 offset in the Java overlay or controller mapper.

Content-free validation uses `bf2_hud_widescreen_check`, `bf2_menu_viewport_check`
and `android/thor/tests/MenuPointerMapperTest.java`. They establish policy and
coordinate boundaries, not rendered gameplay. Device acceptance must cover main,
pause and loading menus, the Join button, drag release in margins, text/caret
alignment, spawn/commander screens and fullscreen effects. Check a native and a
reduced launch resolution. Native renderer/Flash compilation and physical Thor
results must be reported separately.

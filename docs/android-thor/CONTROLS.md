# Playing with a controller

These defaults use the logical buttons Android reports. In the Thor's Xbox controller mode, they correspond to the Xbox names below. Gameplay button assignments send named BF2 actions through the normal game input processing. The sticks retain keyboard movement and relative mouse aiming.

| Control | Default gameplay action |
|---|---|
| Left stick | Walk or strafe |
| Right stick | Look or aim |
| Right trigger | Fire |
| Left trigger | Aim down sights |
| A | Jump |
| B tap | Crouch / stand; stand when already prone |
| B hold | Go prone, without crouching first |
| X | Reload |
| Y | Select weapon slot 3 |
| Left bumper | Sprint |
| Right bumper | Interact, enter or leave a vehicle |
| Start tap or gamepad Back | Open or close the game menu |
| Start hold | Open the Android controller menu |
| Select | Show scoreboard |
| Keyboard left Ctrl tap | Crouch / stand |
| Keyboard Z | Go prone |
| Touch Crouch | Same tap/hold behavior as B |

Use **Controller bindings** to assign a tap and optional hold action to a controller button,
D-pad direction, trigger, or physical keyboard key. Detection waits for the control to release
before opening its editor. Each physical control has one assignment; detecting an existing
control offers to edit it. Multiple controls can intentionally share an action without releasing
one another. **No action** disables that assignment; **Remove** lets an unassigned keyboard key
reach BF2's normal bindings again.

When a hold action is assigned, a release before 450 ms runs only the tap action; reaching 450 ms
runs only the hold action. Without a hold assignment, the action follows the physical press.
Ctrl is the exception: its tap runs on release so Ctrl shortcuts remain available. Keyboard
remaps stand aside for BF2 menus, console, chat and squad-name editing. Start's hold always
opens the Android controller menu so custom bindings cannot remove access to settings.

Available actions include weapon/equipment slots, vehicle seats, communication and tactical
roses, map controls, scoreboard, fire mode, kit handling and chat. The squad/commander action
opens BF2's contextual screen for the player's current role. Equipment and vehicle actions
still depend on the current kit, vehicle and game rules. Saved assignments apply when gameplay
resumes. Leaving the game window, opening settings, dying, changing soldier or entering/leaving
a vehicle clears held actions and crouch state.

In the BF2 main/pause menu and in-game screens that enable a pointer, the right stick moves a visible cursor and RT or A clicks. B sends Esc. This includes selecting a spawn location or kit; the left stick also moves the cursor when the right stick is idle. The cursor is available before touching the screen and stays visible with touch controls hidden. Switching between menus and gameplay releases held inputs; release RT before pressing it again to click or fire in the new screen. Gameplay movement uses digital directions with a 30% threshold. Looking uses at least a 16% stick deadzone and follows the configured mouse sensitivity.

In the Android launcher, offline map selector, career dialog and LAN browser, move the right stick to show the cursor. Press and release RT to activate an item. Hold RT and move the stick to drag a scrollable list. Android handles the system folder and file pickers.

A deliberate input from a second controller switches control to that device. Disconnecting the active pad or leaving the game window releases held buttons and stops stick motion. Touch controls and keyboard/mouse input remain available.

## Touch controls and the menu cursor

The launcher’s **Show touch controls** setting is saved as soon as it changes and also applies when resuming an active game. Turn it off for a clear controller-play view. This hides the virtual movement/buttons, keyboard button, Skip Intro, and touch markers. Touchscreen taps and drags still work in menus and spawn/kit selection screens. A connected controller’s menu cursor and the small **Game options** gear stay available independently of this setting.

Touch gameplay buttons appear only during gameplay, with subdued slate panels and sand outlines. Menu/score/confirm controls sit along the sides to leave the top HUD corners clear. Touch crouch uses the same tap/hold stance action as the controller; cancellation never toggles stance. Skip Intro appears only during a skippable intro clip. Log export lives in Game options, so no floating LOG button covers the game. Direct offline/LAN loading uses the map loading view regardless of the touch setting. Leaving the game window, cancelling a touch, hiding controls, or changing the native input phase releases held touch inputs immediately.

## Game options and returning to the launcher

Tap the 48 dp gear on the left edge, or hold **Start** on the controller. The panel pauses client frames and isolates game input while it is open. Use D-pad and A, right stick and RT, or touch to select **Return to game**, **Export logs**, or **Return to launcher**. B, Back, Esc or a fresh Start press closes the panel. The release of the Start press that opened it does not immediately close it, and a trigger held when opening cannot activate an item until released.

**Export logs** opens Android's document picker; returning from the picker leaves Game options open. **Return to launcher** preserves the isolated game process and its match. Resuming returns to that same session with the original launch arguments. The launcher's explicit **End match** action closes the session and terminates only the `:game` process after bringing the launcher forward, so a later deployment initializes fresh BF2/SDL state. A stale resume intent without launch arguments is also cleaned up rather than poisoning the next launch.

The focused overlay regression check runs with `python3 android/thor/tests/test_touch_overlay.py` and a Java JDK (`JAVA_HOME` is respected). It exercises the production overlay’s input and lifecycle logic with recorded SDL calls; it does not replace a physical-device rendering or controller check.

`run_gear_runtime.py` and `run_surface_runtime.py` in the same tests directory accept `--classes` (the APK's compiled Java class directory, including SDL), `--out`, and `--android-sdk`. They exercise actual Android 13 view/focus behavior and Activity callback ordering with Robolectric. JNI/SDL lifecycle and process termination are observed fixture boundaries, not a physical-device relaunch test.

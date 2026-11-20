# Playing with a controller

These bindings use the logical buttons Android reports. In the Thor's Xbox controller mode, they correspond to the Xbox button names below. The game applies the active BF2 keyboard and mouse bindings to these inputs.

| Controller | In a match | BF2 input |
|---|---|---|
| Left stick | Walk or strafe | W, A, S, D |
| Right stick | Look or aim | Relative mouse motion |
| Right trigger | Fire | Left mouse button |
| Left trigger | Aim down sights | Right mouse button |
| A | Jump | Space |
| B | Crouch | Left Ctrl |
| X | Reload | R |
| Y | Select weapon slot 3 | 3 |
| Left bumper | Sprint | Left Shift |
| Right bumper | Interact, enter or leave a vehicle | E |
| Start or gamepad Back | Open or close the menu | Esc |
| Select | Show scoreboard | Tab |

In the BF2 main/pause menu and in-game screens that enable a pointer, the right stick moves a visible cursor and RT or A clicks. B sends Esc. This includes selecting a spawn location or kit; the left stick also moves the cursor when the right stick is idle. The cursor is available before touching the screen and stays visible with touch controls hidden. Switching between menus and gameplay releases held inputs; release RT before pressing it again to click or fire in the new screen. Gameplay movement uses digital directions with a 30% threshold. Looking uses at least a 16% stick deadzone and follows the configured mouse sensitivity.

In the Android launcher, offline map selector, career dialog and LAN browser, move the right stick to show the cursor. Press and release RT to activate an item. Hold RT and move the stick to drag a scrollable list. Android handles the system folder and file pickers.

A deliberate input from a second controller switches control to that device. Disconnecting the active pad or leaving the game window releases held buttons and stops stick motion. Touch controls and keyboard/mouse input remain available.

## Touch controls and the menu cursor

The launcher’s **Show touch controls** setting is saved as soon as it changes and also applies when resuming an active game. Turn it off for a clear controller-play view. This hides the virtual movement/buttons, keyboard button, Skip Intro, LOG, and touch markers. Touchscreen taps and drags still work in menus and spawn/kit selection screens. A connected controller’s menu cursor stays visible independently of this setting, and touching the menu moves that cursor to the tapped position.

Touch gameplay buttons appear only during gameplay. Skip Intro appears only while the native player reports a skippable intro clip; LOG appears only in the main menu or intro. Neither covers a match or map loading. Direct offline/LAN loading uses the map loading view regardless of the touch setting. Leaving the game window, cancelling a touch, hiding controls, or changing the native input phase releases held touch inputs immediately.

The focused overlay regression check runs with `python3 android/thor/tests/test_touch_overlay.py` and a Java JDK (`JAVA_HOME` is respected). It exercises the production overlay’s input and lifecycle logic with recorded SDL calls; it does not replace a physical-device rendering or controller check.

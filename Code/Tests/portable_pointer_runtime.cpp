// [TEMP] Real SDL3 event processing and the portable client's Win32/DirectInput bridge.
// A dummy SDL window replaces Android's surface; no game or GPU is constructed.
#include "../BF2/PortShim/Win32OnSdl.cpp"
#include <cassert>
#include <cmath>

// Internal SDL entry points used by Android_OnMouse in the pinned SDL source.
extern "C" void SDL_SendMouseMotion(Uint64, SDL_Window*, SDL_MouseID, bool, float, float);
extern "C" void SDL_SendMouseButton(Uint64, SDL_Window*, SDL_MouseID, Uint8, bool);
extern "C" void SDL_SetMouseFocus(SDL_Window*);
#define SDL_DEFAULT_MOUSE_ID 1 // SDL_mouse_c.h: Android_OnMouse's internal device ID.

int main() {
    SDL_SetHint(SDL_HINT_VIDEO_DRIVER, "dummy");
    assert(SDL_Init(SDL_INIT_VIDEO | SDL_INIT_EVENTS));
    g_window = SDL_CreateWindow("pointer fixture", 1920, 1080, 0);
    assert(g_window);
    g_engineWidth = 1280; g_engineHeight = 720;
    SDL_SetMouseFocus(g_window);
    pumpSdl(); g_messages.clear();
    IDirectInputDevice8A mouse; mouse.kind = 2;
    mouse.Acquire();
    assert(SDL_GetWindowRelativeMouseMode(g_window));
    g_pointerSeen = false;
    SDL_SendMouseMotion(0, g_window, SDL_DEFAULT_MOUSE_ID, false, 200, 150);
    pumpSdl();
    assert(!g_pointerSeen); // Reproduces why menu analog movement was lost before the fix.

    portableSetPointerMode(true);
    assert(portablePointerMode() && !SDL_GetWindowRelativeMouseMode(g_window));
    mouse.Acquire();
    assert(!SDL_GetWindowRelativeMouseMode(g_window)); // Reacquisition must not break menus.
    SDL_SendMouseMotion(0, g_window, SDL_DEFAULT_MOUSE_ID, false, 1440, 810);
    pumpSdl();
    float x = 0, y = 0;
    assert(portablePointerPosition(&x, &y));
    assert(std::fabs(x - .75f) < .0001f && std::fabs(y - .75f) < .0001f);
    assert(!g_messages.empty() && g_messages.back().message == WM_MOUSEMOVE);
    assert(LOWORD(g_messages.back().lParam) == 960 && HIWORD(g_messages.back().lParam) == 540);

    g_mouseEvents.clear(); g_messages.clear();
    SDL_SendMouseButton(0, g_window, SDL_DEFAULT_MOUSE_ID, SDL_BUTTON_LEFT, true);
    SDL_SendMouseButton(0, g_window, SDL_DEFAULT_MOUSE_ID, SDL_BUTTON_LEFT, false);
    pumpSdl();
    assert(g_mouseEvents.size() == 2 && g_messages.size() == 2);
    assert(g_mouseEvents[0].dwOfs == DIMOFS_BUTTON0 && g_mouseEvents[0].dwData == 0x80);
    assert(g_mouseEvents[1].dwOfs == DIMOFS_BUTTON0 && g_mouseEvents[1].dwData == 0);
    assert(g_messages[0].message == WM_LBUTTONDOWN && g_messages[1].message == WM_LBUTTONUP);

    portableSetPointerMode(false);
    assert(!portablePointerMode() && SDL_GetWindowRelativeMouseMode(g_window));
    g_mouseEvents.clear();
    SDL_SendMouseMotion(0, g_window, SDL_DEFAULT_MOUSE_ID, true, 12, -7);
    pumpSdl();
    assert(g_mouseEvents.size() == 2);
    assert(g_mouseEvents[0].dwOfs == DIMOFS_X && (LONG)g_mouseEvents[0].dwData == 12);
    assert(g_mouseEvents[1].dwOfs == DIMOFS_Y && (LONG)g_mouseEvents[1].dwData == -7);
    mouse.Unacquire();
    assert(!SDL_GetWindowRelativeMouseMode(g_window));
    portableSetPointerMode(false);
    assert(!SDL_GetWindowRelativeMouseMode(g_window)); // Focus loss cannot reacquire the device.
    DestroyWindow(g_window); SDL_Quit();
    std::puts("portable pointer: absolute menu movement, RT click route and relative gameplay passed");
}

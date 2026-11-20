// [TEMP] Actual plugin methods with synthetic movie dimensions and production host objects.
// Access control is relaxed only in this fixture; no offsets or alternative object layouts are used.
#include <string>
#include <vector>
#include <map>
#include <dlfcn.h>
#include <cstdio>
#include "base/tu_file.h"
#include "gameswf.h"
#include "gameswf_render.h"
#include "SwiffPlayer/SwiffPlayerModule.h"
#define private public
#include "SwiffPlayer/SwiffPlayer.h"
#undef private

namespace {
dice::hfe::ui::SwiffModule* module;
gameswf::movie_definition* movie;
}
extern "C" bool swiff_start()
{
    module = new dice::hfe::ui::SwiffModule;
    return module->init();
}
extern "C" bool swiff_load(const char* path)
{
    tu_file file(path, "rb");
    if (file.get_error()) return false;
    movie = gameswf::create_movie_no_recurse(&file, gameswf::DO_NOT_LOAD_BITMAPS,
                                            gameswf::DO_LOAD_FONT_SHAPES);
    return movie != nullptr && movie->get_width_pixels() == 1024 && movie->get_height_pixels() == 768;
}
extern "C" void swiff_drop_movie()
{
    if (movie) { movie->drop_ref(); movie = nullptr; }
}
extern "C" bool swiff_pointer(void* host, int x, int y, int button, int expectedX, int expectedY)
{
    dice::hfe::ui::SwiffPlayer player;
    player.setSwiffHost(static_cast<dice::hfe::ui::ISwiffHost*>(host));
    player.movie_width = 1024; player.movie_height = 768;
    // No display/movie is needed to test the real host viewport call and pointer conversion.
    player.notifyMouse(x, y, button);
    const bool result = player.mouse_x == expectedX && player.mouse_y == expectedY
        && player.mouse_buttons == button;
    std::printf("notifyMouse(%d,%d,%d) = (%d,%d,%d) %s\n", x,y,button,
        player.mouse_x,player.mouse_y,player.mouse_buttons,result ? "PASS" : "FAIL");
    return result;
}
extern "C" bool swiff_stop()
{
    const bool result = module->close();
    delete module; module = nullptr;
    return result;
}

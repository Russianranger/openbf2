// [TEMP] Content-free checks of the production menu geometry and Flash pointer conversion.
// Does not create an Android window, renderer, or Flash movie.
#include "../BF2/Menu/MenuViewport.h"
#include <cstdio>
#include <limits>

using dice::hfe::ui::MenuViewport;
static unsigned checks, failures;
static void check(bool result, const char* message)
{
    ++checks;
    if (!result) { ++failures; std::printf("FAIL: %s\n", message); }
}

int main()
{
    // Native-size and reduced-resolution Thor launches, desktop baselines, portrait and resize.
    const unsigned cases[][6] = {
        {800,600,0,0,800,600}, {1024,768,0,0,1024,768},
        {1280,720,160,0,960,720}, {1920,1080,240,0,1440,1080},
        {2340,1080,450,0,1440,1080}, {2560,1080,560,0,1440,1080},
        {3840,2160,480,0,2880,2160}, {1080,1920,0,555,1080,810},
        {800,600,0,0,800,600}
    };
    for (const auto& row : cases) {
        const auto view = MenuViewport::fit(row[0], row[1]);
        check(view.x == row[2] && view.y == row[3] && view.width == row[4] &&
              view.height == row[5], "centered 4:3 drawable rectangle");
        int x, y;
        check(view.moviePoint(view.x, view.y, 1024, 768, x, y) && x == 0 && y == 0,
              "first drawable pixel reaches movie origin");
        check(view.moviePoint(view.x + view.width / 2, view.y + view.height / 2,
                             1024, 768, x, y) && x == 512 && y == 384,
              "touch/controller drawable center reaches movie center exactly once");
        check(view.moviePoint(view.x + view.width - 1, view.y + view.height - 1,
                             1024, 768, x, y) && x < 1024 && y < 768,
              "last in-bounds pixel remains within movie");
        check(!view.moviePoint(view.x + view.width, view.y, 1024, 768, x, y)
              && x == -32768 && y == -32768, "right margin releases outside Flash");
        check(!view.moviePoint(view.x, view.y + view.height, 1024, 768, x, y)
              && x == -32768 && y == -32768, "bottom margin releases outside Flash");
        check(!view.moviePoint(int(view.x) - 1, view.y, 1024, 768, x, y),
              "left margin is not clamped onto a menu control");
        check(!view.moviePoint(view.x, int(view.y) - 1, 1024, 768, x, y),
              "top margin is not clamped onto a menu control");
    }
    int x, y;
    check(!MenuViewport::fit(0,0).moviePoint(0,0,1024,768,x,y), "zero drawable has no input");
    check(!MenuViewport::fit(1,1).moviePoint(0,0,1024,768,x,y), "subpixel fit has no input");
    check(!MenuViewport::fit(1920,1080).moviePoint(960,540,0,768,x,y), "invalid movie has no input");
    const unsigned max = std::numeric_limits<unsigned>::max();
    const auto huge = MenuViewport::fit(max, max);
    check(huge.width == max && huge.height == (std::uint64_t(max) * 3 / 4),
          "fitting arithmetic does not overflow");
    std::printf("Menu viewport: %u/%u checks passed.\n", checks - failures, checks);
    return failures ? 1 : 0;
}

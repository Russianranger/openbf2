#pragma once

#include <cstdint>

namespace dice { namespace hfe { namespace ui {

// [CHOSEN, not recovered] The Flash menus occupy a centered 4:3 rectangle (#45).
// Coordinates and dimensions are in drawable pixels. The right and bottom edges are exclusive.
struct MenuViewport
{
    unsigned x, y, width, height;

    // [CHOSEN, not recovered] Issue #45: shared menu bounds for rendering and hit testing.
    // Out-of-bounds coordinates preserve Flash's release-outside behavior.
    bool moviePoint(int outputX, int outputY, int movieWidth, int movieHeight,
                    int& movieX, int& movieY) const
    {
        movieX = movieY = -32768;
        if (outputX < 0 || outputY < 0 || movieWidth <= 0 || movieHeight <= 0 ||
            static_cast<unsigned>(outputX) < x || static_cast<unsigned>(outputY) < y)
            return false;
        const unsigned localX = static_cast<unsigned>(outputX) - x;
        const unsigned localY = static_cast<unsigned>(outputY) - y;
        if (localX >= width || localY >= height)
            return false;
        movieX = static_cast<int>(double(localX) * movieWidth / width);
        movieY = static_cast<int>(double(localY) * movieHeight / height);
        return true;
    }

    static MenuViewport fit(unsigned outputWidth, unsigned outputHeight)
    {
        unsigned w = outputWidth;
        unsigned h = outputHeight;
        if (std::uint64_t(w) * 3 > std::uint64_t(h) * 4)
            w = static_cast<unsigned>(std::uint64_t(h) * 4 / 3);
        else
            h = static_cast<unsigned>(std::uint64_t(w) * 3 / 4);
        return {(outputWidth - w) / 2, (outputHeight - h) / 2, w, h};
    }
};

} } }

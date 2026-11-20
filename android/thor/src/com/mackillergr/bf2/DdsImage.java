package com.mackillergr.bf2;

import java.io.IOException;

/** Decodes the base image of stock BF2 DDS loading art, often named loadmap.png. */
final class DdsImage {
    final int width, height;
    final int[] pixels;
    private DdsImage(int width, int height) {
        this.width = width; this.height = height; pixels = new int[width * height];
    }
    static boolean matches(byte[] b) { return b.length >= 4 && word(b, 0) == 0x20534444; }
    private static int word(byte[] b, int p) {
        return (b[p] & 255) | (b[p+1] & 255) << 8 | (b[p+2] & 255) << 16 | (b[p+3] & 255) << 24;
    }
    private static int shortWord(byte[] b, int p) { return (b[p] & 255) | (b[p+1] & 255) << 8; }
    private static int rgb565(int v) {
        int r = v >> 11, g = (v >> 5) & 63, b = v & 31;
        return 0xff000000 | ((r << 3) | (r >> 2)) << 16 | ((g << 2) | (g >> 4)) << 8 | (b << 3) | (b >> 2);
    }
    private static int mix(int a, int b, int wa, int wb, int divisor) {
        int value = 0xff000000;
        for (int shift = 0; shift <= 16; shift += 8)
            value |= ((((a >>> shift) & 255) * wa + ((b >>> shift) & 255) * wb) / divisor) << shift;
        return value;
    }
    // DXT1/3/5 formats: https://learn.microsoft.com/windows/win32/direct3d9/compressed-texture-formats
    static DdsImage decode(byte[] bytes) throws IOException {
        if (bytes.length < 128 || !matches(bytes) || word(bytes, 4) != 124 || word(bytes, 76) != 32)
            throw new IOException("Invalid DDS header");
        int w = word(bytes, 16), h = word(bytes, 12), format = word(bytes, 84);
        boolean dxt1 = format == 0x31545844, dxt3 = format == 0x33545844, dxt5 = format == 0x35545844;
        if (!dxt1 && !dxt3 && !dxt5) throw new IOException("Unsupported DDS loading image format");
        if (w <= 0 || h <= 0 || w > 4096 || h > 4096 || (long)w * h > 4194304)
            throw new IOException("Unsupported DDS loading image size");
        int bw = (w + 3) / 4, bh = (h + 3) / 4, stride = dxt1 ? 8 : 16;
        if (128L + (long)bw * bh * stride > bytes.length) throw new IOException("Truncated DDS loading image");
        DdsImage image = new DdsImage(w, h);
        int[] colors = new int[4], alphas = new int[8];
        for (int by = 0, p = 128; by < bh; by++) for (int bx = 0; bx < bw; bx++, p += stride) {
            int cp = p + (dxt1 ? 0 : 8), c0 = shortWord(bytes, cp), c1 = shortWord(bytes, cp + 2);
            colors[0] = rgb565(c0); colors[1] = rgb565(c1);
            if (!dxt1 || c0 > c1) {
                colors[2] = mix(colors[0], colors[1], 2, 1, 3);
                colors[3] = mix(colors[0], colors[1], 1, 2, 3);
            } else {
                colors[2] = mix(colors[0], colors[1], 1, 1, 2); colors[3] = 0;
            }
            int selectors = word(bytes, cp + 4);
            long alphaBits = 0;
            if (dxt3) for (int i = 0; i < 8; i++) alphaBits |= (long)(bytes[p+i] & 255) << (8*i);
            if (dxt5) {
                alphas[0] = bytes[p] & 255; alphas[1] = bytes[p+1] & 255;
                int count = alphas[0] > alphas[1] ? 7 : 5;
                for (int i = 2; i <= count; i++)
                    alphas[i] = ((count + 1 - i) * alphas[0] + (i - 1) * alphas[1]) / count;
                if (count == 5) { alphas[6] = 0; alphas[7] = 255; }
                for (int i = 0; i < 6; i++) alphaBits |= (long)(bytes[p+2+i] & 255) << (8*i);
            }
            for (int i = 0; i < 16; i++) {
                int x = bx*4 + i%4, y = by*4 + i/4;
                if (x >= w || y >= h) continue;
                int color = colors[(selectors >>> (2*i)) & 3];
                if (dxt3) color = (color & 0xffffff) | (int)(((alphaBits >>> (4*i)) & 15) * 17) << 24;
                if (dxt5) color = (color & 0xffffff) | alphas[(int)((alphaBits >>> (3*i)) & 7)] << 24;
                image.pixels[y*w+x] = color;
            }
        }
        return image;
    }
}

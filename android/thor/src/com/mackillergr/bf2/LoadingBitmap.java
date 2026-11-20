package com.mackillergr.bf2;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

/** The map preview and loading screen share BF2's DDS-in-PNG decoding path. */
final class LoadingBitmap {
    private LoadingBitmap() {}
    static Bitmap read(File image) throws IOException {
        if (image != null && image.length() <= 16 * 1024 * 1024) {
            byte[] bytes = Files.readAllBytes(image.toPath());
            if (DdsImage.matches(bytes)) {
                DdsImage dds = DdsImage.decode(bytes);
                return Bitmap.createBitmap(dds.pixels, dds.width, dds.height, Bitmap.Config.ARGB_8888);
            } else {
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inJustDecodeBounds = true;
                BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
                if (options.outWidth > 0 && options.outHeight > 0) {
                    options.inSampleSize = 1;
                    while (options.outWidth / options.inSampleSize > 2048 || options.outHeight / options.inSampleSize > 2048)
                        options.inSampleSize *= 2;
                    options.inJustDecodeBounds = false;
                    return BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
                }
            }
        }
        return null;
    }
}

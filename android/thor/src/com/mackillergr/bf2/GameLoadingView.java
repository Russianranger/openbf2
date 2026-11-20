package com.mackillergr.bf2;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

/** Android loading presentation for the direct-start path that bypasses the Flash frontend. */
public final class GameLoadingView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final android.os.Handler ui = new android.os.Handler(android.os.Looper.getMainLooper());
    private File mod;
    private String map = "";
    private Bitmap artwork;
    private boolean direct = true;
    private int snapshot = AndroidUiState.STARTING, generation;
    private boolean active, closed, artworkDirty;
    private final Runnable poll = new Runnable() {
        @Override public void run() {
            if (!active || closed || !direct) return;
            int next = AndroidUiState.snapshot();
            updateState(next, AndroidUiState.isLoading(next) ? AndroidUiState.level() : "");
            postDelayed(this, AndroidUiState.isLoading(next) ? 100 : 250);
        }
    };
    public GameLoadingView(Context context, String[] args) {
        super(context);
        for (String argument : args) if ("--menu".equals(argument)) direct = false;
        for (int i = 0; i+1 < args.length; i++) {
            if ("--mod".equals(args[i])) mod = new File(args[++i]);
            else if ("--level".equals(args[i])) map = LoadingArt.levelName(args[++i]);
        }
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        loadArtwork();
    }
    public void setActive(boolean value) {
        active = value; removeCallbacks(poll);
        if (active && !closed && direct) post(poll);
    }
    public void close() {
        closed = true; generation++; setActive(false);
        if (artwork != null) { artwork.recycle(); artwork = null; }
    }
    void updateState(int next, String level) {
        boolean wasVisible = direct && AndroidUiState.isLoading(snapshot);
        boolean visible = direct && AndroidUiState.isLoading(next);
        boolean changed = wasVisible != visible || visible &&
                AndroidUiState.progress(snapshot) != AndroidUiState.progress(next);
        snapshot = next;
        if (visible) {
            String name = LoadingArt.levelName(level);
            if (!name.isEmpty() && !name.equals(map)) {
                map = name; loadArtwork(); changed = true;
            }
        }
        if (changed || visible && artworkDirty) {
            artworkDirty = false;
            invalidate();
        }
    }
    private void loadArtwork() {
        if (!direct || closed) return;
        final int request = ++generation;
        final File folder = mod;
        final String level = map;
        if (artwork != null) { artwork.recycle(); artwork = null; }
        if (folder == null || level.isEmpty()) return;
        new Thread(() -> {
            Bitmap decoded = null;
            try {
                File image = LoadingArt.find(folder, level);
                if (image != null && image.length() <= 16 * 1024 * 1024) {
                    byte[] bytes = Files.readAllBytes(image.toPath());
                    if (DdsImage.matches(bytes)) {
                        DdsImage dds = DdsImage.decode(bytes);
                        decoded = Bitmap.createBitmap(dds.pixels, dds.width, dds.height, Bitmap.Config.ARGB_8888);
                    } else {
                        BitmapFactory.Options options = new BitmapFactory.Options();
                        options.inJustDecodeBounds = true;
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
                        if (options.outWidth > 0 && options.outHeight > 0) {
                            options.inSampleSize = 1;
                            while (options.outWidth / options.inSampleSize > 2048 || options.outHeight / options.inSampleSize > 2048)
                                options.inSampleSize *= 2;
                            options.inJustDecodeBounds = false;
                            decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
                        }
                    }
                }
            } catch (IOException | RuntimeException failure) {
                android.util.Log.w("BF2Loading", "Loading artwork unavailable: " + failure.getMessage());
            }
            final Bitmap ready = decoded;
            ui.post(() -> {
                if (closed || request != generation) { if (ready != null) ready.recycle(); return; }
                if (ready == null) return;
                artwork = ready; artworkDirty = true;
                if (active && direct && AndroidUiState.isLoading(snapshot)) {
                    artworkDirty = false; invalidate();
                }
            });
        }, "BF2-loading-art").start();
    }
    @Override protected void onDraw(Canvas canvas) {
        if (!direct || !AndroidUiState.isLoading(snapshot)) return;
        int width = getWidth(), height = getHeight();
        canvas.drawColor(Color.BLACK);
        if (artwork != null) {
            float scale = Math.min((float)width / artwork.getWidth(), (float)height / artwork.getHeight());
            float w = artwork.getWidth()*scale, h = artwork.getHeight()*scale;
            paint.setColor(Color.WHITE);
            canvas.drawBitmap(artwork, null, new RectF((width-w)/2, (height-h)/2, (width+w)/2, (height+h)/2), paint);
        }
        paint.setColor(Color.argb(220, 10, 14, 18));
        canvas.drawRect(0, height*.78f, width, height, paint);
        paint.setTextAlign(Paint.Align.LEFT); paint.setColor(Color.WHITE);
        paint.setTextSize(Math.max(18, height*.035f));
        String title = map.isEmpty() ? "Starting Battlefield 2" : "Loading " + map.replace('_', ' ');
        canvas.drawText(title, width*.07f, height*.855f, paint);
        int progress = AndroidUiState.progress(snapshot);
        paint.setTextSize(Math.max(14, height*.024f));
        canvas.drawText(progress < 0 ? "Preparing…" : progress + "%", width*.07f, height*.905f, paint);
        paint.setColor(Color.rgb(55, 60, 64));
        canvas.drawRect(width*.07f, height*.94f, width*.93f, height*.955f, paint);
        if (progress >= 0) {
            paint.setColor(Color.rgb(218, 181, 69));
            canvas.drawRect(width*.07f, height*.94f, width*(.07f+.86f*progress/100f), height*.955f, paint);
        }
    }
    @Override public boolean onTouchEvent(MotionEvent event) { return direct && AndroidUiState.isLoading(snapshot); }
}

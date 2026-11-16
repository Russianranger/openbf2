package com.mackillergr.bf2;

import android.content.Context;
import android.util.Log;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileLock;

/** The operating system releases this lock automatically when the isolated SDL process exits. */
final class GameSession {
    private static RandomAccessFile owner;
    private static FileLock lock;
    private GameSession() {}

    private static RandomAccessFile open(Context context) throws IOException {
        return new RandomAccessFile(new File(context.getFilesDir(), "game-session.lock"), "rw");
    }
    static synchronized void hold(Context context) {
        if (owner != null) return;
        try {
            owner = open(context);
            lock = owner.getChannel().tryLock();
            if (lock == null) release();
        } catch (IOException e) { release(); Log.e("OpenBF2", "Unable to mark game session", e); }
    }
    static synchronized void release() {
        try { if (lock != null) lock.release(); }
        catch (IOException e) { Log.w("OpenBF2", "Unable to release session lock", e); }
        finally {
            try { if (owner != null) owner.close(); }
            catch (IOException e) { Log.w("OpenBF2", "Unable to close session lock", e); }
            owner = null; lock = null;
        }
    }
    static boolean isActive(Context context) throws IOException {
        try (RandomAccessFile probe = open(context); FileLock temp = probe.getChannel().tryLock()) {
            return temp == null;
        }
    }
}

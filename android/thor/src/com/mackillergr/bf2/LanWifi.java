package com.mackillergr.bf2;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.util.Log;

/** Wi-Fi filtering can discard LAN broadcasts unless the app is actively listening. */
final class LanWifi {
    private LanWifi() {}
    static WifiManager.MulticastLock acquire(Context context) {
        WifiManager wifi = context.getApplicationContext().getSystemService(WifiManager.class);
        if (wifi == null) return null;
        try {
            WifiManager.MulticastLock lock = wifi.createMulticastLock("openbf2:lan");
            lock.setReferenceCounted(false);
            lock.acquire();
            return lock;
        } catch (SecurityException e) {
            Log.w("OpenBF2", "Wi-Fi discovery unavailable", e);
            return null;
        }
    }
    static void release(WifiManager.MulticastLock lock) {
        if (lock != null && lock.isHeld()) lock.release();
    }
}

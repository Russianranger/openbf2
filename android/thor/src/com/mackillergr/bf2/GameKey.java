package com.mackillergr.bf2;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.AtomicFile;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.Arrays;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;

/** Stores the owner's optional LAN key under the application UID. */
final class GameKey {
    private static final String ALIAS = "openbf2-game-key";
    private GameKey() {}
    private static AtomicFile file(Context c) { return new AtomicFile(new File(c.getFilesDir(), "bf2-key.enc")); }
    static boolean saved(Context c) { return file(c).getBaseFile().isFile(); }
    private static SecretKey key(boolean create) throws GeneralSecurityException, IOException {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        SecretKey key = (SecretKey) store.getKey(ALIAS, null);
        if (key != null) return key;
        if (!create) throw new GeneralSecurityException("Stored key unavailable");
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
        return generator.generateKey();
    }
    static void save(Context context, String entered) throws IOException {
        String value = entered.replace("-", "").trim();
        if (!value.matches("[A-Za-z0-9]{12,32}"))
            throw new IOException("Check the Battlefield 2 product key.");
        AtomicFile target = file(context);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key(true));
            byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.US_ASCII));
            FileOutputStream stream = target.startWrite();
            try {
                stream.write(cipher.getIV().length); stream.write(cipher.getIV()); stream.write(encrypted);
                target.finishWrite(stream);
            } catch (IOException e) { target.failWrite(stream); throw e; }
        } catch (GeneralSecurityException e) { throw new IOException("Could not store the product key.", e); }
    }
    static String read(Context context) throws IOException {
        if (!saved(context)) return "";
        try {
            byte[] data = file(context).readFully();
            if (data.length < 30 || data.length > 128 || data[0] != 12)
                throw new IOException("Re-enter the Battlefield 2 product key.");
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(false), new GCMParameterSpec(128, data, 1, 12));
            return new String(cipher.doFinal(data, 13, data.length - 13), StandardCharsets.US_ASCII);
        } catch (GeneralSecurityException e) { throw new IOException("Re-enter the Battlefield 2 product key.", e); }
    }
    static String[] append(Context context, String[] src) {
        for (String option : src) if ("--coop".equals(option)) return src;
        try {
            String value = read(context);
            if (!value.isEmpty()) {
                String[] args = Arrays.copyOf(src, src.length + 2);
                args[src.length] = "--cdkey"; args[src.length+1] = value; return args;
            }
        } catch (IOException ignored) { /* The launcher checks the owner's key before a LAN join. */ }
        return src;
    }
}

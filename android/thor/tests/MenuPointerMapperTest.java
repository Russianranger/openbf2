package com.mackillergr.bf2;

/** Checks the production Java-to-SDL contract. Flash owns the later 4:3 conversion. */
public final class MenuPointerMapperTest {
    private static void same(float actual, float expected) {
        if (Math.abs(actual - expected) > .01f)
            throw new AssertionError(actual + " != " + expected);
    }
    public static void main(String[] args) {
        int[][] sizes = {{1920,1080,1920,1080}, {2340,1080,2340,1080},
                {1920,1080,1280,720}, {2340,1080,1280,720}};
        for (int[] s : sizes) {
            float[] middle = MenuPointerMapper.map(s[0]/2f, s[1]/2f, s[0],s[1],s[2],s[3]);
            same(middle[0], s[0]/2f); same(middle[1], s[1]/2f);
            // The SDL shim maps this to game pixels; only SwiffPlayer removes menu margins.
            same(middle[0] * s[2]/s[0], s[2]/2f);
            same(middle[1] * s[3]/s[1], s[3]/2f);
        }
        // Native 16:9 menu left edge is game X=240. Java must not apply another offset.
        float[] left = MenuPointerMapper.map(240,0,1920,1080,1920,1080);
        same(left[0],240); same(left[1],0);
        float[] margin = MenuPointerMapper.map(239,100,1920,1080,1920,1080);
        same(margin[0],239); same(margin[1],100);
        System.out.println("Menu pointer mapping checks passed");
    }
}

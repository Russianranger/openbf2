package com.mackillergr.bf2;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;

/** [TEMP] Synthetic DXT blocks and optional owner-provided stock image; no game art is bundled. */
public final class LoadingArtTest {
    private static void equal(int expected, int actual) {
        if (expected != actual) throw new AssertionError(Integer.toHexString(actual)+" != "+Integer.toHexString(expected));
    }
    private static void word(byte[] b, int p, int value) {
        for (int i=0;i<4;i++) b[p+i]=(byte)(value >>> (8*i));
    }
    private static byte[] block(char format) {
        byte[] b=new byte[format=='1'?136:144];
        word(b,0,0x20534444);word(b,4,124);word(b,12,4);word(b,16,4);word(b,76,32);
        word(b,84,0x00545844 | (format<<24));
        return b;
    }
    public static void main(String[] args) throws Exception {
        byte[] b=block('1'); word(b,128,0x001ff800); word(b,132,0xe4e4e4e4);
        DdsImage image=DdsImage.decode(b);
        equal(0xffff0000,image.pixels[0]);equal(0xff0000ff,image.pixels[1]);
        equal(0xffaa0055,image.pixels[2]);equal(0xff5500aa,image.pixels[3]);
        word(b,128,0xffff0000);word(b,132,-1);word(b,12,2);word(b,16,3);
        image=DdsImage.decode(b);equal(6,image.pixels.length);
        for(int pixel:image.pixels) equal(0,pixel);
        b=block('3');word(b,136,0xffff0000);word(b,140,-1);
        for(int i=0;i<8;i++) b[128+i]=(byte)((2*i)|((2*i+1)<<4));
        image=DdsImage.decode(b);
        equal(0x00aaaaaa,image.pixels[0]);equal(0xffaaaaaa,image.pixels[15]);
        for(int low:new int[]{100,220}) {
            b=block('5');b[128]=(byte)200;b[129]=(byte)low;word(b,136,0x001ff800);
            long indices=0;for(int i=0;i<16;i++) indices|=(long)(i%8)<<(3*i);
            for(int i=0;i<6;i++) b[130+i]=(byte)(indices>>>(8*i));
            image=DdsImage.decode(b);
            int[] expected=low==100?new int[]{200,100,185,171,157,142,128,114}:new int[]{200,220,204,208,212,216,0,255};
            for(int i=0;i<16;i++) equal(expected[i%8],image.pixels[i]>>>24);
        }
        for(byte[] broken:new byte[][]{new byte[0],Arrays.copyOf(b,143)}) {
            try { DdsImage.decode(broken); throw new AssertionError("Accepted truncated DDS"); }
            catch(IOException expected) {}
        }
        word(b,16,Integer.MAX_VALUE);
        try { DdsImage.decode(b);throw new AssertionError("Accepted oversized DDS"); } catch(IOException expected) {}
        if(DdsImage.matches(new byte[]{(byte)137,80,78,71})) throw new AssertionError("PNG misidentified");
        File mod=Files.createTempDirectory("loading-art-").toFile();
        File info=new File(mod,"LeVeLs/Gulf_of_Oman/Info");if(!info.mkdirs()) throw new AssertionError();
        File fallback=new File(info,"LoadMap_16.PNG"),exact=new File(info,"LOADMAP.PNG");
        Files.write(fallback.toPath(),new byte[]{1});
        if(!fallback.equals(LoadingArt.find(mod,"levels/gulf_of_oman/"))) throw new AssertionError("Case-insensitive art selection");
        Files.write(exact.toPath(),new byte[]{1});
        if(!exact.equals(LoadingArt.find(mod,"gulf_of_oman"))) throw new AssertionError("Exact art priority");
        if(LoadingArt.find(mod,"..")!=null || LoadingArt.find(mod,"missing")!=null) throw new AssertionError("Invalid map");
        exact.delete();fallback.delete();info.delete();info.getParentFile().delete();info.getParentFile().getParentFile().delete();mod.delete();
        if(args.length==2) {
            image=DdsImage.decode(Files.readAllBytes(new File(args[0]).toPath()));
            try(DataOutputStream out=new DataOutputStream(new FileOutputStream(args[1]))) {
                for(int pixel:image.pixels) {out.writeByte(pixel>>>16);out.writeByte(pixel>>>8);out.writeByte(pixel);out.writeByte(pixel>>>24);}
            }
            System.out.println("Stock loading image decoded: "+image.width+"x"+image.height);
        }
        equal(AndroidUiState.INTRO,AndroidUiState.phase(2|256));
        if(!AndroidUiState.canSkipIntro(2|256) || AndroidUiState.canSkipIntro(4|256)) throw new AssertionError("Intro state");
        equal(-1,AndroidUiState.progress(1));equal(42,AndroidUiState.progress(1|(43<<16)));
        System.out.println("DDS DXT1/3/5, PNG magic, malformed data, map selection and state checks passed");
    }
}

package android.content.pm;

import android.content.Context;

import java.io.File;

public class ApplicationInfo {
    public String nativeLibraryDir;

    public ApplicationInfo(File ctx) {
       this.nativeLibraryDir = new File(ctx, "lib").getAbsolutePath();

        // XXX - unpack from JARs into temp dir

        //File nativeLibDir = new File("lib\\src\\main\\cpp\\vc\\x64\\Debug");
        //this.nativeLibraryDir  = nativeLibDir.getAbsolutePath();
    }
}

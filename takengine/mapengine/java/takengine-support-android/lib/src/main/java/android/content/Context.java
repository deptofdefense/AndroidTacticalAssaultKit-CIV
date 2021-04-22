package android.content;

import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.content.res.Resources;

import java.io.File;
import java.io.IOException;

public class Context {

    static Runtime rt;

    public Context() {
        synchronized(Context.class) {
            if(rt == null)
                rt = new Runtime();
        }
    }

    public Resources getResources() {
        return rt.resources;
    }

    public String getPackageName() {
        return rt.packageName;
    }
    public File getFilesDir() {
        return rt.filesDir;
    }
    public File getDatabasePath(String name) {
        return new File(getFilesDir(), name);
    }
    public AssetManager getAssets() {
        return rt.resources.getAssets();
    }
    public File getCacheDir() {
        return rt.cacheDir;
    }
    public File[] getExternalCacheDirs() {
        return new File[] {getCacheDir()};
    }
    public PackageManager getPackageManager() {
        return new PackageManager(getPackageName(), rt.filesDir);
    }
    public ContentResolver getContentResolver() {
        return new ContentResolver(rt);
    }

    static class Runtime {
        Resources resources;
        String packageName;

        File cacheDir;
        File filesDir;

        public Runtime() {
            this.packageName = "com.atakmap.app";
            this.resources = new Resources();

            try {
                this.cacheDir = File.createTempFile(packageName, "");
            } catch(IOException e) {
                throw new RuntimeException();
            }

            this.cacheDir.delete();
            this.cacheDir.mkdirs();

            this.filesDir = new File(this.cacheDir, "files");
            this.filesDir.mkdirs();
        }
    }
}

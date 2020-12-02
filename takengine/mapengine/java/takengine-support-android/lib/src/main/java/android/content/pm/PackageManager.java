package android.content.pm;

import android.content.Context;

import java.io.File;

public final class PackageManager {
    public static class NameNotFoundException extends Exception {}

    private final String packageName;
    private final File filesDir;
    public PackageManager(String pkgName, File filesDir) {
        this.packageName = pkgName;
        this.filesDir = filesDir;
    }
    public ApplicationInfo getApplicationInfo(String name, int i) throws NameNotFoundException {
        if(!name.equals(packageName))
            throw new NameNotFoundException();
        return new ApplicationInfo(this.filesDir);
    }
}

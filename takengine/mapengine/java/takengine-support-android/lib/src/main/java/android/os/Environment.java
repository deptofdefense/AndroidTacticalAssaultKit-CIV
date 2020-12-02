package android.os;

import java.io.File;

public final class Environment {
    private Environment() {}

    public static File getExternalStorageDirectory() {
        return new File(System.getProperty("java.io.tmpdir"));
    }
}

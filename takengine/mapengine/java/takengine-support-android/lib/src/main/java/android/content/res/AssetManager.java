package android.content.res;

import android.content.Context;

import java.io.IOException;
import java.io.InputStream;

public final class AssetManager {
    public InputStream open(String path) throws IOException {
        return AssetManager.class.getResourceAsStream("/assets/" + path);
    }
    public String[] list(String assetDir) throws IOException {
        throw new UnsupportedOperationException();
    }
}

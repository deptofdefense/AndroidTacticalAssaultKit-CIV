package gov.tak.platform.commons.resources;

import android.content.Context;
import android.content.res.AssetManager;

import java.io.IOException;
import java.io.InputStream;

import gov.tak.api.commons.resources.IAssetManager;

abstract class AssetManagerBase implements IAssetManager {
    final AssetManager assets;

    AssetManagerBase(Context ctx) {
        this(ctx.getResources().getAssets());
    }

    AssetManagerBase(AssetManager mgr) {
        assets = mgr;
    }

    @Override
    public final InputStream open(String path) throws IOException {
        return assets.open(path);
    }
}

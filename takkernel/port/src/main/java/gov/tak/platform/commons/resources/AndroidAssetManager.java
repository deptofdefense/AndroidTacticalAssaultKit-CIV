package gov.tak.platform.commons.resources;

import android.content.Context;
import android.content.res.AssetManager;

public final class AndroidAssetManager extends AssetManagerBase {

    public AndroidAssetManager(Context ctx) {
        super(ctx);
    }

    public AndroidAssetManager(AssetManager assets) {
        super(assets);
    }
}

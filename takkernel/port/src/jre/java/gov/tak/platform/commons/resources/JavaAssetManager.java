package gov.tak.platform.commons.resources;

import android.content.Context;
import android.content.res.AssetManager;

public final class JavaAssetManager extends AssetManagerBase {

    public JavaAssetManager() {
        super(new Context());
    }
}

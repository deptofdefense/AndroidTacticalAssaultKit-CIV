package com.atakmap.map.layer.model.assimp;

import com.atakmap.io.ZipVirtualFile;
import com.atakmap.map.layer.model.ModelInfo;
import com.atakmap.map.layer.model.ModelInfoSpi;
import com.atakmap.coremap.log.Log;

import java.io.File;
import java.util.Collections;
import java.util.Set;

import jassimp.AiScene;
import jassimp.Jassimp;

public class AssimpModelInfoSpi implements ModelInfoSpi {

    public final static String TAG = "AssimpModelInfoSpi";

    public final static ModelInfoSpi INSTANCE = new AssimpModelInfoSpi();
    public final static String TYPE = "ASSIMP";

    @Override
    public String getName() {
        return TYPE;
    }

    @Override
    public int getPriority() {
        return 1;
    }

    @Override
    public boolean isSupported(String uri) {
        return true;
    }

    @Override
    public Set<ModelInfo> create(String object) {
        try {
            File file = new File(object);
            if (ATAKAiIOSystem.isZipFile(file)) {
                file = new ZipVirtualFile(object);
            }
            file = ATAKAiIOSystem.findObj(file);

            if (file== null)
                 return null;

            AiScene scene = Jassimp.importFile(file.getPath(), ATAKAiIOSystem.INSTANCE);
            ModelInfo model = new ModelInfo();
            model.uri = file.getAbsolutePath();
            model.type = TYPE;
            model.name = file.getName();
            return Collections.singleton(model);
        } catch (Exception e) {
            Log.e(TAG, "error", e);
        }
        return null;
    }
}

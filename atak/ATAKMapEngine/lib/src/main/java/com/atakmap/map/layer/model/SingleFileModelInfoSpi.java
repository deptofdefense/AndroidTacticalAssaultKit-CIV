package com.atakmap.map.layer.model;

import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;
import com.atakmap.io.ZipVirtualFile;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SingleFileModelInfoSpi implements ModelInfoSpi {

    private static String TAG = "SingleFileModelInfoSpi";
    private String name;
    private String modelInfoType;
    private String ext;

    public SingleFileModelInfoSpi(String name, int priority, String modelInfoType, String ext) {
        this.modelInfoType = modelInfoType;
        this.name = name;
        this.ext = ext;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public int getPriority() {
        return 1;
    }

    @Override
    public boolean isSupported(String path) {
        return create(path) != null;
    }

    public Set<ModelInfo> create(List<File> plyFiles) throws IOException {
        Set<ModelInfo> retval = new HashSet<>();
        for (File plyFile : plyFiles) {
            ModelInfo info = new ModelInfo();
            info.name = plyFile.getName();
            info.uri = plyFile.getAbsolutePath();
            info.type = modelInfoType;
            retval.add(info);
        }
        return retval.isEmpty() ? null : retval;
    }

    @Override
    public Set<ModelInfo> create(String path) {
        try {
            File file = new File(path);
            if (FileSystemUtils.checkExtension(file, "zip")) {
                ZipVirtualFile zf = new ZipVirtualFile(path);
                List<File> plyFiles = ModelFileUtils.findFiles(zf, Collections.singleton(this.ext));
                return create(plyFiles);
            } else if (FileSystemUtils.checkExtension(file, this.ext)) {
                return create(Collections.singletonList(file));
            }
        } catch (IllegalArgumentException | IOException e) {
            Log.e(TAG + "-" + this.name, "error", e);
        }

        return null;
    }
}

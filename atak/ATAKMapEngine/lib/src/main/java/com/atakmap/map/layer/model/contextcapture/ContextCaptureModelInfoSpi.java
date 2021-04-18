package com.atakmap.map.layer.model.contextcapture;

import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;
import com.atakmap.io.ZipVirtualFile;
import com.atakmap.map.layer.model.ModelInfo;
import com.atakmap.map.layer.model.ModelInfoSpi;
import com.atakmap.map.layer.model.obj.ObjUtils;

import java.io.File;
import java.util.Collections;
import java.util.Set;

public final class ContextCaptureModelInfoSpi implements ModelInfoSpi {
    public final static ModelInfoSpi INSTANCE = new ContextCaptureModelInfoSpi();

    private ContextCaptureModelInfoSpi() {}

    @Override
    public String getName() {
        return "ContextCapture";
    }

    @Override
    public int getPriority() {
        return 2;
    }

    @Override
    public boolean isSupported(String path) {
        long s1 = System.currentTimeMillis();
        boolean r1 = isSupported1(path);
        long e1 = System.currentTimeMillis();

        Log.i("ContextCaptureModelInfoSpi", "isSupported in " + (e1-s1));
        return r1;
    }
    private boolean isSupported1(String path) {
        File f = new File(path);
        if(FileSystemUtils.isZipPath(path)) {
            try {
                File entry = ObjUtils.findObj(new ZipVirtualFile(f));
                if (entry == null)
                    return false;
                return ContextCaptureGeoreferencer.locateMetadataFile(entry.getAbsolutePath()) != null;
            } catch (IllegalArgumentException ignored) {}
        }

        return false;
    }

    @Override
    public Set<ModelInfo> create(String path) {
        File f = new File(path);
        if(FileSystemUtils.isZipPath(path)) {
            try {
                ZipVirtualFile.mountArchive(f);
                try {
                    File entry = ObjUtils.findObj(new ZipVirtualFile(f));
                    if (entry == null)
                        return null;
                    final ModelInfo retval = new ModelInfo();
                    retval.type = "ContextCapture";
                    retval.uri = entry.getAbsolutePath();
                    retval.name = ContextCaptureGeoreferencer.getDatasetName(retval.uri);
                    retval.maxDisplayResolution = 0d;
                    // XXX - can derive real resolution for LOD datasets
                    // XXX - can estimate real resolution for non-LOD datasets from single tile???
                    retval.minDisplayResolution = 5d;
                    retval.altitudeMode = ModelInfo.AltitudeMode.Absolute;
                    if(!ContextCaptureGeoreferencer.INSTANCE.locate(retval))
                        return null;
                    retval.uri = f.getAbsolutePath();
                    return Collections.singleton(retval);
                } catch (IllegalArgumentException ignored) {
                } finally {
                    ZipVirtualFile.unmountArchive(f);
                }
            } catch(Throwable ignored) {}
        }

        return null;
    }
}

package com.atakmap.map.layer.model.pix4d;

import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.io.ZipVirtualFile;
import com.atakmap.map.gdal.GdalLibrary;
import com.atakmap.map.layer.model.Georeferencer;
import com.atakmap.map.layer.model.ModelInfo;
import com.atakmap.map.layer.model.obj.ObjUtils;
import com.atakmap.map.projection.ProjectionFactory;
import com.atakmap.math.Matrix;
import com.atakmap.math.PointD;
import com.atakmap.coremap.log.Log;
import org.gdal.osr.SpatialReference;

import java.io.File;

public final class Pix4dGeoreferencer implements Georeferencer {
    public final static Georeferencer INSTANCE = new Pix4dGeoreferencer();
    public static final String TAG = "Pix4dGeoreferencer";

    private Pix4dGeoreferencer() {}

    @Override
    public boolean locate(ModelInfo model) {
        try {
            File f = new File(model.uri);
            if(FileSystemUtils.isZipPath(model.uri)) {
                try {
                    f = new ZipVirtualFile(model.uri);
                } catch(IllegalArgumentException ignored) {}
            }
            if(!IOProviderFactory.exists(f))
                return false;

            // derive the base filename
            String baseFileName = f.getName();
            baseFileName = baseFileName.substring(0, baseFileName.lastIndexOf('.'));
            if (baseFileName.endsWith("_simplified_3d_mesh"))
                baseFileName = baseFileName.replace("_simplified_3d_mesh", "");

            // check for offset file
            File offsetFile = ObjUtils.findFile(f.getParentFile(), baseFileName, new String[] {"_offset.xyz", ".xyz"});
            if (!FileSystemUtils.isFile(offsetFile)) { 
                // last ditch effort to find a prj file.   users are not always following the 
                // name convention.
                offsetFile = ObjUtils.findFirst(f.getParentFile(), ".xyz");
                if (!FileSystemUtils.isFile(offsetFile))
                     return false;

            }

            File projectionFile = ObjUtils.findFile(f.getParentFile(), baseFileName, new String[] {"_wkt.prj", ".prj"});
            if(!FileSystemUtils.isFile(projectionFile)) { 
                projectionFile = ObjUtils.findFirst(f.getParentFile(), ".prj");
                // last ditch effort to find a prj file.   users are not always following the 
                // name convention.
                if (!FileSystemUtils.isFile(projectionFile)) 
                     return false;
            }

            // extract the offset
            String xyz = ObjUtils.copyStreamToString(offsetFile);
            String[] splits = xyz.split("\\s");
            if (splits.length != 3) { 
                Log.d(TAG, offsetFile.getName() + " length is not == 3");
                Log.d(TAG, "xyz contents: " + xyz);
                return false;
            }

            PointD localFrameOrigin = new PointD(Double.parseDouble(splits[0]),
                                                 Double.parseDouble(splits[1]),
                                                 Double.parseDouble(splits[2]));
            // check for projection file
            String wkt = ObjUtils.copyStreamToString(projectionFile);
            // try to extract the SRID from the WKT representation
            final SpatialReference ref = new SpatialReference(wkt);
            int srid = GdalLibrary.getSpatialReferenceID(ref);

            model.srid = srid;
            model.localFrame = Matrix.getTranslateInstance(localFrameOrigin.x, localFrameOrigin.y, localFrameOrigin.z);

            // XXX - altitude mode must stay relative until we have some kind
            //       of "rubber sheet" functionality in core
            model.altitudeMode = ModelInfo.AltitudeMode.Relative;

            // XXX - this should really be the center of the AABB
            model.location = ProjectionFactory.getProjection(srid).inverse(localFrameOrigin, null);

            return true;
        } catch(Throwable t) {
            Log.e(TAG, "error loading geospatial information about the model: " + model.uri, t);
            return false;
        }
    }
}

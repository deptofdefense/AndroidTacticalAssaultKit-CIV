package com.atakmap.map.formats.c3dt;

import android.net.Uri;

import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProvider;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.io.UriFactory;
import com.atakmap.io.ZipVirtualFile;
import com.atakmap.map.layer.model.ModelInfo;
import com.atakmap.map.layer.model.ModelInfoSpi;
import com.atakmap.map.layer.model.opengl.GLSceneFactory;
import com.atakmap.map.projection.ECEFProjection;
import com.atakmap.math.Matrix;
import com.atakmap.math.PointD;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.InputStream;
import java.net.URI;
import java.util.Collections;
import java.util.Set;

public final class Cesium3DTilesModelInfoSpi implements ModelInfoSpi {
    public final static ModelInfoSpi INSTANCE = new Cesium3DTilesModelInfoSpi();
    static {
        GLSceneFactory.registerSpi(GLTileset.SPI);
    }

    @Override
    public String getName() {
        return "Cesium 3D Tiles";
    }

    @Override
    public int getPriority() {
        return 0;
    }

    @Override
    public boolean isSupported(String s) {
        // URI must have tileset.json
        try {
            URI uri = new URI(s);
            String path = uri.getPath();
            String[] parts = path.split("/");
            if (parts.length > 0 && parts[parts.length - 1].compareToIgnoreCase("tileset.json") == 0)
                return true;
        } catch (Exception e) {
            // ignore
        }

        // fallback on File test
        File f = new File(s);
        if(f.getName().endsWith(".zip")) {
            try {
                f = new ZipVirtualFile(f);
            } catch(Throwable ignored) {}
        }
        if(!IOProviderFactory.isDirectory(f))
            return false; // XXX - workaround for ATAK-12324
        else if(f instanceof ZipVirtualFile)
            f = new ZipVirtualFile(f, "tileset.json");
        else
            f = new File(f, "tileset.json");
        return IOProviderFactory.exists(f) && f.getName().equals("tileset.json");
    }

    @Override
    public Set<ModelInfo> create(String s) {
        File f = new File(s);
        if(f.getName().endsWith(".zip")) {
            try {
                f = new ZipVirtualFile(f);
            } catch(Throwable ignored) {}
        }
        // remote URL load
        if(!IOProviderFactory.exists(f)) {
            UriFactory.OpenResult uriOpenResult = UriFactory.open(s);
            if (uriOpenResult != null) {
                try {
                    byte[] buffer = null;
                    if (uriOpenResult.contentLength > 0 && uriOpenResult.contentLength <= Integer.MAX_VALUE)
                        buffer = FileSystemUtils.read(uriOpenResult.inputStream, (int) uriOpenResult.contentLength, true);
                    else
                        buffer = FileSystemUtils.read(uriOpenResult.inputStream);
                    String name = s;
                    try {
                        name = Uri.parse(s).getPath();
                    } catch (Throwable ignored) {}
                    return createFromString(new String(buffer, FileSystemUtils.UTF8_CHARSET), name, s);
                } catch (Throwable e) {
                    return null;
                }
            }
        }

        if (IOProviderFactory.isDirectory(f)) {
            if(f instanceof ZipVirtualFile) {
                try {
                    f = new ZipVirtualFile(f, "tileset.json");
                } catch(Throwable t) {
                    return null;
                }
            } else {
                f = new File(f, "tileset.json");
            }
        }
        if (!IOProviderFactory.exists(f) || !f.getName().equals("tileset.json"))
            return null;

        try {
            InputStream is;
            if(f instanceof ZipVirtualFile)
                is = ((ZipVirtualFile)f).openStream();
            else
                is = IOProviderFactory.getInputStream(f);
            return createFromString(
                    FileSystemUtils.copyStreamToString(
                            is, true, FileSystemUtils.UTF8_CHARSET),
                    f.getParentFile().getName(),
                    f.getAbsolutePath());
        } catch (Throwable t) {
            return null;
        }
    }

    private Set<ModelInfo> createFromString(String jsonStr, String name, String uri) throws JSONException {
        Tileset ts = Tileset.parse(new JSONObject(jsonStr));
        if (ts == null)
            return null;
        ModelInfo info = new ModelInfo();
        info.altitudeMode = ModelInfo.AltitudeMode.Absolute;
        info.localFrame = null;
        info.minDisplayResolution = Double.MAX_VALUE;
        info.maxDisplayResolution = 0d;
        info.name = name;//f.getParentFile().getName();
        info.srid = 4326;
        info.type = getName();
        info.uri = uri;//f.getAbsolutePath();
        if (ts.root.boundingVolume instanceof Volume.Region) {
            Volume.Region region = (Volume.Region) ts.root.boundingVolume;
            info.location = new GeoPoint(Math.toDegrees(region.north + region.south) / 2d, Math.toDegrees(region.east + region.west) / 2d);
        }
        if (ts.root.boundingVolume instanceof Volume.Sphere) {
            Volume.Sphere sphere = (Volume.Sphere) ts.root.boundingVolume;

            PointD center = new PointD(sphere.centerX, sphere.centerX, sphere.centerZ);
            // transform the center
            if (ts.root.transform != null) {
                Matrix transform = new Matrix(
                        ts.root.transform[0], ts.root.transform[4], ts.root.transform[8], ts.root.transform[12],
                        ts.root.transform[1], ts.root.transform[5], ts.root.transform[9], ts.root.transform[13],
                        ts.root.transform[2], ts.root.transform[6], ts.root.transform[10], ts.root.transform[14],
                        ts.root.transform[3], ts.root.transform[7], ts.root.transform[11], ts.root.transform[15]
                );
                transform.transform(center, center);
            }

            info.location = ECEFProjection.INSTANCE.inverse(center, null);
        } else if (ts.root.boundingVolume instanceof Volume.Box) {
            Volume.Box box = (Volume.Box) ts.root.boundingVolume;

            PointD center = new PointD(box.centerX, box.centerX, box.centerZ);
            // transform the center
            if (ts.root.transform != null) {
                Matrix transform = new Matrix(
                        ts.root.transform[0], ts.root.transform[4], ts.root.transform[8], ts.root.transform[12],
                        ts.root.transform[1], ts.root.transform[5], ts.root.transform[9], ts.root.transform[13],
                        ts.root.transform[2], ts.root.transform[6], ts.root.transform[10], ts.root.transform[14],
                        ts.root.transform[3], ts.root.transform[7], ts.root.transform[11], ts.root.transform[15]
                );
                transform.transform(center, center);
            }

            info.location = ECEFProjection.INSTANCE.inverse(center, null);
        }
        return Collections.singleton(info);
    }

}

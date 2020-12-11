package com.atakmap.map.layer.model.dae;

import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.locale.LocaleUtil;
import com.atakmap.coremap.maps.coords.GeoCalculations;
import com.atakmap.coremap.maps.coords.GeoPoint.AltitudeReference;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.io.ZipVirtualFile;
import com.atakmap.map.layer.model.ModelFileUtils;
import com.atakmap.map.layer.model.ModelInfo;
import com.atakmap.map.layer.model.ModelInfoSpi;
import com.atakmap.map.projection.ProjectionFactory;
import com.atakmap.math.Matrix;
import com.atakmap.coremap.log.Log;
import com.atakmap.math.PointD;
import android.util.Pair;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Scanner;
import java.util.Set;

public class DaeModelInfoSpi implements ModelInfoSpi {

    static final int DOES_NOT_EXIST = -1;
    static final int UNDETERMINED = 0;
    static final int Y_UP = 1;
    static final int X_UP = 2;
    static final int Z_UP = 3;


    public static final String TAG = "DaeModelInfoSpi";

    public static final DaeModelInfoSpi INSTANCE = new DaeModelInfoSpi();

    @Override
    public String getName() {
        return "COLLADA";
    }

    @Override
    public int getPriority() {
        return 1;
    }

    @Override
    public boolean isSupported(String path) {
        return this.create(path) != null;
    }

    private void findModels(ZipVirtualFile kmlFile, String name, Element elem, List<ModelTuple> result) {
        String tag = elem.getTagName();
        if (tag.equals("Model")) {
            result.add(new ModelTuple(kmlFile, name, elem));
            return;
        }

        // XXX - not crazy about the double scan
        NodeList children = elem.getChildNodes();
        for (int i = 0; i < children.getLength(); ++i) {
            Node node = children.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE)
                continue;

            if(((Element)node).getTagName().equalsIgnoreCase("name")) {
                name = ((Element)node).getTextContent();
                break;
            }
        }

        for (int i = 0; i < children.getLength(); ++i) {
            Node node = children.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                findModels(kmlFile, name, (Element)node, result);
            }
        }
    }

    private static Element getChild(Element elem, String[] path, int index) {
        if (index == path.length) {
            return elem;
        }
        NodeList children = elem.getChildNodes();
        for (int i = 0; i < children.getLength(); ++i) {
            Node node = children.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE && ((Element)node).getTagName().equals(path[index])) {
                return getChild((Element)node, path, index + 1);
            }
        }
        return null;
    }

    static Element getChild(Element elem, String[] path) {
        return getChild(elem, path, 0);
    }

    static String getTextContent(Element elem, String[] path) {
        Element child = getChild(elem, path);
        if (child != null)
            return child.getTextContent();
        return "";
    }

    static ModelInfo.AltitudeMode parseAltMode(String mode) {
        if (mode.equals("absolute"))
            return ModelInfo.AltitudeMode.Absolute;
        if (mode.equals("clampToGround"))
            return ModelInfo.AltitudeMode.Relative;
        if (mode.equals("clampToSeaFloor"))
            return ModelInfo.AltitudeMode.Relative;
        if (mode.equals("relativeToGround"))
            return ModelInfo.AltitudeMode.Relative;
        if (mode.equals("relativeToSeaFloor"))
            return ModelInfo.AltitudeMode.Relative;
        return ModelInfo.AltitudeMode.Relative;
    }

    static Pair<Double, AltitudeReference> parseAlt(String value, String mode) {
        AltitudeReference ref = AltitudeReference.HAE;
        double val = Double.parseDouble(value);

        //XXX-- expect to be clamped
        if (mode.equals("clampToGround"))
            ref = AltitudeReference.AGL;
        if (mode.equals("clampToSeaFloor"))
           ref = AltitudeReference.HAE;                  // XXY AltitudeReference.MSL;
        if (mode.equals("relativeToGround"))
           ref = AltitudeReference.AGL;
        if (mode.equals("relativeToSeaFloor"))
           ref = AltitudeReference.HAE;                  // XXY AltitudeReference.MSL;

        return new Pair<>(val, ref);
    }

    Set<ModelInfo> modelInfos(List<ModelTuple> models) {
        Set<ModelInfo> result = new HashSet<>();
        for (ModelTuple entry : models) {
            final String name = entry.name;
            final Element model = entry.model;
            final File kmlFile = entry.kmlFile;

            ModelInfo info = new ModelInfo();
            if(models.size() > 1)
                info.name = name;
            else
                info.name = kmlFile.getParentFile().getName();

            String altMode = getTextContent(model, new String[]{"altitudeMode"});
            info.altitudeMode = parseAltMode(altMode);

            String modelPath = getTextContent(model, new String[]{"Link", "href"});
            info.uri = kmlFile.getParentFile().getPath() + File.separator + modelPath;

            int upAxis = determineUpAxis(info.uri);
            if (upAxis == DOES_NOT_EXIST) {
                continue;
            }

            double scaleX = 1.0, scaleY = 1.0, scaleZ = 1.0;
            Element scale = getChild(model, new String[]{"Scale"});
            if (scale != null) {
                try {
                    double tx = Double.parseDouble(getTextContent(scale, new String[]{"x"}));
                    double ty = Double.parseDouble(getTextContent(scale, new String[]{"y"}));
                    double tz = Double.parseDouble(getTextContent(scale, new String[]{"z"}));
                    scaleX = tx;
                    scaleY = ty;
                    scaleZ = tz;
                    info.scale = new PointD(scaleX, scaleY, scaleZ);
                } catch (Exception e) {
                    Log.d(TAG, "failed to parse Scale (assuming 1, 1, 1)", e);
                }
            }

            if (upAxis == Y_UP) {
                if (info.scale == null)
                    info.scale = new PointD(1, 1, 1);
                info.scale.x *= -1;
                info.scale.y *= -1;
            }

            double heading = 0.0, tilt = 0.0, roll = 0.0;
            Element orientation = getChild(model, new String[]{"Orientation"});
            if (orientation != null) {
                try {

                    final String thStr = getTextContent(orientation, new String[]{"heading"});
                    final String ttStr = getTextContent(orientation, new String[]{"tilt"});
                    final String trStr = getTextContent(orientation, new String[]{"roll"});
                    if (thStr.length() == 0 || ttStr.length() == 0 || trStr.length() == 0) {
                        Log.d(TAG, "missing heading="+thStr+",tilt="+ttStr+",roll="+trStr+"  --- assuming 0,0,0");
                    } else {
                        double th = Double.parseDouble(thStr);
                        double tt = Double.parseDouble(ttStr);
                        double tr = Double.parseDouble(trStr);
                        heading = th;
                        tilt = tt;
                        roll = tr;
                        info.rotation = new PointD(tilt, heading, roll);
                    }
                } catch (Exception e) {
                    Log.d(TAG, "failed to parse Orientation (assuming 0, 0, 0)", e);
                }
            }

            Element location = getChild(model, new String[]{"Location"});
            if (location != null) {
                double lat = Double.parseDouble(getTextContent(location, new String[]{"latitude"}));
                double lon = Double.parseDouble(getTextContent(location, new String[]{"longitude"}));
                Pair<Double, AltitudeReference> alt = parseAlt(getTextContent(location, new String[]{"altitude"}), altMode);
                GeoPoint origin = new GeoPoint(lat, lon, alt.first, alt.second, 0, 0);

                info.location = origin;

                // XXX - assuming an ENU coordinate system. use of web mercator produced significant
                //       errors; use of local UTM produced better results but the error was still
                //       larger than using ENU based on local meters-per-degree

                // compute local degrees to meters
                final double metersLat = GeoCalculations.approximateMetersPerDegreeLatitude(origin.getLatitude());
                final double metersLng = GeoCalculations.approximateMetersPerDegreeLongitude(origin.getLatitude());

                PointD p = new PointD(0d, 0d, 0d);
                ProjectionFactory.getProjection(4326).forward(origin, p);

                info.localFrame = Matrix.getIdentity();

                // Order: translate -> scale -> rotate
                info.localFrame.translate(p.x, p.y, p.z);

                info.localFrame.scale(1d/metersLng * scaleX, 1d/metersLat * scaleY, scaleZ);

                // XXX-- Google Earth interprets Y_UP models as mirrored in the X and Y axis. No real
                // explanation why.
                // Find a better way to find Y_UP via Assimp loader SPI
                if (upAxis == Y_UP) {
                    info.localFrame.scale(-1, -1, 1);
                }

                //XXX-- use quaternion to avoid gimbal lock
                info.localFrame.rotate(Math.toRadians(-heading), 0.0f,0.0f,1.0f);
                info.localFrame.rotate(Math.toRadians(roll), 0.0f,1.0f,0.0f);
                info.localFrame.rotate(Math.toRadians(tilt), 1.0f,0.0f,0.0f);

                info.srid = 4326;

                // XXX - altitude mode must stay relative until "rubber sheet"
                //       type capability is available from core UX
                info.altitudeMode = ModelInfo.AltitudeMode.Relative;
            }

            Element resourceMap = getChild(model, new String[] {"ResourceMap"});
            if(resourceMap != null) {
                /*
<ResourceMap>
    <Alias>
        <targetHref>material_1.jpg</targetHref>
        <sourceHref>skyline_typeE_powerpole/material_1.jpg</sourceHref>
    </Alias>
</ResourceMap>
                 */

                // for each 'Alias' child, add a resource mapping entry
                NodeList aliasNodes = resourceMap.getElementsByTagName("Alias");
                if(aliasNodes != null) {
                    for(int i = 0; i < aliasNodes.getLength(); i++) {
                        final String targetHref = getTextContent((Element)aliasNodes.item(i), new String[] {"targetHref"});
                        final String sourceHref = getTextContent((Element)aliasNodes.item(i), new String[] {"sourceHref"});
                        if(targetHref == null || sourceHref == null)
                            continue;
                        if(info.resourceMap == null)
                            info.resourceMap = new HashMap<String, String>();
                        info.resourceMap.put(sourceHref, targetHref);
                    }
                }
            }

            info.type = "DAE";
            result.add(info);
        }
        return result.isEmpty() ? null : result;
    }

    private Set<ModelInfo> modelInfos(String name, List<File> daeFiles) {
        if (FileSystemUtils.isEmpty(daeFiles))
            return null;
        Set<ModelInfo> result = new HashSet<>();
        for (File f : daeFiles) {
            ModelInfo info = new ModelInfo();
            if (name.endsWith(".zip"))
                info.uri = "zip://" + f.getAbsolutePath();
            else
                info.uri = f.getAbsolutePath();
            info.altitudeMode = ModelInfo.AltitudeMode.Absolute;
            info.name = name;
            info.type = "DAE";
            result.add(info);
        }
        return result;
    }

    static class ModelTuple {

        public ModelTuple(ZipVirtualFile kmlFile, String name, Element model) {
            this.kmlFile = kmlFile;
            this.name = name;
            this.model = model;
        }

        ZipVirtualFile kmlFile;
        String name;
        Element model;
    }

    @Override
    public Set<ModelInfo> create(String path) {
        try {
            File file = new File(path);
            String lowerName = file.getName().toLowerCase(LocaleUtil.getCurrent());
            if (lowerName.endsWith(".kmz")) {
                // Geospatial DAE (requires doc.kml)
                ZipVirtualFile zf = new ZipVirtualFile(path);
                List<File> kmlFiles = ModelFileUtils.findFiles(zf, Collections.singleton("kml"));
                LinkedList<ModelTuple> models = new LinkedList<>();
                for (File kmlFile : kmlFiles) {
                    InputStream is = null;
                    try {
                        ZipVirtualFile zvfKmlFile = new ZipVirtualFile(kmlFile);
                        is = zvfKmlFile.openStream();
                        long start = System.currentTimeMillis();
                        final Scanner scanner = new Scanner(is);
                        String asset = scanner.findWithinHorizon("<href>[\\s\\S]*(.dae|.DAE)<\\/href>", 128 * 1024);
                        scanner.close();

                        Log.d(TAG, "initial scan for a referenced dae file: " + kmlFile + " " + (System.currentTimeMillis() - start) + "ms");
                        if (asset != null) {
                            Log.d(TAG, "found a reference to a dae in" + kmlFile);
                            InputStream xmlis = null;
                            try {
                                Document kmlDoc = ModelFileUtils.parseXML(xmlis = zvfKmlFile.openStream());
                                if (kmlDoc != null) {
                                    findModels(zvfKmlFile, file.getName(), kmlDoc.getDocumentElement(), models);
                                }
                            } finally {
                                if (xmlis != null) {
                                    try {
                                        xmlis.close();
                                    } catch (Exception e) {
                                    }
                                }
                            }
                        }
                    } finally {
                        if (is != null) {
                            try {
                                is.close();
                            } catch (IOException ignored) {
                            }
                        }
                    }
                }
                return modelInfos(models);
            } else if (lowerName.endsWith(".zip")) {
                // Zipped DAE (no geospatial info)
                ZipVirtualFile zf = new ZipVirtualFile(path);
                List<File> daeFiles = ModelFileUtils.findFiles(zf,
                        Collections.singleton("dae"));
                return modelInfos(file.getName(), daeFiles);
            } else if (lowerName.endsWith(".dae")) {
                // Single DAE file
                return modelInfos(file.getName(),
                        Collections.singletonList(file));
            }
        } catch (IllegalArgumentException iae) {
            // occurs when a corrupt zip file is opened or something that is not a zip file
            Log.e(TAG, "error", iae);
        } catch (IOException e) {
            Log.e(TAG, "error", e);
        }

        return null;
    }

    private static int determineUpAxis(String uri) {
        InputStream inputStream = null;
        try {
            inputStream = ModelFileUtils.openInputStream(uri);
            if (inputStream == null)
                return DOES_NOT_EXIST;
            final Scanner scanner = new Scanner(inputStream);
            String asset = scanner.findWithinHorizon("<asset>[\\s\\S]*?<\\/asset>", 128 * 1024);
            scanner.close();
            if (asset != null) {
                Document doc = ModelFileUtils.parseXML(new ByteArrayInputStream(asset.getBytes()));
                if (doc != null) {
                    NodeList upAxisNodes = doc.getDocumentElement().getElementsByTagName("up_axis");
                    if (upAxisNodes.getLength() > 0) {
                        String value = upAxisNodes.item(0).getTextContent();
                        if (value != null) {
                            if (value.equals("Y_UP")) {
                                return Y_UP;
                            } else if (value.equals("Z_UP")) {
                                return Z_UP;
                            } else if (value.equals("X_UP")) {
                                return X_UP;
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            // undetermined
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException ignored) {
                }
            }
        }
        return UNDETERMINED;
    }
}

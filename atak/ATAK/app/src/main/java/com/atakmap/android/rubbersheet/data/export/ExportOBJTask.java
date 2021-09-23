
package com.atakmap.android.rubbersheet.data.export;

import android.graphics.Color;
import android.graphics.PointF;
import android.os.SystemClock;

import com.atakmap.android.maps.MapView;
import com.atakmap.android.rubbersheet.data.ModelTransformListener;
import com.atakmap.android.rubbersheet.data.RubberModelData;
import com.atakmap.android.rubbersheet.data.RubberSheetManager;
import com.atakmap.android.rubbersheet.maps.RubberModel;
import com.atakmap.android.util.ATAKConstants;
import com.atakmap.android.util.ATAKUtilities;
import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.conversion.EGM96;
import com.atakmap.coremap.maps.coords.GeoCalculations;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.UTMPoint;
import com.atakmap.coremap.maps.time.CoordinatedTime;
import com.atakmap.map.layer.model.Material;
import com.atakmap.map.layer.model.Mesh;
import com.atakmap.map.layer.model.Model;
import com.atakmap.map.layer.model.ModelInfo;
import com.atakmap.map.layer.model.Models;
import com.atakmap.map.layer.model.VertexDataLayout;
import com.atakmap.math.MathUtils;
import com.atakmap.math.Matrix;
import com.atakmap.math.PointD;

import com.atakmap.util.zip.IoUtils;
import org.gdal.osr.SpatialReference;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.DecimalFormat;
import java.util.LinkedHashMap;
import java.util.Objects;

/**
 * Task for exporting a rubber model to an OBJ zip
 */
public class ExportOBJTask extends ExportFileTask implements
        ExportFileTask.FileProgressCallback,
        Models.OnTransformProgressListener {

    private static final String TAG = "ExportOBJTask";
    private static final DecimalFormat df = new DecimalFormat("0.######");

    private final RubberModel _item;
    private final CoordinatedTime _exportTime;

    private String _baseName;

    public ExportOBJTask(MapView mapView, RubberModel item, Callback callback) {
        super(mapView, item, callback);
        _item = item;
        _exportTime = new CoordinatedTime();
        _baseName = item.getTitle();
        for (String ext : RubberModelData.EXTS) {
            ext = "." + ext;
            if (_baseName.endsWith(ext)) {
                _baseName = _baseName.substring(0, _baseName.lastIndexOf(ext));
                break;
            }
        }
    }

    @Override
    protected String getProgressMessage() {
        return _context.getString(R.string.transforming_model, _baseName);
    }

    @Override
    protected int getProgressStages() {
        return 1;
    }

    @Override
    protected File doInBackground(Void... params) {
        if (!_item.isLoaded())
            return null;

        Model model = _item.getModel();
        ModelInfo info = _item.getInfo();
        GeoPoint center = _item.getCenterPoint();
        if (info == null || model == null)
            return null;

        // Create temporary zip directory
        File tmpDir = new File(RubberSheetManager.DIR, ".tmp_"
                + _baseName + "_objzip");
        if (IOProviderFactory.exists(tmpDir))
            FileSystemUtils.delete(tmpDir);
        if (!IOProviderFactory.mkdirs(tmpDir)) {
            Log.d(TAG, "Failed to create temp dir: " + tmpDir);
            return null;
        }
        addOutputFile(tmpDir);

        // Transform the model to match the user's edits
        double[] scale = _item.getModelScale();
        double[] rotation = _item.getModelRotation();
        ModelInfo srcInfo = new ModelInfo(info);
        ModelInfo dstInfo = new ModelInfo(info);
        dstInfo.localFrame = Matrix.getIdentity();
        srcInfo.localFrame = Matrix.getIdentity();

        // Grid north offset - must be applied or else the model is offset slightly
        // when viewed outside of the tool
        GeoPoint gEnd = GeoCalculations.pointAtDistance(center, 0,
                Math.max(_item.getWidth(), _item.getLength()));
        double gConv = ATAKUtilities.computeGridConvergence(center, gEnd);

        dstInfo.localFrame.scale(1d / scale[0], 1d / scale[1], 1d / scale[2]);
        dstInfo.localFrame.rotate(Math.toRadians(rotation[0]), 1.0f, 0.0f,
                0.0f);
        dstInfo.localFrame.rotate(Math.toRadians(rotation[1] - gConv), 0.0f,
                0.0f, 1.0f);
        dstInfo.localFrame.rotate(Math.toRadians(rotation[2]), 0.0f, 1.0f,
                0.0f);
        model = Models.transform(srcInfo, model, dstInfo,
                new ModelTransformListener(model, this));

        if (isCancelled())
            return null;

        // Begin export
        setProgressMessage(R.string.exporting_obj, _baseName);
        progress(0, 100);

        // Create projection file
        UTMPoint utm = UTMPoint.fromGeoPoint(center);
        int zone = utm.getLngZone();

        GeoPoint merPoint = new UTMPoint(utm.getZoneDescriptor(), 500000,
                utm.getNorthing()).toGeoPoint();
        int meridian = (int) Math.round(merPoint.getLongitude());

        SpatialReference ref = new SpatialReference();
        ref.SetProjCS("WGS 84 / UTM zone " + zone + "N");
        ref.SetGeogCS("WGS 84", "WGS_1984", "WGS 84", 6378137, 298.257223563,
                "Greenwich", 0);
        ref.SetProjection("Transverse_Mercator");
        ref.SetProjParm("latitude_of_origin", 0);
        ref.SetProjParm("central_meridian", meridian);
        ref.SetProjParm("scale_factor", 0.9996);
        ref.SetProjParm("false_easting", 500000);
        ref.SetProjParm("false_northing", 0);
        ref.SetLinearUnits("metre", 1);
        ref.SetAuthority("PROJCS", "EPSG", 32600 + zone);
        ref.SetAuthority("PROJCS|UNIT", "EPSG", 9001);
        ref.SetAuthority("GEOGCS", "EPSG", 4326);
        ref.SetAuthority("GEOGCS|DATUM", "EPSG", 6326);
        ref.SetAuthority("GEOGCS|DATUM|SPHEROID", "EPSG", 7030);
        ref.SetAuthority("GEOGCS|PRIMEM", "EPSG", 8901);
        ref.SetAuthority("GEOGCS|UNIT", "EPSG", 9122);

        String wkt = ref.ExportToWkt();
        writeToFile(new File(tmpDir, _baseName + "_wkt.prj"), wkt);

        // Create UTM offset file
        double alt = EGM96.getHAE(center);
        if (Double.isNaN(alt))
            alt = 0;
        String offset = utm.getEasting() + " " + utm.getNorthing() + " " + alt;
        writeToFile(new File(tmpDir, _baseName + "_offset.xyz"), offset);

        // Create MTL file
        StringBuilder sb = new StringBuilder();
        int meshCount = model.getNumMeshes();
        int meshNum = 0;
        for (int i = 0; i < meshCount; i++) {
            Mesh mesh = model.getMesh(i);
            if (mesh == null)
                continue;

            Material mat = null;
            int numMaterials = mesh.getNumMaterials();
            for (int j = 0; j < numMaterials; j++) {
                mat = mesh.getMaterial(j);
                if (mat == null || FileSystemUtils.isEmpty(mat.getTextureUri()))
                    mat = null;
            }
            if (mat == null)
                continue;

            // Write entry for material
            sb.append("newmtl material-").append(meshNum++).append("\n");

            // Vertex color already takes care of diffuse, so all 1s here
            sb.append(
                    "illum 0\nKa 1.000000 1.000000 1.000000\nKd 1.000000 1.000000 1.000000\n");

            // Texture file
            String uri = mat.getTextureUri();
            if (!FileSystemUtils.isEmpty(uri)) {
                String texName = new File(uri).getName();
                sb.append("map_Kd ").append(texName).append("\n");
                copyFile(uri, tmpDir);
            }

            if (i < meshCount - 1)
                sb.append("\n");

            if (isCancelled())
                return null;
        }
        writeToFile(new File(tmpDir, _baseName + ".mtl"), sb.toString());

        if (isCancelled())
            return null;

        // Create OBJ file
        File obj = new File(tmpDir, _baseName + ".obj");
        long start = SystemClock.elapsedRealtime();
        writeOBJ(obj, model, _baseName + ".mtl");
        Log.d(TAG, "Took " + (SystemClock.elapsedRealtime() - start)
                + "ms to write OBJ for " + _baseName);

        if (isCancelled())
            return null;

        start = SystemClock.elapsedRealtime();
        File zipFile = new File(FileSystemUtils.getItem(
                FileSystemUtils.EXPORT_DIRECTORY), _baseName + ".zip");
        addOutputFile(zipFile);
        setProgressMessage(R.string.compressing_zip, zipFile.getName());
        try {
            zipDirectory(tmpDir, zipFile, this);
        } catch (IOException ioe) {
            Log.e(TAG, "error occurred zipping OBJ zip: " + zipFile, ioe);
            return null;
        }
        Log.d(TAG, "Took " + (SystemClock.elapsedRealtime() - start)
                + "ms to compress OBJ zip for " + _baseName);

        if (isCancelled())
            return null;

        // Cleanup
        if (IOProviderFactory.exists(tmpDir))
            FileSystemUtils.delete(tmpDir);

        return zipFile;
    }

    @Override
    public boolean onProgress(File file, long progress, long max) {
        int percent = (int) Math.round(100 * ((double) progress / max));
        return progress(percent, 100);
    }

    @Override
    public void onTransformProgress(int progress) {
        if (!isCancelled())
            progress(progress, 100);
    }

    private void writeOBJ(File obj, Model model, String mtlName) {
        String path = obj.getAbsolutePath();
        File vFile = new File(path + ".v");
        File tFile = new File(path + ".vt");
        File nFile = new File(path + ".vn");
        File fFile = new File(path + ".f");
        FileOutputStream fos = null;
        try (PrintWriter ow = new PrintWriter(
                IOProviderFactory.getOutputStream(new File(path)));
                PrintWriter vw = new PrintWriter(
                        IOProviderFactory.getFileWriter(vFile));
                PrintWriter tw = new PrintWriter(
                        IOProviderFactory.getFileWriter(tFile));
                PrintWriter nw = new PrintWriter(
                        IOProviderFactory.getFileWriter(nFile));
                PrintWriter fw = new PrintWriter(
                        IOProviderFactory.getFileWriter(fFile))) {

            LinkedHashMap<ColorVertex, Integer> vMap = new LinkedHashMap<>();
            LinkedHashMap<TexCoord, Integer> tMap = new LinkedHashMap<>();
            LinkedHashMap<Vertex, Integer> nMap = new LinkedHashMap<>();
            PointD pSrc = new PointD(0, 0, 0);
            PointD pDst = new PointD(0, 0, 0);
            ColorVertex v = new ColorVertex();
            TexCoord uv = new TexCoord();
            Vertex n = new Vertex();
            StringBuilder sb = new StringBuilder();
            StringBuilder fsb = new StringBuilder();

            long start = SystemClock.elapsedRealtime();

            // Get the total number of vertices that need to be processed
            int numMeshes = model.getNumMeshes();
            int totalVerts = 0;
            for (int i = 0; i < numMeshes; i++)
                totalVerts += model.getMesh(i).getNumVertices();

            // Track total faces and current vertex number
            int totalFaces = 0;
            int totalVertNum = 0;
            int meshNum = 0;
            for (int i = 0; i < numMeshes; i++) {
                Mesh mesh = model.getMesh(i);
                if (mesh == null)
                    continue;

                int numVerts = mesh.getNumVertices();
                if (numVerts == 0)
                    continue;

                // Write the material used by this mesh
                Material material = mesh.getMaterial(0);
                if (material == null || FileSystemUtils.isEmpty(
                        material.getTextureUri())) {
                    Log.w(TAG, "Mesh #" + meshNum + " of model " + _baseName
                            + " is missing material! Skipping export...");
                    continue;
                }
                sb.append("\nusemtl material-").append(meshNum++);
                println(fw, sb);

                VertexDataLayout vdl = mesh.getVertexDataLayout();
                boolean hasUVs = MathUtils.hasBits(vdl.attributes,
                        Mesh.VERTEX_ATTR_TEXCOORD_0);
                boolean hasNormals = MathUtils.hasBits(vdl.attributes,
                        Mesh.VERTEX_ATTR_NORMAL);
                boolean hasVertexColors = MathUtils.hasBits(vdl.attributes,
                        Mesh.VERTEX_ATTR_COLOR);

                int faceIndex = 3;

                for (int j = 0; j < numVerts; j++) {
                    // Vertex
                    mesh.getPosition(j, pSrc);

                    // Include vertex color if applicable
                    int c = hasVertexColors ? mesh.getColor(j) : Color.WHITE;

                    v.set(pSrc, c);
                    Integer vIndex = vMap.get(v);
                    if (vIndex == null) {
                        // Vertex coordinate string
                        sb.append("v ")
                                .append(df.format(pSrc.x)).append(" ")
                                .append(df.format(pSrc.y)).append(" ")
                                .append(df.format(pSrc.z));

                        // Append vertex color
                        if (hasVertexColors) {
                            sb.append(" ")
                                    .append(df.format(Color.red(c) / 255f))
                                    .append(" ")
                                    .append(df.format(Color.green(c) / 255f))
                                    .append(" ")
                                    .append(df.format(Color.blue(c) / 255f));
                        }

                        println(vw, sb);
                        vMap.put(new ColorVertex(pSrc, c),
                                vIndex = vMap.size() + 1);
                    }

                    // Texture coordinate (UV)
                    Integer tIndex = null;
                    if (hasUVs) {
                        mesh.getTextureCoordinate(Mesh.VERTEX_ATTR_TEXCOORD_0,
                                j, pSrc);
                        uv.set(pSrc);
                        tIndex = tMap.get(uv);
                        if (tIndex == null) {
                            pDst.x = pSrc.x;
                            pDst.y = (pSrc.y * -1) + 1;
                            sb.append("vt ")
                                    .append(df.format(pDst.x)).append(" ")
                                    .append(df.format(pDst.y));
                            println(tw, sb);
                            tMap.put(new TexCoord(pSrc),
                                    tIndex = tMap.size() + 1);
                        }
                    }

                    // Vertex normals
                    Integer nIndex = null;
                    if (hasNormals) {
                        mesh.getNormal(j, pSrc);
                        n.set(pSrc);
                        nIndex = nMap.get(n);
                        if (nIndex == null) {
                            sb.append("vn ")
                                    .append(df.format(pSrc.x)).append(" ")
                                    .append(df.format(pSrc.y)).append(" ")
                                    .append(df.format(pSrc.z));
                            println(nw, sb);
                            nMap.put(new Vertex(pSrc),
                                    nIndex = nMap.size() + 1);
                        }
                    }

                    // Faces
                    if (faceIndex == 3) {
                        faceIndex = 0;
                        println(fw, fsb);
                        fsb.append("f");
                        totalFaces++;
                    }
                    fsb.append(" ").append(vIndex);
                    if (tIndex != null)
                        fsb.append("/").append(tIndex);
                    if (nIndex != null) {
                        if (tIndex == null)
                            fsb.append("/");
                        fsb.append("/").append(nIndex);
                    }

                    faceIndex++;

                    if (!progress(++totalVertNum, totalVerts))
                        return; // Task cancelled
                }
            }
            totalVerts = vMap.size();
            Log.d(TAG, "Took " + (SystemClock.elapsedRealtime() - start)
                    + "ms to write " + totalVerts + " vertices, "
                    + tMap.size() + " UVs, " + totalFaces + " faces for "
                    + _baseName);

            // Free up resources
            vw.close();
            tw.close();
            nw.close();
            fw.close();
            vMap.clear();
            tMap.clear();

            // Now write out the header for the OBJ file

            // Version info
            ow.println("# "
                    + _context.getString(R.string.version_production_string,
                            ATAKConstants.getVersionName()));
            ow.println("# " + _exportTime);

            // Statistics
            ow.println("# " + totalVerts + " vertices, " + totalFaces
                    + " faces\n");

            // Material file reference
            ow.println("mtllib " + mtlName + "\n");
            ow.close();

            fos = IOProviderFactory.getOutputStream(new File(path), true);

            // Copy vertex file into the OBJ file
            byte[] buf = new byte[FileSystemUtils.BUF_SIZE];
            appendStream(vFile, fos, buf, null);
            fos.flush();

            // Concatenate texture coordinates
            appendStream(tFile, fos, buf, null);
            fos.flush();

            // Concatenate normals
            appendStream(nFile, fos, buf, null);
            fos.flush();

            // Concatenate faces
            appendStream(fFile, fos, buf, null);
            fos.flush();
        } catch (Exception e) {
            Log.e(TAG, "Failed to write OBJ: " + obj, e);
        } finally {
            IoUtils.close(fos);
            FileSystemUtils.delete(vFile);
            FileSystemUtils.delete(tFile);
            FileSystemUtils.delete(nFile);
            FileSystemUtils.delete(fFile);
        }
    }

    private static class Vertex extends PointF {

        float z;

        Vertex() {
        }

        Vertex(PointD p) {
            set(p);
        }

        void set(PointD p) {
            this.x = (float) p.x;
            this.y = (float) p.y;
            this.z = (float) p.z;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (o == null || getClass() != o.getClass())
                return false;
            if (!super.equals(o))
                return false;
            Vertex vertex = (Vertex) o;
            return Float.compare(vertex.z, z) == 0;
        }

        @Override
        public int hashCode() {
            return Objects.hash(super.hashCode(), z);
        }
    }

    private static class ColorVertex extends Vertex {

        int color = Color.WHITE;

        ColorVertex() {
        }

        ColorVertex(PointD p, int color) {
            super(p);
            this.color = color;
        }

        void set(PointD p, int color) {
            this.x = (float) p.x;
            this.y = (float) p.y;
            this.z = (float) p.z;
            this.color = color;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (o == null || getClass() != o.getClass())
                return false;
            if (!super.equals(o))
                return false;
            ColorVertex that = (ColorVertex) o;
            return color == that.color;
        }

        @Override
        public int hashCode() {
            return Objects.hash(super.hashCode(), color);
        }
    }

    private static class TexCoord extends PointF {

        TexCoord() {
        }

        TexCoord(PointD p) {
            set(p);
        }

        void set(PointD p) {
            set((float) p.x, (float) p.y);
        }
    }

    private static void println(PrintWriter pw, StringBuilder sb) {
        pw.println(sb.toString());
        sb.delete(0, sb.length());
    }
}


package com.atakmap.map.layer.raster.gdal;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.BufferUnderflowException;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;
import java.util.Vector;

import org.gdal.gdal.GCP;
import org.gdal.gdal.gdal;
import org.gdal.gdal.Dataset;
import org.gdal.gdal.Driver;
import org.gdal.osr.CoordinateTransformation;
import org.gdal.osr.SpatialReference;

import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoPoint;

import com.atakmap.map.gdal.GdalLibrary;
import com.atakmap.map.layer.raster.DatasetProjection2;
import com.atakmap.math.NoninvertibleTransformException;
import com.atakmap.math.PointD;
import com.atakmap.math.Matrix;

public abstract class GdalDatasetProjection2 implements DatasetProjection2 {

    public static final String TAG = "GdalDatasetProjection";

    protected final SpatialReference datasetSpatialReference;
    protected final CoordinateTransformation proj2geo;
    protected final CoordinateTransformation geo2proj;
    protected final int nativeSpatialReferenceID;

    protected GdalDatasetProjection2(SpatialReference datasetSpatialReference) {
        this.datasetSpatialReference = datasetSpatialReference;
        this.proj2geo = new CoordinateTransformation(this.datasetSpatialReference,
                GdalLibrary.EPSG_4326);
        this.geo2proj = new CoordinateTransformation(GdalLibrary.EPSG_4326,
                this.datasetSpatialReference);

        this.nativeSpatialReferenceID = GdalLibrary
                .getSpatialReferenceID(this.datasetSpatialReference);
    }

    protected abstract PointD image2projected(PointD p);

    protected abstract PointD projected2image(PointD p);

    /*************************************************************************/

    @Override
    public final boolean groundToImage(GeoPoint g, PointD p) {
        final double[] proj = this.geo2proj.TransformPoint(g.getLongitude(), g.getLatitude());

        PointD r = this.projected2image(new PointD(proj[0], proj[1]));
        if (p == null)
            return false;
        p.x = r.x;
        p.y = r.y;
        return true;
    }

    @Override
    public final boolean imageToGround(PointD p, GeoPoint g) {
        p = this.image2projected(p);

        final double[] geo = this.proj2geo.TransformPoint(p.x, p.y);
        g.set(geo[1], geo[0]);
        return true;
    }
    
    @Override
    public void release() {}

    public final int getNativeSpatialReferenceID() {
        return this.nativeSpatialReferenceID;
    }

    /*************************************************************************/

    public static GdalDatasetProjection2 getInstance(Dataset dataset) {
        if (shouldUseNitfHighPrecisionCoordinates(dataset)) {
            String epsg4326ProjectionRef = GdalLibrary.EPSG_4326.ExportToWkt();
            ICHIPB ichipb = null;
            if (dataset.GetMetadataItem("ICHIPB", "TRE") != null)
                ichipb = new ICHIPB(ByteBuffer.wrap(dataset.GetMetadataItem("ICHIPB", "TRE")
                        .getBytes()));

            if (dataset.GetMetadataItem("CSCRNA", "TRE") != null) {
                try {
                    return new GroundControlPoints(dataset,
                            getCscrnaGCPs(dataset, ByteBuffer.wrap(dataset.GetMetadataItem(
                                    "CSCRNA", "TRE").getBytes()), ichipb),
                            epsg4326ProjectionRef, ichipb);
                } catch (Exception e) {
                    Log.w(TAG, "Failed to create GroundControlPoints for CSCRNA", e);
                }
            }

            if (dataset.GetMetadata_Dict("RPC") != null
                    && dataset.GetMetadata_Dict("RPC").size() > 0) {
                try {
                    return new GroundControlPoints(dataset,
                            getRpcGCPs(dataset, ichipb),
                            epsg4326ProjectionRef, ichipb);
                } catch (Exception e) {
                    Log.w(TAG, "Failed to create GroundControlPoints for RPC", e);
                }
            }
        }

        if (isUsableProjection(dataset.GetProjectionRef())
                && isUsableGeoTransform(dataset.GetGeoTransform())) {
            return new GeoTransform(dataset);
        } else if (dataset.GetGCPCount() >= 4) {
            try {
                return new GroundControlPoints(dataset);
            } catch (NoninvertibleTransformException e) {
                Log.w(TAG, "Failed to create GroundControlPoints for Ground Control Points", e);
                return null;
            }
        } else {
            return null;
        }
    }

    private static boolean isUsableProjection(String wkt) {
        return (wkt != null && wkt.length() > 0);
    }

    public static boolean isUsableGeoTransform(double[] geoTransform) {
        if (geoTransform == null)
            return false;
        for (int i = 0; i < geoTransform.length; i++)
            if (geoTransform[i] != 0)
                return true;
        return false;
    }

    public static boolean shouldUseNitfHighPrecisionCoordinates(Dataset dataset) {
        Driver driver = dataset.GetDriver();

        if (driver == null)
            return false;

        if (!driver.GetDescription().equals("NITF"))
            return false;
        Hashtable<String, String> TREs = dataset.GetMetadata_Dict("TRE");
        if (TREs == null)
            TREs = new Hashtable<String, String>();

        // GDAL will automatically utilize high precision coordinates in the
        // presence of some TREs, check for these TREs, and, if present, return

        // RPF TREs
        if (TREs.containsKey("RPFIMG") && TREs.containsKey("RPFDES") && TREs.contains("RPFHDR"))
            return false;
        // BLOCKA
        if (TREs.containsKey("BLOCKA") && checkBLOCKA(TREs.get("BLOCKA")))
            return false;
        // GEOSDEs
        if (TREs.containsKey("GEOPSB") && TREs.containsKey("PRJPSB") && TREs.containsKey("MAPLOB"))
            return false;

        return (TREs.containsKey("CSCRNA") || (dataset.GetMetadata_Dict("RPC") != null && dataset
                .GetMetadata_Dict("RPC").size() > 0));
    }

    private static boolean checkBLOCKA(String blocka) {
        String locationSection = blocka.substring(34);
        if (locationSection.trim().length() < 84)
            return false;
        else if (locationSection
                .matches("([NS]0{6}\\.00\\[EW]0{7}\\.00|[\\+\\-]0{2}\\.0{6}[\\+\\-]0{3}\\.0{6}){4}"))
            return false;
        return locationSection
                .matches("([NS]\\d{6}\\.\\d{2}\\[EW]\\d{7}\\.\\d{2}|[\\+\\-]\\d{2}\\.\\d{6}[\\+\\-]\\d{3}\\.\\d{6}){4}");
    }

    private static Vector<GCP> getCscrnaGCPs(Dataset dataset, ByteBuffer cscrna, ICHIPB ichipb) {
        Vector<GCP> gcps = new Vector<GCP>(4);
        String[] ids = new String[] {
                "UpperLeft", "UpperRight", "LowerRight", "LowerLeft"
        };
        
        final double fullImageWidth;
        final double fullImageHeight;
        if(ichipb == null) {
            fullImageWidth = (double) dataset.GetRasterXSize() - 0.5d;
            fullImageHeight = (double) dataset.GetRasterYSize() - 0.5d;
        } else {
            fullImageWidth = ichipb.fi_col;
            fullImageHeight = ichipb.fi_row;
        }

        double[] pixel = new double[] {
                0.5, fullImageWidth
        };
        double[] line = new double[] {
                0.5, fullImageHeight
        };

        skip(cscrna, 1); // predict_corners
        String lat;
        String lon;
        for (int i = 0; i < 4; i++) {
            lat = getString(cscrna, 9);
            lon = getString(cscrna, 10);
            skip(cscrna, 8); // XXcnr_ht

            gcps.add(new GCP(Double.parseDouble(lon), Double.parseDouble(lat),
                    pixel[((i + (i / 2)) % 2)], line[(i / 2)], "CSCRNA", ids[i]));
        }

        return gcps;
    }

    private static Vector<GCP> getRpcGCPs(Dataset dataset, ICHIPB ichipb) {
        RapidPositioningControlB rpc00b = new RapidPositioningControlB(dataset);

        Vector<GCP> gcps = new Vector<GCP>(4);
        String[] ids = new String[] {
                "UpperLeft", "UpperRight", "LowerRight", "LowerLeft"
        };

        final double fullImageWidth;
        final double fullImageHeight;
        if(ichipb == null) {
            fullImageWidth = (double) dataset.GetRasterXSize() - 0.5d;
            fullImageHeight = (double) dataset.GetRasterYSize() - 0.5d;
        } else {
            fullImageWidth = ichipb.fi_col;
            fullImageHeight = ichipb.fi_row;
        }

        double[] pixel = new double[] {
                0.5, fullImageWidth
        };
        double[] line = new double[] {
                0.5, fullImageHeight
        };

        GeoPoint p;
        for (int i = 0; i < 4; i++) {
            p = rpc00b.inverse(new PointD(pixel[((i + (i / 2)) % 2)], line[(i / 2)]));

            gcps.add(new GCP(p.getLongitude(), p.getLatitude(), pixel[((i + (i / 2)) % 2)],
                    line[(i / 2)], "RPC00B", ids[i]));
        }

        return gcps;
    }

    private static String getString(ByteBuffer buffer, int length) {
        if (buffer.remaining() < length)
            throw new BufferUnderflowException();
        final String retval = new String(buffer.array(), buffer.position(), length, FileSystemUtils.UTF8_CHARSET);
        skip(buffer, length);
        return retval;
    }

    private static Buffer skip(ByteBuffer buffer, int length) {
        if (buffer.remaining() < length)
            throw new BufferUnderflowException();
        return buffer.position(buffer.position() + length);
    }

    /**************************************************************************/

    private static class GeoTransform extends GdalDatasetProjection2 {
        private double[] img2proj;
        private double[] proj2img;

        private GeoTransform(Dataset dataset) {
            super(new SpatialReference(dataset.GetProjectionRef()));

            this.img2proj = dataset.GetGeoTransform();
            this.proj2img = gdal.InvGeoTransform(this.img2proj);
        }

        @Override
        protected PointD projected2image(PointD p) {
            final double imgX = this.proj2img[0] + p.x * this.proj2img[1] + p.y * this.proj2img[2];
            final double imgY = this.proj2img[3] + p.x * this.proj2img[4] + p.y * this.proj2img[5];

            return new PointD(imgX, imgY);
        }

        @Override
        protected PointD image2projected(PointD p) {
            final double projX = this.img2proj[0] + p.x * this.img2proj[1] + p.y * this.img2proj[2];
            final double projY = this.img2proj[3] + p.x * this.img2proj[4] + p.y * this.img2proj[5];

            return new PointD(projX, projY);
        }
    }

    private static class GroundControlPoints extends GdalDatasetProjection2 {
        private Matrix img2proj;
        private Matrix proj2img;

        private GroundControlPoints(Dataset dataset) throws NoninvertibleTransformException {
            this(dataset, dataset.GetGCPs(), dataset.GetGCPProjection(), null);
        }

        private GroundControlPoints(Dataset dataset, Vector<GCP> gcps, String projectionRef,
                ICHIPB ichipb) throws NoninvertibleTransformException {
            super(new SpatialReference(projectionRef));

            final Map<String, Integer> id2idx = new HashMap<String, Integer>();
            id2idx.put("UpperLeft", Integer.valueOf(0));
            id2idx.put("UpperRight", Integer.valueOf(1));
            id2idx.put("LowerRight", Integer.valueOf(2));
            id2idx.put("LowerLeft", Integer.valueOf(3));

            PointD[][] src2dst = new PointD[4][2];
            GCP gcp;
            String id;
            int idx;
            for (int i = 0; i < gcps.size(); i++) {
                gcp = gcps.get(i);
                id = gcp.getId();
                if (!id2idx.containsKey(id))
                    continue;
                idx = id2idx.get(id).intValue();
                if (src2dst[idx][0] != null)
                    continue;
                src2dst[idx][0] = new PointD(gcp.getGCPPixel(), gcp.getGCPLine());
                src2dst[idx][1] = new PointD(gcp.getGCPX(), gcp.getGCPY());
            }

            this.img2proj = Matrix.mapQuads(src2dst[0][0], src2dst[1][0], src2dst[2][0],
                    src2dst[3][0],
                    src2dst[0][1], src2dst[1][1], src2dst[2][1], src2dst[3][1]);

            if (ichipb != null)
                this.img2proj.concatenate(ichipb.getTransform());

            this.proj2img = this.img2proj.createInverse();
        }

        @Override
        protected PointD projected2image(PointD p) {
            return this.proj2img.transform(p, null);
        }

        @Override
        protected PointD image2projected(PointD p) {
            return this.img2proj.transform(p, null);
        }
    }

    private static class ICHIPB {

        private Matrix op2fi;
        final int fi_row;
        final int fi_col;
        
        // XXX - Consider not assigning/removing unreferenced variables from this class if possible.
        @SuppressWarnings("unused")
        public ICHIPB(ByteBuffer ichipb) {
            final int xform_flag = Integer.parseInt(getString(ichipb, 2));
            final double scale_factor = Double.parseDouble(getString(ichipb, 10));
            final int anamrph_corr = Integer.parseInt(getString(ichipb, 2));
            final int scanblk_num = Integer.parseInt(getString(ichipb, 2));
            final double op_row_11 = Double.parseDouble(getString(ichipb, 12));
            final double op_col_11 = Double.parseDouble(getString(ichipb, 12));
            final double op_row_12 = Double.parseDouble(getString(ichipb, 12));
            final double op_col_12 = Double.parseDouble(getString(ichipb, 12));
            final double op_row_21 = Double.parseDouble(getString(ichipb, 12));
            final double op_col_21 = Double.parseDouble(getString(ichipb, 12));
            final double op_row_22 = Double.parseDouble(getString(ichipb, 12));
            final double op_col_22 = Double.parseDouble(getString(ichipb, 12));
            final double fi_row_11 = Double.parseDouble(getString(ichipb, 12));
            final double fi_col_11 = Double.parseDouble(getString(ichipb, 12));
            final double fi_row_12 = Double.parseDouble(getString(ichipb, 12));
            final double fi_col_12 = Double.parseDouble(getString(ichipb, 12));
            final double fi_row_21 = Double.parseDouble(getString(ichipb, 12));
            final double fi_col_21 = Double.parseDouble(getString(ichipb, 12));
            final double fi_row_22 = Double.parseDouble(getString(ichipb, 12));
            final double fi_col_22 = Double.parseDouble(getString(ichipb, 12));
            fi_row = Integer.parseInt(getString(ichipb, 8));
            fi_col = Integer.parseInt(getString(ichipb, 8));

            this.op2fi = Matrix.mapQuads(op_col_11, op_row_11,
                    op_col_12, op_row_12,
                    op_col_22, op_row_22,
                    op_col_21, op_row_21,
                    fi_col_11, fi_row_11,
                    fi_col_12, fi_row_12,
                    fi_col_22, fi_row_22,
                    fi_col_21, fi_row_21);
        }

        public Matrix getTransform() {
            return this.op2fi;
        }
    }
}

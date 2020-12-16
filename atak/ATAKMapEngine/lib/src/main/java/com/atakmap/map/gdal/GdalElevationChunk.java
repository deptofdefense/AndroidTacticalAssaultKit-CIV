package com.atakmap.map.gdal;

import com.atakmap.coremap.io.IOProvider;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.lang.Unsafe;
import com.atakmap.map.elevation.ElevationChunk;
import com.atakmap.map.layer.feature.geometry.Polygon;
import com.atakmap.map.layer.raster.DatasetDescriptor;
import com.atakmap.map.layer.raster.DatasetProjection2;
import com.atakmap.map.layer.raster.gdal.GdalDatasetProjection2;
import com.atakmap.math.PointD;

import org.gdal.gdal.gdal;
import org.gdal.gdalconst.gdalconst;
import org.gdal.gdal.Dataset;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public final class GdalElevationChunk extends ElevationChunk.Factory.Sampler {

    private Dataset dataset;
    private DatasetProjection2 proj;
    private int width;
    private int height;
    private double noDataValue;

    private GdalElevationChunk(Dataset dataset, DatasetProjection2 proj) {
        this.dataset = dataset;
        this.proj = proj;
        this.width = dataset.GetRasterXSize();
        this.height = dataset.GetRasterYSize();

        // query the "No Data Value"
        Double[] ndv = new Double[1];
        dataset.GetRasterBand(1).GetNoDataValue(ndv);
        noDataValue = (ndv[0] != null) ? ndv[0] : Double.NaN;
    }

    @Override
    public double sample(double latitude, double longitude) {
        // run elevation G2I to discover pixel
        PointD img = new PointD(0d, 0d, 0d);
        if(!this.proj.groundToImage(new GeoPoint(latitude, longitude), img))
            return Double.NaN;

        // sample elevation at pixel

        // handle out of bounds
        if (img.x < 0 || img.x >= width)
            return Double.NaN;
        if (img.y < 0 || img.y >= height)
            return Double.NaN;

        final int dataType = dataset.GetRasterBand(1).getDataType();
        ByteBuffer arr = null;
        try {
            final int w = Math.min(2, width-(int)img.x);
            final int h = Math.min(2, height-(int)img.y);

            arr = ByteBuffer.allocateDirect((w*h)*8); // up to 4 samples
            arr.order(ByteOrder.nativeOrder());

            // read the pixel
            final int success = dataset.ReadRaster_Direct(
                    (int) img.x, (int) img.y, w, h, // src x,y,w,h
                    w, h, // dst w,h
                    dataType,
                    arr,
                    new int[]{
                            1
                    } // bands
            );
            if (success != gdalconst.CE_None)
                return Double.NaN;

            arr.clear();

            double[] samples = new double[w*h];
            if (dataType == gdalconst.GDT_Byte)
                for(int i = 0; i < (w*h); i++)
                    samples[i] = (arr.get() & 0xFF);
            else if (dataType == gdalconst.GDT_UInt16)
                for(int i = 0; i < (w*h); i++)
                    samples[i] = (arr.getShort() & 0xFFFF);
            else if (dataType == gdalconst.GDT_Int16)
                for(int i = 0; i < (w*h); i++)
                    samples[i]  = arr.getShort();
            else if (dataType == gdalconst.GDT_UInt32)
                for(int i = 0; i < (w*h); i++)
                    samples[i]  = ((long) arr.getInt() & 0xFFFFFFFFL);
            else if (dataType == gdalconst.GDT_Int32)
                for(int i = 0; i < (w*h); i++)
                    samples[i] = arr.getInt();
            else if (dataType == gdalconst.GDT_Float32)
                for(int i = 0; i < (w*h); i++)
                    samples[i] = arr.getFloat();
            else if (dataType == gdalconst.GDT_Float64)
                for(int i = 0; i < (w*h); i++)
                    samples[i] = arr.getDouble();
            else
                return Double.NaN;

            int ndvCount = 0;
            for(int i = 0; i < (w*h); i++)
                if(samples[i] == noDataValue)
                    ndvCount++;

            // check if all are NDV
            if(ndvCount == (w*h))
                return Double.NaN;

            // interpolate the samples
            double[] weights = new double[w*h];
            final double[] weight_x = new double[] {1d-(img.x-(int)img.x), (img.x-(int)img.x)};
            final double[] weight_y = new double[] {1d-(img.y-(int)img.y), (img.y-(int)img.y)};

            int idx = 0;
            for(int i = 0; i < h; i++)
                for(int j = 0; j < w; j++)
                    weights[idx++] = weight_x[j]*weight_y[i];

            // if there are any NDVs, fill the voids with the average
            if(ndvCount > 0) {
                double avg = 0d;
                for(int i = 0; i < (w*h); i++) {
                    if(samples[i] == noDataValue)
                        continue;
                    avg += samples[i] / ((w*h)-ndvCount);
                }

                for(int i = 0; i < (w*h); i++) {
                    if (samples[i] != noDataValue)
                        samples[i] = avg;
                }
            }

            double retval = 0d;
            for(int i = 0; i < (w*h); i++) {
                if(samples[i] == noDataValue)
                    continue;
                retval += samples[i]*weights[i];
            }

            return retval;
        } finally {
            if(arr != null)
                Unsafe.free(arr);
        }
    }

    @Override
    public void dispose() {
        if(this.proj != null) {
            this.proj.release();
            this.proj = null;
        }
        if(this.dataset != null) {
            this.dataset.delete();
            this.dataset = null;
        }
    }

    public static ElevationChunk create(String path, String type, double resolution, int hints) {
        final File f = new File(path);
        if(!IOProviderFactory.exists(f))
            return null;
        else
            return create(GdalLibrary.openDatasetFromFile(f), true, type, resolution, hints);
    }

    public static ElevationChunk create(Dataset dataset, boolean deleteOnFail, String type, double resolution, int hints) {
        if(dataset == null)
            return null;

        final DatasetProjection2 proj = GdalDatasetProjection2.getInstance(dataset);
        if (proj == null) {
            if(deleteOnFail)
                dataset.delete();
            return null;
        }

        final GdalElevationChunk retval = new GdalElevationChunk(dataset, proj);

        GeoPoint ul = GeoPoint.createMutable();
        retval.proj.imageToGround(new PointD(0, 0), ul);
        GeoPoint ur = GeoPoint.createMutable();
        retval.proj.imageToGround(new PointD(retval.width, 0), ur);
        GeoPoint lr = GeoPoint.createMutable();
        retval.proj.imageToGround(new PointD(retval.width, retval.height), lr);
        GeoPoint ll = GeoPoint.createMutable();
        retval.proj.imageToGround(new PointD(0, retval.height), ll);

        try { 
            if(type == null)
                type = retval.dataset.GetDriver().GetDescription();
        } catch (NullPointerException ignored) { 
             // on the insiginifcant chance that GetDriver() returns null, just continue without
             // the type set.
        }
        if(Double.isNaN(resolution))
            resolution = DatasetDescriptor.computeGSD(retval.width, retval.height, ul, ur, lr, ll);
        return ElevationChunk.Factory.create(type, retval.dataset.GetDescription(), hints, resolution, (Polygon)DatasetDescriptor.createSimpleCoverage(ul, ur, lr, ll), Double.NaN, Double.NaN, true, retval);
    }
}

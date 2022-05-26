
package com.atakmap.map.layer.raster;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.filesystem.FileSystemUtils;

import android.util.Pair;

import com.atakmap.coremap.maps.coords.GeoCalculations;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.spatial.CoverageAccumulator;
import com.atakmap.map.layer.feature.geometry.Envelope;
import com.atakmap.map.layer.feature.geometry.Geometry;
import com.atakmap.map.layer.feature.geometry.GeometryFactory;
import com.atakmap.map.layer.feature.geometry.LineString;
import com.atakmap.map.layer.feature.geometry.Polygon;
import com.atakmap.spatial.SpatialCalculator;

/**
 * Immutable descriptor object for a dataset. Defines a number of common
 * properties over a dataset (e.g. name, resolution, URI, etc.) and also
 * provides a mechanism for storage of opaque application specific data.
 * 
 * <P>The descriptor may retain ownership over a <I>working directory</I>. This
 * directory is guaranteed to remain unmodified by the application for the
 * lifetime of the descriptor, including between runtime invocations of the
 * application so long as the descriptor still exists and is valid (e.g. it is
 * stored in persistent storage on the local filesystem such as SQLite). This
 * directory may be used by dataset service providers to store information and
 * data associated with the dataset. There should be an expectation that no
 * service provider instance for any other dataset descriptor will access the
 * directory, nor will any data for any dataset descriptor ever be written to
 * the directory. Example uses would be a catalog for a dataset composed of
 * adjacent images or a cache for subsampled tiles of high-resolution imagery.
 * 
 * @author Developer
 */
public abstract class DatasetDescriptor {

    private final static String TAG = "DatasetDescriptor";

    private final static int TYPE_IMAGE = 0;
    private final static int TYPE_MOSAIC = 1;

    public final static Charset URI_CHARACTER_ENCODING = FileSystemUtils.UTF8_CHARSET;

    /** the layer name */
    private final String _name;
    /** the URI to the layer on the device. */
    private final String _uri;

    private final String datasetType;
    private final Collection<String> imageryTypes;
    private final Map<String, Geometry> coverages;

    private final Map<String, Pair<Double, Double>> resolutions;

    /** the spatial reference ID for the native projection of the layer */
    private final int spatialReferenceID;

    /** the layer type */
    private final String provider;

    private final Map<String, String> extraData;

    private final Map<String, Object> localData;

    private long layerId;

    private final boolean isRemote;
    
    private final File workingDirectory;

    private Envelope minimumBoundingBox;

    /**************************************************************************/

    /**
     * Creates a new instance.
     * 
     * @param layerId       The ID for the descriptor
     * @param name          The name of the datset
     * @param uri           The URI for the dataset.
     * @param provider      The name of service provider responsible for
     *                      creating this instance
     * @param datasetType   The dataset type
     * @param imageryTypes  The imagery types available for the dataset
     * @param resolutions   The resolutions for each imagery type in the dataset
     * @param coverages     The coverage for each imagery type in the dataset
     * @param srid          The Spatial Reference ID for the dataset or
     *                      <code>-1</code> if unknown or not well-defined.
     * @param isRemote      A flag indicating whether or not the dataset content
     *                      if local or remote
     * @param workingDir    The working directory for the dataset
     * @param extraData     Opaque application specified data associated with
     *                      the dataset.
     */
    DatasetDescriptor(long layerId, String name, String uri, String provider, String datasetType, Collection<String> imageryTypes,
            Map<String, Pair<Double, Double>> resolutions, Map<String, Geometry> coverages, int srid, boolean isRemote,
            File workingDir, Map<String, String> extraData) {

        this.layerId = layerId;
        _name = name;
        _uri = uri;
        this.provider = provider;
        this.datasetType = datasetType;
        this.spatialReferenceID = srid;
        this.isRemote = isRemote;
        this.workingDirectory = workingDir;
        this.extraData = extraData;
        
        if(imageryTypes.size() < 1)
            throw new IllegalArgumentException("Failed to create " + name + " " + uri);

        this.imageryTypes = new HashSet<String>();
        this.resolutions = new HashMap<String, Pair<Double, Double>>();
        this.coverages = new HashMap<String, Geometry>();
        Pair<Double, Double> resolution;
        Geometry coverage = null;
        this.minimumBoundingBox = null;
        
        double minRes = Double.NaN;
        double maxRes = Double.NaN;        
        
        for(String type : imageryTypes) {
            resolution = resolutions.get(type);
            if(resolution == null)
                throw new IllegalArgumentException();
            coverage = coverages.get(type);
            if(coverage == null)
                throw new IllegalArgumentException();
            
            this.imageryTypes.add(type);
            this.coverages.put(type, coverage);
            this.resolutions.put(type, resolution);
            
            if(Double.isNaN(minRes) || Double.isNaN(maxRes)) {
                minRes = resolution.first.doubleValue();
                maxRes = resolution.second.doubleValue();
            } else {
                if(resolution.first.doubleValue() > minRes)
                    minRes = resolution.first.doubleValue();
                if(resolution.second.doubleValue() < maxRes)
                    maxRes = resolution.second.doubleValue();
            }
        }
        
        Geometry combinedCoverage = null;
        if(this.imageryTypes.size() == 1) {
            // if there is only one type, then we've assigned the coverage
            // up above
            combinedCoverage = coverage;
        } else if(coverages.containsKey(null)) {
            // use client supplied coverage
            combinedCoverage = coverages.get(null);
        } else {
            SpatialCalculator calc = new SpatialCalculator();
            try {
                calc.beginBatch();

                CoverageAccumulator acc = new CoverageAccumulator(calc);
                for(Geometry cov : this.coverages.values()) {
                    acc.addBounds(cov);
                }
    
                combinedCoverage = acc.getCoverageGeometry();
            } finally {
                calc.endBatch(false);
                calc.dispose();
            }
        }
        if (combinedCoverage != null) {  
            this.minimumBoundingBox = combinedCoverage.getEnvelope();
            this.coverages.put(null, combinedCoverage);
         
            this.resolutions.put(null, Pair.create(Double.valueOf(minRes), Double.valueOf(maxRes)));
        }
        
        this.localData = new HashMap<String, Object>();
    }

    /**************************************************************************/

    /**
     * Returns the name of the dataset.
     * 
     * @return  The name of the dataset.
     */
    public String getName() {
        return _name;
    }

    /**
     * Returns the name of the service provider that was responsible for
     * creating the dataset descriptor.
     * 
     * @return  The name of the service provider that created the descriptor
     */
    public String getProvider() {
        return this.provider;
    }

    /**
     * Returns the type of the dataset. The dataset type will drive which
     * service providers are selected for activities such as rendering the
     * dataset and performing the image-to-ground and ground-to-image functions
     * on the dataset.
     * 
     * @return  The type of the dataset
     */
    public String getDatasetType() {
        return this.datasetType;
    }

    /**
     * Returns the imagery types available for the dataset. These types should
     * be relatively well-defined (e.g. cib1, onc, tpc).
     * 
     * @return  The imagery types available for the dataset.
     */
    public Collection<String> getImageryTypes() {
        return Collections.unmodifiableCollection(this.imageryTypes);
    }

    /**
     * Returns the minimum resolution, in meters-per-pixel, for the specified
     * imagery type. Data for the specified type should not be displayed when
     * the map resolution is lower than the returned value.
     * 
     * @param type  The imagery type or <code>null</code> to return the minimum
     *              resolution for the dataset.
     *              
     * @return  The minimum resolution, in meters-per-pixel, for the specified
     *          imagery type
     */
    public double getMinResolution(String type) {
        Pair<Double, Double> retval = this.resolutions.get(type);
        if(retval == null)
            return Double.NaN;
        return retval.first.doubleValue();
    }

    /**
     * Returns the maximum resolution, in meters-per-pixel, for the specified
     * imagery type.
     * 
     * @param type  The imagery type or <code>null</code> to return the maximum
     *              resolution for the dataset.
     *              
     * @return  The maximum resolution, in meters-per-pixel, for the specified
     *          imagery type
     */
    public double getMaxResolution(String type) {
        Pair<Double, Double> retval = this.resolutions.get(type);
        if(retval == null)
            return Double.NaN;
        return retval.second.doubleValue();
    }

    /**
     * Returns the coverage for the specified imagery type.
     * 
     * @param type  The imagery type or <code>null</code> to return the coverage
     *              the dataset across all imagery types.
     *              
     * @return  The coverage for the specified imagery type
     */
    public Geometry getCoverage(String type) {
        return this.coverages.get(type);
    }
    
    /**
     * Returns a flag indicating whether or not the dataset is remote.
     * 
     * @return  <code>true</code> if the dataset is remote, <code>false</code>
     *          if the dataset resides on the local filesystem.
     */
    public boolean isRemote() {
        return isRemote;
    }

    /**
     * Returns the URI for the dataset.
     * 
     * @return  The URI for the dataset.
     */
    public String getUri() {
        return _uri;
    }

    /**
     * Returns the spatial reference ID for the dataset.
     * 
     * @return  The spatial reference ID for the dataset or <code>-1</code> if
     *          the spatial reference is not well-defined.
     */
    public int getSpatialReferenceID() {
        return this.spatialReferenceID;
    }

    /**
     * Returns the extra data associated with the specified key. The extra data
     * is opaque application specific data that may only be correctly
     * interpreted by the relevant service providers.
     * 
     * @param key   The key
     * 
     * @return  The extra data associated with the specified key.
     */
    public String getExtraData(String key) {
        return this.extraData.get(key);
    }
    
    public Map<String, String> getExtraData(){
        Map<String, String> retval = new HashMap<String, String>();
        retval.putAll(extraData);
        return retval;
    }

    /**
     * Set extra data associated with a specific key. Extra data is not
     * automatically persisted to the database here.
     *
     * @param key Data key
     * @param value Data string
     */
    public void setExtraData(String key, String value) {
        this.extraData.put(key, value);
    }

    /**
     * Returns the ID assigned by the data store that the dataset descriptor
     * resides in. 
     * 
     * @return  The ID for the dataset descriptor. A value of <code>0</code>
     *          is reserved and indicates that the dataset descriptor does not
     *          belong to a data store.
     */
    public long getLayerId() {
        return this.layerId;
    }
    
    /**
     * Returns the minimum bounding box for the dataset.
     * 
     * @return  The minimum bounding box for the dataset.
     */
    public Envelope getMinimumBoundingBox() {
        return this.minimumBoundingBox;
    }

    /**
     * Returns the working directory for the dataset. Generally, contents of the
     * working directory should only be modified by applicable service
     * providers.
     * 
     * @return  The working directory for the dataset descriptor or
     *          <code>null</code> if the dataset descriptor does not have a
     *          working directory. 
     */
    public File getWorkingDirectory() {
        return this.workingDirectory;
    }

    /**
     * Encodes the descriptor as a byte array, assigning the specified ID to
     * the result. The descriptor that will result from a subsequent decode of
     * the byte array will have the specified ID.
     *  
     * @param layerId   The ID
     * 
     * @return  The descriptor, assigned the specified ID, encoded as a byte
     *          array.
     *          
     * @throws IOException  If an I/O error occurs during encoding
     */
    public byte[] encode(long layerId) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            this.encode(layerId, baos);
            return baos.toByteArray();
        } finally {
            baos.close();
        }
    }

    /**
     * Encodes the descriptor to the specified
     * {@link java.io.OutputStream OutputStream}, assigning the specified ID to
     * the result. The descriptor that will result from a subsequent decode of
     * the stream contents will have the specified ID.
     * 
     * @param outputStream  An <code>OutputStream</code>; this method will not
     *                      close or flush <code>outputStream</code> prior to
     *                      returning
     *
     * @throws IOException  thrown if an I/O error occurs during encode.
     */
    public void encode(long id, OutputStream outputStream) throws IOException {
        DataOutputStream dos = null;

        if (outputStream instanceof DataOutputStream)
            dos = (DataOutputStream) outputStream;
        else
            dos = new DataOutputStream(outputStream);
        
        // write the header
        dos.write(0x00);
        dos.write(0x01);
        dos.write(0xFF);
        dos.write(0x09); // version byte
        
        if(this instanceof ImageDatasetDescriptor)
            dos.writeInt(TYPE_IMAGE);
        else if(this instanceof MosaicDatasetDescriptor)
            dos.writeInt(TYPE_MOSAIC);
        
        dos.writeLong(id);
        dos.writeUTF(_name);
        dos.writeUTF(_uri);
        dos.writeUTF(this.provider);
        dos.writeUTF(this.datasetType);
        
        dos.writeInt(this.imageryTypes.size());

        Pair<Double, Double> resolution;
        Geometry coverage;
        byte[] coverageWkb = new byte[] {};
        int coverageWkbSize;
        for(String s : this.imageryTypes) {
            dos.writeUTF(s);
            resolution = this.resolutions.get(s);
            dos.writeDouble(resolution.first.doubleValue());
            dos.writeDouble(resolution.second.doubleValue());
            coverage = this.coverages.get(s);
            coverageWkbSize = coverage.computeWkbSize();
            if(coverageWkb.length < coverageWkbSize)
                coverageWkb = new byte[coverageWkbSize];
            coverage.toWkb(ByteBuffer.wrap(coverageWkb));
            dos.writeInt(coverageWkbSize);
            dos.write(coverageWkb, 0, coverageWkbSize);
        }

        coverage = this.coverages.get(null);
        coverageWkbSize = coverage.computeWkbSize();
        if(coverageWkb.length < coverageWkbSize)
            coverageWkb = new byte[coverageWkbSize];
        coverage.toWkb(ByteBuffer.wrap(coverageWkb));
        dos.writeInt(coverageWkbSize);
        dos.write(coverageWkb, 0, coverageWkbSize);

        
        dos.writeInt(this.spatialReferenceID);
        dos.writeBoolean(this.isRemote);
        dos.writeBoolean(this.workingDirectory != null);
        if(this.workingDirectory != null)
            dos.writeUTF(this.workingDirectory.getAbsolutePath());
        
        dos.writeInt(this.extraData.size());
        for(Map.Entry<String, String> entry : this.extraData.entrySet()) {
            dos.writeUTF(entry.getKey());
            dos.writeUTF(entry.getValue());
        }

        // XXX - ew
        if(this instanceof ImageDatasetDescriptor) {
            final ImageDatasetDescriptor image = (ImageDatasetDescriptor)this;
            dos.writeInt(image.getWidth());
            dos.writeInt(image.getHeight());
            dos.writeBoolean(image.isPrecisionImagery());
        } else if(this instanceof MosaicDatasetDescriptor) {
            final MosaicDatasetDescriptor mosaic = (MosaicDatasetDescriptor)this;
            final File mosaicDbFile = mosaic.getMosaicDatabaseFile();
            dos.writeBoolean(mosaicDbFile != null);
            if(mosaicDbFile != null)
                dos.writeUTF(mosaicDbFile.getAbsolutePath());
            final String mosaicDbProvider = mosaic.getMosaicDatabaseProvider();
            dos.writeBoolean(mosaicDbProvider != null);
            if(mosaicDbProvider != null)
                dos.writeUTF(mosaicDbProvider);
        }
        if (dos != outputStream)
            dos.flush();
    }

    /**
     * Retrieves the specified local data for this <code>DatasetDescriptor</code>. Local
     * data is runtime information that external objects may associate with an
     * instance.
     * 
     * <P>Local data is only valid for the instance and will not persist between
     * invocations of the software.
     * 
     * @param key   The key
     * 
     * @return  The associated value or <code>null</code> if no value is
     *          associated with the specified key.
     */
    public Object getLocalData(String key) {
        return this.localData.get(key);
    }

    /**
     * Retrieves the specified local data for this <code>DatasetDescriptor</code>. Local
     * data is runtime information that external objects may associate with an
     * instance.
     * 
     * <P>Local data is only valid for the instance and will not persist between
     * invocations of the software.
     * 
     * @param key   The key
     * @param clazz The type for the return value
     * 
     * @return  The associated value, cast to {@link Class} <code>clazz</code>,
     *          or <code>null</code> if there is no value associated with the
     *          specified key
     *          
     * @throws ClassCastException   If the object associated with the key cannot
     *                              be cast to {@link Class} <code>clazz</code>.
     */
    public <T> T getLocalData(String key, Class<T> clazz) {
        return clazz.cast(this.getLocalData(key));
    }

    /**
     * Set the specified local data for this <code>DatasetDescriptor</code>. Local
     * data is runtime information that external objects may associate with an
     * instance.
     * 
     * <P>Local data is only valid for the instance and will not persist between
     * invocations of the software.
     * 
     * @param key   The key
     * @param value The value
     * 
     * @return  The old value associated with the specified key or
     *          <code>null</code> if no value was previously associated with the
     *          key.
     */
    public Object setLocalData(String key, Object value) {
        return this.localData.put(key, value);
    }

    /**************************************************************************/
    // Object

    @Override
    public String toString() {
        return _uri;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof DatasetDescriptor))
            return false;
        return ((DatasetDescriptor) other)._uri.equals(_uri);
    }

    @Override
    public int hashCode() {
        return _uri.hashCode();
    }

    /*************************************************************************/

    /**
     * Decodes a dataset descriptor from the specified byte array.
     * 
     * @param bytes A byte array
     * 
     * @return  The decoded descriptor
     * 
     * @throws IOException  If an I/O error occurs decoding the descriptor
     */
    public static DatasetDescriptor decode(byte[] bytes) throws IOException {
        ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
        try {
            return decode(bais);
        } finally {
            bais.close();
        }
    }

    /**
     * Decodes a dataset descriptor from the specified
     * {@link java.io.InputStream InputStream}.
     * 
     * @param s An {@link java.io.InputStream InputStream}
     * 
     * @return  The decoded descriptor
     * 
     * @throws IOException  If an I/O error occurs decoding the descriptor
     */
    public static DatasetDescriptor decode(InputStream s) throws IOException {
        byte[] header = new byte[3];
        int n = s.read(header);
        if (n < 3)
            throw new EOFException("Unexpected end of file.");

        if (header[0] == 0x00 && header[1] == 0x01 && (header[2] & 0xFF) == 0xFF) {
            switch (s.read()) {
                case 0x07:
                    return decodeVersion7(s);
                case 0x08:
                    return decodeVersion8(s);
                case 0x09:
                    return decodeVersion9(s);
                case -1:
                    throw new EOFException("Unexpected end of file.");
                case 0x01:
                case 0x02:
                case 0x03:
                case 0x04:
                case 0x05 :
                case 0x06 :
                default:
                    throw new UnsupportedOperationException("Unsupported DatasetDescriptor serialization.");
            }
        } else {
            throw new UnsupportedOperationException("Unsupported DatasetDescriptor serialization.");
        }
    }

    private static DatasetDescriptor decodeVersion7(InputStream s) throws IOException {
        DataInputStream dis = null;

        if (s instanceof DataInputStream)
            dis = (DataInputStream) s;
        else
            dis = new DataInputStream(s);

        final int descriptorType = dis.readInt();
        final long layerId = dis.readLong(); 
        final String name = dis.readUTF();
        final String uri = dis.readUTF();
        final String provider = dis.readUTF();
        final String datasetType = dis.readUTF();

        final int numImageryTypes = dis.readInt();

        Set<String> imageryTypes = new HashSet<String>();
        Map<String, Pair<Double, Double>> resolutions = new HashMap<String, Pair<Double, Double>>();
        Map<String, Geometry> coverages = new HashMap<String, Geometry>();
        String type;
        byte[] coverageWkb = new byte[] {};
        int coverageWkbSize;
        Geometry coverage;
        for(int i = 0; i < numImageryTypes; i++) {
            type = dis.readUTF();
            
            imageryTypes.add(type);
            resolutions.put(type, Pair.create(Double.valueOf(dis.readDouble()), Double.valueOf(dis.readDouble())));
            
            coverageWkbSize = dis.readInt();
            if(coverageWkb.length < coverageWkbSize)
                coverageWkb = new byte[coverageWkbSize];

            int r = dis.read(coverageWkb, 0, coverageWkbSize);
            if (r != coverageWkbSize) 
                Log.d(TAG, "coverageWkb, read: " + r + " expected: " +coverageWkbSize);

            coverage = GeometryFactory.parseWkb(ByteBuffer.wrap(coverageWkb, 0, coverageWkbSize));
            if(coverage == null)
                throw new RuntimeException();
            
            coverages.put(type, coverage);
        }

        final int srid = dis.readInt();
        final boolean isRemote = dis.readBoolean();
        final File workingDir = dis.readBoolean() ? new File(FileSystemUtils.sanitizeWithSpacesAndSlashes(dis.readUTF())) : null;
        
        Map<String, String> extraData = new HashMap<String, String>();
        final int extraDataSize = dis.readInt();
        
        for(int i = 0; i < extraDataSize; i++)
            extraData.put(dis.readUTF(), dis.readUTF());
        
        switch(descriptorType) {
            case TYPE_IMAGE :
            {
                final int width = dis.readInt();
                final int height = dis.readInt();

                final boolean isPrecisionImagery = PrecisionImageryFactory.isSupported(uri);
                
                final String imageryType = imageryTypes.iterator().next();

                return new ImageDatasetDescriptor(layerId,
                                                  name,
                                                  uri,
                                                  provider,
                                                  datasetType,
                                                  imageryType,
                                                  resolutions.get(imageryType).first.doubleValue(),
                                                  resolutions.get(imageryType).second.doubleValue(),
                                                  coverages.get(imageryType),
                                                  srid,
                                                  isRemote,
                                                  workingDir,
                                                  extraData,
                                                  width,
                                                  height,
                                                  isPrecisionImagery);
            }
            case TYPE_MOSAIC :
            {
                final File mosaicDbFile = dis.readBoolean() ? new File(FileSystemUtils.sanitizeWithSpacesAndSlashes(dis.readUTF())) : null;
                final String mosaicDbProvider = dis.readBoolean() ? dis.readUTF() : null;
                
                return new MosaicDatasetDescriptor(layerId,
                                                   name,
                                                   uri,
                                                   provider,
                                                   datasetType,
                                                   mosaicDbFile,
                                                   mosaicDbProvider,
                                                   imageryTypes,
                                                   resolutions,
                                                   coverages,
                                                   srid,
                                                   isRemote,
                                                   workingDir,
                                                   extraData);
            }
            default :
                throw new IllegalStateException();
        }
    }
    
    private static DatasetDescriptor decodeVersion8(InputStream s) throws IOException {
        DataInputStream dis = null;

        if (s instanceof DataInputStream)
            dis = (DataInputStream) s;
        else
            dis = new DataInputStream(s);

        final int descriptorType = dis.readInt();
        final long layerId = dis.readLong(); 
        final String name = dis.readUTF();
        final String uri = dis.readUTF();
        final String provider = dis.readUTF();
        final String datasetType = dis.readUTF();

        final int numImageryTypes = dis.readInt();

        Set<String> imageryTypes = new HashSet<String>();
        Map<String, Pair<Double, Double>> resolutions = new HashMap<String, Pair<Double, Double>>();
        Map<String, Geometry> coverages = new HashMap<String, Geometry>();
        String type;
        byte[] coverageWkb = new byte[] {};
        int coverageWkbSize;
        Geometry coverage;
        for(int i = 0; i < numImageryTypes; i++) {
            type = dis.readUTF();
            
            imageryTypes.add(type);
            resolutions.put(type, Pair.create(Double.valueOf(dis.readDouble()), Double.valueOf(dis.readDouble())));
            
            coverageWkbSize = dis.readInt();
            if(coverageWkb.length < coverageWkbSize)
                coverageWkb = new byte[coverageWkbSize];

            int numRead = dis.read(coverageWkb, 0, coverageWkbSize);
            if (numRead != coverageWkbSize) { 
                Log.d(TAG, "coverageWkb (numImageryTypes=" + i + "), read: " + numRead + " expected: " +coverageWkbSize);
            }
            coverage = GeometryFactory.parseWkb(ByteBuffer.wrap(coverageWkb, 0, coverageWkbSize));
            if(coverage == null)
                throw new RuntimeException();
            
            coverages.put(type, coverage);
        }
        
        // aggregate coverage
        coverageWkbSize = dis.readInt();
        if(coverageWkb.length < coverageWkbSize)
            coverageWkb = new byte[coverageWkbSize];

        int numRead = dis.read(coverageWkb, 0, coverageWkbSize);
        if (numRead != coverageWkbSize) { 
            Log.d(TAG, "coverageWkb (aggregate), read: " + numRead + " expected: " +coverageWkbSize);
        }
        coverage = GeometryFactory.parseWkb(ByteBuffer.wrap(coverageWkb, 0, coverageWkbSize));
        if(coverage == null)
            throw new RuntimeException();
        
        coverages.put(null, coverage);

        final int srid = dis.readInt();
        final boolean isRemote = dis.readBoolean();
        final File workingDir = dis.readBoolean() ? new File(FileSystemUtils.sanitizeWithSpacesAndSlashes(dis.readUTF())) : null;
        
        Map<String, String> extraData = new HashMap<String, String>();
        final int extraDataSize = dis.readInt();
        
        for(int i = 0; i < extraDataSize; i++)
            extraData.put(dis.readUTF(), dis.readUTF());
        
        switch(descriptorType) {
            case TYPE_IMAGE :
            {
                final int width = dis.readInt();
                final int height = dis.readInt();

                final String imageryType = imageryTypes.iterator().next();

                final boolean isPrecisionImagery = PrecisionImageryFactory.isSupported(uri);

                return new ImageDatasetDescriptor(layerId,
                                                  name,
                                                  uri,
                                                  provider,
                                                  datasetType,
                                                  imageryType,
                                                  resolutions.get(imageryType).first.doubleValue(),
                                                  resolutions.get(imageryType).second.doubleValue(),
                                                  coverages.get(imageryType),
                                                  srid,
                                                  isRemote,
                                                  workingDir,
                                                  extraData,
                                                  width,
                                                  height,
                                                  isPrecisionImagery);
            }
            case TYPE_MOSAIC :
            {
                final File mosaicDbFile = dis.readBoolean() ? new File(FileSystemUtils.sanitizeWithSpacesAndSlashes(dis.readUTF())) : null;
                final String mosaicDbProvider = dis.readBoolean() ? dis.readUTF() : null;
                
                return new MosaicDatasetDescriptor(layerId,
                                                   name,
                                                   uri,
                                                   provider,
                                                   datasetType,
                                                   mosaicDbFile,
                                                   mosaicDbProvider,
                                                   imageryTypes,
                                                   resolutions,
                                                   coverages,
                                                   srid,
                                                   isRemote,
                                                   workingDir,
                                                   extraData);
            }
            default :
                throw new IllegalStateException();
        }
    }
    
    private static DatasetDescriptor decodeVersion9(InputStream s) throws IOException {
        DataInputStream dis = null;

        if (s instanceof DataInputStream)
            dis = (DataInputStream) s;
        else
            dis = new DataInputStream(s);

        final int descriptorType = dis.readInt();
        final long layerId = dis.readLong(); 
        final String name = dis.readUTF();
        final String uri = dis.readUTF();
        final String provider = dis.readUTF();
        final String datasetType = dis.readUTF();

        final int numImageryTypes = dis.readInt();

        Set<String> imageryTypes = new HashSet<String>();
        Map<String, Pair<Double, Double>> resolutions = new HashMap<String, Pair<Double, Double>>();
        Map<String, Geometry> coverages = new HashMap<String, Geometry>();
        String type;
        byte[] coverageWkb = new byte[] {};
        int coverageWkbSize;
        Geometry coverage;
        for(int i = 0; i < numImageryTypes; i++) {
            type = dis.readUTF();
            
            imageryTypes.add(type);
            resolutions.put(type, Pair.create(Double.valueOf(dis.readDouble()), Double.valueOf(dis.readDouble())));
            
            coverageWkbSize = dis.readInt();
            if(coverageWkb.length < coverageWkbSize)
                coverageWkb = new byte[coverageWkbSize];

            int numRead = dis.read(coverageWkb, 0, coverageWkbSize);
            if (numRead != coverageWkbSize) { 
                Log.d(TAG, "coverageWkb (numImageryTypes=" + i + "), read: " + numRead + " expected: " +coverageWkbSize);
            }
            coverage = GeometryFactory.parseWkb(ByteBuffer.wrap(coverageWkb, 0, coverageWkbSize));
            if(coverage == null)
                throw new RuntimeException();
            
            coverages.put(type, coverage);
        }
        
        // aggregate coverage
        coverageWkbSize = dis.readInt();
        if(coverageWkb.length < coverageWkbSize)
            coverageWkb = new byte[coverageWkbSize];

        int numRead = dis.read(coverageWkb, 0, coverageWkbSize);
        if (numRead != coverageWkbSize) { 
            Log.d(TAG, "coverageWkb (aggregate), read: " + numRead + " expected: " +coverageWkbSize);
        }
        coverage = GeometryFactory.parseWkb(ByteBuffer.wrap(coverageWkb, 0, coverageWkbSize));
        if(coverage == null)
            throw new RuntimeException();
        
        coverages.put(null, coverage);

        final int srid = dis.readInt();
        final boolean isRemote = dis.readBoolean();
        final File workingDir = dis.readBoolean() ? new File(FileSystemUtils.sanitizeWithSpacesAndSlashes(dis.readUTF())) : null;
        
        Map<String, String> extraData = new HashMap<String, String>();
        final int extraDataSize = dis.readInt();
        
        for(int i = 0; i < extraDataSize; i++)
            extraData.put(dis.readUTF(), dis.readUTF());
        
        switch(descriptorType) {
            case TYPE_IMAGE :
            {
                final int width = dis.readInt();
                final int height = dis.readInt();

                final boolean isPrecisionImagery = dis.readBoolean();

                final String imageryType = imageryTypes.iterator().next();

                return new ImageDatasetDescriptor(layerId,
                                                  name,
                                                  uri,
                                                  provider,
                                                  datasetType,
                                                  imageryType,
                                                  resolutions.get(imageryType).first.doubleValue(),
                                                  resolutions.get(imageryType).second.doubleValue(),
                                                  coverages.get(imageryType),
                                                  srid,
                                                  isRemote,
                                                  workingDir,
                                                  extraData,
                                                  width,
                                                  height,
                                                  isPrecisionImagery);
            }
            case TYPE_MOSAIC :
            {
                final File mosaicDbFile = dis.readBoolean() ? new File(FileSystemUtils.sanitizeWithSpacesAndSlashes(dis.readUTF())) : null;
                final String mosaicDbProvider = dis.readBoolean() ? dis.readUTF() : null;
                
                return new MosaicDatasetDescriptor(layerId,
                                                   name,
                                                   uri,
                                                   provider,
                                                   datasetType,
                                                   mosaicDbFile,
                                                   mosaicDbProvider,
                                                   imageryTypes,
                                                   resolutions,
                                                   coverages,
                                                   srid,
                                                   isRemote,
                                                   workingDir,
                                                   extraData);
            }
            default :
                throw new IllegalStateException();
        }
    }

    /**
     * Convenience method for obtaining an extra data value.
     * 
     * @param info      The dataset descriptor
     * @param key       The key
     * @param defval    The value to be returned in the event that no value is
     *                  associated with the specified key.
     *                  
     * @return  The extra data value associated with the key or
     *          <code>defval</code> if no value is associated with the key.
     */
    public static String getExtraData(DatasetDescriptor info, String key, String defval) {
        if(info == null || info.getExtraData() == null || !info.getExtraData().containsKey(key))
            return defval;
        String retval = info.getExtraData(key);
        if (retval == null)
            retval = defval;
        return retval;
    }

    /**
     * Computes the resolution, in meters-per-pixel, of an image given the
     * dimensions of the image and its four corner coordinates. The returned
     * value is a local resolution and results may not be accurate for images
     * with large spatial extents.
     * 
     * @param width     The width of the image, in pixels
     * @param height    The height of the image, in pixels
     * @param ul        The upper-left coordinate of the image
     * @param ur        The upper-right coordinate of the image
     * @param lr        The lower-right coordinate of the image
     * @param ll        The lower-left coordinate of the iamge
     * 
     * @return  The resolution of the image, in meters-per-pixel.
     */
    public static double computeGSD(long width, long height, GeoPoint ul, GeoPoint ur, GeoPoint lr,
            GeoPoint ll) {
        final double localResolution_UL_LR = GeoCalculations.distanceTo(ul, lr)
                / Math.sqrt(((double) width * (double) width) + ((double) height * (double) height));
        final double localResolution_UR_LL = GeoCalculations.distanceTo(ur, ll)
                / Math.sqrt(((double) width * (double) width) + ((double) height * (double) height));

        return Math.sqrt(localResolution_UL_LR * localResolution_UR_LL);
    }

    /**
     * Returns a type for an image formatted as:
     *
     * <P>&nbsp;&nbsp;&lt;<code>baseType</code>&gt;&lt;resolution&gt;
     *
     * Where:
     * 
     * <UL>
     *   <LI>&lt;<code>baseType</code>&gt; is the image type</LI>
     *   <LI>&lt;resolution&gt; is the resolution formatted as <code>0_c</code>
     *       for resolution in centimeters or <code>m</code> for resolution in
     *       meters.</LI>
     * </UL>
     * 
     * <P>Examples:
     * 
     * <UL>
     *   <LI>Nitf10 is NITF 10m data</LI>
     *   <LI>Nitf0_5 is NITF 50cm data</LI>
     * </UL>
     * 
     * 
     * @param baseType      The image type
     * @param resolution    The resolution of the image, in meters-per-pixel
     * 
     * @return  The formatted imagery type
     */
    public static String formatImageryType(String baseType, double resolution) {
        if(resolution < 1) {
            final int cm = (int)Math.round(resolution*10)*10;
            if(cm == 100) { // XXX - 
                return baseType + "1";
            } else {
                return baseType + "0_" + String.valueOf(cm);
            }
        }

        final int meters = (int)Math.ceil(resolution);
        if(meters > 1000){
            return baseType + String.valueOf((int)Math.ceil(meters/100d)*100);
        } else if(meters > 100) {
            return baseType + String.valueOf((int)Math.ceil(meters/25d)*25);
        } else if(meters > 10) {
            return baseType + String.valueOf((int)Math.ceil(meters/5d)*5);
        } else {
            return baseType + String.valueOf(meters);
        }
    }
    
    public static String formatResolution(double resolution) {
        if(resolution < 1) {
            final int cm = (int)Math.round(resolution*10)*10;
            if(cm == 100) { // XXX - 
                return "1m";
            } else {
                return String.valueOf(cm) + "cm";
            }
        }

        final int meters = (int)Math.round(resolution);
        if(meters > 1000){
            return String.valueOf((int)Math.round(meters/1000d)) + "km";
        } else if(meters > 100) {
            return String.valueOf((int)Math.round(meters/25d)*25) + "m";
        } else if(meters > 10) {
            return String.valueOf((int)Math.round(meters/5d)*5) + "m";
        } else {
            return String.valueOf(meters) + "m";
        }
    }

    /**
     * Creates a simple quad from four coordinates.
     * 
     * @param ul    The upper-left coordinate
     * @param ur    The upper-right coordinate
     * @param lr    The lower-right coordinate
     * @param ll    The lower-left coordinate
     * 
     * @return  A quad corresponding to the specified coordinates.
     */
    public static Geometry createSimpleCoverage(GeoPoint ul, GeoPoint ur, GeoPoint lr, GeoPoint ll) {
        Polygon retval = new Polygon(2);
        retval.addRing(new LineString(2));
        retval.getExteriorRing().addPoint(ul.getLongitude(), ul.getLatitude());
        retval.getExteriorRing().addPoint(ur.getLongitude(), ur.getLatitude());
        retval.getExteriorRing().addPoint(lr.getLongitude(), lr.getLatitude());
        retval.getExteriorRing().addPoint(ll.getLongitude(), ll.getLatitude());
        retval.getExteriorRing().addPoint(ul.getLongitude(), ul.getLatitude());
        return retval;
    }
} // DatasetDescriptor

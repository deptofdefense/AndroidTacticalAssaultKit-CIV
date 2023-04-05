
package com.atakmap.android.missionpackage.file;

import android.os.Parcel;
import android.os.Parcelable;
import android.os.SystemClock;

import com.atakmap.android.drawing.mapItems.DrawingRectangle;
import com.atakmap.android.editableShapes.EditablePolyline;
import com.atakmap.android.hashtags.util.HashtagUtils;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.missionpackage.ui.MissionPackageListMapItem;
import com.atakmap.android.routes.Route;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.locale.LocaleUtil;
import com.atakmap.coremap.log.Log;
import com.atakmap.util.zip.ZipEntry;
import com.atakmap.util.zip.ZipFile;

import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.Element;
import org.simpleframework.xml.Root;
import org.simpleframework.xml.Serializer;
import org.simpleframework.xml.core.Persister;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Maintains current state of Mission Package being built by user. The Mission Package may or may
 * not exist on the SD card, and may or may not match the current state of an instance of this
 * class. Mission Package Tool allows multiple user edits to package before "saving" the changes, at
 * which point the Mission Package .zip file on the SD is (re)created. Parcelable - can be passed
 * via intents XML Serializable - can be stored to file as XML/string
 * 
 * 
 */
@Root(name = "MissionPackageManifest")
public class MissionPackageManifest implements Parcelable {
    private static final String TAG = "MissionPackageManifest";

    // Estimated size of Map Item CoT in bytes: 3/4 KB
    public static final long MAP_ITEM_ESTIMATED_SIZE = 768;

    // Estimated size of a CoT point: 70 bytes
    public static final long POINT_ESTIMATED_SIZE = 70;

    // Estimated size of a CoT route point: 140 bytes
    public static final long ROUTE_POINT_ESTIMATED_SIZE = 140;

    @Element(name = "Configuration", required = true)
    MissionPackageConfiguration _configuration;

    @Element(name = "Contents", required = true)
    MissionPackageContents _contents;

    /**
     * Path to the Mission Package .zip file
     */
    String _path;
    String _lastSavedPath;

    @Attribute(name = "version", required = true)
    private int VERSION = 2;

    /**
     * ctor
     */
    public MissionPackageManifest() {
        this(null, null);
    }

    /**
     * ctor
     * 
     * @param name Name of Mission Package
     * @param dir Directory to store Mission Package
     */
    public MissionPackageManifest(String name, String dir) {
        this(name, UUID.randomUUID().toString(), dir);
    }

    public MissionPackageManifest(String name, String uid, String dir) {
        _configuration = new MissionPackageConfiguration();
        _contents = new MissionPackageContents();

        setUID(uid);
        if (!FileSystemUtils.isEmpty(name))
            setName(name);

        _path = getPath(dir, name);
        _lastSavedPath = null;
    }

    /**
     * copy ctor
     * 
     * @param copy
     */
    public MissionPackageManifest(MissionPackageManifest copy) {
        VERSION = copy.getVersion();
        _configuration = new MissionPackageConfiguration(
                copy.getConfiguration());
        _contents = new MissionPackageContents(copy.getContents());

        String temp = copy.getPath();
        if (!FileSystemUtils.isEmpty(temp))
            _path = temp;

        temp = copy.getLastSavedPath(false);
        if (!FileSystemUtils.isEmpty(temp))
            _lastSavedPath = temp;
    }

    public boolean isValid() {
        return _configuration != null && _configuration.isValid() &&
                _contents != null && _contents.isValid();
    }

    public MissionPackageContents getContents() {
        return _contents;
    }

    public MissionPackageConfiguration getConfiguration() {
        return _configuration;
    }

    /**
     * dir/<name>.zip
     * 
     * @param dir
     * @param name
     * @return
     */
    private static String getPath(String dir, String name) {
        return dir + File.separator + name + ".zip";
    }

    /**
     * Empty if no Map Items and no Files
     * 
     * @return
     */
    public boolean isEmpty() {
        return !hasFiles() && !hasMapItems();
    }

    /**
     * Clear Map Items and Files
     */
    public void clear() {
        _configuration.clear();
        _contents.clear();
    }

    /**
     * Get Unique ID (UUID)
     * 
     * @return
     */
    public String getUID() {
        NameValuePair p = _configuration
                .getParameter(MissionPackageConfiguration.PARAMETER_UID);
        return p == null ? null : p.getValue();
    }

    /**
     * Get user provided name for the package
     * 
     * @return
     */
    public String getName() {
        NameValuePair p = _configuration
                .getParameter(MissionPackageConfiguration.PARAMETER_NAME);
        return p == null ? null : p.getValue();
    }

    /**
     * Set user provided name for the package
     * 
     * @return
     */
    public void setName(String name) {
        _configuration.setParameter(new NameValuePair(
                MissionPackageConfiguration.PARAMETER_NAME,
                name));
    }

    private void setUID(String uid) {
        _configuration.setParameter(new NameValuePair(
                MissionPackageConfiguration.PARAMETER_UID,
                uid));
    }

    /**
     * Get user provided remarks for the package
     * 
     * @return
     */
    public String getRemarks() {
        NameValuePair p = _configuration
                .getParameter(MissionPackageConfiguration.PARAMETER_REMARKS);
        return p == null ? null : p.getValue();
    }

    /**
     * Set user provided remarks for the package
     * 
     * @return
     */
    public void setRemarks(String remarks) {
        _configuration.setParameter(new NameValuePair(
                MissionPackageConfiguration.PARAMETER_REMARKS, remarks));
    }

    /**
     * Set name and update path
     * 
     * @param name
     * @param dir
     */
    public void setName(String name, String dir) {
        _lastSavedPath = _path;
        setName(name);
        _path = getPath(dir, name);
        Log.d(TAG, "setName: " + this + ", old path: " + _lastSavedPath);
    }

    /**
     * Get path (including directory and filename)
     * 
     * @return
     */
    public String getPath() {
        return _path;
    }

    public boolean pathExists() {
        return _path != null && IOProviderFactory.exists(new File(_path));
    }

    /**
     * Set path (including directory and filename)
     * 
     * @return
     */
    public void setPath(String path) {
        _path = path;
    }

    /**
     * True if Mission Package has Map Items
     * 
     * @return
     */
    public boolean hasMapItems() {
        return _contents.hasContent(true);
    }

    /**
     * True if Mission Package has files
     * 
     * @return
     */
    public boolean hasFiles() {
        return _contents.hasContent(false);
    }

    /**
     * Get number of files in the Mission Package
     * 
     * @return
     */
    public int getFileCount() {
        return getFiles().size();
    }

    /**
     * Get list of file paths in the Mission Package
     * 
     * @return
     */
    public List<MissionPackageContent> getFiles() {
        return _contents.getContents(false);
    }

    /**
     * Same as above but the actual list of files is returned, not metadata
     * @return List of files
     */
    public List<File> getLocalFiles() {
        List<File> files = new ArrayList<>();
        for (MissionPackageContent c : _contents.getContents(false)) {
            NameValuePair p = c
                    .getParameter(MissionPackageContent.PARAMETER_LOCALPATH);
            if (p == null || FileSystemUtils.isEmpty(p.getValue()))
                continue;
            files.add(new File(p.getValue()));
        }
        return files;
    }

    /**
     * Get list of UIDs for Map Items in the Mission Package
     * 
     * @return
     */
    public List<MissionPackageContent> getMapItems() {
        return _contents.getContents(true);
    }

    public int getCount() {
        return _contents.getContents().size();
    }

    /**
     * Add specified file to the data package and optionally attach to a map
     * item
     *
     * @param file File to add
     * @param contentType Content type of the file (null if N/A)
     * @param attachedUID Map item UID (null if not an attachment)
     * @return True if added
     */
    public boolean addFile(File file, String contentType, String attachedUID) {
        MissionPackageContent mc = MissionPackageManifestAdapter
                .FileToContent(file, attachedUID);
        if (mc == null)
            return false;

        // Set import content type
        if (contentType != null)
            mc.setParameter(MissionPackageContent.PARAMETER_CONTENT_TYPE,
                    contentType);

        return addContent(mc);
    }

    /**
     * Add specified file to the data package and optionally attach to a map
     * item
     *
     * @param file File to add
     * @param attachedUID Map item UID (null if not an attachment)
     * @return True if added
     */
    public boolean addFile(File file, String attachedUID) {
        return addFile(file, null, attachedUID);
    }

    public boolean addContent(MissionPackageContent content) {
        if (content == null || !content.isValid()) {
            Log.w(TAG, "Failed to add content to Mission Package Content");
            return false;
        }

        return _contents.setContent(content);
    }

    /**
     * Add Map Items to the Mission Package
     * 
     * @param mapItemUIDArray
     * @return
     */
    public boolean addMapItems(String... mapItemUIDArray) {
        boolean success = true;
        for (String uid : mapItemUIDArray)
            success &= addMapItem(uid);
        return success;
    }

    /**
     * Add Map Item to the Mission Package
     * 
     * @param uid
     * @return
     */
    public boolean addMapItem(String uid) {
        return addContent(MissionPackageManifestAdapter.UIDToContent(uid));
    }

    public boolean removeContent(MissionPackageContent content) {
        if (content == null || !content.isValid())
            return false;
        return _contents.removeContent(content);
    }

    /**
     * Remove file from the Mission Package
     * 
     * @param content
     * @return
     */
    public boolean removeFile(MissionPackageContent content) {
        return removeContent(content);
    }

    /**
     * Check if the specified file is in the Mission Package
     * 
     * @param content
     * @return
     */
    public boolean hasFile(MissionPackageContent content) {
        if (content == null || !content.isValid()) {
            Log.w(TAG, "Failed to adapt file path to Mission Package Content");
            return false;
        }

        return _contents.hasContent(content);
    }

    /**
     * Remove Map Item from the Mission Package
     * 
     * @param item
     * @return
     */
    public boolean removeMapItem(MissionPackageListMapItem item) {
        return removeContent(item.getContent());
    }

    /**
     * Get number of Map Items in the Mission Package
     * 
     * @return
     */
    public int getMapItemCount() {
        return _contents.getContents(true).size();
    }

    /**
     * Get total uncompressed size of files in the Mission Package
     * @return Size in bytes
     */
    public long getFilesSize() {
        long totalSizeInBytes = 0;
        for (MissionPackageContent file : getFiles()) {
            NameValuePair p = file
                    .getParameter(MissionPackageContent.PARAMETER_LOCALPATH);
            if (p == null || !p.isValid()) {
                Log.w(TAG, "Skipping invalid file: " + file);
                continue;
            }

            File f = new File(p.getValue());
            if (IOProviderFactory.exists(f))
                totalSizeInBytes += IOProviderFactory.length(f);
            else
                totalSizeInBytes += file.getSize();
        }
        return totalSizeInBytes;
    }

    /**
     * Get uncompressed (or otherwise estimated) size of all Map Items
     * in the Mission Package
     * @return Size in bytes
     */
    public long getMapDataSize() {
        MapView mv = MapView.getMapView();
        long totalSizeInBytes = 0;
        for (MissionPackageContent item : getMapItems()) {
            long size = item.getSize();
            if (size <= 0) {
                size = MAP_ITEM_ESTIMATED_SIZE;
                NameValuePair p = item
                        .getParameter(MissionPackageContent.PARAMETER_UID);
                if (mv != null && p != null && p.isValid()) {
                    // Estimate size based on map item
                    MapItem mi = mv.getRootGroup().deepFindUID(p.getValue());
                    size = estimateMapItemSize(mi);
                }
                item.setSizes(size, item.getCompressedSize());
            }
            totalSizeInBytes += size;
        }
        return totalSizeInBytes;
    }

    /**
     * Get total estimated uncompressed size of all contents in the Mission Package
     * 
     * @return
     */
    public long getTotalSize() {
        long totalSizeInBytes = 0;
        if (hasFiles())
            totalSizeInBytes += getFilesSize();
        if (hasMapItems())
            totalSizeInBytes += getMapDataSize();
        return totalSizeInBytes;
    }

    /**
     * Estimate the size of a map item CoT message
     * @param item Map item
     * @return Size in bytes
     */
    public static long estimateMapItemSize(MapItem item) {
        long size = MAP_ITEM_ESTIMATED_SIZE;
        if (item == null)
            return size;
        if (item instanceof Route)
            size += ((Route) item).getNumPoints()
                    * ROUTE_POINT_ESTIMATED_SIZE;
        else if (item instanceof EditablePolyline)
            size += ((EditablePolyline) item).getNumPoints()
                    * POINT_ESTIMATED_SIZE;
        else if (item instanceof DrawingRectangle)
            size += 4 * POINT_ESTIMATED_SIZE;
        return size;
    }

    @Override
    public String toString() {
        return String.format(LocaleUtil.getCurrent(),
                "%s, %s, %d Files, %d Map Items",
                getName(), _path, getFileCount(), getMapItemCount());
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int flags) {
        if (isValid()) {
            parcel.writeString(_path);
            parcel.writeParcelable(_configuration, flags);
            parcel.writeParcelable(_contents, flags);
        }
    }

    private MissionPackageManifest(Parcel in) {
        readFromParcel(in);
    }

    private void readFromParcel(Parcel in) {
        setPath(in.readString());
        _configuration = in.readParcelable(MissionPackageConfiguration.class
                .getClassLoader());
        _contents = in.readParcelable(MissionPackageContents.class
                .getClassLoader());
    }

    public static final Parcelable.Creator<MissionPackageManifest> CREATOR = new Parcelable.Creator<MissionPackageManifest>() {
        @Override
        public MissionPackageManifest createFromParcel(Parcel in) {
            return new MissionPackageManifest(in);
        }

        @Override
        public MissionPackageManifest[] newArray(int size) {
            return new MissionPackageManifest[size];
        }
    };

    /**
     * Serialize the content listing out to the specified file
     * 
     * @param file
     * @return
     */
    boolean toXml(File file) {
        Log.d(TAG, "Saving Package Manifest: " + this + ", to file "
                + file.getAbsolutePath());
        Serializer serializer = new Persister();

        try (FileOutputStream fos = IOProviderFactory.getOutputStream(file)) {
            serializer.write(this, fos);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to save manifest: " + this, e);
            return false;
        }
    }

    /**
     * Serialize the content listing out to String
     * 
     * @param localManifest true for local manifest (extra data included), false for package
     *            manifest
     * @return
     */
    public String toXml(boolean localManifest) {
        Log.d(TAG, "Saving Package Manifest to string");
        Serializer serializer = new Persister();

        // see if we should strip out local (device specific) details
        MissionPackageManifest out = null;
        if (localManifest)
            out = this;
        else {
            out = new MissionPackageManifest(this);
            for (MissionPackageContent content : out.getContents()
                    .getContents()) {
                if (content
                        .hasParameter(
                                MissionPackageContent.PARAMETER_LOCALPATH)) {
                    content.removeParameter(
                            MissionPackageContent.PARAMETER_LOCALPATH);
                }

                if (content
                        .hasParameter(
                                MissionPackageContent.PARAMETER_LOCALISCOT)) {
                    content.removeParameter(
                            MissionPackageContent.PARAMETER_LOCALISCOT);
                }
            }
        }

        if (out == null || !out.isValid()) {
            Log.e(TAG, "Failed to save manifest: " + this);
            return "";
        }

        try {
            StringWriter sw = new StringWriter();
            serializer.write(out, sw);
            return sw.toString();
        } catch (Exception e) {
            Log.e(TAG, "Failed to save manifest: " + this, e);
            return "";
        }
    }

    /**
     * Parse the specified content listing file
     * 
     * @param file
     * @return
     */
    static MissionPackageManifest fromXml(File file) {
        Log.d(TAG,
                "Loading Package Manifest from file: "
                        + file.getAbsolutePath());

        Serializer serializer = new Persister();
        try (FileInputStream fis = IOProviderFactory.getInputStream(file)) {
            MissionPackageManifest contents = serializer.read(
                    MissionPackageManifest.class, fis);
            contents.setPath(file.getAbsolutePath());
            return contents;
        } catch (Exception e) {
            Log.e(TAG, "Failed to load manifest: " + file.getAbsolutePath(), e);
            return null;
        }
    }

    /**
     * Parse the specified Mission Package manifest XML to a manifest object
     * @param xml Manifest XML
     * @param filePath Local file path of the Mission Package ZIP
     * @param calcSizes True to calculate the compressed and uncompressed
     *                 size of all content inside the manifest
     * @return Mission Package manifest object
     */
    public static MissionPackageManifest fromXml(String xml, String filePath,
            boolean calcSizes) {
        if (FileSystemUtils.isEmpty(xml))
            return null;

        filePath = FileSystemUtils.sanitizeWithSpacesAndSlashes(filePath);

        Log.d(TAG, "Loading Package Manifest from xml");

        MissionPackageManifest manifest;
        Serializer serializer = new Persister();
        try {
            manifest = serializer.read(MissionPackageManifest.class, xml);
            manifest.setPath(filePath);
        } catch (Exception e) {
            Log.e(TAG, "Failed to load manifest: " + xml, e);
            manifest = null;
        }
        if (calcSizes && manifest != null && FileSystemUtils.isFile(filePath)) {
            // Read compressed and uncompressed file sizes of contents
            long start = SystemClock.elapsedRealtime();
            ZipFile zf = null;
            try {
                zf = new ZipFile(filePath);
                List<MissionPackageContent> contents = manifest.getContents()
                        .getContents();
                for (MissionPackageContent c : contents) {
                    ZipEntry ze = zf.getEntry(c.getManifestUid());
                    if (ze != null)
                        c.setSizes(ze.getSize(), ze.getCompressedSize());
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to read Mission Package ZIP: "
                        + filePath, e);
            } finally {
                try {
                    if (zf != null)
                        zf.close();
                } catch (Exception ignore) {
                }
            }
            Log.d(TAG,
                    "calcSizes took " + (SystemClock.elapsedRealtime() - start)
                            + "ms");
        }
        return manifest;
    }

    public static MissionPackageManifest fromXml(String xml, String filePath) {
        return fromXml(xml, filePath, false);
    }

    void setLastSavedPath(String path) {
        _lastSavedPath = path;
    }

    /**
     * Get last path the Mission Package was saved to
     * 
     * @return
     */
    public String getLastSavedPath() {
        return getLastSavedPath(true);
    }

    /**
     * Get last path the Mission Package was saved to
     * 
     * @param bDefaulToPath optionally return current path if Mission Package has not yet been saved
     * @return
     */
    private String getLastSavedPath(boolean bDefaulToPath) {
        if (!FileSystemUtils.isEmpty(_lastSavedPath))
            return _lastSavedPath;

        // has not yet been saved since it was loaded, just return path
        if (bDefaulToPath)
            return _path;
        else
            return null;
    }

    public Collection<String> getHashtags() {
        return HashtagUtils.extractTags(getRemarks());
    }

    /**
     * Get content listing version
     * 
     * @return
     */
    public int getVersion() {
        return VERSION;
    }

    @Override
    public int hashCode() {
        int result = (_configuration == null) ? 0 : _configuration.hashCode();
        result = 31 * result + ((_contents == null) ? 0 : _contents.hashCode());
        result = 31 * result + ((_path == null) ? 0 : _path.hashCode());
        result = 31 * result
                + ((_lastSavedPath == null) ? 0 : _lastSavedPath.hashCode());
        result = 31 * result + VERSION;
        return result;
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof MissionPackageManifest) {
            MissionPackageManifest c = (MissionPackageManifest) o;
            return this.equals(c);
        } else {
            return super.equals(o);
        }
    }

    public boolean equals(MissionPackageManifest rhsc) {
        if (VERSION != rhsc.getVersion())
            return false;
        if (!FileSystemUtils.isEquals(_path, rhsc.getPath()))
            return false;
        if (!FileSystemUtils.isEquals(_lastSavedPath,
                rhsc.getLastSavedPath(false)))
            return false;
        if (!_configuration.equals(rhsc._configuration))
            return false;
        if (!_contents.equals(rhsc._contents))
            return false;

        return true;
    }
}

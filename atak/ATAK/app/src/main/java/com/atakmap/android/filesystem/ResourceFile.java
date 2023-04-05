
package com.atakmap.android.filesystem;

import android.os.Parcel;
import android.os.Parcelable;

import com.atakmap.android.gpkg.GeoPackageImporter;
import com.atakmap.android.grg.GRGMapComponent;
import com.atakmap.android.util.ATAKUtilities;
import com.atakmap.android.wfs.WFSImporter;
import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.locale.LocaleUtil;
import com.atakmap.spatial.file.FalconViewSpatialDb;
import com.atakmap.spatial.file.GMLSpatialDb;
import com.atakmap.spatial.file.GpxFileSpatialDb;
import com.atakmap.spatial.file.KmlFileSpatialDb;
import com.atakmap.spatial.file.ShapefileSpatialDb;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;

/**
 * Parcelable asset file on the device
 * 
 * 
 */
public class ResourceFile implements Parcelable {

    public static final String UNKNOWN_MIME_TYPE = "application/octet-stream";

    // Supported MIME Types
    public enum MIMEType {

        //TODO consider combining this with ImageryFileType subclasses (extensions and MIMEs)

        // image files
        JPG("image/jpeg", "jpg", "asset://icons/camera.png"),
        JPEG("image/jpeg", "jpeg", "asset://icons/camera.png"),
        PNG("image/png", "png", "asset://icons/camera.png"),
        BMP("image/bmp", "bmp", "asset://icons/camera.png"),
        TIF(GRGMapComponent.IMPORTER_TIFF_MIME_TYPE,
                "tif",
                "asset://icons/camera.png"),
        TIFF(GRGMapComponent.IMPORTER_TIFF_MIME_TYPE,
                "tiff",
                "asset://icons/camera.png"),
        GIF("image/gif", "gif", "asset://icons/camera.png"),
        NTF("image/nitf", "ntf", "asset://icons/camera.png"),
        NITF("image/nitf", "nitf", "asset://icons/camera.png"),

        // video files
        MPG("video/mpeg", "mpg", "asset://icons/video.png"),
        AVI("video/avi", "avi", "asset://icons/video.png"),
        WMV("video/x-ms-wmv", "wmv", "asset://icons/video.png"),
        MOV("video/quicktime", "mov", "asset://icons/video.png"),
        MP4("video/mp4", "mp4", "asset://icons/video.png"),

        // audio files
        WAV("audio/wav", "wav", "asset://icons/audio.png"),
        MP3("audio/mpeg", "mp3", "asset://icons/audio.png"),
        OGG("audio/ogg", "ogg", "asset://icons/audio.png"),
        MID("audio/midi", "mid", "asset://icons/audio.png"),
        MIDI("audio/midi", "midi", "asset://icons/audio.png"),

        // document and other files
        DOC("application/msword", "doc", "asset://icons/doc.png"),
        DOCX("application/msword", "docx", "asset://icons/doc.png"),
        PPT("application/vnd.ms-powerpoint", "ppt", "asset://icons/ppt.png"),
        PPTX("application/vnd.ms-powerpoint", "pptx", "asset://icons/ppt.png"),
        TXT("text/plain", "txt", "asset://icons/generic_doc.png"),
        HTM("text/html", "htm", "asset://icons/generic_doc.png"),
        HTML("text/html", "html", "asset://icons/generic_doc.png"),
        XLS("application/vnd.ms-excel", "xls", "asset://icons/generic_doc.png"),

        DPKG("application/x-datapackage",
                "dpkg",
                "asset://icons/missionpackage.png"),
        FPKG("application/x-feedbackpackage",
                "fpkg",
                "asset://icons/feedback.png"),

        XLSX("application/vnd.ms-excel",
                "xlsx",
                "asset://icons/generic_doc.png"),
        PRJ("application/vnd.ms-project",
                "mpp",
                "asset://icons/generic_doc.png"),
        PEM("application/x-pem-file", "pem", "asset://icons/digitalcert.png"),
        P12("application/x-pkcs12", "p12", "asset://icons/digitalcert.png"),
        PFX("application/x-pkcs12", "pfx", "asset://icons/digitalcert.png"),
        ZIP("application/zip", "zip", "asset://icons/zip.png"),
        PDF("application/pdf", "pdf", "asset://icons/pdf.png"),
        XML(WFSImporter.MIME_XML, "xml", "asset://icons/generic_doc.png"),
        KML(KmlFileSpatialDb.KML_FILE_MIME_TYPE,
                "kml",
                "asset://icons/kml.png"),
        KMZ(KmlFileSpatialDb.KMZ_FILE_MIME_TYPE,
                "kmz",
                "asset://icons/kml.png"),
        APK("application/vnd.android.package-archive",
                "apk",
                "asset://icons/android_app.png"),
        GML(GMLSpatialDb.GML_FILE_MIME_TYPE,
                "gml",
                "asset://icons/esri.png"),
        GPX(GpxFileSpatialDb.GPX_FILE_MIME_TYPE,
                "gpx",
                "asset://icons/gpx.png"),
        SHP(ShapefileSpatialDb.SHP_FILE_MIME_TYPE,
                "shp",
                "asset://icons/esri.png"),
        SHPZ(ShapefileSpatialDb.SHP_FILE_MIME_TYPE,
                "shpz",
                "asset://icons/esri.png"),
        DRW(FalconViewSpatialDb.MIME_TYPE,
                "drw",
                "asset://icons/geojson.png"),
        LPT(FalconViewSpatialDb.MIME_TYPE,
                "lpt",
                "asset://icons/geojson.png"),
        CSV("text/csv", "csv", "asset://icons/generic_doc.png"),
        GPKG(GeoPackageImporter.MIME_TYPE,
                "gpkg",
                R.drawable.gpkg);

        public final String MIME;
        public final String EXT;
        public final String ICON_URI;

        MIMEType(String m, String e, String iconUri) {
            MIME = m;
            EXT = e;
            ICON_URI = iconUri;
        }

        MIMEType(String m, String e, int iconResId) {
            MIME = m;
            EXT = e;
            String uri = ATAKUtilities.getResourceUri(iconResId);
            if (FileSystemUtils.isEmpty(uri))
                uri = "asset://icons/generic_doc.png";
            ICON_URI = uri;
        }

        @Override
        public String toString() {
            return MIME;
        }
    }

    /**
     * Obtains the mimetype for a specific file.
     * @param fn the file
     * @return the mimetype, or null if no mimetype is supported.
     */
    public static MIMEType getMIMETypeForFile(String fn) {
        if (FileSystemUtils.isEmpty(fn))
            return null;

        int lastPeriod = fn.lastIndexOf(".");
        if (lastPeriod == -1)
            return null;

        fn = fn.substring(lastPeriod + 1);
        fn = fn.toLowerCase(LocaleUtil.getCurrent());
        for (MIMEType t : MIMEType.values()) {
            if (fn.equals(t.EXT))
                return t;
        }

        return null;
    }

    public static MIMEType getMIMETypeForMIME(String mime) {
        if (FileSystemUtils.isEmpty(mime))
            return null;

        mime = mime.toLowerCase(LocaleUtil.getCurrent());
        for (MIMEType t : MIMEType.values()) {
            if (mime.equals(t.MIME))
                return t;
        }

        return null;
    }

    private final String mFilePath;
    private final String mMimeType;

    /**
     * Optional content type, further describes how ATAK is using the File/MIME
     */
    private final String mContentType;

    /**
     * @param filePath the file path
     * @param mimeType the mime type
     * @param contentType the content type
     */
    public ResourceFile(String filePath, String mimeType, String contentType) {
        mFilePath = filePath;
        mMimeType = mimeType;
        mContentType = contentType;
    }

    /**
     * @param filePath the file path
     * @param contentType the content type
     */
    public ResourceFile(String filePath, String contentType) {
        mFilePath = filePath;
        mContentType = contentType;
        MIMEType mt = getMIMETypeForFile(filePath);
        mMimeType = mt != null ? mt.MIME : null;
    }

    /**
     * Is the resource file valid
     * @return return true if the file path for the resource type is not empty.
     */
    public boolean isValid() {
        return !FileSystemUtils.isEmpty(mFilePath);
    }

    /**
     * Returns the file path for the resource
     * @return the file path
     */
    public String getFilePath() {
        return mFilePath;
    }

    /**
     * Returns the mimetype for the resource
     * @return the mimetype
     */
    public String getMimeType() {
        return mMimeType;
    }

    /**
     * Returns the content type for the resource
     * @return the content type
     */
    public String getContentType() {
        return mContentType;
    }

    @Override
    public String toString() {
        return String.format("%s %s (%s)", mMimeType, mContentType, mFilePath);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mMimeType);
        dest.writeString(mContentType);
        dest.writeString(mFilePath);
    }

    public static final Parcelable.Creator<ResourceFile> CREATOR = new Parcelable.Creator<ResourceFile>() {
        @Override
        public ResourceFile createFromParcel(Parcel in) {
            return new ResourceFile(in);
        }

        @Override
        public ResourceFile[] newArray(int size) {
            return new ResourceFile[size];
        }
    };

    private ResourceFile(Parcel in) {
        mMimeType = in.readString();
        mContentType = in.readString();
        mFilePath = in.readString();
    }

    public static ResourceFile fromJSON(JSONObject json) throws JSONException {
        ResourceFile r = new ResourceFile(
                json.getString("FilePath"),
                json.has("MimeType") ? json.getString("MimeType") : null,
                json.has("ContentType") ? json.getString("ContentType") : null);

        if (!r.isValid())
            throw new JSONException("Invalid ResourceFile");

        return r;
    }

    public JSONObject toJSON() throws JSONException {
        if (!isValid())
            throw new JSONException("Invalid ResourceFile");

        JSONObject json = new JSONObject();
        json.put("FilePath", mFilePath);
        if (!FileSystemUtils.isEmpty(mContentType))
            json.put("ContentType", mContentType);
        if (!FileSystemUtils.isEmpty(mMimeType))
            json.put("MimeType", mMimeType);
        return json;
    }

    public long getSize() {
        if (FileSystemUtils.isEmpty(mFilePath))
            return 0;

        File file = new File(mFilePath);
        return IOProviderFactory.length(file);
    }
}

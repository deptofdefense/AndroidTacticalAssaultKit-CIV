
package com.atakmap.android.image;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.view.View;

import com.atakmap.android.data.URIContentHandler;
import com.atakmap.android.data.URIContentManager;
import com.atakmap.android.filesystem.ResourceFile;
import com.atakmap.android.filesystem.ResourceFile.MIMEType;
import com.atakmap.android.hierarchy.action.GoTo;
import com.atakmap.android.hierarchy.items.AbstractChildlessListItem;
import com.atakmap.android.hierarchy.items.MapItemUser;
import com.atakmap.android.image.nitf.NITFHelper;
import com.atakmap.android.maps.ILocation;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.util.ATAKUtilities;
import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.maps.coords.GeoBounds;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.MutableGeoBounds;
import com.atakmap.map.gdal.GdalLibrary;

import org.apache.sanselan.formats.tiff.TiffImageMetadata;
import org.apache.sanselan.formats.tiff.constants.TiffConstants;
import org.gdal.gdal.Dataset;

import java.io.File;

public class GalleryFileItem extends AbstractChildlessListItem implements
        GalleryItem, MapItemUser, ILocation, GoTo {

    protected final MapView mapView;
    private final File file;
    private final MapItem mapItem;
    protected GeoPoint location;
    protected TiffImageMetadata exif;
    protected long gpsTime;
    protected String imageCaption;
    protected URIContentHandler contentHandler;

    public GalleryFileItem(MapView mapView, File file, MapItem mapItem) {
        this.mapView = mapView;
        this.file = file;
        this.mapItem = mapItem;
        refreshImpl();
    }

    public GalleryFileItem(MapView mapView, File file) {
        this.mapView = mapView;
        this.file = file;
        String uid = findAttachmentUID();
        if (!FileSystemUtils.isEmpty(uid))
            this.mapItem = mapView.getRootGroup().deepFindUID(uid);
        else
            this.mapItem = null;
        refreshImpl();
    }

    public File getFile() {
        return this.file;
    }

    public long getTime() {
        return this.gpsTime;
    }

    public String getAbsolutePath() {
        return getFile().getAbsolutePath();
    }

    public boolean isImage() {
        File f = getFile();
        if (f == null)
            return false;
        return ImageDropDownReceiver.ImageFileFilter.accept(
                f.getParentFile(), f.getName());
    }

    public Drawable getIcon() {
        Drawable dr = null;

        // First look for the content handler icon
        if (this.contentHandler != null)
            dr = this.contentHandler.getIcon();

        // Then try mime type icon
        if (dr == null) {
            MIMEType mt = ResourceFile.getMIMETypeForFile(getName());
            if (mt != null) {
                Bitmap bmp = ATAKUtilities.getUriBitmap(mt.ICON_URI);
                if (bmp != null)
                    dr = new BitmapDrawable(this.mapView.getResources(), bmp);
            }
        }

        // Fallback to generic file icon
        if (dr == null)
            dr = this.mapView.getResources().getDrawable(
                    R.drawable.details);
        return dr;
    }

    public int getIconColor() {
        if (this.contentHandler != null)
            return this.contentHandler.getIconColor();
        return Color.WHITE;
    }

    @Override
    public TiffImageMetadata getExif() {
        return exif;
    }

    @Override
    public String getName() {
        return file.getName();
    }

    @Override
    public String getUID() {
        return this.mapItem != null ? this.mapItem.getUID()
                : findAttachmentUID();
    }

    protected String findAttachmentUID() {
        File f = getFile();
        if (f == null)
            return null;
        File parent = f.getParentFile();
        if (parent != null) {
            File attDir = parent.getParentFile();
            if (attDir != null && attDir.getName().equals("attachments"))
                return parent.getName();
        }
        return null;
    }

    @Override
    public String getURI() {
        return Uri.fromFile(file).toString();
    }

    @Override
    public String getTitle() {
        return getName();
    }

    @Override
    public String getDescription() {
        return imageCaption;
    }

    @Override
    public String getAuthor() {
        return null;
    }

    @Override
    public void refreshImpl() {
        this.contentHandler = URIContentManager.getInstance()
                .getHandler(this.file);

        TiffImageMetadata exif = null;
        long modTime = IOProviderFactory.lastModified(file);
        String caption = null;
        GeoPoint location = null;

        File dir = file.getParentFile();
        String name = file.getName();
        if (ImageDropDownReceiver.ImageFileFilter.accept(dir, name)
                && IOProviderFactory.exists(file)) {
            if (ImageContainer.NITF_FilenameFilter.accept(dir, name)) {
                Dataset nitf = GdalLibrary.openDatasetFromFile(file);
                if (nitf != null) {
                    caption = NITFHelper.getTitle(nitf);
                    location = NITFHelper.getCenterLocation(nitf);
                    nitf.delete();
                }
            } else if ((exif = ExifHelper.getExifMetadata(file)) != null) {
                long timeStamp = ExifHelper.getTimeStamp(exif, -1);
                if (timeStamp != -1)
                    modTime = timeStamp;
                caption = ExifHelper.getString(exif,
                        TiffConstants.TIFF_TAG_IMAGE_DESCRIPTION,
                        null);

                try {
                    location = ExifHelper.getLocation(exif);
                } catch (Exception ignored) {
                    // has EXIF data but the format of the location is not correct for sanselan
                }
            }
        }
        this.exif = exif;
        this.gpsTime = modTime;
        this.imageCaption = caption;
        this.location = location;
    }

    @Override
    public Object getUserObject() {
        return file;
    }

    @Override
    public View getExtraView() {
        return null;
    }

    @Override
    public MapItem getMapItem() {
        return mapItem;
    }

    @Override
    public GeoPoint getPoint(GeoPoint point) {
        if (this.contentHandler instanceof ILocation)
            return ((ILocation) this.contentHandler).getPoint(point);
        if (this.location == null) {
            if (point != null && point.isMutable()) {
                point.set(GeoPoint.ZERO_POINT);
                return point;
            } else {
                return GeoPoint.ZERO_POINT;
            }
        }
        if (point != null && point.isMutable()) {
            point.set(this.location);
            return point;
        } else
            return this.location;
    }

    @Override
    public GeoBounds getBounds(MutableGeoBounds bounds) {
        if (this.contentHandler instanceof ILocation)
            return ((ILocation) this.contentHandler).getBounds(bounds);

        if (this.location == null) {
            if (bounds != null)
                bounds.clear();
            return bounds;
        }
        double lat = this.location.getLatitude();
        double lng = this.location.getLongitude();
        if (bounds != null) {
            bounds.set(lat, lng, lat, lng);
            return bounds;
        } else {
            return new GeoBounds(lat, lng, lat, lng);
        }
    }

    @Override
    public String toString() {
        return file.getAbsolutePath();
    }

    @Override
    public boolean goTo(boolean select) {
        if (!FileSystemUtils.isFile(this.file))
            return false;

        // Use content handler
        if (this.contentHandler instanceof GoTo
                && this.contentHandler.isActionSupported(GoTo.class)) {
            ((GoTo) this.contentHandler).goTo(select);
            return true;
        }

        GeoBounds bounds = getBounds(null);
        if (bounds != null) {
            // Scale to fit based on getBounds() return
            ATAKUtilities.scaleToFit(this);
            return true;
        }
        return false;
    }
}

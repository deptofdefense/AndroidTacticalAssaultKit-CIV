
package com.atakmap.android.hashtags.attachments;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.net.Uri;

import com.atakmap.android.data.URIContentHandler;
import com.atakmap.android.data.URIContentManager;
import com.atakmap.android.data.URIHelper;
import com.atakmap.android.hashtags.HashtagContent;
import com.atakmap.android.hashtags.HashtagManager;
import com.atakmap.android.hashtags.util.HashtagSet;
import com.atakmap.android.hashtags.util.HashtagUtils;
import com.atakmap.android.hierarchy.action.Delete;
import com.atakmap.android.hierarchy.action.GoTo;
import com.atakmap.android.hierarchy.items.MapItemUser;
import com.atakmap.android.image.ExifHelper;
import com.atakmap.android.image.ImageContainer;
import com.atakmap.android.image.ImageDropDownReceiver;
import com.atakmap.android.image.nitf.NITFHelper;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.ILocation;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.maps.coords.GeoBounds;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.MutableGeoBounds;
import com.atakmap.map.gdal.GdalLibrary;

import org.apache.sanselan.formats.tiff.TiffImageMetadata;
import org.apache.sanselan.formats.tiff.constants.TiffConstants;
import org.apache.sanselan.formats.tiff.write.TiffOutputSet;
import org.gdal.gdal.Dataset;
import org.gdal.gdalconst.gdalconst;

import java.io.File;
import java.util.Collection;

public class AttachmentContent implements HashtagContent, GoTo, Delete,
        MapItemUser, ILocation {

    private final MapView _mapView;
    private final File _file;
    private final HashtagSet _hashtags = new HashtagSet();

    private long _lastMod;
    private String _caption;
    private GeoPoint _point;
    private MapItem _mapItem;

    public AttachmentContent(MapView mapView, File file) {
        _mapView = mapView;
        _file = file;
    }

    private void refresh() {
        long lastMod = IOProviderFactory.lastModified(_file);
        if (lastMod == _lastMod)
            return;

        File dir = _file.getParentFile();
        String name = _file.getName();
        String caption = null;
        GeoPoint point = null;

        if (ImageContainer.JPEG_FilenameFilter.accept(dir, name)) {
            TiffImageMetadata exif = ExifHelper.getExifMetadata(_file);
            if (exif != null) {
                try {
                    point = ExifHelper.getLocation(exif);
                } catch (Exception ignored) {
                    // has EXIF data bug the values are not what sanselan is expecting
                }
                caption = ExifHelper.getString(exif,
                        TiffConstants.TIFF_TAG_IMAGE_DESCRIPTION, "");
            }
        } else {
            Dataset ds = GdalLibrary.openDatasetFromFile(_file,
                    gdalconst.GA_ReadOnly);
            if (ds != null) {
                if (ImageContainer.NITF_FilenameFilter.accept(dir, name)) {
                    caption = NITFHelper.getTitle(ds);
                    point = NITFHelper.getCenterLocation(ds);
                } else if (name.endsWith(".png"))
                    caption = ds.GetMetadataItem("Description");
                ds.delete();
            }
        }

        if (point == null) {
            String uid = dir.getName();
            _mapItem = _mapView.getRootGroup().deepFindUID(uid);
        }

        _lastMod = lastMod;
        _caption = caption;
        _point = point;
    }

    public void readHashtags() {
        String caption = getCaption();
        if (caption != null) {
            _hashtags.clear();
            _hashtags.addAll(HashtagUtils.extractTags(caption));
        }
    }

    private String getCaption() {
        refresh();
        return _caption;
    }

    private void setCaption(String caption) {
        File dir = _file.getParentFile();
        String name = _file.getName();
        if (ImageContainer.JPEG_FilenameFilter.accept(dir, name)) {
            // Update EXIF image description
            TiffImageMetadata exif = ExifHelper.getExifMetadata(_file);
            TiffOutputSet tos = ExifHelper.getExifOutput(exif);
            if (ExifHelper.updateField(tos,
                    TiffConstants.TIFF_TAG_IMAGE_DESCRIPTION, caption))
                ExifHelper.saveExifOutput(tos, _file);
        } else if (ImageContainer.NITF_FilenameFilter.accept(dir, name)) {
            // Update NITF file title
            Dataset nitf = GdalLibrary.openDatasetFromFile(_file);
            if (nitf != null) {
                NITFHelper.setTitle(nitf, caption);
                nitf.delete();
            }
        } else if (name.endsWith(".png")) {
            ExifHelper.setPNGDescription(_file, caption);
        }
    }

    @Override
    public String getURI() {
        return URIHelper.getURI(_file);
    }

    @Override
    public String getTitle() {
        return _file.getName();
    }

    @Override
    public Drawable getIconDrawable() {
        return _mapView.getContext().getDrawable(R.drawable.camera);
    }

    @Override
    public int getIconColor() {
        return Color.WHITE;
    }

    @Override
    public void setHashtags(Collection<String> tags) {
        // Remove old tags
        String caption = getCaption();
        if (caption == null)
            caption = "";
        for (String tag : _hashtags) {
            if (!tags.contains(tag))
                caption = caption.replace(tag, "");
        }

        // Add new tags to remarks
        StringBuilder sb = new StringBuilder(caption);
        for (String tag : tags) {
            if (!_hashtags.contains(tag))
                sb.append(" ").append(tag);
        }

        setCaption(sb.toString());
        HashtagManager.getInstance().updateContent(this, tags);
    }

    @Override
    public Collection<String> getHashtags() {
        return _hashtags;
    }

    @Override
    public boolean goTo(boolean select) {

        URIContentHandler h = URIContentManager.getInstance().getHandler(_file);

        if (h != null && h.isActionSupported(GoTo.class))
            return ((GoTo) h).goTo(select);

        if (ImageDropDownReceiver.ImageFileFilter.accept(null,
                _file.getName())) {

            String uid = _file.getParentFile().getName();
            MapItem item = _mapView.getRootGroup().deepFindUID(uid);

            Intent intent = new Intent(ImageDropDownReceiver.IMAGE_DISPLAY);
            if (item != null)
                intent.putExtra("uid", uid);
            else
                intent.putExtra("imageURI", Uri.fromFile(_file).toString());
            AtakBroadcast.getInstance().sendBroadcast(intent);
        }

        return false;
    }

    @Override
    public boolean delete() {
        FileSystemUtils.deleteFile(_file);
        return true;
    }

    @Override
    public MapItem getMapItem() {
        refresh();
        return _mapItem;
    }

    @Override
    public GeoPoint getPoint(GeoPoint point) {
        refresh();
        if (point != null)
            point.set(_point != null ? _point : GeoPoint.ZERO_POINT);
        return _point;
    }

    @Override
    public GeoBounds getBounds(MutableGeoBounds bounds) {
        return null;
    }
}

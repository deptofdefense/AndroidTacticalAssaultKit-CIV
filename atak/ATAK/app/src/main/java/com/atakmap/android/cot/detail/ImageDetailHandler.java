
package com.atakmap.android.cot.detail;

import android.content.Intent;
import android.util.Base64;
import android.webkit.MimeTypeMap;

import com.atakmap.android.image.ImageDropDownReceiver;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.comms.CommsMapComponent.ImportResult;
import com.atakmap.coremap.cot.event.CotDetail;
import com.atakmap.coremap.cot.event.CotEvent;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;
import com.atakmap.util.zip.IoUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;

/**
 * Used for handling images within CoT events
 * Probably should never be exported since we have Mission Packages
 * Not to mention the 64K protobuf limit
 */
class ImageDetailHandler extends CotDetailHandler {

    private static final String TAG = "ImageDetailHandler";

    private final MapView _mapView;

    ImageDetailHandler(MapView mapView) {
        super("image");
        _mapView = mapView;
    }

    @Override
    public boolean toCotDetail(MapItem item, CotEvent event, CotDetail detail) {
        // Never exported
        return false;
    }

    @Override
    public ImportResult toItemMetadata(MapItem item, CotEvent event,
            CotDetail detail) {
        String mime = detail.getAttribute("mime");
        if (mime == null)
            mime = "image/jpeg";

        String innerText = detail.getInnerText();
        if (FileSystemUtils.isEmpty(innerText))
            return ImportResult.FAILURE;

        File imageFile = _dumpBase64Image(detail.getInnerText(), mime,
                event.getUID());
        if (imageFile == null)
            return ImportResult.FAILURE;

        item.setMetaString("imageCacheDir", imageFile.getParent());

        // add a link file if <link> tag exists.
        CotDetail linkDetail = event.findDetail("link");
        String linkUID;
        if (linkDetail != null && (linkUID = linkDetail
                .getAttribute("uid")) != null) {
            File linkFile = _dumpImageLink(imageFile.getPath(),
                    linkUID);
            if (linkFile != null) {
                MapItem link = _mapView.getRootGroup().deepFindUID(linkUID);
                if (link != null)
                    link.setMetaString("imageCacheDir", linkFile.getParent());
            }
        }
        // file success, notify viewer if running
        AtakBroadcast.getInstance().sendBroadcast(
                new Intent(ImageDropDownReceiver.IMAGE_UPDATE)
                        .putExtra("uid", event.getUID()));
        // send notification here instead of CotMapComponent //TODO
        return ImportResult.SUCCESS;
    }

    private File _dumpImageLink(String linkPath, String uid) {
        FileWriter w = null;
        File linkFile = null;
        try {
            linkFile = ImageDropDownReceiver.createAndGetPathToImageFromUID(
                    uid, "lnk");
            if (linkFile != null) {
                w = IOProviderFactory.getFileWriter(linkFile);
                w.write(linkPath);
                w.close();
            }
        } catch (IOException e) {
            Log.e(TAG, "error: ", e);
        } finally {
            IoUtils.close(w, TAG, "error: ");
        }
        return linkFile;
    }

    private File _dumpBase64Image(String b64, String mime, String uid) {
        File imageFile = null;
        byte[] bytes = Base64.decode(b64, Base64.DEFAULT);
        try {
            imageFile = ImageDropDownReceiver.createAndGetPathToImageFromUID(
                    uid,
                    _extFromMime(mime));

            try (FileOutputStream fos = IOProviderFactory
                    .getOutputStream(imageFile)) {
                fos.write(bytes);
            }
        } catch (IOException e) {
            Log.e(TAG, "error: ", e);
        }

        return imageFile;
    }

    private static String _extFromMime(String mime) {
        String ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime);
        if (ext != null && !ext.isEmpty())
            return ext;
        else
            return "dat";
    }
}


package com.atakmap.android.video.cot;

import com.atakmap.android.cot.detail.CotDetailHandler;
import com.atakmap.android.maps.MapEvent;
import com.atakmap.android.maps.MapEventDispatcher.MapEventDispatchListener;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.video.ConnectionEntry;
import com.atakmap.android.video.manager.VideoManager;
import com.atakmap.comms.CommsMapComponent.ImportResult;
import com.atakmap.coremap.cot.event.CotDetail;
import com.atakmap.coremap.cot.event.CotEvent;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;
import com.atakmap.android.video.VideoMapComponent;

import java.io.File;

/**
 * Video aliases which can optionally be attached to a marker
 * <__video 
 *      url = the url for the video
 *      uid = the uid of the video link if it already is known to exist
 *      spi = the uid of the spi (useful in the case of ghosting)
 *      sensor = the uid of the sensor (useful in the case of ghosting)
 *      extplayer = a packagename to be used to launch an external player
 *      buffer = the buffer to be used (default is -1)
 *      timeout = the timeout to be used (default is 5000 ms)
 *
 * WARNING
 * -------
 * marker values video_spi_uid and video_sensor_uid are now being used by 
 * the uas tool.  do not change these.
 */
public class VideoDetailHandler extends CotDetailHandler
        implements MapEventDispatchListener {

    public static final String TAG = "VideoDetailHandler";

    private final MapView _mapView;

    public VideoDetailHandler(MapView mapView) {
        super("__video");
        _mapView = mapView;
        _mapView.getMapEventDispatcher().addMapEventListener(
                MapEvent.ITEM_REMOVED, this);
    }

    public void dispose() {
        _mapView.getMapEventDispatcher().removeMapEventListener(
                MapEvent.ITEM_REMOVED, this);
    }

    @Override
    public boolean toCotDetail(MapItem item, CotEvent event, CotDetail detail) {
        if (item.hasMetaValue("videoUID") || item.hasMetaValue("videoUrl")) {
            CotDetail video = new CotDetail("__video");

            if (item.hasMetaValue("videoUID"))
                video.setAttribute("uid",
                        item.getMetaString("videoUID", ""));
            if (item.hasMetaValue("videoUrl"))
                video.setAttribute("url",
                        item.getMetaString("videoUrl", ""));
            detail.addChild(video);

            String videoUID = item.getMetaString("videoUID", null);
            if (videoUID != null) {
                CotDetail cd = VideoMapComponent.getVideoAliasDetail(videoUID);
                if (cd != null)
                    video.addChild(cd);
            }
            return true;
        }
        return false;
    }

    @Override
    public ImportResult toItemMetadata(MapItem item, CotEvent event,
            CotDetail detail) {
        CotDetail ce = detail.getFirstChildByName(0, "ConnectionEntry");
        if (ce != null) {
            String uid = ce.getAttribute("uid");
            if (FileSystemUtils.isEmpty(uid))
                uid = event.getUID();
            if (!FileSystemUtils.isEmpty(uid)) {
                // Video requires UID
                ConnectionEntry entry = new ConnectionEntry(
                        ce.getAttribute("alias"),
                        ce.getAttribute("address"), null,
                        parseInt(ce.getAttribute("port"), -1),
                        parseInt(ce.getAttribute("roverPort"), -1),
                        ce.getAttribute("path"),
                        ConnectionEntry.Protocol
                                .fromString(ce.getAttribute("protocol")),
                        parseInt(ce.getAttribute("networkTimeout"), 5000),
                        parseInt(ce.getAttribute("bufferTime"), -1),
                        parseInt(ce.getAttribute("rtspReliable"), 0),
                        "",
                        ConnectionEntry.Source.EXTERNAL);
                entry.setIgnoreEmbeddedKLV(Boolean.parseBoolean(
                        ce.getAttribute("ignoreEmbeddedKLV")));
                entry.setUID(uid);
                ConnectionEntry existing = VideoManager.getInstance()
                        .getEntry(uid);

                // Empty URL = force removal
                if (existing != null && existing.isTemporary()
                        && FileSystemUtils.isEmpty(entry.getAddress())) {
                    VideoManager.getInstance().removeEntry(existing);
                    entry = null;
                } else if (existing == null && item != null
                        || existing != null && existing.isTemporary()) {
                    entry.setTemporary(true);
                    entry.setLocalFile(new File(VideoManager.ENTRIES_DIR,
                            uid + ".xml"));
                } else if (existing != null)
                    entry.setLocalFile(existing.getLocalFile());

                if (entry != null) {
                    VideoManager.getInstance().addEntry(entry);
                }
            }
        }

        if (item != null) {
            pullString(detail, "url", item, "videoUrl");
            pullString(detail, "uid", item, "videoUID");
            pullString(detail, "spi", item, "video_spi_uid");
            pullString(detail, "sensor", item, "video_sensor_uid");
            pullString(detail, "extplayer", item, "video_extplayer");
            pullInt(detail, "buffer", item, "buffer", -1);
            pullInt(detail, "timeout", item, "timeout", 5000);

            if (ce == null
                    && FileSystemUtils.isEmpty(detail.getAttribute("url"))) {
                String uid = detail.getAttribute("uid");
                if (FileSystemUtils.isEmpty(uid))
                    uid = event.getUID();

                final ConnectionEntry existing = VideoManager.getInstance()
                        .getEntry(uid);
                if (existing != null)
                    VideoManager.getInstance().removeEntry(existing);
            }
        }
        return ImportResult.SUCCESS;
    }

    /**
     * Allow for the pulling of a string from a the CotDetail and setting the value as part of the 
     * associated marker.
     */
    private void pullString(CotDetail detail, String attributeName,
            MapItem item, String metaDataName) {
        final String val = detail.getAttribute(attributeName);
        if (val != null) {
            item.setMetaString(metaDataName, val);
        } else {
            item.removeMetaData(metaDataName);
        }
    }

    /**
     * Allow for the pulling of a int from a the CotDetail and setting the value as part of the 
     * associated marker.
     */
    private void pullInt(CotDetail detail, String attributeName,
            MapItem item, String metaDataName, int def) {
        final String val = detail.getAttribute(attributeName);
        if (val != null) {
            try {
                int vfy = Integer.parseInt(val);
                item.setMetaString(metaDataName, Integer.toString(vfy));
            } catch (Exception e) {
                item.setMetaString(metaDataName, Integer.toString(def));
                Log.e(TAG,
                        "could not parse " + attributeName + " value: " + val +
                                " defaulting to " + def);
            }
        } else {
            item.removeMetaData(metaDataName);
        }
    }

    @Override
    public void onMapEvent(MapEvent event) {
        // Item removed - remove matching alias if it's temporary
        MapItem item = event.getItem();
        if (item == null)
            return;

        String videoUID = item.getMetaString("videoUID", null);
        if (FileSystemUtils.isEmpty(videoUID))
            return;

        ConnectionEntry ce = VideoManager.getInstance().getEntry(videoUID);
        if (ce != null && ce.isTemporary())
            VideoManager.getInstance().removeEntry(ce);
    }
}

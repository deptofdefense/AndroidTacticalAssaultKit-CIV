
package com.atakmap.android.video;

import android.content.Context;
import android.content.Intent;

import com.atakmap.android.cot.detail.CotDetailManager;
import com.atakmap.android.cot.importer.CotImporterManager;
import com.atakmap.android.importexport.ImportExportMapComponent;
import com.atakmap.android.ipc.AtakBroadcast.DocumentedIntentFilter;

import com.atakmap.android.dropdown.DropDownMapComponent;
import com.atakmap.android.ipc.DocumentedExtra;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.video.cot.VideoAliasImporter;
import com.atakmap.android.video.cot.VideoDetailHandler;
import com.atakmap.android.video.manager.VideoManager;
import com.atakmap.android.video.manager.VideoXMLHandler;
import com.atakmap.android.video.overlay.GLVideoOverlayLayer;
import com.atakmap.android.video.overlay.VideoBrowserMapOverlay;
import com.atakmap.coremap.cot.event.CotDetail;
import com.atakmap.coremap.loader.NativeLoader;
import com.atakmap.coremap.log.Log;
import com.atakmap.map.layer.opengl.GLLayerFactory;
import com.partech.pgscmedia.MediaException;
import com.partech.pgscmedia.MediaProcessor;

/**
 * Core video capability within tak.
 */
public class VideoMapComponent extends DropDownMapComponent {

    private VideoManager manager;
    private VideoDropDownReceiver vdr;
    private VideoBrowserDropDownReceiver vbdr;
    protected VideoBrowserMapOverlay overlay;
    private ImportVideoAliasSort aliasSorter;
    private VideoAliasImporter cotImporter;
    private VideoDetailHandler cotHandler;

    static {
        NativeLoader.loadLibrary("gnustl_shared");
        NativeLoader.loadLibrary("avutilpgsc");
        NativeLoader.loadLibrary("avcodecpgsc");
        NativeLoader.loadLibrary("swscalepgsc");
        NativeLoader.loadLibrary("avformatpgsc");
        NativeLoader.loadLibrary("avdevicepgsc");
        NativeLoader.loadLibrary("avvideopgsc");
        NativeLoader.loadLibrary("pgscmedia");
        NativeLoader.loadLibrary("pgscmediajni");
        NativeLoader.loadLibrary("pgscmobilevid");
    }

    static boolean initDone = false;

    @Override
    public void onCreate(Context context, Intent intent, MapView view) {
        super.onCreate(context, intent, view);

        if (!initDone) {
            try {
                /* Initialize Gv2F **/
                MediaProcessor.PGSCMediaInit(context.getApplicationContext());

            } catch (MediaException e) {
                Log.e(TAG,
                        "Error when initializing native components for video player {"
                                + context.getApplicationContext()
                                        .getPackageName()
                                + "}",
                        e);
                throw new RuntimeException(e);
            }
            initDone = true;
        }

        manager = new VideoManager(view);
        manager.init();

        GLLayerFactory.register(GLVideoOverlayLayer.SPI2);

        vbdr = new VideoBrowserDropDownReceiver(view, context);
        vdr = new VideoDropDownReceiver(view, context);

        // relate vbdr to vdr // temporary
        vbdr.setVideoDropDownReceiver(vdr);

        final DocumentedIntentFilter vbIntent = new DocumentedIntentFilter(
                VideoBrowserDropDownReceiver.VIDEO_TOOL,
                "Launches the video component responsible for displaying the videos and aliases available on the system");
        registerDropDownReceiver(vbdr, vbIntent);

        final DocumentedIntentFilter vIntent = new DocumentedIntentFilter(
                VideoDropDownReceiver.DISPLAY,
                "Launches the video component responsible for displaying the video.   At this time it is likely this would contain a ConnectionEntry.",
                new DocumentedExtra[] {
                        new DocumentedExtra("CONNECTION_ENTRY",
                                "The video connection entry", false,
                                ConnectionEntry.class),
                        new DocumentedExtra("layers",
                                "The id's for previously described video view layers to display when the video is playing",
                                true, String[].class)
                });
        registerDropDownReceiver(vdr, vIntent);

        overlay = new VideoBrowserMapOverlay(view);
        view.getMapOverlayManager().addOverlay(overlay);

        ImportExportMapComponent.getInstance().addImporterClass(
                this.aliasSorter = new ImportVideoAliasSort(context));

        CotDetailManager.getInstance().registerHandler(
                cotHandler = new VideoDetailHandler(view));
        CotImporterManager.getInstance().registerImporter(
                cotImporter = new VideoAliasImporter(view, cotHandler));

    }

    @Override
    protected void onDestroyImpl(Context context, MapView view) {
        super.onDestroyImpl(context, view);

        ImportExportMapComponent.getInstance().removeImporterClass(
                this.aliasSorter);

        CotImporterManager.getInstance().unregisterImporter(cotImporter);
        CotDetailManager.getInstance().unregisterHandler(cotHandler);

        cotHandler.dispose();

        if (vdr != null)
            vdr.dispose();
        if (vbdr != null)
            vbdr.dispose();
        view.getMapOverlayManager().removeOverlay(overlay);
        overlay.dispose();
        manager.dispose();
    }

    /**
     * Given a video alias uid, return the CotDetail that represents it.
     * @param videoUID the uid for the video
     * @return null if the video uid does not exist, otherwise a CotDetail that represents the
     * video.
     */
    public static CotDetail getVideoAliasDetail(String videoUID) {
        final ConnectionEntry ce = VideoManager.getInstance()
                .getEntry(videoUID);
        if (ce == null)
            return null;

        return VideoXMLHandler.toCotDetail(ce);
    }

    /**
     * Given a set of parameters, this will construct a video connection entry and add it to the
     * video managemement system.
     * @param address the address of the video
     * @param uid the uid for the video
     * @param alias the alias or common name for the stream
     * @param port the port to be used to open the video
     * @param rtspReliable if it should be using rtsp reliable 0 == no / 1 == yes
     * @param passphrase is the pasphrase required for the connection
     * @param path the path
     * @param protocol the protocol
     * @param networkTimeout the network timeout
     * @param bufferTime the buffering timeout
     */
    public static void addVideoAlias(final String address,
            final String uid,
            final String alias,
            final int port,
            final int rtspReliable,
            final String passphrase,
            final String path,
            final String protocol,
            final int networkTimeout,
            final int bufferTime) {
        try {
            final ConnectionEntry ce = new ConnectionEntry(alias, address, null,
                    port, -1,
                    path, ConnectionEntry.Protocol.fromString(protocol),
                    networkTimeout,
                    bufferTime, rtspReliable, passphrase,
                    ConnectionEntry.Source.LOCAL_STORAGE);
            ce.setUID(uid);

            VideoManager.getInstance().addEntry(ce);
        } catch (Exception e) {
            Log.e(TAG, "error adding a new or updating an existing alias", e);
        }
    }

    /**
     * Given a set of parameters, this will construct a video connection entry and add it to the
     * video managemement system.
     * @param address the address of the video
     * @param uid the uid for the video
     * @param alias the alias or common name for the stream
     * @param port the port to be used to open the video
     * @param rtspReliable if it should be using rtsp reliable 0 == no / 1 == yes
     * @param path the path
     * @param protocol the protocol
     * @param networkTimeout the network timeout
     * @param bufferTime the buffering timeout
     */
    public static void addVideoAlias(final String address,
            final String uid,
            final String alias,
            final int port,
            final int rtspReliable,
            final String path,
            final String protocol,
            final int networkTimeout,
            final int bufferTime) {
        try {
            final ConnectionEntry ce = new ConnectionEntry(alias, address, null,
                    port, -1,
                    path, ConnectionEntry.Protocol.fromString(protocol),
                    networkTimeout,
                    bufferTime, rtspReliable, "",
                    ConnectionEntry.Source.LOCAL_STORAGE);
            ce.setUID(uid);

            VideoManager.getInstance().addEntry(ce);
        } catch (Exception e) {
            Log.e(TAG, "error adding a new or updating an existing alias", e);
        }
    }

}

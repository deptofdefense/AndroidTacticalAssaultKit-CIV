
package com.atakmap.android.layers.wms;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

import com.atakmap.android.layers.LayersManagerBroadcastReceiver;
import com.atakmap.android.layers.OnlineLayersDownloadManager;
import com.atakmap.android.util.NotificationUtil;
import com.atakmap.app.R;

public class DownloadAndCacheBroadcastReceiver extends BroadcastReceiver {

    private final OnlineLayersDownloadManager downloader;
    private final LayersManagerBroadcastReceiver lmbr;

    public DownloadAndCacheBroadcastReceiver(
            final OnlineLayersDownloadManager downloader,
            final LayersManagerBroadcastReceiver lmbr) {
        this.downloader = downloader;
        this.lmbr = lmbr;
    }

    @Override
    public void onReceive(final Context context, final Intent intent) {
        if (intent.getExtras() != null) {
            if (intent.getExtras().containsKey(
                    DownloadAndCacheService.DOWNLOAD_STATUS)) {

                int queuedDownloads = intent.getExtras().getInt(
                        DownloadAndCacheService.QUEUE_SIZE);

                if (queuedDownloads <= 1) {
                    downloader.setTileProgressText(intent.getExtras()
                            .getString(
                                    DownloadAndCacheService.TILE_STATUS)
                            + " tiles");
                    downloader.setLayerProgressText(intent.getExtras()
                            .getString(
                                    DownloadAndCacheService.LAYER_STATUS)
                            + " layers");
                    downloader.setLabelVisibility(false, 0);
                } else {
                    downloader.setLabelVisibility(true, queuedDownloads);
                    downloader.setTileProgressText(intent.getExtras()
                            .getString(
                                    DownloadAndCacheService.TILE_STATUS));
                    downloader.setLayerProgressText(intent.getExtras()
                            .getString(
                                    DownloadAndCacheService.LAYER_STATUS));
                }
                downloader
                        .setProgress(
                                intent.getExtras()
                                        .getInt(DownloadAndCacheService.PROGRESS_BAR_PROGRESS),
                                intent.getExtras()
                                        .getInt(
                                                DownloadAndCacheService.PROGRESS_BAR_ADJUST_SECONDARY),
                                intent
                                        .getExtras()
                                        .getInt(DownloadAndCacheService.PROGRESS_BAR_SET_MAX));
                downloader.setTimeProgressText(intent.getExtras().getLong(
                        DownloadAndCacheService.TIME_STATUS));
            } else if (intent.getExtras().containsKey(
                    DownloadAndCacheService.JOB_STATUS)) {
                int status = intent.getExtras().getInt(
                        DownloadAndCacheService.JOB_STATUS);
                String mapTitle = intent.getExtras().getString(
                        DownloadAndCacheService.TITLE);
                int downloadsLeft = intent.getExtras().containsKey(
                        DownloadAndCacheService.QUEUE_SIZE)
                                ? intent
                                        .getExtras().getInt(
                                                DownloadAndCacheService.QUEUE_SIZE)
                                : 0;
                switch (status) {
                    case DownloadJob.CONNECTING:
                        downloader.setTileProgressText("Connecting...");
                        downloader.setLayerProgressText("");
                        break;
                    case DownloadJob.COMPLETE:
                        notification(mapTitle, false);
                        downloader.setTileProgressText("");
                        downloader.setLayerProgressText("Done");
                        downloader.setTimeProgressText(0);
                        downloader.cancelRegionSelect();
                        if (downloadsLeft <= 0
                                && !downloader.isSelectingRegion()
                                && !downloader.hasRegionShape())
                            lmbr.onDownloadComplete();
                        if (downloadsLeft <= 0)
                            downloader.toggleProgressBarVisibility(false);
                        break;
                    case DownloadJob.CANCELLED:
                        downloader.toastStatus("Download of " + mapTitle
                                + " cancelled.",
                                Toast.LENGTH_SHORT);
                        downloader.setTileProgressText("");
                        downloader.setLayerProgressText("Cancelled");
                        downloader.cancelRegionSelect();
                        downloader.setTimeProgressText(0);
                        if (downloadsLeft <= 0
                                && !downloader.isSelectingRegion()
                                && !downloader.hasRegionShape())
                            lmbr.onDownloadCanceled();
                        if (downloadsLeft <= 0)
                            downloader.toggleProgressBarVisibility(false);
                        break;
                    case DownloadJob.DOWNLOADING:
                        downloader.toggleProgressBarVisibility(true);
                        break;
                    case DownloadJob.ERROR:
                        notification(mapTitle, true);
                        downloader.setTileProgressText("");
                        downloader.setLayerProgressText("Done with errors");
                        downloader.setTimeProgressText(0);
                        downloader.cancelRegionSelect();
                        if (downloadsLeft <= 0
                                && !downloader.isSelectingRegion()
                                && !downloader.hasRegionShape())
                            lmbr.onDownloadError();
                        if (downloadsLeft <= 0)
                            downloader.toggleProgressBarVisibility(false);
                        break;
                    default:
                        break;
                }
            } else if (intent.getExtras().containsKey(
                    DownloadAndCacheService.PROGRESS_BAR_STATUS)) {
                if (intent.getExtras().containsKey(
                        DownloadAndCacheService.PROGRESS_BAR_SET_MAX)) {
                    downloader.setProgressBarMax(intent.getExtras().getInt(
                            DownloadAndCacheService.PROGRESS_BAR_SET_MAX));
                } else if (intent.getExtras().containsKey(
                        DownloadAndCacheService.PROGRESS_BAR_ADJUST_SECONDARY)) {
                    downloader
                            .adjustProgressBarSecondary(intent
                                    .getExtras()
                                    .getInt(
                                            DownloadAndCacheService.PROGRESS_BAR_ADJUST_SECONDARY));
                }
            }
        }
    }

    private void notification(String mapTitle, boolean errors) {
        int icon = R.drawable.download_complete;
        NotificationUtil.NotificationColor color = NotificationUtil.GREEN;
        String success = "successfully";
        if (errors) {
            success = "with errors";
            icon = R.drawable.download_complete_errors;
            color = NotificationUtil.RED;
        }
        NotificationUtil.getInstance().postNotification(icon, color,
                "Download of " + mapTitle + " complete",
                "Download Complete",
                mapTitle + " finished downloading " + success);
    }

}


package com.atakmap.android.layers.wms;

import android.widget.Toast;

import com.atakmap.android.layers.LayerDownloader;
import com.atakmap.android.layers.LayersManagerBroadcastReceiver;
import com.atakmap.android.layers.OnlineLayersDownloadManager;
import com.atakmap.android.util.NotificationUtil;
import com.atakmap.app.R;

public class DownloadAndCacheBroadcastReceiver
        implements LayerDownloader.Callback {

    private final OnlineLayersDownloadManager downloader;
    private final LayersManagerBroadcastReceiver lmbr;

    public DownloadAndCacheBroadcastReceiver(
            final OnlineLayersDownloadManager downloader,
            final LayersManagerBroadcastReceiver lmbr) {
        this.downloader = downloader;
        this.downloader.setCallback(this);
        this.lmbr = lmbr;
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

    @Override
    public void onDownloadStatus(LayerDownloader.DownloadStatus status) {
        if (status.queuedDownloads <= 1) {
            downloader.setTileProgressText(status.tileStatus + " tiles");
            downloader.setLayerProgressText(status.layerStatus + " layers");
            downloader.setLabelVisibility(false, 0);
        } else {
            downloader.setLabelVisibility(true, status.queuedDownloads);
            downloader.setTileProgressText(status.tileStatus);
            downloader.setLayerProgressText(status.layerStatus);
        }
        downloader.setProgress(status.tilesDownloaded, status.levelTotalTiles,
                status.totalTiles);
        downloader.setTimeProgressText(status.timeLeft);
    }

    @Override
    public void onMaxProgressUpdate(String title, int progress) {
        downloader.setProgressBarMax(progress);
    }

    @Override
    public void onLevelProgressUpdate(String title, int progress) {
        downloader.adjustProgressBarSecondary(progress);
    }

    @Override
    public void onJobStatus(LayerDownloader.JobStatus status) {
        switch (status.code) {
            case DownloadJob.CONNECTING:
                downloader.setTileProgressText("Connecting...");
                downloader.setLayerProgressText("");
                break;
            case DownloadJob.DOWNLOADING:
                downloader.toggleProgressBarVisibility(true);
                break;
            case DownloadJob.COMPLETE:
            case DownloadJob.CANCELLED:
            case DownloadJob.ERROR: {
                boolean errors = status.code == DownloadJob.ERROR;
                boolean canceled = status.code == DownloadJob.CANCELLED;
                if (canceled)
                    downloader.toastStatus("Download of " + status.title
                            + " cancelled.",
                            Toast.LENGTH_SHORT);
                else
                    notification(status.title, errors);
                downloader.setTileProgressText("");
                downloader.setLayerProgressText(errors ? "Done with errors"
                        : (canceled ? "Cancelled" : "Done"));
                downloader.setTimeProgressText(0);
                downloader.cancelRegionSelect();
                if (status.queuedDownloads <= 0
                        && !downloader.isSelectingRegion()
                        && !downloader.hasRegionShape()) {
                    if (errors)
                        lmbr.onDownloadError();
                    else if (canceled)
                        lmbr.onDownloadCanceled();
                    else
                        lmbr.onDownloadComplete();
                }
                if (status.queuedDownloads <= 0)
                    downloader.toggleProgressBarVisibility(false);
                break;
            }
        }
    }

}


package com.atakmap.android.importfiles.http;

import android.app.Activity;
import android.content.Context;

import com.atakmap.android.importfiles.resource.RemoteResource;
import com.atakmap.android.importfiles.task.NetworkLinkRefresh;
import com.atakmap.android.util.NotificationUtil;
import com.atakmap.app.R;
import com.foxykeep.datadroid.requestmanager.RequestManager;

import java.util.HashSet;
import java.util.Set;

/**
 * Abstract network link downloader/refresher
 */
public abstract class NetworkLinkDownloader
        implements RequestManager.RequestListener {

    protected final Context _context;
    protected final Set<String> _downloading = new HashSet<>();
    protected final NetworkLinkRefresh _refresher;
    private int _curNotificationId;

    protected NetworkLinkDownloader(Context appCtx, int baseNotificationId) {
        _context = appCtx;
        _curNotificationId = baseNotificationId;
        _refresher = new NetworkLinkRefresh((Activity) appCtx, this);
    }

    public Context getContext() {
        return _context;
    }

    /**
     * Keep for legacy behavior.
     */
    public NetworkLinkRefresh getLinkRefresh() {
        return _refresher;
    }

    public synchronized int getNotificationId() {
        return _curNotificationId++;
    }

    /**
     * Add a link to the refresher
     * @param res Remote resource
     */
    public void addRefreshLink(RemoteResource res) {
        _refresher.add(res);
    }

    public void removeRefreshLink(RemoteResource res) {
        _refresher.remove(res);
    }

    public boolean isDownloading(String url) {
        return _downloading.contains(url);
    }

    /**
     * Begin downloading a remote resource
     * @param resource Remote resource
     * @param showNotifications True to show notifications during DL/import
     */
    public abstract void download(RemoteResource resource,
            boolean showNotifications);

    public void download(RemoteResource resource) {
        download(resource, true);
    }

    public void shutdown() {
        _refresher.shutdown();
    }

    protected void postNotification(int notifyId, int icon, String title,
            String msg) {
        NotificationUtil.getInstance().postNotification(notifyId, icon,
                isErrorNotification(icon)
                        ? NotificationUtil.RED
                        : NotificationUtil.BLUE,
                title, msg, msg);
    }

    protected void postNotification(int notifyId, int icon, int title,
            int msg) {
        postNotification(notifyId, icon, getString(title), getString(msg));
    }

    protected void postNotification(RemoteResourcesRequest req, int icon,
            String title, String msg) {
        if (req.showNotifications() || isErrorNotification(icon))
            postNotification(req.getNotificationId(), icon, title, msg);
    }

    protected void postNotification(RemoteResourceRequest req, int icon,
            String title, String msg) {
        if (req.showNotifications() || isErrorNotification(icon))
            postNotification(req.getNotificationId(), icon, title, msg);
    }

    protected String getString(int strId, Object... args) {
        return _context.getString(strId, args);
    }

    protected boolean isErrorNotification(int iconId) {
        return iconId == R.drawable.ic_network_error_notification_icon;
    }
}

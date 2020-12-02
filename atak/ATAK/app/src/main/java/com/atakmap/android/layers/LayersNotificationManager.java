
package com.atakmap.android.layers;

import android.content.Context;
import android.os.SystemClock;
import android.app.Notification;

import com.atakmap.android.util.NotificationUtil;
import com.atakmap.coremap.log.Log;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

public final class LayersNotificationManager {
    private final static String TAG = "LayersNotificationManager";

    private final static String GROUP_KEY_IMAGERY = "imagery";

    private static Context context;

    private static final Impl impl = new NotificationImpl();

    private LayersNotificationManager() {
    }

    static void initialize(Context context) {
        LayersNotificationManager.context = context;
    }

    public synchronized static void notifyImportStarted(File file) {
        impl.notifyBegin(context, file);
    }

    public synchronized static void notifyImportComplete(File file,
            boolean success) {
        impl.notifyComplete(context, file, success);
    }

    public synchronized static void notifyImportProgress(File file,
            int itemsProcessed) {
        String msg;
        boolean important = false;
        if (itemsProcessed >= 0) {
            msg = itemsProcessed + " items processed";
        } else if (itemsProcessed == -100) {
            msg = "computing coverages (may take a while)";
            important = true;
        } else {
            msg = "...";
        }

        impl.notifyProgress(context, file, msg, important);
    }

    private static class NotificationSpec {
        public int notifyId;
        public String message;
        public long lastUpdate;

        public NotificationSpec(String message) {
            this.message = message;
            this.notifyId = -1;
            this.lastUpdate = -1L;
        }
    }

    /**************************************************************************/

    private interface Impl {
        void notifyBegin(Context context, File file);

        void notifyProgress(Context context, File file, String message,
                boolean important);

        void notifyComplete(Context context, File file, boolean success);
    }

    private static class LargeFormatNotificationImpl implements Impl {
        private final static long NOTIFICATION_UPDATE_INTERVAL = 100L;

        private final Map<File, NotificationSpec> fileStatus = new LinkedHashMap<>();
        private int successful = 0;
        private int failed = 0;
        private int notificationId = -1;

        @Override
        public void notifyBegin(Context context, File file) {
            fileStatus.put(file, new NotificationSpec("loading..."));

            this.notifyUpdate(context, false);
        }

        @Override
        public void notifyProgress(Context context, File file, String msg,
                boolean important) {
            NotificationSpec s = fileStatus.get(file);
            if (s != null) {
                s.message = msg;
                final long current = SystemClock.elapsedRealtime();
                if (important
                        || (current
                                - s.lastUpdate) >= NOTIFICATION_UPDATE_INTERVAL) {
                    s.lastUpdate = current;
                    this.notifyUpdate(context, important);
                }
            } else {
                Log.w(TAG, "Requesting progress update for " + file.getName()
                        + "; no record of import starting");
            }
        }

        @Override
        public void notifyComplete(Context context, File file,
                boolean success) {
            if (success)
                successful++;
            else
                failed++;

            // processing for the file is complete; remove it
            NotificationSpec notification = fileStatus.remove(file);

            this.notifyUpdate(context, false);
            if (notification != null) {
                if (notification.notifyId != -1)
                    NotificationUtil.getInstance().clearNotification(
                            notification.notifyId);
            }
            // if the load failed, post as a new, individual notification 
            if (!success) {
                NotificationUtil.getInstance().postNotification(
                        NotificationUtil.GeneralIcon.SYNC_ERROR.getID(),
                        NotificationUtil.RED,
                        "Failed to Load",
                        null,
                        file.getName() + " failed to load.");
            }
        }

        private void notifyUpdate(Context context, boolean important) {
            if (notificationId == -1) {
                notificationId = NotificationUtil.getInstance()
                        .reserveNotifyId();

                NotificationUtil.getInstance().postNotification(
                        notificationId,
                        NotificationUtil.GeneralIcon.SYNC_ORIGINAL.getID(),
                        NotificationUtil.BLUE,
                        "Loading Imagery (" + successful + " Loaded, " + failed
                                + " Failed)",
                        null, null, !important);
            }

            Notification.Builder builder = NotificationUtil.getInstance()
                    .getNotificationBuilder(notificationId);

            // moving back to a smaller version of the v4 compat library
            //builder.setGroup(GROUP_KEY_IMAGERY);
            //builder.setGroupSummary(true);

            Notification.InboxStyle style = new Notification.InboxStyle();
            for (Map.Entry<File, NotificationSpec> status : fileStatus
                    .entrySet())
                style.addLine(status.getKey().getName() + " "
                        + status.getValue().message);

            builder.setStyle(style);

            Notification summaryNotification = builder.build();
            NotificationUtil.getInstance().postNotification(notificationId,
                    summaryNotification, !important);
        }
    }

    private static class NotificationImpl implements Impl {
        private final static long NOTIFICATION_UPDATE_INTERVAL = 100L;

        private final Map<File, NotificationSpec> fileStatus = new LinkedHashMap<>();
        private boolean summaryDirty = false;
        private int successful = 0;
        private int failed = 0;
        private int notificationId = -1;

        Notification.Builder statusBuilder;
        Notification.Builder summaryBuilder;

        @Override
        public void notifyBegin(Context context, File file) {
            fileStatus.put(file, new NotificationSpec("loading..."));

            this.notifyUpdate(context, false);
        }

        @Override
        public void notifyProgress(Context context, File file, String msg,
                boolean important) {
            NotificationSpec s = fileStatus.get(file);
            if (s != null) {
                s.message = msg;
                final long current = SystemClock.elapsedRealtime();
                if (important
                        || (current
                                - s.lastUpdate) >= NOTIFICATION_UPDATE_INTERVAL) {
                    s.lastUpdate = current;
                    this.notifyUpdate(context, important);
                }
            } else {
                Log.w(TAG, "Requesting progress update for " + file.getName()
                        + "; no record of import starting");
            }
        }

        @Override
        public void notifyComplete(Context context, File file,
                boolean success) {
            if (success)
                successful++;
            else
                failed++;
            this.summaryDirty = true;

            // processing for the file is complete; remove it
            NotificationSpec notification = fileStatus.remove(file);

            this.notifyUpdate(context, false);
            if (notification != null && notification.notifyId != -1)
                NotificationUtil.getInstance().clearNotification(
                        notification.notifyId);

            // if the load failed, post as a new, individual notification 
            if (!success) {
                NotificationUtil.getInstance().postNotification(
                        NotificationUtil.GeneralIcon.SYNC_ERROR.getID(),
                        NotificationUtil.RED,
                        "Failed to Load",
                        null,
                        file.getName() + " failed to load.");
            }
        }

        private void notifyUpdate(Context ignored, boolean important) {
            if (this.notificationId == -1) {
                this.notificationId = NotificationUtil.getInstance()
                        .reserveNotifyId();
            }

            final int icon = NotificationUtil.GeneralIcon.SYNC_ORIGINAL.getID();

            if (summaryDirty) {
                if (this.summaryBuilder == null) {
                    NotificationUtil.getInstance().postNotification(
                            notificationId,
                            icon, NotificationUtil.WHITE,
                            "Loading Imagery (" + successful + " Loaded, "
                                    + failed + " Failed)",
                            null, null,
                            !important);
                    this.summaryBuilder = NotificationUtil.getInstance()
                            .getNotificationBuilder(notificationId);

                    if (summaryBuilder != null) {
                        // moving back to a smaller version of the v4 compat library
                        //this.summaryBuilder.setGroup(GROUP_KEY_IMAGERY);
                        //this.summaryBuilder.setGroupSummary(true);
                    }
                }

                if (summaryBuilder != null) {
                    this.summaryBuilder.setContentTitle("Loading Imagery ("
                            + successful + " Loaded, " + failed + " Failed)");

                    NotificationUtil.getInstance().postNotification(
                            this.notificationId, this.summaryBuilder.build(),
                            !important);
                }
                this.summaryDirty = false;
            }

            if (!fileStatus.isEmpty()) {

                String title;
                String message;

                for (Map.Entry<File, NotificationSpec> status : fileStatus
                        .entrySet()) {
                    title = status.getKey().getName();
                    message = status.getValue().message;

                    if (status.getValue().notifyId == -1)
                        status.getValue().notifyId = NotificationUtil
                                .getInstance().reserveNotifyId();

                    if (this.statusBuilder == null) {
                        NotificationUtil.getInstance().postNotification(
                                status.getValue().notifyId, icon,
                                NotificationUtil.WHITE,
                                "Loading " + title, null, null);

                        this.statusBuilder = NotificationUtil.getInstance()
                                .getNotificationBuilder(
                                        status.getValue().notifyId);

                        // set common properties
                        if (summaryBuilder != null) {
                            // moving back to a smaller version of the v4 compat library
                            //this.statusBuilder.setGroup(GROUP_KEY_IMAGERY);
                            //this.statusBuilder.setGroupSummary(true);
                            this.statusBuilder.setSmallIcon(icon);
                        }
                    }

                    if (summaryBuilder != null) {
                        statusBuilder.setContentTitle("Loading " + title);
                        statusBuilder.setContentText(message);

                        NotificationUtil.getInstance().postNotification(
                                status.getValue().notifyId,
                                statusBuilder.build(),
                                !important);
                    }
                }
            }
        }
    }
}

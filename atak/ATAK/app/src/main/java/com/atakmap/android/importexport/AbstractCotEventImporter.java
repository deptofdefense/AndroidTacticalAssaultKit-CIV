
package com.atakmap.android.importexport;

import android.content.Context;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;

import com.atakmap.android.cot.CotMapAdapter;
import com.atakmap.android.math.MathUtils;
import com.atakmap.android.util.NotificationUtil;
import com.atakmap.app.R;
import com.atakmap.coremap.cot.event.CotAttribute;
import com.atakmap.coremap.cot.event.CotDetail;
import com.atakmap.coremap.cot.event.CotEvent;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.comms.CommsMapComponent.ImportResult;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.CRC32;

public abstract class AbstractCotEventImporter extends AbstractImporter
        implements CotEventImporter {

    final public static String TAG = "AbstractCotEventImporter";

    private final static Bundle EMPTY_BUNDLE = new Bundle();

    protected final Set<String> supportedMimeTypes = new HashSet<>();

    // Detail tags that should be ignored when generating a CRC for marker details
    protected final Set<String> crcBlacklist = new HashSet<>();

    private final Context context;

    protected AbstractCotEventImporter(Context context, String contentType) {
        super(contentType);

        this.context = context;

        this.supportedMimeTypes.add("application/cot+xml");

        // This are server-side tags that should be ignored when checking for
        // detail changes
        this.crcBlacklist.add("marti");
        this.crcBlacklist.add(CotMapAdapter.FLOWTAG);
    }

    @Override
    public abstract ImportResult importData(CotEvent cot, Bundle bundle);

    protected abstract ImportResult importNonCotData(InputStream source,
            String mime) throws IOException;

    /**************************************************************************/
    // Importer

    @Override
    public Set<String> getSupportedMIMETypes() {
        return this.supportedMimeTypes;
    }

    @Override
    public ImportResult importData(InputStream source, String mime,
            final Bundle bundle) throws IOException {
        boolean showNotifications = false;
        if (bundle != null
                && bundle
                        .containsKey(ImportReceiver.EXTRA_SHOW_NOTIFICATIONS)) {
            showNotifications = bundle
                    .getBoolean(ImportReceiver.EXTRA_SHOW_NOTIFICATIONS);
        }

        final int notificationId = NotificationUtil.getInstance()
                .reserveNotifyId();
        if (showNotifications) {
            NotificationUtil
                    .getInstance()
                    .postNotification(
                            notificationId,
                            NotificationUtil.GeneralIcon.SYNC_ORIGINAL.getID(),
                            NotificationUtil.BLUE,
                            String.format(
                                    context.getString(
                                            R.string.importmgr_starting_import),
                                    getContentType()),
                            null, null, false);
        }

        ImportResult retval = ImportResult.FAILURE;

        if (mime.equals("application/cot+xml")) {
            @SuppressWarnings("resource")
            final InputStreamReader reader = new InputStreamReader(source);
            char[] arr = new char[128];
            int numChars;
            StringBuilder cot = new StringBuilder();
            do {
                numChars = reader.read(arr);
                if (numChars < 0)
                    break;
                cot.append(arr, 0, numChars);
            } while (true);

            retval = this.importData(CotEvent.parse(cot.toString()),
                    (bundle != null) ? bundle : EMPTY_BUNDLE);
        } else {
            retval = this.importNonCotData(source, mime);
        }

        if (showNotifications) {
            if (retval == ImportResult.SUCCESS) {
                NotificationUtil
                        .getInstance()
                        .postNotification(
                                notificationId,
                                NotificationUtil.GeneralIcon.SYNC_ORIGINAL
                                        .getID(),
                                NotificationUtil.BLUE,
                                String.format(
                                        context.getString(
                                                R.string.importmgr_finished_import),
                                        getContentType()),
                                null, null, true);
            } else {
                NotificationUtil
                        .getInstance()
                        .postNotification(
                                notificationId,
                                NotificationUtil.GeneralIcon.SYNC_ERROR.getID(),
                                NotificationUtil.RED,
                                String.format(
                                        context.getString(
                                                R.string.importmgr_failed_import),
                                        getContentType()),
                                null, null, true);
            }
        }
        return retval;
    }

    @Override
    public ImportResult importData(Uri uri, String mime, Bundle bundle)
            throws IOException {
        return importUriAsStream(this, uri, mime, bundle);
    }

    /**************************************************************************/

    public static ImportResult importData(Importer importer, CotEvent event,
            Bundle bundle) throws IOException {

        if (importer == null)
            return ImportResult.FAILURE;

        StringBuilder xml = new StringBuilder();
        event.buildXml(xml);

        ByteArrayInputStream inputStream = null;
        try {
            inputStream = new ByteArrayInputStream(xml.toString().getBytes(
                    FileSystemUtils.UTF8_CHARSET));
            return importer.importData(inputStream, "application/cot+xml",
                    bundle);
        } finally {
            if (inputStream != null)
                inputStream.close();
        }
    }

    /**
     * Convert a string to a double (exceptions caught)
     *
     * @param value String value
     * @param defaultVal Default value if conversion fails
     * @return Converted value or default value if conversion failed
     */
    protected double parseDouble(String value, double defaultVal) {
        return MathUtils.parseDouble(value, defaultVal);
    }

    /**
     * Convert a string to an integer (exceptions caught)
     * @param value String value
     * @param defaultVal Default value if conversion fails
     * @return Converted value or default if failed
     */
    protected int parseInt(String value, int defaultVal) {
        return MathUtils.parseInt(value, defaultVal);
    }

    /**
     * Convert a string to a color (exception color)
     * String may be in hex format or the color name (i.e. "red")
     * @param value String value
     * @param defaultColor Default color if conversion fails
     * @return Converted color or default if failed
     */
    protected int parseColor(String value, int defaultColor) {
        try {
            return Color.parseColor(value);
        } catch (Exception e) {
            return defaultColor;
        }
    }

    /**
     * Generate a CRC32 for a CoT details node
     * @param root Root detail node
     * @param crc CRC32 instance
     */
    protected void crcDetails(CotDetail root, CRC32 crc) {
        if (root == null)
            return;
        // Populate map for other details
        List<CotDetail> children = root.getChildren();
        for (CotDetail d : children) {
            if (d == null)
                continue;

            String dName = d.getElementName();
            if (this.crcBlacklist.contains(dName))
                continue;

            crc.update(dName.getBytes());
            crc.update(0);

            CotAttribute[] attrs = d.getAttributes();
            if (!FileSystemUtils.isEmpty(attrs)) {
                for (CotAttribute att : attrs) {
                    if (att == null)
                        continue;

                    if (this.crcBlacklist.contains(dName + "/" + att.getName()))
                        continue;

                    crc.update(
                            (att.getName() + "=" + att.getValue()).getBytes());
                    crc.update(0);
                }
            }

            // Child details
            crcDetails(d, crc);
        }
    }
}


package com.atakmap.android.maps;

import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.widget.Toast;

import com.atakmap.android.importexport.ImportExportMapComponent;
import com.atakmap.android.metrics.activity.MetricActivity;
import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

/**
 * Simple import activity to copy file from VIEW action, and pass off to ATAK Import Manager
 * May be run whether main ATAKActivity is currently running or not
 *
 * 
 */
public class ImportFileActivity extends MetricActivity {

    public static final String TAG = "ImportFileActivity";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(null);

        final Intent intent = getIntent();
        final String action = intent.getAction();
        Uri uri = null;

        try {
            uri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
        } catch (Exception e) {
            Log.e(TAG, "error getting parcelable", e);
        }

        if (action == null || uri == null) {
            Log.d(TAG, "unable to open: " + action + " " + uri);
            finish();
            return;
        }

        if (action.equals(Intent.ACTION_VIEW) ||
                action.equals(Intent.ACTION_SEND)) {

            final File sendtoLocation = FileSystemUtils.getItem("tools/sendto");
            FileSystemUtils.deleteDirectory(sendtoLocation, false);

            IOProviderFactory.mkdirs(sendtoLocation);
            ContentResolver resolver = getContentResolver();
            final String fileName = getContentName(resolver, uri);

            if (fileName == null) {
                Log.e(TAG, "could not import: " + uri);
                finish();
                return;
            }

            final File fileToImport = new File(sendtoLocation, fileName);

            try (InputStream is = resolver.openInputStream(uri);
                    FileOutputStream fos = new FileOutputStream(fileToImport)) {
                FileSystemUtils.copy(is, fos);
            } catch (Exception ignored) {
            }

            Intent atakFrontIntent = new Intent();
            atakFrontIntent.setComponent(new ComponentName(
                    getPackageName(),
                    getString(R.string.atak_activity)));

            // because we need to copy the file to a new location, we do not want it imported in
            // place.
            final Intent i = new Intent(
                    ImportExportMapComponent.USER_HANDLE_IMPORT_FILE_ACTION);
            i.putExtra("filepath", fileToImport.getAbsolutePath())
                    .putExtra("promptOnMultipleMatch", true)
                    .putExtra("importInPlace", false);

            atakFrontIntent.putExtra("internalIntent", i);

            atakFrontIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP
                    | Intent.FLAG_ACTIVITY_SINGLE_TOP);

            // requires the use of currentTimeMillis
            PendingIntent contentIntent = PendingIntent.getActivity(this,
                    (int) System.currentTimeMillis(),
                    atakFrontIntent, PendingIntent.FLAG_CANCEL_CURRENT);
            try {
                contentIntent.send();
            } catch (Exception e) {
                Log.e(TAG, "pending intent error");
            }
        }
        finish();
    }

    private String getContentName(ContentResolver resolver, Uri uri) {
        Cursor cursor = null;
        try {
            cursor = resolver.query(uri, null, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int nameIndex = cursor
                        .getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME);
                if (nameIndex >= 0) {
                    return cursor.getString(nameIndex);
                }
            }
        } catch (SecurityException se) {
            Log.e(TAG, "unable to load: " + uri, se);
            Toast.makeText(this, String.format("Unable to load %s", uri),
                    Toast.LENGTH_LONG).show();

        } finally {
            if (cursor != null)
                cursor.close();
        }

        return null;
    }

}

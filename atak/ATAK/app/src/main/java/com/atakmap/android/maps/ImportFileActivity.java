
package com.atakmap.android.maps;

import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.atakmap.android.importexport.ImportExportMapComponent;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.metrics.activity.MetricActivity;
import com.atakmap.android.update.AppMgmtUtils;
import com.atakmap.app.ATAKActivity;
import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.FileIOProviderFactory;
import com.atakmap.coremap.log.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
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
        super.onCreate(savedInstanceState);

        //display splash/loading screen
        setContentView(R.layout.atak_import_activity);
        try {
            ((TextView) findViewById(R.id.revision))
                    .setText("Version "
                            + getPackageManager().getPackageInfo(
                                    getPackageName(), 0).versionName);

            File splash = FileSystemUtils
                    .getItem(FileSystemUtils.SUPPORT_DIRECTORY
                            + File.separatorChar + "atak_splash.png");
            if (FileSystemUtils.isFile(splash)) {
                Bitmap bmp = BitmapFactory.decodeFile(splash.getAbsolutePath());
                if (bmp != null) {
                    Log.d(TAG, "Loading custom splash screen");
                    ImageView atak_splash_imgView = findViewById(
                            R.id.atak_splash_imgView);
                    atak_splash_imgView.setImageBitmap(bmp);
                }
            }

        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "Error: " + e);
        }

        //get dest directory
        Log.d(TAG, "onCreate");
        final Intent intent = getIntent();
        String action = intent.getAction();
        File destDir = new File(this.getCacheDir(), FileSystemUtils.ATAKDATA);
        if (!FileIOProviderFactory.mkdirs(destDir)) {
            Log.e(TAG, "Error creating directories");
        }
        File destFile = null;

        //copy source file to dest dir
        if (FileSystemUtils.isEquals(action, Intent.ACTION_VIEW)) {
            String scheme = intent.getScheme();
            ContentResolver resolver = getContentResolver();

            if (FileSystemUtils
                    .isEquals(scheme, ContentResolver.SCHEME_CONTENT)) {
                Uri uri = intent.getData();
                String name = getContentName(resolver, uri);
                ((TextView) findViewById(R.id.revision)).setText("Importing "
                        + name + "...");
                Log.v(TAG, "Content intent detected: " + action + " : "
                        + intent.getDataString() + " : " + intent.getType()
                        + " : " + name + "   from: " + uri);
                InputStream input = null;
                FileOutputStream fos = null;
                try {
                    if (uri != null) {
                        input = resolver.openInputStream(uri);

                        if (input != null) {
                            destFile = new File(destDir,
                                    FileSystemUtils.validityScan(name));
                            FileSystemUtils.copy(input,
                                    fos = FileIOProviderFactory.getOutputStream(
                                            destFile));
                        }
                    }
                } catch (SecurityException | IOException e) {
                    Log.w(TAG,
                            "Failed to open content file: " + uri.toString(),
                            e);
                    FileSystemUtils.deleteFile(destFile);
                } finally {
                    if (fos != null) {
                        try {
                            fos.close();
                        } catch (IOException e) {
                            Log.e(TAG, "failed to close the output stream");
                        }
                    }
                    if (input != null) {
                        try {
                            input.close();
                        } catch (IOException e) {
                            Log.e(TAG, "failed to close the input stream");
                        }
                    }
                }
            } else if (FileSystemUtils.isEquals(scheme,
                    ContentResolver.SCHEME_FILE)) {
                Uri uri = intent.getData();

                if (uri != null) {
                    final String name = FileSystemUtils.sanitizeFilename(uri
                            .getLastPathSegment());
                    ((TextView) findViewById(R.id.revision))
                            .setText("Importing "
                                    + name + "...");

                    Log.v(TAG,
                            "File intent detected: " + action + " : "
                                    + intent.getDataString() + " : "
                                    + intent.getType() + " : " + name);

                    InputStream input = null;
                    FileOutputStream fos = null;
                    try {
                        input = resolver.openInputStream(uri);
                        if (input != null && name != null) {
                            destFile = new File(destDir, name);
                            FileSystemUtils.copy(input,
                                    fos = FileIOProviderFactory.getOutputStream(
                                            destFile));
                        }
                    } catch (IOException e) {
                        Log.w(TAG,
                                "Failed to open scheme file: " + uri.toString(),
                                e);
                        FileSystemUtils.deleteFile(destFile);
                    } finally {
                        if (fos != null) {
                            try {
                                fos.close();
                            } catch (Exception ignore) {
                            }
                        }

                        if (input != null) {
                            try {
                                input.close();
                            } catch (Exception ignore) {
                            }
                        }
                    }
                }
            } else {
                Log.w(TAG, "Ignoring file scheme: " + scheme);
            }
        }

        if (!FileSystemUtils.isFile(destFile)) {
            Toast.makeText(this, "Ignoring unsupported file scheme",
                    Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        //see if ATAK is currently running
        boolean bIsATAKrunning = AppMgmtUtils.isActivityRunning(
                ATAKActivity.class, getBaseContext());
        Log.d(TAG, "Processing: " +
                ((destFile == null) ? "" : destFile.getAbsolutePath())
                + ", ATAK running: " + bIsATAKrunning);
        if (bIsATAKrunning) {
            if (destFile != null) {
                //send intent to import file now
                Log.d(TAG, "Sending intent to import file: " + destFile);
                try {
                    AtakBroadcast
                            .getInstance()
                            .sendBroadcast(
                                    new Intent(
                                            ImportExportMapComponent.USER_HANDLE_IMPORT_FILE_ACTION)
                                                    .putExtra("filepath",
                                                            destFile.getAbsolutePath()));
                    Toast.makeText(this,
                            "Importing " + destFile.getName() + "...",
                            Toast.LENGTH_LONG).show();
                } catch (Exception e) {
                    Log.w(TAG, "Failed to send ATAK broadcast", e);
                    Toast.makeText(this, "Launch ATAK to view file...",
                            Toast.LENGTH_LONG).show();
                }
            }
        } else {
            //launch ATAK activity to process files in "atakdata" folder
            Log.d(TAG, "Launching ATAK");
            try {
                startActivity(new Intent(this, ATAKActivity.class));
            } catch (ActivityNotFoundException e) {
                Log.w(TAG, "Failed to launch ATAK", e);
                Toast.makeText(this, "Launch ATAK to view file...",
                        Toast.LENGTH_LONG).show();
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

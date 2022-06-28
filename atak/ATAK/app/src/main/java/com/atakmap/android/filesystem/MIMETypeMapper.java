
package com.atakmap.android.filesystem;

import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.webkit.MimeTypeMap;
import android.widget.Toast;

import com.atakmap.android.util.FileProviderHelper;
import com.atakmap.app.R;
import com.atakmap.comms.http.HttpUtil;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;

import java.io.File;

/**
 * Map File extension to MIME Type using Android webkit
 * 
 * 
 */
public class MIMETypeMapper implements ContentTypeMapper {

    private static final String TAG = "MIMETypeMapper";

    @Override
    public String getContentType(File file) {
        return MIMETypeMapper.GetContentType(file);
    }

    /**
     * Given a file, attempt to determine the mime type from the extension
     * @param file the file
     * @return the mime type as a string
     */
    public static String GetContentType(File file) {
        String mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(
                FileSystemUtils.getExtension(file, false, false));

        // Use updated XML MIME Type...
        if (!FileSystemUtils.isEmpty(mimeType) && "text/xml".equals(mimeType))
            mimeType = HttpUtil.MIME_XML;

        return mimeType;
    }

    /**
     * Get an activity intent for opening a file
     *
     * @param context Application context
     * @param file File to open
     * @return Activity intent
     */
    public static Intent getOpenIntent(Context context, File file) {
        // Make sure file exists
        if (!FileSystemUtils.isFile(file))
            return null;

        // Need valid MIME type
        String mime = MIMETypeMapper.GetContentType(file);
        if (FileSystemUtils.isEmpty(mime))
            return null;

        try {
            // Build out intent
            Intent intent = new Intent();
            intent.setAction(android.content.Intent.ACTION_VIEW);
            FileProviderHelper.setDataAndType(context, intent, file, mime);

            // Try to resolve an activity
            ResolveInfo resolveInfo = context.getPackageManager()
                    .resolveActivity(
                            intent, PackageManager.MATCH_DEFAULT_ONLY);
            if (resolveInfo == null)
                return null;
            return intent;
        } catch (Exception e) {
            Log.e(TAG, "Failed to get activity intent for " + file, e);
            return null;
        }
    }

    /**
     * Open dialog with details of and operations for specified file
     * 
     * @param file the file to open
     */
    public static void openFile(final File file, final Context context) {
        if (!FileSystemUtils.isFile(file)) {
            Log.w(TAG, "Failed to open invalid file");

            new AlertDialog.Builder(context)
                    .setTitle("File not found")
                    .setMessage("Please refresh file list and try again")
                    .setCancelable(false)
                    .setNeutralButton(R.string.ok, null)
                    .show();

            return;
        }

        Log.d(TAG, "Opening file: " + file.getAbsolutePath());
        String mime = MIMETypeMapper.GetContentType(file);
        if (mime == null || mime.length() < 1) {
            Log.w(TAG,
                    "Failed to find MIME type for file: "
                            + file.getAbsolutePath());
            AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(
                    context);
            alertDialogBuilder
                    .setTitle("File MIME Type Not Found")
                    .setMessage(
                            "Please manually open the file or install an application to handle file type: "
                                    + FileSystemUtils.getExtension(file, true,
                                            false))
                    .setCancelable(false)
                    .setNeutralButton(R.string.ok, null)
                    .show();
            return;
        }
        Log.d(TAG, String.format("Mapped %s to MIME type: %s", file.getName(),
                mime));

        // build out intent
        Intent intent = getOpenIntent(context, file);
        if (intent == null) {
            // failed to resolve an activity
            Log.w(TAG, "Failed to resolve Activity for MIME type: %s" + mime);

            AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(
                    context);
            alertDialogBuilder
                    .setTitle(R.string.no_file_viewer_found)
                    .setMessage(
                            "Please manually open the file or install an application to handle MIME type: "
                                    + mime)
                    .setCancelable(false)
                    .setNeutralButton(R.string.ok, null)
                    .show();
            return;
        }

        // launch activity
        Log.d(TAG, String.format("Launching activity for MIME type: %s", mime));
        try {
            // close ATAK DropDowns (why?)
            //DropDownManager.getInstance().closeAllDropDowns();

            // now launch activity
            Toast.makeText(context, R.string.opening_file, Toast.LENGTH_SHORT)
                    .show();
            context.startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Log.e(TAG, "Failed to launch activity", e);
        }
    }
}

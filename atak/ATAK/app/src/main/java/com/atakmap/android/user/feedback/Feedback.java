
package com.atakmap.android.user.feedback;

import android.content.Context;

import androidx.annotation.NonNull;

import com.atakmap.android.util.ATAKConstants;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.locale.LocaleUtil;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.time.CoordinatedTime;
import gov.tak.api.util.Disposable;

import org.json.JSONObject;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class Feedback implements Disposable {

    private final static String TAG = "Feedback";

    private final Map<String, String> metadata = new HashMap<>();
    private final List<FeedbackFile> supportingFiles = new ArrayList<>();
    private final File folder;
    private final String FEEDBACK_FILE = "feedback.json";

    private final String JSON_FEEDBACK_ROOT = "feedback";
    private final String JSON_TITLE_ITEM = "title";
    private final String JSON_CALLSIGN_ITEM = "callsign";
    private final String JSON_DESCRIPTION_ITEM = "description";
    private final String JSON_TIMESTAMP_ITEM = "timestamp";

    private final String callsign;

    private boolean disposed = false;

    /**
     * Given a feedback directory, create a feedback object
     * @param folder the directory
     */
    public Feedback(File folder, String callsign) {
        this.folder = folder;
        this.callsign = callsign;
    }

    /**
     * Load the json file and supporting files.
     */
    public boolean load() {
        if (disposed)
            throw new IllegalStateException(
                    "cannot use a disposed feedback object");

        try {
            final File f = new File(folder, "feedback.json");

            if (!IOProviderFactory.exists(f))
                return false;

            String s = FileSystemUtils.copyStreamToString(f);
            JSONObject object = new JSONObject(s);
            JSONObject feedback = object.getJSONObject(JSON_FEEDBACK_ROOT);
            set(JSON_TITLE_ITEM, feedback.getString(JSON_TITLE_ITEM));
            set(JSON_CALLSIGN_ITEM, feedback.getString(JSON_CALLSIGN_ITEM));
            set(JSON_DESCRIPTION_ITEM,
                    feedback.getString(JSON_DESCRIPTION_ITEM));

            final File[] files = IOProviderFactory.listFiles(folder);
            if (files != null) {
                for (File file : files) {
                    if (!file.getName().equals(FEEDBACK_FILE)) {
                        supportingFiles.add(new FeedbackFile(file));
                    }
                }
            }

        } catch (Exception e) {
            Log.e(TAG, "error loading feedback", e);
            return false;
        }
        return true;
    }

    /**
     * Save the json file.
     * @param context the context to use for saving
     */
    public boolean save(Context context) {
        if (disposed)
            throw new IllegalStateException(
                    "cannot use a disposed feedback object");

        BufferedWriter output = null;
        try {
            File f = new File(folder, "feedback.json");
            JSONObject root = new JSONObject();
            JSONObject feedback = new JSONObject();
            root.put(JSON_FEEDBACK_ROOT, feedback);
            feedback.put(JSON_TITLE_ITEM, get(JSON_TITLE_ITEM));
            feedback.put(JSON_CALLSIGN_ITEM, callsign);
            feedback.put(JSON_DESCRIPTION_ITEM, get(JSON_DESCRIPTION_ITEM));
            feedback.put(JSON_TIMESTAMP_ITEM,
                    CoordinatedTime.toCot(new CoordinatedTime()));
            Map<String, String> information = ATAKConstants
                    .getGeneralInformation(context);
            for (Map.Entry<String, String> entry : information.entrySet())
                feedback.put(entry.getKey(), entry.getValue());

            output = new BufferedWriter(IOProviderFactory.getFileWriter(f));
            output.write(root.toString());
        } catch (Exception e) {
            Log.e(TAG, "error writing feedback file", e);
            return false;
        } finally {
            if (output != null)
                try {
                    output.close();
                } catch (IOException ignored) {
                }
        }
        return true;
    }

    /**
     * Return the value for the key
     * @return the value or an empty string if the value is not set
     */
    @NonNull
    public String get(String key) {
        String s = metadata.get(key);
        if (s == null)
            s = "";
        return s;
    }

    /**
     * Set the value for a key.
     * @param key the key
     * @param val the value
     */
    public void set(final String key, String val) {
        if (val == null)
            metadata.remove(key);
        else
            metadata.put(key, val);
    }

    @Override
    public void dispose() {
        FileSystemUtils.delete(folder);
        metadata.clear();
        disposed = true;
    }

    /**
     * Add associated files to the Feedback that will be included during submission.    If there is a
     * file that already exists, then modify the file name to reflect a copy much like microsoft
     * windows does.
     * @param src the file to add
     * @return the feedback file object representing for the included file.
     */
    public synchronized FeedbackFile addAssociatedFile(File src) {
        if (disposed)
            throw new IllegalStateException(
                    "cannot use a disposed feedback object");

        for (FeedbackFile feedbackFile : supportingFiles) {
            if (feedbackFile.getFile().equals(src))
                return feedbackFile;
        }

        File dest = new File(folder, src.getName());
        int count = 0;
        while (dest.exists()) {
            count++;
            String r = src.getName();
            int lastDotIndex = r.lastIndexOf('.');
            if (lastDotIndex > 0)
                r = r.substring(0, lastDotIndex) + "_"
                        + formatLeadingZero(count, 2)
                        + r.substring(lastDotIndex);
            dest = new File(folder, r);
        }

        try {
            FileSystemUtils.copyFile(src, dest);

            final FeedbackFile retval = new FeedbackFile(dest);
            supportingFiles.add(retval);
            return retval;
        } catch (Exception e) {
            Log.e(TAG, "Failed to copy file from " + src + " to " + dest, e);
            return null;
        }

    }

    /**
     * Remove the associated feedback
     * @param f the feedback file to remove
     */
    public synchronized void removeAssociatedFile(FeedbackFile f) {
        FileSystemUtils.delete(f.getFile());
        supportingFiles.remove(f);
    }

    /**
     * Return the associated feedback files.
     * @return a shallow copy of the associated feedback files list
     */
    public ArrayList<FeedbackFile> getAssociatedFiles() {
        return new ArrayList<>(supportingFiles);
    }

    private String formatLeadingZero(final int i, final int num) {
        return String.format(LocaleUtil.US, "%0" + num + "d", i);
    }

    /**
     * Obtains the folder that represents the feedback.
     * @return the folder that represents the feedback
     */
    public File getFolder() {
        return folder;
    }

    /**
     * Gets the size of the feeback in bytes.
     * @return return the size in bytes
     */
    public long getFeedbackSize() {
        return folderSize(folder);
    }

    private static long folderSize(File directory) {
        long length = 0;
        for (File file : IOProviderFactory.listFiles(directory)) {
            if (IOProviderFactory.isFile(file))
                length += IOProviderFactory.length(file);
            else
                length += folderSize(file);
        }
        return length;
    }

}

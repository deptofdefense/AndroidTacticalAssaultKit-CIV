
package com.atakmap.android.user.feedback;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import com.atakmap.android.util.ImageThumbnailCache;
import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;

import java.io.File;

class FeedbackFile {

    private final static ImageThumbnailCache itc = new ImageThumbnailCache(
            FileSystemUtils.getItem(
                    UserFeedbackCollector.FEEDBACK_FOLDER + "/.cache/"));

    private final File f;

    /**
     * Construct a feedback file object that points to a file to be included in the feedback
     * provided by the user.
     * @param f the file
     */
    public FeedbackFile(File f) {
        this.f = f;

    }

    /**
     * Returns the underlying file referenced by the FeedbackFile pointer.
     * @return the file
     */
    public File getFile() {
        return f;
    }

    /**
     * Returns the name of the file as a shortcut to getFile().getName().
     * @return the file name
     */
    public String getName() {
        return f.getName();
    }

    /**
     * Returns an icon created for the file.  This makes use of the ImageThumbnail cache that is
     * shared across all feedback/
     * @param context the context used for resource lookup
     * @param r the callback to be notified when the icon is generated
     * @return the bitmap to use until / when if the callback is called.
     */
    public Bitmap getIcon(Context context, ImageThumbnailCache.Callback r) {
        Bitmap icon = itc.getOrCreateThumbnail(f, 128, 128, r);
        if (icon == null)
            return BitmapFactory.decodeResource(context.getResources(),
                    R.drawable.details);
        else
            return icon;
    }

}

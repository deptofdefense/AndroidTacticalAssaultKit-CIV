
package com.atakmap.android.contact;

import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.widget.Toast;

import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.PointMapItem;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;
import com.atakmap.filesystem.HashingUtils;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;

/**
 * Represents an avatar (e.g. profile pic) provided by an
 * <code>{@link com.atakmap.android.contact.ContactConnectorManager.ContactConnectorHandler}</code>
 *
 *
 */
public abstract class AvatarFeature {

    private static final String TAG = "AvatarFeature";

    /**
     * Validate the feature instance
     * @return returns the validity of the feature
     */
    public abstract boolean isValid();

    /**
     * Get the avatar image as bitmap
     * @return the bitmap representing the avatar
     */
    public abstract Bitmap getAvatar();

    /**
     * Get the avatar image bytes
     * @return the raw image bytes that correspond with the Bitmap.
     */
    public abstract byte[] getAvatarBytes();

    /**
     * Get the avatar hash
     * @return the hashcode that describes the avatar
     */
    public abstract String getHash();

    /**
     * Get the associated comms connector type
     * @return the connector type string
     */
    public abstract String getConnectorType();

    /**
     * Store VCard avatar on file system
     * Currently saves to atak/tmp
     * Re-use existing avatar file if it exists and has same hash
     *
     * @param filename the name of the file to save to
     * @return file the actual file saved
     */
    public File saveFile(final String filename) {
        //TODO do on background thread?
        if (!isValid() || FileSystemUtils.isEmpty(filename))
            return null;

        final String avatarDirPath = FileSystemUtils.getItem(
                FileSystemUtils.TMP_DIRECTORY).getPath();
        final File dir = new File(avatarDirPath);
        if (!IOProviderFactory.exists(dir)) {
            Log.d(TAG, "creating avatar directory: " + dir);
            if (!IOProviderFactory.mkdirs(dir))
                Log.w(TAG, "Failed to mkdir: " + dir);
        }

        boolean bCreate;
        final File avatarFile = new File(dir, filename);

        String existingHash;
        String newHash = getHash();
        if (IOProviderFactory.exists(avatarFile)) {
            //check hash prior to creating file
            existingHash = HashingUtils.sha1sum(avatarFile);
            if (!FileSystemUtils.isEquals(existingHash, newHash)) {
                Log.d(TAG, "Deleting old avatar file: " + avatarFile
                        + ", hash=" + existingHash);
                FileSystemUtils.delete(avatarFile);
                bCreate = true;
            } else {
                Log.d(TAG, "Existing avatar still current: " + avatarFile
                        + ", hash=" + existingHash);
                bCreate = false;
            }
        } else {
            Log.d(TAG, "No existing avatar: " + avatarFile);
            bCreate = true;
        }

        if (bCreate) {
            try (OutputStream fos = IOProviderFactory
                    .getOutputStream(avatarFile)) {
                Log.d(TAG, "Creating avatar file: " + avatarFile + ", hash="
                        + newHash);
                fos.write(getAvatarBytes());
                fos.flush();
            } catch (IOException e) {
                Log.w(TAG, "Failed to create avatar file", e);
            }
        }

        if (!FileSystemUtils.isFile(avatarFile)) {
            Log.w(TAG,
                    "Failed to create avatar for: "
                            + avatarFile.getAbsolutePath());
            return null;
        }

        return avatarFile;
    }

    /**
     * Display the avatar in the ATAK image viewer
     *
     * @param avatar the avatar feature to display
     * @param item the point map item to associate
     * @param alternateTag the alternativeTag in case it cannot be shown.
     */
    public static void openAvatar(AvatarFeature avatar, PointMapItem item,
            String alternateTag) {
        //TODO background thread?
        if (avatar == null
                || (item == null && FileSystemUtils.isEmpty(alternateTag))) {
            Log.w(TAG, "Cannot display invalid avatar");
            return;
        }

        String filename;
        if (item != null) {
            filename = item.getUID();
        } else {
            filename = alternateTag;
        }
        filename += "_avatar.png";

        File file = avatar.saveFile(filename);

        // view image and zoom map
        if (!FileSystemUtils.isFile(file)) {
            Log.w(TAG, "Failed to view larger image for: " + filename);
            Toast.makeText(MapView.getMapView().getContext(),
                    "Failed to view image", Toast.LENGTH_LONG).show();
        } else {
            Log.d(TAG, "Viewing larger image for: " + filename);

            if (item != null) {
                Intent zoomIntent = new Intent(
                        "com.atakmap.android.maps.FOCUS");
                zoomIntent.putExtra("uid", item.getUID());
                zoomIntent.putExtra("useTightZoom", true);
                AtakBroadcast.getInstance().sendBroadcast(zoomIntent);
            }

            //now display image
            Intent intent = new Intent();
            intent.setAction("com.atakmap.maps.images.DISPLAY");
            intent.putExtra("imageURI", Uri.fromFile(file).toString());
            AtakBroadcast.getInstance().sendBroadcast(intent);
        }
    }

}

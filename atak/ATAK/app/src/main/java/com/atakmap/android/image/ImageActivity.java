
package com.atakmap.android.image;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;

import com.atakmap.android.hashtags.StickyHashtags;
import com.atakmap.android.hashtags.attachments.AttachmentContent;
import com.atakmap.android.ipc.AtakBroadcast.DocumentedIntentFilter;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.widget.Toast;

import com.atakmap.android.util.FileProviderHelper;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.tools.menu.ActionBroadcastData;
import com.atakmap.android.tools.menu.ActionBroadcastExtraStringData;
import com.atakmap.app.ATAKActivity;
import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;
import com.atakmap.annotations.DeprecatedApi;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;
import java.io.InputStream;
import java.io.OutputStream;

public class ImageActivity {

    public static final String TAG = "ImageActivity";

    private static final int CAMERA_REQUEST = 1999;
    private final MapView _mapView;
    private final Context _context;
    private final String _uid;
    private final File _tmpFile;
    private final File _output;
    private final ActionBroadcastData _broadcast;
    private final boolean _multiCapture;
    private boolean useGeoTakCam = false;

    /**
     * @deprecated #see ImageActivity(MapView mapView, File outImage, String uid,
     *             ActionBroadcastData broadcast, boolean multiCapture)
     */
    @Deprecated
    @DeprecatedApi(since = "4.6", forRemoval = true, removeAt = "4.9")
    public ImageActivity(final Context context,
            final String uid,
            final ActionBroadcastData broadcast,
            final String output,
            final MapView mapView,
            final boolean noDetails) {
        _context = context;
        _uid = uid;
        _broadcast = broadcast;
        _output = new File(output);
        _tmpFile = createTempFile(_context, _output);
        _mapView = mapView;
        _multiCapture = false;
    }

    /**
     * Construct an activity facade to handle a quick pic request
     * @param mapView the map view
     * @param outImage the image output or the directory if it is multicapture
     * @param uid the uid for the map item
     * @param broadcast the details to broadcast
     * @param multiCapture if this is in support of multicapture
     */
    public ImageActivity(MapView mapView, File outImage, String uid,
            ActionBroadcastData broadcast, boolean multiCapture) {
        _mapView = mapView;
        _context = mapView.getContext();
        _output = outImage;
        _tmpFile = createTempFile(_context, outImage);
        _uid = uid;
        _broadcast = broadcast;
        _multiCapture = multiCapture;
    }

    private static File createTempFile(Context context, File file) {
        File cacheDir = context.getExternalCacheDir();
        File result = null;
        try {
            result = File.createTempFile("ImageActivity", file.getName(),
                    cacheDir);
            result.deleteOnExit();
        } catch (IOException e) {
            Log.w(TAG,
                    "Unable to create temporary file for camera to save image to",
                    e);
        }
        return result;
    }

    final BroadcastReceiver activityResultReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Bundle extras = intent.getExtras();
            if (extras != null) {
                final int requestCode = extras.getInt("requestCode");
                final int resultCode = extras.getInt("resultCode");
                final Intent data = extras.getParcelable("data");
                onActivityResult(requestCode, resultCode, data);
            }
        }
    };

    public void useTakGeoCam(final boolean useGeoTakCam) {
        this.useGeoTakCam = useGeoTakCam;
    }

    public void start() {
        DocumentedIntentFilter f = new DocumentedIntentFilter(
                ATAKActivity.ACTIVITY_FINISHED,
                "Fired when ATAK is ready to process a captured image");
        AtakBroadcast.getInstance().registerReceiver(activityResultReceiver, f);
        Intent i;
        Uri uri = FileProviderHelper.fromFile(_context, _tmpFile);

        if (useGeoTakCam) {
            i = new Intent("com.atakmap.android.Image.IMAGE_CAPTURE");
            i.setComponent(new ComponentName("com.partech.geocamera",
                    "com.partech.geocamera.MainActivity"));
            FileProviderHelper.setReadAccess(i);
            _context.grantUriPermission("com.partech.geocamera", uri,
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        } else {
            i = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        }

        i.putExtra(MediaStore.EXTRA_OUTPUT, uri);
        if (_multiCapture)
            i.putExtra("multiCapture", true);
        try {
            ((Activity) _context).startActivityForResult(i, CAMERA_REQUEST);
        } catch (Exception e) {
            Log.w(TAG, "Failed to ACTION_IMAGE_CAPTURE", e);
            Toast.makeText(_context, R.string.image_text2,
                    Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Reads data from input file using plain File I/O and writes it to output file using
     * the IOProviderFactory. Moves by copying data and then deleting input file.
     *
     * @param input The File to read with plain File I/O
     * @param output The File to write with the IOProviderFactory
     */
    private static void moveWithProvider(File input, File output) {
        try (InputStream is = new FileInputStream(input);
                OutputStream os = IOProviderFactory.getOutputStream(output)) {
            FileSystemUtils.copy(is, os);
        } catch (IOException e) {
            Log.w(TAG, String.format(
                    "Unable to copy image from %s with plain File I/O -- to %s with IOProviderFactory",
                    input.getAbsolutePath(), output.getAbsolutePath()), e);
        } finally {

            if (!input.delete())
                Log.d(TAG, "unable to delete the file: " + input);
        }
    }

    /**
     * Called when the Android Camera Activity returns
     */
    protected void onActivityResult(int requestCode, int resultCode,
            Intent data) {
        AtakBroadcast.getInstance().unregisterReceiver(activityResultReceiver);

        Log.d(TAG, "Got Activity Result: "
                + (resultCode == Activity.RESULT_OK ? "OK" : "ERROR"));
        if (requestCode == CAMERA_REQUEST
                && resultCode == Activity.RESULT_OK) {
            if (_multiCapture && data != null && data.hasExtra("pathList")) {
                int imgCount = 0;
                File file = null;
                String[] pathList = data.getStringArrayExtra("pathList");
                if (pathList != null) {
                    File parentDir = _output.getParentFile();
                    for (String path : pathList) {
                        File tmpFile = new File(path);
                        if (tmpFile.exists()) {
                            File destFile = new File(parentDir,
                                    tmpFile.getName());

                            moveWithProvider(tmpFile, destFile);

                            if (IOProviderFactory.exists(destFile)) {
                                file = destFile;
                                insertExifLocationData(destFile);
                                imgCount++;
                            }
                        }
                    }
                }
                broadcastIntentToListeners(imgCount == 1
                        ? file.getAbsolutePath()
                        : _output.getParent(), _uid);
            } else if (_tmpFile != null && _tmpFile.isFile()
                    && _tmpFile.canRead()) {
                // image is already saved in _tmpFile, copy image to _output
                // and broadcast result to any listeners!

                moveWithProvider(_tmpFile, _output);

                insertExifLocationData(_output);
                broadcastIntentToListeners(_output.getAbsolutePath(), _uid);
            } else if (data != null
                    && data.getExtras() != null
                    && data.getExtras().get("data") != null
                    && data.getExtras().get("data") instanceof Bitmap) {
                Bitmap photo = (Bitmap) data.getExtras().get("data");
                // save AND broadcast the result
                saveImageFile(bitmapToBytes(photo));
                photo.recycle();
            } else {
                Log.w(TAG,
                        "Could not interpret camera result. Extras: "
                                + (data == null || data.getExtras() == null
                                        ? "[NULL]"
                                        : data
                                                .getExtras().toString()));
            }
        } else {
            broadcastIntentToListeners(null, _uid);
            Log.w(TAG, "Invalid camera result or request code: " + resultCode
                    + ", " + requestCode);
        }
        Intent intent = new Intent(
                "com.atakmap.android.images.CAMERA_CLOSED");
        intent.putExtra("uid", _uid);
        AtakBroadcast.getInstance().sendBroadcast(intent);
    }

    private void insertExifLocationData(File f) {
        if (!FileSystemUtils.isFile(f))
            return;

        // Fix location metadata
        ExifHelper.fixImage(_mapView, f.getPath());

        // Apply sticky hashtags to new image
        List<String> tags = StickyHashtags.getInstance().getTags();
        if (FileSystemUtils.isEmpty(tags))
            return;

        AttachmentContent content = new AttachmentContent(_mapView, f);
        content.setHashtags(tags);
    }

    private static byte[] bitmapToBytes(final Bitmap bmap) {
        ByteArrayOutputStream strm = new ByteArrayOutputStream();
        bmap.compress(Bitmap.CompressFormat.JPEG, 100, strm);
        return strm.toByteArray();
    }

    public void saveImageFile(byte[] data) {
        try (OutputStream outStream = IOProviderFactory
                .getOutputStream(_output)) {
            outStream.write(data);

            insertExifLocationData(_output);
            broadcastIntentToListeners(_output.getAbsolutePath(), _uid);
        } catch (IOException e) {
            Log.e(TAG, "error: ", e);
        }
    }

    private void broadcastIntentToListeners(final String path,
            final String uid) {
        Intent intent = null;
        if (_broadcast != null && _broadcast.isValid()) {
            intent = new Intent(_broadcast.getAction());
            if (!FileSystemUtils.isEmpty(path)) {
                intent.putExtra("path", path);
            }
            intent.putExtra("uid", uid);
            if (_broadcast.hasExtras()) {
                for (ActionBroadcastExtraStringData extra : _broadcast
                        .getExtras()) {
                    intent.putExtra(extra.getKey(), extra.getValue());
                }
            }
            AtakBroadcast.getInstance().sendBroadcast(intent);
            Log.d(TAG, "Sent " + _broadcast.getAction() + " Intent: " + path);
            return;
        }

        // by default, display the image
        if (!FileSystemUtils.isEmpty(path)) {
            intent = new Intent("com.atakmap.android.images.NEW_IMAGE");
            intent.putExtra("path", path);
            intent.putExtra("uid", uid);
            AtakBroadcast.getInstance().sendBroadcast(intent);
        }
    }

}

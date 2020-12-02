
package com.atakmap.android.image;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.atakmap.android.hashtags.StickyHashtags;
import com.atakmap.android.hashtags.attachments.AttachmentContent;
import com.atakmap.android.ipc.AtakBroadcast.DocumentedIntentFilter;
import android.graphics.Bitmap;
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
import com.atakmap.coremap.io.FileIOProviderFactory;
import com.atakmap.coremap.log.Log;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

public class ImageActivity {

    public static final String TAG = "ImageActivity";

    private static final int CAMERA_REQUEST = 1999;
    private final MapView _mapView;
    private final Context _context;
    private final String _uid;
    private final File _output;
    private final ActionBroadcastData _broadcast;
    private final boolean _multiCapture;

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
        _mapView = mapView;
        _multiCapture = false;
    }

    public ImageActivity(MapView mapView, File outImage, String uid,
            ActionBroadcastData broadcast, boolean multiCapture) {
        _mapView = mapView;
        _context = mapView.getContext();
        _output = outImage;
        _uid = uid;
        _broadcast = broadcast;
        _multiCapture = multiCapture;
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

    public void start() {
        DocumentedIntentFilter f = new DocumentedIntentFilter(
                ATAKActivity.ACTIVITY_FINISHED,
                "Fired when ATAK is ready to process a captured image");
        AtakBroadcast.getInstance().registerReceiver(activityResultReceiver, f);
        Intent i = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        i.putExtra(MediaStore.EXTRA_OUTPUT,
                FileProviderHelper.fromFile(_context, _output));
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
                    for (String path : pathList) {
                        File f = new File(path);
                        if (FileIOProviderFactory.exists(f)) {
                            file = f;
                            insertExifLocationData(f);
                            imgCount++;
                        }
                    }
                }
                broadcastIntentToListeners(imgCount == 1
                        ? file.getAbsolutePath()
                        : _output.getParent(), _uid);
            } else if (_output != null && _output.isFile()
                    && _output.canRead()) {
                // image is already saved in _output,
                // just broadcast the result to any listeners!
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
        FileOutputStream outStream = null;
        try {
            outStream = FileIOProviderFactory.getOutputStream(_output);
            try {
                outStream.write(data);
            } finally {
                outStream.close();
            }

            insertExifLocationData(_output);
            broadcastIntentToListeners(_output.getAbsolutePath(), _uid);
        } catch (FileNotFoundException e) {
            Log.e(TAG, "error: ", e);
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


package com.atakmap.android.maps;

import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;

import com.atakmap.android.location.LocationMapComponent;
import com.atakmap.android.metrics.activity.MetricActivity;
import com.atakmap.coremap.io.FileIOProviderFactory;
import com.atakmap.net.AtakAuthenticationDatabase;
import com.atakmap.net.AtakCertificateDatabase;

import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.android.image.ImageDropDownReceiver;
import com.atakmap.android.statesaver.StateSaver;
import com.atakmap.app.R;
import com.atakmap.coremap.log.Log;
import com.atakmap.database.CursorIface;

import org.xml.sax.InputSource;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringReader;
import java.util.LinkedList;
import java.util.List;

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

/**
 * Image Accept Activity is used to receive the intent from another application that allows
 * for file sharing.  The on receive does make use of Parcelable extraction from the intent, 
 * but the type is cast as a Uri immediately and it is unclear how to completely protect 
 * against an external * entity passing in something other than a Uri.   Continue to evaluate 
 * each release and see if Android is able to prevent this type of attack at a lower level.
 */
public class ImageAcceptActivity extends MetricActivity {

    public static final String TAG = "ImageAcceptActivity";

    private ListView listView = null;
    private ArrayAdapter<AtakImageRecipient> adapter = null;
    private ImageView imgView = null;
    private Uri imageUri = null;

    @Override
    public void onCreate(Bundle savedInstanceState) {

        // important first step, initialize the native loader
        // otherwise as classes are loader, the static blocks
        // are run which might load native libraries.
        // this needs to be called in case ATAK is not running already.
        com.atakmap.coremap.loader.NativeLoader.init(this);
        String deviceId = LocationMapComponent
                ._determineBestDeviceUID(ImageAcceptActivity.this);
        AtakCertificateDatabase.setDeviceId(deviceId);
        AtakAuthenticationDatabase.setDeviceId(deviceId);

        AtakAuthenticationDatabase.initialize(ImageAcceptActivity.this);
        AtakCertificateDatabase.initialize(ImageAcceptActivity.this);
        //

        super.onCreate(savedInstanceState);
        setContentView(R.layout.import_image);

        imgView = findViewById(R.id.imgpreview);
        imgView.setAdjustViewBounds(true);
        imgView.setMaxHeight(180);
        imgView.setMaxWidth(180);

        Intent intent = getIntent();
        String action = intent == null ? null : intent.getAction();
        String type = intent == null ? null : intent.getType();

        if (Intent.ACTION_SEND.equals(action)) {
            if (type != null && type.startsWith("image/")) {
                imageUri = intent
                        .getParcelableExtra(Intent.EXTRA_STREAM);
                if (imageUri != null) {
                    Log.d(TAG, "handle Image: " + imageUri + " "
                            + imageUri.getClass());
                    // imgView.setImageURI(imageUri);
                } else {
                    Log.w(TAG, "NULL Image URI.");
                }
            } else {
                Log.w(TAG, "Unexpected Type: " + type);
            }
        } else {
            Log.w(TAG, "Unexpected Action: " + action);
        }

        try {
            listView = findViewById(R.id.uidlist);
            final List<AtakImageRecipient> listOfPotentialRecipients = getImageRecipients();
            adapter = new ArrayAdapter<>(
                    this, /* context */
                    android.R.layout.simple_list_item_1,
                    listOfPotentialRecipients);
            listView.setAdapter(adapter);
            listView.setOnItemClickListener(new OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> parent, View view,
                        int position, long id) {
                    AtakImageRecipient recip = listOfPotentialRecipients
                            .get(position);
                    if (recip != null) {
                        String uid = recip.uid;
                        if (uid != null) {
                            final File fileToWrite = ImageDropDownReceiver
                                    .createAndGetPathToImageFromUID(uid, "jpg");
                            if (fileToWrite != null && imageUri != null) {
                                new Thread(new Runnable() {
                                    @Override
                                    public void run() {
                                        try {
                                            Log.d(TAG, "Copying URI " + imageUri
                                                    + " to " + fileToWrite);
                                            copyFile(imageUri, fileToWrite);
                                            Log.d(TAG, "DONE Copying file "
                                                    + imageUri.getPath()
                                                    + " to " + fileToWrite);
                                        } catch (IOException e) {
                                            Log.w(TAG,
                                                    "Error copying file from "
                                                            + imageUri.getPath()
                                                            + " to "
                                                            + fileToWrite,
                                                    e);
                                        }
                                    }
                                }, "ImageWriteThread").start();
                                finish();
                            } else {
                                Log.w(TAG, "NULL source or destination file: "
                                        + fileToWrite + ", "
                                        + imageUri);
                            }
                        } else {
                            Log.w(TAG,
                                    "Couldn't find recipient's UID: " + recip);
                        }
                    } else {
                        Log.w(TAG,
                                "Couldn't find recipient number: " + position);
                    }
                }
            });

        } catch (Exception e) {

            Log.w(TAG, "error", e);
        }
    }

    private File uriToFile(Uri uri) {
        String selectedImagePath = null;
        String filemanagerPath = uri.getPath();

        String[] projection = {
                MediaStore.Images.Media.DATA
        };
        Cursor cursor = getContentResolver().query(uri, projection, null, null,
                null);

        if (cursor != null) {

            try {
                int column_index = cursor
                        .getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
                cursor.moveToFirst();
                selectedImagePath = cursor.getString(column_index);
            } catch (IllegalArgumentException iae) {
                return null;
            } finally {
                try {
                    cursor.close();
                } catch (Exception e) {
                }
            }
        }

        if (selectedImagePath != null) {
            return new File(FileSystemUtils
                    .sanitizeWithSpacesAndSlashes(selectedImagePath));
        } else if (filemanagerPath != null) {
            try {
                return new File(FileSystemUtils.validityScan(filemanagerPath));
            } catch (IOException ioe) {
                return null;
            }
        }
        return null;
    }

    private void copyFile(Uri src, File dst) throws IOException {
        File srcFile = uriToFile(src);
        if (srcFile == null)
            throw new IOException("Could not get File from URI: " + src);

        InputStream in = null;
        OutputStream out = null;

        try {
            // Transfer bytes from in to out
            in = FileIOProviderFactory.getInputStream(srcFile);
            out = FileIOProviderFactory.getOutputStream(dst);
            FileSystemUtils.copy(in, out);
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException ignored) {
                }
            }
            if (out != null) {
                try {
                    out.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    static class AtakImageRecipient {
        public final String uid;
        public final String callsign;
        public final String cotType;

        AtakImageRecipient(String uid, String callsign, String cotType) {
            this.uid = uid;
            this.callsign = callsign;
            this.cotType = cotType;
        }

        public String toString() {
            return callsign == null ? uid : callsign;
        }
    }

    private boolean isValidImageRecipient(AtakImageRecipient potentialRecip) {
        return (potentialRecip != null)
                &&
                (potentialRecip.cotType == null || !potentialRecip.cotType
                        .startsWith("u"));
    }

    private List<AtakImageRecipient> getImageRecipients() {
        List<AtakImageRecipient> ret = new LinkedList<>();
        CursorIface result = null;
        try {
            result = StateSaver.getInstance().getStateSaverDatabase()
                    .query("SELECT " +
                            StateSaver.COLUMN_TYPE + ", " +
                            StateSaver.COLUMN_UID + ", " +
                            StateSaver.COLUMN_EVENT + " FROM " +
                            StateSaver.TABLE_COTEVENTS, null);

            while (result.moveToNext()) {
                AtakImageRecipient recip = getRecipientFromCotEvent(result);
                if (isValidImageRecipient(recip)) {
                    ret.add(recip);
                }
            }
        } finally {
            if (result != null)
                result.close();
        }

        return ret;
    }

    private static AtakImageRecipient getRecipientFromCotEvent(
            CursorIface eventCursor) {
        String uid = eventCursor.getString(eventCursor
                .getColumnIndex(StateSaver.COLUMN_UID));
        if (uid == null)
            return null;

        String cotType = eventCursor.getString(eventCursor
                .getColumnIndex(StateSaver.COLUMN_TYPE));

        XPath xpath = XPathFactory.newInstance().newXPath();
        InputSource inputSource = new InputSource(new StringReader(
                eventCursor.getString(eventCursor
                        .getColumnIndex(StateSaver.COLUMN_EVENT))));
        String callsign = null;
        try {
            callsign = xpath.evaluate("/event/detail/contact/@callsign",
                    inputSource);
        } catch (XPathExpressionException ignored) {
        }

        return new AtakImageRecipient(uid, callsign, cotType);
    }

}

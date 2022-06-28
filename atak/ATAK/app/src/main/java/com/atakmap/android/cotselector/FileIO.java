
package com.atakmap.android.cotselector;

import android.app.Activity;
import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapFactory.Options;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.os.Environment;
import android.util.DisplayMetrics;

import com.atakmap.android.user.icon.Icon2525cPallet;
import com.atakmap.coremap.log.Log;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;

class FileIO {

    public static final String TAG = "FileIO";

    private final Activity context;

    private CustomListView clv = null;

    FileIO(final Context context) {
        this.context = (Activity) context;
    }

    void setCustomListView(final CustomListView clv) {
        this.clv = clv;
    }

    BitmapDrawable loadIcon(String fn) {
        BitmapDrawable ic = null;
        String filename = Icon2525cPallet.ASSET_PATH + fn;
        if (_checkAsset(filename)) {
            InputStream bitis = null;
            try {
                Options opts = new BitmapFactory.Options();

                DisplayMetrics dm = new DisplayMetrics();
                context.getWindowManager().getDefaultDisplay().getMetrics(dm);

                int dpiClassification = dm.densityDpi;

                opts.inDensity = DisplayMetrics.DENSITY_MEDIUM;

                opts.inTargetDensity = dpiClassification;
                opts.inScaled = true;

                bitis = context.getAssets().open(filename);
                Bitmap bm = BitmapFactory.decodeStream(bitis, new Rect(), opts);
                ic = new BitmapDrawable(context.getResources(), bm);
            } catch (IOException e) {
                Log.e(TAG, "error: ", e);
            } finally {
                if (bitis != null) {
                    try {
                        bitis.close();
                    } catch (IOException ignored) {
                    }
                }
            }

        }
        return ic;
    }

    private boolean _checkAsset(final String pathName) {
        boolean found = false;
        try {
            AssetFileDescriptor fd = context.getAssets().openFd(pathName);
            fd.close();
            found = true;
        } catch (IOException ignored) {
            // nothing
        }
        return found;
    }

    void readAndParse2525DataFile() {

        String state = Environment.getExternalStorageState();

        // handleExternalStorageState(mExternalStorageAvailable, mExternalStorageWriteable);
        if (clv != null && (state.equals(Environment.MEDIA_MOUNTED)
                || state.equals(Environment.MEDIA_MOUNTED_READ_ONLY))) {
            // read and parse the data file. state machine

            InputStream fileis = null;
            DataInputStream dis;
            BufferedReader br;
            try {
                fileis = context.getResources().getAssets().open("symbols.dat");// context.getResources().openRawResource(R.raw.symbols);
                dis = new DataInputStream(fileis);
                br = new BufferedReader(new InputStreamReader(dis));

                String line;
                String pre = null;
                String req = null;
                ArrayList<String> items = new ArrayList<>(5);

                while ((line = br.readLine()) != null) {
                    if (line.startsWith("#REQ:")) {
                        req = line;
                    } else if (line.startsWith("#PRE:")) {
                        pre = line;
                    } else if (line.equals("")) {
                        clv.add2525ToList(req, pre,
                                new ArrayList<>(items));
                        req = null;
                        pre = null;
                        items.clear();
                    } else {
                        items.add(line);
                    }
                }

            } catch (IOException e) {
                Log.e(TAG, "error: ", e);
            } finally {
                try {
                    if (fileis != null)
                        fileis.close();
                } catch (IOException e) {
                    Log.e(TAG, "error: ", e);
                }

            }
        }
    }

}

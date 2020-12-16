
package com.atakmap.android.icons;

import android.content.res.AssetManager;

import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;
import com.atakmap.android.maps.MapView;
import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

public class CotDescriptions {
    public static final String TAG = "CotDescriptions";

    private static CotDescriptions ref = null;

    private static final Map<String, String> descriptions_ = new HashMap<>();

    private CotDescriptions(final AssetManager assets) {

        String[] values;

        InputStream fileis = null;
        DataInputStream dis;
        BufferedReader br;
        try {
            fileis = assets.open("symbols.dat");
            dis = new DataInputStream(fileis);
            br = new BufferedReader(new InputStreamReader(dis,
                    FileSystemUtils.UTF8_CHARSET));

            String line;

            while ((line = br.readLine()) != null) {
                if (line.startsWith("#REQ:")) {

                } else if (line.startsWith("#PRE:")) {

                } else if (line.equals("")) {

                } else {
                    values = line.split("/");
                    String key = values[1];
                    descriptions_.put(key, values[0]);
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

    public static String GetDescription(AssetManager assets, String cotInput) {
        if (ref == null) {
            ref = new CotDescriptions(assets);
        }

        String tmp = descriptions_.get(cotInput);

        if (tmp == null) {
            return MapView.getMapView().getContext()
                    .getString(com.atakmap.app.R.string.not_recognized);
        }

        return tmp;
    }

}

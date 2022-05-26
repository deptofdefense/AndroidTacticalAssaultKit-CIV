
package com.atakmap.android.importfiles.sort;

import android.content.Context;
import android.content.Intent;
import android.util.Pair;

import com.atakmap.android.icons.IconsMapAdapter;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.app.R;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.locale.LocaleUtil;
import com.atakmap.coremap.log.Log;
import com.atakmap.util.zip.ZipEntry;
import com.atakmap.util.zip.ZipFile;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Enumeration;
import java.util.List;
import java.util.Set;

/**
 * Does not move the input file, rather simply imports into DB, if not already loaded
 * Must contain at least one image, and a valid iconset.xml in root of zip file
 * 
 * 
 */
public class ImportUserIconSetSort extends ImportInPlaceResolver {

    private static final String TAG = "ImportUserIconSetSort";
    public static final String ICONSET_XML = "iconset.xml";
    private final static String ICONSET_XML_MATCH = "<iconset";

    private static final String CONTENT_TYPE = "User Icon Set";
    private static final String MIME_TYPE = "application/zip";

    public ImportUserIconSetSort(Context context, boolean validateExt) {
        super(".zip", null, validateExt, false, true, context
                .getString(R.string.user_icon_set),
                context.getDrawable(R.drawable.cot_icon_sugp));
    }

    @Override
    public boolean match(File file) {
        if (!super.match(file))
            return false;

        // it is a .zip, now lets see if it contains an iconset.xml
        return HasIconset(file, true);
    }

    /**
     * Search for a zip entry matching iconset.xml and at least one .png
     * 
     * @param file
     * @param requireXml
     * @return
     */
    public static boolean HasIconset(File file, boolean requireXml) {

        if (file == null || !IOProviderFactory.exists(file)) {
            Log.d(TAG,
                    "ZIP does not exist: "
                            + (file == null ? "null" : file.getAbsolutePath()));
            return false;
        }

        ZipFile zip = null;
        try {
            zip = new ZipFile(file);

            boolean bIconsetXml = false, bHasImage = false;
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry ze = entries.nextElement();
                if (ze.getName().toLowerCase(LocaleUtil.getCurrent())
                        .endsWith(ICONSET_XML)) {
                    if (isIconsetXml(zip.getInputStream(ze))) {
                        bIconsetXml = true;
                    } else {
                        Log.w(TAG,
                                "Found invalid archived Zip file: "
                                        + ze.getName());
                    }
                } else if (IconsMapAdapter.IconFilenameFilter.accept(null,
                        ze.getName())) {
                    bHasImage = true;
                }

                if (bIconsetXml && bHasImage) {
                    // found what we needed, quit looping
                    break;
                }
            }

            if (!bHasImage) {
                Log.w(TAG,
                        "Invalid iconset (no image): "
                                + file.getAbsolutePath());
                return false;
            }

            if (requireXml && !bIconsetXml) {
                Log.w(TAG,
                        "Invalid iconset (XML required): "
                                + file.getAbsolutePath());
                return false;
            }

            Log.d(TAG, "Matched iconset: " + file.getAbsolutePath());
            return true;
        } catch (Exception e) {
            Log.d(TAG,
                    "Failed to find iconset content in: "
                            + file.getAbsolutePath(),
                    e);
        } finally {
            if (zip != null) {
                try {
                    zip.close();
                } catch (Exception e) {
                    Log.e(TAG,
                            "Failed closing iconset: " + file.getAbsolutePath(),
                            e);
                }
            }
        }

        return false;
    }

    private static boolean isIconsetXml(InputStream input) {
        try {
            // read first few hundred bytes and search for known iconset strings
            char[] buffer = new char[1024];
            BufferedReader reader = new BufferedReader(new InputStreamReader(
                    input));
            int numRead = reader.read(buffer);
            reader.close();

            if (numRead < 1) {
                Log.d(TAG, "Failed to read iconset stream");
                return false;
            }

            String content = String.valueOf(buffer, 0, numRead);
            boolean match = content.contains(ICONSET_XML_MATCH);
            if (!match) {
                Log.d(TAG, "Failed to match iconset content");
            }

            return match;
        } catch (Exception e) {
            Log.d(TAG, "Failed to match iconset", e);
            return false;
        }
    }

    @Override
    protected void onFileSorted(File src, File unused, Set<SortFlags> flags) {
        Intent loadIntent = new Intent();
        loadIntent.setAction(IconsMapAdapter.ADD_ICONSET);
        loadIntent.putExtra("filepath", src.getAbsolutePath());
        AtakBroadcast.getInstance().sendBroadcast(loadIntent);
    }

    @Override
    public Pair<String, String> getContentMIME() {
        return new Pair<>(CONTENT_TYPE, MIME_TYPE);
    }

    @Override
    public void filterFoundResolvers(final List<ImportResolver> importResolvers,
            File file) {
        // Remove data package sorters
        for (int i = 0; i < importResolvers.size(); i++) {
            ImportResolver ir = importResolvers.get(i);
            if (ir instanceof ImportMissionPackageSort)
                importResolvers.remove(i--);
        }
    }
}

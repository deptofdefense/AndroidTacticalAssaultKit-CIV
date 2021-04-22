
package com.atakmap.android.importfiles.sort;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.preference.PreferenceManager;
import android.util.Pair;

import com.atakmap.android.maps.MapView;
import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProvider;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import com.atakmap.coremap.locale.LocaleUtil;
import java.util.Set;

/**
 * Matches contact info in a CSV, based on configured callsign
 * 
 * 
 */
public class ImportAlternateContactSort extends ImportResolver {

    private static final String TAG = "ImportAlternateContactSort";

    private final static String CONTACT_MATCH = "::ALTERNATE CONTACT v2";
    private static final String COMMENT = "::";
    private static final String SPLIT = ",";
    private static final List<String> IGNORE;

    final char[] _charBuffer; // reuse for performance
    private final Context _context;

    static {
        IGNORE = new ArrayList<>();
        IGNORE.add("NA");
        IGNORE.add("N/A");
    }

    public ImportAlternateContactSort(final Context context,
            final boolean validateExt,
            boolean copyFile) {
        super(".csv", "", validateExt, copyFile);
        _context = context;
        _charBuffer = new char[FileSystemUtils.CHARBUFFERSIZE];
    }

    @Override
    public boolean match(File file) {
        if (!super.match(file))
            return false;

        try (InputStream is = IOProviderFactory.getInputStream(file)) {
            return isContact(is, _charBuffer);
        } catch (IOException e) {
            Log.e(TAG,
                    "Error checking contact info: " + file.getAbsolutePath(),
                    e);
        }

        return false;
    }

    private static boolean isContact(InputStream stream, char[] buffer)
            throws IOException {

        BufferedReader reader = null;
        try {
            // read first few hundred bytes and search for known strings
            reader = new BufferedReader(new InputStreamReader(
                    stream));
            int numRead = reader.read(buffer);
            if (numRead < 1) {
                Log.d(TAG, "Failed to read .csv stream");
                return false;
            }

            String content = String.valueOf(buffer, 0, numRead);
            return isContact(content);
        } catch (Exception e) {
            Log.d(TAG, "Failed to match .csv", e);
            return false;
        } finally {
            if (reader != null)
                reader.close();
        }
    }

    private static boolean isContact(String content) {
        if (FileSystemUtils.isEmpty(content)) {
            Log.w(TAG, "Unable to match empty content");
            return false;
        }

        boolean match = content.contains(CONTACT_MATCH);
        if (!match) {
            Log.d(TAG, "Failed to match content from .csv: ");
        }

        return match;
    }

    @Override
    public boolean beginImport(File file) {
        return beginImport(file, Collections.<SortFlags> emptySet());
    }

    /**
     * Parse CSV to find contact info
     * 
     * @param file the file to import with the contact information
     * @return true if the import was successful
     */
    @Override
    public boolean beginImport(final File file, final Set<SortFlags> flags) {
        try {
            importContact(file);
        } catch (IOException e) {
            Log.w(TAG,
                    "Failed to parse contact info: " + file.getAbsolutePath(),
                    e);
        }

        // remove the .csv file from source location so it won't be reimported next time ATAK starts
        File atakdata = new File(_context.getCacheDir(),
                FileSystemUtils.ATAKDATA);
        if (file.getAbsolutePath().startsWith(atakdata.getAbsolutePath())
                && IOProviderFactory.delete(file, IOProvider.SECURE_DELETE))
            Log.d(TAG,
                    "Deleted imported contact info: " + file.getAbsolutePath());

        this.onFileSorted(file, file, flags);
        return true;
    }

    private void importContact(File file) throws IOException {
        String myCallsign = MapView.getMapView().getDeviceCallsign()
                .toLowerCase(LocaleUtil.getCurrent());
        SharedPreferences prefs = PreferenceManager
                .getDefaultSharedPreferences(MapView.getMapView().getContext());

        try (Reader r = IOProviderFactory.getFileReader(file);
                BufferedReader br = new BufferedReader(r)) {
            String line;
            String[] parse;
            while ((line = br.readLine()) != null) {
                if (line.startsWith(COMMENT)) {
                    Log.d(TAG, "Skipping comment: " + line);
                    continue;
                }

                parse = line.split(SPLIT);
                if (parse.length != 5) {
                    Log.w(TAG, "Invalid contact format. Skipping.");
                    continue;
                }

                String callsign = parse[0];
                String includePhone = parse[1];
                String voip = parse[2];
                String email = parse[3];
                String xmpp = parse[4];

                if (FileSystemUtils.isEmpty(callsign)) {
                    Log.w(TAG, "Invalid callsign. Skipping.");
                    continue;
                }

                callsign = callsign.toLowerCase(LocaleUtil.getCurrent());
                if (!FileSystemUtils.isEquals(myCallsign, callsign)) {
                    Log.w(TAG, "Not my callsign. Skipping: " + callsign);
                    continue;
                }

                if (!FileSystemUtils.isEmpty(includePhone)
                        && !IGNORE.contains(includePhone)) {
                    boolean bIncludePhone = Boolean.parseBoolean(includePhone);
                    prefs.edit().putBoolean("saHasPhoneNumber", bIncludePhone)
                            .apply();
                    Log.d(TAG, "Setting includePhone=" + bIncludePhone
                            + ", for callsign: " + myCallsign);
                }

                if (!FileSystemUtils.isEmpty(voip) && !IGNORE.contains(voip)) {
                    //set VoIP number, and use "manual entry"
                    prefs.edit().putString("saSipAddress", voip).apply();
                    prefs.edit()
                            .putString(
                                    "saSipAddressAssignment",
                                    _context.getString(
                                            R.string.voip_assignment_manual_entry))
                            .apply();
                    Log.d(TAG, "Setting sip address=" + voip
                            + ", for callsign: " + myCallsign);
                }

                if (!FileSystemUtils.isEmpty(email)
                        && !IGNORE.contains(email)) {
                    prefs.edit().putString("saEmailAddress", email).apply();
                    Log.d(TAG, "Setting email address=" + email
                            + ", for callsign: " + myCallsign);
                }

                if (!FileSystemUtils.isEmpty(xmpp) && !IGNORE.contains(xmpp)) {
                    prefs.edit().putString("saXmppUsername", xmpp).apply();
                    Log.d(TAG, "Setting xmpp username=" + xmpp
                            + ", for callsign: " + myCallsign);
                }
            }
        }
    }

    @Override
    public File getDestinationPath(File file) {
        return file;
    }

    @Override
    public String getDisplayableName() {
        return _context.getString(R.string.contact_info);
    }

    @Override
    public Drawable getIcon() {
        return _context.getDrawable(R.drawable.ic_menu_contact);
    }

    @Override
    public Pair<String, String> getContentMIME() {
        return new Pair<>("Contact Info", "text/csv");
    }

    @Override
    protected void onFileSorted(File src, File dst, Set<SortFlags> flags) {
        super.onFileSorted(src, dst, flags);
    }
}

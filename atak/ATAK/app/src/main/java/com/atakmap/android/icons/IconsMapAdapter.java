
package com.atakmap.android.icons;

import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.preference.PreferenceManager;

import com.atakmap.android.importfiles.sort.ImportUserIconSetSort;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.Marker;
import com.atakmap.android.user.icon.Icon2525cPallet;
import com.atakmap.android.user.icon.SpotMapPallet;
import com.atakmap.android.user.icon.UserIconPalletFragment;
import com.atakmap.android.util.NotificationUtil;
import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.locale.LocaleUtil;
import com.atakmap.coremap.log.Log;
import com.atakmap.filesystem.HashingUtils;
import com.atakmap.util.zip.ZipEntry;
import com.atakmap.util.zip.ZipFile;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.UUID;

public class IconsMapAdapter extends BroadcastReceiver {

    private static final String TAG = "IconsMapAdapter";

    //intents to cause action
    public static final String ADD_ICONSET = "com.atakmap.android.icons.ADD_ICONSET";
    public static final String REMOVE_ICONSET = "com.atakmap.android.icons.REMOVE_ICONSET";

    //intents after action is complete
    public static final String ICONSET_ADDED = "com.atakmap.android.icons.ICONSET_ADDED";
    public static final String ICONSET_REMOVED = "com.atakmap.android.icons.ICONSET_REMOVED";

    private static final int MAX_ICONS_PER_ICONSET = 5000;

    private Context _context;
    private SharedPreferences _prefs;

    private Icon2525cIconAdapter _2525cIconAdapter;
    private SpotMapIconAdapter _spotMapIconAdapter;
    private UserIconsetIconAdapter _iconsetIconAdapter;

    public IconsMapAdapter(Context context) {
        _context = context;
        _prefs = PreferenceManager.getDefaultSharedPreferences(context);
        _2525cIconAdapter = new Icon2525cIconAdapter(context);
        _spotMapIconAdapter = new SpotMapIconAdapter();
        _iconsetIconAdapter = new UserIconsetIconAdapter(_context);
    }

    /**
     * Keeping the initialization code in here, but will be called by the
     * so it can store a reference to keep from getting garbage collected.
     */
    public static synchronized UserIconDatabase initializeUserIconDB(
            Context context, SharedPreferences prefs) {
        return UserIconDatabase.instance(context);
    }

    /**
     * Adapt marker based on user settings and/or 2525C type, in the following order:
     *  If the marker is the self marker or if annotated with "adapt_marker_icon" with the value
     *  false, then the whole process is skipped.   Otherwise, it is adapted in the following order:
     *  1. First check for force/override default/preferred icon set (local users requires a single iconset)
     *  2. Then check for user specified icon set (remote user set an icon)
     *  3. Then check for default/preferred icon set (local user prefers a default icon set if none specified by remote user)
     *  4. Finally, fallback on legacy 2525C icons (explicitly 2525C pallet, or no better match found)
     * 
     * @param marker the marker to be adapted
     */
    public void adaptMarkerIcon(final Marker marker) {
        if (marker.getType().equals("self"))
            return;

        try {
            if (!marker.getMetaBoolean("adapt_marker_icon", true))
                return;
        } catch (Exception ignored) {
        }

        String preferredCoTMappingUUID = IconManagerView
                .getDefaultCoTMapping(_prefs);
        boolean bForceMapping = IconManagerView.getForceCoTMapping(_prefs);
        final String iconsetPath = marker.getMetaString(UserIcon.IconsetPath,
                "");

        //1. first check for force/override icon set   
        if (bForceMapping) {
            //check 2525C and Spot Map special cases
            if (preferredCoTMappingUUID
                    .equals(Icon2525cPallet.COT_MAPPING_2525C)) {
                //Log.i("adaptMarkerIcon", "1a");
                _2525cIconAdapter.adapt(marker);
            } else if (preferredCoTMappingUUID
                    .equals(SpotMapPallet.COT_MAPPING_SPOTMAP)) {
                //Log.i("adaptMarkerIcon", "1b");
                if (!_spotMapIconAdapter.adapt(marker)) {
                    //Log.d(TAG, "Failed to map icon to preferred: " + preferredCoTMappingUUID); 
                    _2525cIconAdapter.adapt(marker);
                }

            } else if (iconsetPath.startsWith(preferredCoTMappingUUID)) {
                //Log.i("adaptMarkerIcon", "1c");
                if (!_iconsetIconAdapter.adaptIconsetPath(marker)) {
                    //Log.d(TAG, "Failed to map icon to IconsetPath pallet: " + preferredCoTMappingUUID); 
                    _2525cIconAdapter.adapt(marker);
                }

            } else {
                //Log.i("adaptMarkerIcon", "1d");
                if (!_iconsetIconAdapter.adaptPreferredIconset(marker,
                        preferredCoTMappingUUID)) {
                    //Log.d(TAG, "Failed to map icon to preferred Iconset pallet: " + preferredCoTMappingUUID); 
                    _2525cIconAdapter.adapt(marker);
                }

            }
        } else {
            //not forcing mapping based on user setting

            //2. Then check for user specified iconset
            if (iconsetPath.startsWith(Icon2525cPallet.COT_MAPPING_2525C)) {
                //Log.i("adaptMarkerIcon", "2a");
                _2525cIconAdapter.adapt(marker);
                return;
            } else if (iconsetPath
                    .startsWith(SpotMapPallet.COT_MAPPING_SPOTMAP)) {
                //Log.i("adaptMarkerIcon", "2b");
                _spotMapIconAdapter.adapt(marker);
                return;
            } else {
                //check for user iconset                
                if (_iconsetIconAdapter.adaptIconsetPath(marker)) {
                    //Log.i("adaptMarkerIcon", "2c");
                    return;
                }
            }

            //3. Then check for user default/preferred icon set
            //check 2525C and Spot Map special cases
            switch (preferredCoTMappingUUID) {
                case Icon2525cPallet.COT_MAPPING_2525C:
                    if (_2525cIconAdapter.adapt(marker)) {
                        //Log.i("adaptMarkerIcon", "3a");
                        return;
                    }
                    break;
                case SpotMapPallet.COT_MAPPING_SPOTMAP:
                    if (_spotMapIconAdapter.adapt(marker)) {
                        //Log.i("adaptMarkerIcon", "3b");
                        return;
                    }
                    break;
                default:
                    //check for user iconset
                    if (_iconsetIconAdapter.adaptPreferredIconset(marker,
                            preferredCoTMappingUUID)) {
                        //Log.i("adaptMarkerIcon", "3c");
                        return;
                    }
                    break;
            }

            //4. Finally, fallback on legacy 2525C icon mapping
            //Log.i("adaptMarkerIcon", "4");
            _2525cIconAdapter.adapt(marker);
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (ADD_ICONSET.equals(intent.getAction())) {
            String filepath = intent.getStringExtra("filepath");
            if (FileSystemUtils.isEmpty(filepath)) {
                Log.w(TAG, "Unable to import icon set with no filepath");
                return;
            }

            Log.d(TAG, "Adding iconset: " + filepath);
            new AddIconsetTask(filepath, _context).execute();
        } else if (REMOVE_ICONSET.equals(intent.getAction())) {
            String iconsetUid = intent.getStringExtra("iconsetUid");
            if (FileSystemUtils.isEmpty(iconsetUid)) {
                Log.w(TAG, "Unable to remove icon set with no uid");
                return;
            }

            Log.d(TAG, "Removing iconset: " + iconsetUid);
            new RemoveIconsetTask(iconsetUid, _context, _prefs).execute();
        }
    }

    public static final FilenameFilter IconFilenameFilter = new FilenameFilter() {
        @Override
        public boolean accept(File dir, String filename) {
            String fn = filename.toLowerCase(LocaleUtil.getCurrent());
            return fn.endsWith(".jpg")
                    || fn.endsWith(".jpeg")
                    || fn.endsWith(".png")
                    || fn.endsWith(".bmp")
                    || fn.endsWith(".gif");
        }
    };

    private static UserIconSet addIconset(final File file,
            final Context context) {
        UserIconSet iconset;
        long start = android.os.SystemClock.elapsedRealtime();

        if (file == null || !IOProviderFactory.exists(file)) {
            Log.w(TAG,
                    "ZIP does not exist: "
                            + (file == null ? "null" : file.getAbsolutePath()));
            return null;
        }

        if (!ImportUserIconSetSort.HasIconset(file, false)) {
            Log.w(TAG,
                    "ZIP does not contain iconset: " + file.getAbsolutePath());
            return null;
        }

        Log.d(TAG, "Loading iconset: " + file.getAbsolutePath());
        ZipFile zip = null;
        UserIconDatabase userIconDB = UserIconDatabase.instance(context);
        try {
            zip = new ZipFile(file);
            //get iconset.xml
            iconset = loadIconset(zip,
                    zip.getEntry(ImportUserIconSetSort.ICONSET_XML));
            if (iconset == null || !iconset.isValid()) {
                String uuid = HashingUtils.sha256sum(file);
                if (FileSystemUtils.isEmpty(uuid)) {
                    Log.w(TAG,
                            "Failed to compute iconset zip hash for use as UUID, generating random UUID");
                    uuid = UUID.randomUUID().toString();
                }

                Log.i(TAG,
                        "Generating iconset.xml for missing or invalid iconset: "
                                + file.getAbsolutePath() +
                                ", new UUID=" + uuid);
                iconset = new UserIconSet(
                        FileSystemUtils.stripExtension(file.getName()), uuid);
            }

            //if iconset already exists, remove old copy
            UserIconSet existing = userIconDB.getIconSet(iconset.getUid(),
                    false, false);
            if (existing != null) {
                Log.d(TAG,
                        "Removing old version of iconset: "
                                + existing);
                userIconDB.removeIconSet(existing);
            }

            //set default group if XML specifies it...
            if (iconset.hasSelectedGroup()) {
                Log.d(TAG,
                        "Setting initial icon group: "
                                + iconset.getSelectedGroup());
            }

            //store iconset in DB, in a single DB transaction
            userIconDB.beginTransaction();
            long iconsetId = userIconDB.addIconSet(iconset);
            if (iconsetId < 0)
                throw new IOException("Failed to load iconset into DB: "
                        + file.getAbsolutePath());
            iconset.setId((int) iconsetId);

            //track already used filenames, allow only once per iconset (even across zip groups/directories)
            List<String> usedFilenames = new ArrayList<>();

            //now loop and store icons in DB
            int iconCount = 0;
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                if (iconCount > MAX_ICONS_PER_ICONSET) {
                    Log.d(TAG, "Max icons processed: " + MAX_ICONS_PER_ICONSET
                            + ", skipping remaining");
                    break;
                }

                ZipEntry ze = entries.nextElement();
                try {
                    if (!IconFilenameFilter.accept(null, ze.getName())) {
                        Log.d(TAG, "Ignoring zip entry: " + ze.getName());
                        continue;
                    }

                    //extract filename, group is top level directory name, default to "Other"
                    //if files are in top level of zip
                    String[] tokens = ze.getName().replace("\\", "/")
                            .split("/");
                    String group = tokens.length <= 1
                            ? UserIconPalletFragment.DEFAULT_GROUP
                            : tokens[0];
                    String filename = tokens[tokens.length - 1];

                    if (usedFilenames.contains(filename.toLowerCase(LocaleUtil
                            .getCurrent()))) {
                        Log.d(TAG, "Skipping duplicate filename: " + filename
                                + ", for zip entry: " + ze.getName());
                        continue;
                    }
                    usedFilenames.add(filename.toLowerCase(LocaleUtil
                            .getCurrent()));

                    //read in image
                    InputStream is = null;
                    byte[] bytes;
                    try {
                        is = zip.getInputStream(ze);
                        bytes = FileSystemUtils.read(is,
                                (int) ze.getSize(), false);
                    } finally {
                        if (is != null)
                            is.close();
                    }
                    if (FileSystemUtils.isEmpty(bytes)) {
                        Log.w(TAG, "Failed to load icon: " + ze.getName());
                        continue;
                    }

                    //see if we should scale all icons to 32x32
                    if (!iconset.isSkipResize()) {
                        Bitmap b = BitmapFactory.decodeByteArray(bytes, 0,
                                bytes.length);
                        b = Bitmap.createScaledBitmap(b, 32, 32, false);

                        //now back to byte rep
                        ByteArrayOutputStream byteArrayBitmapStream = new ByteArrayOutputStream();
                        b.compress(Bitmap.CompressFormat.PNG, 50,
                                byteArrayBitmapStream);
                        bytes = byteArrayBitmapStream.toByteArray();

                        if (FileSystemUtils.isEmpty(bytes)) {
                            Log.w(TAG,
                                    "Failed to load resized icon: "
                                            + ze.getName());
                            continue;
                        }
                    }

                    //see if we have optional data from iconset.xml
                    UserIcon icon = iconset.getIcon(filename);
                    if (icon == null) {
                        icon = new UserIcon();
                        icon.setFileName(filename);
                    }
                    icon.setGroup(group);
                    icon.setIconsetUid(iconset.getUid());
                    if (!userIconDB.addIcon(icon, bytes)) {
                        Log.w(TAG, "Failed to store icon: " + ze.getName());
                        continue;
                    }

                    iconCount++;
                } catch (Exception e) {
                    Log.e(TAG, "Failed to process: " + ze.getName(), e);
                }
            } //end zipentry loop

            Log.d(TAG,
                    "Added iconset: "
                            + iconset
                            + " with "
                            + iconCount
                            +
                            " icons in "
                            + ((double) (android.os.SystemClock
                                    .elapsedRealtime() - start)) / 1000D
                            + " seconds");
            if (!iconset.isSkipResize())
                Log.d(TAG,
                        "Normalized icon sizes to 32px square: "
                                + iconset);
            NotificationUtil.getInstance().postNotification(
                    com.atakmap.android.util.ATAKConstants.getIconId(),
                    NotificationUtil.WHITE,
                    context.getString(R.string.point_dropper_text26),
                    context.getString(R.string.point_dropper_text27)
                            + iconset.getName(),
                    context.getString(R.string.point_dropper_text27)
                            + iconset.getName(),
                    new Intent(IconManagerDropdown.DISPLAY_DROPDOWN));

            userIconDB.setTransactionSuccessful();
            return iconset;
        } catch (IOException ie) {
            Log.w(TAG, "Failed to add icon set: " + file.getAbsolutePath(), ie);
        } finally {
            userIconDB.endTransaction();
            if (zip != null) {
                try {
                    zip.close();
                } catch (Exception e) {
                    Log.e(TAG,
                            "Failed closing icon set: "
                                    + file.getAbsolutePath(),
                            e);
                }
            }
        }

        return null;
    }

    private static UserIconSet loadIconset(ZipFile zip, ZipEntry entry) {
        if (entry == null) {
            Log.d(TAG, "Missing " + ImportUserIconSetSort.ICONSET_XML);
            return null;
        }

        String iconsetXml;
        try {
            iconsetXml = FileSystemUtils.copyStreamToString(
                    zip.getInputStream(entry), false,
                    FileSystemUtils.UTF8_CHARSET);
        } catch (IOException e) {
            Log.e(TAG, "Failed to read " + ImportUserIconSetSort.ICONSET_XML,
                    e);
            return null;
        }

        if (FileSystemUtils.isEmpty(iconsetXml)) {
            Log.d(TAG, "Empty " + ImportUserIconSetSort.ICONSET_XML);
            return null;
        }

        return UserIconSet.loadUserIconSet(iconsetXml);
    }

    public void dispose() {
        _context = null;
        _prefs = null;

        if (_2525cIconAdapter != null) {
            _2525cIconAdapter.dispose();
            _2525cIconAdapter = null;
        }

        if (_spotMapIconAdapter != null) {
            _spotMapIconAdapter.dispose();
            _spotMapIconAdapter = null;
        }

        if (_iconsetIconAdapter != null) {
            _iconsetIconAdapter.dispose();
            _iconsetIconAdapter = null;
        }
    }

    /**
     * Simple background task add an iconset
     * 
     * 
     */
    private static class AddIconsetTask extends AsyncTask<Void, Void, String> {

        private static final String TAG = "AddIconsetTask";

        private ProgressDialog _progressDialog;
        private final String _filepath;
        private final Context _context;

        public AddIconsetTask(final String filepath, final Context _context) {
            _filepath = filepath;
            this._context = _context;
        }

        @Override
        protected void onPreExecute() {
            if (FileSystemUtils.isEmpty(_filepath)) {
                return;
            }

            _progressDialog = new ProgressDialog(_context);
            _progressDialog.setIcon(
                    com.atakmap.android.util.ATAKConstants.getIconId());
            _progressDialog.setTitle(_context
                    .getString(R.string.point_dropper_text28));
            _progressDialog.setMessage(_context.getString(R.string.importing)
                    + new File(_filepath).getName());
            _progressDialog.setIndeterminate(true);
            _progressDialog.setCancelable(false);
            _progressDialog.show();
        }

        @Override
        protected String doInBackground(Void... params) {
            Thread.currentThread().setName(TAG);

            if (_filepath == null) {
                Log.w(TAG, "No file to import");
                return null;
            }

            UserIconSet iconset = addIconset(new File(_filepath), _context);
            if (iconset != null) {
                //Log.d(TAG, "Added iconset: " + _filepath);
                return iconset.getUid();
            } else {
                //Log.w(TAG, "Failed to add iconset: " + _filepath);
                return null;
            }
        }

        @Override
        protected void onPostExecute(String uid) {
            if (_progressDialog != null) {
                _progressDialog.dismiss();
                _progressDialog = null;
            }

            if (FileSystemUtils.isEmpty(uid)) {
                Log.w(TAG, "Failed to import file: " + _filepath);
                return;
            }

            Log.d(TAG, "Finished importing: " + _filepath);
            AtakBroadcast.getInstance().sendBroadcast(
                    new Intent(IconsMapAdapter.ICONSET_ADDED)
                            .putExtra("uid", uid));
        }
    }

    /**
     * Simple background task remove an iconset
     * 
     * 
     */
    private static class RemoveIconsetTask
            extends AsyncTask<Void, Void, Boolean> {

        private static final String TAG = "RemoveIconsetTask";

        private ProgressDialog _progressDialog;
        private final String _uid;
        private final Context _context;
        private final SharedPreferences _prefs;

        public RemoveIconsetTask(final String uid, final Context _context,
                final SharedPreferences _prefs) {
            _uid = uid;
            this._context = _context;
            this._prefs = _prefs;
        }

        @Override
        protected void onPreExecute() {
            if (FileSystemUtils.isEmpty(_uid)) {
                return;
            }

            _progressDialog = new ProgressDialog(_context);
            _progressDialog.setIcon(
                    com.atakmap.android.util.ATAKConstants.getIconId());
            _progressDialog.setTitle(_context
                    .getString(R.string.point_dropper_text29));
            _progressDialog.setMessage(_context
                    .getString(R.string.point_dropper_text30));
            _progressDialog.setIndeterminate(true);
            _progressDialog.setCancelable(false);
            _progressDialog.show();
        }

        @Override
        protected Boolean doInBackground(Void... params) {
            Thread.currentThread().setName(TAG);

            if (_uid == null) {
                Log.w(TAG, "No Iconset to remove");
                return false;
            }

            if (UserIconDatabase.instance(_context).removeIconSet(_uid)) {
                //Log.d(TAG, "Removed iconset: " + _uid);
                IconManagerView.validateDefaultCoTMapping(_prefs, _uid);
                return true;
            } else {
                //Log.w(TAG, "Failed to remove iconset: " + _uid);
                return false;
            }
        }

        @Override
        protected void onPostExecute(Boolean result) {
            if (_progressDialog != null) {
                _progressDialog.dismiss();
                _progressDialog = null;
            }

            if (!result) {
                Log.w(TAG, "Failed to remove: " + _uid);
                return;
            }

            Log.d(TAG, "Finished removing: " + _uid);
            AtakBroadcast.getInstance().sendBroadcast(
                    new Intent(IconsMapAdapter.ICONSET_REMOVED)
                            .putExtra("uid", _uid));
        }
    }
}

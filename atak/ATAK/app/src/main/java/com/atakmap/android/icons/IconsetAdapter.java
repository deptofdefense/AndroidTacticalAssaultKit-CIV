
package com.atakmap.android.icons;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.AsyncTask;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageButton;
import android.widget.TextView;

import com.atakmap.android.importexport.send.SendDialog;
import com.atakmap.android.importfiles.sort.ImportUserIconSetSort;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.util.ATAKConstants;
import com.atakmap.android.util.NotificationUtil;
import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Manages list of Iconsets from the UI perspective
 * 
 * 
 */
public class IconsetAdapter extends BaseAdapter {
    private static final String TAG = "IconsetAdapter";

    public static final String ICONSET_CONTENTTYPE = "iconset";

    protected final List<UserIconSet> _iconsets;
    private final Context _context;

    public IconsetAdapter(MapView mapView) {
        _context = mapView.getContext();
        _iconsets = new ArrayList<>();
    }

    public void dispose() {
        _iconsets.clear();
    }

    /**
     * Prevents double adding, causes adapter to be redrawn
     * 
     * @param iconset the iconset to add to the adapter
     */
    synchronized public boolean add(UserIconSet iconset) {
        if (iconset == null) {
            Log.d(TAG, "Tried to add NULL iconset. Ignoring!");
            return false;
        }

        if (!iconset.isValid()) {
            Log.w(TAG, "Skipping invalid iconset: " + iconset);
            return false;
        }

        for (UserIconSet c : _iconsets) {
            if (c.equals(iconset)) {
                Log.d(TAG,
                        "Skipping already existing iconset: " + c);
                return false;
            }
        }

        if (!_iconsets.add(iconset)) {
            Log.w(TAG, "Failed to add iconset: " + iconset);
            return false;
        }

        Collections.sort(_iconsets, NameSort);
        requestRedraw();
        // Log.d(TAG, "Adding resource to UI: " + resource.toString());
        return true;
    }

    synchronized public UserIconSet getIconset(int position) {
        return (UserIconSet) getItem(position);
    }

    @Override
    public int getCount() {
        return _iconsets.size();
    }

    @Override
    public Object getItem(int position) {
        return _iconsets.get(position);
    }

    @Override
    public long getItemId(int position) {
        return _iconsets.get(position).hashCode();
    }

    @Override
    public View getView(int position, View convertView,
            final ViewGroup parent) {
        convertView = LayoutInflater.from(_context).inflate(
                R.layout.iconset_list_child, parent, false);

        if (_iconsets.size() <= position) {
            Log.w(TAG, "Unable to instantiate row view for position: "
                    + position);
            return LayoutInflater.from(_context).inflate(R.layout.empty, parent,
                    false);
        }

        final UserIconSet iconset = _iconsets.get(position);
        if (!iconset.isValid()) {
            Log.w(TAG,
                    "Unable to instantiate row view for position, for invalid resource "
                            + iconset);
            return LayoutInflater.from(_context).inflate(R.layout.empty, parent,
                    false);
        }

        Log.i(TAG, " building view for index " + position + "; Resource: "
                + iconset);

        TextView txtName = convertView
                .findViewById(R.id.iconset_child_name);
        txtName.setText(iconset.getName());

        TextView txtUid = convertView
                .findViewById(R.id.iconset_child_uid);
        txtUid.setText(iconset.getUid());

        TextView txtCount = convertView
                .findViewById(R.id.iconset_child_count);
        txtCount.setText(iconset.hasIcons() ? String.valueOf(iconset.getIcons()
                .size()) : String.valueOf(0));

        ImageButton btnSend = convertView
                .findViewById(R.id.iconset_child_send);
        btnSend.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                final File file = FileSystemUtils
                        .getItem(FileSystemUtils.EXPORT_DIRECTORY
                                + File.separatorChar
                                +
                                FileSystemUtils.sanitizeFilename(iconset
                                        .getName())
                                + ".zip");

                if (IOProviderFactory.exists(file)) {
                    new AlertDialog.Builder(_context)
                            .setTitle(R.string.point_dropper_text24)
                            .setMessage(
                                    _context.getString(
                                            R.string.point_dropper_text23)
                                            + file.getName()
                                            + "' ?")
                            .setPositiveButton(R.string.export,
                                    new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(
                                                DialogInterface dialog,
                                                int whichButton) {
                                            dialog.dismiss();
                                            new ExportIconsetTask(iconset, file,
                                                    _context)
                                                            .execute();
                                        }
                                    })
                            .setNegativeButton(R.string.cancel, null).show();
                } else {
                    new ExportIconsetTask(iconset, file, _context).execute();
                }
            }
        });

        ImageButton btnDelete = convertView
                .findViewById(R.id.iconset_child_delete);
        btnDelete.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                AlertDialog.Builder b = new AlertDialog.Builder(_context);
                b.setTitle(R.string.verify_delete);
                b.setMessage(_context.getString(R.string.delete_no_space)
                        + " '" + iconset.getName() + _context.getString(
                                R.string.iconset_delete));
                b.setPositiveButton(R.string.delete_no_space,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int w) {
                                AtakBroadcast.getInstance()
                                        .sendBroadcast(new Intent(
                                                IconsMapAdapter.REMOVE_ICONSET)
                                                        .putExtra("iconsetUid",
                                                                iconset.getUid()));
                            }
                        });
                b.setNegativeButton(R.string.cancel, null);
                b.show();
            }
        });

        return convertView;

    }

    @Override
    public boolean hasStableIds() {
        return true;
    }

    private void requestRedraw() {
        try {
            ((Activity) _context).runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    notifyDataSetChanged();
                }
            });
        } catch (ClassCastException e) {
            Log.e(TAG, "Failed to redraw adapter", e);
        }
    }

    protected synchronized void clear() {
        _iconsets.clear();
        this.notifyDataSetChanged();
    }

    private final Comparator<UserIconSet> NameSort = new Comparator<UserIconSet>() {

        @Override
        public int compare(UserIconSet lhs, UserIconSet rhs) {

            if (lhs == null || rhs == null)
                return 0;

            if (lhs.getName() == null || rhs.getName() == null)
                return 0;

            return lhs.getName().compareToIgnoreCase(rhs.getName());
        }
    };

    /**
     * Simple background task export an iconset
     * 
     * 
     */
    private static class ExportIconsetTask
            extends AsyncTask<Void, Void, Boolean> {

        private static final String TAG = "ExportIconsetTask";

        private ProgressDialog _progressDialog;
        private final UserIconSet _iconset;
        private final File _zip;
        private final Context _context;

        public ExportIconsetTask(UserIconSet iconset, File zip,
                Context _context) {
            _iconset = iconset;
            _zip = zip;
            this._context = _context;
        }

        @Override
        protected void onPreExecute() {
            if (_iconset == null) {
                return;
            }

            _progressDialog = new ProgressDialog(_context);
            _progressDialog.setIcon(
                    com.atakmap.android.util.ATAKConstants.getIconId());
            _progressDialog.setTitle(_context.getString(
                    R.string.point_dropper_text22));
            _progressDialog.setMessage(_context.getString(
                    R.string.exporting)
                    + _iconset.getName());
            _progressDialog.setIndeterminate(true);
            _progressDialog.setCancelable(false);
            _progressDialog.show();
        }

        @Override
        protected Boolean doInBackground(Void... params) {
            Thread.currentThread().setName(TAG);

            if (_iconset == null || _zip == null) {
                Log.w(TAG, "No Iconset to export");
                return false;
            }

            return export(_iconset, _zip);
        }

        @Override
        protected void onPostExecute(Boolean result) {
            if (_progressDialog != null) {
                _progressDialog.dismiss();
                _progressDialog = null;
            }

            if (!result) {
                Log.w(TAG, "Failed to export iconset");
                return;
            }

            Log.d(TAG, "Finished exporting: " + _iconset.toString() + " to "
                    + _zip.getAbsolutePath());

            //validate the required files
            if (ImportUserIconSetSort.HasIconset(_zip, true)) {
                Log.d(TAG, "Exported: " + _zip.getAbsolutePath()
                        + " with icon count " + _iconset.getIcons().size());

                //allow user to export/upload e.g. FTP/Sync/etc                
                AlertDialog.Builder b = new AlertDialog.Builder(_context);
                b.setTitle(R.string.iconset_exported);
                b.setMessage(_context.getString(R.string.iconset_send)
                        + _zip.getAbsolutePath());
                b.setPositiveButton(R.string.send,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int w) {
                                SendDialog.Builder b = new SendDialog.Builder(
                                        MapView.getMapView());
                                b.addFile(_zip, ICONSET_CONTENTTYPE);
                                b.setIcon(ATAKConstants.getIcon());
                                b.show();
                            }
                        });
                b.setNegativeButton(R.string.cancel, null);
                b.show();
            } else {
                Log.w(TAG,
                        "Failed to export valid iconset: "
                                + _zip.getAbsolutePath());
                NotificationUtil.getInstance().postNotification(
                        R.drawable.ic_network_error_notification_icon,
                        NotificationUtil.RED,
                        _context.getString(R.string.export_failed),
                        _context.getString(R.string.iconset_export_fail)
                                + _iconset.getName(),
                        _context.getString(R.string.iconset_export_fail)
                                + _iconset.getName());
            }
        }

        private boolean export(final UserIconSet iconset, final File zip) {

            if (iconset == null || !iconset.isValid() || !iconset.hasIcons()) {
                Log.w(TAG, "Failed to export invalid iconset: "
                        + (iconset == null ? "" : iconset.toString()));
                NotificationUtil.getInstance().postNotification(
                        R.drawable.ic_network_error_notification_icon,
                        NotificationUtil.RED,
                        _context.getString(R.string.export_failed),
                        _context.getString(R.string.iconset_export_fail),
                        _context.getString(R.string.iconset_export_fail2));
                return false;
            }

            //export iconset.xml
            String iconsetXml = iconset.save();
            if (FileSystemUtils.isEmpty(iconsetXml)) {
                Log.w(TAG,
                        "Failed to export iconset XML: " + iconset);
                NotificationUtil.getInstance().postNotification(
                        R.drawable.ic_network_error_notification_icon,
                        NotificationUtil.RED,
                        _context.getString(R.string.export_failed),
                        _context.getString(R.string.iconset_export_fail)
                                + iconset.getName(),
                        _context.getString(R.string.iconset_export_fail)
                                + iconset.getName());
                return false;
            }

            try (ZipOutputStream zos = FileSystemUtils
                    .getZipOutputStream(zip)) {

                //add iconset.xml
                addFile(zos, null, ImportUserIconSetSort.ICONSET_XML,
                        iconsetXml.getBytes(FileSystemUtils.UTF8_CHARSET));

                //add all icons
                for (UserIcon icon : iconset.getIcons()) {

                    byte[] bitMap = UserIconDatabase.instance(
                            _context).getIconBytes(icon.getId());
                    if (FileSystemUtils.isEmpty(bitMap)) {
                        throw new IOException("Failed to export icon: "
                                + icon);
                    }

                    addFile(zos, icon.getGroup(), icon.getFileName(), bitMap);
                }

                return true;
            } catch (Exception e) {
                Log.e(TAG, "Failed to create iconset " + zip.getAbsolutePath(),
                        e);
                NotificationUtil.getInstance().postNotification(
                        R.drawable.ic_network_error_notification_icon,
                        NotificationUtil.RED,
                        _context.getString(R.string.export_failed),
                        _context.getString(R.string.iconset_export_fail)
                                + iconset.getName(),
                        _context.getString(R.string.iconset_export_fail)
                                + iconset.getName());
                return false;
            }
        }
    }

    private static void addFile(ZipOutputStream zos, String folder,
            String filename, byte[] bitMap) {
        try {
            // create new zip entry
            String entryName = filename;
            if (!FileSystemUtils.isEmpty(folder)) {
                entryName = folder + File.separatorChar + filename;
            }

            ZipEntry entry = new ZipEntry(entryName);
            zos.putNextEntry(entry);

            // stream file into zipstream
            FileSystemUtils.copyStream(new ByteArrayInputStream(bitMap), true,
                    zos, false);

            // close current file & corresponding zip entry
            zos.closeEntry();
        } catch (IOException e) {
            Log.e(TAG, "Failed to add File: " + filename, e);
        }
    }
}

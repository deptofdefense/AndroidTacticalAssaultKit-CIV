
package com.atakmap.android.missionpackage.export;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.atakmap.android.filesharing.android.service.AndroidFileInfo;
import com.atakmap.android.filesharing.android.service.FileInfo;
import com.atakmap.android.filesharing.android.service.FileInfoPersistanceHelper;
import com.atakmap.android.math.MathUtils;
import com.atakmap.android.missionpackage.file.MissionPackageManifest;
import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Adapter for displaying a list of Mission Packages
 */
public class MissionPackageAdapter extends BaseAdapter {

    private static final String TAG = "MissionPackageAdapter";

    private static final Comparator<MissionPackageManifest> NAME_COMP = new Comparator<MissionPackageManifest>() {
        @Override
        public int compare(MissionPackageManifest lhs,
                MissionPackageManifest rhs) {
            return lhs.getName().compareTo(rhs.getName());
        }
    };

    private final Context _context;
    private final LayoutInflater _inflater;
    private final List<MissionPackageManifest> _packages = new ArrayList<>();

    public MissionPackageAdapter(Context context) {
        _context = context;
        _inflater = LayoutInflater.from(context);
        refreshPackages();
    }

    private void refreshPackages() {
        Map<String, MissionPackageManifest> pkgs = new HashMap<>();
        List<AndroidFileInfo> files = FileInfoPersistanceHelper.instance()
                .allFiles(FileInfoPersistanceHelper.TABLETYPE.SAVED);
        if (!FileSystemUtils.isEmpty(files)) {
            for (FileInfo fi : files) {
                MissionPackageManifest c = MissionPackageManifest.fromXml(
                        fi.fileMetadata(), fi.file().getAbsolutePath());
                if (c == null || !c.isValid()) {
                    Log.w(TAG, "Failed to load Manifest: " + fi);
                    continue;
                }
                MissionPackageManifest existing = pkgs.get(c.getName());
                if (existing != null) {
                    File fc = new File(c.getPath());
                    File fe = new File(existing.getPath());
                    if (!IOProviderFactory.exists(fc) || (IOProviderFactory
                            .exists(fe)
                            && IOProviderFactory.lastModified(
                                    fc) < IOProviderFactory.lastModified(fe)))
                        continue;
                }
                pkgs.put(c.getName(), c);
            }
        }
        _packages.clear();
        _packages.addAll(pkgs.values());
        Collections.sort(_packages, NAME_COMP);
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return _packages.size();
    }

    @Override
    public Object getItem(int position) {
        return _packages.get(position);
    }

    @Override
    public long getItemId(int position) {
        return 0;
    }

    @Override
    public View getView(int position, View row, ViewGroup parent) {
        ViewHolder h = row != null ? (ViewHolder) row.getTag() : null;
        if (h == null) {
            row = _inflater.inflate(R.layout.missionpackage_list_child,
                    parent, false);
            h = new ViewHolder();
            h.name = row.findViewById(R.id.mission_package_name);
            h.size = row.findViewById(R.id.mission_package_size);
            h.items = row.findViewById(R.id.mission_package_items);
            row.setTag(h);
        }

        MissionPackageManifest mpm = (MissionPackageManifest) getItem(position);
        if (mpm == null)
            return _inflater.inflate(R.layout.empty, parent, false);

        h.name.setText(mpm.getName());
        h.size.setText(MathUtils.GetLengthString(mpm.getTotalSize()));

        h.items.setText(_context
                .getString(R.string.mission_package_edit_content_text,
                        mpm.getMapItemCount(), mpm.getFileCount())
                .replace("(", "").replace(")", ""));

        return row;
    }

    private static class ViewHolder {
        TextView name, size, items;
    }
}

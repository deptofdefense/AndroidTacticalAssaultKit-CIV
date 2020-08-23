
package com.atakmap.android.missionpackage.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.text.InputFilter;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.atakmap.android.data.URIContentHandler;
import com.atakmap.android.data.URIContentManager;
import com.atakmap.android.hashtags.HashtagContent;
import com.atakmap.android.hashtags.util.HashtagUtils;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.missionpackage.MissionPackageUtils;
import com.atakmap.android.missionpackage.export.MissionPackageExportMarshal;
import com.atakmap.android.missionpackage.file.MissionPackageContent;
import com.atakmap.android.missionpackage.file.MissionPackageManifest;
import com.atakmap.android.missionpackage.file.NameValuePair;
import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Edit dialog for data package metadata
 */
public class MissionPackageEditDialog {

    private static final String TAG = "MissionPackageEditDialog";

    private final MapView _mapView;
    private final Context _context;
    private final MissionPackageManifest _manifest;

    public MissionPackageEditDialog(MapView mapView,
            MissionPackageManifest mpm) {
        _mapView = mapView;
        _context = mapView.getContext();
        _manifest = mpm;
    }

    private EditText editName, editRemarks;

    public void show() {
        View v = LayoutInflater.from(_context).inflate(
                R.layout.missionpackage_edit, _mapView, false);

        // allow only alphanumeric, space, underscore (this will be a filename)
        editName = v.findViewById(R.id.name);
        editName.setFilters(new InputFilter[] {
                MissionPackageExportMarshal.NAME_FILTER
        });
        editName.setText(_manifest.getName());

        editRemarks = v.findViewById(R.id.remarks);
        editRemarks.setText(_manifest.getRemarks());

        TextView txtContent = v.findViewById(R.id.content_count);
        if (_manifest.isEmpty())
            txtContent.setVisibility(TextView.GONE);
        else
            txtContent.setText(_context.getString(
                    R.string.mission_package_edit_content_text,
                    _manifest.getMapItemCount(),
                    _manifest.getFileCount()));

        AlertDialog.Builder b = new AlertDialog.Builder(_context);
        b.setIcon(R.drawable.missionpackage_icon);
        b.setTitle(R.string.mission_package_name);
        b.setCancelable(false);
        b.setPositiveButton(R.string.done, null);
        b.setNegativeButton(R.string.cancel, null);
        b.setView(v);
        final AlertDialog d = b.show();

        Button doneBtn = d.getButton(AlertDialog.BUTTON_POSITIVE);
        doneBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // Validate inputs (e.g. package name)
                if (validateInput())
                    d.dismiss();
            }
        });
    }

    /**
     * test the fields for valid input If valid, set them on the Manifest working copy
     * @return - true if all fields are valid
     */
    private boolean validateInput() {
        if (editName == null)
            return false;
        String name = editName.getText().toString().trim();
        if (FileSystemUtils.isEmpty(name)) {
            Toast.makeText(_context, R.string.mission_package_specify_name,
                    Toast.LENGTH_LONG).show();
            return false;
        }

        // see if name has changed and be sure unique
        if (!name.equals(_manifest.getName())) {
            // be sure name is unique
            String temp = MissionPackageUtils.getUniqueName(_context, name);
            if (!FileSystemUtils.isEquals(name, temp)) {
                name = temp;
                Log.d(TAG, "Updated to unique name: " + name);
                editName.setText(name);
            }

            // now update the manifest working copy
            _manifest.setName(name);
        }

        String remarks = editRemarks.getText().toString().trim();
        String prevRemarks = _manifest.getRemarks();
        if (!FileSystemUtils.isEquals(remarks, prevRemarks))
            _manifest.setRemarks(remarks);

        // Update content hashtags
        List<String> tags = HashtagUtils.extractTags(remarks);
        List<String> oldTags = HashtagUtils.extractTags(prevRemarks);
        List<String> newTags = new ArrayList<>(tags);

        newTags.removeAll(oldTags);
        oldTags.removeAll(tags);

        // No tags to update
        if (newTags.isEmpty() && oldTags.isEmpty())
            return true;

        for (MissionPackageContent c : _manifest.getContents().getContents()) {
            HashtagContent content = null;
            if (c.isCoT()) {
                NameValuePair nvp = c
                        .getParameter(MissionPackageContent.PARAMETER_UID);
                if (nvp != null)
                    content = _mapView.getRootGroup()
                            .deepFindUID(nvp.getValue());
            } else {
                NameValuePair nvp = c.getParameter(
                        MissionPackageContent.PARAMETER_LOCALPATH);
                if (nvp == null)
                    continue;
                String path = nvp.getValue();
                if (FileSystemUtils.isEmpty(path))
                    continue;
                URIContentHandler handler = URIContentManager.getInstance()
                        .getHandler(new File(path));
                if (handler instanceof HashtagContent)
                    content = (HashtagContent) handler;
            }
            if (content != null) {
                Collection<String> tagSet = content.getHashtags();
                tagSet.removeAll(oldTags);
                tagSet.addAll(newTags);
                content.setHashtags(tagSet);
            }
        }

        return true;
    }
}

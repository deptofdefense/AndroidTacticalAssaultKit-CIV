
package com.atakmap.android.rubbersheet.ui.dialog;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.RadioGroup;
import android.widget.TextView;

import com.atakmap.android.maps.MapView;
import com.atakmap.android.rubbersheet.data.ModelProjection;
import com.atakmap.android.rubbersheet.data.RubberModelData;
import com.atakmap.android.rubbersheet.data.create.CreateRubberModelTask;
import com.atakmap.app.R;

import java.io.File;

/**
 * 3D Model import options
 */
public class ImportModelDialog {

    private final MapView _mapView;
    private final Context _context;

    public ImportModelDialog(MapView mapView) {
        _mapView = mapView;
        _context = mapView.getContext();
    }

    /**
     * Show the import dialog for a model
     * At this point all we know is that the file contains a supported model
     * @param file Model file
     */
    public void show(final File file, final CreateRubberModelTask.Callback cb) {
        View v = LayoutInflater.from(_context).inflate(
                R.layout.rs_model_import_dialog, _mapView, false);
        TextView msg = v.findViewById(R.id.import_msg);
        msg.setText(_context.getString(R.string.import_model_msg,
                file.getName()));

        final RadioGroup proj = v.findViewById(R.id.projection);
        final CheckBox flipYZ = v.findViewById(R.id.flip_yz);

        proj.setOnCheckedChangeListener(
                new RadioGroup.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(RadioGroup group,
                            int checkedId) {
                        flipYZ.setEnabled(checkedId == R.id.projection_enu);
                    }
                });

        AlertDialog.Builder b = new AlertDialog.Builder(_context);
        b.setTitle(_context.getString(R.string.import_model));
        b.setView(v);
        b.setPositiveButton(_context.getString(R.string.import_string),
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        RubberModelData data = new RubberModelData(file);
                        int selectedProj = proj.getCheckedRadioButtonId();
                        if (selectedProj == R.id.projection_enu) {
                            if (flipYZ.isChecked())
                                data.projection = ModelProjection.ENU_FLIP_YZ;
                            else
                                data.projection = ModelProjection.ENU;
                        } else
                            data.projection = ModelProjection.LLA;
                        new CreateRubberModelTask(_mapView, data, false, cb)
                                .execute();
                    }
                });
        b.setNegativeButton(_context.getString(R.string.cancel), null);
        b.show();
    }
}

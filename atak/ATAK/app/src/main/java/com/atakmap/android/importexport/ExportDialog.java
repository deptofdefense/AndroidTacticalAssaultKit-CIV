
package com.atakmap.android.importexport;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Environment;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Toast;

import com.atakmap.android.gui.ImportFileBrowserDialog;
import com.atakmap.android.importexport.ExporterManager.ExportMarshalMetadata;
import com.atakmap.android.importexport.send.SendDialog;
import com.atakmap.android.maps.MapView;
import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.locale.LocaleUtil;
import com.atakmap.coremap.log.Log;
import com.atakmap.spatial.file.GpxFileSpatialDb;
import com.atakmap.spatial.file.KmlFileSpatialDb;
import com.atakmap.spatial.file.ShapefileSpatialDb;

import java.io.File;
import java.util.List;

/**
 * General purpose dialog for exporting items using {@link ExporterManager}
 */
public class ExportDialog {

    private static final String TAG = "ExportDialog";

    /**
     * Export selection callback
     */
    public interface Callback {

        /**
         * Exporter selected
         * @param marshal Export marshal
         */
        void onExporterSelected(ExportMarshal marshal);
    }

    private final MapView _mapView;
    private final Context _context;

    private Callback _callback;

    public ExportDialog(MapView mapView) {
        _mapView = mapView;
        _context = mapView.getContext();
    }

    /**
     * Set the callback when an export marshal is selected
     * @param cb Callback
     */
    public void setCallback(Callback cb) {
        _callback = cb;
    }

    /**
     * Show export dialog
     */
    public void show() {
        List<ExportMarshalMetadata> exporters = ExporterManager
                .getExporterTypes();
        if (FileSystemUtils.isEmpty(exporters)) {
            Toast.makeText(_context, R.string.no_exporters_registered,
                    Toast.LENGTH_SHORT).show();
            Log.w(TAG, "No exporters registered");
            return;
        }

        final ExportMarshalAdapter exportAdapter = new ExportMarshalAdapter(
                _context, R.layout.exportdata_item,
                exporters.toArray(new ExportMarshalMetadata[0]));

        LayoutInflater inflater = LayoutInflater.from(_context);
        LinearLayout layout = (LinearLayout) inflater.inflate(
                R.layout.exportdata_list, _mapView, false);
        ListView listView = layout.findViewById(R.id.exportDataList);
        listView.setAdapter(exportAdapter);

        AlertDialog.Builder b = new AlertDialog.Builder(_context);
        b.setIcon(R.drawable.export_menu_default);
        b.setTitle(R.string.selection_export_dialogue);
        b.setView(layout);
        b.setPositiveButton(R.string.previous_export_dialogue,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        final String startDir;
                        File exportDir = FileSystemUtils.getItem(
                                FileSystemUtils.EXPORT_DIRECTORY);

                        if (FileSystemUtils.isFile(exportDir)
                                && IOProviderFactory.isDirectory(exportDir))
                            startDir = exportDir.getAbsolutePath();
                        else
                            startDir = Environment.getExternalStorageDirectory()
                                    .getPath();

                        ImportFileBrowserDialog.show(_context.getString(
                                R.string.select_file_to_send), startDir,
                                new String[] {
                                        GpxFileSpatialDb.GPX_CONTENT_TYPE
                                                .toLowerCase(LocaleUtil
                                                        .getCurrent()),
                                        KmlFileSpatialDb.KML_TYPE,
                                        KmlFileSpatialDb.KMZ_TYPE,
                                        ShapefileSpatialDb.SHP_TYPE
                        },
                                new ImportFileBrowserDialog.DialogDismissed() {
                                    @Override
                                    public void onFileSelected(File file) {
                                        sendFile(file);
                                    }

                                    @Override
                                    public void onDialogClosed() {
                                        //do nothing
                                    }
                                }, _context);
                    }
                });
        b.setNegativeButton(R.string.cancel, null);

        final AlertDialog bd = b.create();
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {

            @Override
            public void onItemClick(AdapterView<?> arg0, View arg1, int arg2,
                    long arg3) {
                bd.dismiss();

                ExportMarshalMetadata meta = exportAdapter.getItem(arg2);
                if (meta == null || FileSystemUtils.isEmpty(meta.getType())) {
                    Toast.makeText(_context, "Failed to export selected items",
                            Toast.LENGTH_SHORT).show();
                    Log.w(TAG,
                            "Failed to export selected items, no type selected");
                    return;
                }

                //go ahead and select items
                final ExportMarshal marshal = ExporterManager.findExporter(
                        _context, meta.getType());
                if (marshal == null) {
                    Toast.makeText(_context,
                            "Failed to export selected items",
                            Toast.LENGTH_SHORT).show();
                    Log.w(TAG,
                            "Failed to export selected items, no Export Marshal available");
                    return;
                }

                _callback.onExporterSelected(marshal);
            }
        });

        bd.show();
    }

    private void sendFile(File file) {
        ExportMarshal exporter;
        String ext = FileSystemUtils.getExtension(file, false, false);
        if (ShapefileSpatialDb.SHP_TYPE.equalsIgnoreCase(ext))
            exporter = ExporterManager.findExporter(_context,
                    ShapefileSpatialDb.SHP_CONTENT_TYPE);
        else
            exporter = ExporterManager.findExporter(_context,
                    ext.toUpperCase(LocaleUtil.getCurrent()));

        if (exporter == null) {
            Log.w(TAG, "No exporter available for : " + file);
            return;
        }

        Log.d(TAG, "Sending previously exported file: "
                + file.getAbsolutePath());

        SendDialog.Builder b = new SendDialog.Builder(_mapView);
        b.setName(file.getName());
        b.setIcon(exporter.getIconId());
        b.addFile(file, exporter.getContentType());
        b.show();
    }
}

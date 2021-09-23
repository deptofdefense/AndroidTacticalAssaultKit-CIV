
package com.atakmap.android.tilecapture.imagery;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;

import com.atakmap.android.imagecapture.CapturePrefs;
import com.atakmap.android.imagecapture.ImageDialog;
import com.atakmap.android.maps.MapView;
import com.atakmap.coremap.log.Log;

/**
 * Image capture preview dialog w/ post-processing options
 */
public class ImageryCaptureDialog extends ImageDialog implements TextWatcher {

    private static final String TAG = "ImageryCaptureDialog";

    // Text input
    private static final int LABEL_SIZE = 0;
    private static final int ICON_SIZE = 1;
    private static final int LINE_WEIGHT = 2;
    private static final int ET_COUNT = 3;

    protected LinearLayout _container;
    protected CheckBox _imageryCB;
    protected final EditText[] _et = new EditText[ET_COUNT];

    // Callbacks
    protected Runnable _onSave, _onRedo;
    protected Runnable _onChanged;

    public ImageryCaptureDialog(MapView mapView) {
        super(mapView.getContext());
    }

    @Override
    public void onViewCreated() {
        _container = _root
                .findViewById(com.atakmap.app.R.id.image_dlg_container);
        _et[LABEL_SIZE] = _root
                .findViewById(com.atakmap.app.R.id.viz_cap_label_size);
        _et[ICON_SIZE] = _root
                .findViewById(com.atakmap.app.R.id.viz_cap_icon_size);
        _et[LINE_WEIGHT] = _root
                .findViewById(com.atakmap.app.R.id.viz_cap_line_weight);

        // Check boxes
        _imageryCB = _root
                .findViewById(com.atakmap.app.R.id.viz_cap_show_imagery);

        // Set text inputs
        for (int i = 0; i < ET_COUNT; i++) {
            if (_et[i] == null)
                Log.w(TAG, "Text input[" + i + "] is null!");
            else {
                int defValue = 0;
                String key = getETPrefIndex(i);
                switch (i) {
                    case LABEL_SIZE:
                        defValue = 10;
                        break;
                    case ICON_SIZE:
                        defValue = 24;
                        break;
                    case LINE_WEIGHT:
                        defValue = 3;
                        break;
                }
                _et[i].setText(String.valueOf(CapturePrefs.get(key, defValue)));
                _et[i].addTextChangedListener(this);
            }
        }

        _imageryCB.setChecked(CapturePrefs.get(
                CapturePrefs.PREF_SHOW_IMAGERY, true));
        _imageryCB.setOnCheckedChangeListener(
                new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton v,
                            boolean check) {
                        CapturePrefs.set(CapturePrefs.PREF_SHOW_IMAGERY, check);
                        notifyChange();
                    }
                });
    }

    @Override
    protected void setupDialogButtons(AlertDialog.Builder adb) {
        super.setupDialogButtons(adb);
        adb.setPositiveButton(com.atakmap.app.R.string.save,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (_onSave != null)
                            _onSave.run();
                        dialog.dismiss();
                    }
                });
        adb.setNegativeButton(com.atakmap.app.R.string.cancel,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (_onRedo != null)
                            _onRedo.run();
                        dialog.dismiss();
                    }
                });
    }

    public void notifyChange() {
        if (_onChanged != null)
            _onChanged.run();
    }

    protected String getETPrefIndex(int index) {
        switch (index) {
            default:
            case LABEL_SIZE:
                return CapturePrefs.PREF_LABEL_SIZE;
            case ICON_SIZE:
                return CapturePrefs.PREF_ICON_SIZE;
            case LINE_WEIGHT:
                return CapturePrefs.PREF_LINE_WEIGHT;
        }
    }

    @Override
    public String getTitle() {
        return "Capture Result";
    }

    @Override
    protected int getLayoutId() {
        return com.atakmap.app.R.layout.image_capture_pp_dialog;
    }

    @Override
    protected Context getLayoutContext() {
        return _context;
    }

    public void setOnSaveListener(Runnable r) {
        _onSave = r;
    }

    public void setOnRedoListener(Runnable r) {
        _onRedo = r;
    }

    public void setOnChangedListener(Runnable r) {
        _onChanged = r;
    }

    @Override
    protected void beginSetup() {
        if (_created) {
            _loader.setVisibility(View.GONE);
            _container.setVisibility(View.VISIBLE);
            _imgView.setVisibility(View.VISIBLE);
            _imgView.setImageBitmap(_bmp);
            setTitle(null);
            loadFinished();
        }
    }

    @Override
    protected Bitmap loadBitmap() {
        return _bmp;
    }

    @Override
    public void loadFinished() {
        _container.setVisibility(View.VISIBLE);
    }

    @Override
    public void beforeTextChanged(CharSequence s,
            int start, int count, int after) {
    }

    @Override
    public void onTextChanged(CharSequence s,
            int start, int before, int count) {
    }

    @Override
    public void afterTextChanged(Editable s) {
        try {
            for (int i = 0; i < ET_COUNT; i++)
                CapturePrefs.set(getETPrefIndex(i), Integer.parseInt(
                        _et[i].getText().toString()));
            notifyChange();
        } catch (NumberFormatException ignored) {
        }
    }
}


package com.atakmap.android.imagecapture;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.graphics.Bitmap;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.EditText;
import android.widget.LinearLayout;

import com.atakmap.app.R;
import com.atakmap.coremap.log.Log;

import com.atakmap.coremap.locale.LocaleUtil;

/**
 * Survey image capture dialog w/ post-processing options
 */
public class CaptureDialog extends ImageDialog
        implements TextWatcher, ImageCapturePP.ProgressCallback {

    private static final String TAG = "GRGCaptureDialog";

    // Text input
    private static final int LABEL_SIZE = 0;
    private static final int ICON_SIZE = 1;
    private static final int LINE_WEIGHT = 2;
    private static final int ET_COUNT = 3;

    protected ImageCapturePP _postDraw;
    protected LinearLayout _container;
    protected CheckBox _imageryCB;
    protected final EditText[] _et = new EditText[ET_COUNT];

    // Callbacks
    protected Runnable _onSave, _onRedo;
    protected Runnable _onChanged;

    public CaptureDialog(Context context, ImageCapturePP postDraw) {
        super(context);
        _postDraw = postDraw;
        _postDraw.setProgressCallback(this);
    }

    @Override
    public void onViewCreated() {
        _container = _root
                .findViewById(R.id.image_dlg_container);
        _et[LABEL_SIZE] = _root
                .findViewById(R.id.viz_cap_label_size);
        _et[ICON_SIZE] = _root.findViewById(R.id.viz_cap_icon_size);
        _et[LINE_WEIGHT] = _root
                .findViewById(R.id.viz_cap_line_weight);

        // Check boxes
        _imageryCB = _root.findViewById(R.id.viz_cap_show_imagery);

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
        _imageryCB.setOnCheckedChangeListener(new OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton v, boolean check) {
                CapturePrefs.set(CapturePrefs.PREF_SHOW_IMAGERY, check);
                notifyChange();
            }
        });
    }

    @Override
    protected void setupDialogButtons(AlertDialog.Builder adb) {
        super.setupDialogButtons(adb);
        adb.setPositiveButton(R.string.save, new OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (_onSave != null)
                    _onSave.run();
                dialog.dismiss();
            }
        });
        adb.setNegativeButton(R.string.cancel, new OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (_onRedo != null)
                    _onRedo.run();
                dialog.dismiss();
            }
        });
    }

    public Bitmap getBitmap() {
        return _bmp;
    }

    @Override
    public void onProgress(int prog) {
        setTitle(String.format(LocaleUtil.getCurrent(), _context.getResources()
                .getString(R.string.processing_image), prog));
    }

    protected void notifyChange() {
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
        return R.layout.image_capture_pp_dialog;
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

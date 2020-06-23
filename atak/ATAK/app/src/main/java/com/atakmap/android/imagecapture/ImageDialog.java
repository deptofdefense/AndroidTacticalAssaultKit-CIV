
package com.atakmap.android.imagecapture;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Handler;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.atakmap.app.R;

import java.io.File;

/**
 * Generic image dialog
 */
public abstract class ImageDialog implements DialogInterface.OnDismissListener,
        DialogInterface.OnCancelListener {
    private static final String TAG = "ImageDialog";

    // view components
    protected final Context _context;
    protected View _root;
    protected TextView _title;
    protected ImageView _imgView;
    protected ProgressBar _loader;

    // protected fields
    protected Bitmap _bmp;
    protected File _inFile;
    protected int _width, _height;
    protected boolean _loadingImg = false;

    protected boolean _created = false;
    protected Handler _viewHandler;
    protected Runnable _onCancel;

    protected AlertDialog _dialog;

    protected final OnClickListener _cancelClick = new OnClickListener() {
        @Override
        public void onClick(DialogInterface dialog, int which) {
            onCancel(dialog);
        }
    };

    public ImageDialog(Context context) {
        _context = context;
    }

    protected void initView() {
        _created = false;
        _root = LayoutInflater.from(getLayoutContext())
                .inflate(getLayoutId(), null, false);

        AlertDialog.Builder adb = new AlertDialog.Builder(_context);
        adb.setView(_root);

        setupDialogButtons(adb);
        _dialog = adb.create();

        DisplayMetrics metrics = _context.getResources()
                .getDisplayMetrics();
        float margin = metrics.density * 10f;
        int width = metrics.widthPixels;
        int height = metrics.heightPixels;
        _width = (int) (width - margin);
        _height = (int) (height - margin);
        _root.setMinimumWidth(_width);
        _root.setMinimumHeight(_height);
    }

    protected void setupView() {
        _title = _root.findViewById(R.id.image_dlg_title);
        _imgView = _root.findViewById(R.id.image_dlg_bitmap);
        _loader = _root.findViewById(R.id.image_dlg_loader);

        _viewHandler = new Handler();
        onViewCreated();

        _created = true;
    }

    public void show() {
        if (_dialog == null || _dialog.isShowing())
            return;
        setupView();
        beginSetup();
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams();
        Window w = _dialog.getWindow();
        if (w != null) {
            w.requestFeature(Window.FEATURE_NO_TITLE);
            w.setFlags(
                    WindowManager.LayoutParams.FLAG_FULLSCREEN,
                    WindowManager.LayoutParams.FLAG_FULLSCREEN);
            lp.copyFrom(w.getAttributes());
            lp.width = WindowManager.LayoutParams.MATCH_PARENT;
            lp.height = WindowManager.LayoutParams.MATCH_PARENT;
        }
        _dialog.setCanceledOnTouchOutside(false);
        _dialog.setOnCancelListener(this);
        _dialog.setOnDismissListener(this);

        _dialog.show();

        w = _dialog.getWindow();
        if (w != null) {
            w.setAttributes(lp);

            // Need to set the nav bar color AFTER calling show() or some
            // versions of Android throw a fit - see ATAK-10907
            w.addFlags(
                    WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            w.getDecorView()
                    .setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
            w.setNavigationBarColor(Color.rgb(0, 0, 0));
        }
    }

    // Process extra views
    protected void onViewCreated() {
        // Override me
    }

    @Override
    public void onCancel(DialogInterface dialog) {
        if (_onCancel != null)
            _onCancel.run();
        dialog.dismiss();
    }

    @Override
    public void onDismiss(DialogInterface dialog) {
        dispose();
    }

    public void dispose() {
        _created = false;
        _inFile = null;
        cleanup();
    }

    /**
     * Set dialog buttons text and actions
     * @param adb Dialog builder
     */
    protected void setupDialogButtons(AlertDialog.Builder adb) {
        adb.setNegativeButton(R.string.cancel, _cancelClick);
    }

    public void setOnCancelListener(Runnable r) {
        _onCancel = r;
    }

    // Dialog title
    protected abstract String getTitle();

    // Return layout resource id here
    protected abstract int getLayoutId();

    // Return layout context (usually mapView.getContext())
    protected abstract Context getLayoutContext();

    // Image finished loading
    protected abstract void loadFinished();

    // Return image bitmap here
    protected Bitmap loadBitmap() {
        return _bmp;
    }

    public void setTitle(int resId) {
        if (_created)
            _title.setText(resId);
    }

    public void setTitle(String title) {
        if (_created) {
            if (title == null)
                title = getTitle();
            _title.setText(title);
        }
    }

    public void setupImage(final Bitmap bmp) {
        cleanup();
        _bmp = bmp;
        beginSetup();
    }

    // Set input image and display (if dialog is ready)
    public void setupImage(File inFile) {
        _inFile = inFile;
        beginSetup();
    }

    protected void beginSetup() {
        if (_created) {
            _loadingImg = true;
            setTitle(R.string.loading_image);
            _imgView.setVisibility(View.GONE);
            _loader.setVisibility(View.VISIBLE);
            // Start markup processing
            new Thread(new Runnable() {
                @Override
                public void run() {
                    if (_inFile != null) {
                        cleanup();
                        _bmp = loadBitmap();
                    }
                    // Show new bitmap in container
                    if (_created) {
                        _viewHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                _loader.setVisibility(View.GONE);
                                _imgView.setVisibility(View.VISIBLE);
                                _imgView.setImageBitmap(_bmp);
                                _loadingImg = false;
                                setTitle(null);
                                loadFinished();
                            }
                        });
                    } else
                        cleanup();
                }
            }, TAG + "-BeginSetup").start();
        }
    }

    protected void cleanup() {
        if (_bmp != null) {
            _bmp.recycle();
            _bmp = null;
        }
    }
}

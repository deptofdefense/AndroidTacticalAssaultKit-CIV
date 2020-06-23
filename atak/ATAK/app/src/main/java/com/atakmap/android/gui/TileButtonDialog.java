
package com.atakmap.android.gui;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.graphics.drawable.Drawable;

import com.atakmap.android.maps.MapView;
import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Dialog with tile buttons
 */
public class TileButtonDialog implements DialogInterface.OnDismissListener,
        View.OnClickListener {

    public static final int WHICH_CANCEL = -1;
    private final MapView _mapView;
    private final Context _context, _plugin;
    private final LayoutInflater _inflater;
    private final List<TileButton> _buttons = new ArrayList<>();
    private final boolean _bPersistent;
    private AlertDialog _dialog;
    private DialogInterface.OnClickListener _onClick;
    private Drawable _icon;

    // Tile button measurements
    private final float _dp;

    public TileButtonDialog(MapView mapView) {
        this(mapView, false);
    }

    public TileButtonDialog(MapView mapView, boolean persistent) {
        this(mapView, mapView.getContext(), persistent);
    }

    public TileButtonDialog(MapView mapView, Context plugin) {
        this(mapView, plugin, false);
    }

    public TileButtonDialog(MapView mapView, Context plugin,
            boolean bPersistent) {
        this(mapView, mapView.getContext(), plugin, bPersistent);
    }

    /**
     * Instantiates a Plugin Context friendly variant of the TileButtonDialog.
     * @param mapView the mapView for using the TileButtonDialog
     * @param plugin the plugin context.
     */
    public TileButtonDialog(MapView mapView, Context context, Context plugin,
            boolean bPersistent) {
        _mapView = mapView;
        _context = context;
        _plugin = plugin;
        _inflater = LayoutInflater.from(_context);
        _bPersistent = bPersistent;
        _dp = _context.getResources().getDisplayMetrics().density;
    }

    /**
     * Creates a button for adding to the TileButton dialog.   The return is the id that 
     * should be used to distinguish this button from other Buttons. 
     */
    public TileButton createButton(Drawable icon, String text) {
        return new TileButton(icon, text);
    }

    /**
     * Add a button to the dialog
     *
     * @param icon Icon drawable
     * @param text Text string (null to hide)
     * @return Newly added tile button
     */
    public TileButton addButton(Drawable icon, String text) {
        TileButton tb = createButton(icon, text);
        addButton(tb);
        return tb;
    }

    /**
     * Add a button to the dialog
     *
     * @param iconId Icon drawable resource ID (plugin context)
     * @param textId Text string resource ID (plugin context, 0 to hide)
     * @return Newly added tile button
     */
    public TileButton addButton(int iconId, int textId) {
        return addButton(_plugin.getDrawable(iconId),
                textId != 0 ? _plugin.getString(textId) : null);
    }

    synchronized public void addButton(TileButton addButton) {
        if (addButton != null)
            _buttons.add(addButton);
    }

    synchronized public void removeButton(TileButton removeButton) {
        if (removeButton != null)
            _buttons.remove(removeButton);
    }

    synchronized public void setOnClickListener(
            DialogInterface.OnClickListener onClick) {
        _onClick = onClick;
    }

    synchronized public void setIcon(Drawable icon) {
        _icon = icon;
        if (_dialog != null)
            _dialog.setIcon(_icon);
    }

    public void setIcon(int iconId) {
        setIcon(_plugin.getDrawable(iconId));
    }

    synchronized public void show(String title, String message,
            boolean showCancel) {
        show(title, message, showCancel, null);
    }

    synchronized public void show(String title, String message,
            boolean showCancel, String cancelTitle) {
        dismiss();
        if (_buttons.size() == 0) {
            Toast.makeText(_context,
                    "no options available",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        View v = _inflater.inflate(R.layout.tile_button_dialog,
                _mapView, false);

        TextView msg = v.findViewById(R.id.message);
        if (!FileSystemUtils.isEmpty(message))
            msg.setText(message);
        else
            msg.setVisibility(View.GONE);

        LinearLayout cont = v.findViewById(R.id.container);

        DisplayMetrics metrics = _context.getResources().getDisplayMetrics();
        float tileWidth = _dp * 100f;
        float tileHeight = _dp * 80f;
        int screenWidth = metrics.widthPixels;
        int maxCols = Math.max(1, (int) (screenWidth / tileWidth));
        LinearLayout horiz = null;
        for (int i = 0; i < _buttons.size(); i++) {
            TileButton btn = _buttons.get(i);
            if (horiz == null || (i % maxCols) == 0) {
                horiz = new LinearLayout(_plugin);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.gravity = Gravity.CENTER_VERTICAL;
                horiz.setLayoutParams(lp);
                horiz.setOrientation(LinearLayout.HORIZONTAL);
                cont.addView(horiz);
            }
            View btnView = btn.view;
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    (int) tileWidth, (int) tileHeight);
            LinearLayout parent = (LinearLayout) btnView.getParent();
            if (parent != null)
                parent.removeView(btnView);
            horiz.addView(btnView);
            btn.view.setLayoutParams(lp);
            if (btn.internalClickListener == null)
                btn.setOnClickListener(this);
        }

        AlertDialog.Builder b = new AlertDialog.Builder(_context);
        if (_icon != null)
            b.setIcon(_icon);
        if (!FileSystemUtils.isEmpty(title))
            b.setTitle(title);
        b.setView(v);
        if (showCancel)
            b.setNegativeButton(
                    FileSystemUtils.isEmpty(cancelTitle)
                            ? _context.getString(R.string.cancel)
                            : cancelTitle,
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface,
                                int i) {
                            if (_onClick != null) {
                                _onClick.onClick(_dialog, WHICH_CANCEL);
                            }
                        }
                    });
        _dialog = b.create();
        _dialog.show();
        _dialog.setOnDismissListener(this);

        // Dialog window doesn't size correctly for some reason...
        forceWrapContent(cont);
    }

    public void show(String title, String message) {
        show(title, message, false);
    }

    public void show(int titleId, int msgId, boolean showCancel) {
        show(titleId != 0 ? _plugin.getString(titleId) : null,
                msgId != 0 ? _plugin.getString(msgId) : null, showCancel);
    }

    public void show(int titleId, int msgId) {
        show(titleId, msgId, false);
    }

    public void show(int titleId, boolean showCancel) {
        show(titleId, 0, showCancel);
    }

    public void show(int titleId) {
        show(titleId, 0, false);
    }

    @Override
    public synchronized void onClick(View v) {
        if (_onClick != null && v.getTag() instanceof TileButton) {
            TileButton btn = (TileButton) v.getTag();
            _onClick.onClick(_dialog, _buttons.indexOf(btn));
        }
        dismiss();
    }

    // Forces the alert dialog to wrap its content properly
    // See https://stackoverflow.com/questions/14907104/
    private void forceWrapContent(View v) {
        View current = v;

        // Travel up the tree until fail, modifying the LayoutParams
        do {
            // Get the parent
            ViewParent parent = current.getParent();

            // Check if the parent exists
            if (parent != null) {
                // Get the view
                try {
                    current = (View) parent;
                } catch (ClassCastException e) {
                    // This will happen when at the top view, it cannot be cast to a View
                    break;
                }

                // Modify the layout
                current.getLayoutParams().width = ViewGroup.LayoutParams.WRAP_CONTENT;
            }
        } while (current.getParent() != null);

        // Request a layout to be re-done
        current.requestLayout();
    }

    public boolean dismiss() {
        if (_bPersistent) {
            return false;
        }

        if (_dialog != null)
            _dialog.dismiss();
        _dialog = null;
        return true;
    }

    @Override
    public void onDismiss(DialogInterface dialog) {
        _dialog = null;
    }

    public class TileButton {
        final TileButtonView view;
        private View.OnClickListener internalClickListener;

        TileButton(final Drawable icon, final String text) {
            view = (TileButtonView) _inflater.inflate(R.layout.tile_button,
                    _mapView, false);
            view.setIcon(icon);
            view.setText(text);
            view.setTag(this);
        }

        synchronized public void setOnClickListener(
                final View.OnClickListener ocl) {
            view.setOnClickListener(
                    internalClickListener = new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            if (!dismiss())
                                view.setSelected(true);
                            if (ocl != null)
                                ocl.onClick(v);
                        }
                    });
        }
    }
}

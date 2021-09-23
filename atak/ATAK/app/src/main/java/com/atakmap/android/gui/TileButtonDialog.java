
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
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.graphics.drawable.Drawable;

import com.atakmap.android.maps.MapView;
import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;

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
    private TextView _messageTxt;
    private DialogInterface.OnClickListener _onClick;
    private DialogInterface.OnCancelListener _onCancel;
    private Drawable _icon;
    private String _title;
    private String _message, _cancelText;
    private View _customView;
    private ViewGroup _customViewContainer;

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
     * @param mapView Map view instance
     * @param context Activity context (usually {@link MapView#getContext()})
     * @param plugin Plugin context used for looking up string and icon resources
     * @param bPersistent True if the dialog should stay open when a tile
     *                   button is pressed. The buttons act as toggles instead.
     */
    public TileButtonDialog(MapView mapView, Context context, Context plugin,
            boolean bPersistent) {
        _mapView = mapView;
        _context = context;
        _plugin = plugin;
        _inflater = LayoutInflater.from(_context);
        _bPersistent = bPersistent;
        _dp = _context.getResources().getDisplayMetrics().density;
        _cancelText = _context.getString(R.string.cancel);
    }

    /**
     * Set the title of this dialog
     * @param title Title
     * @return Tile button dialog
     */
    public synchronized TileButtonDialog setTitle(String title) {
        _title = title;
        if (_dialog != null)
            _dialog.setTitle(title);
        return this;
    }

    public TileButtonDialog setTitle(int titleId, Object... args) {
        return setTitle(_plugin.getString(titleId, args));
    }

    /**
     * Set the message displayed above the buttons
     * @param message Message
     * @return Tile button dialog
     */
    public synchronized TileButtonDialog setMessage(String message) {
        _message = message;
        if (_messageTxt != null) {
            if (!FileSystemUtils.isEmpty(_message)) {
                _messageTxt.setText(_message);
                _messageTxt.setVisibility(View.VISIBLE);
            } else
                _messageTxt.setVisibility(View.GONE);
        }
        return this;
    }

    public TileButtonDialog setMessage(int msgId, Object... args) {
        return setMessage(_plugin.getString(msgId, args));
    }

    /**
     * Set the cancel button text
     * @param txt Cancel text
     * @return Tile button dialog
     */
    public TileButtonDialog setCancelText(String txt) {
        _cancelText = txt;
        if (_dialog != null)
            _dialog.getButton(DialogInterface.BUTTON_NEGATIVE)
                    .setText(txt);
        return this;
    }

    public TileButtonDialog setCancelText(int txtId, Object... args) {
        return setCancelText(_plugin.getString(txtId, args));
    }

    /**
     * Set a custom view to be displayed between the message and buttons
     * @param v View
     * @return Tile button dialog
     */
    public TileButtonDialog setCustomView(View v) {
        _customView = v;
        if (_customViewContainer != null) {
            _customViewContainer.removeAllViews();
            if (_customView != null)
                _customViewContainer.addView(_customView);
        }
        return this;
    }

    /**
     * Creates a button for adding to the TileButton dialog
     * @param icon Icon drawable
     * @param text Button text
     * @return The newly created tile button
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

    public synchronized TileButtonDialog addButton(TileButton addButton) {
        if (addButton != null)
            _buttons.add(addButton);
        return this;
    }

    public synchronized void removeButton(TileButton removeButton) {
        if (removeButton != null)
            _buttons.remove(removeButton);
    }

    public synchronized TileButtonDialog setOnClickListener(
            DialogInterface.OnClickListener onClick) {
        _onClick = onClick;
        return this;
    }

    public synchronized TileButtonDialog setOnCancelListener(
            DialogInterface.OnCancelListener onCancel) {
        _onCancel = onCancel;
        return this;
    }

    public synchronized TileButtonDialog setIcon(Drawable icon) {
        _icon = icon;
        if (_dialog != null)
            _dialog.setIcon(_icon);
        return this;
    }

    public TileButtonDialog setIcon(int iconId) {
        return setIcon(_plugin.getDrawable(iconId));
    }

    public synchronized void show(boolean showCancel) {
        dismiss();
        if (_buttons.size() == 0) {
            Toast.makeText(_context, R.string.no_available_options,
                    Toast.LENGTH_SHORT).show();
            return;
        }
        View v = _inflater.inflate(R.layout.tile_button_dialog,
                _mapView, false);

        _messageTxt = v.findViewById(R.id.message);
        _customViewContainer = v.findViewById(R.id.custom_container);

        setMessage(_message);
        setCustomView(_customView);

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
        if (!FileSystemUtils.isEmpty(_title))
            b.setTitle(_title);
        b.setView(v);
        if (showCancel) {
            b.setNegativeButton(_cancelText,
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface d, int w) {
                            if (_onCancel != null)
                                _onCancel.onCancel(d);
                            else if (_onClick != null)
                                _onClick.onClick(_dialog, WHICH_CANCEL);
                        }
                    });
        }
        _dialog = b.create();
        try {
            _dialog.show();
            _dialog.setOnDismissListener(this);
            _dialog.setOnCancelListener(_onCancel);
            // Dialog window doesn't size correctly for some reason...
            forceWrapContent(cont);

        } catch (Exception e) {
            Log.e("TileButtonDialog", "could not display the button dialog", e);
        }

    }

    public void show() {
        show(false);
    }

    // XXX - Really no reason to have all these overloaded methods as opposed
    // to just using setTitle/setMessage/etc. methods

    public void show(String title, String message,
            boolean showCancel, String cancelTitle) {
        setTitle(title);
        setMessage(message);
        setCancelText(cancelTitle);
        show(showCancel);
    }

    public void show(String title, String message, boolean showCancel) {
        show(title, message, showCancel, null);
    }

    public void show(String title, String message) {
        show(title, message, false);
    }

    public void show(int titleId, int msgId, boolean showCancel) {
        setTitle(titleId);
        setMessage(msgId);
        show(showCancel);
    }

    public void show(int titleId, int msgId) {
        show(titleId, msgId, false);
    }

    public void show(int titleId, boolean showCancel) {
        setTitle(titleId);
        show(showCancel);
    }

    public void show(int titleId) {
        show(titleId, false);
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

                // Modify the layout so it wraps content width and is centered
                // horizontally on the screen
                ViewGroup.LayoutParams lp = current.getLayoutParams();
                if (lp instanceof FrameLayout.LayoutParams)
                    ((FrameLayout.LayoutParams) lp).gravity = Gravity.CENTER_HORIZONTAL;
                if (lp instanceof LinearLayout.LayoutParams)
                    ((LinearLayout.LayoutParams) lp).gravity = Gravity.CENTER_HORIZONTAL;
                lp.width = ViewGroup.LayoutParams.WRAP_CONTENT;
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
        private View.OnLongClickListener internalLongClickListener;

        TileButton(final Drawable icon, final String text) {
            view = (TileButtonView) _inflater.inflate(R.layout.tile_button,
                    _mapView, false);
            view.setIcon(icon);
            view.setText(text);
            view.setTag(this);
        }

        /**
         * Set whether the tile button is highlighted
         * @param selected True to select/highlight
         */
        public void setSelected(boolean selected) {
            view.setSelected(selected);
        }

        public synchronized void setOnClickListener(
                final View.OnClickListener ocl) {
            view.setOnClickListener(
                    internalClickListener = new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            if (!dismiss())
                                setSelected(true);
                            if (ocl != null)
                                ocl.onClick(v);
                        }
                    });
        }

        /**
         * Registers a long click listener on the button. Only one may be registered--any previously
         * registered listener will be replaced.
         * @param olcl The listener to register.
         */
        public synchronized void setOnLongClickListener(
                final View.OnLongClickListener olcl) {
            view.setOnLongClickListener(
                    internalLongClickListener = new View.OnLongClickListener() {

                        @Override
                        public boolean onLongClick(View v) {
                            if (olcl != null) {
                                return olcl.onLongClick(v);
                            }
                            return false;
                        }
                    });
        }
    }
}

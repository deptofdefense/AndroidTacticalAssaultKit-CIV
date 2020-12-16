
package com.atakmap.android.drawing.details;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Context;
import android.content.Intent;

import com.atakmap.android.cotdetails.extras.ExtraDetailsLayout;
import com.atakmap.android.drawing.DrawingPreferences;
import com.atakmap.android.gui.RangeEntryDialog;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.RectShape;
import android.graphics.drawable.shapes.Shape;
import android.os.Bundle;
import android.text.Editable;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;

import com.atakmap.android.contact.ContactPresenceDropdown;
import com.atakmap.android.dropdown.DropDownReceiver;
import com.atakmap.android.gui.ColorPalette;
import com.atakmap.android.gui.ColorPalette.OnColorSelectedListener;
import com.atakmap.android.gui.RangeAndBearingTableHandler;
import com.atakmap.android.hashtags.view.HashtagEditText;
import com.atakmap.android.hashtags.view.RemarksLayout;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.preference.UnitPreferences;
import com.atakmap.android.toolbar.Tool;
import com.atakmap.android.toolbar.ToolListener;
import com.atakmap.android.toolbar.ToolManagerBroadcastReceiver;
import com.atakmap.android.util.AfterTextChangedWatcher;
import com.atakmap.android.util.Undoable;
import com.atakmap.app.R;
import com.atakmap.coremap.conversions.CoordinateFormat;
import com.atakmap.coremap.conversions.Span;
import com.atakmap.coremap.conversions.SpanUtilities;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;

/**
 * Generic details view for a map item
 */
public abstract class GenericDetailsView extends RelativeLayout implements
        OnSharedPreferenceChangeListener, ToolListener {

    public static final String TAG = "GenericDetailsView";

    protected MapView _mapView;
    protected MapItem _item;

    protected DropDownReceiver dropDown = null;
    protected String _prevName, _prevRemarks;
    protected int _alpha;
    protected CoordinateFormat _cFormat = CoordinateFormat.MGRS;

    protected EditText _nameEdit;
    protected RemarksLayout _remarksLayout;
    protected ExtraDetailsLayout _extrasLayout;
    protected View _noGps;
    protected RangeAndBearingTableHandler rabtable;
    protected SeekBar _transSeek;
    protected SeekBar _thickSeek;
    protected Button _centerButton, _heightButton;
    protected ImageButton _colorButton;
    protected CheckBox _showLabels;
    protected final UnitPreferences _unitPrefs;
    protected final DrawingPreferences _drawPrefs;

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        //  Add prompts for any edit text inputs - prevents the keyboard overlap issue
        addEditTextPrompts(this);

        // Title and remarks
        _nameEdit = findViewById(R.id.nameEdit);
        if (_nameEdit != null) {
            _nameEdit.addTextChangedListener(new AfterTextChangedWatcher() {
                @Override
                public void afterTextChanged(Editable s) {
                    if (_item == null || _mapView == null)
                        return;
                    _item.setTitle(_nameEdit.getText().toString());
                    _item.refresh(_mapView.getMapEventDispatcher(), null,
                            GenericDetailsView.class);
                }
            });
        }
        _remarksLayout = findViewById(R.id.remarksLayout);
        if (_remarksLayout != null) {
            _remarksLayout
                    .addTextChangedListener(new AfterTextChangedWatcher() {
                        @Override
                        public void afterTextChanged(Editable s) {
                            if (_item == null || _mapView == null)
                                return;
                            _item.setMetaString("remarks",
                                    _remarksLayout.getText());
                        }
                    });
        }

        // Show labels checkbox used by most shapes
        _showLabels = findViewById(R.id.labelVisibilityCB);
        if (_showLabels != null) {
            _showLabels
                    .setOnCheckedChangeListener(new OnCheckedChangeListener() {
                        @Override
                        public void onCheckedChanged(CompoundButton buttonView,
                                boolean isChecked) {
                            if (_item == null || _mapView == null)
                                return;
                            _item.toggleMetaData("labels_on", isChecked);
                        }
                    });
        }

        _extrasLayout = findViewById(R.id.extrasLayout);
    }

    @Override
    public void onSharedPreferenceChanged(
            final SharedPreferences sp,
            final String key) {

        if (key.equals(UnitPreferences.COORD_FMT))
            _cFormat = _unitPrefs.getCoordinateFormat();
    }

    /****************************** CONSTRUCTOR ****************************/

    public GenericDetailsView(final Context context) {
        this(context, null);
    }

    public GenericDetailsView(final Context context, final AttributeSet attrs) {
        super(context, attrs);
        MapView mv = MapView.getMapView();
        _unitPrefs = new UnitPreferences(mv);
        _drawPrefs = new DrawingPreferences(mv);
        if (!isInEditMode()) {
            ToolManagerBroadcastReceiver.getInstance().registerListener(this);
            _unitPrefs.registerListener(this);
            _cFormat = _unitPrefs.getCoordinateFormat();
        }
    }

    /****************************** PUBLIC METHODS ****************************/

    public void setDropDownMapReceiver(final DropDownReceiver r) {
        dropDown = r;
        if (dropDown != null) {
            dropDown.setRetain(true);
        }
    }

    /****************************** PRIVATE METHODS ****************************/

    protected ShapeDrawable updateColorButtonDrawable() {
        Shape rect = new RectShape();
        rect.resize(50, 50);
        ShapeDrawable color = new ShapeDrawable();
        color.setBounds(0, 0, 50, 50);
        color.setIntrinsicHeight(50);
        color.setIntrinsicWidth(50);
        color.setShape(rect);
        return color;
    }

    protected void promptColor(int currentColor) {
        AlertDialog.Builder b = new AlertDialog.Builder(getContext());
        b.setTitle(R.string.select_a_color);
        ColorPalette palette = new ColorPalette(getContext(), currentColor);
        b.setView(palette);
        final AlertDialog d = b.show();
        OnColorSelectedListener l = new OnColorSelectedListener() {
            @Override
            public void onColorSelected(int color, String label) {
                d.dismiss();
                _onColorSelected(color, label);
            }
        };
        palette.setOnColorSelectedListener(l);
    }

    protected void _onColorSelected() {
        int color = Color.WHITE;
        Drawable dr = _colorButton.getDrawable();
        if (dr instanceof ShapeDrawable) {
            ShapeDrawable drawable = (ShapeDrawable) dr;
            color = drawable.getPaint().getColor();
        }
        promptColor(color);
    }

    /**
     * Set the map item associated with this details view
     * @param mapView The map view
     * @param item Map item to set
     * @return True if this item is acceptable by this details view
     */
    public boolean setItem(MapView mapView, MapItem item) {
        _mapView = mapView;
        _item = item;
        if (_showLabels != null)
            _showLabels.setChecked(_item.hasMetaValue("labels_on"));
        if (_extrasLayout != null)
            _extrasLayout.setItem(item);
        return item != null;
    }

    /**
     * Called when the detail screen is made visible.
     */
    public abstract void refresh();

    protected abstract void _onColorSelected(int color, String label);

    protected void _onHeightSelected() {
        // Map item may not have a modifiable height
    }

    /**
     * Height has been selected
     * @param heightM The new height in meters (internal value)
     * @param unit The new height unit
     * @param heightU The new height in preferred units (display value)
     */
    protected void heightSelected(double heightM, Span unit, double heightU) {
        // Map item may not have a modifiable height
    }

    protected void createHeightDialog(final MapItem item, int titleId,
            Span[] acceptableValues) {
        if (item == null)
            return;
        double heightM = item.getHeight();
        Span heightUnit = getUnitSpan(item);
        if (acceptableValues == null) {
            acceptableValues = Span.values();
        }
        new RangeEntryDialog(dropDown.getMapView()).show(titleId, heightM,
                heightUnit, new RangeEntryDialog.Callback() {
                    @Override
                    public void onSetValue(double valueM, Span unit) {
                        heightSelected(valueM, unit, SpanUtilities.convert(
                                valueM, Span.METER, unit));
                    }
                }, acceptableValues);
    }

    /**
     * Begin editing mode on this item
     */
    public void startEditingMode() {
        String toolId = getEditTool();
        if (_item == null || FileSystemUtils.isEmpty(toolId))
            return;

        // Starts the tool immediately instead of using an intent
        Bundle extras = new Bundle();
        extras.putString("uid", _item.getUID());
        extras.putBoolean("ignoreToolbar", true);
        extras.putBoolean("scaleToFit", false);
        ToolManagerBroadcastReceiver.getInstance().startTool(toolId, extras);
    }

    /**
     * End editing mode on this item
     */
    public void endEditingMode() {
        if (editToolActive())
            ToolManagerBroadcastReceiver.getInstance().endCurrentTool();
    }

    /**
     * Check if the edit tool this details view uses is active
     * @return True if active
     */
    protected boolean editToolActive() {
        return getActiveEditTool() != null;
    }

    @SuppressWarnings("unchecked")
    protected <T extends Tool> T getActiveEditTool() {
        String toolID = getEditTool();
        if (FileSystemUtils.isEmpty(toolID))
            return null;
        Tool active = ToolManagerBroadcastReceiver.getInstance()
                .getActiveTool();
        return active != null && active.getIdentifier().equals(toolID)
                ? (T) active
                : null;
    }

    /**
     * Undo an edit by the current tool
     */
    protected void undoToolEdit() {
        Tool t = getActiveEditTool();
        if (t instanceof Undoable)
            ((Undoable) t).undo();
    }

    /**
     * Get the edit tool used by this details view
     * @return Edit tool ID or null if N/A
     */
    protected String getEditTool() {
        return null;
    }

    /**
     * The edit tool associated with this details view has been started
     * @param extras Extras bundle
     */
    protected void onEditToolBegin(Bundle extras) {
        // Show your edit buttons here
    }

    /**
     * The edit tool associated with this details view has ended
     */
    protected void onEditToolEnd() {
        // Hide your edit buttons here
    }

    @Override
    public void onToolBegin(Tool tool, Bundle extras) {
        if (FileSystemUtils.isEquals(tool.getIdentifier(), getEditTool()))
            onEditToolBegin(extras);
    }

    @Override
    public void onToolEnded(Tool tool) {
        if (FileSystemUtils.isEquals(tool.getIdentifier(), getEditTool()))
            onEditToolEnd();
    }

    public void onClose() {
        _unitPrefs.unregisterListener(this);
        ToolManagerBroadcastReceiver.getInstance().unregisterListener(this);
    }

    protected void sendSelected(final String uid) {

        // Make sure the object is shared since the user hit "Send".
        MapItem item = dropDown.getMapView().getRootGroup()
                .deepFindItem("uid", uid);
        if (item != null) {
            item.setMetaBoolean("shared", true);
        } else {
            Log.d(TAG, "cannot send item that is missing: " + uid);
            return;
        }

        Intent contactList = new Intent();
        contactList.setAction(ContactPresenceDropdown.SEND_LIST);
        contactList.putExtra("targetUID", uid);
        AtakBroadcast.getInstance().sendBroadcast(contactList);
    }

    protected static Span getUnitSpan(MapItem item) {
        if (item != null) {
            Span unit = Span.findFromValue(item.getMetaInteger("height_unit",
                    Span.FOOT.getValue()));
            if (unit != null)
                return unit;
        }
        return Span.FOOT;
    }

    /**
     * Add prompts for all EditText views in a layout
     * Resolves issue where the EditText is covered by the keyboard (ATAK-8700)
     * @param v View group to process
     */
    public static void addEditTextPrompts(ViewGroup v) {
        // Prepare edit text prompts
        int childCount = v.getChildCount();
        for (int i = 0; i < childCount; i++) {
            View child = v.getChildAt(i);
            if (child instanceof ViewGroup)
                addEditTextPrompts((ViewGroup) child);
            else if (child instanceof EditText) {
                final EditText et = (EditText) child;
                CharSequence desc = et.getContentDescription();
                if (desc != null && desc.length() > 0) {
                    et.setFocusableInTouchMode(true);
                    et.setFocusable(false);
                    et.setOnClickListener(new OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            promptEditText(et);
                        }
                    });
                }
            }
        }
    }

    /**
     * Shows an alert dialog for entering into an EditText
     * @param et Edit text (must contain a valid contentDescription)
     * @return True if prompt is shown, false if failed
     */
    public static boolean promptEditText(final EditText et) {
        MapView mv = MapView.getMapView();
        if (mv == null)
            return false;

        int inputFlags = et.getInputType();
        CharSequence desc = et.getContentDescription();
        if (desc == null || desc.length() <= 0 || inputFlags == 0
                || et instanceof HashtagEditText)
            return false;

        boolean singleLine = (inputFlags
                & EditorInfo.TYPE_TEXT_FLAG_MULTI_LINE) != EditorInfo.TYPE_TEXT_FLAG_MULTI_LINE;

        final EditText input = new EditText(et.getContext());
        input.setText(et.getText());
        input.setHint(et.getHint());
        input.setInputType(inputFlags);
        input.setSelection(et.getText().length());
        input.setSingleLine(singleLine);

        AlertDialog.Builder b = new AlertDialog.Builder(mv.getContext());
        b.setTitle(desc);
        b.setView(input);
        b.setPositiveButton(R.string.done,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        et.setText(input.getText());
                    }
                });
        b.setNegativeButton(R.string.cancel, null);
        final AlertDialog d = b.create();
        if (d.getWindow() != null)
            d.getWindow().setSoftInputMode(
                    WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
                            | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN);
        d.show();
        input.setOnEditorActionListener(new OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int act, KeyEvent evt) {
                if (act == EditorInfo.IME_ACTION_DONE)
                    d.getButton(DialogInterface.BUTTON_POSITIVE).performClick();
                return false;
            }
        });
        return true;
    }

    /**
     * Given a button from one of the detail views, set up the alert dialog that allows
     * one to pick the appropriate format of the area.
     * @param b button to set up.
     * @param r action to take when the selection is make
     */
    protected void setupAreaButton(final Button b, final Runnable r) {
        b.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                AlertDialog.Builder builder = new AlertDialog.Builder(
                        getContext());
                builder.setTitle("Select Area Display");
                // TODO - extract as a string array
                String[] choices = new String[] {
                        "Feet/Miles", "Meters/Kilometers", "Nautical Miles",
                        "Acres"
                };
                builder.setItems(choices,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog,
                                    int which) {
                                _unitPrefs.setAreaSystem(which);
                                if (r != null)
                                    r.run();
                            }
                        });
                AlertDialog dialog = builder.create();
                dialog.show();
            }
        });
    }
}


package com.atakmap.android.gui;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Color;

import androidx.annotation.ColorInt;
import androidx.annotation.LayoutRes;
import androidx.annotation.Nullable;

import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.GridView;
import android.widget.LinearLayout;

import com.atakmap.android.util.SimpleSeekBarChangeListener;
import com.atakmap.annotations.DeprecatedApi;
import com.atakmap.annotations.ModifierApi;
import com.atakmap.app.R;

/**
 * A GridView that displays various colors for the user to choose.
 * This view is typically displayed within an {@link AlertDialog}. When the
 * {@link OnColorSelectedListener} callback is called the dialog should be
 * dismissed.
 */
public class ColorPalette extends LinearLayout {

    /**
     * Note this pallet is referenced by other sections of code
     */
    public static final int COLOR1 = Color.WHITE, COLOR2 = Color.YELLOW,
            COLOR3 = 0xffff7700, COLOR4 = Color.MAGENTA, COLOR5 = Color.RED,
            COLOR6 = 0xff7f0000, COLOR7 = 0xff7f007f, COLOR8 = 0xff00007f,
            COLOR9 = Color.BLUE, COLOR10 = Color.CYAN, COLOR11 = 0xff007f7f,
            COLOR12 = Color.GREEN, COLOR13 = 0xff007f00, COLOR14 = 0xff777777,
            COLOR15 = 0xff000000;

    /** Array of colors to be used for display */
    // XXX If someone changes these colors make sure you format them so they are
    // in an order that goes from
    // light to dark, dark to light, etc.
    protected static final Integer[] colorArray = new Integer[] {
            COLOR1, COLOR2, COLOR3, COLOR4, COLOR5,
            COLOR6, COLOR7, COLOR8, COLOR9, COLOR10,
            COLOR11, COLOR12, COLOR13, COLOR14, COLOR15
    };

    protected static final String[] colorLabelArray = new String[] {
            "White", "Yellow", "Orange", "Magenta", "Red",
            "Brown", "Purple", "Navy", "Blue", "Cyan",
            "Turqoise", "Green", "Forest", "Gray", "Black"
    };

    /**
     * Callback to specify when a color has been selected in single-color
     * select mode. When selecting both a stroke and fill color this callback
     * is NOT fired. You must pull from {@link #getStrokeColor()} and
     * {@link #getFillColor()} instead.
     */
    public interface OnColorSelectedListener {
        void onColorSelected(int color, String label);
    }

    private GridView _grid;
    private RadioGroup _selector;
    private ShapeColorButton _preview;
    private View _alphaLayout;
    private SeekBar _alphaBar;

    private int _strokeColor = Color.WHITE, _fillColor = 0;
    private boolean _showFill = false;
    @ModifierApi(since = "4.5", target = "4.8", modifiers = {
            "private"
    })
    protected OnColorSelectedListener _listener;

    public ColorPalette(Context context) {
        this(context, null);
    }

    public ColorPalette(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ColorPalette(Context context, @Nullable AttributeSet attrs,
            int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public ColorPalette(Context context, @Nullable AttributeSet attrs,
            int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);

        // Read attributes
        final TypedArray a = context.obtainStyledAttributes(
                attrs, R.styleable.ColorPalette, defStyleAttr, defStyleRes);

        int layoutId = a.getInt(R.styleable.ColorPalette_layoutId,
                R.layout.color_palette);
        boolean alpha = a.getBoolean(R.styleable.ColorPalette_showAlpha, false);
        boolean fill = a.getBoolean(R.styleable.ColorPalette_showFill, false);

        a.recycle();

        // Setup based on attribute values
        setLayout(layoutId);
        setShowAlpha(alpha);
        setShowFill(fill);
    }

    /**
     * Set the layout resource for this palette
     * The layout MUST contain the accompanying resource IDs specified in
     * color_palette.xml. Otherwise this will throw an exception.
     * @param layoutId Layout ID
     */
    private void setLayout(@LayoutRes int layoutId) {
        View root = LayoutInflater.from(getContext())
                .inflate(layoutId, this, false);
        _selector = root.findViewById(R.id.color_rg);
        _preview = root.findViewById(R.id.color_preview);
        _grid = root.findViewById(R.id.color_grid);
        _alphaLayout = root.findViewById(R.id.alpha_layout);
        _alphaBar = root.findViewById(R.id.alpha_bar);
        Button customBtn = root.findViewById(R.id.custom_color);

        // Stroke or fill selector
        _selector.setOnCheckedChangeListener(
                new RadioGroup.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(RadioGroup group,
                            int checkedId) {
                        if (checkedId == R.id.fill_color
                                || checkedId == R.id.both_colors) {
                            _alphaBar.setEnabled(true);
                            _alphaBar.setProgress(Color.alpha(_fillColor));
                            if (checkedId == R.id.both_colors)
                                selectColor(_strokeColor);
                        } else {
                            _alphaBar.setEnabled(false);
                            _alphaBar.setProgress(255);
                        }
                    }
                });

        _alphaBar.setOnSeekBarChangeListener(new SimpleSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar sb, int p, boolean user) {
                if (user)
                    selectColor(settingFill() ? _fillColor : _strokeColor);
            }
        });

        // Color grid
        ArrayAdapter<Integer> colorAdapter = new ArrayAdapter<Integer>(
                getContext(),
                android.R.layout.simple_list_item_1,
                ColorPalette.colorArray) {
            @Override
            public View getView(int position, View row, ViewGroup parent) {
                final ColorButton btn;
                if (!(row instanceof ColorButton)) {
                    LayoutInflater inf = LayoutInflater.from(getContext());
                    btn = (ColorButton) inf.inflate(
                            R.layout.color_palette_button, parent, false);
                } else
                    btn = (ColorButton) row;
                btn.setColor(ColorPalette.colorArray[position]);
                btn.setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        selectColor(((ColorButton) v).getColor());
                    }
                });
                return btn;
            }
        };
        _grid.setAdapter(colorAdapter);

        // Custom color button
        customBtn.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View arg0) {
                showCustomColorDialog();
            }
        });

        // Set the main layout
        removeAllViews();
        addView(root);
    }

    /**
     * Set whether to display the alpha transparency bar
     * @param showAlpha True to show alpha
     */
    public void setShowAlpha(boolean showAlpha) {
        _preview.setShowAlpha(showAlpha);
        _alphaLayout.setVisibility(showAlpha ? VISIBLE : GONE);
    }

    /**
     * Set whether to allow the user to select a differing fill color
     * @param showFill True to allow the user to select a fill color
     */
    public void setShowFill(boolean showFill) {
        _showFill = showFill;
        _preview.setShowFill(_showFill);
        _selector.setVisibility(_showFill ? VISIBLE : GONE);
    }

    /**
     * Set the current color for this palette
     * When {@link #setShowFill(boolean)} is true this sets both the stroke
     * and fill color.
     * @param color Color
     */
    public void setColor(@ColorInt int color) {
        setColors(color, color);
    }

    /**
     * Set the stroke color displayed in the preview
     * @param strokeColor Stroke color
     */
    public void setStrokeColor(@ColorInt int strokeColor) {
        _strokeColor = strokeColor;
        _preview.setStrokeColor(strokeColor);
    }

    /**
     * Set the fill color displayed in the preview
     * @param fillColor Fill color
     */
    public void setFillColor(@ColorInt int fillColor) {
        _fillColor = fillColor;
        _preview.setFillColor(_fillColor);
    }

    /**
     * Set the colors displayed in the preview
     * @param strokeColor Stroke color
     * @param fillColor Fill color
     */
    public void setColors(@ColorInt int strokeColor, @ColorInt int fillColor) {
        _strokeColor = strokeColor;
        _fillColor = fillColor;
        _preview.setColors(strokeColor, fillColor);
        if ((_strokeColor & 0xFFFFFF) == (_fillColor & 0xFFFFFF))
            _selector.check(R.id.both_colors);
        else
            _selector.check(R.id.stroke_color);
    }

    /**
     * Get the currently selected stroke color
     * @return Stroke color
     */
    @ColorInt
    public int getStrokeColor() {
        return _strokeColor;
    }

    /**
     * Get the currently selected fill color
     * @return Fill color
     */
    @ColorInt
    public int getFillColor() {
        return _fillColor;
    }

    @ColorInt
    public int getColor() {
        return settingFill() ? _fillColor : _strokeColor;
    }

    /**
     * Set the {@link OnColorSelectedListener} to be called back when a stroke
     * color is selected while {@link #setShowFill(boolean)} is <code>false</code>.
     * @param listener Listener
     */
    public void setOnColorSelectedListener(OnColorSelectedListener listener) {
        _listener = listener;
    }

    /**
     * A color has been selected from the grid
     * @param color Color that was selected
     */
    private void selectColor(@ColorInt int color) {
        if (!_showFill) {
            _strokeColor = color;
            if (_listener != null)
                _listener.onColorSelected(color, "");
            return;
        }
        if (settingFill() || settingBoth()) {
            _fillColor = (color & 0xFFFFFF) + (_alphaBar.getProgress() << 24);
            _preview.setFillColor(_fillColor);
        }
        if (!settingFill()) {
            _strokeColor = (color & 0xFFFFFF) + 0xFF000000;
            _preview.setStrokeColor(_strokeColor);
        }
    }

    /**
     * Check if we're setting fill color
     * @return True if seting fill color
     */
    private boolean settingFill() {
        return _selector.getCheckedRadioButtonId() == R.id.fill_color;
    }

    /**
     * Check if we're setting both colors
     * @return True if setting both colors
     */
    private boolean settingBoth() {
        return _selector.getCheckedRadioButtonId() == R.id.both_colors;
    }

    /**
     * Show the custom color picker dialog
     */
    private void showCustomColorDialog() {
        final ColorPicker picker = new ColorPicker(getContext(),
                getColor());
        AlertDialog.Builder b = new AlertDialog.Builder(getContext());
        b.setTitle(R.string.custom_color_dialog);
        b.setView(picker);
        b.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface d, int w) {
                selectColor(picker.getColor());
            }
        });
        b.setNegativeButton(R.string.cancel, null);
        fixWindowSize(b.show());
    }

    /**
     * Fix the dialog window so it doesn't take up the entire screen
     * Must be called after show()
     * @param dialog Alert dialog
     */
    private void fixWindowSize(AlertDialog dialog) {
        Window w = dialog.getWindow();
        if (w == null || !dialog.isShowing())
            return;

        Resources res = getContext().getResources();
        DisplayMetrics dm = res.getDisplayMetrics();
        boolean isPortrait = dm.widthPixels < dm.heightPixels;
        boolean isTablet = res.getBoolean(R.bool.isTablet);
        boolean smallWindow = isPortrait && !isTablet;
        _grid.setNumColumns(smallWindow ? 4 : 5);

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams();
        lp.copyFrom(w.getAttributes());
        lp.width = (int) ((smallWindow ? 320 : 400) * dm.density);
        lp.height = WindowManager.LayoutParams.WRAP_CONTENT;
        w.setAttributes(lp);
    }

    /**
     * Creates a color grid that displays various colors for the user to choose
     * @param context {@link Context} for this view
     * @param attrs {@link AttributeSet} that holds the data from xml
     * @param defStyle style definition for this view
     * @param listener {@link OnColorSelectedListener} is a callback when a color
     *            from the palette is selected.
     * @deprecated Please use the standard {@link ColorPalette(Context)}
     *             constructor instead
     */
    @Deprecated
    @DeprecatedApi(since = "4.5.1", forRemoval = true, removeAt = "4.8")
    public ColorPalette(Context context, int initialColor, AttributeSet attrs,
            int defStyle,
            OnColorSelectedListener listener) {
        this(context, attrs, defStyle);
        _listener = listener;
        setColor(initialColor);
    }

    /**
     * Creates a color grid that displays various colors for the user to choose
     * @param context - {@link Context} for this view
     * @param attrs - {@link AttributeSet} that holds the data from xml
     * @param listener - {@link OnColorSelectedListener} is a callback when a color
     *            from the palette is selected.
     * @deprecated Please use the standard {@link ColorPalette(Context)}
     *             constructor instead
     */
    @Deprecated
    @DeprecatedApi(since = "4.5.1", forRemoval = true, removeAt = "4.8")
    public ColorPalette(Context context, int initialColor, AttributeSet attrs,
            OnColorSelectedListener listener) {
        this(context, attrs);
        setColor(initialColor);
        setOnColorSelectedListener(listener);
    }

    /**
     * Creates a color grid that displays various colors for the user to choose
     * @param context - {@link Context} for this view
     * @param listener - {@link OnColorSelectedListener} is a callback when a color
     *            from the palette is selected.
     * @deprecated Please use the standard {@link ColorPalette(Context)}
     *             constructor instead
     */
    @Deprecated
    @DeprecatedApi(since = "4.5.1", forRemoval = true, removeAt = "4.8")
    public ColorPalette(Context context, int initialColor,
            OnColorSelectedListener listener) {
        this(context);
        setColor(initialColor);
        setOnColorSelectedListener(listener);
    }

    /**
     * Creates a color grid that displays various colors for the user to choose
     * @param context - {@link Context} for this view
     * @deprecated Please use the standard {@link ColorPalette(Context)}
     *             constructor instead
     */
    @Deprecated
    @DeprecatedApi(since = "4.5.1", forRemoval = true, removeAt = "4.8")
    public ColorPalette(Context context, int initialColor) {
        this(context);
        setColor(initialColor);
    }

    /**
     *  Creates a color grid that displays various colors for the user to choose
     * @param context - {@link Context} for this view
     * @param initialColor - Enables the alpha slider in the {@link ColorPicker} if equal to true.
     * @deprecated Please use the standard {@link ColorPalette(Context)}
     *             constructor instead
     */
    @Deprecated
    @DeprecatedApi(since = "4.5.1", forRemoval = true, removeAt = "4.8")
    public ColorPalette(Context context, int initialColor,
            boolean enableAlphaSlider) {
        this(context);
        setShowAlpha(enableAlphaSlider);
        setColor(initialColor);
    }
}

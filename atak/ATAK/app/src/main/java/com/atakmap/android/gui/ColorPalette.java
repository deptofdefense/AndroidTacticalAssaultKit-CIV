
package com.atakmap.android.gui;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.RectShape;
import android.graphics.drawable.shapes.Shape;
import androidx.annotation.NonNull;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.TextView;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ImageButton;
import android.widget.LinearLayout;

import com.atakmap.app.R;

/**
 * A GridView that displays various colors for the user to choose.<br>
 * This view should be displayed within an {@link AlertDialog} or another Dialog. When the
 * OnColorSelected callback is called the Dialog should be dismissed.<br>
 * 
 * 
 */
public class ColorPalette extends LinearLayout {

    /** Callback listener for when a color is selected */
    protected OnColorSelectedListener _listener;

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
     * Callback for {@see ColorPallete} to specify when a color has been selected.
     * 
     * 
     */
    public interface OnColorSelectedListener {
        void onColorSelected(int color, String label);
    }

    /**
     * Creates a {@see ColorPallete} gridview that displays various colors for the user to choose.
     * Note
     * 
     * @param context - {@link Context} for this view
     * @param attrs - {@link AttributeSet} that holds the data from xml
     * @param defStyle - style definition for this view
     * @param listener - {@see ColorPallete.OnColorSelectedListener} is a callback when a color
     *            from the palette is selected.
     */
    public ColorPalette(Context context, int initialColor, AttributeSet attrs,
            int defStyle,
            OnColorSelectedListener listener) {
        super(context, attrs, defStyle);
        _listener = listener;
        init(initialColor);
    }

    /**
     * Creates a {@see ColorPallete} gridview that displays various colors for the user to choose.
     * Note
     * 
     * @param context - {@link Context} for this view
     * @param attrs - {@link AttributeSet} that holds the data from xml
     * @param listener - {@see ColorPallete.OnColorSelectedListener} is a callback when a color
     *            from the palette is selected.
     */
    public ColorPalette(Context context, int initialColor, AttributeSet attrs,
            OnColorSelectedListener listener) {
        super(context, attrs);
        _listener = listener;
        init(initialColor);
    }

    /**
     * Creates a {@see ColorPallete} gridview that displays various colors for the user to choose.
     * Note
     * 
     * @param context - {@see Context} for this view
     * @param listener - {@see ColorPallete.OnColorSelectedListener} is a callback when a color
     *            from the palette is selected.
     */
    public ColorPalette(Context context, int initialColor,
            OnColorSelectedListener listener) {
        super(context);
        _listener = listener;
        init(initialColor);
    }

    /**
     * Creates a {@see ColorPallete} gridview that displays various colors for the user to choose.
     * Note
     * 
     * @param context - {@link Context} for this view
     */
    public ColorPalette(Context context, int initialColor) {
        super(context);
        init(initialColor);
    }

    /**
     * Set the {@see ColorPallete.OnColorSelectedListener} to be called back when a color is
     * selected.
     * 
     * @param listener - The {@link OnColorSelectedListener} to be set.
     */
    public void setOnColorSelectedListener(OnColorSelectedListener listener) {
        _listener = listener;
    }

    /*
     * Initialize the Color Palette view with the color buttons separated and of a static height
     */
    protected void init(final int initialColor) {
        final Context context = this.getContext();
        this.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                550, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.gravity = Gravity.CENTER;
        this.setLayoutParams(params);

        TextView current = new TextView(this.getContext());
        current.setText(R.string.current_color);
        current.setGravity(Gravity.CENTER);
        current.setTextColor(initialColor);

        GridView gv = new GridView(this.getContext());
        gv.setNumColumns(5);
        gv.setVerticalSpacing(5);
        gv.setHorizontalSpacing(5);
        gv.setColumnWidth(75);
        gv.setGravity(Gravity.CENTER);

        ArrayAdapter<Integer> colorAdapter = new ArrayAdapter<Integer>(
                getContext(),
                android.R.layout.simple_list_item_1, colorArray) {
            @NonNull
            @Override
            public View getView(int position, View convertView,
                    @NonNull ViewGroup parent) {
                final ImageButton colorBtn = new ImageButton(getContext());
                colorBtn.setBackgroundResource(R.drawable.atak_button);
                colorBtn.setLayoutParams(new AbsListView.LayoutParams(
                        AbsListView.LayoutParams.WRAP_CONTENT,
                        AbsListView.LayoutParams.WRAP_CONTENT));
                Shape rect = new RectShape();
                rect.resize(50, 50);
                ShapeDrawable color = new ShapeDrawable();
                color.setBounds(0, 0, 50, 50);
                color.setIntrinsicHeight(50);
                color.setIntrinsicWidth(50);
                color.getPaint().setColor(colorArray[position]);
                color.setShape(rect);
                final String colorLablel = colorLabelArray[position];

                colorBtn.setImageDrawable(color);
                colorBtn.invalidate();
                colorBtn.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        int newColor = ((ShapeDrawable) colorBtn.getDrawable())
                                .getPaint()
                                .getColor();
                        _listener.onColorSelected(newColor, colorLablel);
                    }
                });
                return colorBtn;
            }
        };
        gv.setAdapter(colorAdapter);
        FrameLayout.LayoutParams params2 = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT);
        params2.gravity = Gravity.CENTER;
        gv.setLayoutParams(params2);

        Button b = new Button(this.getContext());
        b.setText(R.string.custom);
        b.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View arg0) {
                final ColorPicker picker = new ColorPicker(context,
                        initialColor);
                AlertDialog.Builder b = new AlertDialog.Builder(context)
                        .setTitle(R.string.custom_color_dialog)
                        .setPositiveButton(R.string.ok,
                                new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog,
                                            int which) {
                                        dialog.dismiss();
                                        _listener.onColorSelected(
                                                picker.getColor(), "Cust");
                                    }
                                })
                        .setNegativeButton(R.string.cancel, null);

                b.setView(picker);
                final AlertDialog alert = b.create();
                alert.show();
            }
        });
        //b.setLayoutParams(params);
        this.addView(current);
        this.addView(gv);
        this.addView(b);
    }
}

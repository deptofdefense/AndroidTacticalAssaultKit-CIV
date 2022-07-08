
package com.atakmap.android.bpha;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;

import com.atakmap.annotations.ModifierApi;

public class BPHAGridView extends View {

    private int _Rows = 2;
    private int _Columns = 2;
    private int squareWidth = 30;
    private Paint _paintStroke;

    // fix DrawAllocation: Memory allocations within drawing code fix by preallocate and reuse 
    private final Path path = new Path();
    private final RectF bounds = new RectF();

    public BPHAGridView(final Context context) {
        super(context);
        init();
    }

    public BPHAGridView(final Context context, final AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public BPHAGridView(final Context context, final AttributeSet attrs,
            final int defStyle) {
        super(context, attrs, defStyle);
        init();
    }

    private void init() {
        setWillNotDraw(false);
        float width = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                15, getResources().getDisplayMetrics());
        squareWidth = Math.round(width);
        _paintStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        _paintStroke.setStyle(Paint.Style.STROKE);
        _paintStroke.setColor(Color.argb(255, 0, 0, 0));
        _paintStroke.setStrokeWidth(4);
    }

    @Override
    protected void onDraw(Canvas canvas) {

        super.onDraw(canvas);
        for (int i = 0; i < _Rows; ++i) {
            drawBlocksForRow(i, path);
        }
        path.computeBounds(bounds, true);
        path.addCircle(bounds.centerX(),
                bounds.centerY() + _paintStroke.getStrokeWidth() / 4, 10,
                Path.Direction.CW);
        canvas.drawPath(path, _paintStroke);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        int height = (_Rows * squareWidth) + getPaddingTop()
                + getPaddingBottom();
        int width = (_Columns * squareWidth) + getPaddingLeft()
                + getPaddingRight();

        int heightSizeAndState = resolveSizeAndState(height, heightMeasureSpec,
                0);
        int widthSizeAndState = resolveSizeAndState(width, widthMeasureSpec, 0);
        setMeasuredDimension(widthSizeAndState, heightSizeAndState);
    }

    private void drawBlocksForRow(int row, Path path) {
        int startingPointForThisRow = getTop() + getPaddingTop() + row
                * squareWidth;

        for (int i = 0; i < _Columns; ++i) {
            drawSquare(getLeft() + getPaddingLeft() + i * squareWidth,
                    startingPointForThisRow, path);
        }
    }

    private void drawSquare(int startLeft, int startTop, Path path) {
        path.moveTo(startLeft, startTop);
        path.lineTo(startLeft + squareWidth, startTop);
        path.lineTo(startLeft + squareWidth, startTop + squareWidth);
        path.lineTo(startLeft, startTop + squareWidth);
        path.lineTo(startLeft, startTop - _paintStroke.getStrokeWidth() / 2);
    }

    @ModifierApi(since = "4.6", target = "4.9", modifiers = {})
    public int get_Rows() {
        return _Rows;
    }

    @ModifierApi(since = "4.6", target = "4.9", modifiers = {})
    public void set_Rows(int _Rows) {
        this._Rows = _Rows;
        requestLayout();
        invalidate();
    }

    @ModifierApi(since = "4.6", target = "4.9", modifiers = {})
    public int get_Columns() {
        return _Columns;
    }

    @ModifierApi(since = "4.6", target = "4.9", modifiers = {})
    public void set_Columns(int _Columns) {
        this._Columns = _Columns;
        requestLayout();
        invalidate();
    }

}

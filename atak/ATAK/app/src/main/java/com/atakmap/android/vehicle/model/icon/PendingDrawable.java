
package com.atakmap.android.vehicle.model.icon;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewParent;
import android.widget.Adapter;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ImageView;

import com.atakmap.android.maps.MapView;

import java.util.Objects;
import java.util.UUID;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Bitmap-based drawable that may not be loaded on initial use
 *
 * Used for vehicle icons which are not immediately available if they have
 * not been generated yet
 */
public class PendingDrawable extends Drawable {

    private final String _uid;
    private Drawable _placeholder;
    private Drawable _drawable;
    private int _alpha = 255;
    private ColorFilter _colorFilter;

    public PendingDrawable() {
        _uid = UUID.randomUUID().toString();
    }

    public PendingDrawable(Drawable placeholder) {
        this();
        _placeholder = placeholder;
    }

    public PendingDrawable(PendingDrawable mutate) {
        _uid = mutate._uid;
        if (mutate._placeholder != null)
            _placeholder = mutate._placeholder.mutate();
        if (mutate._drawable != null)
            _drawable = mutate._drawable.mutate();
        _alpha = mutate._alpha;
        _colorFilter = mutate._colorFilter;
        setBounds(mutate.getBounds());
    }

    private Drawable getCurrentDrawable() {
        return _drawable != null ? _drawable : _placeholder;
    }

    public boolean isPending() {
        return _drawable == null;
    }

    public void set(Drawable drawable) {
        _drawable = drawable;

        // Let the callback know the drawable has changed
        Callback cb = getCallback();

        // XXX - The view doesn't actually redraw when we fire the invalidate
        // callback, so instead we need to do a bunch of hacks to get the
        // drawable to update properly (see ATAK-12965)
        if (cb instanceof View) {
            // Drawable is assigned to the background of a view
            View v = (View) cb;
            if (equals(v.getBackground())) {
                v.setBackground(null);
                v.setBackground(this);
            }

            // Drawable is assigned as the source of an image view
            if (cb instanceof ImageView) {
                ImageView iv = (ImageView) cb;
                if (equals(iv.getDrawable())) {
                    iv.setImageDrawable(null);
                    iv.setImageDrawable(this);
                }
            }

            // And if all that wasn't enough, refresh the parent adapter if
            // we can find one
            ViewParent vp = v.getParent();
            while (vp != null) {
                if (vp instanceof AdapterView) {
                    AdapterView<?> adapterView = (AdapterView<?>) vp;
                    Adapter adapter = adapterView.getAdapter();
                    if (adapter instanceof BaseAdapter)
                        ((BaseAdapter) adapter).notifyDataSetChanged();
                    break;
                }
                vp = vp.getParent();
            }
        }

        // Fire invalidate anyway in case the callback actually does something with it
        invalidateSelf();
    }

    public void setBitmap(Bitmap bmp) {
        MapView mv = MapView.getMapView();
        if (mv != null)
            set(new BitmapDrawable(mv.getResources(), bmp));
    }

    @Override
    public void setAlpha(int alpha) {
        _alpha = alpha;
        invalidateSelf();
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {
        _colorFilter = colorFilter;
        invalidateSelf();
    }

    @Override
    public int getOpacity() {
        if (_alpha < 255)
            return PixelFormat.TRANSLUCENT;
        Drawable current = getCurrentDrawable();
        return current != null ? current.getOpacity() : PixelFormat.TRANSLUCENT;
    }

    @Override
    public int getIntrinsicWidth() {
        Drawable current = getCurrentDrawable();
        return current != null ? current.getIntrinsicWidth() : 0;
    }

    @Override
    public int getIntrinsicHeight() {
        Drawable current = getCurrentDrawable();
        return current != null ? current.getIntrinsicHeight() : 0;
    }

    @Override
    @NonNull
    public PendingDrawable mutate() {
        return new PendingDrawable(this);
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        Drawable current = getCurrentDrawable();
        if (current != null) {
            current.setBounds(getBounds());
            current.setColorFilter(_colorFilter);
            current.setAlpha(_alpha);
            current.draw(canvas);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        PendingDrawable that = (PendingDrawable) o;
        return Objects.equals(_uid, that._uid);
    }

    @Override
    public int hashCode() {
        return Objects.hash(_uid);
    }
}

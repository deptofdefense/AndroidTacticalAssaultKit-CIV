
package com.atakmap.android.hierarchy;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.atakmap.android.util.ATAKUtilities;
import com.atakmap.app.R;
import com.atakmap.android.hierarchy.HierarchyListItem.Sort;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.atakmap.android.gui.PluginSpinner;

/**
 * Spinner that displays sort modes
 * When clicked: switches to next sort mode in list
 * When long-pressed: default spinner behavior
 */
public class SortSpinner extends PluginSpinner implements
        GestureDetector.OnGestureListener {

    private GestureDetector _gDetector;
    private final Adapter _adapter;

    public SortSpinner(Context context) {
        super(context, null);
        setAdapter(_adapter = new Adapter(context));
    }

    public SortSpinner(Context context, int mode) {
        super(context, mode);
        setAdapter(_adapter = new Adapter(context));
    }

    public SortSpinner(Context context, AttributeSet attrs) {
        super(context, attrs);
        setAdapter(_adapter = new Adapter(context));
    }

    public SortSpinner(Context context, AttributeSet attrs, int mode) {
        super(context, attrs, mode);
        setAdapter(_adapter = new Adapter(context));
    }

    public SortSpinner(Context context, AttributeSet attrs, int style,
            int mode) {
        super(context, attrs, style, mode);
        _adapter = new Adapter(context);
    }

    public void setSortModes(List<Sort> modes) {
        _adapter.setModes(modes);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (_gDetector == null)
            _gDetector = new GestureDetector(getContext(), this);
        return _gDetector.onTouchEvent(event);
    }

    @Override
    public boolean onDown(MotionEvent e) {
        return true;
    }

    @Override
    public void onShowPress(MotionEvent e) {
    }

    @Override
    public boolean onSingleTapUp(MotionEvent e) {
        int nextPos = (getSelectedItemPosition() + 1) % getCount();
        setSelection(nextPos);
        return true;
    }

    @Override
    public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX,
            float distanceY) {
        return false;
    }

    @Override
    public void onLongPress(MotionEvent e) {
        performClick();
    }

    @Override
    public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX,
            float velocityY) {
        return false;
    }

    public static class Adapter extends BaseAdapter {

        private final LayoutInflater _inflater;
        private final List<Sort> _modes = new ArrayList<>();
        private final Map<String, Bitmap> _iconCache = new HashMap<>();

        public Adapter(Context context) {
            _inflater = LayoutInflater.from(context);
        }

        static private class ViewHolder {
            ImageView icon;
            TextView title;
        }

        public void setModes(List<Sort> modes) {
            synchronized (_modes) {
                _modes.clear();
                _modes.addAll(modes);
            }
            notifyDataSetChanged();
        }

        @Override
        public Object getItem(int position) {
            synchronized (_modes) {
                return _modes.get(position);
            }
        }

        @Override
        public int getCount() {
            synchronized (_modes) {
                return _modes.size();
            }
        }

        @Override
        public long getItemId(int position) {
            return 0;
        }

        @Override
        public View getDropDownView(int position, View convertView,
                ViewGroup parent) {
            return getView(position, convertView, parent, true);
        }

        @Override
        public View getView(final int position, View convertView,
                ViewGroup parent) {
            return getView(position, convertView, parent, false);
        }

        public View getView(final int position, View row, ViewGroup parent,
                boolean list) {
            Sort sort = (Sort) getItem(position);
            if (sort == null)
                return row;

            ViewHolder h = row == null ? null : (ViewHolder) row.getTag();
            if (h == null) {
                row = _inflater
                        .inflate(R.layout.sort_spinner_item, parent, false);
                h = new ViewHolder();
                h.icon = row.findViewById(R.id.sort_icon);
                h.title = row.findViewById(R.id.sort_title);
                row.setTag(h);
            }

            String iconUri = sort.getIconUri();
            Bitmap icon = _iconCache.get(iconUri);
            if (icon == null) {
                icon = ATAKUtilities.getUriBitmap(iconUri);
                _iconCache.put(iconUri, icon);
            }
            h.icon.setImageBitmap(icon);

            if (list) {
                h.title.setVisibility(View.VISIBLE);
                h.title.setText(sort.getTitle());
            } else
                h.title.setVisibility(View.GONE);

            return row;
        }
    }
}

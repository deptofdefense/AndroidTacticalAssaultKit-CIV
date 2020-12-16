
package com.atakmap.android.gui;

import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.atakmap.app.R;

/**
 *
 * To be used with layouts in the form of:
 *
  <include layout="@layout/atak_selection_flat" 
           android:layout_width="match_parent" 
           android:layout_height="32dp" 
           android:layout_gravity="center_horizontal"
           android:layout_marginTop="4dp" 
           android:layout_marginBottom="4dp" 
           android:layout_marginRight="2dp" 
           android:layout_marginLeft="2dp" 
           android:id="@+id/user_id" />
 *
 *
 */
public class ActionButton {

    private final View _root;
    private final TextView _title;
    private final TextView _selectedText;
    private boolean _enabled = true;
    private final ImageView _icon;

    public ActionButton(View root) {
        _root = root;
        _title = _root.findViewById(R.id.selector_title);
        _title.setVisibility(View.GONE);
        _selectedText = _root.findViewById(R.id.selector_item);
        _icon = _root.findViewById(R.id.selector_icon);
    }

    public void setVisibility(int view) {
        _root.setVisibility(view);
    }

    public void setOnClickListener(View.OnClickListener listener) {
        _root.setOnClickListener(listener);
        _selectedText.setOnClickListener(listener);
        _icon.setOnClickListener(listener);
    }

    public void setTitle(String title) {
        _title.setText(title);
    }

    public void setTitleVisible(boolean b) {
        if (b) {
            _title.setVisibility(View.VISIBLE);
        } else {
            _title.setVisibility(View.GONE);
        }
    }

    public void setText(final String text) {
        _selectedText.setText(text);
    }

    public void setTextColor(final int color) {
        _selectedText.setTextColor(color);
    }

    public String getText() {
        return _selectedText.getText().toString();
    }

    public String getTitle() {
        return _title.getText().toString();
    }

    public boolean isEnabled() {
        return _enabled;
    }

    public void setEnabled(boolean enabled) {
        this._enabled = enabled;
        _selectedText.setClickable(enabled);
        _root.setClickable(enabled);
        _icon.setClickable(enabled);
        if (_enabled) {
            // show the triangle
            _icon.setVisibility(View.VISIBLE);
        } else {
            // hide the triangle
            _icon.setVisibility(View.INVISIBLE);
        }

    }

}

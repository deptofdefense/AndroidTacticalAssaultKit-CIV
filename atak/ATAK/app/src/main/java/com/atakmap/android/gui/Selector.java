
package com.atakmap.android.gui;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Typeface;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.atakmap.app.R;
import com.atakmap.coremap.log.Log;

import com.atakmap.android.util.BasicNameValuePair;

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
public class Selector {

    private static final String TAG = "Selector";

    private Context _context;
    private View _root;
    private TextView _title;
    private TextView _selectedText;
    private int _selectedItemId;
    private OnSelectionChangedListener _selectionChangedListener;
    private boolean _enabled = true;
    private ImageView _icon;

    private BasicNameValuePair[] _items;

    /**
     * Creates a standard selector within the system provided a resource defined list.
     */
    public Selector(final Context context, final View root,
            final int resourceListId) {
        this(context, root, context.getResources().getStringArray(
                resourceListId));
    }

    public Selector(final Context context, final View root,
            final String[] items) {
        BasicNameValuePair[] result = new BasicNameValuePair[items.length];
        for (int i = 0; i < items.length; i++) {
            result[i] = new BasicNameValuePair(items[i], items[i]);
        }
        init(context, root, result);
    }

    public Selector(Context context, View root, BasicNameValuePair[] items) {
        init(context, root, items);
    }

    /**
     * Allows for reinitialization of the current Selector items.
     */
    public void setItems(String[] items) {
        BasicNameValuePair[] result = new BasicNameValuePair[items.length];
        for (int i = 0; i < items.length; i++) {
            result[i] = new BasicNameValuePair(items[i], items[i]);
        }
        init(_context, _root, result);
    }

    private void init(final Context context, final View root,
            final BasicNameValuePair[] items) {
        _context = context;
        _root = root;
        _items = items;
        _selectedItemId = -1;
        _title = _root.findViewById(R.id.selector_title);
        _selectedText = _root.findViewById(R.id.selector_item);
        _selectedText.setTypeface(_selectedText.getTypeface(), Typeface.BOLD);
        _icon = _root.findViewById(R.id.selector_icon);
        _root.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                selectorOnClick();
            }
        });

        // do not both the user by showing additional options
        setEnabled(_items.length >= 2);
    }

    public void setOnSelectionChangedListener(
            OnSelectionChangedListener listener) {
        _selectionChangedListener = listener;
    }

    private void selectorOnClick() {
        if (!_enabled) {
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(_context);
        builder.setTitle(_context.getString(R.string.select_space)
                + _title.getText().toString());
        String[] nameList = new String[_items.length];
        for (int i = 0; i < _items.length; i++) {
            nameList[i] = _items[i].getName();
        }
        // set the items in the selection
        builder.setItems(nameList, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                // save the selected item id
                _selectedItemId = which;
                // set the selected Item text view
                _selectedText.setText(_items[_selectedItemId].getName());
                // trigger onSelectionChanged
                if (_selectionChangedListener != null) {
                    _selectionChangedListener.onSelectionChanged(
                            _items[_selectedItemId].getName(), _selectedItemId);
                }
            }
        });
        builder.setNegativeButton(R.string.cancel, null);
        builder.create().show();

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

    public String getTitle() {
        return _title.getText().toString();
    }

    public void setSelection(final int itemId) {
        setSelectedItem(itemId);
    }

    public void setSelectedItem(final int itemId) {
        if (_items.length - 1 < itemId) {
            return;
        }
        _selectedItemId = itemId;
        _selectedText.setText(_items[_selectedItemId].getName());
        if (_selectionChangedListener != null) {
            _selectionChangedListener.onSelectionChanged(
                    _items[_selectedItemId].getName(), _selectedItemId);
        }

    }

    public boolean setSelectedName(final String text) {
        for (int i = 0; i < _items.length; i++) {
            if (_items[i].getName().equalsIgnoreCase(text)) {
                setSelectedItem(i);
                return true;
            }
        }
        // if we did not find a matching item throw exception
        // TODO decide do we throw an error or just log a message
        Log.d(TAG, "String '" + text + "' not found in Selectors Item List");
        return false;
    }

    public void setCustomText(final String text) {
        _selectedText.setText(text);
    }

    public void setSelectedValue(final String text) {
        for (int i = 0; i < _items.length; i++) {
            if (_items[i].getValue().equalsIgnoreCase(text)) {
                setSelectedItem(i);
                return;
            }
        }
    }

    public int getSelectedItemId() {
        return _selectedItemId;
    }

    public int getSelectedItemPosition() {
        return _selectedItemId;
    }

    public int getCount() {
        return _items.length;
    }

    public String getSelectedName() {
        if (_selectedItemId == -1 || _items == null)
            return null;

        return _items[_selectedItemId].getName();
    }

    public String getSelectedItem() {
        return getSelectedName();
    }

    public String getSelectedValue() {
        if (_selectedItemId == -1 || _items == null)
            return null;

        return _items[_selectedItemId].getValue();
    }

    public interface OnSelectionChangedListener {
        void onSelectionChanged(String selectionText, int selectedIndex);
    }

    public boolean isEnabled() {
        return _enabled;
    }

    public void setEnabled(boolean enabled) {
        this._enabled = enabled;
        if (_enabled) {
            // show the triangle
            _icon.setVisibility(View.VISIBLE);
        } else {
            // hide the triangle
            _icon.setVisibility(View.INVISIBLE);
        }

    }

    public void setVisibility(int state) {
        _root.setVisibility(state);
    }

}

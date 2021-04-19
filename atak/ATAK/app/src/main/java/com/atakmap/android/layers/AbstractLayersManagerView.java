
package com.atakmap.android.layers;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.LinearLayout;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;

public abstract class AbstractLayersManagerView extends LinearLayout {

    protected final int listViewId;
    protected final int outlineToggleButtonId;

    private ListView _listView;
    private CompoundButton _outlineToggleButton;

    protected AbstractLayersManagerView(Context context, AttributeSet attrs,
            int listViewId, int outlineToggleButtonId) {
        super(context, attrs);

        this.listViewId = listViewId;
        this.outlineToggleButtonId = outlineToggleButtonId;
    }

    public final void scrollToSelection(final int index) {
        if (index != -1) {
            final ListView listView = _getListView();
            listView.post(new Runnable() {
                @Override
                public void run() {
                    View v = listView.getChildAt(0);
                    int top = (v == null) ? 0 : v.getTop();
                    listView.setSelectionFromTop(index, top);
                }
            });
        }
    }

    public final void setListAdapter(ListAdapter adapter) {
        final ListView listView = _getListView();
        listView.setAdapter(adapter);
        listView.post(new Runnable() {
            @Override
            public void run() {
                int index = listView.getFirstVisiblePosition();
                View v = listView.getChildAt(0);
                int top = (v == null) ? 0 : v.getTop();
                listView.setSelectionFromTop(index, top);
            }
        });
    }

    public final void setOnItemClickListener(OnItemClickListener listener) {
        _getListView().setOnItemClickListener(listener);
    }

    public void setOnItemLongClickListener(OnItemLongClickListener l) {
        _getListView().setOnItemLongClickListener(l);
    }

    protected synchronized final ListView _getListView() {
        if (_listView == null) {
            _listView = findViewById(this.listViewId);
            final TextView emptyText = findViewById(
                    android.R.id.empty);
            _listView.setEmptyView(emptyText);
        }
        return _listView;
    }

    protected CompoundButton getOutlineToggleButton() {
        if (_outlineToggleButton == null) {
            _outlineToggleButton = findViewById(
                    this.outlineToggleButtonId);
        }
        return _outlineToggleButton;
    }

    protected void setOnOutlineToggleListener(
            OnCheckedChangeListener listener) {
        CompoundButton outlineButton = getOutlineToggleButton();
        outlineButton.setOnCheckedChangeListener(listener);
    }

    protected void setOutlineToggleState(boolean visible) {
        CompoundButton outlineButton = getOutlineToggleButton();
        outlineButton.setChecked(visible);
    }
}

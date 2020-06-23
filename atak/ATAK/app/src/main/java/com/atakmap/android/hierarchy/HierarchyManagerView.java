
package com.atakmap.android.hierarchy;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.LinearLayout;
import android.widget.ListAdapter;
import android.widget.ListView;

import com.atakmap.app.R;

public class HierarchyManagerView extends LinearLayout {

    private ListView _hierarchyList;

    public HierarchyManagerView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void setAdapter(ListAdapter adapter) {
        ListView hierarchyList = _getHierarchyList();
        ((HierarchyListAdapter) adapter).setHierManView(this);
        hierarchyList.setAdapter(adapter);
    }

    public void setOnItemClickListener(OnItemClickListener l) {
        ListView lv = _getHierarchyList();
        lv.setOnItemClickListener(l);
    }

    public void setOnItemLongClickListener(OnItemLongClickListener l) {
        ListView lv = _getHierarchyList();
        lv.setOnItemLongClickListener(l);
    }

    public void performItemClick(View view, int position, long rowid) {
        ListView lv = _getHierarchyList();
        lv.performItemClick(view, position, rowid);
    }

    public ListView _getHierarchyList() {
        if (_hierarchyList == null) {
            _hierarchyList = findViewById(
                    R.id.hierarchy_manager_list);
        }
        return _hierarchyList;
    }
}

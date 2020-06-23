
package com.atakmap.android.user;

import android.content.Context;
import android.database.DataSetObserver;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListAdapter;
import android.widget.TextView;

import com.atakmap.android.maps.Marker;
import com.atakmap.lang.Objects;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

public class TaskableMarkerListAdapter implements ListAdapter {

    public TaskableMarkerListAdapter(Context context) {
        _markerList = new ArrayList<>();
        _context = context;
    }

    public void addMarker(Marker marker) {
        for (Marker m : _markerList) {
            if (Objects.equals(m.getUID(), marker.getUID())) {
                _markerList.set(_markerList.indexOf(m), marker);
                return;
            }
        }
        _markerList.add(marker);
    }

    public void removeMarker(Marker marker) {
        _markerList.remove(marker);
    }

    @Override
    public boolean areAllItemsEnabled() {
        return false;
    }

    @Override
    public boolean isEnabled(int position) {
        return true;
    }

    @Override
    public int getCount() {
        return _markerList.size();
    }

    @Override
    public Object getItem(int position) {
        return _markerList.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public int getItemViewType(int position) {
        return 0;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        TextView tv = new TextView(_context);
        float density = _context.getResources().getDisplayMetrics().density;
        tv.setText(_markerList.get(position).getTitle());
        tv.setPadding(0, (int) (density * 10), 0, (int) (10 * density));
        return tv;
    }

    @Override
    public int getViewTypeCount() {
        return 1;
    }

    @Override
    public boolean hasStableIds() {
        return true;
    }

    @Override
    public boolean isEmpty() {
        return _markerList.isEmpty();
    }

    @Override
    public void registerDataSetObserver(DataSetObserver observer) {
        _dataSetObservers.add(observer);
    }

    @Override
    public void unregisterDataSetObserver(DataSetObserver observer) {
        _dataSetObservers.remove(observer);
    }

    private final ConcurrentLinkedQueue<DataSetObserver> _dataSetObservers = new ConcurrentLinkedQueue<>();
    private final List<Marker> _markerList;
    private final Context _context;
}

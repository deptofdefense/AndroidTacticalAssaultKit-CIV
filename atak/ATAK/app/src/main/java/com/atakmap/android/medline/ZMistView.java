
package com.atakmap.android.medline;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.util.AttributeSet;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.PointMapItem;
import com.atakmap.app.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 *
 * View for ZMISTs in the Med Line
 */
public class ZMistView extends LinearLayout {

    public static final String TAG = "ZMistView";

    Map<Integer, Object> _zMists;

    private final Context _context;
    private PointMapItem _marker;
    private MapView _mapView;

    private Button addButton;
    private ImageButton closedArrow;
    private ImageButton openArrow;
    private TextView zMistCount;
    private LinearLayout expandedView;

    public ZMistView(final Context context) {
        this(context, null);
    }

    public ZMistView(final Context context,
            final AttributeSet attrSet) {
        super(context, attrSet);
        this._context = context;
    }

    public void init(final MapView mapView) {
        _mapView = mapView;
        addButton = findViewById(R.id.addZMist);
        zMistCount = findViewById(R.id.zmistCount);
        closedArrow = findViewById(R.id.arrowClosed);
        openArrow = findViewById(R.id.arrowOpen);
        expandedView = findViewById(R.id.zMistsExpanded);
        expandedView.setVisibility(View.GONE);

        _zMists = new HashMap<>();

        setUpClickListeners();
    }

    public void setMarker(PointMapItem marker) {
        _marker = marker;
        loadData();
        setCount();
    }

    /**
     * Set up the add and expand buttons
     */
    private void setUpClickListeners() {
        //On Click for the add button
        addButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                ZMist zmist = addZMist();
                zmist.loadData(null);
                zmist.saveData();
                if (expandedView.getVisibility() == GONE) {

                    closedArrow.setVisibility(GONE);
                    openArrow.setVisibility(VISIBLE);
                    expandedView.setVisibility(VISIBLE);
                }
                setCount();
            }
        });

        //on click for the closed arrow
        closedArrow.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (expandedView.getChildAt(0) != null) {
                    closedArrow.setVisibility(GONE);
                    openArrow.setVisibility(VISIBLE);
                    expandedView.setVisibility(VISIBLE);
                }
            }
        });

        //on click for the open arrow
        openArrow.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                openArrow.setVisibility(GONE);
                closedArrow.setVisibility(VISIBLE);
                expandedView.setVisibility(GONE);
            }
        });

    }

    private void setCount() {
        String count = "(" +
                _zMists.size() +
                ")";
        zMistCount.setText(count);
    }

    /**
     * Add a ZMIST
     * @return - the ZMIST object added
     */
    private ZMist addZMist() {
        ZMist zMist = new ZMist(_context);

        final int id = _zMists.size() + 1;
        _zMists.put(id, zMist);

        LinearLayout ll = zMist.createZMist(id, _marker, _mapView);
        expandedView.addView(ll);
        ImageButton remove = ll.findViewById(R.id.zmist_remove);
        //the remove button on click listener
        remove.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                final AlertDialog.Builder builder = new AlertDialog.Builder(
                        _context);
                builder.setTitle(R.string.delete2);
                builder.setIcon(R.drawable.ic_menu_delete_32);
                builder.setMessage(_context.getString(
                        R.string.are_you_sure_delete2, "ZMIST" + id));
                builder.setPositiveButton(R.string.delete2,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog,
                                    int which) {
                                remove(id);
                                loadData();
                                setCount();

                            }
                        }).setNegativeButton(R.string.cancel, null);
                builder.show();
            }
        });

        return zMist;
    }

    /**
     * Load the data from the marker's
     * zMist map
     */
    private void loadData() {
        expandedView.removeAllViews();
        _zMists.clear();
        if (_marker != null) {
            Map<String, Object> zMistsMap = _marker.getMetaMap("zMists");
            if (zMistsMap != null) {
                List<String> keys = new ArrayList<>(zMistsMap.keySet());
                //need to sort the keys
                Collections.sort(keys);
                for (Object o : keys) {
                    String key = (String) o;
                    Map<String, Object> zMistData = (HashMap<String, Object>) zMistsMap
                            .get(key);
                    ZMist zMist = addZMist();

                    zMist.loadData(zMistData);
                    zMist.saveData();
                }
            }
        }
        if (_zMists.size() == 0) {
            expandedView.setVisibility(View.GONE);
            closedArrow.setVisibility(VISIBLE);
            openArrow.setVisibility(GONE);
        }
    }

    /**
     * Method to save the data when the
     * dropdown is closed
     */
    public void shutdown() {
        for (Map.Entry e : _zMists.entrySet()) {
            ZMist zmist = (ZMist) e.getValue();
            zmist.saveData();
        }
    }

    /**
     * Remove a ZMIST
     * @param id - the id of the ZMIST
     */
    private void remove(int id) {
        Map<String, Object> zMistsMap = _marker.getMetaMap("zMists");
        if (zMistsMap == null) {
            return;
        }

        String title = _context.getString(R.string.zmist) + id;
        zMistsMap.remove(title);
        //decrement any of the id numbers after the removed item
        while (id <= zMistsMap.size()) {
            id++;
            String oldTitle = _context.getString(R.string.zmist) + id;
            if (zMistsMap.containsKey(oldTitle)) {
                Map<String, Object> zMist = (Map<String, Object>) zMistsMap
                        .get(oldTitle);
                if (zMist != null) {
                    int newId = id - 1;
                    String newTitle = _context.getString(R.string.zmist)
                            + newId;
                    zMistsMap.put(newTitle, zMist);
                    zMistsMap.remove(oldTitle);
                }
            }
        }
        _marker.setMetaMap("zMists", zMistsMap);
    }

}

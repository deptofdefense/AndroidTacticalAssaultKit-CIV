
package com.atakmap.android.fires;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageButton;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import com.atakmap.android.gui.ImageRadioButton;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Marker;
import com.atakmap.android.util.AltitudeUtilities;
import com.atakmap.app.R;
import com.atakmap.coremap.conversions.CoordinateFormat;
import com.atakmap.coremap.conversions.CoordinateFormatUtilities;
import com.atakmap.coremap.conversions.AngleUtilities;
import com.atakmap.coremap.maps.coords.GeoPoint;

import com.atakmap.android.util.ATAKUtilities;

import java.util.List;

class HostileManagerAdapter extends BaseAdapter {

    private final List<Marker> _list; //List to monitor
    private final SharedPreferences prefs;
    private int selectedIndex = -1;
    private final HostileManagerDropDownReceiver hmddr;

    HostileManagerAdapter(final HostileManagerDropDownReceiver hmddr,
            List<Marker> list) {
        _list = list;
        prefs = PreferenceManager.getDefaultSharedPreferences(MapView
                .getMapView().getContext());
        this.hmddr = hmddr;
    }

    @Override
    public int getCount() {
        return _list.size();
    }

    @Override
    public Object getItem(int position) {
        return _list.get(position);
    }

    public int getSelectedIndex() {
        return selectedIndex;
    }

    void updateSelected(int index) {
        selectedIndex = index;
    }

    @Override
    public long getItemId(int position) {
        return 0;
    }

    @Override
    public View getView(final int position, View convertView,
            ViewGroup parent) {

        final MapView mv = MapView.getMapView();

        if (convertView == null) {
            LayoutInflater inflater = LayoutInflater.from(mv.getContext());
            convertView = inflater.inflate(
                    R.layout.hostile_manager_list_item, null);
        }
        convertView.setVisibility(View.VISIBLE);

        final RadioButton radioButton = convertView
                .findViewById(R.id.radiogroupfools);
        radioButton.setChecked(position == selectedIndex);

        TextView titleTV = convertView
                .findViewById(R.id.hierarchy_manager_list_item_title);
        TextView positionTV = convertView
                .findViewById(R.id.hierarchy_manager_list_item_desc);
        TextView cfTV = convertView
                .findViewById(R.id.hierarchy_manager_list_item_cf);
        TextView ccaTV = convertView
                .findViewById(R.id.hierarchy_manager_list_item_cca);
        ImageButton nineLineBtn = convertView
                .findViewById(R.id.hostile_manager_list_item_nine_btn);

        final HostileListItem item = new HostileListItem(mv,
                _list.get(position));
        if (item != null) {

            GeoPoint point = item.getPoint(null);
            CoordinateFormat cf = CoordinateFormat.find("MGRS");

            titleTV.setText(item.getTitle());
            titleTV.setTextColor(item.getTextColor());
            positionTV.setText(
                    CoordinateFormatUtilities.formatToString(point, cf));
            positionTV
                    .setText(new StringBuilder().append("4/6:     ")
                            .append(AltitudeUtilities.format(point,
                                    prefs))
                            .append("   ")
                            .append(CoordinateFormatUtilities.formatToString(
                                    point, cf))
                            .toString());
            cfTV.setText("    8:     " + item.getClosestFriendly());

            final double dist = point.distanceTo(mv.getSelfMarker().getPoint());
            if (dist < 100000) {
                final double bearing = point
                        .bearingTo(mv.getSelfMarker().getPoint());
                ccaTV.setText("CCF:  " +
                        AngleUtilities
                                .format(ATAKUtilities.convertFromTrueToMagnetic(
                                        point, bearing))
                        +
                        " " + Math.round(dist) + "m");
            }
        }
        convertView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { //Select/unselect line

                {
                    if (position == selectedIndex) {
                        selectedIndex = -1;
                        radioButton.setChecked(false);
                        hmddr.showArrows(false);
                    } else {
                        selectedIndex = position;
                        radioButton.setChecked(true);
                        hmddr.showArrows(true);
                        item.goTo(false);
                    }
                    notifyDataSetChanged();
                }
            }
        });
        convertView.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) { //When long pressed, prompt for color selection
                LayoutInflater inflater = LayoutInflater.from(MapView
                        .getMapView().getContext());
                final View changeColor = inflater.inflate(
                        R.layout.change_color_opts, null);
                new AlertDialog.Builder(MapView.getMapView().getContext())
                        .setTitle(R.string.select_a_color)
                        .setMessage(
                                R.string.nineline_text97)
                        .setView(changeColor)
                        .setNegativeButton(R.string.cancel, null)
                        .setPositiveButton(R.string.ok,
                                new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog,
                                            int which) {
                                        RadioGroup group = changeColor
                                                .findViewById(
                                                        R.id.select_color_group);
                                        final int id = group
                                                .getCheckedRadioButtonId();

                                        if (id == R.id.blue_btn)
                                            item.setTextColor(
                                                    HostileListItem.BLUE);
                                        else if (id == R.id.green_btn)
                                            item.setTextColor(
                                                    HostileListItem.GREEN);
                                        else if (id == R.id.yellow_btn)
                                            item.setTextColor(
                                                    HostileListItem.YELLOW);
                                        else if (id == R.id.red_btn)
                                            item.setTextColor(
                                                    HostileListItem.RED);
                                        else
                                            item.setTextColor(
                                                    HostileListItem.WHITE);

                                        notifyDataSetChanged();
                                    }
                                })
                        .show();
                ImageRadioButton whiteBtn = changeColor
                        .findViewById(R.id.white_btn);
                whiteBtn.setChecked(true);
                return true;
            }
        });

        nineLineBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                item.goTo(false);
                Intent displayNineLine = new Intent(); //Open 9-line for selected item

                // decouple is from the NineLine class, just use the string
                displayNineLine.setAction("com.atakmap.baokit.NINE_LINE");
                displayNineLine.putExtra("targetUID", item.getUID());
                AtakBroadcast.getInstance().sendBroadcast(displayNineLine);
            }
        });

        return convertView;
    }
}

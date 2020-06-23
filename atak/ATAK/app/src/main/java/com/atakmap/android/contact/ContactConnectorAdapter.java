
package com.atakmap.android.contact;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import com.atakmap.android.cot.CotMapComponent;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.PointMapItem;
import com.atakmap.android.math.MathUtils;
import com.atakmap.android.tools.menu.ActionBroadcastData;
import com.atakmap.android.util.ATAKUtilities;
import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

class ContactConnectorAdapter extends BaseAdapter {

    private static final String TAG = "ContactConnectorAdapter";
    private final SharedPreferences _prefs;
    private final MapView _view;

    private List<Connector> _connectors;
    private IndividualContact _contact;
    private PointMapItem _marker;

    ContactConnectorAdapter(MapView view, SharedPreferences prefs) {
        _view = view;
        _prefs = prefs;
    }

    public void refresh(IndividualContact contact, PointMapItem marker) {
        _contact = contact;
        _marker = marker;
        if (contact != null) {
            _connectors = new ArrayList<>(
                    _contact.getConnectors(true));
            Collections.sort(_connectors, connectorComparator);
        } else {
            _connectors = new ArrayList<>();
        }

        _view.post(new Runnable() {
            @Override
            public void run() {
                notifyDataSetChanged();
            }
        });
    }

    /**
     * Sort alphabetically
     */
    private final Comparator<Connector> connectorComparator = new Comparator<Connector>() {
        @Override
        public int compare(Connector c1, Connector c2) {

            //TODO put default up top?
            //TODO compare most recently used?
            if (c1 == null || FileSystemUtils.isEmpty(c1.getConnectionLabel()))
                return 1;
            else if (c2 == null
                    || FileSystemUtils.isEmpty(c2.getConnectionLabel()))
                return -1;

            return c1.getConnectionLabel().compareTo(c2.getConnectionLabel());
        }
    };

    @Override
    public int getCount() {
        if (_connectors == null)
            return 0;

        return _connectors.size();
    }

    @Override
    public Object getItem(int position) {
        if (position >= _connectors.size())
            return null;

        return _connectors.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    private static class ViewHolder {
        public ImageView icon = null;
        public TextView label = null;
        ImageButton detailsBtn = null;
        ImageButton profileBtn = null;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;
        if (convertView == null || convertView.getTag() == null) {
            final LayoutInflater inflater = LayoutInflater
                    .from(_view.getContext());
            convertView = inflater.inflate(
                    R.layout.contact_detail_connector_item, parent, false);

            holder = new ViewHolder();
            holder.label = convertView
                    .findViewById(R.id.contactInfoConnectorItemText);
            holder.icon = convertView
                    .findViewById(R.id.contactInfoConnectorItemIcon);
            holder.detailsBtn = convertView
                    .findViewById(R.id.contactInfoConnectorItemDetails);
            holder.profileBtn = convertView
                    .findViewById(R.id.contactInfoConnectorItemProfile);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        updateViewHolder(holder, convertView, position);

        return convertView;
    }

    private void updateViewHolder(ViewHolder holder, View convertView,
            int position) {
        final Connector connector = _connectors.get(position);
        holder.label.setText(connector.getConnectionLabel());
        String iconUri = connector.getIconUri();
        if (FileSystemUtils.isEmpty(iconUri)) {
            holder.icon.setVisibility(View.GONE);
        } else {
            holder.icon.setVisibility(View.VISIBLE);
            holder.icon.setFocusable(false);
            ATAKUtilities.SetIcon(_view.getContext(), holder.icon, iconUri,
                    Color.WHITE);
            ContactListAdapter.updateConnectorView(_contact, connector,
                    holder.icon, _view.getContext());
        }

        holder.detailsBtn.setFocusable(false);
        holder.detailsBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showDetails(_prefs, _view, _contact, connector, _marker);
            }
        });

        Object profile = CotMapComponent
                .getInstance()
                .getContactConnectorMgr()
                .getFeature(_contact,
                        connector,
                        ContactConnectorManager.ConnectorFeature.Profile);
        if (profile instanceof ActionBroadcastData) {
            final ActionBroadcastData intent = (ActionBroadcastData) profile;

            holder.profileBtn.setVisibility(View.VISIBLE);
            holder.profileBtn.setFocusable(false);
            holder.profileBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Log.d(TAG, "Profile clicked for: " + _contact);
                    ActionBroadcastData.broadcast(intent);
                }
            });
        } else {
            holder.profileBtn.setVisibility(View.GONE);
        }
    }

    public static void showDetails(final SharedPreferences prefs,
            final MapView view,
            final IndividualContact contact, final Connector connector,
            final PointMapItem marker) {

        if (prefs == null || contact == null || connector == null) {
            Log.w(TAG, "cannot show details");
            return;
        }

        final View detailsView = LayoutInflater.from(
                MapView.getMapView().getContext())
                .inflate(R.layout.contact_connector_detail, null);
        TextView contactInfoConnectorDetailText = detailsView
                .findViewById(R.id.contactInfoConnectorDetailText);
        contactInfoConnectorDetailText.setText(connector.getConnectionLabel());

        ImageView contactInfoConnectorDetailIcon = detailsView
                .findViewById(R.id.contactInfoConnectorDetailIcon);
        ATAKUtilities.SetIcon(view.getContext(),
                contactInfoConnectorDetailIcon,
                connector.getIconUri(), Color.WHITE);
        ContactListAdapter.updateConnectorView(contact, connector,
                contactInfoConnectorDetailIcon, view.getContext());

        String defaultConnector = ContactConnectorManager
                .getDefaultConnectorType(
                        prefs, contact.getUID());
        CheckBox contactInfoConnectorDetailDefaultChk = detailsView
                .findViewById(R.id.contactInfoConnectorDetailDefaultChk);
        contactInfoConnectorDetailDefaultChk.setChecked(FileSystemUtils
                .isEquals(defaultConnector, connector.getConnectionType()));

        TextView contactInfoConnectorAddressText = detailsView
                .findViewById(R.id.contactInfoConnectorAddressText);
        String s = connector.getConnectionDisplayString();
        contactInfoConnectorAddressText.setText(s);

        TextView contactInfoConnectorLastUsedText = detailsView
                .findViewById(R.id.contactInfoConnectorLastUsedText);
        long lastUsed = ContactConnectorManager.getLastUsed(prefs,
                contact.getUID(), connector.getConnectionType());
        if (lastUsed <= 0) {
            contactInfoConnectorLastUsedText.setText("--");
        } else {
            long millisNow = System.currentTimeMillis();
            long millisAgo = millisNow - lastUsed;
            contactInfoConnectorLastUsedText.setText(MathUtils
                    .GetTimeRemainingOrDateString(
                            millisNow, millisAgo, true));
        }

        TextView contactInfoConnectorHandlerText = detailsView
                .findViewById(R.id.contactInfoConnectorHandlerText);
        ContactConnectorManager.ContactConnectorHandler handler = CotMapComponent
                .getInstance().getContactConnectorMgr()
                .getHandler(connector.getConnectionType());
        if (handler == null) {
            contactInfoConnectorHandlerText.setText("--");
        } else {
            String desc = handler.getDescription();
            contactInfoConnectorHandlerText.setText(FileSystemUtils
                    .isEmpty(desc) ? "--" : desc);
        }

        View contactInfoConnectorProfileLayout = detailsView
                .findViewById(R.id.contactInfoConnectorProfileLayout);
        ImageButton contactInfoConnectorProfileBtn = detailsView
                .findViewById(R.id.contactInfoConnectorProfileBtn);
        Object profile = CotMapComponent
                .getInstance()
                .getContactConnectorMgr()
                .getFeature(contact,
                        connector,
                        ContactConnectorManager.ConnectorFeature.Profile);
        if (profile instanceof ActionBroadcastData) {
            final ActionBroadcastData intent = (ActionBroadcastData) profile;

            contactInfoConnectorProfileLayout.setVisibility(View.VISIBLE);
            contactInfoConnectorProfileBtn
                    .setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            Log.d(TAG, "Profile clicked for: " + contact);
                            ActionBroadcastData.broadcast(intent);
                        }
                    });
        } else {
            contactInfoConnectorProfileLayout.setVisibility(View.GONE);
        }

        AlertDialog.Builder b = new AlertDialog.Builder(view.getContext())
                .setIcon(R.drawable.ic_profile_white)
                .setTitle(
                        contact.getName()
                                + " "
                                + view.getContext().getString(
                                        R.string.connector_details))
                .setView(detailsView);

        if (ATAKUtilities.isSelf(view, marker)) {
            b.setNeutralButton(R.string.ok, null);
        } else {
            b.setPositiveButton("Contact",
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            Log.d(TAG, "Contacting: " + contact + ", via: "
                                    + connector);
                            CotMapComponent.getInstance()
                                    .getContactConnectorMgr()
                                    .initiateContact(contact, connector);
                        }
                    }).setNegativeButton(R.string.cancel, null);
        }

        b.show();
    }
}


package com.atakmap.android.contact;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListView;

import com.atakmap.android.cot.CotMapComponent;
import com.atakmap.android.util.ATAKUtilities;
import com.atakmap.app.R;
import com.atakmap.coremap.log.Log;

public class ContactConnectorsView extends ContactDetailView {

    public static final String TAG = "ContactConnectorsView";

    private ContactConnectorAdapter _connectorAdapter;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        _connectorAdapter = new ContactConnectorAdapter(_mapView, _prefs);

        View v = inflater.inflate(R.layout.contact_detail_connectors,
                container,
                false);

        ListView list = v
                .findViewById(R.id.contactInfo_connectorList);
        list.setAdapter(_connectorAdapter);
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view,
                    int position, long id) {
                onClick(position);
            }
        });
        list.setOnItemLongClickListener(
                new AdapterView.OnItemLongClickListener() {
                    @Override
                    public boolean onItemLongClick(AdapterView<?> parent,
                            View view,
                            int position, long id) {
                        onClick(position);
                        return true;
                    }
                });

        return v;
    }

    private void onClick(int position) {
        if (_contact == null) {
            Log.w(TAG, "No contact available");
            return;
        }

        Connector connector = (Connector) _connectorAdapter.getItem(position);
        if (ATAKUtilities.isSelf(_mapView, _marker)) {
            ContactConnectorAdapter.showDetails(_prefs, _mapView, _contact,
                    connector, _marker);
            return;
        }

        if (connector == null
                || !CotMapComponent.getInstance().getContactConnectorMgr()
                        .initiateContact(_contact, connector)) {
            Log.w(TAG,
                    "No connector handler available for: "
                            + _contact.toString());
            //TODO notify user?
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        refresh(_parent.getSelectedItem(), _parent.getSelectedContact());
    }

    @Override
    protected void refresh() {
        if (_connectorAdapter == null) {
            Log.w(TAG, "refresh not ready");
            return;
        }

        if (_contact != null) {
            _connectorAdapter.refresh(_contact, _marker);
        } else {
            cleanup();
        }
    }

    @Override
    protected void cleanup() {
        if (_connectorAdapter == null)
            return;

        _connectorAdapter.refresh(null, null);
    }
}

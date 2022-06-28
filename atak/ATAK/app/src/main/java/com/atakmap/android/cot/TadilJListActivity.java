
package com.atakmap.android.cot;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.atakmap.android.contact.Contact;
import com.atakmap.android.contact.ContactUtil;
import com.atakmap.android.contact.Contacts;
import com.atakmap.android.contact.GroupContact;
import com.atakmap.android.contact.IpConnector;
import com.atakmap.android.contact.TadilJChatConnector;
import com.atakmap.android.contact.TadilJContact;
import com.atakmap.android.contact.TadilJContactDatabase;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.metrics.activity.MetricActivity;
import com.atakmap.android.preference.AtakPreferenceFragment;
import com.atakmap.app.R;
import com.atakmap.comms.NetConnectString;
import com.atakmap.coremap.log.Log;

import java.util.ArrayList;
import java.util.List;

public class TadilJListActivity extends MetricActivity {

    public static final String TAG = "TadilJListActivity";

    private Drawable defaultStyle;
    private TadilJContactDatabase db;

    /**
     * Portrait or Landscape.
     */
    public boolean isPortrait() {
        return (getRequestedOrientation() == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
    }

    /**
     * Custom adapter for displaying a list of TadilJContact objects.
     */
    private static class TadilJAdapter extends BaseAdapter {
        private final LayoutInflater inflater;
        private final List<TadilJContact> destinations;
        private final TadilJListActivity listActivity;

        TadilJAdapter(TadilJListActivity listActivity,
                ArrayList<TadilJContact> _list) {
            super();
            this.inflater = LayoutInflater.from(listActivity);
            this.destinations = _list;
            this.listActivity = listActivity;
        }

        @Override
        public int getCount() {
            return this.destinations.size();
        }

        @Override
        public Object getItem(int postion) {
            return destinations.get(postion);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            final ViewHolder holder;
            if (convertView == null) {
                convertView = inflater.inflate(R.layout.manage_tadilj,
                        null);
                holder = new ViewHolder();
                holder.checkbox = convertView
                        .findViewById(R.id.tadilj_checkbox);
                holder.checkbox.setClickable(false); // Checkbox is toggled by selecting the list
                // item
                holder.checkbox.setFocusable(false); // Necessary so CheckBox does not intercept
                // clicks
                holder.uid = convertView //connectString
                        .findViewById(R.id.tadilj_uid);
                holder.name = convertView //description
                        .findViewById(R.id.tadilj_name);
                holder.chatNCS = convertView
                        .findViewById(R.id.tadilj_chat_ncs);
                holder.pointNCS = convertView
                        .findViewById(R.id.tadilj_point_ncs);
                holder.deleteButton = convertView
                        .findViewById(R.id.tadilj_delete);
                holder.editButton = convertView
                        .findViewById(R.id.tadilj_edit);

                convertView.setTag(holder);
            } else {
                holder = (ViewHolder) convertView.getTag();
            }

            final TadilJContact destination = destinations.get(position);
            holder.uid.setText(destination.getUID());
            holder.name.setText(destination.getName());

            NetConnectString chatNcs = ContactUtil
                    .getGeoChatIpAddress(destination);
            NetConnectString pointNcs = ContactUtil.getIpAddress(destination);
            if (chatNcs != null && pointNcs != null) {
                holder.chatNCS.setText("Chat: " + chatNcs.getHost()
                        + ":" + chatNcs.getPort());
                holder.pointNCS.setText("Point: " + pointNcs.getHost()
                        + ":" + pointNcs.getPort());
            }

            Log.d(TAG,
                    "holder is getting refreshed: " + destination.isEnabled());
            holder.checkbox.setChecked(destination.isEnabled());
            holder.checkbox.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    boolean newEnabledState = !destination.isEnabled();
                    if (newEnabledState)
                        Contacts.getInstance().addContact(
                                (GroupContact) Contacts.getInstance()
                                        .getContactByUuid("TadilJGroup"),
                                destination);
                    else
                        Contacts.getInstance().removeContactByUuid(
                                destination.getUID());
                    destination.setEnabled(newEnabledState);
                    listActivity.updateContact(destination);
                    holder.checkbox.setChecked(newEnabledState);
                }
            });
            holder.deleteButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    listActivity.showDeleteDialog(destination);
                }
            });
            holder.editButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    listActivity.showEditDialog(destination);
                }
            });

            return convertView;
        }
    }

    protected static class ViewHolder {
        TextView uid, name, chatNCS, pointNCS;
        CheckBox checkbox;
        ImageButton deleteButton, editButton;
    }

    @Override
    public void onCreate(Bundle bundle) {
        super.onCreate(bundle);

        AtakPreferenceFragment.setOrientation(this);

        setContentView(R.layout.tadilj_list_layout);

        ListView listView = findViewById(R.id.id_list);

        db = TadilJContactDatabase.getInstance();
        List<Contact> storedContacts = db.getContacts();
        for (Contact contact : storedContacts) {
            if (contact instanceof TadilJContact)
                _list.add((TadilJContact) contact);
        }

        _adapter = new TadilJAdapter(this, _list);
        listView.setAdapter(_adapter);

        listView.setItemsCanFocus(false);
        listView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
    }

    private void showDeleteDialog(final TadilJContact contact) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.remove_contact);
        builder.setMessage(getString(R.string.are_you_sure_remove)
                + contact.getName() + getString(R.string.question_mark_symbol));
        builder.setPositiveButton(R.string.yes,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int id) {
                        db.removeContact(contact.getUID());
                        Contacts.getInstance().removeContactByUuid(
                                contact.getUID());
                        _list.remove(contact);
                        _adapter.notifyDataSetChanged();
                    }
                });
        builder.setNegativeButton(R.string.no, null);
        AlertDialog dialog = builder.create();
        dialog.show();
    }

    private void showEditDialog(final TadilJContact contact) {
        AlertDialog.Builder b = new AlertDialog.Builder(this);
        LayoutInflater inflater = LayoutInflater.from(this);
        final Context context = MapView.getMapView().getContext();
        final SharedPreferences prefs = PreferenceManager
                .getDefaultSharedPreferences(context);
        final View entryView = inflater
                .inflate(
                        R.layout.tadilj_entry_dialog, null);
        final AlertDialog entryDialog = b.setTitle(R.string.details_text50)
                .setView(entryView)
                .setPositiveButton(R.string.ok, null) //Setup basic dialog.  Button backend setup below
                .setNegativeButton(R.string.cancel, null)
                .create();
        entryDialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(DialogInterface dialog) {
                final EditText nameEntry = entryView
                        .findViewById(R.id.tadilj_name_entry);
                final EditText uidEntry = entryView
                        .findViewById(R.id.tadilj_uid_entry);

                // Custom multicast
                final EditText chatIP = entryView
                        .findViewById(R.id.tadilj_chat_ip);
                final EditText chatPort = entryView
                        .findViewById(R.id.tadilj_chat_port);
                final EditText pointIP = entryView
                        .findViewById(R.id.tadilj_point_ip);
                final EditText pointPort = entryView
                        .findViewById(R.id.tadilj_point_port);
                final CheckBox defaultConn = entryView
                        .findViewById(R.id.tadilj_default_conn);

                boolean useDefConn = true;
                final NetConnectString cDef = TadilJContact.DEFAULT_CHAT_NCS;
                final NetConnectString pDef = TadilJContact.DEFAULT_POINT_NCS;
                if (contact != null) {
                    nameEntry.setText(contact.getName());
                    uidEntry.setText(contact.getUID());
                    uidEntry.setEnabled(false);
                    NetConnectString conChat = ContactUtil
                            .getGeoChatIpAddress(contact);
                    NetConnectString conPoint = ContactUtil
                            .getIpAddress(contact);
                    if (conChat != null && conPoint != null) {
                        chatIP.setText(conChat.getHost());
                        chatPort.setText(String.valueOf(conChat.getPort()));
                        pointIP.setText(conPoint.getHost());
                        pointPort.setText(String.valueOf(conPoint.getPort()));
                        useDefConn = conChat.equals(cDef)
                                && conPoint.equals(pDef);
                    }
                }

                defaultConn.setOnCheckedChangeListener(
                        new OnCheckedChangeListener() {
                            @Override
                            public void onCheckedChanged(
                                    CompoundButton cb, boolean check) {
                                chatIP.setEnabled(!check);
                                chatPort.setEnabled(!check);
                                pointIP.setEnabled(!check);
                                pointPort.setEnabled(!check);
                                if (check) {
                                    // Reset to defaults
                                    chatIP.setText(prefs.getString(
                                            "chatAddress",
                                            cDef.getHost()));
                                    chatPort.setText(prefs.getString(
                                            "chatPort",
                                            String.valueOf(cDef.getPort())));
                                    pointIP.setText(pDef.getHost());
                                    pointPort.setText(String.valueOf(pDef
                                            .getPort()));
                                }
                            }
                        });
                defaultConn.setChecked(useDefConn);

                defaultStyle = nameEntry.getBackground();
                Button positive = entryDialog
                        .getButton(DialogInterface.BUTTON_POSITIVE);
                positive.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        String name = nameEntry.getText().toString();
                        String uid = uidEntry.getText().toString();
                        if (name.trim().isEmpty()) {
                            nameEntry.setBackgroundColor(Color.RED); //Don't let empty Name
                            setTextWatcher(nameEntry);
                            return;
                        } else if (uid.isEmpty()) {
                            uidEntry.setBackgroundColor(Color.RED); //Don't let empty Uid
                            setTextWatcher(uidEntry);
                            return;
                        }

                        int existing = -1;
                        for (int i = 0; i < _list.size(); i++) {
                            if (_list.get(i).getUID().equals(uid)) {
                                existing = i;
                                break;
                            }
                        }

                        // Attempting to create a new contact with a duplicate UID
                        if (contact == null && existing != -1) {
                            Toast.makeText(context,
                                    R.string.details_text58
                                            + uid,
                                    Toast.LENGTH_LONG).show();
                            return;
                        }

                        TadilJContact newCon = (contact == null
                                ? new TadilJContact(name, uid)
                                : contact);
                        newCon.setName(name);

                        // Set custom multicast data
                        if (!defaultConn.isChecked() || contact != null) {
                            String cIP = chatIP.getText().toString(),
                                    pIP = pointIP.getText().toString();
                            if (cIP.isEmpty())
                                cIP = cDef.getHost();
                            if (pIP.isEmpty())
                                pIP = pDef.getHost();
                            int pPort, cPort;
                            try {
                                cPort = Integer.parseInt(
                                        chatPort.getText().toString());
                            } catch (Exception e) {
                                cPort = cDef.getPort();
                            }
                            try {
                                pPort = Integer.parseInt(
                                        pointPort.getText().toString());
                            } catch (Exception e) {
                                pPort = pDef.getPort();
                            }
                            IpConnector pointConn = new IpConnector(
                                    new NetConnectString("udp", pIP, pPort));
                            TadilJChatConnector chatConn = new TadilJChatConnector(
                                    new NetConnectString("udp", cIP, cPort));
                            newCon.addConnector(pointConn);
                            newCon.addConnector(chatConn);
                        }

                        // Save contact
                        if (db.addContact(newCon)) {
                            if (existing != -1)
                                Contacts.getInstance().removeContact(newCon);
                            Contacts.getInstance().addContact(
                                    (GroupContact) Contacts.getInstance()
                                            .getContactByUuid("TadilJGroup"),
                                    newCon);
                            if (existing != -1) {
                                _list.remove(existing);
                                _list.add(existing, newCon);
                            } else
                                _list.add(newCon);
                            _adapter.notifyDataSetChanged();
                            entryDialog.dismiss(); //Only close the dialog at success
                        } else {
                            Toast.makeText(TadilJListActivity.this,
                                    R.string.details_text51,
                                    Toast.LENGTH_LONG).show();
                        }
                    }
                });
            }
        });
        entryDialog.show();
    }

    @Override
    protected void onResume() {
        AtakPreferenceFragment.setOrientation(this);
        super.onResume();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.add_menu_add) {
            showEditDialog(null);
        } else if (item.getItemId() == R.id.add_menu_remove_all) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.details_text52)
                    .setPositiveButton(R.string.ok,
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog,
                                        int which) {
                                    for (TadilJContact contact : _list)
                                        Contacts.getInstance()
                                                .removeContactByUuid(
                                                        contact.getUID());
                                    db.clearTable();
                                    _list.clear();
                                    _adapter.notifyDataSetChanged();
                                }
                            })
                    .setNegativeButton(R.string.cancel, null)
                    .show();
        }
        return true;
    }

    private void setTextWatcher(final EditText editText) {
        editText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count,
                    int after) {
                editText.setBackground(defaultStyle);
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before,
                    int count) {
                //Do nothing
            }

            @Override
            public void afterTextChanged(Editable s) {
                //Do nothing
            }
        });
    }

    /**
     * Update the contact be re-adding it to the database
     * This will properly update it if it already exists
     * @param contact TADIL-J contact
     */
    protected void updateContact(TadilJContact contact) {
        db.addContact(contact);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.add_menu, menu);
        return true;
    }

    protected final ArrayList<TadilJContact> _list = new ArrayList<>();
    protected TadilJAdapter _adapter;
}

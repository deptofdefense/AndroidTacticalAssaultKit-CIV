
package com.atakmap.android.contact;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Parcelable;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.atakmap.android.attachment.DeleteAfterSendCallback;
import com.atakmap.android.util.AfterTextChangedWatcher;
import com.atakmap.android.util.SimpleItemSelectedListener;

import com.atakmap.android.chat.GeoChatService;
import com.atakmap.android.chat.ChatDatabase;
import com.atakmap.android.chat.ChatManagerMapComponent;
import com.atakmap.android.chat.GeoChatConnectorHandler;
import com.atakmap.android.contact.ContactFilter.FilterMode;
import com.atakmap.android.contact.ContactListAdapter.RefreshListener;
import com.atakmap.android.contact.ContactListAdapter.ViewMode;
import com.atakmap.android.contact.ContactConnectorManager.ContactConnectorHandler;
import com.atakmap.android.cot.CotMapComponent;
import com.atakmap.android.dropdown.DropDown;
import com.atakmap.android.dropdown.DropDownReceiver;
import com.atakmap.android.hierarchy.HierarchyListFilter;
import com.atakmap.android.hierarchy.HierarchyListItem;
import com.atakmap.android.hierarchy.HierarchyListItem.Sort;
import com.atakmap.android.hierarchy.HierarchyListItem.ComparatorSort;
import com.atakmap.android.hierarchy.SortSpinner;
import com.atakmap.android.hierarchy.filters.FOVFilter;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.AnchoredMapItem;
import com.atakmap.android.maps.DefaultMetaDataHolder;
import com.atakmap.android.maps.MapEvent;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Marker;
import com.atakmap.android.maps.PointMapItem;
import com.atakmap.android.maps.Shape;
import com.atakmap.android.missionpackage.api.MissionPackageApi;
import com.atakmap.android.missionpackage.file.MissionPackageManifest;
import com.atakmap.app.R;
import com.atakmap.comms.ReportingRate;
import com.atakmap.coremap.cot.event.CotEvent;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.UUID;

public class ContactPresenceDropdown extends DropDownReceiver
        implements DropDown.OnStateListener, OnCheckedChangeListener {

    public static final String TAG = "ContactPresenceDropdown";

    //Display normal/presence contact list
    public static final String PRESENCE_LIST = "com.atakmap.android.contact.CONTACT_LIST";
    //Geo-chat display
    public static final String GEO_CHAT_LIST = "com.atakmap.android.contact.GEO_CHAT";
    //Display contact list for sending data
    public static final String SEND_LIST = "mil.arl.atak.CONTACT_LIST";
    // Refresh list
    public static final String REFRESH_LIST = "com.atakmap.android.contact.REFRESH_LIST";
    // Send directly to a list of contacts using the same logic employed by SEND_LIST
    public static final String SEND_TO_CONTACTS = "com.atakmap.android.contact.SEND_TO_CONTACTS";

    private final Context _context;
    private final SharedPreferences _prefs;

    // Current view mode and base view mode
    private ViewMode mode = ViewMode.DEFAULT;
    private ViewMode baseMode = ViewMode.DEFAULT;
    private String openAction;
    private boolean reOpening = false;

    private final ActionStack prevAction = new ActionStack();
    private final Set<String> _favUIDs = new HashSet<>();

    private final ContactListAdapter adapter;
    private ListView list;

    private ImageButton _newGroupBtn, _deleteGroupBtn, _addGroupUser,
            _delGroupUser, _backBtn, _searchButton, _historyBtn, _favBtn,
            _filterMultiselectBtn;
    private LinearLayout _groupActions, _checkAllLayout,
            _filtersLayout, _sendLayout;
    private SortSpinner _sortSpinner;
    private CheckBox _checkAll, _unreadOnly, _showAll, _showAllTop,
            _hideFiltered;
    private Button _titleBtn, _actionBtn;
    private View _titleLayout, _titleArrow;
    private EditText _userEntryEt;
    private boolean _selectModeActive = false;
    private boolean hideFiltered = false;

    // Connector filters
    private Spinner _connSpinner;
    private String[] _connectors;

    // Add/remove group users
    private GroupContact _group = null;

    // Search function
    private final SearchResults _searchResults;
    private String _searchTerms = "";

    // General purpose custom list
    private final GroupContact _customList;

    // Master contacts list
    protected final Contacts _contacts;

    public ContactPresenceDropdown(MapView mapView,
            ContactListAdapter contactAdapter, Contacts contacts) {
        super(mapView);
        this.adapter = contactAdapter;
        _context = mapView.getContext();
        _searchResults = new SearchResults();
        _searchResults.setIgnoreStack(true);
        _customList = new GroupContact("CustomList", "", false);
        _customList.setIgnoreStack(true);
        _contacts = contacts;
        _prefs = PreferenceManager.getDefaultSharedPreferences(_context);
    }

    public ContactPresenceDropdown(MapView mapView,
            ContactListAdapter contactAdapter) {
        this(mapView, contactAdapter, Contacts.getInstance());
    }

    @Override
    public void disposeImpl() {
        //Do nothing
    }

    @Override
    public void onReceive(final Context context, Intent intent) {
        String action = intent.getAction();
        if (action == null)
            return;

        switch (action) {
            // Refresh the display
            case REFRESH_LIST:
                if (adapter != null && !isClosed())
                    adapter.refreshList(intent.getStringExtra("reason"));
                return;

            // Directly send data to a list of contacts
            case SEND_TO_CONTACTS: {
                String[] contactUIDs = intent
                        .getStringArrayExtra("contactUIDs");
                if (FileSystemUtils.isEmpty(contactUIDs))
                    return;
                SendRequest req = new SendRequest(intent);
                sendToContacts(req, Arrays.asList(contactUIDs));
                return;
            }
        }

        if (!isClosed() && openAction != null && !openAction.equals(action)) {
            reOpening = true;
            closeDropDown();
        }
        openAction = action;

        Log.d(TAG, "Received event: " + action);

        LayoutInflater inf = LayoutInflater.from(getMapView().getContext());
        View root = inf.inflate(R.layout.chat_manager_list_view, null);
        root.setBackgroundColor(Color.BLACK);

        list = root.findViewById(R.id.chat_presence_list);
        list.setAdapter(adapter);

        _backBtn = root.findViewById(R.id.chat_contact_back_btn);
        OnClickListener backListener = new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mode == ViewMode.FAVORITES || !popList()) {
                    if (!popMode())
                        _connSpinner.performClick();
                }
            }
        };

        List<ContactConnectorHandler> handlers = CotMapComponent.getInstance()
                .getContactConnectorMgr().getHandlers();
        _connectors = new String[handlers.size() + 1];
        _connectors[0] = Contacts.getInstance().getRootGroup().getName();
        for (int i = 0; i < handlers.size(); i++)
            _connectors[i + 1] = handlers.get(i).getName();

        ArrayAdapter<String> sa = new ArrayAdapter<>(_context,
                android.R.layout.simple_spinner_dropdown_item, _connectors);
        sa.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item);
        _connSpinner = root.findViewById(R.id.contact_filter_spinner);
        _connSpinner.setAdapter(sa);
        _connSpinner
                .setOnItemSelectedListener(
                        new SimpleItemSelectedListener() {
                            @Override
                            public void onItemSelected(AdapterView<?> parent,
                                    View view, int position, long id) {
                                String name = (String) parent
                                        .getItemAtPosition(position);
                                Log.d(TAG,
                                        "Selected connector filter: " + name);
                                setConnectorFilter(name);
                            }

                        });

        ImageButton closeBtn = root.findViewById(R.id.close_hmv);
        closeBtn.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                closeDropDown();
            }
        });

        _titleLayout = root.findViewById(R.id.chat_contact_title_layout);
        _titleBtn = root
                .findViewById(R.id.chat_contact_title_textview);
        _titleArrow = root.findViewById(R.id.chat_contact_title_arrow);

        _userEntryEt = root
                .findViewById(R.id.chat_contact_text_entry);
        _userEntryEt.addTextChangedListener(new AfterTextChangedWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                updateSearch(s.toString());
            }
        });
        _userEntryEt.setOnFocusChangeListener(_keyboardFocus);
        _userEntryEt.setInputType(InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);

        _backBtn.setOnClickListener(backListener);
        _titleBtn.setOnClickListener(backListener);

        _historyBtn = root
                .findViewById(R.id.chat_contact_history_btn);
        _historyBtn.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                List<String> historyEntries = ChatDatabase
                        .getInstance(_context).getAvailableConversations();
                pushMode(ViewMode.HISTORY);
                adapter.setCustomUIDs(historyEntries);
                adapter.refreshList("History mode activated");
            }
        });

        _favBtn = root.findViewById(R.id.chat_favorites_btn);
        _favBtn.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                showCustomList(context.getString(R.string.favorites),
                        getFavorites());
                if (mode != ViewMode.SEND_LIST)
                    pushMode(ViewMode.FAVORITES);
            }
        });

        _groupActions = root.findViewById(R.id.group_actions);
        _checkAllLayout = root
                .findViewById(R.id.selectAll_layout);
        _checkAll = root.findViewById(R.id.selectAll_cb);
        _checkAllLayout.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                _checkAll.toggle();
            }
        });
        _checkAll.setOnCheckedChangeListener(this);

        _sendLayout = root
                .findViewById(R.id.contact_send_layout);
        _filtersLayout = root.findViewById(R.id.filters_layout);

        _filterMultiselectBtn = root
                .findViewById(R.id.chat_filter_multiselect_btn);
        _filterMultiselectBtn.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                pushMode(ViewMode.FILTER);
                filterContacts();
            }
        });

        _addGroupUser = root.findViewById(
                R.id.chat_contact_add_users_btn);
        _addGroupUser.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mode == ViewMode.FAVORITES) {
                    _group = _customList;
                    addFavorites();
                } else {
                    _group = adapter.getCurrentList();
                    addUsersToGroup();
                }
            }
        });

        _delGroupUser = root.findViewById(
                R.id.chat_contact_delete_users_btn);
        _delGroupUser.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mode == ViewMode.FAVORITES) {
                    _group = _customList;
                    delFavorites();
                } else {
                    _group = adapter.getCurrentList();
                    deleteUsersFromGroup();
                }
            }
        });

        _newGroupBtn = root
                .findViewById(R.id.chat_contact_new_group_btn);
        _newGroupBtn.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                createChatGroup();
            }
        });

        _deleteGroupBtn = root
                .findViewById(R.id.chat_contact_delete_group_btn);
        _deleteGroupBtn.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                deleteChatGroup();
            }
        });

        _sortSpinner = root.findViewById(R.id.sort_spinner);
        _sortSpinner.setOnItemSelectedListener(
                new SimpleItemSelectedListener() {
                    @Override
                    public void onItemSelected(AdapterView<?> parent,
                            View v, int pos,
                            long id) {
                        Sort sort = (Sort) _sortSpinner.getItemAtPosition(pos);
                        if (sort != null) {
                            adapter.sort(sort);
                            setViewToMode();
                        }
                    }

                });
        List<Sort> sorts = new ArrayList<>();
        sorts.add(new ComparatorSort(Contact.COMPARE_ALPHA,
                "Alphabetical", R.drawable.alpha_sort));
        sorts.add(new ComparatorSort(Contact.COMPARE_STATUS,
                "Status", R.drawable.status_sort));
        sorts.add(new ComparatorSort(Contact.COMPARE_UNREAD,
                "Unread", R.drawable.unread_sort));
        sorts.add(new ComparatorSort(Contact.COMPARE_LOCATION,
                "Distance", R.drawable.prox_sort));
        _sortSpinner.setSortModes(sorts);
        _sortSpinner.setSelection(0);

        _actionBtn = root.findViewById(R.id.chat_contact_action_btn);

        _searchButton = root
                .findViewById(R.id.chat_contact_search_btn);
        _searchButton.setOnClickListener(_searchListener);

        boolean showAll = !_prefs.getBoolean(FOVFilter.PREF, false);
        _showAll = root.findViewById(R.id.showAll_cb);
        _showAll.setChecked(showAll);
        _showAll.setOnCheckedChangeListener(this);

        _showAllTop = root.findViewById(R.id.showAll_cb_top);
        _showAllTop.setChecked(showAll);
        _showAllTop.setOnCheckedChangeListener(this);

        adapter.showAll(showAll);

        _unreadOnly = root.findViewById(R.id.unread_cb);
        _unreadOnly.setOnCheckedChangeListener(this);

        _hideFiltered = root.findViewById(R.id.filtered_cb);
        _hideFiltered.setOnCheckedChangeListener(this);

        list.setOnItemLongClickListener(new ListView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(final AdapterView<?> parent,
                    View view, int position, long id) {

                onRowSelected((Contact) adapter.getItem(position));
                return true;
            }
        });

        switch (action) {
            case SEND_LIST:
                // Prepare list of contacts to send items/files to
                if (!prepareContactList(root, intent))
                    return;
                break;
            case GEO_CHAT_LIST:
                // Geo-chat (history, search, group management)
                setBaseViewMode(ViewMode.GEO_CHAT);
                break;
            default:
                // Default
                setBaseViewMode(ViewMode.DEFAULT);

                final String connector = intent.getStringExtra("connector");
                if (!FileSystemUtils.isEmpty(connector) && sa != null) {
                    int pos = sa.getPosition(connector);
                    if (pos > -1)
                        _connSpinner.setSelection(pos);
                }
                break;
        }
        _unreadOnly.setChecked(showUnreadOnly());

        list.setOnItemClickListener(this.itemClickListener);

        setRetain(true);

        //TODO add chat mode (separate from main contact list), and associate chat prefs in that case
        //        if(this.mode == ViewMode.GEOCHAT) {
        //            setAssociationKey("chatPreference");
        //        }
        showDropDown(root, 0.4d, FULL_HEIGHT, FULL_WIDTH, HALF_HEIGHT,
                false, true, this);
        adapter.refreshList("Drop-down activated");
    }

    @Override
    public void onCheckedChanged(CompoundButton cb, boolean checked) {
        if (cb == _checkAll) {
            if (checked)
                adapter.selectAll();
            else
                adapter.unSelectAll();
        } else if (cb == _showAll || cb == _showAllTop)
            adapter.showAll(checked);
        else if (cb == _unreadOnly)
            adapter.unreadOnly(checked);
        else if (cb == _hideFiltered) {
            hideFiltered = !hideFiltered;
            adapter.hideFiltered(checked);
        }
    }

    private void setConnectorFilter(String name) {
        Log.d(TAG, "setConnectorFilter: " + name);
        ContactConnectorHandler handler = CotMapComponent
                .getInstance()
                .getContactConnectorMgr()
                .getHandlerByName(name);
        if (adapter != null)
            adapter.setConnectorFilter(handler);
        if (baseMode != ViewMode.SEND_LIST) {
            if (handler instanceof GeoChatConnectorHandler)
                setBaseViewMode(ViewMode.GEO_CHAT);
            else
                setBaseViewMode(ViewMode.DEFAULT);
        } else {
            setViewToMode();
        }
    }

    private boolean prepareContactList(final View root, final Intent intent) {
        Bundle extras = intent.getExtras();
        if (extras == null)
            return false;

        setBaseViewMode(ViewMode.SEND_LIST);

        final SendRequest req = new SendRequest(intent);

        Button share = root
                .findViewById(R.id.contact_list_button_share_selected);
        share.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (sendToContacts(req, adapter.getSelectedUids())) {
                    // disp.shareWithContacts(selectedContacts, cotEvent, cotType);
                    adapter.loadView();
                    closeDropDown();
                }
            }
        });

        Button sendBroadcast = root
                .findViewById(R.id.contact_list_button_send_to_all);
        if (req.filename != null || req.imageFilename != null
                || req.disableBroadcast) {
            sendBroadcast.setVisibility(View.INVISIBLE);
        } else {
            sendBroadcast.setVisibility(View.VISIBLE);
        }
        sendBroadcast.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                sendBroadcast(req);
                adapter.loadView();
                closeDropDown();
            }
        });

        if (req.uid != null) {
            MapItem mi = getMapView().getRootGroup().deepFindUID(req.uid);
            if (mi instanceof PointMapItem)
                setSelected(mi, "asset:/icons/outline.png");
        }
        return true;
    }

    private static class SendRequest {

        private final Intent intent;

        // UID of map item(s)
        private final String uid;
        private final String[] uids;

        // GeoChat message
        private final String msg;

        // CoT event(s)
        private final CotEvent cotEvent;
        private final ArrayList<CotEvent> cotEvents;

        // File transfer CoT events
        private final Map<String, CotEvent> ftrMap = new HashMap<>();

        // Callback intent action
        private final Intent sendCallback;

        // File path to send
        private final String filename;

        // Image file path
        private final String imageFilename;

        // True to disable broadcast imageFilename
        private final boolean disableBroadcast;

        SendRequest(Intent intent) {
            this.intent = intent;
            this.uid = intent.getStringExtra("targetUID");
            this.uids = this.uid != null ? new String[] {
                    this.uid
            }
                    : intent.getStringArrayExtra("targetsUID");
            this.msg = intent.getStringExtra("message");

            this.cotEvent = intent
                    .getParcelableExtra("com.atakmap.contact.CotEvent");
            this.cotEvents = (ArrayList<CotEvent>) intent.getSerializableExtra(
                    "com.atakmap.contact.MultipleCotEvents");

            // Mapped TAK server connect strings to file transfer cot events above
            String[] ftrConns = intent.getStringArrayExtra("FileTransferConns");
            if (!FileSystemUtils.isEmpty(ftrConns)
                    && !FileSystemUtils.isEmpty(this.cotEvents)) {
                int i = 0;
                for (CotEvent ce : this.cotEvents) {
                    if (ce == null || !ce.isValid() || ce.getDetail() == null
                            || ce.getDetail().getFirstChildByName(0,
                                    "fileshare") == null)
                        continue;
                    ftrMap.put(ftrConns[i], ce);
                    i++;
                }
            }

            // Intent to fire when contacts are selected, instead of immediately sending
            Intent sendCallback = null;
            if (intent.hasExtra("sendCallback")) {
                Object o = intent.getExtras().get("sendCallback");
                if (o instanceof String)
                    sendCallback = new Intent((String) o);
                else if (o instanceof Parcelable)
                    sendCallback = intent.getParcelableExtra("sendCallback");
            }
            this.sendCallback = sendCallback;

            // This is used in FileSharing
            this.filename = intent.getStringExtra("filename");

            // This is used when embedding an image in CoT
            this.imageFilename = intent.getStringExtra("imageFilename");

            // see if sender desires no broadcast option
            this.disableBroadcast = intent.getBooleanExtra("disableBroadcast",
                    false);
        }
    }

    /**
     * Send data to a list of contacts using various methods
     * @param req Send request
     * @param toUIDs Contact UIDs to send to
     * @return True if sent successfully
     */
    private boolean sendToContacts(SendRequest req, List<String> toUIDs) {
        // Nothing to do
        if (req.sendCallback == null && req.cotEvent == null
                && req.filename == null
                && FileSystemUtils.isEmpty(req.cotEvents)
                && FileSystemUtils.isEmpty(req.uids)
                && FileSystemUtils.isEmpty(req.msg))
            return false;

        if (toUIDs.isEmpty()) {
            // Nothing selected
            Toast.makeText(
                    getMapView().getContext(),
                    R.string.chat_text21,
                    Toast.LENGTH_SHORT).show();
            return false;
        }

        // Multi-convo message sending
        if (req.msg != null) {
            List<Contact> convos = new ArrayList<>();
            for (String u : toUIDs) {
                Contact c = _contacts.getContactByUuid(u);
                if (c != null)
                    convos.add(c);
            }
            if (convos.isEmpty()) {
                Toast.makeText(getMapView().getContext(),
                        R.string.chat_text21, Toast.LENGTH_SHORT)
                        .show();
                return false;
            }
            ChatManagerMapComponent.getInstance().sendMessage(
                    req.msg, convos);
            return true;
        }

        Map<String, IndividualContact> uniqueSelected = new HashMap<>();
        for (String u : toUIDs) {
            Contact contact = _contacts.getContactByUuid(u);
            if (GroupContact.isGroup(contact)) {
                for (Contact c : contact
                        .getFiltered(true)) {
                    if (c instanceof IndividualContact)
                        uniqueSelected.put(c.getUID(),
                                (IndividualContact) c);
                }
            } else if (contact instanceof IndividualContact)
                uniqueSelected.put(u, (IndividualContact) contact);
        }
        if (uniqueSelected.isEmpty()) {
            // Groups are empty
            Toast.makeText(getMapView().getContext(),
                    R.string.chat_text22,
                    Toast.LENGTH_SHORT).show();
            return false;
        }

        // First, send to all the plugins... i.e., via intents
        List<IndividualContact> contacts = new ArrayList<>(
                uniqueSelected.values());
        for (IndividualContact ic : contacts) {
            if (ic == null)
                continue;

            if (!req.ftrMap.isEmpty()) {
                // Workaround for Mission Package sending issue
                // TODO: Move MP implementation to Common Commo
                String serverFrom = ic.getServerFrom();
                CotEvent ce = req.ftrMap.get(String.valueOf(
                        serverFrom));
                if (ce != null) {
                    dispatchCot(ce, req.imageFilename,
                            new IndividualContact[] {
                                    ic
                            });
                    uniqueSelected.remove(ic.getUID());
                    continue;
                }
            }

            IpConnector ipConn = (IpConnector) ic.getConnector(
                    IpConnector.CONNECTOR_TYPE);
            if (ipConn != null && !FileSystemUtils.isEmpty(
                    ipConn.getSendIntent())) {
                // Send intent instead of sending by normal means
                Intent sendIntent = new Intent(req.intent);
                sendIntent.setAction(ipConn.getSendIntent());
                sendIntent.putExtra("contactUID", ic.getUID());
                AtakBroadcast.getInstance().sendBroadcast(
                        sendIntent);
                uniqueSelected.remove(ic.getUID());
                continue;
            }
            if (req.uids == null
                    || ContactUtil.getIpAddress(ic) != null)
                continue;
            for (String uid : req.uids) {
                Log.d(TAG,
                        "Sending an intent for plugin ContactListItem: "
                                + ic.getName() + " to "
                /*+ item.intentAction*/);
                Intent forPlugin = createIntentForPlugin(
                        req.intent, ic, uid, req.cotEvent);
                Log.d(TAG,
                        " intent has uid: "
                                + forPlugin
                                        .getStringExtra("uid"));
                AtakBroadcast.getInstance()
                        .sendBroadcast(forPlugin);
            }
        }

        if (!uniqueSelected.isEmpty()) {
            IndividualContact[] allSelected = uniqueSelected
                    .values().toArray(new IndividualContact[0]);

            if (req.sendCallback != null) {
                Intent cb = new Intent(req.sendCallback);
                cb.putExtras(req.intent);
                cb.putExtra("sendTo", uniqueSelected.keySet()
                        .toArray(new String[0]));
                // Uses LocalBroadcast to mitigate any risk.  Cannot be manipulated externally.
                AtakBroadcast.getInstance().sendBroadcast(cb);
            } else if (req.uids != null) {
                sendCot(req.uids, allSelected);
            } else if (req.cotEvent != null) {
                dispatchCot(req.cotEvent, req.imageFilename, allSelected);
            } else if (req.cotEvents != null) {
                for (CotEvent ce : req.cotEvents)
                    dispatchCot(ce, req.imageFilename, allSelected);
            } else {
                sendFile(req.filename, allSelected);
            }
        }
        return true;
    }

    private void sendBroadcast(SendRequest req) {
        if (req.sendCallback != null) {
            Intent cb = new Intent(req.sendCallback);
            cb.putExtra("sendTo", new String[] {
                    GeoChatService.DEFAULT_CHATROOM_NAME
            });
            AtakBroadcast.getInstance().sendBroadcast(cb);
        } else if (req.uids != null) {
            sendCot(req.uids, null);
        } else if (req.cotEvent != null) {
            dispatchCot(req.cotEvent, req.imageFilename, null);
        } else if (req.cotEvents != null) {
            for (CotEvent ce : req.cotEvents)
                dispatchCot(ce, req.imageFilename, null);
        } else if (req.filename != null) {
            sendFile(req.filename, null);
        } else if (req.msg != null) {
            List<Contact> dests = new ArrayList<>();
            dests.add(Contacts.getInstance().getContactByUuid(
                    GeoChatService.DEFAULT_CHATROOM_NAME));
            ChatManagerMapComponent.getInstance().sendMessage(
                    req.msg, dests);
        }
    }

    /**
     * Bring up keyboard when focused on text input
     */
    private final View.OnFocusChangeListener _keyboardFocus = new View.OnFocusChangeListener() {
        @Override
        public void onFocusChange(View view, boolean b) {
            InputMethodManager imm = (InputMethodManager) _context
                    .getSystemService(Context.INPUT_METHOD_SERVICE);

            if (imm != null) {
                if (b)
                    imm.showSoftInput(view,
                            InputMethodManager.SHOW_IMPLICIT);
                else
                    imm.hideSoftInputFromWindow(
                            view.getWindowToken(), 0);
            }
        }
    };

    /**
     * Click listener for contacts
     */
    private final OnItemClickListener itemClickListener = new OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position,
                long id) {

            onRowSelected((Contact) adapter.getItem(position));
        }
    };

    private void onRowSelected(Contact contact) {
        if (contact == null) {
            Log.w(TAG, "Could not identify clicked contact");
            return;
        }

        String contactUID = contact.getUID();

        if (_selectModeActive) {
            if (GroupContact.isGroup(contact)) {
                pushList((GroupContact) contact);
            } else {
                List<String> selected = adapter.getSelectedUids();
                if (!selected.contains(contactUID))
                    selected.add(contactUID);
                else
                    selected.remove(contactUID);
                adapter.setSelectedUids(selected);
            }
            return;
        }

        //Log.d(TAG, "clicked: " + contact.getName());

        if (GroupContact.isGroup(contact)) {
            if (mode == ViewMode.HISTORY && contact.getChildCount() == 0)
                // auto-open history when there are no children elements
                adapter.openGeoChatWindow(contact);
            else
                pushList((GroupContact) contact);
        } else if (contact instanceof IndividualContact) {
            if (!((IndividualContact) contact).onSelected(_prefs)) {
                Log.w(TAG, "Failed to select row: " + contact);
            }
        }
    }

    /**
     * Load favorite contacts list from file
     */
    private void loadFavorites() {
        _favUIDs.clear();
        String path = FileSystemUtils.getItem(
                FileSystemUtils.TOOL_DATA_DIRECTORY)
                .getPath() + File.separator + "favorites"
                + File.separator + "contacts.txt";
        try {
            File f = new File(path);
            if (IOProviderFactory.isDirectory(f))
                FileSystemUtils.deleteDirectory(f, false);
            if (IOProviderFactory.exists(f)) {
                try (BufferedReader reader = new BufferedReader(
                        IOProviderFactory.getFileReader(f))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        line = line.replace("\n", "");
                        if (!line.isEmpty())
                            _favUIDs.add(line);
                    }
                }

            } else {
                File fd = (new File(path)).getParentFile();
                if (!IOProviderFactory.mkdir(fd))
                    Log.w(TAG,
                            "Failed to create directory: "
                                    + ((fd == null) ? "fd == null"
                                            : fd.getAbsolutePath()));
            }
        } catch (IOException ignored) {
        }
    }

    private List<Contact> getFavorites() {
        List<Contact> contacts = new ArrayList<>();
        for (String uid : _favUIDs) {
            Contact c = _contacts.getContactByUuid(uid);
            if (c != null)
                contacts.add(c);
        }
        return contacts;
    }

    /**
     * Save favorites list to file
     */
    private void saveFavorites() {
        String path = FileSystemUtils.getItem(
                FileSystemUtils.TOOL_DATA_DIRECTORY)
                .getPath() + File.separator + "favorites"
                + File.separator + "contacts.txt";
        try {
            File f = new File(path);
            File fd = f.getParentFile();
            if (!IOProviderFactory.exists(fd) && !IOProviderFactory.mkdir(fd)) {
                Log.w(TAG, "Failed to create directory"
                        + ((fd == null) ? "fd == null" : fd.getAbsolutePath()));
                return;
            }
            try (BufferedWriter writer = new BufferedWriter(
                    IOProviderFactory.getFileWriter(f))) {
                for (String uid : _favUIDs)
                    writer.write(uid + "\n");
            }
        } catch (IOException i) {
            Log.e(TAG, "Failed to create favorites file: " + path, i);
        }
    }

    /**
     * Display a custom list contacts
     * @param title Title to display in the custom list
     * @param contacts List of contacts to display
     */
    private void showCustomList(String title, List<Contact> contacts) {
        _customList.setName(title);
        _customList.setContacts(contacts);
        if (adapter.getCurrentList() != _customList)
            pushList(_customList);
        else
            adapter.refreshList("Using custom list");
    }

    /**
     * Begin adding users to the current group
     */
    private void addUsersToGroup() {
        if (!editableGroup(_group)) {
            Toast.makeText(_context, _context.getString(R.string.chat_text8),
                    Toast.LENGTH_LONG).show();
            return;
        }
        pushMode(ViewMode.ADD_TO_GROUP);
        _actionBtn.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                // Make sure we don't add any groups here
                // See ATAK-9706, ATAK-10640, ATAK-10641
                List<Contact> selected = adapter.getSelected();
                List<Contact> users = new ArrayList<>(selected.size());
                for (Contact c : selected) {
                    if (c instanceof IndividualContact)
                        users.add(c);
                }
                if (!users.isEmpty()) {
                    _group.addContacts(users);
                    ChatDatabase.getInstance(_context).addGroup(
                            _group, true);
                    GroupContact root = _group.getRootUserGroup();
                    List<String> dest = root != null ? root
                            .getUserUIDs(true) : _group.getUserUIDs();
                    ChatManagerMapComponent.getInstance().persistMessage(
                            ChatManagerMapComponent.NEW_CONTACT_MESSAGE,
                            _group, dest);
                }
                popMode();
                adapter.refreshList("Added users to group");
            }
        });
    }

    /**
     * Begin removing users from the current group
     */
    private void deleteUsersFromGroup() {
        if (!editableGroup(_group)) {
            Toast.makeText(_context, _context.getString(R.string.chat_text8),
                    Toast.LENGTH_LONG).show();
            return;
        }
        if (_group.getAllContacts(false).isEmpty()) {
            Toast.makeText(_context, _context.getString(R.string.chat_text9),
                    Toast.LENGTH_LONG).show();
            return;
        }
        pushMode(ViewMode.DEL_FROM_GROUP);
        _actionBtn.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                // Remove contact
                _group.removeContacts(adapter.getSelected());
                ChatDatabase.getInstance(_context).addGroup(
                        _group, true);

                // Send "updated contacts" message to remaining users
                GroupContact root = _group.getRootUserGroup();
                List<String> dest = root != null ? root
                        .getUserUIDs(true) : _group.getUserUIDs();
                ChatManagerMapComponent.getInstance().persistMessage(
                        ChatManagerMapComponent.NEW_CONTACT_MESSAGE,
                        _group, dest);

                // Send kick message to removed users
                List<String> var = adapter.getSelectedUids();
                ChatManagerMapComponent.getInstance().sendMessage(
                        ChatManagerMapComponent.REMOVE_MESSAGE,
                        _group, dest.toArray(new String[0]),
                        var.toArray(new String[0]));

                popMode();
                adapter.refreshList("Removed users from group");
            }
        });
    }

    private void addFavorites() {
        if (mode != ViewMode.FAVORITES ||
                adapter.getCurrentList() != _customList)
            return;
        pushList(_customList);
        pushMode(ViewMode.ADD_TO_GROUP);
        _actionBtn.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                _favUIDs.addAll(adapter.getSelectedUids());
                saveFavorites();
                _customList.setContacts(getFavorites());
                popMode();
                adapter.refreshList("Updated favorites");
            }
        });
    }

    private void delFavorites() {
        if (mode != ViewMode.FAVORITES ||
                adapter.getCurrentList() != _customList)
            return;
        pushList(_customList);
        pushMode(ViewMode.DEL_FROM_GROUP);
        _actionBtn.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                _favUIDs.removeAll(adapter.getSelectedUids());
                saveFavorites();
                _customList.setContacts(getFavorites());
                popMode();
                adapter.refreshList("Updated favorites");
            }
        });
    }

    private final OnClickListener _searchListener = new OnClickListener() {
        @Override
        public void onClick(View v) {
            _searchTerms = "";
            if (mode == ViewMode.SEARCH)
                popMode();
            else
                pushMode(ViewMode.SEARCH);
        }
    };

    private final RefreshListener _searchRefresh = new RefreshListener() {
        @Override
        public void onRefreshFinished(Contact c) {
            if (_searchResults.getSearchList() == c) {
                updateListButtons();
                updateSearch(_searchTerms);
            }
        }
    };

    private void updateSearch(String terms) {
        if (mode != ViewMode.SEARCH || terms == null)
            return;
        boolean termsChanged = !_searchTerms.equals(terms);
        _searchTerms = terms;
        GroupContact list = getLastList(_searchResults);
        _searchResults.setSearchList(list);
        if (list == null)
            return;
        List<Contact> results = new ArrayList<>();
        for (HierarchyListItem item : list.find(_searchTerms)) {
            if (item instanceof Contact)
                results.add((Contact) item);
        }
        _searchResults.setContacts(results);
        if (adapter.getCurrentList() != _searchResults)
            pushList(_searchResults);
        else if (termsChanged)
            adapter.refreshList("Updating search: " + terms);
    }

    private void filterContacts() {
        adapter.unSelectAll();
        List<Contact> allContacts = adapter.getCurrentList()
                .getAllContacts(false);
        List<String> idsToSelect = new ArrayList<String>();
        for (Contact c : allContacts) {
            if (FilteredContactsManager.getInstance().isContactFiltered(c))
                idsToSelect.add(c.contactUUID);
        }
        adapter.setSelectedUids(idsToSelect);

        _actionBtn.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                List<Contact> selectedContacts = adapter.getSelected();
                for (Contact c : selectedContacts) {
                    if (!FilteredContactsManager.getInstance()
                            .isContactFiltered(c)) {
                        FilteredContactsManager.getInstance()
                                .setContactFiltered(c, true, false);
                    }
                }

                List<Contact> unselectedContacts = adapter.getUnselected();
                for (Contact c : unselectedContacts) {
                    if (FilteredContactsManager.getInstance()
                            .isContactFiltered(c)) {
                        FilteredContactsManager.getInstance()
                                .setContactFiltered(c, false, false);
                    }
                }

                // dispatch intent
                FilteredContactsManager.getInstance()
                        .fireFilteredContactsChangedIntent();
                // update total unread indicator
                Contacts.getInstance().updateTotalUnreadCount();

                popMode();
            }
        });
    }

    /**
     * Get the top list in the stack that isn't equal to 'list'
     * @param list List that should be ignored
     * @return Group list
     */
    private GroupContact getLastList(Contact list) {
        if (adapter.getCurrentList() != list)
            return adapter.getCurrentList();
        for (int i = prevAction.size() - 1; i >= 0; i--) {
            Object o = prevAction.get(i);
            if (o instanceof GroupContact && o != list)
                return (GroupContact) o;
        }
        return null;
    }

    private void setViewToMode() {
        // Define most common cases to cut down on clutter
        multiSelect(false);
        _sortSpinner.setVisibility(View.VISIBLE);
        _userEntryEt.setVisibility(View.GONE);
        _titleLayout.setVisibility(View.VISIBLE);
        _filtersLayout.setVisibility(View.VISIBLE);
        _sendLayout.setVisibility(View.GONE);
        _showAllTop.setVisibility(View.GONE);
        _filterMultiselectBtn.setVisibility(View.GONE);
        if (mode != ViewMode.GEO_CHAT) {
            _searchButton.setVisibility(View.GONE);
            _historyBtn.setVisibility(View.GONE);
            _favBtn.setVisibility(View.GONE);
            adapter.setExtraViewActive(false);
        }
        updateListButtons();
        List<FilterMode> dispModes = new ArrayList<>();
        switch (mode) {
            case DEFAULT:
                _filterMultiselectBtn.setVisibility(View.VISIBLE);
                _searchButton.setVisibility(View.VISIBLE);
                _favBtn.setVisibility(View.VISIBLE);
                _hideFiltered.setVisibility(View.GONE);
                _showAll.setVisibility(View.VISIBLE);
                _unreadOnly.setVisibility(View.VISIBLE);
                _hideFiltered.setChecked(hideFiltered);
                break;
            case GEO_CHAT:
                adapter.setExtraViewActive(true);
                //_backBtn.setVisibility(View.GONE);
                _searchButton.setVisibility(View.VISIBLE);
                _historyBtn.setVisibility(View.VISIBLE);
                _favBtn.setVisibility(View.VISIBLE);
                break;
            case SEND_LIST:
                multiSelect(true);
                _actionBtn.setVisibility(View.GONE);
                _favBtn.setVisibility(View.VISIBLE);
                _filtersLayout.setVisibility(View.GONE);
                _sendLayout.setVisibility(View.VISIBLE);
                _showAllTop.setVisibility(View.VISIBLE);
                dispModes.add(FilterMode.UID_BLACKLIST);
                List<String> excluded = new ArrayList<>();
                excluded.add(GeoChatService.DEFAULT_CHATROOM_NAME);
                adapter.setCustomUIDs(excluded);
                break;
            case SELECT_W_ACTION:
                multiSelect(true);
                break;
            case ADD_GROUP:
            case ADD_TO_GROUP:
                multiSelect(true);
                _groupActions.setVisibility(View.GONE);
                pushList(_contacts.getRootGroup());
                dispModes.add(FilterMode.INDIVIDUAL_CONTACTS_ONLY);
                if (mode == ViewMode.ADD_GROUP) {
                    _titleLayout.setVisibility(View.GONE);
                    _actionBtn.setText(R.string.create);
                    showUserEntry(_context.getString(R.string.name));
                    _userEntryEt.setTextSize(12);
                } else {
                    dispModes.add(FilterMode.UID_BLACKLIST);
                    adapter.setCustomUIDs(_group.getUserUIDs());
                    _titleBtn.setText(_context.getString(R.string.add_to)
                            + _group.getName());
                    _actionBtn.setText(R.string.add);
                }
                break;
            case DEL_GROUP:
                dispModes.add(FilterMode.GROUPS);
                multiSelect(true);
                _groupActions.setVisibility(View.GONE);
                _titleBtn.setText(R.string.delete_groups);
                _actionBtn.setText(R.string.delete2);
                break;
            case DEL_FROM_GROUP:
                dispModes.add(FilterMode.INDIVIDUAL_CONTACTS_ONLY);
                multiSelect(true);
                _groupActions.setVisibility(View.GONE);
                pushList(_group);
                _actionBtn.setText(R.string.delete);
                _titleBtn.setText(_context.getString(R.string.delete_from)
                        + _group.getName());
                break;
            case SEARCH:
                adapter.setExtraViewActive(true);
                _titleLayout.setVisibility(View.GONE);
                _sortSpinner.setVisibility(View.GONE);
                _searchButton.setVisibility(View.VISIBLE);
                showUserEntry(_context.getString(R.string.search),
                        _searchTerms);
                break;
            case FAVORITES:
                _titleBtn.setText(R.string.favorites);
                adapter.setExtraViewActive(true);
                _customList.setContacts(getFavorites());
                break;
            case HISTORY:
                dispModes.add(FilterMode.UID_WHITELIST);
                _titleBtn.setText(R.string.history);
                _sortSpinner.setVisibility(View.GONE);
                break;
            case FILTER:
                dispModes.add(FilterMode.INDIVIDUAL_CONTACTS_ONLY);
                multiSelect(true);
                _titleBtn.setText(R.string.filter_contacts);
                _actionBtn.setText(R.string.filter);
                _backBtn.setVisibility(View.VISIBLE);
                _sortSpinner.setVisibility(View.GONE);
                _searchButton.setVisibility(View.GONE);
                _showAll.setVisibility(View.GONE);
                _unreadOnly.setVisibility(View.GONE);
                _hideFiltered.setVisibility(View.VISIBLE);
                _hideFiltered.setChecked(hideFiltered);
                break;
        }
        adapter.setDisplayModes(dispModes);
    }

    /**
     * Update all buttons whose appearance depends on the current list
     */
    private void updateListButtons() {
        if (adapter == null)
            return;
        GroupContact current = adapter.getCurrentList();

        // Chat button notification count
        /*if (mode == ViewMode.HISTORY) {
            _chatBtn.setImageResource(R.drawable.clock);
            _chatBtn.setEnabled(true);
        } else {
            int unread = current.getExtras()
                    .getInt("unreadMessageCount", 0);
            LayerDrawable ld = (LayerDrawable) _context.getResources()
                    .getDrawable(R.drawable.ic_menu_chat_layers);
            if (ld != null) {
                AtakLayerDrawableUtil.getInstance(_context)
                        .setBadgeCount(ld, unread);
                _chatBtn.setImageDrawable(ld);
            } else
                _chatBtn.setImageResource(R.drawable.ic_menu_chat);
            _chatBtn.setEnabled(!(_selectModeActive
                    || current.getUnmodifiable() && unread <= 0));
        }*/

        // Back button visibility
        _backBtn.setVisibility(prevAction.isEmpty()
                ? View.GONE
                : View.VISIBLE);

        // Group button visibility
        _groupActions.setVisibility(editableGroup(current)
                || mode == ViewMode.FAVORITES ? View.VISIBLE : View.GONE);
        // Shouldn't be able to add users to the "User Groups" list
        boolean ug = current.getUID().equals(Contacts.USER_GROUPS);
        boolean custom = current == _customList;
        _newGroupBtn.setVisibility(custom ? View.GONE : View.VISIBLE);
        _deleteGroupBtn.setVisibility(custom ? View.GONE : View.VISIBLE);
        _addGroupUser.setVisibility(ug ? View.GONE : View.VISIBLE);
        _delGroupUser.setVisibility(ug ? View.GONE : View.VISIBLE);

        // Title button text
        _titleArrow.setVisibility(View.GONE);
        if (mode == baseMode) {
            GroupContact rootList = Contacts.getInstance().getRootGroup();
            _titleBtn.setText(adapter.getTitle());
            if (current == rootList)
                _titleArrow.setVisibility(View.VISIBLE);
        }

        // Select all box
        if (_selectModeActive) {
            List<String> selected = adapter.getSelectedUids();
            List<String> filtered = current.getFilteredUIDs(false);
            boolean allChecked = !filtered.isEmpty();
            for (String uid : filtered) {
                if (!selected.contains(uid)) {
                    allChecked = false;
                    break;
                }
            }
            if (allChecked != _checkAll.isChecked())
                _checkAll.setChecked(allChecked);
            adapter.setSelectedUids(selected);
        }
    }

    private void showUserEntry(String hint, String value) {
        _userEntryEt.setVisibility(View.VISIBLE);
        _userEntryEt.setHint(hint);
        _userEntryEt.requestFocus();
        _userEntryEt.setSelected(true);
        _userEntryEt.setError(null);
        _userEntryEt.setText(value);
        _userEntryEt.setSelection(value.length());
        _userEntryEt.setTextSize(18);
    }

    private void showUserEntry(String hint) {
        showUserEntry(hint, "");
    }

    private void multiSelect(boolean on) {
        adapter.setCheckboxActive(_selectModeActive = on);
        _actionBtn.setVisibility(on ? View.VISIBLE : View.GONE);
        _checkAllLayout.setVisibility(on ? View.VISIBLE : View.GONE);
        updateListButtons();
    }

    /**
     * Enter add chat group mode
     */
    private void createChatGroup() {
        Log.d(TAG, "Creating new chat group...");
        _actionBtn.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d(TAG,
                        "Trying to create a new chat group: "
                                + _userEntryEt.getText());
                // Current list is the root list when adding groups
                // Need to obtain the previous list instead
                GroupContact curList = prevAction.peek(GroupContact.class);
                if (!editableGroup(curList)) {
                    Log.d(TAG, "Group " + curList + " is not editable");
                    Toast.makeText(_context,
                            _context.getString(R.string.chat_text19),
                            Toast.LENGTH_LONG).show();
                    return;
                }
                if (_userEntryEt.length() > 0) {
                    String groupName = _userEntryEt.getText().toString();
                    groupName = groupName.trim();

                    GroupContact groupToAdd = new GroupContact(
                            UUID.randomUUID().toString(),
                            groupName, adapter.getSelected(), true);
                    Log.d(TAG,
                            "creating a group contact: " + groupToAdd.getName()
                                    +
                                    " with uid " + groupToAdd.getUID());

                    _contacts.addContact(curList, groupToAdd);
                    ChatDatabase.getInstance(_context).addGroup(
                            groupToAdd, true);
                    // Send [UPDATED CONTACTS] message to new users
                    GroupContact root = groupToAdd.getRootUserGroup();
                    List<String> dest = root != null ? root.getUserUIDs(true)
                            : groupToAdd.getUserUIDs();
                    ChatManagerMapComponent.getInstance().persistMessage(
                            ChatManagerMapComponent.NEW_CONTACT_MESSAGE,
                            groupToAdd, dest);
                } else {
                    /*_userEntryEt.requestFocus();
                    _userEntryEt.setSelected(true);
                    _userEntryEt.setError("Set a group name.");*/
                    Toast.makeText(_context,
                            _context.getString(R.string.chat_text20),
                            Toast.LENGTH_LONG).show();
                    return;
                }

                dismissKeyboard(_actionBtn);
                popMode();
                adapter.refreshList("Created chat group");
            }
        });
        pushMode(ViewMode.ADD_GROUP);
    }

    private static boolean editableGroup(GroupContact list) {
        return list != null && !list.getUnmodifiable()
                && (list.isUserCreated()
                        || list.getUID().equals(Contacts.USER_GROUPS));
    }

    private void dismissKeyboard(View v) {
        InputMethodManager imm = (InputMethodManager) _context
                .getSystemService(
                        Context.INPUT_METHOD_SERVICE);
        if (imm != null)
            imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
    }

    /**
     * Enter delete chat group mode
     */
    private void deleteChatGroup() {
        _actionBtn.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                confirmDeleteGroups();
            }
        });
        pushMode(ViewMode.DEL_GROUP);
    }

    /**
     * Bring up delete groups confirmation dialog
     */
    private void confirmDeleteGroups() {
        final List<Contact> groups = adapter.getSelected();
        if (groups.isEmpty())
            return;

        LayoutInflater inf = LayoutInflater.from(_context);
        View v = inf.inflate(R.layout.chat_delete_groups, null);
        TextView groupList = v.findViewById(R.id.group_list);

        StringBuilder groupStr = new StringBuilder(
                groups.get(0).getName());
        for (int i = 1; i < groups.size(); i++) {
            groupStr.append("\n");
            groupStr.append(groups.get(i).getName());
        }
        groupList.setText(groupStr.toString());

        AlertDialog.Builder adb = new AlertDialog.Builder(_context);
        adb.setTitle(R.string.confirm_delete);
        adb.setView(v);
        adb.setPositiveButton(R.string.delete2,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        deleteGroups(groups);
                        popMode();
                        adapter.refreshList("Deleted groups");
                    }
                });
        adb.setNegativeButton(R.string.cancel, null);
        adb.show();
    }

    /**
     * Delete a list of groups from the database and notify all users
     * @param groups List of group contacts to delete
     * @param notifyUsers True to notify users of the deletion
     */
    private void deleteGroups(List<Contact> groups, boolean notifyUsers) {
        Map<String, String[]> destMap = new HashMap<>();
        Map<String, String> parentMap = new HashMap<>();
        for (Contact c : groups) {
            Contact group = _contacts.getContactByUuid(c.getUID());
            if (!GroupContact.isGroup(group))
                continue;
            GroupContact gc = (GroupContact) group;
            Set<String> destUIDs = new HashSet<>();
            if (notifyUsers) {
                // Build destination map (all individuals within sub-groups)
                Contact parent = _contacts
                        .getContactByUuid(gc.getParentUID());
                if (GroupContact.isGroup(parent)) {
                    GroupContact destRoot = gc.getRootUserGroup();
                    if (destRoot == null)
                        destRoot = gc;
                    if (gc.isUserCreated()) {
                        List<Contact> children = destRoot
                                .getAllContacts(true);
                        for (int i = 0; i < children.size(); i++) {
                            Contact child = children.get(i);
                            if (child instanceof IndividualContact)
                                destUIDs.add(child.getUID());
                        }
                        destMap.put(c.getUID(), destUIDs.toArray(
                                new String[0]));
                    }
                    GroupContact gcp = (GroupContact) parent;
                    gcp.removeContact(gc);
                    parentMap.put(c.getUID(), gcp.getUID());
                }
            }
            // Remove all contacts including sub-groups
            deleteGroups(gc.getAllContacts(false), false);
            _contacts.removeContact(gc);
            ChatDatabase.getInstance(_context)
                    .removeGroup(gc.getUID());
        }
        if (notifyUsers) {
            // Notify all users under this root group
            GroupContact userGroups = (GroupContact) _contacts
                    .getContactByUuid(Contacts.USER_GROUPS);
            for (Contact c : groups) {
                String[] dest = destMap.get(c.getUID());
                if (dest == null)
                    continue;
                Contact parent = _contacts
                        .getContactByUuid(parentMap.get(c.getUID()));
                if (!GroupContact.isGroup(parent))
                    parent = userGroups;
                Bundle msg = ChatManagerMapComponent.getInstance()
                        .buildMessage("Deleted group: "
                                + c.getName(), (GroupContact) parent, dest);
                msg.putString("deleteChild", c.getUID());
                ChatManagerMapComponent.getInstance().sendMessage(msg, dest);
            }
        }
    }

    private void deleteGroups(List<Contact> groups) {
        deleteGroups(groups, true);
    }

    private void pushMode(ViewMode mode) {
        prevAction.push(this.mode);
        setViewMode(mode);
    }

    private void pushList(GroupContact gc) {
        if (gc != null && adapter != null) {
            if (mode == ViewMode.SEARCH && gc != _searchResults)
                popMode();
            if (!adapter.getCurrentList().getIgnoreStack())
                prevAction.push(adapter.getCurrentList());
            setCurrentList(gc);
        }
    }

    private void setCurrentList(GroupContact gc) {
        if (gc != null && adapter != null) {
            adapter.setCurrentList(gc);
            updateListButtons();
        }
    }

    private void setViewMode(ViewMode mode) {
        this.mode = mode == null ? this.baseMode : mode;
        if (adapter != null)
            adapter.setViewMode(mode);
        setViewToMode();
    }

    private void setBaseViewMode(ViewMode base) {
        if (adapter != null)
            adapter.setBaseViewMode(base);
        setViewMode(this.baseMode = base);
    }

    private boolean popMode() {
        if (prevAction.peek(ViewMode.class) != null) {
            ViewMode old = mode;
            setViewMode(prevAction.pop(ViewMode.class));
            if (old.changeList)
                popList();
            if (old.groupMode) {
                _checkAll.setChecked(false);
                adapter.unSelectAll(true);
            }
            return true;
        } else if (mode != baseMode) {
            setViewMode(baseMode);
            return true;
        }
        return false;
    }

    private boolean popList() {
        if (prevAction.peek(GroupContact.class) != null) {
            if (mode == ViewMode.FAVORITES) {
                setCurrentList(_customList);
                return true;
            }
            if (mode.changeList && popMode())
                return true;
            setCurrentList(prevAction.pop(GroupContact.class));
            if (!editableGroup(adapter.getCurrentList())) {
                while (mode.groupMode)
                    popMode();
            }
            return true;
        }
        return false;
    }

    private boolean popAction() {
        if (!prevAction.empty() && prevAction.peek() != null) {
            Object act = prevAction.peek();
            boolean success = false;
            if (act instanceof ViewMode)
                success = popMode();
            else if (act instanceof GroupContact)
                success = popList();
            return success;
        }
        return false;
    }

    //TODO: delete?
    public ContactListAdapter getAdapter() {
        return adapter;
    }

    public boolean showUnreadOnly() {
        return baseMode != ViewMode.SEND_LIST &&
                _prefs.getBoolean(UnreadFilter.PREF, false);
    }

    @Override
    protected boolean onBackButtonPressed() {
        if (!popAction())
            closeDropDown();
        return true;
    }

    @Override
    public void onDropDownClose() {
        // Remove all modes from stack, leave lists
        prevAction.clear(ViewMode.class);
        if (!reOpening)
            openAction = null;
        reOpening = false;
    }

    @Override
    public void onDropDownVisible(boolean v) {
        if (v) {
            loadFavorites();
            adapter.registerListeners();
            adapter.addRefreshListener(_searchRefresh);
            setViewToMode();
            adapter.unreadOnly(showUnreadOnly());
        } else {
            while (mode.changeList && mode != baseMode)
                popMode();
            if (adapter.getCurrentList() == _customList)
                popList();
            adapter.unregisterListeners();
            adapter.removeRefreshListener(_searchRefresh);
            adapter.unSelectAll(true);

            // Disable unread-only filtering while chat window is open
            // or messages won't be sent to contacts with no unread msgs
            adapter.unreadOnly(false, true);
        }
    }

    @Override
    public void onDropDownSizeChanged(double width, double height) {
    }

    @Override
    public void onDropDownSelectionRemoved() {
    }

    /******************************
     * Contact list CoT sending methods
     ******************************/
    private void sendCot(String[] uids, IndividualContact[] netContacts) {

        // Force internal replication only if flagged true
        Bundle persistExtras = new Bundle();
        persistExtras.putBoolean("internal", false);

        for (String uid : uids) {

            if (netContacts != null) {
                String[] toUIDs = ContactUtil.getUIDs(netContacts);
                String[] toConnectStrings = ContactUtil.getConnectStrings(
                        netContacts, true);

                // (KYLE) We should send the entire NetworkContact so that we have
                // an extensible path for information transfer to the CotService.
                // For now, I'm adding it as a /separate/ key in the bundle, meaning
                // that we're duplicating information to the Service. We should
                // eventually remove the "toConnectStrings" key, in favor of the "toUIDs" key.
                //Log.d(TAG, "Sending netContacts: " + netContacts.getClass());
                persistExtras.putStringArray("toConnectStrings",
                        toConnectStrings);
                persistExtras.putStringArray("toUIDs", toUIDs);
            }

            MapItem target = findTarget(uid);
            if (target == null)
                continue;
            if (target.getType() != null && target.getType().equals("self")) {
                Log.d(TAG, "asked to report self now");
                AtakBroadcast.getInstance().sendBroadcast(
                        new Intent(ReportingRate.REPORT_LOCATION));
            } else if (target instanceof Marker) {
                // XXX - LEGACY for NOW (required for CoT based Markers)
                target.persist(getMapView().getMapEventDispatcher(),
                        persistExtras,
                        this.getClass());
            } else {
                // Tell other components the Map Item needs to be sent
                MapEvent.Builder eb2 = new MapEvent.Builder(
                        MapEvent.ITEM_SHARED);
                eb2.setItem(target)
                        .setExtras(persistExtras);
                getMapView().getMapEventDispatcher().dispatch(eb2.build());
            }
        }
    }

    private void dispatchCot(CotEvent event, String imageFilename,
            IndividualContact[] netContacts) {

        //TODO dispatchCot vs sendCot?

        if (event == null) {
            Log.w(TAG, "Failed to dispatch null CoT event.");
            return;
        }

        Bundle extras = new Bundle();
        extras.putBoolean("internal", false);

        if (imageFilename != null)
            extras.putString("imageFilename", imageFilename);

        if (netContacts != null) {
            String[] toUIDs = ContactUtil.getUIDs(netContacts);
            String[] toConnectStrings = ContactUtil.getConnectStrings(
                    netContacts, false);

            // (KYLE) We should send the entire NetworkContact so that we have
            // an extensible path for information transfer to the CotService.
            // For now, I'm adding it as a /separate/ key in the bundle, meaning
            // that we're duplicating information to the Service. We should
            // eventually remove the "toConnectStrings" key, in favor of the "toUIDs" key.
            extras.putStringArray("toConnectStrings", toConnectStrings);
            extras.putStringArray("toUIDs", toUIDs);
        }
        CotMapComponent.getExternalDispatcher().dispatch(event, extras);
    }

    private Intent createIntentForPlugin(Intent fromIntent, Contact contact,
            String uid, CotEvent event) {
        Intent forPlugin = new Intent(fromIntent);
        if (contact != null) {
            //forPlugin.setAction(contact.intentAction);
            //forPlugin.putExtra("param", contact.parameters);
        }
        MapItem i = findTarget(uid);
        GeoPointMetaData p;
        if (i != null) {
            // Put all the item's meta-data into the intent for the bundle
            Bundle metaData = new Bundle();
            Map<String, Object> m = new HashMap<>();
            i.getMetaData(m); // populates the arg
            DefaultMetaDataHolder.metaMapToBundle(m, metaData, true);
            forPlugin.putExtras(metaData);
            p = null;
            if (i instanceof PointMapItem)
                p = ((PointMapItem) i).getGeoPointMetaData();
            else if (i instanceof Shape)
                p = ((Shape) i).getCenter();
            else if (i instanceof AnchoredMapItem)
                p = ((AnchoredMapItem) i).getAnchorItem().getGeoPointMetaData();
            if (p != null) {
                // Also, add the current lat & lon (since they're not part of the meta-data)
                forPlugin.putExtra("lat", p.get().getLatitude());
                forPlugin.putExtra("lon", p.get().getLongitude());
            } else {
                Log.w(TAG, "Unable to obtain location for MapItem with uid "
                        + uid
                        + " to send info to create an intent");
            }
        } else {
            Log.w(TAG, "couldn't find MapItem with uid " + uid
                    + " to send info to create an intent");
        }
        return forPlugin;
    }

    /**
     * Send file to specified contacts, via Mission Package Tool
     * //TODO how to test this? What UI code invokes this? Perhaps none, in that case remove this method
     *
     * @param filename
     * @param netContacts
     */
    private void sendFile(final String filename,
            IndividualContact[] netContacts) {

        if (!FileSystemUtils.isFile(filename)) {
            Log.w(TAG, "Cannot send invalid file: " + filename);
            return;
        }
        File file = new File(filename);

        Log.d(TAG, "Sending file via Mission Package Tool: " + filename);

        // Create the Mission Package containing the file
        // receiving device will delete after file is imported
        MissionPackageManifest manifest = MissionPackageApi
                .CreateTempManifest(file.getName(), true, true, null);
        if (!manifest.addFile(file, null) || manifest.isEmpty()) {
            Log.w(TAG, "Unable to add file to Mission Package");
            return;
        }

        // send null contact list so Contact List is displayed, delete local
        // mission package after sent
        MissionPackageApi.Send(_context, manifest,
                DeleteAfterSendCallback.class,
                netContacts);
    }

    private MapItem findTarget(String targetUID) {
        MapItem item = null;
        if (targetUID != null) {
            MapGroup rootGroup = getMapView().getRootGroup();
            item = rootGroup.deepFindUID(targetUID);
        }
        return item;
    }

    private static class ActionStack extends Stack<Object> {

        /**
         * Remove all elements belonging to a specific class
         * @param c The class of the elements to remove
         */
        public synchronized void clear(Class<?> c) {
            for (int i = 0; i < size(); i++) {
                if (c.isInstance(get(i)))
                    remove(i--);
            }
        }

        /**
         * Pop an element of a specific class
         * @param c The class of the element to pop
         * @return The removed element
         */
        public synchronized <T> T pop(Class<T> c) {
            T el = peek(c);
            if (el != null) {
                remove(size() - search(el));
                return el;
            }
            return null;
        }

        /**
         * Find element of a specific class closest to the top of the stack
         * @param c The class of the element to find
         * @param <T> The return type of the element
         * @return The element nearest to the top of the stack
         */
        public synchronized <T> T peek(Class<T> c) {
            for (int i = size() - 1; i >= 0; i--) {
                Object el = get(i);
                if (c.isInstance(el))
                    return c.cast(el);
            }
            return null;
        }
    }

    private static class SearchResults extends GroupContact {

        private static final String TAG = "SearchResults";
        private GroupContact _list;

        public SearchResults() {
            super("SearchResults", "Results", false);
        }

        public synchronized void setSearchList(GroupContact list) {
            _list = list;
        }

        public synchronized GroupContact getSearchList() {
            return _list;
        }

        @Override
        public void setContacts(final List<Contact> contacts) {
            synchronized (_contacts) {
                super.setContacts(contacts);
                updateChildren(new ArrayList<HierarchyListItem>(
                        _contacts.values()));
            }
        }

        @Override
        public synchronized HierarchyListFilter refresh(
                final HierarchyListFilter filter) {
            if (_list != null)
                return _list.refresh(filter);
            return filter;
        }
    }
}

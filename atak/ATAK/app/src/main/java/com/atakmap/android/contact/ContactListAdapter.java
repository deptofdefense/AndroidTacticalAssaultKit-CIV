
package com.atakmap.android.contact;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.atakmap.android.chat.ChatDatabase;
import com.atakmap.android.chat.ChatManagerMapComponent;
import com.atakmap.android.chat.TeamGroup;
import com.atakmap.android.chat.GeoChatService;
import com.atakmap.android.contact.Contact.UpdateStatus;
import com.atakmap.android.contact.ContactFilter.FilterMode;
import com.atakmap.android.contact.Contacts.OnContactsChangedListener;
import com.atakmap.android.contact.ContactConnectorManager.ContactConnectorHandler;
import com.atakmap.android.cot.CotMapComponent;
import com.atakmap.android.hierarchy.HierarchyListFilter;
import com.atakmap.android.hierarchy.HierarchyListItem;
import com.atakmap.android.hierarchy.HierarchyListItem.ComparatorSort;
import com.atakmap.android.hierarchy.HierarchyListItem.Sort;
import com.atakmap.android.hierarchy.filters.EmptyListFilter;
import com.atakmap.android.hierarchy.filters.FOVFilter;
import com.atakmap.android.hierarchy.filters.MultiFilter;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Marker;
import com.atakmap.android.maps.PointMapItem;
import com.atakmap.android.tools.AtakLayerDrawableUtil;
import com.atakmap.android.tools.menu.ActionBroadcastData;
import com.atakmap.android.tools.menu.ActionBroadcastExtraStringData;
import com.atakmap.android.util.ATAKUtilities;
import com.atakmap.android.util.LimitingThread;
import com.atakmap.app.R;
import com.atakmap.coremap.log.Log;
import com.atakmap.map.AtakMapView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public class ContactListAdapter extends BaseAdapter implements
        OnContactsChangedListener {
    private static final String TAG = "ContactListAdapter";

    private static final int refreshRate = 1000;

    private final List<String> selectedUids = new ArrayList<>();

    private GroupContact currentList;
    private final GroupContact customList;
    private final List<String> customUIDs = new ArrayList<>();
    private ViewMode viewMode = ViewMode.DEFAULT,
            baseViewMode = ViewMode.DEFAULT;
    private ContactConnectorHandler _filterHandler;

    private final Contacts contacts;
    private ViewSetting savedView = null;
    private List<FilterMode> displayModes = new ArrayList<>();
    private Sort sortMode = new HierarchyListItem.SortAlphabet();
    private boolean sortByLocation = false;
    private MultiFilter filter;
    private final SharedPreferences prefs;
    private boolean showAll, unreadOnly, hideFiltered;
    private final Activity ctx;
    private final MapView mapView;
    private boolean checkBoxActive = false;
    private boolean extraViewActive = false;

    // The current list of items being displayed
    private final List<HierarchyListItem> items = new ArrayList<>();

    // Adapter is being used
    private boolean _active = false;

    private final List<RefreshListener> _refreshListeners = new ArrayList<>();

    private final LimitingThread refreshThread = new LimitingThread(
            "RefreshContacts", new Runnable() {
                @Override
                public void run() {
                    if (_active) {
                        refreshListImpl();
                        try {
                            Thread.sleep(refreshRate);
                        } catch (InterruptedException ignored) {
                        }
                    }
                }
            });

    private static class ViewSetting {
        boolean checkBoxActive;
        boolean extraViewActive;
        List<FilterMode> displayModes;

        ViewSetting(boolean checkBoxActive, boolean extraViewActive,
                List<FilterMode> displayModes) {
            this.checkBoxActive = checkBoxActive;
            this.extraViewActive = extraViewActive;
            this.displayModes = displayModes;
        }
    }

    public enum ViewMode {
        DEFAULT, // Base contact list
        GEO_CHAT, // Contacts with group management, search, history
        SEND_LIST, // Contact list without chat features, with send buttons
        SELECT_W_ACTION, //Select contacts to execute action
        ADD_GROUP(true, true), // Add group
        ADD_TO_GROUP(true, true), // Add users to group
        DEL_GROUP(true), // Delete group
        DEL_FROM_GROUP(true, true), // Delete users from group
        SEARCH(false, true), //Search contacts
        FAVORITES(false, true), // List of favorite contacts
        HISTORY, //History view
        FILTER; //filter out contacts

        // True if this mode is a group modifier
        public final boolean groupMode;
        // True if this mode changes the current list
        public final boolean changeList;

        ViewMode(boolean groupMode, boolean changeList) {
            this.changeList = changeList;
            this.groupMode = groupMode;
        }

        ViewMode(boolean groupMode) {
            this(groupMode, false);
        }

        ViewMode() {
            this(false, false);
        }
    }

    public ContactListAdapter(MapView mapView, Contacts contacts,
            Context context) {
        this.currentList = Contacts.getInstance().getRootGroup();
        this.customList = new GroupContact("CustomList",
                context.getString(R.string.actionbar_contacts), false);
        this.customList.setIgnoreStack(true);
        this.contacts = contacts;
        this.contacts.addListener(this);
        this.mapView = mapView;
        this.ctx = (Activity) context;
        prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
        this.showAll = !prefs.getBoolean(FOVFilter.PREF, false);
        selectedUids.addAll(
                FilteredContactsManager.getInstance().getFilteredContacts());
    }

    public ContactListAdapter(Contacts contacts, Context context) {
        this(MapView.getMapView(), contacts, context);
    }

    @Override
    public synchronized int getCount() {
        return this.items.size();
    }

    @Override
    public synchronized Object getItem(int position) {
        return this.items.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(final int position, View v, ViewGroup parent) {
        final ViewHolder holder;
        if (v == null) {
            v = LayoutInflater
                    .from(MapView.getMapView().getContext())
                    .inflate(R.layout.contact_list_child, parent, false);

            holder = new ViewHolder();
            holder.checkBox = v
                    .findViewById(R.id.contact_list_child_checkbox);
            holder.statusIconImageView = v
                    .findViewById(R.id.contact_list_presence_icon);
            holder.callsignTextView = v
                    .findViewById(R.id.contact_list_contact_callsign);
            holder.profileBtn = v
                    .findViewById(R.id.contact_list_profile_btn);
            holder.defaultCommsBtn = v
                    .findViewById(R.id.contact_list_defaultComms_btn);
            holder.extraContainer = v.findViewById(
                    R.id.contact_list_extras_container);
            holder.filterCheckBox = v
                    .findViewById(R.id.contact_list_filter_checkbox);
            holder.filterCheckBox.setVisibility(View.GONE);

            v.setTag(holder);
        } else {
            holder = (ViewHolder) v.getTag();
        }

        holder.checkBox
                .setOnCheckedChangeListener(new OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton cb,
                            boolean checked) {
                        synchronized (selectedUids) {
                            HierarchyListItem item = (HierarchyListItem) getItem(
                                    position);
                            if (!(item instanceof Contact))
                                return;
                            String uid = item.getUID();
                            if (checked == selectedUids.contains(uid))
                                return;
                            if (checked)
                                selectedUids.add(uid);
                            else
                                selectedUids.remove(uid);
                        }
                    }
                });

        updateViewHolder(holder, v, position);
        return v;
    }

    /**
     * Update the items displayed in the current list
     * This is the only place where this.items should be modified
     */
    private void updateItems() {
        ((Activity) this.mapView.getContext()).runOnUiThread(new Runnable() {
            @Override
            public void run() {
                List<HierarchyListItem> newItems = new ArrayList<>();
                final GroupContact curList = currentList;
                if (curList != null) {
                    // Apply post-filtering
                    List<HierarchyListFilter> filterList = new ArrayList<>();
                    if (viewMode != ViewMode.DEL_GROUP)
                        filterList.add(new EmptyListFilter());
                    curList.setPostFilter(new MultiFilter(
                            sortMode, filterList));

                    // Get latest items list
                    newItems.addAll(curList.getChildren());
                }
                // 'items' not synchronized since it's only touched on UI
                items.clear();
                items.addAll(newItems);
                ContactListAdapter.super.notifyDataSetChanged();
            }
        });
    }

    private void updateHolderBasedOnConnectionStatus(TextView textView,
            ImageView view,
            View parentView, Contact contact) {
        if (contact == null)
            return;

        // Read from contact properties
        view.setVisibility(
                contact.getUpdateStatus() == null ? View.GONE : View.VISIBLE);
        textView.setTextColor(contact.getTextColor());
        parentView.setBackgroundColor(contact.getBackgroundColor());
        view.setColorFilter(contact.getIconColor(), PorterDuff.Mode.MULTIPLY);

        ATAKUtilities.SetIcon(mapView.getContext(), view,
                contact.getIconUri(), contact.getIconColor());

        // Set the icon
        // If a Drawable icon is not available then the URI is used as fallback
        // If "gone" is specified for the URI then the icon space is removed
        Drawable iconDr;
        String iconUri = contact.getIconUri();
        if ((iconDr = contact.getIconDrawable()) != null) {
            view.setImageDrawable(iconDr);
            view.setColorFilter(contact.getIconColor(),
                    PorterDuff.Mode.MULTIPLY);
            view.setVisibility(View.VISIBLE);
        } else if (iconUri != null && iconUri.equals("gone"))
            view.setVisibility(View.GONE);
        else
            ATAKUtilities.SetIcon(mapView.getContext(), view,
                    contact.getIconUri(), contact.getIconColor());

        //now overlay presence if available
        LayerDrawable ld = (LayerDrawable) mapView.getContext().getResources()
                .getDrawable(R.drawable.details_badge);
        if (ld != null) {
            AtakLayerDrawableUtil.getInstance(mapView.getContext())
                    .setBadgeCount(ld,
                            view.getDrawable(), 0, contact.getPresenceColor());
            view.setImageDrawable(ld);
        }
    }

    /**
     * Enable the custom uid list filter
     * Only used when display mode is set to CUSTOM_LIST
     * Unlike the above method, this maintains the hierarchy of the stack
     * @param uids List of UIDs to accept in filter
     */
    public void setCustomUIDs(List<String> uids) {
        if (uids != null) {
            synchronized (this.customUIDs) {
                this.customUIDs.clear();
                this.customUIDs.addAll(uids);
            }
        }
    }

    public void setConnectorFilter(ContactConnectorHandler handler) {
        if (_filterHandler != handler) {
            _filterHandler = handler;
            refreshList("Changed connector filter");
        }
    }

    /**
     * Get the title of the current list
     * @return Current list title
     */
    public String getTitle() {
        GroupContact list = getCurrentList();
        if (_filterHandler != null && list == Contacts
                .getInstance().getRootGroup())
            return _filterHandler.getName();
        return list.getTitle();
    }

    private void setCheckBoxStatus(CheckBox checkbox, String uuid) {
        if (checkBoxActive) {
            checkbox.setVisibility(View.VISIBLE);
            synchronized (selectedUids) {
                boolean check = selectedUids.contains(uuid);
                if (check != checkbox.isChecked())
                    checkbox.setChecked(check);
            }
        } else
            checkbox.setVisibility(View.GONE);
    }

    private void updateViewHolder(final ViewHolder holder, View convertView,
            final int position) {
        HierarchyListItem item = (HierarchyListItem) getItem(position);
        String uuid = item != null ? item.getUID() : null;
        if (uuid == null)
            return;
        Contact c = item instanceof Contact ? (Contact) item : null;
        if (c == null) {
            String name = "Unknown";
            Resources res = ctx.getResources();
            String[] teamNames = res.getStringArray(R.array.squad_names);
            if (ChatManagerMapComponent.isSpecialGroup(uuid)) //Check for the two big groups
                name = uuid;
            else { //then check for a team group
                Contact teams = this.contacts.getContactByUuid("TeamGroups");
                for (String team : teamNames) {
                    if (team.equals(uuid)) {
                        name = uuid;
                        c = new TeamGroup(uuid);
                        Bundle tempExtras = c.getExtras();
                        tempExtras.putSerializable("updateStatus",
                                UpdateStatus.NA);
                        c.setExtras(tempExtras);
                        if (teams instanceof GroupContact)
                            this.contacts.addContact((GroupContact) teams, c);
                        break;
                    }
                }
            }
            if (name.equals("Unknown")) { //hasn't been found yet - either custom group or P2P
                List<String> info = ChatDatabase.getInstance(ctx).getGroupInfo(
                        uuid);
                if (info.size() == 3) { //Found group with the right name
                    List<String> recipients = Arrays.asList(info.get(1).split(
                            ","));
                    if (recipients.size() > 1) { //group contact
                        c = new GroupContact(uuid, info.get(0),
                                Contacts.fromUIDs(recipients), false);
                    } else { //probably p2p (or group of 1 user)
                        c = new IndividualContact(info.get(0),
                                uuid);
                    }
                    Contact parent = this.contacts
                            .getContactByUuid(info.get(2));
                    if (!(parent instanceof GroupContact))
                        parent = this.contacts.getContactByUuid(
                                Contacts.USER_GROUPS);

                    Bundle tempExtras = c.getExtras();
                    tempExtras.putSerializable("updateStatus", UpdateStatus.NA);
                    c.setExtras(tempExtras);
                    if (parent instanceof GroupContact)
                        this.contacts.addContact((GroupContact) parent, c);
                } else {
                    //We don't have enough info to find the contact (should never happen)
                    c = new IndividualContact(name, uuid);
                    Log.w(TAG, "Unable to find contact: " + c);
                    Bundle tempExtras = c.getExtras();
                    tempExtras.putSerializable("updateStatus", UpdateStatus.NA);
                    c.setExtras(tempExtras);
                    this.contacts.addContact(c);
                }
            }
        }
        setCheckBoxStatus(holder.checkBox, uuid);
        updateHolderBasedOnConnectionStatus(holder.callsignTextView,
                holder.statusIconImageView,
                convertView, c);

        holder.profileBtn.setFocusable(false);
        holder.profileBtn.setFocusableInTouchMode(false);

        final Contact fc = c;
        holder.profileBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (fc == null) {
                    Log.d(TAG, "Profile button clicked, no contact");
                } else if (fc instanceof IndividualContact) {
                    IndividualContact individual = (IndividualContact) fc;
                    Log.d(TAG, "Profile button clicked for individual: "
                            + individual);
                    individual.zoom();
                    ActionBroadcastData.broadcast(individual
                            .getDefaultProfile());

                } else {
                    Log.d(TAG, "Profile button clicked: "
                            + fc.getClass().getName());
                }
            }
        });

        holder.defaultCommsBtn.setFocusable(false);
        holder.defaultCommsBtn.setFocusableInTouchMode(false);

        //set contact name
        if (c != null) {
            String name = c.getName();
            holder.callsignTextView.setText(name);

            // Append extra view
            holder.extraContainer.setVisibility(View.GONE);
            holder.extraContainer.removeAllViews();

            // In case extra view needs access to this adapter
            c.setListener(this);

            // Append extra view
            View extraView = c.getExtraView();
            if (extraView != null) {
                holder.extraContainer.setVisibility(View.VISIBLE);
                holder.extraContainer.addView(extraView, 0);
            }

            holder.filterCheckBox.setChecked(
                    FilteredContactsManager.getInstance().isContactFiltered(c));
        }

        //update default comms button
        updateDefaultCommsBtn(c, holder.defaultCommsBtn);

        //set default contact icon
        if (!(c instanceof IndividualContact)
                || !((IndividualContact) c).hasProfile()) {
            holder.profileBtn.setVisibility(View.GONE);
        } else {
            holder.profileBtn.setVisibility(View.VISIBLE);
            holder.profileBtn.setImageResource(R.drawable.ic_profile_white);
            updateTextColor(c, holder.callsignTextView);
        }
    }

    /**
     * Update text color based on filter status
     */
    public void updateTextColor(Contact c, TextView v) {
        if (FilteredContactsManager.getInstance().isContactFiltered(c)) {
            v.setTextColor(Color.GRAY);
        } else {
            v.setTextColor(Color.WHITE);
        }
    }

    /**
     * Update default communication button
     * @param c Contact
     * @param btn Default comms button
     *
     *  Note, this matches behavior in IndividualContact.initiateDefaultComms()
     */
    private void updateDefaultCommsBtn(final Contact c, ImageButton btn) {
        btn.setEnabled(true);
        btn.setVisibility(View.GONE);
        if (c == null || this.baseViewMode == ViewMode.SEND_LIST)
            return;

        // Special case for geo-chat history mode
        if (this.baseViewMode == ViewMode.GEO_CHAT
                && this.viewMode == ViewMode.HISTORY) {
            btn.setVisibility(View.VISIBLE);
            btn.setImageResource(R.drawable.clock);
            synchronized (customUIDs) {
                btn.setEnabled(customUIDs.contains(c.getUID()));
            }
            btn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    openGeoChatWindow(c);
                }
            });
            return;
        }

        /* Set default comms button with following order:
        * 1) If no unread, use "default" connector, initiate comms when tapped
        * 2) If single connector with unread, use that connector, initiate comms when tapped
        * 3) If multiple connectors with unread, use generic icon and display comms tab when clicked
        */
        if (c instanceof IndividualContact) {
            final IndividualContact individual = (IndividualContact) c;
            final Connector defaultConnector = individual
                    .getDefaultConnector(prefs);
            if (defaultConnector != null) {
                //see how many connectors have unread
                int connectorsWithUnread = 0;
                Collection<Connector> connectors = individual
                        .getConnectors(true);
                Connector unreadConnector = null;
                for (Connector cur : connectors) {
                    if (individual.getUnreadCount(cur) > 0) {
                        unreadConnector = cur;
                        connectorsWithUnread++;
                    }
                    //for now we only care if there are multiple connectors with unread
                    if (connectorsWithUnread > 1)
                        break;
                }
                int totalUnread = individual.getUnreadCount();
                if (FilteredContactsManager.getInstance().isContactFiltered(c))
                    totalUnread = 0;
                if (totalUnread == 0 || unreadConnector == null) {
                    //no unread, display default comms icon w/no unread overlay
                    updateConnectorView(individual, defaultConnector,
                            btn, this.ctx);
                    btn.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            Log.d(TAG,
                                    "Default comms clicked for individual: "
                                            + individual);
                            if (!CotMapComponent
                                    .getInstance()
                                    .getContactConnectorMgr()
                                    .initiateContact(individual,
                                            defaultConnector)) {
                                Log.w(TAG,
                                        "No connector handler available for: "
                                                + individual);
                                //TODO notify user?
                            }
                        }
                    });
                } else {
                    if (connectorsWithUnread > 1) {
                        //multiple connectors with unread, display generic icon and open comms tab
                        btn.setVisibility(View.VISIBLE);
                        btn.setImageResource(R.drawable.open_chat_layers);
                        LayerDrawable ld = (LayerDrawable) this.ctx
                                .getResources().getDrawable(
                                        R.drawable.details_badge);
                        AtakLayerDrawableUtil.getInstance(this.ctx)
                                .setBadgeCount(ld, btn.getDrawable(),
                                        totalUnread, null);
                        btn.setImageDrawable(ld);
                        btn.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                Log.d(TAG,
                                        "Multiple comms clicked for individual: "
                                                + individual);
                                ActionBroadcastData intent = individual
                                        .getDefaultProfile();
                                if (intent != null
                                        && ContactDetailDropdown.CONTACT_DETAILS
                                                .equals(intent
                                                        .getAction())) {
                                    //if default TAK profile, then lets jump to comms tab
                                    intent.getExtras()
                                            .add(new ActionBroadcastExtraStringData(
                                                    "tab",
                                                    ContactConnectorsView.TAG));
                                }
                                ActionBroadcastData.broadcast(intent);
                            }
                        });
                    } else {
                        //display that connector with unread count overlay
                        final Connector funreadConnector = unreadConnector;
                        updateConnectorView(individual, funreadConnector,
                                btn, this.ctx);
                        btn.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                Log.d(TAG,
                                        "Unread comms clicked for individual: "
                                                + individual
                                                + ", "
                                                + funreadConnector);
                                if (!CotMapComponent
                                        .getInstance()
                                        .getContactConnectorMgr()
                                        .initiateContact(individual,
                                                funreadConnector)) {
                                    Log.w(TAG,
                                            "No connector handler available for: "
                                                    + individual);
                                    //TODO notify user?
                                }
                            }
                        });
                    }
                }
            }
        } else if (c instanceof GroupContact) {
            // Group contacts are still geo-chat exclusive for now
            final GroupContact gc = (GroupContact) c;
            btn.setVisibility(View.VISIBLE);
            int unread = gc.getExtras().getInt("unreadMessageCount", 0);
            LayerDrawable ld = (LayerDrawable) ctx.getResources()
                    .getDrawable(R.drawable.open_chat_layers);
            if (ld != null) {
                AtakLayerDrawableUtil.getInstance(ctx)
                        .setBadgeCount(ld, unread);
                btn.setImageDrawable(ld);
            } else
                btn.setImageResource(R.drawable.open_chat);
            btn.setEnabled(!(gc.getUnmodifiable() && unread <= 0));
            btn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    openGeoChatWindow(gc);
                }
            });
        }
    }

    /**
     * Open a new chat window for the current list
     */
    void openGeoChatWindow(Contact list) {
        boolean editable = list.getExtras().getBoolean("editable",
                !(list instanceof GroupContact))
                || list instanceof GroupContact
                        && !((GroupContact) list).getUnmodifiable()
                        && viewMode != ViewMode.HISTORY;
        String title = null, uid = null;

        Contacts cts = Contacts.getInstance();
        if (!cts.validContact(list)
                || list == cts.getRootGroup())
            list = cts.getContactByUuid(GeoChatService.DEFAULT_CHATROOM_NAME);
        if (list != null) {
            title = list.getName();
            uid = list.getUID();
            //openIntent.putExtra("contacts", contacts);
        }

        ChatManagerMapComponent.getInstance().openConversation(title, uid,
                null, editable);
        AtakBroadcast.getInstance().sendBroadcast(
                new Intent("com.atakmap.android.maps.UNFOCUS"));
        AtakBroadcast.getInstance().sendBroadcast(
                new Intent("com.atakmap.android.maps.HIDE_MENU"));
    }

    /**
     * Overlay presence and unread count (if available) for  the contact's connector, on the
     * specified image view
     *
     * @param individual the IndividualContact to update the connector for
     * @param connector the connector
     * @param view the specified image view
     * @param context the context
     */
    public static void updateConnectorView(IndividualContact individual,
            Connector connector,
            ImageView view, Context context) {

        // if the connector is null, then just return
        if (connector == null) {
            return;
        }

        view.setVisibility(View.VISIBLE);

        //display unread count for default connector
        int connectorUnread = individual.getUnreadCount(connector);
        if (FilteredContactsManager.getInstance().isContactFiltered(individual))
            connectorUnread = 0;
        ATAKUtilities.SetIcon(context, view, connector.getIconUri(),
                Color.WHITE);

        Object connectorPresence = CotMapComponent
                .getInstance()
                .getContactConnectorMgr()
                .getFeature(
                        individual, connector,
                        ContactConnectorManager.ConnectorFeature.Presence);

        //Log.d(TAG, individual.getName() + ", total unread: " + defaultConnectorUnread);

        //overlay presence and unread count, if available
        LayerDrawable ld = (LayerDrawable) context.getResources().getDrawable(
                R.drawable.details_badge);
        AtakLayerDrawableUtil
                .getInstance(context)
                .setBadgeCount(
                        ld,
                        view.getDrawable(),
                        connectorUnread,
                        (connectorPresence instanceof Contact.UpdateStatus)
                                ? IndividualContact
                                        .getPresenceColor(
                                                (Contact.UpdateStatus) connectorPresence)
                                : null);
        view.setImageDrawable(ld);
    }

    /**
     * Set the current view mode
     * @param mode View mode
     */
    public void setViewMode(ViewMode mode) {
        if (this.viewMode != mode) {
            this.viewMode = mode;
            updateItems();
        }
    }

    public ViewMode getViewMode() {
        return this.viewMode;
    }

    /**
     * Set the base view mode (Contacts, GeoChat, Mission Package, etc.)
     * @param baseMode Base view mode
     */
    public void setBaseViewMode(ViewMode baseMode) {
        if (this.baseViewMode != baseMode) {
            this.baseViewMode = baseMode;
            updateItems();
        }
    }

    public ViewMode getBaseViewMode() {
        return this.baseViewMode;
    }

    /**
     * De-select all items
     * @param clear True to de-select everything, false to de-select in list
     */
    public void unSelectAll(boolean clear) {
        synchronized (selectedUids) {
            if (clear)
                selectedUids.clear();
            else {
                List<String> filtered;
                synchronized (this) {
                    filtered = currentList.getFilteredUIDs(false);
                }
                for (String uid : filtered) {
                    selectedUids.remove(uid);
                }
            }
            notifyChange();
        }
    }

    public void unSelectAll() {
        unSelectAll(false);
    }

    public void selectAll() {
        synchronized (selectedUids) {
            selectedUids.clear();
            synchronized (this) {
                selectedUids.addAll(currentList.getFilteredUIDs(false));
            }
            notifyChange();
        }
    }

    public void saveCurrentView(boolean checkBoxActive,
            boolean extraViewActive, FilterMode displayMode) {
        savedView = new ViewSetting(this.checkBoxActive, this.extraViewActive,
                this.displayModes);

        setCheckboxActive(checkBoxActive);
        setExtraViewActive(extraViewActive);
        setDisplayMode(displayMode);
    }

    public void loadView() {
        if (savedView != null) {
            setCheckboxActive(savedView.checkBoxActive);
            setExtraViewActive(savedView.extraViewActive);
            setDisplayModes(savedView.displayModes);
        }
    }

    public void setCheckboxActive(boolean active) {
        if (active != checkBoxActive) {
            checkBoxActive = active;
            notifyChange();
        }
    }

    public void setExtraViewActive(boolean active) {
        if (active != extraViewActive) {
            extraViewActive = active;
            notifyChange();
        }
    }

    /**
     * Request contact list refresh
     * @param reason Reason for refresh
     */
    public void refreshList(String reason) {
        if (_active) {
            //Log.d(TAG, "Refreshing: " + reason);
            this.refreshThread.exec();
        }
    }

    /**
     * Force refresh on current list
     */
    public synchronized void refreshListImpl() {
        refreshFilter();
        this.currentList.setListener(this);
        this.currentList.refresh(this.filter);
    }

    /**
     * Individual item has finished updating
     * @param contact Contact list item
     */
    public void notifyDataSetChanged(final Contact contact) {
        this.ctx.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (currentList == contact)
                    updateItems();
                synchronized (_refreshListeners) {
                    for (RefreshListener r : _refreshListeners)
                        r.onRefreshFinished(contact);
                }
            }
        });
    }

    public void addRefreshListener(RefreshListener r) {
        synchronized (_refreshListeners) {
            if (r != null && !_refreshListeners.contains(r))
                _refreshListeners.add(r);
        }
    }

    public void removeRefreshListener(RefreshListener r) {
        synchronized (_refreshListeners) {
            if (r != null)
                _refreshListeners.remove(r);
        }
    }

    public interface RefreshListener {
        void onRefreshFinished(Contact contact);
    }

    //TODO: should this clear selected list too?
    public void setDisplayMode(final FilterMode mode) {
        this.displayModes = new ArrayList<>();
        this.displayModes.add(mode);
        refreshList("Changed display mode to " + mode.name());
    }

    public void setDisplayModes(List<FilterMode> modes) {
        this.displayModes = modes;
        refreshList("Changed display modes to " + modes);
    }

    public List<FilterMode> getDisplayModes() {
        return new ArrayList<>(this.displayModes);
    }

    public synchronized void setCurrentList(GroupContact gc) {
        this.currentList = gc;
        updateItems();
        refreshList("Changed current list to " + gc.getTitle());
    }

    public synchronized GroupContact getCurrentList() {
        return this.currentList;
    }

    public List<Contact> getSelected() {
        synchronized (this.selectedUids) {
            return Contacts.fromUIDs(this.selectedUids);
        }
    }

    public List<Contact> getUnselected() {
        synchronized (this.selectedUids) {
            List<Contact> unselected = new ArrayList<Contact>();
            for (Contact c : contacts.getAllContacts()) {
                if (!this.selectedUids.contains(c.contactUUID))
                    unselected.add(c);
            }
            return unselected;
        }
    }

    public List<String> getSelectedUids() {
        synchronized (this.selectedUids) {
            return new ArrayList<>(this.selectedUids);
        }
    }

    public void setSelectedUids(List<String> selectedUids) {
        synchronized (this.selectedUids) {
            this.selectedUids.clear();
            this.selectedUids.addAll(selectedUids);
        }
        notifyChange();
    }

    private static class ViewHolder {
        CheckBox checkBox = null;
        ImageView statusIconImageView = null;
        TextView callsignTextView = null;
        ImageButton profileBtn = null;
        ImageButton defaultCommsBtn = null;
        LinearLayout extraContainer = null;
        CheckBox filterCheckBox = null;
    }

    @Override
    public void onContactsSizeChange(final Contacts contacts) {
        refreshList("Contacts size changed to "
                + contacts.getAllContacts().size());
    }

    @Override
    public void onContactChanged(final String uuid) {
        refreshList("Contact changed: " + uuid);
    }

    public void sort(Sort sortMode) {
        if (sortMode == null)
            sortMode = new HierarchyListItem.SortAlphabet();
        this.sortMode = sortMode;
        this.sortByLocation = sortMode instanceof ComparatorSort
                && ((ComparatorSort) sortMode)
                        .getComparator() == Contact.COMPARE_LOCATION;
        refreshList("Changed sort mode");
    }

    /**
     * Toggle FOV filtering of contacts
     * @param show True to show all contacts, false to filter
     */
    public void showAll(boolean show) {
        if (this.showAll != show) {
            this.showAll = show;
            this.prefs.edit().putBoolean(FOVFilter.PREF, !show).apply();
            //refreshList("Toggled show-all checkbox");
            refreshListImpl();
        }
    }

    /**
     * Toggle unread-only filtering
     * @param on True to enable filtering
     * @param temp Temporarily set state (don't save to prefs)
     */
    public void unreadOnly(boolean on, boolean temp) {
        if (this.unreadOnly != on) {
            this.unreadOnly = on;
            if (!temp)
                this.prefs.edit().putBoolean(UnreadFilter.PREF, on).apply();
            //refreshList("Changed unread-only checkbox");F
            refreshListImpl();
        }
    }

    public void unreadOnly(boolean on) {
        unreadOnly(on, false);
    }

    /**
     * Toggle filtering of contacts
     *
     * @param show True to show all filtered contacts, false to show all
     */
    public void hideFiltered(boolean show) {
        if (this.hideFiltered != show) {
            this.hideFiltered = show;
            this.prefs.edit().putBoolean(FilteredContactsFilter.PREF, !show)
                    .apply();
            refreshListImpl();
        }
    }

    private void refreshFilter() {
        List<HierarchyListFilter> filterList = new ArrayList<>();
        // Contact type filtering
        for (FilterMode mode : getDisplayModes()) {
            ContactFilter cFilter = new ContactFilter(mode, this.sortMode);
            if (mode == FilterMode.UID_WHITELIST
                    || mode == FilterMode.UID_BLACKLIST) {
                synchronized (this.customUIDs) {
                    cFilter.setCustomUIDs(new ArrayList<>(customUIDs));
                }
            }
            filterList.add(cFilter);
        }
        // Filter out contacts without an IP connector (sending only)
        if (this.baseViewMode == ViewMode.SEND_LIST)
            filterList.add(new ConnectorFilter(IpConnector.CONNECTOR_TYPE));
        if (_filterHandler != null)
            filterList.add(new ConnectorHandlerFilter(_filterHandler));
        // FOV filtering
        if (!this.showAll)
            filterList
                    .add(new FOVFilter(new FOVFilter.MapState(mapView),
                            this.sortMode));
        if (this.unreadOnly)
            filterList.add(new UnreadFilter(this.sortMode));

        if (this.hideFiltered)
            filterList.add(new FilteredContactsFilter(this.sortMode));
        // Allow history mode to display locked root user groups
        GroupContact currList = getCurrentList();
        if (currList.getUID().equals(Contacts.USER_GROUPS))
            currList.setHideLockedGroups(viewMode != ViewMode.HISTORY);
        this.filter = new MultiFilter(this.sortMode, filterList);
    }

    public void registerListeners() {
        this.mapView.addOnMapMovedListener(_mapMoveListener);
        Marker self = this.mapView.getSelfMarker();
        if (self != null)
            self.addOnPointChangedListener(_selfMoveListener);
        _active = true;
    }

    public void unregisterListeners() {
        _active = false;
        this.mapView.removeOnMapMovedListener(_mapMoveListener);
        Marker self = this.mapView.getSelfMarker();
        if (self != null)
            self.removeOnPointChangedListener(_selfMoveListener);
    }

    public void dispose() {
        refreshThread.dispose();
        _active = false;
    }

    private void notifyChange() {
        this.ctx.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                notifyDataSetChanged();
            }
        });
    }

    private final AtakMapView.OnMapMovedListener _mapMoveListener = new AtakMapView.OnMapMovedListener() {
        @Override
        public void onMapMoved(AtakMapView view, boolean animate) {
            if (!showAll || sortByLocation
                    && ATAKUtilities.findSelf(mapView) == null)
                refreshList("Map Moved");
        }
    };

    private final PointMapItem.OnPointChangedListener _selfMoveListener = new PointMapItem.OnPointChangedListener() {
        @Override
        public void onPointChanged(PointMapItem item) {
            if (sortByLocation)
                refreshList("Self Moved");
        }
    };

    private static class ConnectorFilter extends HierarchyListFilter {

        private final String _connector;

        ConnectorFilter(String connectorType) {
            super(null);
            _connector = connectorType;
        }

        @Override
        public boolean accept(HierarchyListItem item) {
            return !(item instanceof IndividualContact) ||
                    ((IndividualContact) item).hasConnector(
                            _connector);
        }
    }

    private static class ConnectorHandlerFilter extends HierarchyListFilter {

        private final ContactConnectorHandler _handler;

        public ConnectorHandlerFilter(ContactConnectorHandler handler) {
            super(null);
            _handler = handler;
        }

        @Override
        public boolean accept(HierarchyListItem item) {
            if (item instanceof IndividualContact) {
                IndividualContact ic = (IndividualContact) item;
                for (Connector c : ic.getConnectors(false))
                    if (_handler.isSupported(c))
                        return true;
                return false;
            }
            return true;
        }
    }
}

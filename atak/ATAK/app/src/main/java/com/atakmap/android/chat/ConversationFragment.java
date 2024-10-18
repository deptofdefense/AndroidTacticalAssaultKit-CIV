
package com.atakmap.android.chat;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;

import com.atakmap.android.contact.Contact;
import com.atakmap.android.contact.Contacts;
import com.atakmap.android.contact.FilteredContactsManager;
import com.atakmap.android.contact.IndividualContact;
import com.atakmap.android.hierarchy.action.GoTo;
import com.atakmap.android.hierarchy.items.MapItemUser;
import com.atakmap.android.ipc.AtakBroadcast.DocumentedIntentFilter;
import android.content.SharedPreferences;
import android.content.res.XmlResourceParser;
import android.graphics.Color;
import android.os.Bundle;
import android.preference.PreferenceManager;
import androidx.fragment.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;

import com.atakmap.android.chat.ModePicker.ModeUpdateListener;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.ILocation;
import com.atakmap.android.maps.MapTouchController;
import com.atakmap.android.maps.MapView;
import com.atakmap.app.R;
import com.atakmap.coremap.log.Log;

import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.time.CoordinatedTime;
import com.atakmap.map.CameraController;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

public class ConversationFragment extends Fragment implements
        ModeUpdateListener, View.OnClickListener {

    static private class ButtonHolder {
        public final int index; // Not really used
        public final String text;
        public final String value;

        ButtonHolder(int i, String t, String v) {
            index = i;
            text = t;
            value = v;
        }

        public String toString() {
            return "Button - index: " + index + " - text: " + text
                    + " - value: " + value;
        }
    }

    private class Mode {
        public final ArrayList<ButtonHolder> buttons;
        public final String name;

        public Mode(String n, ArrayList<ButtonHolder> b) {
            name = n;
            buttons = b;
            buttons.ensureCapacity(8);
        }

        public Mode(String n) {
            name = n;
            buttons = primeList(8);
        }

        @Override
        public int hashCode() {
            int result = ((buttons == null) ? 0 : buttons.hashCode());
            result = 31 * result + ((name == null) ? 0 : name.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Mode && ((Mode) o).name.equals(this.name);
        }
    }

    /**Returns the attached ChatLineAdapter
     * Can be @null
     * @return ChatLineAdapter
     */
    public ConversationListAdapter getChatAdapter() {
        return _mChatLineAdapter;
    }

    private ArrayList<ButtonHolder> primeList(int size) {
        ArrayList<ButtonHolder> output = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            output.add(new ButtonHolder(i, "", ""));
        }
        return output;
    }

    private static final String TAG = "ConversationFragment";

    private int editAreaVisibility = View.VISIBLE;
    ListView lineList = null;
    TextView titleText = null;
    MapView mapView = null;
    String targetUID = null;
    EditText inputMessage = null;
    private ConversationListAdapter _mChatLineAdapter;
    private boolean _isGroup = false;
    private TableLayout _table;
    private SharedPreferences _chatPrefs;
    private ModePicker _picker;
    private final ArrayList<Mode> modes = new ArrayList<>();
    private BroadcastReceiver _clearReceiver;

    String title = "";

    public ConversationFragment() {
        //_mChatLineAdapter = new ConversationListAdapter(MapView.getMapView()
        //        .getContext());
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        getLineAdapter();
        // Now, add history...
        if (getChatCount() == 0)
            populateHistory();
    }

    private final BroadcastReceiver _historyReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            getLineAdapter().clearChat();
            populateHistory();
        }
    };

    private ConversationListAdapter getLineAdapter() {
        if (_mChatLineAdapter == null) {
            _mChatLineAdapter = new ConversationListAdapter(MapView
                    .getMapView().getContext());
            AtakBroadcast.getInstance().registerReceiver(_historyReceiver,
                    new DocumentedIntentFilter(GeoChatService.HISTORY_UPDATE));

            // DocumentedIntentFilter for incoming chat messages
            AtakBroadcast.DocumentedIntentFilter contactFilter = new AtakBroadcast.DocumentedIntentFilter();
            contactFilter
                    .addAction(FilteredContactsManager.ATAK_FILTER_CONTACT);
            AtakBroadcast.getInstance().registerReceiver(contactFilterReceiver,
                    contactFilter);
        }
        return _mChatLineAdapter;
    }

    private final BroadcastReceiver contactFilterReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent != null) {
                for (Contact c : Contacts.getInstance().getAllContacts()) {
                    if (c instanceof IndividualContact
                            && c.getName().equals(title))
                        c.setUnreadCount(_mChatLineAdapter.getUnreadCount());
                }
                Contacts.getInstance().updateTotalUnreadCount();
            }
        }
    };

    public boolean addOrAckChatLine(ChatLine toAddOrAck) {
        if (!acksAvailable)
            toAddOrAck.acked = true;

        if (isVisible() && !toAddOrAck.read) {
            toAddOrAck.read = true;
            GeoChatService.getInstance().sendReadStatus(toAddOrAck);
        }

        return getLineAdapter().addOrAckChatLine(toAddOrAck);
    }

    public ConversationFragment setIsGroup(boolean isGroup) {
        this._isGroup = isGroup;
        return this;
    }

    public void populateHistory() {
        if (onHistoryRequest != null) {
            List<ChatLine> history = onHistoryRequest.onHistoryRequest();
            for (ChatLine line : history) {
                line.read = true;
                line.acked = false;
                getLineAdapter().addChatLine(line);
            }
        }
    }

    private ChatManagerMapComponent.MessageDestination _destinations = null;

    public ConversationFragment setDests(
            ChatManagerMapComponent.MessageDestination destinations) {
        _destinations = destinations;
        return this;
    }

    public ChatManagerMapComponent.MessageDestination getDests() {
        return _destinations;
    }

    public boolean isGroup() {
        return this._isGroup;
    }

    public ConversationFragment setTitle(String title) {
        this.title = title;
        return this;
    }

    public ConversationFragment setMapView(final MapView mapView) {
        this.mapView = mapView;
        return this;
    }

    public ConversationFragment setTargetUID(final String targetUID) {
        this.targetUID = targetUID;
        return this;
    }

    public MapView getMapView() {
        return mapView;
    }

    public String getTargetUID() {
        return targetUID;
    }

    public Contact getTarget() {
        return Contacts.getInstance().getContactByUuid(getTargetUID());
    }

    public String getTitle() {
        return title;
    }

    interface SendBehavior {
        void onSend(Bundle chatMessage);
    }

    private SendBehavior onSend = null;

    public ConversationFragment setSendBehavior(SendBehavior onSend) {
        this.onSend = onSend;
        return this;
    }

    interface HistoryBehavior {
        List<ChatLine> onHistoryRequest();
    }

    private HistoryBehavior onHistoryRequest = null;

    public ConversationFragment setHistoryBehavior(
            HistoryBehavior onHistoryRequest) {
        this.onHistoryRequest = onHistoryRequest;
        return this;
    }

    private boolean acksAvailable = false;

    public ConversationFragment setAckEnabled(boolean enabled) {
        acksAvailable = enabled;
        return this;
    }

    public void setUserEntryAreaVisibility(boolean visible) {
        editAreaVisibility = (visible) ? View.VISIBLE : View.GONE;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {

        getLineAdapter().markAllRead();
        View rootView = inflater.inflate(R.layout.conversation_main, container,
                false);

        lineList = rootView.findViewById(R.id.lineList);
        lineList.setAdapter(getLineAdapter());
        lineList.setTranscriptMode(ListView.TRANSCRIPT_MODE_ALWAYS_SCROLL);
        lineList.setStackFromBottom(true);

        titleText = rootView
                .findViewById(R.id.conversationTitleText);
        if (titleText != null) {
            titleText.setText(getTitle());
        }

        rootView.findViewById(R.id.chat_user_entry_area).setVisibility(
                editAreaVisibility);

        final ImageButton panTo = rootView
                .findViewById(R.id.conversationPanButton);
        panTo.setOnClickListener(this);

        final Contact target = getTarget();
        panTo.setVisibility(target instanceof MapItemUser
                && ((MapItemUser) target).getMapItem() != null
                || target instanceof ILocation
                || (target != null && target.getAction(GoTo.class) != null)
                        ? View.VISIBLE
                        : View.GONE);

        inputMessage = rootView.findViewById(R.id.messageBox);

        Button sendButton = rootView.findViewById(R.id.sendButton);
        sendButton.setOnClickListener(this);

        _chatPrefs = PreferenceManager.getDefaultSharedPreferences(MapView
                .getMapView().getContext());
        _table = rootView.findViewById(R.id.button_table_layout);
        _picker = rootView.findViewById(R.id.mode_picker);

        String[] modeNames = parseModes();
        if (modeNames != null) {
            _picker.setValues(modeNames);
            _picker.setOnModeUpdateListener(this);
            this.onModeUpdate(modeNames[0]);
            _chatPrefs.edit().putInt("ChatModeCount", modeNames.length)
                    .apply();
        }

        int rowCount = Integer.parseInt(_chatPrefs.getString("rowCount", "1"));
        int btnCount = Integer.parseInt(_chatPrefs.getString("btnCount", "4"));

        for (int r = 0; r < rowCount; r++) {
            TableRow row = (TableRow) _table.getChildAt(r);
            for (int i = 0; i < btnCount; i++) {
                final Button btn = (Button) row.getChildAt(i);
                btn.setOnClickListener(new OnClickListener() {

                    @Override
                    public void onClick(View v) {
                        // When using quick keys, just add in a " " at the end to provide the user the ability
                        // to either add another quick key or start typing without hitting the space bar.
                        if (btn.getTag() != null)
                            inputMessage.append(btn.getTag() + " ");
                        v.setPressed(false);
                    }
                });
                btn.setHint(String.valueOf(r * 4 + i)); // We'll use the hint as a way to store the
                                                        // buttons index
                btn.setOnLongClickListener(new OnLongClickListener() {

                    @Override
                    public boolean onLongClick(View arg0) {
                        AlertDialog.Builder builder = new AlertDialog.Builder(
                                ConversationFragment.this.getActivity());
                        final LayoutInflater inflater = LayoutInflater
                                .from(ConversationFragment.this.getActivity());
                        View pocView = inflater.inflate(R.layout.custom_button,
                                null);
                        builder.setView(pocView);
                        builder.setTitle(R.string.chat_text1);
                        final EditText buttonName = pocView
                                .findViewById(R.id.buttonName);
                        buttonName.setTextColor(Color.BLACK);
                        buttonName.setText(btn.getText());
                        final EditText buttonValue = pocView
                                .findViewById(R.id.buttonValue);
                        buttonValue.setTextColor(Color.BLACK);
                        // get the value that button should output
                        String valueString = modes.get(_picker
                                .getCurrentIndex()).buttons
                                        .get(Integer.parseInt(
                                                (String) btn.getHint())).value;
                        if (valueString != null)
                            buttonValue.setText(valueString);
                        builder.setPositiveButton(R.string.ok, null);
                        builder.setNegativeButton(R.string.cancel, null);
                        final AlertDialog dialog = builder.create();
                        dialog.setOnShowListener(
                                new DialogInterface.OnShowListener() {
                                    @Override
                                    public void onShow(DialogInterface d) {
                                        Button ok = ((AlertDialog) d)
                                                .getButton(
                                                        AlertDialog.BUTTON_POSITIVE);
                                        ok.setOnClickListener(
                                                new OnClickListener() {
                                                    @Override
                                                    public void onClick(
                                                            View v) {
                                                        if (buttonName.getText()
                                                                .length() > 0
                                                                &&
                                                                buttonValue
                                                                        .getText()
                                                                        .length() > 0) {
                                                            Intent intent = new Intent(
                                                                    "com.atakmap.android.MODES_EDITED");
                                                            AtakBroadcast
                                                                    .getInstance()
                                                                    .sendBroadcast(
                                                                            intent);

                                                            String text = buttonName
                                                                    .getText()
                                                                    .toString();
                                                            String msg = buttonValue
                                                                    .getText()
                                                                    .toString();
                                                            String btnLoc = (String) btn
                                                                    .getHint();
                                                            _chatPrefs
                                                                    .edit()
                                                                    .putString(
                                                                            "btnText"
                                                                                    + _picker
                                                                                            .getCurrentIndex()
                                                                                    + ""
                                                                                    + btnLoc,
                                                                            text)
                                                                    .apply();
                                                            _chatPrefs
                                                                    .edit()
                                                                    .putString(
                                                                            "btnMsg"
                                                                                    + _picker
                                                                                            .getCurrentIndex()
                                                                                    + ""
                                                                                    + btnLoc,
                                                                            msg)
                                                                    .apply();
                                                            Log.e(TAG,
                                                                    "long press");
                                                            Log.e(TAG,
                                                                    "btnText"
                                                                            + _picker
                                                                                    .getCurrentIndex()
                                                                            + ""
                                                                            + btnLoc);
                                                            Log.e(TAG,
                                                                    "btnMsg"
                                                                            + _picker
                                                                                    .getCurrentIndex()
                                                                            + ""
                                                                            + btnLoc);
                                                            btn.setText(text);
                                                            btn.setTag(msg);
                                                            modes.get(_picker
                                                                    .getCurrentIndex()).buttons
                                                                            .set(
                                                                                    Integer.parseInt(
                                                                                            btnLoc),
                                                                                    new ButtonHolder(
                                                                                            Integer.parseInt(
                                                                                                    btnLoc),
                                                                                            text,
                                                                                            msg));
                                                            dialog.dismiss();
                                                        } else {
                                                            // display error prompt here
                                                            AlertDialog.Builder builder2 = new AlertDialog.Builder(
                                                                    ConversationFragment.this
                                                                            .getActivity());
                                                            View errorView = inflater
                                                                    .inflate(
                                                                            R.layout.custom_button_error,
                                                                            null);
                                                            builder2.setView(
                                                                    errorView);
                                                            builder2.setTitle(
                                                                    R.string.chat_text2);
                                                            builder2.setPositiveButton(
                                                                    R.string.ok,
                                                                    null);
                                                            final AlertDialog dialog2 = builder2
                                                                    .create();
                                                            dialog2.setOnShowListener(
                                                                    new DialogInterface.OnShowListener() {
                                                                        @Override
                                                                        public void onShow(
                                                                                DialogInterface d) {
                                                                            Button ok = ((AlertDialog) d)
                                                                                    .getButton(
                                                                                            AlertDialog.BUTTON_POSITIVE);
                                                                            ok.setOnClickListener(
                                                                                    new OnClickListener() {
                                                                                        @Override
                                                                                        public void onClick(
                                                                                                View v) {
                                                                                            dialog2.dismiss();
                                                                                        }
                                                                                    });
                                                                        }
                                                                    });
                                                            dialog2.show();
                                                        }
                                                    }
                                                });
                                    }
                                });
                        dialog.show();
                        return true;
                    }

                });
            }
        }

        DocumentedIntentFilter filter = new DocumentedIntentFilter();
        filter.addAction("com.atakmap.android.PREFERENCED_LOADED");
        AtakBroadcast.getInstance().registerReceiver(
                _clearReceiver = new BroadcastReceiver() {

                    @Override
                    public void onReceive(Context arg0, Intent arg1) {
                        modes.clear();
                    }

                }, filter);

        rootView.setBackgroundColor(Color.BLACK);
        return rootView;

    }

    @Override
    public void onClick(View v) {
        int id = v.getId();

        // Pan to contact
        if (id == R.id.conversationPanButton) {
            Contact target = getTarget();
            if (target instanceof GoTo)
                ((GoTo) target).goTo(false);
            else if (target instanceof MapItemUser)
                MapTouchController.goTo(((MapItemUser) target).getMapItem(),
                        false);
            else if (target instanceof ILocation) {
                GeoPoint point = ((ILocation) target).getPoint(null);
                if (point.isValid() && getMapView() != null) {
                    CameraController.Programmatic.panTo(
                            getMapView().getRenderer3(),
                            point, false);
                }
            }
        }

        // Send message
        else if (id == R.id.sendButton) {
            // trim any trailing spaces
            String msg = inputMessage.getText().toString();

            // trim any trailing spaces
            try {
                msg = msg.replaceFirst("\\s++$", "");
            } catch (Exception ignored) {
            }

            // Ignore empty message
            if (msg.isEmpty())
                return;

            // Check if the message has anywhere to go
            final List<Contact> contacts = _destinations.getDestinations();
            final List<Contact> recipients = new ArrayList<>();
            for (Contact c : contacts) {
                if (c != null) {
                    final List<Contact> filtered = c.getFiltered(true, true);
                    if (filtered != null) {
                        for (Contact c2 : filtered) {
                            if (c2 instanceof IndividualContact)
                                recipients.add(c2);
                        }
                    }
                } else {
                    Log.d(TAG, "contact was null for: " + _destinations);
                }
            }

            // Nobody to send it to
            if (recipients.isEmpty()) {
                Toast.makeText(getContext(),
                        R.string.chat_message_no_recipients,
                        Toast.LENGTH_LONG).show();
                return;
            }

            ChatLine toAdd = new ChatLine();
            toAdd.messageId = UUID.randomUUID().toString();
            toAdd.timeSent = (new CoordinatedTime()).getMilliseconds();
            toAdd.senderUid = MapView.getDeviceUid();
            toAdd.message = msg;
            final Bundle arguments = getArguments();
            String conversationId = arguments != null
                    ? arguments.getString("id")
                    : null;
            toAdd.conversationId = (conversationId != null) ? conversationId
                    : "";
            toAdd.conversationName = getTitle();

            if (onSend != null) {
                onSend.onSend(toAdd.toBundle());
            } else {
                Log.w(TAG,
                        "No service available to send outgoing chat message");
            }

            addOrAckChatLine(toAdd);
            inputMessage.setText("");
        }
    }

    private String[] parseModes() {
        final ArrayList<String> modes = new ArrayList<>();
        XmlResourceParser chat_modes = null;
        try {
            final Activity activity = getActivity();
            if (activity != null) {
                chat_modes = activity.getResources().getXml(R.xml.chat_modes);
                int eventType = chat_modes.getEventType();
                while (eventType != XmlResourceParser.END_DOCUMENT) {
                    if (eventType == XmlResourceParser.START_TAG &&
                            chat_modes.getName().equals("mode")) {
                        modes.add(chat_modes.getAttributeValue(null, "name"));
                    }
                    eventType = chat_modes.next();
                }
            }
        } catch (Exception ignored) {

        } finally {
            if (chat_modes != null) {
                chat_modes.close();
            }
        }

        final String[] output = new String[modes.size()];
        return modes.toArray(output);
    }

    private ArrayList<ButtonHolder> parseButtons(final String modeName) {
        int index = modes.indexOf(new Mode(modeName));
        if (index != -1) {
            return modes.get(index).buttons;
        }
        final ArrayList<ButtonHolder> output = primeList(4);
        // new ArrayList<ButtonHolder>(8);
        final Activity activity = getActivity();
        if (activity != null) {
            final XmlResourceParser chat_modes = activity.getResources()
                    .getXml(R.xml.chat_modes);
            try {
                int eventType = chat_modes.getEventType();
                boolean modeFound = false;
                int modeIndex = -1;

                while (eventType != XmlResourceParser.END_DOCUMENT) {
                    if (eventType == XmlResourceParser.START_TAG) {
                        // Log.e(TAG, "Start of element: " + chat_modes.getName() + " name: " +
                        // chat_modes.getAttributeValue(null, "name"));
                        if (chat_modes.getName().equals("mode")
                                &&
                                chat_modes.getAttributeValue(null, "name")
                                        .equals(
                                                modeName)) {
                            // Log.e(TAG, "Found Mode");
                            modeIndex = Integer.parseInt(chat_modes
                                    .getAttributeValue(null, "index"));
                            modeFound = true;
                        } else if (chat_modes.getName().equals("button") &&
                                modeFound) {
                            // Log.e(TAG, "Found button");
                            int buttonIndex = Integer.parseInt(chat_modes
                                    .getAttributeValue(null,
                                            "index"));
                            output.set(
                                    buttonIndex,
                                    new ButtonHolder(buttonIndex, chat_modes
                                            .getAttributeValue(null,
                                                    "label"),
                                            chat_modes
                                                    .getAttributeValue(null,
                                                            "value")));
                        }
                    } else if (eventType == XmlResourceParser.END_TAG) {
                        // Log.e(TAG, "End of element: " + chat_modes.getName());
                        if (chat_modes.getName().equals("mode") && modeFound) {
                            for (int buttonIndex = 0; buttonIndex < 4; buttonIndex++) {
                                String text = _chatPrefs.getString("btnText"
                                        + modeIndex + ""
                                        + buttonIndex, "");
                                String msg = _chatPrefs.getString("btnMsg"
                                        + modeIndex + ""
                                        + buttonIndex, "");

                                if (!msg.equals("") && !text.equals(""))
                                    output.set(buttonIndex, new ButtonHolder(
                                            buttonIndex, text, msg));
                            }
                            modeFound = false;
                            modes.add(modeIndex, new Mode(modeName, output));
                            modeIndex = -1;
                        }
                    }
                    eventType = chat_modes.next();
                }
            } catch (Exception e) {
                Log.e(TAG, "Encountered error", e);
            } finally {
                if (chat_modes != null)
                    chat_modes.close();
            }
        }
        return output;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        AtakBroadcast.getInstance().unregisterReceiver(_clearReceiver);
        AtakBroadcast.getInstance().unregisterReceiver(contactFilterReceiver);
    }

    @Override
    public void onModeUpdate(String mode) {
        ArrayList<ButtonHolder> values = parseButtons(mode);
        Iterator<ButtonHolder> buttons = values.iterator();
        if (buttons.hasNext()) {
            for (int r = 0; r < _table.getChildCount(); r++) {
                TableRow row = (TableRow) _table.getChildAt(r);
                for (int b = 0; b < 4; b++) {
                    Button btn = (Button) row.getChildAt(b);
                    if (buttons.hasNext()) {
                        ButtonHolder button = buttons.next();
                        btn.setText(button.text);
                        btn.setTag(button.value);
                    } else {
                        btn.setText(" ");
                        btn.setTag("");
                    }
                }
            }
        }
        /*
         * else //We're on the custom mode { for(int r = 0; r < _table.getChildCount(); r++) {
         * TableRow row = (TableRow)_table.getChildAt(r); for(int i = 0; i < row.getChildCount();
         * i++) { //final Button btn = new Button(context); final Button btn =
         * (Button)row.getChildAt(i); String msg = _chatPrefs.getString("btnMsg:" + i + "row" + r,
         * ""); String text = _chatPrefs.getString("btnText" + i + "row" + r, ""); Log.e(TAG,
         * "On button " + i + ", " + r); if(!msg.equals("") && !text.equals("")) {
         * btn.setText(text); btn.setTag(msg); } } } }
         */
    }

    public int getChatCount() {
        int count = 0;
        if (_mChatLineAdapter != null)
            count = _mChatLineAdapter.getCount();
        return count;
    }

    public int getUnreadCount() {
        int unreadCount = 0;

        if (_mChatLineAdapter != null) {
            unreadCount = _mChatLineAdapter.getUnreadCount();
            Log.d(TAG, "size: " + _mChatLineAdapter.getCount());
        }
        return unreadCount;
    }

    public void removeLastChatLine() {
        if (_mChatLineAdapter != null)
            _mChatLineAdapter.removeLastChatLine();
    }

    //Need to know when the list is created because of how "read" chat lines are implemented
    private final List<ChatConvoFragCreateWatcher> watcherList = new ArrayList<>();

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        while (watcherList.size() > 0) {
            final ChatConvoFragCreateWatcher watcher = watcherList.remove(0);
            // the next call will eventually remove the watcher from
            // the list.
            watcher.onChatConvoFragCreated(this);
        }
    }

    public void addChatConvoFragCreateWatcher(
            ChatConvoFragCreateWatcher watcher) {
        if (!watcherList.contains(watcher))
            watcherList.add(watcher);
    }

    public void removeChatConvoFragCreateWatcher(
            ChatConvoFragCreateWatcher watcher) {
        watcherList.remove(watcher);
    }
}


package com.atakmap.android.chat;

import android.content.Context;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnLongClickListener;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;

import com.atakmap.android.contact.Contact;
import com.atakmap.android.contact.Contacts;
import com.atakmap.android.contact.FilteredContactsManager;
import com.atakmap.android.maps.MapView;
import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.locale.LocaleUtil;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.time.CoordinatedTime;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

class ConversationListAdapter extends BaseAdapter {

    private static final String TAG = "ConversationListAdapter";

    private static final long MILLIS_TIME_LIMIT = 60000; // one minute
    private final SimpleDateFormat timeOnlyFormat = new SimpleDateFormat(
            "(HH:mm:ss) ", LocaleUtil.getCurrent());
    private final SimpleDateFormat dateOnlyFormat = new SimpleDateFormat(
            "MM/dd/yyyy", LocaleUtil.getCurrent());
    private final Context mContext;
    private final List<ChatLine> chatLines = new ArrayList<>();

    private ChatLine latestSelfMsg, latestSelfChain;

    ConversationListAdapter(final Context context) {
        mContext = context;
    }

    @Override
    public int getCount() {
        return chatLines.size();
    }

    @Override
    public Object getItem(int position) {
        return chatLines.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {

        /**
         * Check to see if a plugin has been loaded that will supply a custom rendering capability
         */
        final ChatMesssageRenderer renderer = ChatManagerMapComponent
                .getInstance().getChatMesssageRenderer();
        if (renderer != null) {
            return renderer.getView(position, convertView, parent,
                    chatLines.get(position));
        }

        ViewHolder holder;
        if (convertView == null) {
            convertView = LayoutInflater
                    .from(mContext)
                    .inflate(R.layout.conversation_item, parent, false);
            holder = new ViewHolder();
            final ViewHolder fholder = holder;
            convertView.setOnLongClickListener(new OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    if (fholder.message == null)
                        return true;
                    android.content.ClipboardManager clipboard = (android.content.ClipboardManager) mContext
                            .getSystemService(Context.CLIPBOARD_SERVICE);
                    if (clipboard != null) {
                        android.content.ClipData clip = android.content.ClipData
                                .newPlainText("Copied Text", fholder.message
                                        .getText().toString());
                        clipboard.setPrimaryClip(clip);
                        Toast.makeText(mContext, R.string.copied_to_clipboard,
                                Toast.LENGTH_SHORT).show();
                    }
                    return true;
                }
            });

            holder.timestampHolder = convertView
                    .findViewById(R.id.timestampText);
            holder.timestamp = convertView
                    .findViewById(R.id.timestamp);
            holder.sender = convertView.findViewById(R.id.sender);
            holder.message = convertView.findViewById(R.id.message);
            holder.status = convertView.findViewById(R.id.status);
            holder.sendProgress = convertView.findViewById(R.id.sendProgress);
            holder.sentSuccessfully = convertView
                    .findViewById(R.id.messageSent);
            holder.date = convertView.findViewById(R.id.date);
            holder.dateTableRow = convertView
                    .findViewById(R.id.dateTableRow);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        updateViewHolder(holder, position);

        return convertView;
    }

    private void updateViewHolder(ViewHolder holder, int position) {
        ChatLine chat;
        ChatLine lastChat;

        chat = chatLines.get(position);
        lastChat = position > 0 ? chatLines.get(position - 1) : null;

        if (chat == null) {
            Log.w(TAG, "No chat line for position: " + position);
        } else {

            if (chat.senderUid.equals(MapView.getDeviceUid())) { // Sent from this device...
                holder.timestamp.setTextAppearance(mContext, R.style.SendLabel);
                holder.sender.setTextAppearance(mContext, R.style.SendLabel);
                holder.timestampHolder.setGravity(Gravity.END);
                holder.message.setGravity(Gravity.END);
                holder.sender.setText(R.string.chat_text18);
            } else { // Received by this device...
                holder.timestamp.setTextAppearance(mContext,
                        R.style.ReceiveLabel);
                holder.sender.setTextAppearance(mContext, R.style.ReceiveLabel);
                holder.message.setGravity(Gravity.START);
                holder.timestampHolder.setGravity(Gravity.START);

                String callsign = getCallsign(chat);

                holder.sender.setText(String.format(LocaleUtil.getCurrent(),
                        "%s: ", callsign));
            }

            // Timestamp (read -> received -> sent)
            Long ts = chat.timeSent;
            if (chat.timeRead != null)
                ts = chat.timeRead;
            else if (chat.timeReceived != null)
                ts = chat.timeReceived;
            holder.timestamp.setText(timeAsString(ts));

            // Set the Size of the text
            holder.sender.setTextSize(15);
            holder.timestamp.setTextSize(10);

            // Message delivery status
            if (chat == latestSelfChain && latestSelfMsg != null
                    && latestSelfMsg.status != ChatLine.Status.NONE) {
                holder.status.setImageResource(
                        latestSelfMsg.status == ChatLine.Status.DELIVERED
                                ? R.drawable.chat_message_delivered
                                : R.drawable.chat_message_read);
                holder.status.setVisibility(View.VISIBLE);
            } else
                holder.status.setVisibility(View.GONE);

            holder.message.setText(chat.message);
            if (isChatFirstForDay(chat, lastChat)
                    && chat.getTimeSentOrReceived() != null) {
                holder.date.setText(dateAsString(chat.getTimeSentOrReceived()));
                holder.dateTableRow.setVisibility(View.VISIBLE);
            } else {
                holder.dateTableRow.setVisibility(View.GONE);
            }

            // don't display name/time if same as previous message
            if (lineShouldBeAppendedTo(lastChat, chat)) {
                holder.timestampHolder.setVisibility(View.GONE);
                holder.timestamp.setVisibility(View.GONE);
                holder.sender.setVisibility(View.GONE);
                holder.sendProgress.setVisibility(View.GONE);
            } else {
                holder.timestampHolder.setVisibility(View.VISIBLE);
                holder.timestamp.setVisibility(View.VISIBLE);
                holder.sender.setVisibility(View.VISIBLE);
                holder.sendProgress.setVisibility(View.VISIBLE);
            }

            // display/hide progress spinner
            if (chat.timeReceived == null) {
                if (chat.acked) {
                    holder.sendProgress.setVisibility(View.GONE);
                } else {
                    // Spin until I get an ACK
                    holder.sentSuccessfully.setVisibility(View.GONE);
                    holder.sendProgress.setVisibility(View.VISIBLE);
                    holder.sendProgress.setIndeterminate(true);
                }
            } else {
                // Not applicable for received messages
                holder.sentSuccessfully.setVisibility(View.GONE);
                holder.sendProgress.setVisibility(View.GONE);
            }

        }
    }

    /**determines if the current chat line is the first chat message(line) for
     * the day we are not displaying dates on all messages just the start of a new day
     * or start of a day of groups
     * @param chat current chat being processed
     * @param lastChat last chat being processed
     * @return bool indicating whether chat is first for the day
     */
    private boolean isChatFirstForDay(ChatLine chat,
            ChatLine lastChat) {

        //if there is no previous chatline then this is the first return true
        if (lastChat == null)
            return true;
        //wrap calendar items
        Calendar c1 = Calendar.getInstance();
        Calendar c2 = Calendar.getInstance();

        //set times (default to now if time is missing for some reason)
        Long c1Time = chat.getTimeSentOrReceived(), c2Time = lastChat
                .getTimeSentOrReceived();
        long now = new CoordinatedTime().getMilliseconds();
        c1.setTime(new Date(c1Time == null ? now : c1Time));
        c2.setTime(new Date(c2Time == null ? now : c2Time));

        //compare the aspects of the dates
        return !(c1.get(Calendar.YEAR) == c2.get(Calendar.YEAR)
                && c1.get(Calendar.MONTH) == c2.get(Calendar.MONTH)
                && c1.get(Calendar.DAY_OF_MONTH) == c2
                        .get(Calendar.DAY_OF_MONTH));
    }

    /**
     * Add or acknowledged a chatline.
     * 
     * @param line The chat line to check
     * @return true if added, false if acknowledged
     */
    boolean addOrAckChatLine(ChatLine line) {
        // Check if it already exists
        for (ChatLine l : chatLines) {
            // Check message-id and sendersName (old ATAK just used an incrementing message ID)
            if (FileSystemUtils.isEquals(line.messageId, l.messageId)) {
                // Ack it if it does exist
                ackChatLine(line, l);
                return false;
            }
        }
        // Add it if it doesn't exist
        addChatLine(line);
        return true;
    }

    private String getCallsign(ChatLine message) {
        String callsignToReturn = "";

        String senderName = message.senderName;
        if (senderName != null && !senderName.isEmpty()) {
            callsignToReturn = senderName;
        } else {
            Contact sender = Contacts.getInstance().getContactByUuid(
                    message.senderUid);
            if (sender != null) {
                callsignToReturn = sender.getName();
            }
        }

        if (callsignToReturn.isEmpty()) {
            callsignToReturn = mContext.getString(R.string.unknown);
        }

        return callsignToReturn;

    }

    /**
     * Iterate through the array marking all messages as read.
     */
    void markAllRead() {

        String conversationId = "";
        ChatLine newestIncoming = null;
        for (ChatLine line : chatLines) {
            if (!line.read) {
                if (!line.isSelfChat())
                    newestIncoming = line;
                line.read = true;
                conversationId = line.conversationId;
            }
        }
        GeoChatService.getInstance().sendReadStatus(newestIncoming);
        if (conversationId != null && !conversationId.isEmpty()) {
            notifyContactListAdapter(conversationId);
            notifyDataSetChanged();
        }
    }

    /**
     * Iterate through the array marking a single message as read.
     * without changing other messages that are marked in the same conversation fragment
     */
    void markSingleRead(String messageId) {

        String conversationId = "";
        ChatLine match = null;
        for (ChatLine line : chatLines) {
            //find the message we need to mark read
            if (line != null
                    && FileSystemUtils.isEquals(line.messageId, messageId)) {
                match = line;
                line.read = true;
                conversationId = line.conversationId;
                break;
            }
        }
        GeoChatService.getInstance().sendReadStatus(match);
        if (conversationId != null && !conversationId.isEmpty()) {
            //update attached adapter
            notifyContactListAdapter(conversationId);
            notifyDataSetChanged();
        }
    }

    /**
     * Remove the last chatline in the arraylist.
     * 
     * <p>
     * 
     * When a new message is received it is added to the DB before anything happens. 
     * So when the history is populated this new message is automatically added to 
     * the history even though it hasn't been viewed yet. This method removes that latest message
     * in order to the unread counts to be correct.
     * 
     */
    void removeLastChatLine() {
        int index = chatLines.size() - 1;
        if (index >= 0)
            chatLines.remove(chatLines.size() - 1);
    }

    void addChatLine(ChatLine toAdd) {
        Log.d(TAG, "adding message: " + toAdd.messageId);
        chatLines.add(toAdd);

        if (!toAdd.read)
            notifyContactListAdapter(toAdd.conversationId);

        notifyDataSetChanged();
    }

    private void notifyContactListAdapter(String conversationId) {
        Contact contact = Contacts.getInstance().getContactByUuid(
                conversationId);
        if (contact != null) {
            //make sure to have contact fire off its change event by set extras as so
            contact.setUnreadCount(getUnreadCount());
            contact.dispatchChangeEvent();
        }
    }

    private void ackChatLine(ChatLine src, ChatLine dst) {
        Log.d(TAG, "Updating message: " + src.messageId);
        dst.acked = true;
        dst.status = src.status;
        dst.timeReceived = src.timeReceived;
        dst.timeRead = src.timeRead;
        notifyDataSetChanged();
    }

    /**format the time to specific format
     * from the stored datetime(long)
     * @param datetime the date time to format
     * @return String readable time
     */
    synchronized private String timeAsString(Long datetime) {
        return datetime != null ? timeOnlyFormat.format(new Date(datetime))
                : "";
    }

    /**formats the date to specific format
     * from stored datetime(long)
     * @param datetime the datetime to format
     * @return String readable date
     */
    synchronized private String dateAsString(Long datetime) {
        return dateOnlyFormat.format(new Date(datetime));
    }

    private static class ViewHolder {
        LinearLayout timestampHolder;
        TextView timestamp;
        TextView sender;
        TextView message;
        ImageView status;
        ProgressBar sendProgress;
        ImageView sentSuccessfully;
        TextView date;
        TableRow dateTableRow;
    }

    private static boolean lineShouldBeAppendedTo(ChatLine lastLine,
            ChatLine newLine) {
        return lastLine != null && newLine != null
                && lastLine.senderUid != null
                && lastLine.senderUid.equals(newLine.senderUid)
                && withinTimeLimit(lastLine, newLine);
    }

    private static boolean withinTimeLimit(ChatLine lastLine,
            ChatLine newLine) {
        return lastLine != null
                && newLine != null
                && lastLine.getTimeSentOrReceived() != null
                && newLine.getTimeSentOrReceived() != null
                && newLine.getTimeSentOrReceived()
                        - lastLine.getTimeSentOrReceived() > 0
                && newLine.getTimeSentOrReceived()
                        - lastLine.getTimeSentOrReceived() < MILLIS_TIME_LIMIT;
    }

    /**
     * 
     * Get number of unread messages.
     * 
     * @return number of unread messages
     */
    public int getUnreadCount() {
        int unreadCount = 0;
        for (ChatLine line : chatLines)
            if (!line.read && !FilteredContactsManager.getInstance()
                    .isContactFiltered(line.senderUid))
                unreadCount++;
        return unreadCount;
    }

    void clearChat() {
        String convId = chatLines.isEmpty() ? null
                : chatLines.get(0).conversationId;
        chatLines.clear();
        if (convId != null && !convId.isEmpty())
            notifyContactListAdapter(convId);
        notifyDataSetChanged();
    }

    @Override
    public void notifyDataSetChanged() {
        ChatLine last = null, latest = null, latestChain = null;
        for (ChatLine line : chatLines) {
            if (line.isSelfChat()) {
                if (!lineShouldBeAppendedTo(last, line))
                    latestChain = line;
                latest = line;
            }
            last = line;
        }
        this.latestSelfMsg = latest;
        this.latestSelfChain = latestChain;
        super.notifyDataSetChanged();
    }
}


package com.atakmap.android.chat;

import android.view.View;
import android.view.ViewGroup;

/**
 * Provide for a plugin to modify the current visualization of the Chat Messages.
 */
public interface ChatMesssageRenderer {

    /**
     * An implementation that is provided that will take a chat message and provide for a visual
     * representation of that message.
     * @param position The position of the item within the adapter's data set of the item whose view
     *        we want.
     * @param convertView The old view to reuse, if possible. Note: You should check that this view
     *        is non-null and of an appropriate type before using. If it is not possible to convert
     *        this view to display the correct data, this method can create a new view.
     *        Heterogeneous lists can specify their number of view types, so that this View is}).
     * @param parent The parent that this view will eventually be attached to
     * @param chatLine the current chat line 
     * @return A view corresponding to the data at the specified position.
     */
    View getView(int position, View convertView, ViewGroup parent,
            ChatLine chatLine);

}

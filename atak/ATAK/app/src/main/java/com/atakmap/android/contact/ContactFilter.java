
package com.atakmap.android.contact;

import com.atakmap.android.chat.ChatManagerMapComponent;
import com.atakmap.android.chat.GeoChatService;
import com.atakmap.android.hierarchy.HierarchyListFilter;
import com.atakmap.android.hierarchy.HierarchyListItem;

import java.util.ArrayList;
import java.util.List;

/**
 * Filter for contacts based on type
 */

public class ContactFilter extends HierarchyListFilter {

    public enum FilterMode {
        INDIVIDUAL_CONTACTS_ONLY,
        UNIQUE_CONTACTS,
        ALL_CONTACTS,
        GROUPS,
        UID_WHITELIST,
        UID_BLACKLIST,
        INVALID
    }

    private FilterMode _mode;
    private final List<String> _customUIDs = new ArrayList<>();

    ContactFilter(FilterMode mode, HierarchyListItem.Sort sort) {
        super(sort);
        setMode(mode);
    }

    public void setMode(FilterMode mode) {
        _mode = mode;
    }

    void setCustomUIDs(List<String> uids) {
        if (uids != null) {
            _customUIDs.clear();
            _customUIDs.addAll(uids);
        }
    }

    @Override
    public boolean accept(HierarchyListItem item) {
        if (item instanceof Contact) {
            switch (_mode) {
                case INDIVIDUAL_CONTACTS_ONLY:
                    return item instanceof IndividualContact
                            && !((IndividualContact) item)
                                    .getExtras().getBoolean("fakeGroup", false);
                case GROUPS:
                    return item instanceof GroupContact;
                case UNIQUE_CONTACTS:
                    String uid = item.getUID();
                    return !uid
                            .equals(GeoChatService.DEFAULT_CHATROOM_NAME_LEGACY)
                            && !uid.equals(GeoChatService.DEFAULT_CHATROOM_NAME)
                            && !uid.equals(ChatManagerMapComponent
                                    .getRoleName());
                case UID_WHITELIST:
                    // In case child has a matching UID
                    return _customUIDs.contains(item.getUID())
                            || item.getChildCount() > 0;
                case UID_BLACKLIST:
                    return !_customUIDs.contains(item.getUID());
                default:
                case ALL_CONTACTS:
                    return true;
            }
        }
        return false;
    }
}

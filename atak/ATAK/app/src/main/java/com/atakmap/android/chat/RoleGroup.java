
package com.atakmap.android.chat;

import com.atakmap.android.contact.Contact;
import com.atakmap.android.contact.Contacts;
import com.atakmap.android.contact.GroupContact;
import com.atakmap.android.maps.MapView;

import java.util.ArrayList;
import com.atakmap.coremap.locale.LocaleUtil;

/* Per josh Role Groups should:
 *  Team leads talk to their team, other team leads, and HQ.
 *  HQ talks to other HQ and team leads 
 *  Team members talk to other team members and their team lead (instead we just don't generate a role group and they can use the Team Group (i.e Cyan)
 *
 */

public class RoleGroup extends GroupContact {

    private final String roleName;

    public RoleGroup(final String role) {
        super(role, role, new ArrayList<Contact>(), false);
        this.roleName = role;
        this.getExtras().putBoolean("fakeGroup", false);
        _iconUri = "asset://icons/roles/"
                + role.toLowerCase(LocaleUtil.getCurrent()).replace(" ", "")
                + ".png";
        setHideIfEmpty(true);
        ChatDatabase.getInstance(MapView.getMapView().getContext())
                .changeLocallyCreated(this.getUID(), false);
    }

    @Override
    protected void refreshImpl() {
        String myRole = ChatManagerMapComponent.getRoleName();
        setContacts(Contacts.fromUIDs(Contacts.getInstance()
                .getAllContactsWithRole(roleName)));
        setUnmodifiable(myRole == null || !myRole.equals(roleName));
        super.refreshImpl();
    }
}

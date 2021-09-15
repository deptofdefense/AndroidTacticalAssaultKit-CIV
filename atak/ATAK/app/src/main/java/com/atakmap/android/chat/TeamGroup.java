
package com.atakmap.android.chat;

import com.atakmap.android.contact.Contact;
import com.atakmap.android.contact.Contacts;
import com.atakmap.android.contact.GroupContact;
import com.atakmap.android.icons.Icon2525cIconAdapter;
import com.atakmap.android.maps.MapView;

import java.util.ArrayList;

public class TeamGroup extends GroupContact {

    private final String teamName;
    private final int teamColor;

    public TeamGroup(final String team) {
        super(team, team, new ArrayList<Contact>(), false);
        this.teamName = team;
        this.teamColor = Icon2525cIconAdapter.teamToColor(this.teamName);
        _iconUri = "asset://icons/roles/team.png";
        this.getExtras().putBoolean("fakeGroup", false);
        ChatDatabase.getInstance(MapView.getMapView().getContext())
                .changeLocallyCreated(this.getUID(), false);
    }

    @Override
    public int getIconColor() {
        return this.teamColor;
    }

    @Override
    protected void refreshImpl() {
        setContacts(Contacts.fromUIDs(Contacts.getInstance()
                .getAllContactsInTeam(this.teamName)));
        String userTeam = ChatManagerMapComponent.getTeamName();
        setUnmodifiable(!this.teamName.equals(userTeam));
        super.refreshImpl();
    }
}

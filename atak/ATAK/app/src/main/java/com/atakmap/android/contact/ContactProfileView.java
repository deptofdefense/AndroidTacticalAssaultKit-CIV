
package com.atakmap.android.contact;

import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import com.atakmap.android.cot.CotMapComponent;
import com.atakmap.android.cot.detail.TakVersionDetailHandler;
import com.atakmap.android.cotdetails.CoTInfoView;
import com.atakmap.android.math.MathUtils;
import com.atakmap.android.tools.menu.ActionBroadcastData;
import com.atakmap.android.util.ATAKUtilities;
import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.time.CoordinatedTime;

import java.util.List;

/**
 *
 */
public class ContactProfileView extends ContactDetailView {

    public static final String TAG = "ContactProfileView";

    private View _contactInfoTeamRoleLayout;
    private TextView _contactInfoTeamRole;
    private TextView _contactInfoAppVersion;
    private TextView _contactInfoNodeType;
    private TextView _contactInfoLastSeenText;
    private TextView _contactInfoBatteryLevelText;
    private TextView _contactInfoDefaultConnectorText;
    private ImageView _contactInfoDefaultConnectorIcon;
    private ImageView _contactInfoDefaultConnectorProfile;
    private ImageView _contactInfoAvatar;
    private View _contactInfoAvatarLayout;
    private View _contactInfoAppVersionLayout;
    private View _contactInfoNodeTypeLayout;
    private View _contactInfoBatteryLevelTextLayout;
    private View _contactInfoDefaultConnectorLayout;
    private View _contactInfoLastSeenTextLayout;
    private View _contactInfoAdditionalProfilesLayout;
    private ImageView _contactInfoAdditionalProfilesIcon;
    private ImageButton _contactInfoDefaultConnectorDetails;
    private Connector _defaultConnector;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {

        View v = inflater.inflate(R.layout.contact_detail_profile, container,
                false);

        _contactInfoTeamRoleLayout = v
                .findViewById(R.id.contactInfoTeamRoleLayout);
        _contactInfoTeamRole = v
                .findViewById(R.id.contactInfoTeamRole);

        _contactInfoAppVersionLayout = v
                .findViewById(R.id.contactInfoAppVersionLayout);
        _contactInfoAppVersion = v
                .findViewById(R.id.contactInfoAppVersion);

        _contactInfoNodeTypeLayout = v
                .findViewById(R.id.contactInfoNodeTypeLayout);
        _contactInfoNodeType = v
                .findViewById(R.id.contactInfoNodeType);

        _contactInfoLastSeenTextLayout = v
                .findViewById(R.id.contactInfoLastSeenTextLayout);
        _contactInfoLastSeenText = v
                .findViewById(R.id.contactInfoLastSeenText);

        _contactInfoBatteryLevelTextLayout = v
                .findViewById(R.id.contactInfoBatteryLevelTextLayout);
        _contactInfoBatteryLevelText = v
                .findViewById(R.id.contactInfoBatteryLevelText);

        _contactInfoDefaultConnectorLayout = v
                .findViewById(R.id.contactInfoDefaultConnectorLayout);
        _contactInfoDefaultConnectorText = v
                .findViewById(R.id.contactInfoDefaultConnectorText);
        _contactInfoDefaultConnectorIcon = v
                .findViewById(R.id.contactInfoDefaultConnectorIcon);
        _contactInfoDefaultConnectorProfile = v
                .findViewById(R.id.contactInfoDefaultConnectorProfile);
        _contactInfoDefaultConnectorDetails = v
                .findViewById(R.id.contactInfoDefaultConnectorDetails);
        _contactInfoDefaultConnectorDetails
                .setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (_contact != null && _defaultConnector != null)
                            ContactConnectorAdapter.showDetails(_prefs,
                                    _mapView, _contact, _defaultConnector,
                                    _marker);
                    }
                });

        _contactInfoAvatarLayout = v.findViewById(R.id.contactInfoAvatarLayout);
        _contactInfoAvatar = v
                .findViewById(R.id.contactInfoAvatarPic);

        //TODO make this a horizontal scroll list of available profiles?
        _contactInfoAdditionalProfilesLayout = v
                .findViewById(R.id.contactInfoAdditionalProfilesLayout);
        _contactInfoAdditionalProfilesIcon = v
                .findViewById(R.id.contactInfoAdditionalProfilesIcon);

        return v;
    }

    @Override
    public void onResume() {
        super.onResume();
        refresh(_parent.getSelectedItem(), _parent.getSelectedContact());
    }

    @Override
    protected void refresh() {
        if (_contactInfoTeamRoleLayout == null) {
            Log.w(TAG, "refresh not ready");
            return;
        }

        if (_marker != null) {
            boolean bSelf = ATAKUtilities.isSelf(_mapView, _marker);

            String s = _marker.getMetaString("team", "");
            if (bSelf) {
                s += " " + _prefs.getString("atakRoleType", "Team Member");
            } else {
                if (_marker.hasMetaValue("atakRoleType"))
                    s += " " + _marker.getMetaString("atakRoleType", "");
            }
            if (FileSystemUtils.isEmpty(s)
                    || FileSystemUtils.isEmpty(s.trim())) {
                _contactInfoTeamRoleLayout.setVisibility(View.GONE);
            } else {
                _contactInfoTeamRoleLayout.setVisibility(View.VISIBLE);
                _contactInfoTeamRole.setText(s);
            }

            if (bSelf) {
                s = TakVersionDetailHandler.getVersion();
            } else {
                s = TakVersionDetailHandler.getVersion(_marker);
            }
            if (FileSystemUtils.isEmpty(s)
                    || FileSystemUtils.isEmpty(s.trim())) {
                _contactInfoAppVersionLayout.setVisibility(View.GONE);
            } else {
                _contactInfoAppVersionLayout.setVisibility(View.VISIBLE);
                _contactInfoAppVersion.setText(s);
            }

            if (bSelf) {
                s = _mapView.getContext().getString(R.string.self);
            } else {
                s = CoTInfoView.getTypeLabel(_mapView.getContext(),
                        _marker.getType());
            }
            if (FileSystemUtils.isEmpty(s)
                    || FileSystemUtils.isEmpty(s.trim())) {
                _contactInfoNodeTypeLayout.setVisibility(View.GONE);
            } else {
                _contactInfoNodeTypeLayout.setVisibility(View.VISIBLE);
                _contactInfoNodeType.setText(s);
            }

            if (bSelf) {
                _contactInfoLastSeenTextLayout.setVisibility(View.GONE);
            } else {
                _contactInfoLastSeenTextLayout.setVisibility(View.VISIBLE);
                long lastSeenTime = _marker.getMetaLong("lastUpdateTime", 0);
                if (lastSeenTime > 0) {
                    long millisNow = new CoordinatedTime().getMilliseconds();
                    long millisAgo = millisNow - lastSeenTime;

                    _contactInfoLastSeenText.setText(_mapView.getContext()
                            .getString(
                                    R.string.last_report)
                            + MathUtils.GetTimeRemainingOrDateString(millisNow,
                                    millisAgo, true));
                } else {
                    _contactInfoLastSeenText
                            .setText(R.string.point_dropper_text16);
                }
            }

            if (bSelf) {
                s = "" + CotMapComponent.getInstance().getBatteryPercent();
            } else {
                s = null;
                long battery = _marker.getMetaLong("battery", -1);
                if (battery != -1) {
                    s = Long.toString(battery);
                }
            }
            if (!FileSystemUtils.isEmpty(s)) {
                //TODO display batter icon. maybe icons for others above too
                _contactInfoBatteryLevelTextLayout.setVisibility(View.VISIBLE);
                _contactInfoBatteryLevelText.setText(s + "%");
            } else {
                _contactInfoBatteryLevelTextLayout.setVisibility(View.GONE);
                _contactInfoBatteryLevelText.setText("");
            }

            if (_contact == null || bSelf) {
                _contactInfoDefaultConnectorLayout.setVisibility(View.GONE);
                _defaultConnector = null;
            } else {
                _defaultConnector = _contact.getDefaultConnector(_prefs);
                if (_defaultConnector == null) {
                    _contactInfoDefaultConnectorLayout.setVisibility(View.GONE);
                } else {
                    _contactInfoDefaultConnectorLayout
                            .setVisibility(View.VISIBLE);
                    _contactInfoDefaultConnectorText.setText(_defaultConnector
                            .getConnectionLabel());

                    String iconUri = _defaultConnector.getIconUri();
                    if (FileSystemUtils.isEmpty(iconUri)) {
                        //TODO display default icon instead?
                        _contactInfoDefaultConnectorIcon
                                .setVisibility(View.GONE);
                    } else {
                        _contactInfoDefaultConnectorIcon
                                .setVisibility(View.VISIBLE);
                        _contactInfoDefaultConnectorIcon.setFocusable(false);
                        ATAKUtilities.SetIcon(_mapView.getContext(),
                                _contactInfoDefaultConnectorIcon, iconUri,
                                Color.WHITE);
                        ContactListAdapter.updateConnectorView(_contact,
                                _defaultConnector,
                                _contactInfoDefaultConnectorIcon,
                                _mapView.getContext());
                    }

                    Object profile = CotMapComponent
                            .getInstance()
                            .getContactConnectorMgr()
                            .getFeature(
                                    _contact,
                                    _defaultConnector,
                                    ContactConnectorManager.ConnectorFeature.Profile);
                    if (profile instanceof ActionBroadcastData) {
                        final ActionBroadcastData intent = (ActionBroadcastData) profile;

                        _contactInfoDefaultConnectorProfile
                                .setVisibility(View.VISIBLE);
                        _contactInfoDefaultConnectorProfile.setFocusable(false);
                        _contactInfoDefaultConnectorProfile
                                .setOnClickListener(new View.OnClickListener() {
                                    @Override
                                    public void onClick(View v) {
                                        Log.d(TAG, "Profile clicked for: "
                                                + _contact);
                                        ActionBroadcastData.broadcast(intent);
                                    }
                                });
                    } else {
                        _contactInfoDefaultConnectorProfile
                                .setVisibility(View.GONE);
                    }

                    //TODO add "more" button to switch over to connectors tab?
                    _contactInfoDefaultConnectorLayout
                            .setOnClickListener(new View.OnClickListener() {
                                @Override
                                public void onClick(View v) {
                                    if (!CotMapComponent
                                            .getInstance()
                                            .getContactConnectorMgr()
                                            .initiateContact(_contact,
                                                    _defaultConnector)) {
                                        Log.w(TAG,
                                                "No connector handler available for: "
                                                        + _contact);
                                        //TODO notify user?
                                    }
                                }
                            });
                }
            }

            final AvatarFeature avatar = _contact == null ? null
                    : _contact
                            .getDefaultAvatar();
            if (avatar == null) {
                _contactInfoAvatarLayout.setVisibility(View.GONE);
                _contactInfoAvatar.setOnClickListener(null);
            } else {
                Log.d(TAG, "Found avatar for: " + _contact.toString());
                _contactInfoAvatarLayout.setVisibility(View.VISIBLE);
                _contactInfoAvatar.setImageBitmap(avatar.getAvatar());
                _contactInfoAvatar
                        .setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                AvatarFeature.openAvatar(avatar, _marker, null);
                            }
                        });
            }

            _contactInfoAdditionalProfilesLayout.setVisibility(View.GONE);
            List<Object> profiles = CotMapComponent
                    .getInstance()
                    .getContactConnectorMgr()
                    .getFeatures(_contact,
                            ContactConnectorManager.ConnectorFeature.Profile,
                            Integer.MAX_VALUE);
            if (!FileSystemUtils.isEmpty(profiles)) {
                for (Object profile : profiles) {
                    Log.d(TAG, profile.toString());
                }

                //TODO horizontal scroll list for extended profiles, when tapping on that icon, send intent to display profile
                //TODO for now, just take first
                Object obj = profiles.get(0);
                if (obj instanceof ActionBroadcastData) {
                    final ActionBroadcastData intent = (ActionBroadcastData) obj;

                    //TODO hack to return icon, should probably instead better assoicate the
                    //connector that this profile was provided by
                    String iconUri = intent.getExtra("iconUri");
                    if (FileSystemUtils.isEmpty(iconUri)) {
                        //TODO display default icon instead?
                        _contactInfoAdditionalProfilesLayout
                                .setVisibility(View.GONE);
                    } else {
                        _contactInfoAdditionalProfilesLayout
                                .setVisibility(View.VISIBLE);
                        //_contactInfoAdditionalProfilesIcon.setFocusable(false);
                        ATAKUtilities.SetIcon(_mapView.getContext(),
                                _contactInfoAdditionalProfilesIcon, iconUri,
                                Color.WHITE);
                    }

                    _contactInfoAdditionalProfilesIcon
                            .setOnClickListener(new View.OnClickListener() {
                                @Override
                                public void onClick(View v) {
                                    Log.d(TAG,
                                            "Additional profile clicked for: "
                                                    + _contact);
                                    ActionBroadcastData.broadcast(intent);
                                }
                            });
                }

            }
        } else {
            cleanup();
        }
    }

    @Override
    protected void cleanup() {
        _defaultConnector = null;
        //TODO anything? release drawable/avatar?
    }
}

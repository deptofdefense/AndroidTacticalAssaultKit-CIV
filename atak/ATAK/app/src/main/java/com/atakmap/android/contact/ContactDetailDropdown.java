
package com.atakmap.android.contact;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;
import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.atakmap.android.cot.CotMapComponent;
import com.atakmap.android.cotdetails.CoTInfoBroadcastReceiver;
import com.atakmap.android.dropdown.DropDown.OnStateListener;
import com.atakmap.android.dropdown.DropDownManager;
import com.atakmap.android.dropdown.DropDownReceiver;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.MapEvent;
import com.atakmap.android.maps.MapEventDispatcher;
import com.atakmap.android.maps.MapEventDispatcher.MapEventDispatchListener;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Marker;
import com.atakmap.android.maps.PointMapItem;
import com.atakmap.android.util.ATAKUtilities;
import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.map.CameraController;

import java.util.ArrayList;
import java.util.List;

public class ContactDetailDropdown extends DropDownReceiver implements
        MapEventDispatchListener, OnStateListener,
        PointMapItem.OnPointChangedListener,
        Marker.OnTrackChangedListener {

    private static final String TAG = "ContactDetailDropdown";
    public static final String CONTACT_DETAILS = "com.atakmap.android.contact.CONTACT_DETAILS";

    private final SharedPreferences _prefs;

    private View _detailView;
    private ViewPager _contactDetailPager;
    private ContactDetailViewAdapter _contactDetailAdapter;
    private int _pageSelected;

    private ContactProfileView _profileView;
    private ContactLocationView _locationView;
    private ContactConnectorsView _connectorsView;
    private ImageButton _contactInfo_detailSelfEdit;
    private ImageButton _contactInfo_detailPanBtn;
    private View _contactInfo_detailLeftDot;
    private View _contactInfo_detailMiddleDot;
    private View _contactInfo_detailRightDot;
    private RelativeLayout _contactInfo_headerLayout;

    private PointMapItem selectedItem;
    private IndividualContact selectedContact;
    private ImageView _contactInfoIcon;
    private TextView _contactInfoName;

    public ContactDetailDropdown(final MapView mapView) {
        super(mapView);
        _prefs = PreferenceManager.getDefaultSharedPreferences(mapView
                .getContext());

        mapView.getSelfMarker().addOnPointChangedListener(this);
        final MapEventDispatcher dispatcher = getMapView()
                .getMapEventDispatcher();
        dispatcher.addMapEventListener(MapEvent.ITEM_CLICK, this);

        Contacts.getInstance().addListener(_contactChangedListener);
    }

    @Override
    public void disposeImpl() {
        final MapEventDispatcher dispatcher = getMapView()
                .getMapEventDispatcher();
        dispatcher.removeMapEventListener(MapEvent.ITEM_CLICK, this);

        Contacts.getInstance().removeListener(_contactChangedListener);

        if (_contactDetailAdapter != null) {
            _contactDetailAdapter = null;
        }

        _contactDetailPager = null;
    }

    @Override
    public void onPointChanged(final PointMapItem item) {
        if (isVisible() && _locationView != null && item.getPoint().isValid()) {
            if (item != _locationView.getMarker()) {
                item.removeOnPointChangedListener(this);
            } else {
                _locationView.onPointChanged();
            }
        }
    }

    @Override
    public void onTrackChanged(Marker marker) {
        if (isVisible() && _locationView != null) {
            if (marker != _locationView.getMarker()) {
                marker.removeOnTrackChangedListener(this);
            } else {
                _locationView.onTrackChanged(marker);
            }
        }
    }

    @Override
    public void onMapEvent(MapEvent event) {
        if (event.getType().equals(MapEvent.ITEM_REMOVED))
            Log.d(TAG, "calling remove"
                    + event.getItem().getUID() + " " + isVisible());

        if (!isVisible())
            return;

        if (event.getType().equals(MapEvent.ITEM_CLICK)) {
            if (event.getItem() == null) {
                return;
            }

            if (event.getItem() instanceof PointMapItem) {
                PointMapItem item = (PointMapItem) event.getItem();
                if (ContactUtil.isTakContact(item))
                    refresh(item);
                else {
                    refresh(null);
                    closeDropDown();
                }
            }
        }
    }

    private void refresh(PointMapItem item) {
        if (selectedItem != item) {
            selectedItem = item;
            setSelected(selectedItem, "asset:/icons/outline.png");
        }

        if (selectedItem != null) {
            Contact contact;
            if (ATAKUtilities.isSelf(getMapView(), selectedItem)) {
                contact = CotMapComponent.getInstance().getSelfContact(true);
            } else {
                contact = Contacts.getInstance().getContactByUuid(
                        selectedItem.getUID());
            }

            if (ATAKUtilities.isSelf(getMapView(), selectedItem)) {
                _contactInfo_detailSelfEdit.setVisibility(View.VISIBLE);
            } else {
                _contactInfo_detailSelfEdit.setVisibility(View.GONE);
            }

            if (contact instanceof IndividualContact) {
                selectedContact = (IndividualContact) contact;
            }

            ATAKUtilities.SetIcon(getMapView().getContext(),
                    this._contactInfoIcon, selectedItem);
            this._contactInfoName.setText(ATAKUtilities
                    .getDisplayName(selectedItem));
        } else {
            this.selectedContact = null;
            this._contactInfoIcon.setVisibility(View.INVISIBLE);
            this._contactInfoName.setText("");
            this._contactInfo_detailSelfEdit.setVisibility(View.GONE);
        }

        if (_profileView != null)
            _profileView.refresh(selectedItem, selectedContact);
        if (_locationView != null)
            _locationView.refresh(selectedItem, selectedContact);
        if (_connectorsView != null)
            _connectorsView.refresh(selectedItem, selectedContact);
    }

    PointMapItem getSelectedItem() {
        return selectedItem;
    }

    IndividualContact getSelectedContact() {
        return selectedContact;
    }

    @Override
    public void onReceive(final Context context, Intent intent) {

        if (intent.getAction().equals(CoTInfoBroadcastReceiver.COTINFO_DETAILS)
                ||
                intent.getAction().equals(CONTACT_DETAILS)) {

            PointMapItem temp = findTarget(intent.getStringExtra("targetUID"));
            if (temp == null
                    || (!ContactUtil.isTakContact(temp) && !ATAKUtilities
                            .isSelf(getMapView(),
                                    temp))) {
                //allow CotInfoBroadcastReceiver to handle
                Log.d(TAG, "!isContact: " + intent.getStringExtra("targetUID"));
                return;
            }

            if (_detailView == null)
                getDetailView();

            if (DropDownManager.getInstance().isTopDropDown(this)) {
                if (!isVisible())
                    DropDownManager.getInstance().unHidePane();
                refresh(temp);
            } else {
                refresh(temp);
                setRetain(true);
                showDropDown(_detailView, THREE_EIGHTHS_WIDTH, FULL_HEIGHT,
                        FULL_WIDTH,
                        HALF_HEIGHT, this);
            }

            setTab(intent.getStringExtra("tab"));
        }
    }

    private void setTab(String tab) {
        if (FileSystemUtils.isEmpty(tab)) {
            return;
        }

        Log.d(TAG, "setTab: " + tab);
        switch (tab) {
            case ContactProfileView.TAG:
                _pageSelected = 0;
                break;
            case ContactLocationView.TAG:
                _pageSelected = 1;
                break;
            case ContactConnectorsView.TAG:
                _pageSelected = 2;
                break;
            default:
                Log.w(TAG, "setTab invalid: " + tab);
                _pageSelected = 0;
                break;
        }

        if (_contactDetailPager != null)
            _contactDetailPager.setCurrentItem(_pageSelected);
    }

    private void getDetailView() {
        if (this._detailView == null) {
            //set up swipe-able fragments
            _profileView = new ContactProfileView();
            _profileView.init(getMapView(), _prefs, this);

            _locationView = new ContactLocationView();
            _locationView.init(getMapView(), _prefs, this);

            _connectorsView = new ContactConnectorsView();
            _connectorsView.init(getMapView(), _prefs, this);

            //set up pager container (main view)
            LayoutInflater inflater = LayoutInflater
                    .from(getMapView().getContext());

            _detailView = inflater.inflate(R.layout.contact_detail, null);

            _contactDetailAdapter = new ContactDetailViewAdapter(
                    ((FragmentActivity) getMapView().getContext())
                            .getSupportFragmentManager());
            _contactDetailPager = _detailView
                    .findViewById(R.id.contactInfo_detailPager);
            _contactDetailPager.setAdapter(_contactDetailAdapter);
            _contactDetailPager.setOnPageChangeListener(_viewChangeListener);

            List<Fragment> fragments = new ArrayList<>();
            fragments.add(_profileView);
            fragments.add(_locationView);
            fragments.add(_connectorsView);
            _contactDetailAdapter.setFragments(fragments);

            _contactInfoIcon = _detailView
                    .findViewById(R.id.contactInfoIcon);
            _contactInfoName = _detailView
                    .findViewById(R.id.contactInfoName);
            _contactInfo_detailLeftDot = _detailView
                    .findViewById(R.id.contactInfo_detailLeftDot);
            _contactInfo_detailLeftDot.setSelected(true);
            _pageSelected = 0;
            _contactInfo_detailMiddleDot = _detailView
                    .findViewById(R.id.contactInfo_detailMiddleDot);
            _contactInfo_detailMiddleDot.setSelected(false);
            _contactInfo_detailRightDot = _detailView
                    .findViewById(R.id.contactInfo_detailRightDot);
            _contactInfo_detailRightDot.setSelected(false);

            _contactInfo_headerLayout = _detailView
                    .findViewById(R.id.contactInfo_headerLayout);
            _contactInfo_headerLayout
                    .setOnTouchListener(new View.OnTouchListener() {
                        @Override
                        public boolean onTouch(View v, MotionEvent event) {
                            //flip to next page
                            _pageSelected = ++_pageSelected % 3;
                            _contactDetailPager.setCurrentItem(_pageSelected);
                            return false;
                        }
                    });

            _contactInfo_detailSelfEdit = _detailView
                    .findViewById(R.id.contactInfo_detailSelfEdit);
            _contactInfo_detailSelfEdit
                    .setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            if (!ATAKUtilities.isSelf(getMapView(),
                                    selectedItem))
                                return;

                            //TODO refresh when settings screen is closed? mabye check dropDown.onVisible()
                            AtakBroadcast.getInstance().sendBroadcast(
                                    new Intent(
                                            "com.atakmap.app.DEVICE_SETTINGS"));
                        }
                    });

            _contactInfo_detailPanBtn = _detailView
                    .findViewById(R.id.contactInfo_detailPanBtn);
            _contactInfo_detailPanBtn
                    .setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            if (selectedItem != null
                                    && selectedItem.getPoint() != null) {
                                final GeoPoint gp = selectedItem.getPoint();
                                CameraController.Programmatic.panTo(
                                        getMapView().getRenderer3(), gp, false);
                            }
                        }
                    });
        }
    }

    private final Contacts.OnContactsChangedListener _contactChangedListener = new Contacts.OnContactsChangedListener() {
        @Override
        public void onContactsSizeChange(Contacts contacts) {
        }

        @Override
        public void onContactChanged(String uuid) {
            if (uuid != null && selectedItem != null && isVisible()
                    && FileSystemUtils.isEquals(selectedItem.getUID(), uuid)) {
                getMapView().post(new Runnable() {
                    @Override
                    public void run() {
                        refresh(selectedItem);
                    }
                });
            }
        }
    };

    @Override
    public void onDropDownSelectionRemoved() {
        getMapView().post(new Runnable() {
            @Override
            public void run() {
                refresh(null);
            }
        });
    }

    @Override
    public void onDropDownVisible(boolean v) {
        if (v && _profileView != null) {
            _profileView.updateVisual();
        }
        if (v && _locationView != null) {
            _locationView.updateVisual();
        }
        if (v && _connectorsView != null) {
            _connectorsView.updateVisual();
        }

    }

    @Override
    public void onDropDownSizeChanged(double width, double height) {
    }

    @Override
    public void onDropDownClose() {
        refresh(null);
    }

    private PointMapItem findTarget(final String targetUID) {
        PointMapItem pointItem = null;
        if (targetUID != null) {
            MapGroup rootGroup = getMapView().getRootGroup();
            MapItem item = rootGroup.deepFindUID(targetUID);
            if (item instanceof PointMapItem) {
                pointItem = (PointMapItem) item;
            }
        }
        return pointItem;
    }

    private final ViewPager.OnPageChangeListener _viewChangeListener = new ViewPager.SimpleOnPageChangeListener() {

        @Override
        public void onPageSelected(int position) {
            if (position > 2) {
                Log.w(TAG, "Unable to select position: " + position);
                return;
            }

            _pageSelected = position;

            _contactInfo_detailLeftDot.setSelected(position == 0);
            _contactInfo_detailMiddleDot.setSelected(position == 1);
            _contactInfo_detailRightDot.setSelected(position == 2);
        }
    };

    static class ContactDetailViewAdapter extends FragmentPagerAdapter {
        private static final String TAG = "ContactDetailViewAdapter";
        List<Fragment> fragments;

        public ContactDetailViewAdapter(FragmentManager fm) {
            super(fm);
        }

        synchronized void setFragments(List<Fragment> p) {
            fragments = p;
            notifyDataSetChanged();
        }

        @Override
        public int getCount() {
            return 3;
        }

        @Override
        public Fragment getItem(int position) {
            if (FileSystemUtils.isEmpty(fragments)
                    || position > fragments.size())
                return null;

            return fragments.get(position);
        }

        @Override
        public int getItemPosition(Object object) {
            //return position none so pager will create (re)added fragments
            return PagerAdapter.POSITION_NONE;
        }
    }
}

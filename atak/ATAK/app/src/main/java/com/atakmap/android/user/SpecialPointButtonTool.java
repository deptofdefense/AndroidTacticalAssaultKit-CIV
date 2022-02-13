
package com.atakmap.android.user;

import android.content.Context;
import android.content.Intent;
import android.graphics.PointF;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;

import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.MapEvent;
import com.atakmap.android.maps.MapEventDispatcher;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Marker;
import com.atakmap.android.maps.PointMapItem;
import com.atakmap.android.navigation.NavButtonManager;
import com.atakmap.android.navigation.models.NavButtonModel;
import com.atakmap.android.navigation.views.NavView;
import com.atakmap.android.toolbar.Tool;
import com.atakmap.android.tools.menu.ActionMenuData;
import com.atakmap.annotations.DeprecatedApi;
import com.atakmap.app.R;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * This class is the base for the buttons that have more than an on and off state.  Examples
 * of these buttons are the Red X (SelectPoint) and the SPI points.  These buttons currently
 * have three states off (marker not visible), enabled (where the tool is on), and disabled (where
 * the tool is off, but the point still visible).  This should eliminate most of the confusion
 * and weird states previously associated with the Red X and SPI.
 *
 *
 */
public abstract class SpecialPointButtonTool extends Tool {

    private static final String TAG = "SpecialPointButtonTool";

    public enum States {
        OFF,
        DISABLED,
        ENABLED
    }

    private final Context _context;
    private States _currentState = States.OFF;

    protected Marker _marker;
    int _iconEnabled, _iconDisabled, _iconOff;
    private List<ImageButton> _buttons;

    protected SpecialPointButtonTool(final MapView mapView,
            final ImageButton button, final String identifier) {
        super(mapView, identifier);
        _context = mapView.getContext();
        if (_buttons == null)
            _buttons = new ArrayList<>();
        if (button != null)
            _buttons.add(button);
        for (ImageButton b : _buttons) {
            b.setOnLongClickListener(_buttonLongClickListener);
            b.setOnClickListener(_buttonClickListener);
        }
    }

    @Override
    public boolean onToolBegin(Bundle bundle) {
        _mapView.getMapEventDispatcher().pushListeners();

        _mapView.getMapEventDispatcher().clearListeners(
                MapEvent.MAP_CLICK);

        _mapView.getMapEventDispatcher().clearListeners(
                MapEvent.ITEM_CLICK);

        _mapView.getMapEventDispatcher().addMapEventListener(
                MapEvent.MAP_CLICK, _mapClickListener);
        _mapView.getMapEventDispatcher().addMapEventListener(
                MapEvent.ITEM_CLICK, _mapClickListener);

        setCurrentState(States.ENABLED);

        if (_marker != null)
            _marker.setMovable(true);

        setIcons();
        return super.onToolBegin(bundle);
    }

    @Override
    public void onToolEnd() {
        _mapView.getMapEventDispatcher().popListeners();
        //_mapView.getMapEventDispatcher().removeMapEventListener(
        //        MapEvent.MAP_CLICK, _mapClickListener);
        switch (_currentState) {
            case OFF:
                //Shouldn't happen.  Do nothing
                break;
            case DISABLED:
            case ENABLED:
                setCurrentState(States.DISABLED);
                if (_marker != null)
                    _marker.setMovable(false);
                setIcons();
                break;
        }
        if (_marker != null) {
            Intent intent = new Intent(
                    "com.atakmap.android.action.HIDE_POINT_DETAILS");
            intent.putExtra("uid", _marker.getUID());
            AtakBroadcast.getInstance().sendBroadcast(intent);
        }
        super.onToolEnd();
    }

    @Override
    public void dispose() {
        if (_buttons != null) {
            for (ImageButton b : _buttons) {
                b.setOnClickListener(null);
                b.setOnLongClickListener(null);
            }
            _buttons.clear();
        }
        _buttons = null;
        _buttonClickListener = null;
        _buttonLongClickListener = null;
    }

    public Marker getMarker() {
        return _marker;
    }

    protected void addButton(ImageButton button) {
        button.setOnLongClickListener(_buttonLongClickListener);
        button.setOnClickListener(_buttonClickListener);
        _buttons.add(button);
    }

    /**
     * Set the ActionMenuData object for this tool.  Only one instance per tool.  If the
     * ActionMenuData item is already set, it will not be overwritten.
     * @param amd  ActionMenuData item to set.
     * @return  Result of setting.  True indicates success and the ActionMenuData item has
     *          been set.  False indicates that the item was already set and has not been
     *          saved.
     * @deprecated No longer used - see {@link NavView}
     */
    @Deprecated
    @DeprecatedApi(since = "4.5", forRemoval = true, removeAt = "4.8")
    public boolean setActionMenuData(ActionMenuData amd) {
        return true;
    }

    /**
     * Get the {@link NavButtonModel} reference for this tool
     * @return Nav button XML reference or null if N/A
     */
    protected String getNavReference() {
        return null;
    }

    protected View.OnLongClickListener _buttonLongClickListener = new View.OnLongClickListener() {
        @Override
        public boolean onLongClick(View v) {
            exectuteLongClick();
            return true;
        }
    };

    public States getState() {
        return _currentState;
    }

    public States exectuteLongClick() {
        States ret = States.OFF;
        switch (_currentState) {
            case OFF:
                ret = States.ENABLED;
                requestBeginTool();
                break;
            case DISABLED:
            case ENABLED:
                setCurrentState(States.OFF);
                setIcons();
                if (_marker != null)
                    removeMarker();
                //_marker.setVisible(false);
                requestEndTool();
                break;
        }
        return ret;
    }

    /**
     * Set the icons for the various places the tool is used.  This includes locations inside
     * of various sub menus, and the Action Bar (if presently set).  The _currentState must
     * be set prior to calling this method for it to operate correctly.
     */
    private void setIcons() {
        int icon = _iconOff;
        boolean selected = false;
        switch (_currentState) {
            case DISABLED:
                icon = _iconDisabled;
                selected = true;
                break;
            case ENABLED:
                icon = _iconEnabled;
                selected = true;
                break;
            default:
                //do nothing - off state already established
        }
        if (_buttons != null) {
            for (ImageButton b : _buttons)
                b.setImageResource(icon);
        }
        String ref = getNavReference();
        if (ref != null) {
            NavButtonModel mdl = NavButtonManager.getInstance()
                    .getModelByReference(ref);
            mdl.setSelected(selected);
            mdl.setSelectedImage(_context.getDrawable(icon));
            NavButtonManager.getInstance().notifyModelChanged(mdl);
        }
    }

    public States off() {
        setCurrentState(States.OFF);
        _mapView.post(new Runnable() {
            @Override
            public void run() {
                setIcons();
            }
        });
        if (_marker != null)
            removeMarker();
        requestEndTool();
        return _currentState;
    }

    protected View.OnClickListener _buttonClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            executeClick();
        }
    };

    public States executeClick() {

        States ret = States.OFF;
        switch (_currentState) {
            case OFF:
            case DISABLED:
                setCurrentState(States.ENABLED);
                ret = States.ENABLED;
                requestBeginTool();
                break;
            case ENABLED:
                setCurrentState(States.DISABLED);
                ret = States.DISABLED;
                requestEndTool();
                if (_marker != null) {
                    Intent intent = new Intent();
                    intent.setAction("com.atakmap.android.maps.HIDE_MENU");
                    intent.putExtra("uid", _marker.getUID());
                    AtakBroadcast.getInstance().sendBroadcast(intent);
                }
                break;
        }
        return ret;
    }

    /**
     * creates a marker that is shared across all of the special points.
     * uses an assetUri in the format icon/xxx.png and not asset://icon/xxx.png
     */
    protected void createMarker(final String assetURL,
            final String uid,
            final String callsign,
            final String menu,
            final String deleteAction) {
        if (_marker == null) {
            _marker = new Marker(uid);
            _marker.setType("b-m-p-s-p-i");
            _marker.setMetaString("how", "h-e");
            _marker.setZOrder(-2000d);

            _marker.setMetaString("iconUri", assetURL);

            // comment out for now until it can be decided what does it 
            // mean to bloodhound yourself 
            _marker.setMetaString("parent_type",
                    _mapView.getMapData().getString("deviceType"));
            _marker.setMetaString("parent_uid",
                    _mapView.getSelfMarker().getUID());

            _marker.setMetaString("label", callsign);
            _marker.setMetaString("callsign", callsign);
            _marker.setTitle(callsign);
            _marker.setShowLabel(false);
            _marker.setMetaBoolean("ignoreFocus", false);
            _marker.setMetaBoolean("toggleDetails", true);
            _marker.setMetaBoolean("ignoreMenu", false);
            _marker.setMetaString("entry", "user");
            _marker.setMetaBoolean("ignoreOffscreen", false);
            _marker.setMetaBoolean("addToObjList", true);
            _marker.setMovable(true);
            _marker.setMetaString("menu", menu);
            _marker.setMetaString("deleteAction", deleteAction);
            _marker.setMetaBoolean("removable", false);
            _marker.setMetaDouble("minRenderScale", Double.MAX_VALUE);
            _marker.setMetaBoolean("extendedElevationDisplay", true);

        }

        if (_marker.hasMetaValue("previouslyDisplayed")) {
            Intent intent = new Intent(
                    "com.atakmap.android.action.SHOW_POINT_DETAILS");
            intent.putExtra("uid", uid);
            AtakBroadcast.getInstance().sendBroadcast(intent);
            addMarker();
        }
        //_marker.setVisible(true);
    }

    /**
     * The marker is now added and removed from the map group to toggle visibility.  This
     * makes it play much more nicely with other tools.  AS.
     */
    synchronized private void addMarker() {
        final MapGroup mg = _mapView.getRootGroup().findMapGroup("SPIs");
        if (mg != null) {
            _marker.setVisible(true); // the SPI subclass makes use of visibility changed
            _marker.setMetaBoolean("addToObjList", true);
            if (!mg.containsItem(_marker))
                mg.addItem(_marker);
        } else {
            Log.d(TAG, "cannot find the SPIs group");
        }
    }

    protected synchronized void removeMarker() {
        final MapGroup mg = _mapView.getRootGroup().findMapGroup("SPIs");
        if (mg != null) {
            _marker.setVisible(false); // the SPI subclass makes use of visibility changed
            _marker.setMetaBoolean("addToObjList", false);
            Intent intent = new Intent();
            intent.setAction("com.atakmap.android.maps.HIDE_MENU");
            intent.putExtra("uid", _marker.getUID());
            AtakBroadcast.getInstance().sendBroadcast(intent);
            //mg.removeItem(_marker);
        } else {
            Log.d(TAG, "cannot find the SPIs group");
        }
    }

    private final MapEventDispatcher.MapEventDispatchListener _mapClickListener = new MapEventDispatcher.MapEventDispatchListener() {

        @Override
        public void onMapEvent(MapEvent event) {

            if (event.getType().equals(MapEvent.ITEM_CLICK)) {
                if (event.getItem() == _marker) {

                    Intent localMenu = new Intent();
                    Intent localDetails = new Intent();
                    Intent zoomIntent = new Intent();
                    localMenu.setAction("com.atakmap.android.maps.SHOW_MENU");
                    localMenu.putExtra("uid", _marker.getUID());

                    localDetails
                            .setAction("com.atakmap.android.maps.SHOW_DETAILS");
                    localDetails.putExtra("uid", _marker.getUID());

                    zoomIntent.setAction("com.atakmap.android.maps.FOCUS");
                    zoomIntent.putExtra("uid", _marker.getUID());
                    zoomIntent.putExtra("useTightZoom", true);

                    ArrayList<Intent> intents = new ArrayList<>(3);
                    intents.add(zoomIntent);
                    intents.add(localMenu);
                    intents.add(localDetails);

                    AtakBroadcast.getInstance().sendIntents(intents);

                    return;
                }
            }

            PointF pt = event.getPointF();

            final GeoPointMetaData gp;

            // for item_click
            if (event.getItem() instanceof PointMapItem) {
                gp = ((PointMapItem) event.getItem()).getGeoPointMetaData();
            } else if (pt != null) {
                gp = _mapView.inverseWithElevation(pt.x, pt.y);
            } else {
                final MapItem item = event.getItem();
                if (item.getMetaString("last_touch", null) != null) {
                    try {
                        gp = GeoPointMetaData.wrap(GeoPoint
                                .parseGeoPoint(item.getMetaString("menu_point",
                                        null)));
                    } catch (Exception e) {
                        return;
                    }
                } else
                    return;
            }

            _marker.setPoint(gp);
            _marker.setVisible(true);
            _marker.setMetaBoolean("previouslyDisplayed", true);

            Intent intent = new Intent(
                    "com.atakmap.android.action.SHOW_POINT_DETAILS");
            intent.putExtra("uid", _marker.getUID());
            AtakBroadcast.getInstance().sendBroadcast(intent);
            addMarker();

        }
    };

    @Override
    public boolean shouldEndOnBack() {
        return false;
    }

    protected int getResourceId(String name) {
        try {
            Class<?> res = R.drawable.class;
            Field field = res.getField(name);
            int drawableId = field.getInt(null);
            return drawableId;
        } catch (Exception e) {
            Log.e(TAG, "Failure to get drawable id.", e);
        }
        return -1;
    }

    protected void setCurrentState(States s) {
        _currentState = s;
    }
}

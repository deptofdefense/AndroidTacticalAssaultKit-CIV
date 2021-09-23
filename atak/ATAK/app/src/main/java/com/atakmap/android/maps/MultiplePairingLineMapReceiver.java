
package com.atakmap.android.maps;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;

import com.atakmap.android.maps.MapGroup.MapItemsCallback;
import com.atakmap.android.widgets.LayoutWidget;
import com.atakmap.android.widgets.TextWidget;
import com.atakmap.coremap.conversions.ConversionFactors;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;

import java.text.DecimalFormat;

import com.atakmap.coremap.locale.LocaleUtil;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class MultiplePairingLineMapReceiver {

    protected static final String TAG = "MultiplePairingLineMapReceiver";

    private MultiplePairingLineMapReceiver() {
    }

    synchronized public static MultiplePairingLineMapReceiver getInstance() {

        if (_instance == null) {
            _instance = new MultiplePairingLineMapReceiver();
        }
        return _instance;
    }

    public void initialize(MapView mapView, MapGroup linkGroup,
            LayoutWidget textContainer) {
        _textContainer = textContainer;
        _mapView = mapView;
        _linkGroup = linkGroup;
        _mapView.getMapEventDispatcher().addMapEventListener(
                MapEvent.ITEM_CLICK,
                _itemClickedListener);

    }

    public synchronized void pair(Context context, Intent intent) {

        boolean doAdd = false;

        Bundle extras = intent.getExtras();

        String uid = extras.getString("startingUID");
        final boolean clampToGround = extras.getBoolean("clampToGround");

        if (extras.containsKey("color")) {
            lineColor = extras.getInt("color", Color.WHITE);
        }

        int givenStyle = extras.getInt("style", Association.STYLE_OUTLINED);
        if (givenStyle == Association.STYLE_DASHED ||
                givenStyle == Association.STYLE_DOTTED ||
                givenStyle == Association.STYLE_SOLID)
            lineStyle = givenStyle;
        else
            lineStyle = Association.STYLE_OUTLINED;

        // XXX link an array of markers
        if (extras.containsKey("uidArray")) {
            final String dipUID = extras.getString("dipUID", null);
            String[] uids = extras.getStringArray("uidArray");
            String[] titles = extras.getStringArray("titleArray");
            for (int i = 0; i < uids.length - 1; i++) {
                try {
                    MapItem item = _mapView.getMapItem(uids[i]);
                    final PointMapItem pmi1 = (PointMapItem) item;
                    item = _mapView.getMapItem(uids[i + 1]);
                    final PointMapItem pmi2 = (PointMapItem) item;
                    if (pmi1 != null && pmi2 != null) {

                        String text = null;
                        if (titles != null && titles.length > i)
                            text = titles[i];
                        final String lineText = text;
                        final int color = lineColor;
                        final int style = lineStyle;
                        _addLink(pmi1, pmi2, dipUID, text, color, lineStyle,
                                clampToGround);
                        pmi1.addOnVisibleChangedListener(
                                new MapItem.OnVisibleChangedListener() {

                                    @Override
                                    public void onVisibleChanged(MapItem item) {
                                        if (pmi2 == null) {
                                            Log.d(TAG,
                                                    "bad pmi2 no longer valid",
                                                    new Exception());
                                        } else if (!pmi1.getVisible()
                                                || !pmi2.getVisible())
                                            _removeLink(pmi1.getUID(),
                                                    pmi2.getUID());
                                        else if (pmi1.getVisible()
                                                && pmi2.getVisible()) {
                                            if (pmi1.getGroup() != null
                                                    && pmi2.getGroup() != null)
                                                _addLink(pmi1, pmi2, dipUID,
                                                        lineText,
                                                        color, style,
                                                        clampToGround);
                                            else {
                                                if (pmi1.getGroup() == null) {
                                                    pmi1.removeOnVisibleChangedListener(
                                                            this);
                                                }
                                            }
                                        }
                                    }
                                });
                        pmi2.addOnVisibleChangedListener(
                                new MapItem.OnVisibleChangedListener() {

                                    @Override
                                    public void onVisibleChanged(MapItem item) {
                                        if (pmi1 == null) {
                                            Log.d(TAG,
                                                    "bad pmi1 no longer valid",
                                                    new Exception());
                                        } else if (!pmi1.getVisible()
                                                || !pmi2.getVisible())
                                            _removeLink(pmi1.getUID(),
                                                    pmi2.getUID());
                                        else if (pmi1.getVisible()
                                                && pmi2.getVisible()) {
                                            if (pmi1.getGroup() != null
                                                    && pmi2.getGroup() != null)
                                                _addLink(pmi1, pmi2, dipUID,
                                                        lineText,
                                                        color, style,
                                                        clampToGround);
                                            else {
                                                if (pmi2.getGroup() == null) {
                                                    pmi2.removeOnVisibleChangedListener(
                                                            this);
                                                }
                                            }
                                        }
                                    }
                                });
                    }
                } catch (Exception ignored) {
                }
            }
            return;
        }

        // XXX remove an array of markers
        if (extras.containsKey("removeUIDArray")) {
            String[] uids = extras.getStringArray("removeUIDArray");
            if (uids != null) {
                for (int i = 0; i < uids.length - 1; i++) {
                    try {
                        _removeLink(uids[i], uids[i + 1]);
                    } catch (Exception ignored) {
                    }
                }
            }
            return;
        }

        // Do we already have a pairing line?
        if (_startItem != null && _endItem != null) {
            if (_text != null) {
                _textContainer.removeWidget(_text);
                _text = null;
            }

            String uidStart = _startItem.getUID();
            String uidEnd = _endItem.getUID();

            _removeLink(uidStart, uidEnd);

            // Should we remove the existing pairing line and
            // start a new one, or just remove it?
            if (uidStart.compareTo(uid) == 0 ||
                    uidEnd.compareTo(uid) == 0) {

                _startItem = null;
                _endItem = null;
            } else {
                doAdd = true;
            }
        } else {
            doAdd = true;
        }

        if (doAdd) {
            _mapView.getMapEventDispatcher().pushListeners();
            _mapView.getMapEventDispatcher()
                    .clearListeners(MapEvent.ITEM_CLICK);
            _mapView.getMapEventDispatcher().addMapEventListener(
                    MapEvent.ITEM_CLICK,
                    _itemClickedListener);
            _addStartingUID(uid);
        }

    }

    /** check to see if a pairing line exists between 2 markers 
     *  with the given uids */
    private boolean checkLink(String uid1, String uid2) {
        Map<String, _Link> map1 = _links.get(uid1);
        Map<String, _Link> map2 = _links.get(uid2);
        if (map1 != null) {
            if (map1.containsKey(uid2))
                return true;
        }

        if (map2 != null) {
            if (map2.containsKey(uid1))
                return true;
        }

        return false;
    }

    private void _addStartingUID(String uid) {
        MapItem item = MapGroup.deepFindItemWithMetaString(
                _mapView.getRootGroup(), "uid", uid);
        if (item instanceof PointMapItem) {
            _startItem = (PointMapItem) item;
            _endItem = null;
            _showText("Select object to pair");
        }
    }

    private final MapEventDispatcher.MapEventDispatchListener _itemClickedListener = new MapEventDispatcher.MapEventDispatchListener() {
        @Override
        public void onMapEvent(MapEvent event) {
            if ((_startItem != null)
                    && (event.getItem() instanceof PointMapItem)) {
                if (_endItem == null) {
                    _endItem = (PointMapItem) event.getItem();
                    if (_startItem != null && _endItem != null) {
                        _addLink(_startItem, _endItem, null, null, lineColor,
                                lineStyle);
                        _mapView.getMapEventDispatcher().popListeners();
                    }
                }
            }
        }
    };

    @SuppressWarnings("unused")
    private void _updateLinkInfo(final PointMapItem startPoint,
            final PointMapItem endPoint) {

        final double dist = startPoint.getPoint().distanceTo(
                endPoint.getPoint());
        double bearing = startPoint.getPoint().bearingTo(endPoint.getPoint());

        // Convert to azimuth
        if (bearing < 0)
            bearing = 180 + (bearing + 180);

        String distFtString = "---";
        if (!Double.isNaN(dist)) {
            distFtString = _FORMAT.format(ConversionFactors.METERS_TO_FEET
                    * dist)
                    + " ft";
        }

        String distMString = "---";
        if (!Double.isNaN(dist)) {
            distMString = _FORMAT.format(dist) + " m";
        }

        String bearingString = "---";
        if (!Double.isNaN(bearing)) {
            bearingString = _FORMAT.format(bearing) + " Deg";
        }

        String pairingText = distFtString + "\n" + distMString + "\n"
                + bearingString;

        _showText(pairingText);

        int lineCount = 3;
        float textWidth = _text.getTextFormat().measureTextWidth(pairingText);

        float lineOffset = lineCount
                * _text.getTextFormat().getBaselineSpacing();
        _text.setPoint((_mapView.getWidth() - textWidth) / 2,
                _mapView.getHeight() - lineOffset
                        - 15f);
        _text.setText(pairingText);
    }

    private void _showText(String text) {
        if (_text == null) {
            _text = new TextWidget();
            _textContainer.setBackingColor(0xff);
            _textContainer.addWidget(_text);
        }
        int lineCount = 2;
        float textWidth = _text.getTextFormat().measureTextWidth(text);

        float lineOffset = lineCount
                * _text.getTextFormat().getBaselineSpacing();
        _text.setPoint((_mapView.getWidth() - textWidth) / 2,
                _mapView.getHeight() - lineOffset
                        - 15f);
        _text.setText(text);
    }

    private void _addLink(PointMapItem start, PointMapItem end, String dipUID,
            String text, int color, int style) {
        _addLink(start, end, dipUID, text, color, style, false);
    }

    private void _addLink(PointMapItem start, PointMapItem end, String dipUID,
            String text, int color, int style, boolean clamp) {
        if (start == null || end == null)
            return;

        _startItem = start;
        _endItem = end;

        final _Link link = new _Link();
        link.assoc = new Association(_startItem, _endItem, UUID.randomUUID()
                .toString());
        if (!FileSystemUtils.isEmpty(dipUID))
            link.assoc.setMetaString("dipUID", dipUID);

        link.assoc.setColor(color);
        link.assoc.setStrokeWeight(3d);
        if (text != null && text.length() > 0)
            link.assoc.setText(text);
        link.assoc.setClampToGround(clamp);
        link.assoc.setStyle(style);
        _linkGroup.addItem(link.assoc);
        //Log.d(TAG, "Adding link for DIP: " + dipUID);

        link.mapEventListener = new MapItem.OnGroupChangedListener() {

            @Override
            public void onItemRemoved(MapItem item, MapGroup group) {
                _linkGroup.removeItem(link.assoc);
                _startItem.removeOnGroupChangedListener(this);
                _endItem.removeOnGroupChangedListener(this);
            }

            @Override
            public void onItemAdded(MapItem item, MapGroup group) {
            }
        };
        _startItem.addOnGroupChangedListener(link.mapEventListener);
        _endItem.addOnGroupChangedListener(link.mapEventListener);

        Map<String, _Link> map = _links.get(_startItem.getUID());
        if (map == null) {
            map = new HashMap<>();
            _links.put(_startItem.getUID(), map);
        }
        map.put(_endItem.getUID(), link);
    }

    private boolean _removeLink(String uid1, String uid2) {
        _Link link = null;
        Map<String, _Link> map = _links.get(uid1);
        if (map != null) {
            link = map.remove(uid2);
            // XXX check to make sure uid2 isnt associated with another marker
            Map<String, _Link> map2 = _links.get(uid2);
            if (map.size() == 0 && map2 == null) {
                _links.remove(uid2);
            }
        }

        if (link != null) {
            link.assoc.getFirstItem().removeOnGroupChangedListener(
                    link.mapEventListener);
            link.assoc.getSecondItem().removeOnGroupChangedListener(
                    link.mapEventListener);
            _linkGroup.removeItem(link.assoc);
        }

        return link != null;
    }

    private static class _Link {
        Association assoc;
        MapItem.OnGroupChangedListener mapEventListener;
    }

    private static MultiplePairingLineMapReceiver _instance = null;

    private PointMapItem _startItem = null;
    private PointMapItem _endItem = null;

    private int lineColor = Color.WHITE;
    private int lineStyle = Association.STYLE_OUTLINED;

    private final Map<String, Map<String, _Link>> _links = new HashMap<>();
    private MapGroup _linkGroup;

    private static final DecimalFormat _FORMAT = LocaleUtil
            .getDecimalFormat("0.00");

    private MapView _mapView;
    private LayoutWidget _textContainer;
    private TextWidget _text;

    public List<Association> getLinks(final String dipUID) {
        final List<Association> links = new ArrayList<>();

        if (FileSystemUtils.isEmpty(dipUID))
            return links;

        MapGroup.mapItems(_linkGroup, new MapItemsCallback() {

            @Override
            public boolean onItemFunction(MapItem item) {
                if (!(item instanceof Association)) {
                    return false;
                }

                if (!dipUID.equals(item.getMetaString("dipUID", ""))) {
                    return false;
                }

                links.add((Association) item);

                return false;
            }
        });

        return links;
    }
}

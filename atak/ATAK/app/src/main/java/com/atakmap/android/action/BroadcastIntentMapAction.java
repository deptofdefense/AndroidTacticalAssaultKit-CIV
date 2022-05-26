
package com.atakmap.android.action;

import android.content.Intent;
import android.net.Uri;

import com.atakmap.android.config.ConfigEnvironment;
import com.atakmap.android.config.ConfigFactory;
import com.atakmap.android.config.DataParser;
import com.atakmap.android.config.ParseUtil;
import com.atakmap.android.config.PhraseParser;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.DefaultMetaDataHolder;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.time.CoordinatedTime;

import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import com.atakmap.coremap.locale.LocaleUtil;

/**
 * Broadcasts an Intent dynamically generated from MapView and MapItem data.   This is specifically
 * used by the xml defined actions for the radial menu.
 */
class BroadcastIntentMapAction implements MapAction {

    public static final String TAG = "BroadcastIntentMapAction";

    private static final int _EXTRA_STRING = 0;
    private static final int _EXTRA_BOOLEAN = 1;
    private static final int _EXTRA_INTEGER = 2;

    private final List<_Extra> _extras = new ArrayList<>();
    private String _dataUri, _dataType;
    private String _action;

    private boolean _isActivity = false;

    public static class Factory implements ConfigFactory<MapAction> {
        @Override
        public MapAction createFromElem(ConfigEnvironment config,
                Node defNode) {
            BroadcastIntentMapAction action = new BroadcastIntentMapAction();

            if (defNode.getNodeName().equals("activity")) {
                action._isActivity = true;
            }

            Node actionNode = ParseUtil.seekNodeNamed(defNode.getFirstChild(),
                    Node.ELEMENT_NODE,
                    "action");
            if (actionNode != null) {
                action.setAction(DataParser.parseStringElem(actionNode, ""));
            }

            Node extrasNode = ParseUtil.seekNodeNamed(defNode.getFirstChild(),
                    Node.ELEMENT_NODE,
                    "extras");
            if (extrasNode != null) {
                _parseExtras(action, extrasNode);
            }
            Node dataNode = ParseUtil.seekNodeNamed(defNode.getFirstChild(),
                    Node.ELEMENT_NODE,
                    "data");
            if (dataNode != null) {
                _parseDataNode(action, dataNode);
            }

            return action;
        }

        private static void _parseExtras(BroadcastIntentMapAction action,
                Node extrasNode) {
            Node c = extrasNode.getFirstChild();
            while (c != null) {
                if (c.getNodeType() == Node.ELEMENT_NODE) {
                    NamedNodeMap attrs = c.getAttributes();
                    Node keyNode = attrs.getNamedItem("key");
                    if (keyNode != null) {
                        String key = keyNode.getNodeValue();
                        String type = c.getNodeName();

                        switch (type) {
                            case "string":
                                action.addStringExtra(key,
                                        DataParser.parseStringElem(c, ""));
                                break;
                            case "boolean":
                                action.addBooleanExtra(key,
                                        DataParser.parseStringElem(c, "false"));
                                break;
                            case "int":
                                action.addIntegerExtra(key,
                                        DataParser.parseStringElem(c, "-1"));
                                break;
                        }
                    }
                }
                c = c.getNextSibling();
            }
        }

        private static void _parseDataNode(BroadcastIntentMapAction action,
                Node node) {
            NamedNodeMap attrs = node.getAttributes();
            Node uriNode = attrs.getNamedItem("uri");
            if (uriNode != null) {
                action._dataUri = uriNode.getNodeValue();
                Node typeNode = attrs.getNamedItem("type");
                if (typeNode != null) {
                    action._dataType = typeNode.getNodeValue();
                }
            }
        }
    }

    private static final PhraseParser.Resolver _TIME_RESOLVER = new PhraseParser.Resolver() {
        @Override
        public String resolvePhraseKey(char specialChar, String key) {
            String r = "";
            try {
                SimpleDateFormat f = new SimpleDateFormat(key,
                        LocaleUtil.getCurrent());
                r = f.format(CoordinatedTime.currentDate());
            } catch (Exception ex) {
                // nothing
            }
            return r;
        }
    };

    @Override
    public void performAction(MapView mapView, MapItem mapItem) {
        Intent intent = new Intent();
        intent.setAction(_action);

        PhraseParser.Parameters parms = new PhraseParser.Parameters();
        parms.setResolver('@',
                new PhraseParser.BundleResolver(
                        mapView != null ? mapView.getMapData()
                                : new DefaultMetaDataHolder()));
        parms.setResolver('^', _TIME_RESOLVER);
        if (mapItem != null) {
            parms.setResolver('$', new PhraseParser.BundleResolver(mapItem));
        }

        for (_Extra ex : _extras) {
            String resolvedValue = PhraseParser.expandPhrase(ex.value, parms);
            switch (ex.type) {
                case _EXTRA_STRING:
                    intent.putExtra(ex.key, resolvedValue);
                    break;
                case _EXTRA_BOOLEAN:
                    intent.putExtra(ex.key,
                            Boolean.parseBoolean(resolvedValue));
                    break;
                case _EXTRA_INTEGER:
                    intent.putExtra(ex.key, Integer.parseInt(resolvedValue));
            }
        }

        if (_dataUri != null) {
            String resolvedUri = PhraseParser.expandPhrase(_dataUri, parms);
            if (_dataType != null) {
                String resolvedType = PhraseParser.expandPhrase(_dataType,
                        parms);
                intent.setDataAndType(Uri.parse(resolvedUri), resolvedType);
            } else {
                intent.setData(Uri.parse(resolvedUri));
            }
        }

        if (mapView == null)
            return;

        if (!_isActivity) {
            AtakBroadcast.getInstance().sendBroadcast(intent);
        } else {
            try {
                mapView.getContext().startActivity(intent);
            } catch (Exception ex) {
                Log.e(TAG, "failed to start activity");
                Log.e(TAG, "error: ", ex);
            }
        }
    }

    public void setAction(String action) {
        _action = action;
    }

    private void addStringExtra(String key, String value) {
        _Extra ex = _addExtra(key, value);
        ex.type = _EXTRA_STRING;
    }

    private void addBooleanExtra(String key, String value) {
        _Extra ex = _addExtra(key, value);
        ex.type = _EXTRA_BOOLEAN;
    }

    private void addIntegerExtra(String key, String value) {
        _Extra ex = _addExtra(key, value);
        ex.type = _EXTRA_INTEGER;
    }

    private _Extra _addExtra(String key, String value) {
        _Extra ex = new _Extra();
        ex.key = key;
        ex.value = value;
        _extras.add(ex);
        return ex;
    }

    private static class _Extra {
        int type;
        String key;
        String value;
    }

}


package com.atakmap.android.menu;

import com.atakmap.android.config.ConfigEnvironment;
import com.atakmap.android.config.DataParser;
import com.atakmap.android.config.FlagsParser;
import com.atakmap.android.config.ParseUtil;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.widgets.AbstractButtonWidget;
import com.atakmap.android.widgets.LayoutWidget;
import com.atakmap.android.widgets.MapWidget;
import com.atakmap.android.widgets.WidgetBackground;
import com.atakmap.coremap.log.Log;

import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import java.util.ArrayList;
import java.util.Collection;

public class MapMenuWidget extends LayoutWidget {

    public static final String TAG = "MapMenuWidget";

    float _buttonSpan = 45f;
    float _buttonRadius = 70f;
    float _buttonWidth = 100f;
    boolean _dragDismiss = false;
    // private String _buttonBg = null;
    WidgetBackground _buttonBackground;

    private float _upperBound;
    private float _lowerBound;
    private float _leftBound;
    private float _rightBound;

    public float getLeftBound() {
        return _leftBound;
    }

    public float getRightBound() {
        return _rightBound;
    }

    public float getUpperBound() {
        return _upperBound;
    }

    public float getLowerBound() {
        return _lowerBound;
    }

    public MapMenuWidget() {
        _setButtonParams(_buttonRadius, _buttonWidth, _buttonSpan, null);
    }

    void _setButtonParams(float radius, float width, float span,
            WidgetBackground bg) {
        _setButtonParams(_buttonRadius, _buttonWidth, _buttonSpan, bg, false);
    }

    void _setButtonParams(float radius, float width, float span,
            WidgetBackground bg, boolean dragDismiss) {
        _buttonRadius = radius;
        _buttonWidth = width;
        _buttonSpan = span;
        // _buttonBg = bg;
        _buttonBackground = bg;
        _dragDismiss = dragDismiss;

        _upperBound = -(_buttonRadius + _buttonWidth);
        _lowerBound = (_buttonRadius + _buttonWidth);
        _leftBound = -(_buttonRadius + _buttonWidth);
        _rightBound = (_buttonRadius + _buttonWidth);
    }

    static class Factory extends LayoutWidget.Factory {
        final float maxRadius;

        Factory(final float maxRadius) {
            this.maxRadius = maxRadius;
        }

        @Override
        public MapWidget createFromElem(ConfigEnvironment config,
                Node defNode) {
            MapMenuWidget widget = new MapMenuWidget();
            configAttributes(widget, config, defNode.getAttributes());
            _parseChildren(widget, config, defNode.getFirstChild());
            return widget;
        }

        private WidgetBackground _loadBg(ConfigEnvironment config,
                String bgRef) {
            WidgetBackground bg = null;
            if (bgRef != null) {
                try {
                    FlagsParser.Parameters stateFlags = new FlagsParser.Parameters();
                    stateFlags.setFlagBits("disabled",
                            MapMenuButtonWidget.STATE_DISABLED);
                    stateFlags.setFlagBits("selected",
                            MapMenuButtonWidget.STATE_SELECTED);
                    stateFlags.setFlagBits("pressed",
                            MapMenuButtonWidget.STATE_PRESSED);
                    ConfigEnvironment config2 = config.buildUpon()
                            .setFlagsParameters(stateFlags)
                            .build();
                    bg = WidgetBackground.resolveWidgetBackground(config2,
                            bgRef);
                } catch (Exception e) {
                    Log.e(TAG, "error: ", e);
                }
            }
            return bg;
        }

        protected void configAttributes(MapMenuWidget widget,
                ConfigEnvironment config,
                NamedNodeMap attrs) {
            super.configAttributes(widget, config, attrs);
            float radius = DataParser.parseFloatText(
                    attrs.getNamedItem("buttonRadius"), 45f)
                    * MapView.DENSITY;// cfgRes.expandFloatAttr(attrs, "buttonRadius", 45f);
            float width = DataParser.parseFloatText(
                    attrs.getNamedItem("buttonWidth"), 100f)
                    * MapView.DENSITY;// cfgRes.expandFloatAttr(attrs, "buttonWidth", 100f);
            float span = DataParser.parseFloatText(
                    attrs.getNamedItem("buttonSpan"), 45f);// cfgRes.expandFloatAttr(attrs,
                                                                                                  // "buttonSpan",

            // 45f);
            boolean dragDismiss = false;
            try {
                dragDismiss = DataParser.parseBooleanText(
                        attrs.getNamedItem("dragDismiss"), false);
            } catch (Exception ignored) {
            }

            if (width + radius > maxRadius) {
                radius = maxRadius * 0.3475f;
                width = maxRadius * 0.6525f;
            }

            WidgetBackground bg = _loadBg(config,
                    DataParser.parseStringText(attrs.getNamedItem("buttonBg"),
                            null));
            widget._setButtonParams(radius, width, span, bg, dragDismiss);
        }

        private void _parseChildren(MapMenuWidget menu,
                ConfigEnvironment config, Node childNode) {

            float lastAngle = 0f;
            float lastSpan = 0f;

            childNode = ParseUtil.seekNodeNamed(childNode, Node.ELEMENT_NODE,
                    "button");
            MapMenuButtonWidget.Factory buttonFactory = new MapMenuButtonWidget.Factory();

            while (childNode != null) {

                MapWidget buttonWidget = buttonFactory.createFromElem(config,
                        childNode);
                if (buttonWidget instanceof MapMenuButtonWidget) {
                    MapMenuButtonWidget button = (MapMenuButtonWidget) buttonWidget;
                    NamedNodeMap attrs = childNode.getAttributes();
                    if (null == attrs.getNamedItem("background")
                            && menu._buttonBackground != null) {
                        try {
                            button.setBackground(menu._buttonBackground);
                        } catch (Exception e) {
                            Log.e(TAG, "error: ", e);
                        }
                    }
                    if (null == attrs.getNamedItem("radius")) {
                        button.setOrientation(button.getOrientationAngle(),
                                menu._buttonRadius);
                    }
                    if (null == attrs.getNamedItem("span")) {
                        button.setButtonSize(menu._buttonSpan,
                                button.getButtonWidth());
                    }
                    if (null == attrs.getNamedItem("width")) {
                        button.setButtonSize(button.getButtonSpan(),
                                menu._buttonWidth);
                    }

                    if (null == attrs.getNamedItem("angle")) {
                        button.setOrientation(lastAngle + lastSpan,
                                button.getOrientationRadius());
                    }
                    lastAngle = button.getOrientationAngle();
                    lastSpan = button.getButtonSpan();

                    menu.addWidget(button);

                }

                childNode = ParseUtil.seekNodeNamed(childNode.getNextSibling(),
                        Node.ELEMENT_NODE,
                        "button");
            }
        }
    }

    @Override
    protected boolean onWidgetCanBeAdded(int index, MapWidget widget) {
        return (widget instanceof MapMenuButtonWidget);
    }

    public int cullDisabledWidgets() {
        Collection<MapWidget> childWidgets = getChildWidgets();
        ArrayList<MapWidget> toDelete = new ArrayList<>();
        for (MapWidget widget : childWidgets) {
            if (widget instanceof AbstractButtonWidget) {
                AbstractButtonWidget abwid = (AbstractButtonWidget) widget;

                if (abwid.getState() == AbstractButtonWidget.STATE_DISABLED) {
                    toDelete.add(widget);
                }
            }
        }

        for (MapWidget widget : toDelete) {
            childWidgets.remove(widget); // this is a copy of the widgets
            removeWidget(widget); // this removes the widgets from the layout
        }

        if (toDelete.size() > 0) {//recalculate menu
            float lastAngleRe = 0f;
            float lastSpanRe = 0f;

            for (MapWidget w : childWidgets) {
                MapMenuButtonWidget button = (MapMenuButtonWidget) w;
                if (button.getOnClick() != null
                        && button.getOnClick().equals("actions/cancel.xml")) {//assume cancel always at -90
                    lastAngleRe = button.getOrientationAngle();
                }

                button.setOrientation(lastAngleRe + lastSpanRe,
                        button.getOrientationRadius());

                lastAngleRe = button.getOrientationAngle();
                lastSpanRe = button.getButtonSpan();
            }
        }

        return toDelete.size();
    }

    public void reorient(float angle) {
        Collection<MapWidget> childWidgets = getChildWidgets();
        int numWid = childWidgets.size();
        if (numWid > 0) {

            float totalSpan = 0f;
            float fstBtnHlfSpn = 0f;
            for (MapWidget widget : childWidgets) {
                MapMenuButtonWidget button = (MapMenuButtonWidget) widget;
                if (totalSpan == 0) {
                    //save half the first button span to make future math easier
                    fstBtnHlfSpn = button.getButtonSpan() / 2;
                }
                totalSpan += button.getButtonSpan();

            }

            if (numWid > 1) {
                angle -= ((totalSpan) / 2 - fstBtnHlfSpn);
            }

            for (MapWidget widget : childWidgets) {
                MapMenuButtonWidget button = (MapMenuButtonWidget) widget;

                float orient = button.getOrientationAngle();

                button.setOrientation(orient + angle,
                        button.getOrientationRadius());
            }
        }

    }

}

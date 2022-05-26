
package com.atakmap.android.menu;

import android.content.Context;

import com.atakmap.android.config.ConfigEnvironment;
import com.atakmap.android.config.DataParser;
import com.atakmap.android.config.FlagsParser;
import com.atakmap.android.config.ParseUtil;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.widgets.LayoutWidget;
import com.atakmap.android.widgets.MapWidget;
import com.atakmap.android.widgets.WidgetBackground;
import com.atakmap.coremap.log.Log;

import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import gov.tak.api.widgets.IMapMenuButtonWidget;
import gov.tak.api.widgets.IMapMenuWidget;
import gov.tak.api.widgets.IMapWidget;

public class MapMenuWidget extends LayoutWidget implements IMapMenuWidget {

    public static final String TAG = "MapMenuWidget";
    private static final float BUTTON_WIDTH_SCALAR = 0.76666f;

    float _buttonSpan = 45f;
    float _buttonRadius = 70f;
    float _buttonWidth = 100f;

    boolean _dragDismiss = false;
    private WidgetBackground _buttonBackground;

    float _coveredAngle = 360f;
    float _startAngle = -90f;

    boolean _explicitSizing = false;

    private boolean _clockwiseWinding = false;

    /**
     * Radial menu container containing buttons and controlling layout.
     */
    public MapMenuWidget() {
    }

    private void _setButtonParams(float radius, float width, float span,
            WidgetBackground bg, boolean dragDismiss) {
        _buttonRadius = radius;
        _buttonWidth = width;
        _buttonSpan = span;
        _buttonBackground = bg;
        _dragDismiss = dragDismiss;
    }

    /**
     * Get whether *any* of the sizing of buttons has been explicitly set in configuration.
     * Default values applied while reading missing configuration are not considered explicit.
     * @return true if any of the width, span, or radius were specified.
     */
    public boolean getExplicitSizing() {
        return _explicitSizing;
    }

    /**
     * Provides expected arc length of each button.
     * @return button span dimension.
     */
    public float getButtonSpan() {
        return _buttonSpan;
    }

    /**
     * Get the total angle that the radiol menu spans in degrees.
     * @return current total angle the radial menu will cover.
     */
    public float getCoveredAngle() {
        return _coveredAngle;
    }

    /**
     * Set the total angle that the radiol menu spans in degrees.
     * Covered angle must be positive and less than 360 degrees in total.
     * @param angle
     */
    public void setCoveredAngle(float angle) {
        _coveredAngle = Math.min(Math.max(0f, angle), 360f);
    }

    /**
     * Get the angle in degrees that locates the middle of the first child button.
     * @return positional angle for the first child button.
     */
    public float getStartAngle() {
        return _startAngle;
    }

    /**
     * Set the angle in degrees that locates the middle of the first child button.
     * Angle to be set should be between -180 and 180 degrees. A value of -90 degrees
     * is straight down, 0 is to the screen right, 90 is straight up, and 180 is to
     * the screen left.
     */
    public void setStartAngle(float angle) {
        _startAngle = Math.min(Math.max(-180f, angle), 180f);
    }

    /**
     * Gets the default inside radius of the radial menu in display adjusted pixel units.
     * @return current inside radius
     */
    public float getInnerRadius() {
        return _buttonRadius;
    }

    /**
     * Sets the default inside radius of the radial menu in display adjusted pixel units.
     * Inner radius value is used as a default to locate child buttons, but this
     * value does not change button positioning after layout operations.
     * @param radius
     */
    public void setInnerRadius(float radius) {
        _buttonRadius = Math.max(0f, radius);
    }

    /**
     * Gets the default radial width of child buttons in display adjusted pixel units.
     * @return default width of child buttons
     */
    public float getButtonWidth() {
        return _buttonWidth;
    }

    /**
     * Sets the default radial width of child buttons in display adjusted pixel units.
     * @param width to be used to size buttons by default
     */
    public void setButtonWidth(float width) {
        _buttonWidth = Math.max(0f, width);
    }

    /**
     * Gets the sequencing convention for the menu's child buttons.
     * Clockwise winding is along an axis into the screen
     * using a right handed convention. The default winding is counter
     * clockwise, or a default return value of false.
     * @return whether winding of child buttons is clockwise
     */
    public boolean isClockwiseWinding() {
        return _clockwiseWinding;
    }

    /**
     * Sets the sequencing convention for the menu's child buttons.
     * Clockwise winding is along an axis into the screen
     * using a right handed convention.
     *
     * @param winding desired for the layout sequencing of buttons
     */
    public void setClockwiseWinding(boolean winding) {
        _clockwiseWinding = winding;
    }

    public boolean onWidgetCanBeAdded(int index, MapWidget widget) {
        return (widget instanceof IMapMenuButtonWidget);
    }

    static class Factory extends LayoutWidget.Factory {
        final Context _context;
        final XmlResourceResolver _resolver;
        final float _maxRadius;

        boolean _explicitSizing = false;

        Factory(final Context context, final XmlResourceResolver resolver,
                float maxRadius) {
            this._context = context;
            this._resolver = resolver;
            this._maxRadius = maxRadius;
        }

        @Override
        public MapWidget createFromElem(ConfigEnvironment config,
                Node defNode) {
            MapMenuWidget widget = new MapMenuWidget();
            configAttributes(widget, config, defNode.getAttributes());
            _parseChildren(widget, config, defNode.getFirstChild());
            widget._explicitSizing = _explicitSizing;
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

        void configAttributes(MapMenuWidget widget,
                ConfigEnvironment config,
                NamedNodeMap attrs) {
            super.configAttributes(widget, config, attrs);

            float radius = DataParser.parseFloatText(
                    attrs.getNamedItem("buttonRadius"), -1f);
            if (radius < 0)
                radius = 45f;
            else
                _explicitSizing = true;
            radius *= MapView.DENSITY;

            float width = DataParser.parseFloatText(
                    attrs.getNamedItem("buttonWidth"), -1f);
            if (width < 0)
                width = 100f;
            else
                _explicitSizing = true;
            width *= MapView.DENSITY * BUTTON_WIDTH_SCALAR;

            float span = DataParser.parseFloatText(
                    attrs.getNamedItem("buttonSpan"), -1f);
            if (span < 0)
                span = 45f;
            else
                _explicitSizing = true;

            boolean dragDismiss = false;
            try {
                dragDismiss = DataParser.parseBooleanText(
                        attrs.getNamedItem("dragDismiss"), false);
            } catch (Exception ignored) {
            }

            if (width + radius > _maxRadius) {
                radius = _maxRadius * 0.3475f;
                width = _maxRadius * 0.6525f;
            }

            WidgetBackground bg = _loadBg(config,
                    DataParser.parseStringText(attrs.getNamedItem("buttonBg"),
                            null));
            widget._setButtonParams(radius, width, span, bg, dragDismiss);
        }

        private void _parseChildren(MapMenuWidget menu,
                ConfigEnvironment config, Node childNode) {

            menu._coveredAngle = 0f; // reset and accumulate spans

            MapMenuButtonWidget.Factory buttonFactory = new MapMenuButtonWidget.Factory(
                    _context, _resolver);

            for (childNode = ParseUtil.seekNodeNamed(childNode,
                    Node.ELEMENT_NODE,
                    "button"); null != childNode; childNode = ParseUtil
                            .seekNodeNamed(childNode.getNextSibling(),
                                    Node.ELEMENT_NODE, "button")) {

                IMapWidget buttonWidget = buttonFactory.createFromElem(config,
                        childNode);
                if (buttonWidget instanceof IMapMenuButtonWidget) {
                    IMapMenuButtonWidget button = (IMapMenuButtonWidget) buttonWidget;
                    NamedNodeMap attrs = childNode.getAttributes();
                    if (null == attrs.getNamedItem("background")
                            && menu._buttonBackground != null) {
                        try {
                            button.setWidgetBackground(menu._buttonBackground);
                        } catch (Exception e) {
                            Log.e(TAG, "error: ", e);
                        }
                    }
                    if (null == attrs.getNamedItem("radius")) {
                        button.setOrientation(button.getOrientationAngle(),
                                menu._buttonRadius);
                        _explicitSizing = true;
                    }
                    if (null == attrs.getNamedItem("span")) {
                        button.setButtonSize(menu._buttonSpan,
                                button.getButtonWidth());
                        _explicitSizing = true;
                    }
                    if (null == attrs.getNamedItem("width")) {
                        button.setButtonSize(button.getButtonSpan(),
                                menu._buttonWidth);
                        _explicitSizing = true;
                    }

                    // By prior convention, the "angle" attribute has not been specified
                    // for each button. Rather, "spans" are used from one button with
                    // an "angle" in the prior layout strategy. Now, use the first
                    // "angle" to indicate the "_startAngle" for the menu.
                    if (null != attrs.getNamedItem("angle")) {
                        menu._startAngle = DataParser.parseFloatText(
                                attrs.getNamedItem("angle"), -90f);
                    }

                    // use button span to indicate layout weight
                    button.setLayoutWeight(button.getButtonSpan());
                    // accumulate spans for total coverage
                    menu._coveredAngle += button.getButtonSpan();

                    // parent / child relationships
                    IMapMenuWidget submenu = button.getSubmenu();
                    if (null != submenu)
                        submenu.setParent(menu);
                    menu.addChildWidget(button);
                }
            }
        }
    }
}

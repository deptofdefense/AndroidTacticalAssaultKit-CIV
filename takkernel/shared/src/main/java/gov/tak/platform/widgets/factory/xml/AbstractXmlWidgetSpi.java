package gov.tak.platform.widgets.factory.xml;

import com.atakmap.coremap.log.Log;

import gov.tak.api.binding.IPropertyValueSpi;
import gov.tak.api.util.Visitor;
import gov.tak.api.widgets.IMapWidget;
import gov.tak.api.widgets.IParentWidget;
import gov.tak.api.widgets.IRadialButtonWidget;
import gov.tak.api.widgets.factory.IMapWidgetSpi;
import gov.tak.platform.binding.PropertyInfo;
import gov.tak.platform.binding.PropertyValue;
import gov.tak.platform.binding.PropertyValueFactory;
import gov.tak.platform.config.DataParser;
import gov.tak.platform.config.FlagsParser;
import gov.tak.platform.graphics.Color;
import gov.tak.platform.graphics.PointF;
import gov.tak.platform.widgets.AbstractButtonWidget;
import gov.tak.platform.widgets.MapWidget;
import gov.tak.platform.widgets.RadialButtonWidget;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import java.util.HashMap;
import java.util.Map;

/**
 * Abstrct base for provider of IMapWidgets defined by an DOM Node
 */
public abstract class AbstractXmlWidgetSpi implements IMapWidgetSpi<Node> {

    public static final String TAG = "AbstractXmlWidgetSpi";
    private PropertyValueFactory<Node> propertyValueFactory = new PropertyValueFactory<>();

    /**
     * Create the provider
     */
    public AbstractXmlWidgetSpi() {
        // register the Node level property value SPI to the factory
        propertyValueFactory.registerSpi(new IPropertyValueSpi<Node>() {
            @Override
            public PropertyValue create(PropertyInfo propertyInfo, Node definition) {
                return ATTR_LEVEL_PV_SPI.create(propertyInfo, definition.getAttributes());
            }
        });
    }

    /**
     * Called to create the specific widget this SPI handles. If the returned IMapWidget is
     * a IParentWidget and the Node has child nodes, createChildWidget is called for each
     * child node recursively.
     *
     * @param node XML node definition
     *
     * @return specific widget instance or null when not supported
     */
    public abstract IMapWidget createRootWidget(Node node);

    /**
     * Called for each child node when the root widget (or ancestor of the root widget) is a IParentWidget.
     * If a widget is created, then
     *
     * @param parentWidget
     * @param childNode
     * @return
     */
    protected IMapWidget createChildWidget(IParentWidget parentWidget, Node childNode) {
        return null;
    }

    protected void parseChildren(IParentWidget parentWidget, Node parentNode) {
        Node childNode = parentNode.getFirstChild();
        while (childNode != null) {
            if (childNode.getNodeType() == Node.ELEMENT_NODE) {
                IMapWidget childWidget = createChildWidget(parentWidget, childNode);
                if (childWidget != null) {
                    parentWidget.addChildWidget(childWidget);
                    childWidget.setParent(parentWidget);
                }
            }
            childNode = childNode.getNextSibling();
        }

    }

    //
    // IWidgetSpi<Node> impl
    //

    @Override
    public IMapWidget create(Node definition) {

        IMapWidget widget = createRootWidget(definition);
        if (widget != null) {
            // parse children if is parent widget
            if (widget instanceof IParentWidget && definition.getFirstChild() != null)
                parseChildren((IParentWidget) widget, definition);
        }

        return widget;
    }

    protected void configureAttributes(IMapWidget widget, Node definition) {
        // visit all properties and create values
        Map<String, PropertyValue> propertyValues = new HashMap<>();
        widget.visitPropertyInfos(new Visitor<PropertyInfo>() {
            @Override
            public void visit(PropertyInfo propertyInfo) {
                PropertyValue value = propertyValueFactory.create(propertyInfo, definition);
                if (value != null)
                    propertyValues.put(propertyInfo.getName(), value);
            }
        });

        // set all created property values
        for (Map.Entry<String, PropertyValue> entry : propertyValues.entrySet()) {
            try {
                widget.setPropertyValue(entry.getKey(), entry.getValue().getValue());
            } catch (IllegalArgumentException ignore) {
                Log.d(TAG, "Property not found: " + entry.getKey() + "\n" + ignore.toString());
            }
        }
    }

    public static final IPropertyValueSpi<NamedNodeMap> ATTR_LEVEL_PV_SPI = new IPropertyValueSpi<NamedNodeMap>() {
        @Override
        public PropertyValue create(PropertyInfo propertyInfo, NamedNodeMap attrs) {

            Class<?> valueClass = propertyInfo.getPropertyClass();
            PropertyValue defVal = propertyInfo.getDefaultValue();

            if (valueClass.equals(RadialButtonWidget.ButtonSize.class))
                return parseButtonSize(attrs, defVal);
            else if (valueClass.equals(RadialButtonWidget.Orientation.class))
                return parseOrientation(attrs, defVal);
            else if (MapWidget.PROPERTY_POINT.canAssign(propertyInfo)) // x,y (point)
                return parsePoint(attrs, defVal);

            Node itemNode = attrs.getNamedItem(propertyInfo.getName());
            String textValue = null;
            if (itemNode != null)
                textValue = itemNode.getNodeValue();

            return VALUE_LEVEL_PV_SPI.create(propertyInfo, textValue);
        }
    };

    public static final IPropertyValueSpi<String> VALUE_LEVEL_PV_SPI = new IPropertyValueSpi<String>() {
        @Override
        public PropertyValue create(PropertyInfo propertyInfo, String textValue) {
            Class<?> valueClass = propertyInfo.getPropertyClass();

            PropertyValue defVal = propertyInfo.getDefaultValue();

            if (valueClass.equals(Boolean.class)) // Boolean
                return parseBoolean(textValue, defVal);
            else if (valueClass.equals(Float.class)) // Float
                return parseFloat(textValue, defVal);
            else if (valueClass.equals(AbstractButtonWidget.StateFlags.class)) // StateFlags
                return parseStateFlags(textValue, defVal);
            else if (valueClass.equals(Color.class)) // Color
                return parseColor(textValue, defVal);
            else if (valueClass.equals(String.class) && textValue != null) // String
                return PropertyValue.of(textValue);

            return defVal;
        }
    };

    private static PropertyValue parsePoint(NamedNodeMap attrs, PropertyValue defVal) {
        Node xNode = attrs.getNamedItem("x");
        Node yNode = attrs.getNamedItem("y");
        PropertyValue result = defVal;
        if (xNode != null || yNode != null) {
            float x = DataParser.parseFloatText(attrs.getNamedItem("x"), 0.f);
            float y = DataParser.parseFloatText(attrs.getNamedItem("y"), 0.f);
            result = PropertyValue.of(new PointF(x, y));
        }
        return result;
    }

    private static PropertyValue parseBoolean(String textValue, PropertyValue defVal) {
        PropertyValue result = defVal;
        try {
            result = PropertyValue.of(Boolean.parseBoolean(textValue));
        } catch (Exception ignore) {}
        return result;
    }

    private static PropertyValue parseFloat(String textValue, PropertyValue defVal) {
        PropertyValue result = defVal;
        try {
            result = PropertyValue.of(Float.parseFloat(textValue));
        } catch (Exception ignore) {}
        return result;
    }

    private static PropertyValue parseColor(String textValue, PropertyValue defVal) {
        PropertyValue result = defVal;
        try {
            result = PropertyValue.of(Color.parseColor(textValue));
        } catch (Exception ignored) {
        }
        return result;
    }

    private static PropertyValue parseStateFlags(String textValue, PropertyValue defaultValue) {
        if (textValue == null)
            return defaultValue;
        int state = FlagsParser.parseFlags(getStateFlagsParams(), textValue);
        return PropertyValue.of(new AbstractButtonWidget.StateFlags(state));
    }

    private static FlagsParser.Parameters getStateFlagsParams() {
        FlagsParser.Parameters params = new FlagsParser.Parameters();
        params.setFlagBits("disabled", AbstractButtonWidget.STATE_DISABLED);
        params.setFlagBits("selected", AbstractButtonWidget.STATE_SELECTED);
        params.setFlagBits("pressed", AbstractButtonWidget.STATE_PRESSED);
        return params;
    }

    private static PropertyValue parseButtonSize(NamedNodeMap attrs, PropertyValue defVal) {

        PropertyValue result = defVal;

        boolean hasDef = false;
        final IRadialButtonWidget.ButtonSize defBs = new IRadialButtonWidget.ButtonSize();
        float span = defBs.getSpan();
        float width = defBs.getWidth();

        try {
            span = Float.parseFloat(attrs.getNamedItem("span").getNodeValue());
            hasDef = true;
        } catch (Exception ignore) {
        }
        try {
            width = Float.parseFloat(attrs.getNamedItem("width").getNodeValue());
            hasDef = true;
        } catch (Exception ignore) {
        }

        return hasDef ? PropertyValue.of(new RadialButtonWidget.ButtonSize(span, width)) : defVal;
    }

    private static PropertyValue parseOrientation(NamedNodeMap attrs, PropertyValue defVal) {

        PropertyValue result = defVal;

        boolean hasDef = false;
        final IRadialButtonWidget.Orientation defO = new IRadialButtonWidget.Orientation();
        float angle = defO.getAngle();
        float radius = defO.getRadius();

        try {
            angle = Float.parseFloat(attrs.getNamedItem("angle").getNodeValue());
            hasDef = true;
        } catch (Exception ignore) {
        }
        try {
            radius = Float.parseFloat(attrs.getNamedItem("radius").getNodeValue());
            hasDef = true;
        } catch (Exception ignore) {
        }

        return hasDef ? PropertyValue.of(new IRadialButtonWidget.Orientation(angle, radius)) : defVal;
    }

}

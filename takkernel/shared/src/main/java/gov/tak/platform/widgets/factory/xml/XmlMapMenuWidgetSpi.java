package gov.tak.platform.widgets.factory.xml;

import gov.tak.api.widgets.IMapMenuWidget;
import gov.tak.api.widgets.IMapWidget;
import gov.tak.api.widgets.IParentWidget;
import gov.tak.api.widgets.factory.IMapMenuWidgetSpi;
import gov.tak.platform.config.DataParser;
import gov.tak.platform.widgets.MapMenuButtonWidget;
import gov.tak.platform.widgets.MapMenuWidget;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

/**
 *
 */
public class XmlMapMenuWidgetSpi extends AbstractXmlWidgetSpi implements IMapMenuWidgetSpi<Node> {

    private static final String TAG = "XmlMapMenuWidgetSpi";

    @Override
    public IMapMenuWidget create(Node definition) {
        return (IMapMenuWidget) super.create(definition);
    }

    @Override
    public IMapMenuWidget createRootWidget(Node node) {

        if (!node.getNodeName().equals("menu"))
            return null;

        MapMenuWidget.Builder builder = new MapMenuWidget.Builder();
        builder.setExplicitSizing(builder.getExplicitSizing());
        IMapMenuWidget widget = builder.reset();

        // configure menu attributes
        configureAttributes(widget, node);

        return widget;
    }

    @Override
    protected IMapWidget createChildWidget(IParentWidget parentWidget, Node childNode) {

        // button
        if (parentWidget instanceof MapMenuWidget && childNode.getNodeName().equals("button")) {

            MapMenuWidget.Builder builder = new MapMenuWidget.Builder((MapMenuWidget)parentWidget);

            MapMenuButtonWidget button = new MapMenuButtonWidget();
            button.setWidgetBackground(builder.getButtonBackground());
            button.setOrientation(button.getOrientationAngle(), builder.getButtonRadius());
            button.setButtonSize(builder.getButtonSpan(), builder.getButtonWidth());
            builder.setExplicitSizing(true);

            // let existing attributes override
            configureAttributes(button, childNode);

            // use button span to indicate layout weight
            button.setLayoutWeight(button.getButtonSpan());
            // accumulate spans for total coverage
            builder.setCoveredAngle(builder.getCoveredAngle() + button.getButtonSpan());

            // By prior convention, the "angle" attribute has not been specified
            // for each button. Rather, "spans" are used from one button with
            // an "angle" in the prior layout strategy. Now, use the first
            // "angle" to indicate the "_startAngle" for the menu.
            if (parentWidget.getChildWidgetCount() == 0) {
                NamedNodeMap attrs = childNode.getAttributes();
                if (null != attrs.getNamedItem("angle")) {
                    builder.setStartAngle(DataParser.parseFloatText(
                            attrs.getNamedItem("angle"), -90f));
                }
            }

            // parent / child relationships
            IMapMenuWidget submenu = button.getSubmenu();
            if (null != submenu)
                builder.setSubmenu(submenu);

            return button;
        }

        // ignore everything else
        return null;
    }
}

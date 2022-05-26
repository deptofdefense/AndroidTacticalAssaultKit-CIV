package gov.tak.platform.widgets.config;

import gov.tak.api.widgets.IMapMenuButtonWidget;
import gov.tak.api.widgets.IMapMenuWidget;
import gov.tak.api.widgets.IMapMenuWidgetController;
import gov.tak.api.widgets.IMapWidget;
import gov.tak.api.widgets.factory.IMapMenuWidgetControllerSpi;
import gov.tak.platform.widgets.AbstractButtonWidget;
import org.w3c.dom.Node;

import java.util.ArrayList;
import java.util.List;

/**
 * Control a widget
 */
public class ConfigMapMenuWidgetController extends ConfigWidgetController implements IMapMenuWidgetController {

    private List<ConfigWidgetController> buttonControllers = new ArrayList<>();

    /**
     * Factory provider
     */
    public static final IMapMenuWidgetControllerSpi<ConfigWidgetModel> SPI = new IMapMenuWidgetControllerSpi<ConfigWidgetModel>() {

        @Override
        public IMapMenuWidgetController create(IMapWidget mapMenu, ConfigWidgetModel model) {
            return new ConfigMapMenuWidgetController((IMapMenuWidget) mapMenu, model);
        }
    };

    /**
     * Create a controller
     *
     * @param mapMenu the widget
     * @param model
     */
    public ConfigMapMenuWidgetController(IMapMenuWidget mapMenu, ConfigWidgetModel model) {
        super(mapMenu, model);

        // find buttons and create config controllers for them
        // XXX-- this could be more robust to widget changes by listening
        Node buttonNode = model.getNode().getFirstChild();
        for (IMapWidget child : mapMenu.getChildren()) {
            if (child instanceof IMapMenuButtonWidget) {
                buttonNode = seekNodeNamed(buttonNode, Node.ELEMENT_NODE, "button");

                ConfigWidgetController buttonController = new ConfigWidgetController(child,
                        new ConfigWidgetModel(buttonNode, model.getConfig()));

                // the following should be expanded with the phrase parser
                buttonController.registerPhraseAttribute(AbstractButtonWidget.PROPERTY_SELECTABLE.getName());
                buttonController.registerPhraseAttribute(AbstractButtonWidget.PROPERTY_TEXT.getName());
                buttonController.registerPhraseAttribute("selected");
                buttonController.registerPhraseAttribute("disabled");

                buttonControllers.add(buttonController);

                buttonNode = buttonNode.getNextSibling();
            }
        }
    }

    public List<ConfigWidgetController> getButtonControllers() {
        return this.buttonControllers;
    }

    @Override
    public IMapMenuWidget getMapMenuWidget() {
        return (IMapMenuWidget) this.widget;
    }

    @Override
    public void refreshProperties() {
        super.refreshProperties();
        for (ConfigWidgetController buttonController : buttonControllers)
            buttonController.refreshProperties();
    }

    private static Node seekNodeNamed(Node node, int type, String name) {
        while (node != null
                && (node.getNodeType() != type || !node.getNodeName().equals(
                name))) {
            node = node.getNextSibling();
        }
        return node;
    }
}

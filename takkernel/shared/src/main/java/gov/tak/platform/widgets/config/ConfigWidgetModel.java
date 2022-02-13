package gov.tak.platform.widgets.config;

import gov.tak.platform.config.ConfigEnvironment;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

/**
 * A widget model where definition and property values come from XML and a ConfigEnvironement
 */
public class ConfigWidgetModel {

    private ConfigEnvironment config;
    private Node node;

    /**
     * Create the model given XML and environment
     *
     * @param node
     * @param config
     */
    public ConfigWidgetModel(Node node, ConfigEnvironment config) {
        this.node = node;
        this.config = config;
    }

    /**
     * Get the config environment
     *
     * @return
     */
    public ConfigEnvironment getConfig() {
        return this.config;
    }

    /**
     * Get the node attributes
     *
     * @return
     */
    public NamedNodeMap getAttrs() {
        return this.node.getAttributes();
    }

    public Node getNode() {
        return this.node;
    }
}

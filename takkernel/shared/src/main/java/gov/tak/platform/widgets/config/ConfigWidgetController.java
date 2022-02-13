package gov.tak.platform.widgets.config;

import gov.tak.api.commons.graphics.IIcon;
import gov.tak.api.widgets.IMapWidget;
import gov.tak.api.widgets.IWidgetBackground;
import gov.tak.platform.binding.PropertyInfo;
import gov.tak.platform.binding.PropertyValue;
import gov.tak.platform.config.ConfigEnvironment;
import gov.tak.platform.config.DataParser;
import gov.tak.platform.config.PhraseParser;
import gov.tak.platform.widgets.*;
import gov.tak.platform.widgets.factory.xml.AbstractXmlWidgetSpi;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import java.util.HashSet;
import java.util.Set;

/**
 * Controller for any widget where the model is a ConfigWidgetModel. Calling refreshProperties
 * or refreshProperty will trigger a change to the widget property value given as extracted
 * from the ConfigWidgetModel. The model value be changed by calling `resetDefinition`
 */
public class ConfigWidgetController extends AbstractWidgetController<ConfigWidgetModel> {

    private Set<String> phraseRegistery = new HashSet<>();

    /**
     * Create a controller
     *
     * @param widget the widget
     * @param definition
     */
    public ConfigWidgetController(IMapWidget widget, ConfigWidgetModel definition) {
        super(widget, definition);
    }

    /**
     * Flag a property name that should be parsed as a Phrase
     *
     * @param propertyName
     */
    public void registerPhraseAttribute(String propertyName) {
        this.phraseRegistery.add(propertyName);
    }

    @Override
    public void onPropertyValueChanged(IMapWidget bindingObject, String propertyName, Object oldValue, Object value) {
        // nothing
    }

    @Override
    protected PropertyValue resolvePropertyValue(PropertyInfo propertyInfo, ConfigWidgetModel model) {

        Class<?> valueClass = propertyInfo.getPropertyClass();
        PropertyValue defVal = propertyInfo.getDefaultValue();
        String expandedValue = null;

        // expand phrase if needed
        if (phraseRegistery.contains(propertyInfo.getName())) {
            expandedValue = expandPhrase(propertyInfo.getName(), model);
        } else {
            expandedValue = getAttribute(propertyInfo.getName(), model);
        }

        // Config required property types
        if (valueClass.equals(IIcon.class)) {
            IIcon icon = ConfigResolver.resolveIcon(model.getConfig(), expandedValue);
            return icon != null ? PropertyValue.of(icon) : defVal;
        } else if (valueClass.equals(IWidgetBackground.class)) {
            IWidgetBackground bg = ConfigResolver.resolveWidgetBackground(model.getConfig(), expandedValue);
            return bg != null ? PropertyValue.of(bg) : defVal;
        } else if (valueClass.equals(AbstractButtonWidget.StateFlags.class)) {
            AbstractButtonWidget.StateFlags stateFlags = parseSeparatedStateFlags(model, phraseRegistery);
            if (stateFlags != null)
                return PropertyValue.of(stateFlags);
            // should fall through to XML spi
        }

        // fall back on attribute level property SPI
        return AbstractXmlWidgetSpi.ATTR_LEVEL_PV_SPI.create(propertyInfo, model.getAttrs());
    }

    private static AbstractButtonWidget.StateFlags parseSeparatedStateFlags(ConfigWidgetModel model,
                                                                            Set<String> phraseRegistery) {
        String selected = null;
        if (phraseRegistery.contains("selected"))
            selected = expandPhrase("selected", model);
        else
            selected = getAttribute("selected", model);

        String disabled = null;
        if (phraseRegistery.contains("disabled"))
            disabled = expandPhrase("disabled", model);
        else
            disabled = getAttribute("disabled", model);

        AbstractButtonWidget.StateFlags result = null;
        int flags = 0;
        boolean hasFlags = false;
        if (selected != null && !selected.isEmpty()) {
            flags |= (DataParser.parseBoolean(selected, false) ? AbstractButtonWidget.STATE_SELECTED : 0);
            hasFlags = true;
        }
        if (disabled != null && !disabled.isEmpty()) {
            flags |= (DataParser.parseBoolean(selected, false) ? AbstractButtonWidget.STATE_DISABLED : 0);
            hasFlags = true;
        }
        return hasFlags ? new AbstractButtonWidget.StateFlags(flags) : null;
    }

    protected static String expandPhrase(String propertyName, ConfigWidgetModel definition) {
        String stringValue = DataParser.parseStringText(
                definition.getAttrs().getNamedItem(propertyName),
                "");

        ConfigEnvironment config = definition.getConfig();

        if (config.getPhraseParserParameters() != null) {
            stringValue = PhraseParser.expandPhrase(stringValue,
                    config.getPhraseParserParameters());
        }

        return stringValue;
    }

    protected static String getAttribute(String propertyName, ConfigWidgetModel definition) {
        NamedNodeMap attrs = definition.getAttrs();
        Node node = attrs.getNamedItem(propertyName);
        if (node == null)
            return null;
        return node.getNodeValue();
    }
}

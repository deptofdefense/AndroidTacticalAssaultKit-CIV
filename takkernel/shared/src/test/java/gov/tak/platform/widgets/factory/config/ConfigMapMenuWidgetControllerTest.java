package gov.tak.platform.widgets.factory.config;

import gov.tak.api.widgets.IAbstractButtonWidget;
import gov.tak.api.widgets.IMapMenuButtonWidget;
import gov.tak.api.widgets.IMapMenuWidget;
import gov.tak.platform.config.ConfigEnvironment;
import gov.tak.platform.config.PhraseParser;
import gov.tak.platform.utils.XMLUtils;
import gov.tak.platform.widgets.AbstractButtonWidget;
import gov.tak.platform.widgets.config.ConfigMapMenuWidgetController;
import gov.tak.platform.widgets.config.ConfigResolver;
import gov.tak.platform.widgets.config.ConfigWidgetController;
import gov.tak.platform.widgets.config.ConfigWidgetModel;
import gov.tak.platform.widgets.factory.MapMenuWidgetFactory;
import gov.tak.platform.widgets.factory.xml.XmlMapMenuWidgetSpi;
import org.apache.commons.math3.analysis.function.Abs;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;

@RunWith(MockitoJUnitRunner.class)
public class ConfigMapMenuWidgetControllerTest {

    public static final String MENU_SRC = "<menu\n" +
            "    buttonBg='bgs/button.xml'\n" +
            "    buttonRadius='65'\n" +
            "    buttonSpan='90'\n" +
            "    buttonWidth='90' >\n" +
            "\n" +
            "    <button\n" +
            "        angle='-90'\n" +
            "        icon='icons/delete.png'\n" +
            "        onClick='actions/remove.xml'\n" +
            "        disabled='!{${removable}}'  />\n" +
            "\n" +
            "    <button\n" +
            "        icon='icons/breadcrumbs.xml'\n" +
            "        onClick='actions/toggle_crumb.xml'\n" +
            "        selectable='true'\n" +
            "        selected='${tracks_on}' />\n" +
            "\n" +
            "    <button\n" +
            "        icon='icons/camlock.xml'\n" +
            "        onClick='actions/lockcam.xml'\n" +
            "        selectable='true'\n" +
            "        selected='${camLocked}' />\n" +
            "\n" +
            "    <button\n" +
            "        icon='icons/pairing_line.png'\n" +
            "        onClick='actions/pairingline.xml'\n" +
            "        selected='${pairingline_on}' />\n" +
            "\n" +
            "</menu>";

    @Test
    public void testBasicConfigMenu() throws ParserConfigurationException, IOException, SAXException {

        XmlMapMenuWidgetSpi spi = new XmlMapMenuWidgetSpi();
        MapMenuWidgetFactory.registerSpi(spi, Node.class, 0);

        DocumentBuilderFactory dbf = XMLUtils.getDocumentBuilderFactory();

        final DocumentBuilder db = dbf.newDocumentBuilder();
        Document doc = db.parse(new ByteArrayInputStream(MENU_SRC.getBytes()));
        IMapMenuWidget mapMenu = MapMenuWidgetFactory.create(doc.getDocumentElement());

        ConfigEnvironment.Builder ceb = new ConfigEnvironment.Builder();

        PhraseParser.Parameters phraseParams = new PhraseParser.Parameters();
        phraseParams.setResolver('!', new PhraseParser.Resolver() {
            @Override
            public String resolvePhraseKey(char specialChar, String key) {
                boolean value = false;
                try {
                    value = Boolean.parseBoolean(key);
                } catch (Exception ex) {
                }

                value = !value;

                return value ? "true" : "false";
            }
        });
        phraseParams.setResolver('$', new PhraseParser.Resolver() {
            @Override
            public String resolvePhraseKey(char specialChar, String key) {
                if (key.equals("removable"))
                    return "true";
                else if (key.equals("tracks_on"))
                    return "true";
                else if (key.equals("camLocked"))
                    return "false";
                else if (key.equals("pairingline_on"))
                    return "true";
                return "false";
            }
        });

        ceb.setPhraseParserParameters(phraseParams);
        ceb.setFlagsParameters(ConfigResolver.getStateFlagsParams());
        ConfigEnvironment config = ceb.build();

        ConfigMapMenuWidgetController controller = new ConfigMapMenuWidgetController(mapMenu,
                new ConfigWidgetModel(doc.getDocumentElement(), config));
        controller.refreshProperties();

        List<ConfigWidgetController> bcs = controller.getButtonControllers();
        Assert.assertEquals(bcs.size(), 4);

        IMapMenuButtonWidget b0 = (IMapMenuButtonWidget) bcs.get(0).getWidget();
        IMapMenuButtonWidget b1 = (IMapMenuButtonWidget) bcs.get(1).getWidget();
        IMapMenuButtonWidget b2 = (IMapMenuButtonWidget) bcs.get(2).getWidget();
        IMapMenuButtonWidget b3 = (IMapMenuButtonWidget) bcs.get(3).getWidget();

        Assert.assertEquals(b0.getState(), 0);
        Assert.assertEquals(b1.getState(), AbstractButtonWidget.STATE_SELECTED);
        Assert.assertEquals(b2.getState(), 0);
        Assert.assertEquals(b3.getState(), AbstractButtonWidget.STATE_SELECTED);
    }
}

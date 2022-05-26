package gov.tak.platform.widgets.factory.xml;

import gov.tak.api.widgets.IMapMenuWidget;
import gov.tak.platform.utils.XMLUtils;
import gov.tak.platform.widgets.factory.MapMenuWidgetFactory;
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

@RunWith(MockitoJUnitRunner.class)
public class XmlMapMenuWidgetSpiTest {

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
    public void testBasicXmlMenu() throws ParserConfigurationException, IOException, SAXException {

        XmlMapMenuWidgetSpi spi = new XmlMapMenuWidgetSpi();
        MapMenuWidgetFactory.registerSpi(spi, Node.class, 0);

        DocumentBuilderFactory dbf = XMLUtils.getDocumentBuilderFactory();

        final DocumentBuilder db = dbf.newDocumentBuilder();
        Document doc = db.parse(new ByteArrayInputStream(MENU_SRC.getBytes()));
        IMapMenuWidget mapMenu = MapMenuWidgetFactory.create(doc.getDocumentElement());

        Assert.assertEquals(mapMenu.getInnerRadius(), 65.0f, 0.0f);
        Assert.assertEquals((Float)mapMenu.getPropertyValue(IMapMenuWidget.PROPERTY_BUTTON_SPAN.getName()), 90.0f, 0.0f);
        Assert.assertEquals((Float)mapMenu.getPropertyValue(IMapMenuWidget.PROPERTY_BUTTON_WIDTH.getName()), 90.0f, 0.0f);

        Assert.assertEquals(mapMenu.getChildWidgetCount(), 4);
    }
}

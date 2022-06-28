
package com.atakmap.coremap.xml;

import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.TransformerFactory;

public class XMLUtils {

    /**
     * Produce a TransformerFactory with the appropriate dangerous
     * features turned such as XXE Injection
     * @return a safe Transformer Factory
     */
    public static TransformerFactory getTransformerFactory() {
        TransformerFactory tf = TransformerFactory.newInstance();
        try {
            tf.setFeature(
                    "http://apache.org/xml/features/disallow-doctype-decl",
                    true);
        } catch (Exception ignored) {
        }
        try {
            tf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING,
                    true);
        } catch (Exception ignored) {
        }
        return tf;
    }

    /**
     * Produce a DocumentBuilderFactory with the appropriate dangerous
     * features turned such as XXE Injection
     * @return a safe DocumentBuilder Factory
     */
    public static DocumentBuilderFactory getDocumenBuilderFactory() {
        DocumentBuilderFactory docFactory = DocumentBuilderFactory
                .newInstance();
        try {
            docFactory.setFeature(
                    "http://apache.org/xml/features/disallow-doctype-decl",
                    true);
        } catch (Exception ignored) {
        }
        try {
            docFactory.setFeature(
                    XMLConstants.FEATURE_SECURE_PROCESSING, true);
        } catch (Exception ignored) {
        }

        return docFactory;
    }

    /**
     * Produce a pull parser with the approprate dangerous features
     * turned off such as expansion bomds like the billion laughs.
     * @return a XmlPullParser with FEATURE_PROCESS_DOCDECL, FEATURE_PROCESS_NAMESPACES 
     * turned off and Xml.FEATURE_RELAXED turned on.
     */
    public static XmlPullParser getXmlPullParser() throws XmlPullParserException {
        XmlPullParser parser = null;
        parser = Xml.newPullParser();
        if (parser == null)
            return null;

        try {
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL, false);
        } catch (Exception ignored2) { }
        try {
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
        } catch (Exception ignored1) { }
        try {
            parser.setFeature(Xml.FEATURE_RELAXED, true);
        } catch (Exception ignored) { }

        return parser;
    }

}

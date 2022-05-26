
package com.atakmap.coremap.xml;

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

}

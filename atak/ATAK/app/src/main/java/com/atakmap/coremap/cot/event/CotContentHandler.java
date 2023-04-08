
package com.atakmap.coremap.cot.event;

import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.time.CoordinatedTime;

import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.InputSource;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;

import java.io.StringReader;
import java.util.Stack;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

class CotContentHandler implements ContentHandler {

    public static final String TAG = "CotContentHandler";

    private final Stack<CotDetail> _detailStack = new Stack<>();
    private boolean _finishedDetail;
    private final StringBuilder _innerTextBuilder = new StringBuilder();
    private CotEvent editor;
    private org.xml.sax.XMLReader reader;

    /**
     * Makes use of internal buffers and will not be able to be threaded.
     */
    synchronized CotEvent parseXML(final String xml) {
        _detailStack.clear();
        _finishedDetail = false;
        _innerTextBuilder.setLength(0);

        editor = new CotEvent();
        try {
            // Note this replace the original XmlParse so that the factory and content
            // handler can be reused for new strings.

            //Xml.parse(xml, this);

            if (reader == null) {
                SAXParserFactory saxParserFactory = SAXParserFactory
                        .newInstance();

                try {
                    saxParserFactory
                            .setFeature(
                                    "http://xml.org/sax/features/external-parameter-entities",
                                    false);
                } catch (ParserConfigurationException ignored) {
                }

                try {
                    saxParserFactory
                            .setFeature(
                                    "http://xml.org/sax/features/external-general-entities",
                                    false);
                } catch (ParserConfigurationException ignored) {
                }

                SAXParser newSAXParser = saxParserFactory.newSAXParser();
                reader = newSAXParser.getXMLReader();

                reader.setContentHandler(this);
            }

            reader.parse(new InputSource(new StringReader(xml)));

        } catch (Exception e) {
            Log.v(TAG, "Bad message encountered: " + xml);
            Log.e(TAG, "error: ", e);
        }

        return editor;

    }

    @Override
    public void characters(final char[] ch, final int start, final int length) {
        if (_detailStack.size() > 0) {
            _innerTextBuilder.append(ch, start, length);
        }
    }

    @Override
    public void endDocument() {

    }

    @Override
    public void endElement(final String uri, final String localName,
            final String qName) {
        if (_detailStack.size() > 0) {
            CotDetail detail = _detailStack.pop();
            if (_innerTextBuilder.length() > 0) {
                detail.setInnerText(_innerTextBuilder.toString().trim());
                _innerTextBuilder.setLength(0);
            }
            if (_detailStack.size() == 0) {
                _finishedDetail = true;
            }
        }
    }

    @Override
    public void endPrefixMapping(final String prefix) {
    }

    @Override
    public void ignorableWhitespace(final char[] ch, final int start,
            final int length) {

    }

    @Override
    public void processingInstruction(final String target, final String data) {
    }

    @Override
    public void setDocumentLocator(final Locator locator) {
    }

    @Override
    public void skippedEntity(final String name) {
    }

    @Override
    public void startDocument() {
    }

    @Override
    public void startElement(final String uri, final String localName,
            final String qName,
            final Attributes attrs)
            throws SAXException {
        try {
            if (localName.equals("event") && _detailStack.size() == 0) {
                editor.setType(_stringOrThrow(attrs, "type",
                        "event: missing type"));
                editor.setVersion(_stringOrFallback(attrs, "version", "2.0"));
                editor.setUID(
                        _stringOrThrow(attrs, "uid", "event: missing uid"));
                editor.setTime(_timeOrDefault(attrs, "time",
                        "event: illegal or missing time"));
                editor.setStart(_timeOrDefault(attrs, "start",
                        "event: illegal or missing start"));
                editor.setStale(_timeOrDefault(attrs, "stale",
                        "event: illegal or missing stale"));
                editor.setHow(_stringOrFallback(attrs, "how", ""));
                editor.setOpex(_stringOrFallback(attrs, "opex", null));
                editor.setQos(_stringOrFallback(attrs, "qos", null));
                editor.setAccess(_stringOrFallback(attrs, "access", null));
                // these might not be clear in the case that a recycled event was passed in
                editor.setPoint(CotPoint.ZERO);
                editor.setDetail(null);
            } else if (localName.equals("point") && _detailStack.size() == 0) {
                // if (_parsedPoint || _eventEditor == null) {
                // throw new CotIllegalException("illegal point tag");
                // }
                double lat = _doubleOrThrow(attrs, "lat",
                        "point: illegal or missing lat");
                double lon = _doubleOrThrow(attrs, "lon",
                        "point: illegal or missing lon");
                double hae = _doubleOrFallback(attrs, "hae",
                        CotPoint.UNKNOWN);
                double le = _doubleOrFallback(attrs, "le",
                        CotPoint.UNKNOWN);
                double ce = _doubleOrFallback(attrs, "ce",
                        CotPoint.UNKNOWN);

                // some systems are starting to spit out Double.NaN incorrectly 
                // for those values that are unknown correctly parse them

                if (Double.isNaN(hae))
                    hae = CotPoint.UNKNOWN;
                if (Double.isNaN(le))
                    le = CotPoint.UNKNOWN;
                if (Double.isNaN(ce))
                    ce = CotPoint.UNKNOWN;

                editor.setPoint(new CotPoint(lat, lon, hae, ce, le));

            } else if (localName.equals("detail") && _detailStack.size() == 0
                    && !_finishedDetail) {
                CotDetail detail = _pushDetail("detail", attrs);
                editor.setDetail(detail);
            } else if (_detailStack.size() > 0) {
                // inside of detail tag just get DOM'ed out
                _pushDetail(localName, attrs);
            }
        } catch (CotIllegalException e) {
            throw new SAXException(e.toString());
        }

    }

    @Override
    public void startPrefixMapping(final String prefix, final String uri) {

    }

    private CotDetail _pushDetail(final String name, final Attributes attrs) {
        CotDetail detail = new CotDetail();

        // set name and attributes
        detail.setElementName(name);
        for (int i = 0; i < attrs.getLength(); ++i) {
            String attrName = attrs.getLocalName(i);
            String attrValue = attrs.getValue(i);
            detail.setAttribute(attrName, attrValue);
        }

        // add it to (any) parent
        if (_detailStack.size() > 0) {
            CotDetail parentDetail = _detailStack.peek();
            parentDetail.addChild(detail);
        }

        // push it on the stack
        _detailStack.push(detail);

        return detail;
    }

    private static CoordinatedTime _timeOrDefault(final Attributes attrs,
            final String name, final String msg) {
        try {
            return CoordinatedTime.fromCot(attrs.getValue(name));
        } catch (Exception ex) {
            Log.e(TAG, "_timeOrDefault" + msg);
            return new CoordinatedTime();
        }
    }

    private static String _stringOrThrow(final Attributes attrs,
            final String name,
            final String msg)
            throws CotIllegalException {
        String value = attrs.getValue(name);
        if (value == null) {
            throw new CotIllegalException(msg);
        }
        return value;
    }

    private static String _stringOrFallback(final Attributes attrs,
            final String name,
            final String fallback) {
        String value = attrs.getValue(name);
        if (value == null) {
            value = fallback;
        }
        return value;
    }

    private static double _doubleOrThrow(final Attributes attrs,
            final String name,
            final String msg)
            throws CotIllegalException {
        try {
            return Double.parseDouble(attrs.getValue(name));
        } catch (Exception ex) {
            throw new CotIllegalException(msg);
        }
    }

    private static double _doubleOrFallback(final Attributes attrs,
            final String name,
            final double fallback) {
        try {
            return Double.parseDouble(attrs.getValue(name));
        } catch (Exception ex) {
            return fallback;
        }
    }
}

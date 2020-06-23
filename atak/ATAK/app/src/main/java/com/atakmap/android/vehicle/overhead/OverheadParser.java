
package com.atakmap.android.vehicle.overhead;

import android.content.Context;

import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;
import android.util.Xml;

import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Parse objects XML file into overhead images
 */

public class OverheadParser implements ContentHandler {

    private static final String TAG = "OverheadParser";
    private static final String EL_OBJECT = "Object";
    private static final String EL_TITLE = "Title";
    private static final String EL_GROUP = "Group";
    private static final String EL_WIDTH = "XInFeet";
    private static final String EL_LENGTH = "YInFeet";
    private static final String EL_HEIGHT = "ZInFeet";
    private static final String EL_ICON = "IconPNG";

    public static final String XML_NAME = "vehicles/overhead_types.xml";

    private final Context _context;
    private String _element;
    private String _title, _group, _width, _length, _height, _icon;
    private static final Map<String, OverheadImage> _imageCache = new HashMap<>();

    public OverheadParser(Context context) {
        _context = context;
        InputStream is = null;
        try {
            is = _context.getAssets().open(XML_NAME);
            Xml.parse(is, Xml.Encoding.UTF_8, this);
        } catch (IOException e) {
            Log.e(TAG, "Failed to read assets/" + XML_NAME);
        } catch (SAXException e) {
            Log.e(TAG, "Failed to parse XML assets/" + XML_NAME);
        } finally {
            try {
                if (is != null)
                    is.close();
            } catch (IOException ignored) {
            }
        }
    }

    public static OverheadParser init(Context context) {
        return new OverheadParser(context);
    }

    public static Map<String, OverheadImage> getImages() {
        return _imageCache;
    }

    public static List<OverheadImage> getImages(String group) {
        List<OverheadImage> ret = new ArrayList<>();
        for (OverheadImage img : _imageCache.values()) {
            if (FileSystemUtils.isEquals(img.group, group))
                ret.add(img);
        }
        Collections.sort(ret, OverheadImage.NAME_COMPARATOR);
        return ret;
    }

    public static OverheadImage getImageByName(String name) {
        return _imageCache.get(name);
    }

    public static String[] getGroups() {
        Set<String> ret = new HashSet<>();
        for (OverheadImage img : _imageCache.values()) {
            if (!FileSystemUtils.isEmpty(img.group))
                ret.add(img.group);
        }
        return ret.toArray(new String[0]);
    }

    @Override
    public void setDocumentLocator(Locator locator) {
    }

    @Override
    public void startDocument() {
        Log.d(TAG, "Began reading " + XML_NAME);
    }

    @Override
    public void endDocument() {
        Log.d(TAG, "Finished reading " + XML_NAME);
    }

    @Override
    public void startPrefixMapping(String prefix, String uri) {
    }

    @Override
    public void endPrefixMapping(String prefix) {
    }

    @Override
    public void startElement(String uri, String localName, String qName,
            Attributes atts) {
        _element = localName;
    }

    @Override
    public void endElement(String uri, String localName, String qName) {
        if (localName != null && localName.equals(EL_OBJECT) && _title != null
                && _group != null && _width != null && _length != null
                && _icon != null) {

            double widthFt = 0, lengthFt = 0, heightFt = 0;
            try {
                widthFt = Double.parseDouble(_width);
            } catch (Exception e) {
                Log.e(TAG, "Failed to parse width for " + _title, e);
            }
            try {
                lengthFt = Double.parseDouble(_length);
            } catch (Exception e) {
                Log.e(TAG, "Failed to parse length for " + _title, e);
            }
            try {
                if (_height != null)
                    heightFt = Double.parseDouble(_height);
            } catch (Exception e) {
                Log.e(TAG, "Failed to parse length for " + _title, e);
            }
            if (widthFt > 0 && lengthFt > 0)
                _imageCache.put(_title, new OverheadImage(_context, _title,
                        _group, _icon, widthFt, lengthFt, heightFt));
            _title = _width = _length = _height = _icon = null;
        }
        _element = null;
    }

    @Override
    public void characters(char[] ch, int start, int length) {
        if (_element != null) {
            String content = new String(ch, start, length);
            switch (_element) {
                case EL_TITLE:
                    _title = content;
                    break;
                case EL_GROUP:
                    _group = content;
                    break;
                case EL_WIDTH:
                    _width = content;
                    break;
                case EL_LENGTH:
                    _length = content;
                    break;
                case EL_HEIGHT:
                    _height = content;
                    break;
                case EL_ICON:
                    _icon = content;
                    break;
            }
        }
    }

    @Override
    public void ignorableWhitespace(char[] ch, int start, int length) {
    }

    @Override
    public void processingInstruction(String target, String data) {
    }

    @Override
    public void skippedEntity(String name) {
    }
}

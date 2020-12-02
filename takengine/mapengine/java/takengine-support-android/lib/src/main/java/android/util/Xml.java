package android.util;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

public final class Xml {
    public final static String FEATURE_RELAXED = "relaxed";

    public static XmlPullParser newPullParser() throws XmlPullParserException {
        XmlPullParserFactory f = XmlPullParserFactory.newInstance();
        f.setNamespaceAware(true);
        XmlPullParser p = f.newPullParser();
        if(p == null)
            return null;
        return new AndroidXmlPullParser(p);
    }
}

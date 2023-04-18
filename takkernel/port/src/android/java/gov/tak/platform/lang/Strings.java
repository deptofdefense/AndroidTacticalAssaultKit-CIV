package gov.tak.platform.lang;

import java.io.IOException;

import org.apache.commons.lang.StringEscapeUtils;

public final class Strings extends StringsBase {
    public static String unescapeHtml(String s) {
        try {
            return StringEscapeUtils.unescapeHtml(s);
        } catch(IOException e) {
            // XXX - not sure about failover here???
            return s;
        }
    }
}

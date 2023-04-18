package gov.tak.platform.lang;

import org.apache.commons.text.StringEscapeUtils;

public final class Strings extends StringsBase {
    public static String unescapeHtml(String s) {
        return StringEscapeUtils.unescapeHtml4(s);
    }
}

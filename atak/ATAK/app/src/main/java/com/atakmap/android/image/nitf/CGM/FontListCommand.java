
package com.atakmap.android.image.nitf.CGM;

import android.graphics.Typeface;

import java.io.DataInput;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import com.atakmap.coremap.locale.LocaleUtil;

/**
 *
 */
public class FontListCommand extends Command {
    String[] fontNames;
    TypefaceHelper[] fonts;

    static Map<String, TypefaceHelper> fontMapping;

    final static int DEFAULT_FONT_SIZE = 32;

    static {
        fontMapping = new HashMap<>();
        fontMapping.put(
                "times-roman",
                new TypefaceHelper(Typeface.create(Typeface.SERIF,
                        Typeface.NORMAL), false));
        fontMapping.put(
                "times-bold",
                new TypefaceHelper(Typeface.create(Typeface.SERIF,
                        Typeface.BOLD), false));
        fontMapping.put(
                "times-italic",
                new TypefaceHelper(Typeface.create(Typeface.SERIF,
                        Typeface.ITALIC), false));
        fontMapping.put(
                "times-bolditalic",
                new TypefaceHelper(Typeface.create(Typeface.SERIF,
                        Typeface.BOLD_ITALIC), false));
        fontMapping.put(
                "times-bold-italic",
                new TypefaceHelper(Typeface.create(Typeface.SERIF,
                        Typeface.BOLD_ITALIC), false));

        fontMapping.put(
                "helvetica",
                new TypefaceHelper(Typeface.create(Typeface.SANS_SERIF,
                        Typeface.NORMAL), false));
        fontMapping.put(
                "helvetica-bold",
                new TypefaceHelper(Typeface.create(Typeface.SANS_SERIF,
                        Typeface.BOLD), false));
        fontMapping.put(
                "helvetica-oblique",
                new TypefaceHelper(Typeface.create(Typeface.SANS_SERIF,
                        Typeface.ITALIC), false));
        fontMapping.put(
                "helvetica-boldoblique",
                new TypefaceHelper(Typeface.create(Typeface.SANS_SERIF,
                        Typeface.BOLD_ITALIC), false));
        fontMapping.put(
                "helvetica-bold-oblique",
                new TypefaceHelper(Typeface.create(Typeface.SANS_SERIF,
                        Typeface.BOLD_ITALIC), false));

        fontMapping.put(
                "courier",
                new TypefaceHelper(Typeface.create(Typeface.MONOSPACE,
                        Typeface.NORMAL), false));
        fontMapping.put(
                "courier-bold",
                new TypefaceHelper(Typeface.create(Typeface.MONOSPACE,
                        Typeface.BOLD), false));
        fontMapping.put(
                "courier-italic",
                new TypefaceHelper(Typeface.create(Typeface.MONOSPACE,
                        Typeface.ITALIC), false));
        fontMapping.put(
                "courier-oblique",
                new TypefaceHelper(Typeface.create(Typeface.MONOSPACE,
                        Typeface.ITALIC), false));
        fontMapping.put(
                "courier-bolditalic",
                new TypefaceHelper(Typeface.create(Typeface.MONOSPACE,
                        Typeface.BOLD_ITALIC), false));
        fontMapping.put(
                "courier-boldoblique",
                new TypefaceHelper(Typeface.create(Typeface.MONOSPACE,
                        Typeface.BOLD_ITALIC), false));
        fontMapping.put(
                "courier-bold-italic",
                new TypefaceHelper(Typeface.create(Typeface.MONOSPACE,
                        Typeface.BOLD_ITALIC), false));
        fontMapping.put(
                "courier-bold-oblique",
                new TypefaceHelper(Typeface.create(Typeface.MONOSPACE,
                        Typeface.BOLD_ITALIC), false));

        // this has to be a font that is able to display all characters, typically a unicode font
        fontMapping.put(
                "symbol",
                new TypefaceHelper(Typeface.create(Typeface.SERIF,
                        Typeface.NORMAL), true));
    }

    FontListCommand(int ec, int eid, int l, DataInput in)
            throws IOException {
        super(ec, eid, l, in);
        int count = 0, i = 0;
        while (i < this.args.length) {
            count++;
            i += this.args[i] + 1;
        }
        this.fontNames = new String[count];
        count = 0;
        i = 0;
        while (i < this.args.length) {
            char[] a = new char[this.args[i]];
            for (int j = 0; j < this.args[i]; j++)
                a[j] = (char) this.args[i + j + 1];
            this.fontNames[count] = new String(a);
            count++;
            i += this.args[i] + 1;
        }

        this.fonts = new TypefaceHelper[this.fontNames.length];
        i = 0;
        for (String fontName : this.fontNames) {
            TypefaceHelper mappedFont = fontMapping
                    .get(normalizeFontName(fontName));
            if (mappedFont != null) {
                this.fonts[i++] = mappedFont;
            } else {
                Typeface decodedFont = Typeface.create(fontName,
                        Typeface.NORMAL);
                // XXX: assume non symbolic encoding, is that right?
                this.fonts[i++] = new TypefaceHelper(decodedFont, false);
            }
        }
    }

    private String normalizeFontName(String fontName) {
        return fontName.toLowerCase(LocaleUtil.getCurrent()).replace('_', '-');
    }

    @Override
    public String toString() {

        StringBuilder sb = new StringBuilder("FontList ");
        for (int i = 0; i < this.fontNames.length - 1; i++)
            sb.append(this.fontNames[i]).append(", ");
        sb.append(this.fontNames[this.fontNames.length - 1]);
        return sb.toString();
    }

    public static class TypefaceHelper {
        public Typeface font;
        public boolean useSymbolEncoding;

        public TypefaceHelper(Typeface font, boolean useSymbolEncoding) {
            this.font = font;
            this.useSymbolEncoding = useSymbolEncoding;
        }
    }
}


package com.atakmap.coremap.locale;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import com.atakmap.coremap.log.Log;

public class LocaleUtil {

    static public final String TAG = "LocaleUtil";

    // RTL detection adapted from https://stackoverflow.com/questions/18996183/identifyng-rtl-language-in-android
    static private final Set<String> RTL_LOCALES = new HashSet<>();
    static {
        RTL_LOCALES.add("ar"); // Arabic
        RTL_LOCALES.add("dv"); // Divehi
        RTL_LOCALES.add("fa"); // Persian (Farsi)
        RTL_LOCALES.add("ha"); // Hausa
        RTL_LOCALES.add("he"); // Hebrew
        RTL_LOCALES.add("iw"); // Hebrew (old code)
        RTL_LOCALES.add("ji"); // Yiddish (old code)
        RTL_LOCALES.add("ps"); // Pashto, Pushto
        RTL_LOCALES.add("ur"); // Urdu
        RTL_LOCALES.add("yi"); // Yiddish
    }

    static private Locale locale = Locale.US;
    static public Locale US = Locale.US;
    static private boolean rtl = RTL_LOCALES.contains(locale.getLanguage());

    /**
     * Obtain the current locale for presentation purposes.   Do not force to US
     * @return Locale the current locale
     */
    static public Locale getCurrent() {
        return locale;
    }

    /**
     * Sets the locale for presentation purposes.
     * @param _locale The locale can be one of the standard java locales
     */
    static public void setLocale(final Locale _locale) {
        Log.d(TAG, "setting the locale to: " + _locale);
        locale = _locale;
        rtl = (locale != null) && RTL_LOCALES.contains(locale.getLanguage());
    }

    /**
     * Returns a flag indicating whether or not the current locale displays
     * characters right-to-left.
     * 
     * @return  <code>true</code> if the current locale displays characters
     *          right-to-left, <code>false</code> if they are displayed
     *          left-to-right.
     */
    static public boolean isRTL() {
        return rtl;
    }

    /**
     * Provided a non US locale, provide for the ability to parse the string and 
     * and force the numbers from the current locale into numbers that would be 
     * expected during text input.
     * Will attempt to convert over as much of the string as possible.   If not numeric 
     * text is included it will not be touched (with the exception of the comma at the moment)
     * Will do a better job detecting the proper comma placement in the future. (digit before and 
     * digit after.
     */
    static public String getNaturalNumber(final String numbers) {
        if (numbers == null)
            return null;

        if (locale.getLanguage().equals("ar")) {
            char[] charSequence = numbers.toCharArray();
            for (int i = 0; i < charSequence.length; ++i) {
                int ch = (int) charSequence[i];
                // turn chars 1632 to 1641 to ASCII 48 to 57
                if ((ch >= 1632 && ch <= 1641))
                    charSequence[i] = (char) (ch - 1632 + 48);
                else if (ch == 1643)
                    charSequence[i] = '.';
                else if (ch == 8207) {
                    charSequence[i] = ' ';
                }
                //else {
                //Log.d(TAG, "unidentified: " + charSequence[i] + " " + ch);       
                //}
            }
            return String.valueOf(charSequence).trim();
        } else {
            return numbers;
        }

    }

    /**
     * Obtains a Locale.US specific decimal formatter.
     * @param format the format
     */
    public static DecimalFormat getDecimalFormat(final String format) {
        final NumberFormat nf = NumberFormat.getNumberInstance(Locale.US);
        if (nf instanceof DecimalFormat) {
            DecimalFormat df = (DecimalFormat) nf;
            df.applyPattern(format);
            return df;
        } else {
            return new DecimalFormat(format);
        }
    }

}

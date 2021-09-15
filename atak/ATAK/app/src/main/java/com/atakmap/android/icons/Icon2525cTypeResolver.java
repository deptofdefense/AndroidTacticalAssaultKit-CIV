
package com.atakmap.android.icons;

import android.content.Context;

import com.atakmap.android.maps.MapView;
import com.atakmap.app.R;

import com.atakmap.coremap.locale.LocaleUtil;

public class Icon2525cTypeResolver {

    public static String mil2525cFromCotType(String type) {
        if (type != null && type.indexOf("a") == 0 && type.length() > 2) {
            String s2525C;

            switch (type.charAt(2)) {
                case 'f':
                case 'a':
                    s2525C = "sf";
                    break;
                case 'n':
                    s2525C = "sn";
                    break;
                case 's':
                case 'j':
                case 'k':
                case 'h':
                    s2525C = "sh";
                    break;
                case 'u':
                default:
                    s2525C = "su";
                    break;
            }
            StringBuilder s2525CSB = new StringBuilder(s2525C);
            for (int x = 4; x < type.length(); x += 2) {
                char[] t = {
                        type.charAt(x)
                };
                String s = new String(t);
                s2525CSB.append(s.toLowerCase(LocaleUtil.getCurrent()));
                if (x == 4) {
                    s2525CSB.append("p");
                }
            }
            for (int x = s2525CSB.length(); x < 15; x++) {
                if (x == 10 && s2525CSB.charAt(2) == 'g'
                        && s2525CSB.charAt(4) == 'i') {
                    s2525CSB.append("h");
                } else {
                    s2525CSB.append("-");
                }
            }
            return s2525CSB.toString();
        }

        return "";
    }

    /**
     * Currently a direct copy of what was used for hostiles
     * No attempt has been made to change it.
     */
    public static String getShortHumanName(final String type,
            final Context context) {
        String targ = context.getString(R.string.not_recognized);
        if (type.startsWith("a-h-A")) {
            targ = context.getResources().getString(R.string.aircraft);
        } else if (type.startsWith("a-h-G-U-C-F")) {
            targ = context.getResources()
                    .getString(R.string.artillery);
        } else if (type.startsWith("a-h-G-I")) {
            targ = context.getResources().getString(R.string.building);
        } else if (type.startsWith("a-h-G-E-X-M")) {
            targ = context.getResources().getString(R.string.mine);
        } else if (type.startsWith("a-h-S")) {
            targ = context.getResources().getString(R.string.ship);
        } else if (type.startsWith("a-h-G-U-C-I-d")) {
            targ = context.getResources().getString(R.string.sniper);
        } else if (type.startsWith("a-h-G-E-V-A-T")) {
            targ = context.getResources().getString(R.string.tank);
        } else if (type.startsWith("a-h-G-U-C-I")) {
            targ = context.getResources().getString(R.string.troops);
        } else if (type.startsWith("a-h-G-E-V")) {
            targ = context.getResources().getString(R.string.cot_type_vehicle);
        } else {
            targ = context.getResources().getString(R.string.ground);
        }
        return targ;
    }

    /**
     * Given a 2525 type, this will return a human friendly name.  
     * @param type the COT type.
     * @param context the context to use.
     */
    public static String getHumanName(final String type,
            final Context context) {

        // get the CoT name from some library
        String _25b25bName = Icon2525cTypeResolver.mil2525cFromCotType(type);

        String targ = context
                .getString(R.string.not_recognized);
        boolean includeType = true;
        if (_25b25bName != null && _25b25bName.length() > 3) {
            includeType = false; // Assume we found it (we fix in a few lines)

            // fortify hates replaceFirst - (the later is much easier to read)
            // _25b25bName = _25b25bName.replaceFirst(_25b25bName .subSequence(1, 2).toString(), "_") + ".png";
            _25b25bName = _25b25bName.charAt(0) + "_"
                    + _25b25bName.substring(2) + ".png";

            String description = CotDescriptions.GetDescription(
                    context.getAssets(),
                    _25b25bName);

            /**
             * Ugly, I know, but it works. First we need to pull the most defined CoT type
             * available, then step up one level to pull the parent's string (if available) and
             * concat it. AS.
             */

            final String UNKNOWN_TYPE = MapView.getMapView().getContext()
                    .getString(R.string.not_recognized);
            while (description.equals(UNKNOWN_TYPE)) { // Check to see if we're defined
                includeType = true; // We're not - display type on details screen
                if (_25b25bName.indexOf("-") > 2) {
                    _25b25bName = _25b25bName
                            .substring(0, _25b25bName.indexOf("-") - 1) // and move
                            // up a
                            // level if
                            // we're
                            // not
                            .concat("-")
                            .concat(_25b25bName.substring(_25b25bName
                                    .indexOf("-")));
                    description = CotDescriptions.GetDescription(
                            context.getAssets(),
                            _25b25bName);
                } else
                    break;

            }
            String previousLevel = CotDescriptions.GetDescription(
                    context.getAssets(), // now get the parent
                    _25b25bName
                            .substring(0, _25b25bName.indexOf("-") - 1)
                            .concat("-")
                            .concat(_25b25bName.substring(_25b25bName
                                    .indexOf("-"))));

            if (previousLevel.equals(UNKNOWN_TYPE))
                previousLevel = "";
            else
                previousLevel += " > ";
            targ = previousLevel
                    + CotDescriptions.GetDescription(context.getAssets(),
                            _25b25bName);
        }

        return targ;
    }

}

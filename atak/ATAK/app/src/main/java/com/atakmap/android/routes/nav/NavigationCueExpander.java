
package com.atakmap.android.routes.nav;

import java.util.HashMap;
import java.util.StringTokenizer;

/**
 * Utility class for expanding common abbreviations found in navigational cues.
 */
public class NavigationCueExpander {

    private static final HashMap<String, String> expansions = new HashMap<>();

    static {
        // https://pe.usps.com/text/pub28/28apc_002.htm
        expansions.put("St", "Street");
        expansions.put("Rd", "Road");
        expansions.put("Blvd", "Boulevard");
        expansions.put("Ave", "Avenue");
        expansions.put("Ln", "Lane");
        expansions.put("Cir", "Circle");
        expansions.put("Dr", "Drive");
        expansions.put("Ctr", "Center");
        expansions.put("Pkwy", "Parkway");
        expansions.put("Rte", "Route");
        expansions.put("N", "North");
        expansions.put("S", "South");
        expansions.put("E", "East");
        expansions.put("W", "West");

        expansions.put("AL", "Alabama");
        expansions.put("AK", "Alaska");
        expansions.put("AS", "American Samoa");
        expansions.put("AZ", "Arizona");
        expansions.put("AR", "Arkansas");
        expansions.put("CA", "California");
        expansions.put("CO", "Colorado");
        expansions.put("CT", "Connecticut");
        expansions.put("DE", "Delaware");
        expansions.put("DC", "District of Columbia");
        expansions.put("FM", "Federated States of Micronesia");
        expansions.put("FL", "Florida");
        expansions.put("GA", "Georgia");
        expansions.put("GU", "Guam");
        expansions.put("HI", "Hawaii");
        expansions.put("ID", "Idaho");
        expansions.put("IL", "Illinois");
        expansions.put("IN", "Indiana");
        expansions.put("IA", "Iowa");
        expansions.put("KS", "Kansas");
        expansions.put("KY", "Kentucky");
        expansions.put("LA", "Louisiana");
        expansions.put("ME", "Maine");
        expansions.put("MH", "Marshall Islands");
        expansions.put("MD", "Maryland");
        expansions.put("MA", "Massachusetts");
        expansions.put("MI", "Michigan");
        expansions.put("MN", "Minnesota");
        expansions.put("MS", "Mississippi");
        expansions.put("MO", "Missouri");
        expansions.put("MT", "Montana");
        expansions.put("NE", "Nebraska");
        expansions.put("NV", "Nevada");
        expansions.put("NH", "New Hampshire");
        expansions.put("NJ", "New Jersey");
        expansions.put("NM", "New Mexico");
        expansions.put("NY", "New York");
        expansions.put("NC", "North Carolina");
        expansions.put("ND", "North Dakota");
        expansions.put("MP", "Northern Mariana Islands");
        expansions.put("OH", "Ohio");
        expansions.put("OK", "Oklahoma");
        expansions.put("OR", "Oregon");
        expansions.put("PW", "Palau");
        expansions.put("PA", "Pennsylvania");
        expansions.put("PR", "Puerto Rico");
        expansions.put("RI", "Rhode Island");
        expansions.put("SC", "South Carolina");
        expansions.put("SD", "South Dakota");
        expansions.put("TN", "Tennessee");
        expansions.put("TX", "Texas");
        expansions.put("UT", "Utah");
        expansions.put("VT", "Vermont");
        expansions.put("VI", "Virgin Islands");
        expansions.put("VA", "Virginia");
        expansions.put("WA", "Washington");
        expansions.put("WV", "West Virginia");
        expansions.put("WI", "Wisconsin");
        expansions.put("WY", "Wyoming");
        expansions.put("US", "U S");

        //Auto-generated acronyms
        expansions.put("SP", "Start Point");
        expansions.put("VDO", "You will arrive at your destination.");
    }

    /**
     * Provides for common expansions of direction names, (St is street, etc)
     *
     * @param cue the unexpanded name
     */
    public static String expand(String cue) {

        cue = cue.replaceAll("-", " ");
        cue = cue.replaceAll("/", " ");

        StringTokenizer st = new StringTokenizer(cue);
        StringBuilder sb = new StringBuilder();

        while (st.hasMoreTokens()) {
            String token = st.nextToken();

            // Switch to the expanded version of the token if there is one
            if (expansions.containsKey(token)) {
                token = expansions.get(token);
            }

            // Add the possibly expanded token back to our output
            sb.append(token);

            // If there are more tokens coming our way, add a space
            if (st.hasMoreTokens()) {
                sb.append(" ");
            }
        }

        return sb.toString();
    }

}

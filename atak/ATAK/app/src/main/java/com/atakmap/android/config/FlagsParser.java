
package com.atakmap.android.config;

import org.w3c.dom.Node;

import java.util.HashMap;
import java.util.Map;

public class FlagsParser {

    public static class Parameters {
        public Parameters() {
            _values = new HashMap<>();
        }

        public Parameters(Parameters parms) {
            _values = new HashMap<>(parms._values);
        }

        public void setFlagBits(String flagName, int flagBits) {
            _values.put(flagName, flagBits);
        }

        int getFlagBits(String flagName) {
            int r = 0;
            Integer v = _values.get(flagName);
            if (v != null) {
                r = v;
            }
            return r;
        }

        private final Map<String, Integer> _values;
    }

    public static int parseFlagsElem(Parameters parms, Node elemNode) {
        int r = 0;
        if (elemNode != null) {
            r = parseFlagsText(parms, elemNode.getFirstChild());
        }
        return r;
    }

    public static int parseFlagsText(Parameters parms, Node textNode) {
        int r = 0;
        if (textNode != null) {
            r = parseFlags(parms, textNode.getNodeValue());
        }
        return r;
    }

    public static int parseFlags(Parameters parms, String flagsString) {
        int r = 0;
        String[] parts = flagsString.split("\\|");
        for (String name : parts) {
            int i = parms.getFlagBits(name);
            r |= i;
        }
        return r;
    }

}

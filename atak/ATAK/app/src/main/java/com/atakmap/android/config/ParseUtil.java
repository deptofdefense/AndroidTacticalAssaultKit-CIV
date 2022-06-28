
package com.atakmap.android.config;

import org.w3c.dom.Node;

public class ParseUtil {

    /**
     * Walks through the nodes looking for a node of a specific name and type.
     * @param node the Node to begin the search from.
     * @param type the type to search for
     * @param name the name to search for
     * @return the Node that matches the type and name otherwise null if no node is found.
     */
    public static Node seekNodeNamed(Node node, int type, String name) {
        while (node != null
                && (node.getNodeType() != type || !node.getNodeName().equals(
                        name))) {
            node = node.getNextSibling();
        }
        return node;
    }
}

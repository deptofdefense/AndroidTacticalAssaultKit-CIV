
package com.atakmap.android.config;

import org.w3c.dom.Node;

/**
 * For creating objects of a given type from a DOM Element and a ConfigEnvironment.
 * 
 * 
 * @param <T>
 */
public interface ConfigFactory<T> {

    T createFromElem(ConfigEnvironment config, Node defNode);

}

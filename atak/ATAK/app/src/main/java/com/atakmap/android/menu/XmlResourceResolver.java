
package com.atakmap.android.menu;

import com.atakmap.android.action.MapAction;
import com.atakmap.android.config.ConfigEnvironment;

/**
 * Interface defining the resolution of assets
 * with XML definitions for <a href="#{@link}">{@link MapAction}</a>
 * and <a href="#{@link}">{@link MapMenuWidget}</a>.
 */
public interface XmlResourceResolver {

    /**
     * Resolves and fully constructs a MapAction from an
     * asset based XML definition
     * @param xmlResource path to asset describing a MapAction
     * @return a fully constructed MapAction instance
     */
    MapAction resolveAction(final String xmlResource);

    /**
     * Resolves and fully constructs a MapAction from an
     * asset based XML definition with additional handling
     * from a specific ConfigEnvironment.
     * @param xmlResource path to asset describing a MapAction
     * @param config container for parser parameters and resolvers
     * @return a fully constructed MapAction instance
     */
    MapAction resolveAction(final String xmlResource,
            final ConfigEnvironment config);

    /**
     * Resolves and fully constructs a MapMenuWidget from an
     * asset based XML definition
     * @param xmlResource path to asset describing a MapMenuWidget
     * @return a fully constructed MapMenuWidget instance
     */
    MapMenuWidget resolveMenu(final String xmlResource);

    /**
     * Resolves and fully constructs a MapMenuWidget from an
     * asset based XML definition with additional handling
     * from a specific ConfigEnvironment.
     * @param resource path to asset describing a MapMenuWidget
     * @param config container for parser parameters and resolvers
     * @return a fully constructed MapMenuWidget instance
     */
    MapMenuWidget resolveMenu(final String resource,
            final ConfigEnvironment config);
}

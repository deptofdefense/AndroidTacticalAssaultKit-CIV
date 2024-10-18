
package com.atakmap.android.contentservices;

public interface ServiceQuery {
    /**
     * The name of the Service Query
     * @return the name
     */
    String getName();

    /**
     * The priority of the service query
     * @return the priority as a number
     */
    int getPriority();

    /**
     * The listing from the url
     * @param url the url
     * @return the listing of services
     */
    ServiceListing queryServices(String url);
}

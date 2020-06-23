
package com.atakmap.android.contentservices;

public interface ServiceQuery {
    String getName();

    int getPriority();

    ServiceListing queryServices(String url);
}

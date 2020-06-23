
package com.atakmap.android.contentservices;

import java.io.IOException;
import java.io.OutputStream;

public interface Service {
    /**
     * Returns the type of the service.
     * 
     * @return  The type of the service
     */
    ServiceType getType();

    /**
     * Returns the name of the service.
     * 
     * @return  The name of the service.
     */
    String getName();

    /**
     * Returns a description of the service (may be null).
     * 
     * @return  A description of the service.
     */
    String getDescription();

    /**
     * Generates a configuration file that can be used with TAK's imagery,
     * feature or terrain infrastructures (as applicable) to import the content
     * into the TAK application.
     * 
     * @param sink  The stream where the config file content will be stored.
     * 
     * @throws IOException  If an IO error occurs.
     */
    void generateConfigFile(OutputStream sink) throws IOException;
}

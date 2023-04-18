package gov.tak.api.commons.resources;

import java.io.IOException;
import java.io.InputStream;

public interface IAssetManager {
    /**
     * Opens the named asset
     *
     * @param path  The path to the asset
     * @return  A stream to the asset specified by <code>path</code>
     *
     * @throws  {@link IOException} if an IO error occurs opening the resource, including file not
     *          found
     */
    InputStream open(String path) throws IOException;
}

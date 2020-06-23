
package com.atakmap.android.importexport;

import android.net.Uri;

import java.io.IOException;
import java.io.InputStream;

/**
 * Interface responsible for detecting the MIME type for data that represents a specified content.
 * 
 * 
 */
public interface Marshal {

    /**
     * Returns the content type of the marshaled data.
     * 
     * @return The content type of the marshaled data.
     */
    String getContentType();

    /**
     * Analyzes the specified stream to determine if it contains the content associated with this
     * <code>Marshal</code>. If the stream does contain compatible content, the MIME type of the
     * stream is returned, otherwise <code>null</code> is returned.
     * <P>
     * Only the number of bytes specified by <code>probeSize</code> may be read from the stream
     * during analysis.
     * 
     * @param inputStream A data stream
     * @param probeSize The number of bytes that may be read from the stream
     * @return The MIME type of the data if it contains the associated content, <code>null</code>
     *         otherwise.
     * @throws IOException If an IO error occurs while reading data from the stream
     */
    String marshal(InputStream inputStream, int probeSize)
            throws IOException;

    /**
     * Analyzes the data at the specified {@link Uri} to determine if it contains the content
     * associated with this <code>Marshal</code>. If the <code>Uri</code> does contain compatible
     * content, the MIME type of the <code>Uri</code> is returned, otherwise <code>null</code> is
     * returned.
     * 
     * @param uri A URI
     * @return The MIME type of the data if it contains the associated content, <code>null</code>
     *         otherwise.
     * @throws IOException If an IO error occurs while reading data from the URI
     */
    String marshal(Uri uri) throws IOException;

    /**
     * Returns the priority level of the marshal. Marshals with higher priority levels are always
     * invoked by the {@link MarshalManager} prior to marshals with lower priority levels.
     * <P>
     * The recommendation is that the priority level should increase when the content is
     * identifiable through an increase in specificity for the data stream. For example, a
     * <code>Marshal</code> that detects a stream as XML should return a priority level of
     * <code>0</code>. A <code>Marshal</code> that detects CoT should return a level of
     * <code>1</code> and a <code>Marshal</code> that detects KML should return a level of
     * <code>1</code> as both are direct derivatives of XML. A <code>Marshal</code> that detects a
     * specific type of CoT should return a level of <code>2</code> to ensure it gets invoked prior
     * to a generic CoT <code>Marshal</code>.
     * 
     * @return The priority level
     */
    int getPriorityLevel();

} // Marshal

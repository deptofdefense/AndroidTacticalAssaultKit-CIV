package com.atakmap.commoncommo;

/**
 * Class representing a NetInterface to a streaming server (TAK server).
 * StreamingNetInterfaces are unique in their "stream id" property.
 */
public class StreamingNetInterface extends NetInterface {
    /** The unique identifier of this stream interface */
    public final String streamId;

    StreamingNetInterface(long nativePtr, String streamId)
    {
        super(nativePtr);
        this.streamId = streamId;
    }
}

package com.atakmap.commoncommo;


public interface CommoLogger {
    /**
     * Various level tags used to identify the severity class
     * of messages originating from within the Commo library.
     */
    public enum Level {
        VERBOSE,
        DEBUG,
        WARNING,
        INFO,
        ERROR;
    }

    /**
     * The type of a logging message.  This additionally
     * indicates the type and form of any additional logging details.
     */
    public enum Type {
        /** General/catch-all log event - no additional detail will be given */
        GENERAL,
        /** 
         * Incoming network message parsing event - any detail given will
         * be a ParsingDetail
         */
        PARSING,
        /**
         * Network interface event - any detail given will be a NetworkDetail
         */
        NETWORK
    }
    
    /**
     * Base interface for all logging details.
     */
    public interface LoggingDetail {
    }

    /**
     * Detail about events encountered while parsing incoming network messages
     */
    public static class ParsingDetail implements LoggingDetail {
        /** The actual raw bytes of the message */
        public final byte[] messageData;

        /** Human-readable of why the parse failed */
        public final String errorDetail;

        /** RemoteEndpointId is identifier of NetworkInterface upon which the message was received or null if unknown **/
        public final String remoteEndpointId;

        ParsingDetail(byte[] messageData, String errorDetail, String remoteEndpointId){
            this.messageData = messageData;
            this.errorDetail = errorDetail;
            this.remoteEndpointId = remoteEndpointId;
        }
    }

    public static class NetworkDetail implements LoggingDetail {
        /**
         * Port number of the (non-tcp) inbound interface
         * issuing the log message
         */
        public final int port;

        NetworkDetail(int port){
            this.port = port;
        }
    }

    /**
     * Invoked upon client implementations by the Commo library to
     * log a message.  Note that this method must be prepared to be invoked
     * by multiple threads at once.
     * @param level the level/severity of the message
     * @param type the type of information being logged
     * @param message the message to log
     * @param detail additional detail - may be null
     */
    public void log(Level level, Type type, String message, LoggingDetail detail);
    
}

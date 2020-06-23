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
     * Invoked upon client implementations by the Commo library to
     * log internal messages.  Note that this method must be prepared
     * to be invoked by multiple threads at once.
     * @param level the level/severity of the message
     * @param message the message to log
     */
    public void log(Level level, String message);
    
}

package gov.tak.platform.ui;

/**
 * Interface for any class that can handle MotionEvents
 */
public interface IMotionEventHandler {
    /**
     * Invoke consumption or ignore of a MotionEvent
     *
     * @param motionEvent the MotionEvent to handle
     *
     * @return true if consumed; false if ignored
     */
    boolean handleMotionEvent(MotionEvent motionEvent);

}

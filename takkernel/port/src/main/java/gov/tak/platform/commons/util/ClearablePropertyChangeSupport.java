package gov.tak.platform.commons.util;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeListenerProxy;
import java.beans.PropertyChangeSupport;

/**
 * Extension of the standard {@link PropertyChangeSupport} to add a mechanism to remove all listeners.
 *
 * @since 0.17.0
 */
public class ClearablePropertyChangeSupport extends PropertyChangeSupport {
    /**
     * Constructs a <code>ClearablePropertyChangeSupport</code> object.
     *
     * @param sourceBean The bean to be given as the source for any events.
     */
    public ClearablePropertyChangeSupport(Object sourceBean) {
        super(sourceBean);
    }

    /**
     * Remove all currently registered property change listeners.
     */
    public void removeAllListeners() {
        for (PropertyChangeListener listener : getPropertyChangeListeners()) {
            if (listener instanceof PropertyChangeListenerProxy) {
                PropertyChangeListenerProxy proxy = (PropertyChangeListenerProxy) listener;
                removePropertyChangeListener(proxy.getPropertyName(), listener);
            } else {
                removePropertyChangeListener(listener);
            }
        }
    }
}

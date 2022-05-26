package com.atakmap.map.layer;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * A {@link Layer} that acts as a proxy for another {@link Layer}.
 *  
 * @author Developer
 */
public class ProxyLayer extends AbstractLayer {
    /**
     * Callback interface for proxy subject changes.
     * 
     * @author Developer
     */
    public interface OnProxySubjectChangedListener {
        /**
         * This method is invoked when the proxy subject has changed.
         * 
         * @param layer The layer whose subject changed
         */
        public void onProxySubjectChanged(ProxyLayer layer);
    }

    /**************************************************************************/
    
    /**
     * The subject.
     */
    protected Layer subject;
    
    /**
     * The subject changed listeners
     */
    protected Set<OnProxySubjectChangedListener> proxySubjectChangedListeners;

    /**
     * Creates a new instance with the specified name and no subject.
     * 
     * @param name  the name
     * @param name
     */
    public ProxyLayer(String name) {
        this(name, null);
    }

    /**
     * Creates a new instance with the specified name and subject.
     * 
     * @param name      The name
     * @param subject   The subject (may be <code>null</code>)
     */
    public ProxyLayer(String name, Layer subject) {
        super(name);
        
        this.subject = subject;
        this.proxySubjectChangedListeners = Collections.newSetFromMap(new IdentityHashMap<OnProxySubjectChangedListener, Boolean>());
    }
    
    /**
     * Returns the current subject
     * 
     * @return  The current subject
     */
    public synchronized Layer get() {
        return this.subject;
    }
    
    /**
     * Sets the current subject
     * 
     * @param subject   The current subject
     */
    public synchronized void set(Layer subject) {
        this.subject = subject;
        this.dispatchOnProxySubjectChangedNoSync();
    }
    
    /**
     * Adds the specified {@link OnProxySubjectChangedListener}.
     * 
     * @param l The listener
     */
    public synchronized void addOnProxySubjectChangedListener(OnProxySubjectChangedListener l) {
        this.proxySubjectChangedListeners.add(l);
    }
    
    /**
     * Removes the specified {@link OnProxySubjectChangedListener}.
     * 
     * @param l The listener
     */
    public synchronized void removeOnProxySubjectChangedListener(OnProxySubjectChangedListener l) {
        this.proxySubjectChangedListeners.remove(l);
    }
    
    /**
     * Invokes the subject changed callback on all subscribed listeners.
     * 
     * <P>This method should always be externally synchronized on
     * <code>this</code>.
     */
    protected void dispatchOnProxySubjectChangedNoSync() {
        for(OnProxySubjectChangedListener l : this.proxySubjectChangedListeners)
            l.onProxySubjectChanged(this);
    }
}


package com.atakmap.util;

/**
 * Thread-safe reference counting class with disposal hook on dereference.
 * 
 * @author Developer
 *
 * @param <T>   The referenced type
 */
public class ReferenceCount<T> {
    public final T value;
    private int count;

    /**
     * Creates a new instance that claims a reference on the value (initial
     * count is set to <code>1</code>).
     * 
     * @param value The value
     */
    public ReferenceCount(T value) {
        this(value, true);
    }

    /**
     * Creates a new instance.
     * 
     * @param value     The value
     * @param reference If <code>true</code> the count is set to <code>1</code>;
     *                  if <code>false</code> the count is set to <code>0</code>
     */
    public ReferenceCount(T value, boolean reference) {
        this.value = value;
        this.count = reference ? 1 : 0;
    }

    /**
     * Returns a flag indicating whether or not the value is currently
     * referenced.
     * 
     * @return  <code>true</code> if the reference count is greater than
     *          <code>zero</code>, <code>false</code> otherwise.
     */
    public final synchronized boolean isReferenced() {
        return (this.count > 0);
    }

    /**
     * Increments the reference count. 
     */
    public final synchronized T reference() {
        this.count++;
        return this.value;
    }

    /**
     * Decrements the reference count. If the count reaches <code>0</code>
     * {@link #onDereferenced()} will be invoked. 
     */
    public final synchronized void dereference() {
        if (this.count > 0) {
            this.count--;
            if (this.count == 0)
                this.onDereferenced();
        }
    }

    public final synchronized int getReferenceCount() {
        return this.count;
    }

    /**
     * Invoked when the count reaches <code>0</code> via invocation of
     * {@link #dereference()}.
     * 
     * <P>The default implementation returns immediately.
     */
    protected void onDereferenced() {
    }
}

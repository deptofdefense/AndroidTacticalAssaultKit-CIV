
package com.atakmap.database;

public interface Bindable {

    /**
     * Binds the specified blob on the specified index.
     * 
     * @param idx   The index (one based)
     * @param value The blob
     */
    public void bind(int idx, byte[] value);

    /**
     * Binds the specified <code>int</code> on the specified index.
     * 
     * @param idx   The index (one based)
     * @param value The value
     */
    public void bind(int idx, int value);

    /**
     * Binds the specified <code>long</code> on the specified index.
     * 
     * @param idx   The index (one based)
     * @param value The value
     */
    public void bind(int idx, long value);

    /**
     * Binds the specified <code>double</code> on the specified index.
     * 
     * @param idx   The index (one based)
     * @param value The value
     */
    public void bind(int idx, double value);

    /**
     * Binds the specified {@link String} on the specified index.
     * 
     * @param idx   The index (one based)
     * @param value The value
     */
    public void bind(int idx, String value);

    /**
     * Binds the specified <code>null</code> on the specified index.
     * 
     * @param idx   The index (one based)
     */
    public void bindNull(int idx);

    /**
     * Clears all bindings.
     */
    public void clearBindings();

}

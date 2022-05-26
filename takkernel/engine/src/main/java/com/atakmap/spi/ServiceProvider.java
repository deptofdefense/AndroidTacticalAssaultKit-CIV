package com.atakmap.spi;

public interface ServiceProvider<T, V> {
    /**
     * Creates the target content/service from the specified input.
     *
     * <P>This method should <B>NEVER</B> throw.
     *
     * @param object    The input
     *
     * @return  An instance of the target content/service if successfuly,
     *          <code>null</code> otherwise.
     */
    public T create(V object);
}

package com.atakmap.spi;

public interface InteractiveServiceProvider<T, V> extends ServiceProvider<T, V> {

    public T create(V object, Callback callback);

    /**************************************************************************/

    public static interface Callback {
        /**
         * Allows the client an asynchronous method to cancel processing. Not
         * all {@link InteractiveServiceProvider} implementations may support
         * canceling; those that do may not be able to cancel immediately.
         * 
         * @return  <code>true</code> if processing should be canceled,
         *          <code>false</code> otherwise.
         */
        public boolean isCanceled();
        
        /**
         * If <code>true</code> is returned, the
         * {@link InteractiveServiceProvider} is instructed to only analyze the
         * input data to see if an output <I>may</I> be produced. The provider
         * will analyze the input and notify the callback via
         * {@link #setProbeMatch(boolean)} to return the probe result.
         * 
         * @return  <code>true</code> if the provider should only probe the
         *          input value, <code>false</code> if it should attempt to
         *          produce an output.
         */
        public boolean isProbeOnly();
        
        /**
         * Returns the probe limit value. The interpretation of the value is
         * specific to the class of {@link InteractiveServiceProvider}
         * implementation. Example interpretations could be the number of bytes
         * in a file or the number of files in a directory.
         *  
         * @return  The limit to be used when probing.
         */
        public int getProbeLimit();
        
        /**
         * If {@link #isProbeOnly()} returns <code>true</code>, the provider
         * will invoke this method once it determines whether or not it
         * <I>may</I> be able to process the input data.
         * 
         * <P>If {@link #isProbeOnly()} returns <code>false</code>, this method
         * will not be invoked.
         * 
         * @param match <code>true</code> if the provider may be able to produce
         *              an output value for the given input value,
         *              <code>false</code> if no output value can be created.
         */
        public void setProbeMatch(boolean match);
        
        /**
         * This method is invoked when an error occurs during processing that
         * will result in immediate termination with no output produced.
         * 
         * @param msg   The error message (may be <code>null</code>).
         * @param t     The {@link java.lang.Throwable Throwable} that was
         *              raised resulting in the termination of processing.
         */
        public void errorOccurred(String msg, Throwable t);
        
        /**
         * This method is invoked during processing to indicate progress. The
         * specific interpretation of the progress value is specific to the
         * class of {@link InteractiveServiceProvider} implementation.
         * 
         * @param progress  The current progress value
         */
        public void progress(int progress);
    } // Callback
}

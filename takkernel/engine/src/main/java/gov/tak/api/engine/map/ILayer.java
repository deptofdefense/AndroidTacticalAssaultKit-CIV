package gov.tak.api.engine.map;

public interface ILayer {

    /**
     * Callback interface for layer visibility changes.
     *
     * @author Developer
     */
    interface OnLayerVisibleChangedListener {
        /**
         * This method is invoked when the layer's visibility has changed.
         *
         * @param layer     The layer whose visibility changed
         * @param visible   Current visibility of layer
         */
        public void onLayerVisibleChanged(ILayer layer, boolean visible);
    } // OnLayerVisibleChangedListener

    /**
     * Sets the visibility of the layer. Any registered
     * {@link OnLayerVisibleChangedListener} instances should be notified if the
     * visibility of the layer changes as a result of the invocation of this
     * method.
     *
     * @param visible   <code>true</code> to make the layer visible,
     *                  <code>false</code> to make it invisible.
     */
    void setVisible(boolean visible);

    /**
     * Returns a flag indicating whether or not the layer is currently visible.
     *
     * @return  <code>true</code> if the layer is visible, <code>false</code>
     *          otherwise.
     */
    boolean isVisible();

    /**
     * Adds the specified {@link OnLayerVisibleChangedListener}.
     *
     * @param l The listener to add
     */
    void addOnLayerVisibleChangedListener(OnLayerVisibleChangedListener l);

    /**
     * Removes the specified {@link OnLayerVisibleChangedListener}.
     *
     * @param l The listener to remove
     */
    void removeOnLayerVisibleChangedListener(OnLayerVisibleChangedListener l);

    /**
     * Returns the name of the layer.
     *
     * @return  The name of the layer
     */
    String getName();

    /**
     * Returns optional extensions associated with the {@link ILayer} instance.
     * @param clazz The extension type
     * @param <T>   The extension type
     * @return
     */
    <T> T getExtension(Class<T> clazz);
}

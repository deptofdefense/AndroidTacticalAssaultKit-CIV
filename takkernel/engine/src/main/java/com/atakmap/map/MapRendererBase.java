package com.atakmap.map;

import com.atakmap.map.layer.Layer2;
import com.atakmap.util.Visitor;

import java.util.Collection;
import java.util.Iterator;
import java.util.Map;

interface MapRendererBase {
        /**
     * Callback interface providing notification when controls are added or
     * removed on a {@link Layer2}.
     *
     * <P>Client code that is interested in receiving such notifications should
     * use registration and unregistration for state information purposes only;
     * the {@link MapRenderer2#visitControl(Layer2, Visitor, Class)} and
     * {@link MapRenderer2#visitControls(Layer2, Visitor)} methods should still
     * be used to safely access controls asynchronously.
     *
     * @author Developer
     */
    public static interface OnControlsChangedListener {
        /**
         * Invoked when a control is registered.
         *
         * @param layer The layer the control is registered against.
         * @param ctrl  The control
         */
        public void onControlRegistered(Layer2 layer, MapControl ctrl);
        /**
         * Invoked when a control is unregistered.
         *
         * @param layer The layer the control was previously registered against.
         * @param ctrl  The control
         */
        public void onControlUnregistered(Layer2 layer, MapControl ctrl);
    }

    /**
     * Registers the specified control for the specified layer.
     *
     * @param layer A layer
     * @param ctrl  The control
     */
    public void registerControl(Layer2 layer, MapControl ctrl);
    /**
     * Unregisters the specified control for the specified layer.
     *
     * @param layer A layer
     * @param ctrl  The control
     */
    public void unregisterControl(Layer2 layer, MapControl ctrl);
    /**
     * Invokes the specified visitor on the specified control for the specified
     * layer. The visitor <B>MAY NOT</B> raise an exception during its
     * invocation. If invoked, the visitor's invocation is always completed
     * before this method returns.
     *
     * <P>Client code should only interact with the control during the
     * invocation of the visitor. Caching the reference to the control and
     * attempting to use it outside of the invocation may lead to undefined
     * results.
     *
     * @param layer     The layer
     * @param visitor   The visitor
     * @param ctrlClazz The class that the control derives from
     *
     * @return  <code>true</code> if the control could be found and the visitor
     *          was invoked, <code>false</code> otherwise.
     */
    public <T extends MapControl> boolean visitControl(Layer2 layer, Visitor<T> visitor, Class<T> ctrlClazz);
    /**
     * Invokes the specified visitor on the controls for the specified layer.
     * The visitor <B>MAY NOT</B> raise an exception during its invocation. If
     * invoked, the visitor's invocation is always completed before this method
     * returns.
     *
     * <P>Client code should only interact with the controls during the
     * invocation of the visitor. Caching the reference to the controls and
     * attempting to use any outside of the invocation may lead to undefined
     * results.
     *
     * @param layer     The layer
     * @param visitor   The visitor
     *
     * @return  <code>true</code> if controls for the layer were available and
     *          the visitor was invoked, <code>false</code> otherwise.
     */
    public boolean visitControls(Layer2 layer, Visitor<Iterator<MapControl>> visitor);
    /**
     * Invokes the specified visitor on the controls across all layers that have
     * had controls registered. The visitor <B>MAY NOT</B> raise an exception
     * during its invocation. If invoked, the visitor's invocation is always
     * completed before this method returns.
     *
     * <P>Client code should only interact with the controls during the
     * invocation of the visitor. Caching the reference to the controls and
     * attempting to use any outside of the invocation may lead to undefined
     * results.
     *
     * @param visitor   The visitor
     */
    public void visitControls(Visitor<Iterator<Map.Entry<Layer2, Collection<MapControl>>>> visitor);

    public void addOnControlsChangedListener(OnControlsChangedListener l);
    public void removeOnControlsChangedListener(OnControlsChangedListener l);
}

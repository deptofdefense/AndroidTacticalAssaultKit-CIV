package transapps.maps.plugin.layer;

import transapps.maps.plugin.Describable;
import transapps.maps.plugin.layer.model.LayerModel;


/**
 * This is the interface used as the extension point through which a layer may be provided to the
 * map so that it can display information natively on the map.
 * 
 * @author SRA
 * @since NW SDK 1.0.34
 * @see MapLayer
 */
public interface MapLayerDescriptor extends Describable
{
    /**
     * This needs to return the layer which is connected to this descriptor. The layer returned
     * needs to always be the same instance if this instance of the descriptor is called multiple
     * times.
     * 
     * @return The layer for this descriptor
     */
    public MapLayer getMapLayer( );

    /**
     * This needs to return the model which is used to provide the information to be shown on the
     * layer.
     * 
     * @return The model for providing the layer with what to show on the map
     */
    public LayerModel getLayerModel( );

    /**
     * This provides the type of the layer which needs to be a constant and never change. This value
     * works with the value returned by the {@link #getLayerTypePriority()} method to provide a hint
     * for the priority in the default order of layers.<br/>
     * This is a hint because the user will be able to change the default order of the layers so
     * even if the type has a higher priority than another layer it might still be rendered below
     * that layer. The only exception is with the {@link LayerType#TOP} value where all top layers
     * will always be drawn over lower priority layers. <br/>
     * When a is a {@link LayerType#TOP} it gains two specific features compared to the non-top
     * layers.<br/>
     * 1. The layer will be ordered with other top layers which will always be above non-top layers.
     * This also means that if there are more than one top layer a top layer might not be on top of
     * all other layers.<br/>
     * 2. The user will not be able to turn off the visibility of the layer. Also the layer
     * visibility can be turned on at any time even if the plugin specifically turns the off the
     * visibility of the layer.<br/>
     * <br/>
     * <b>Note:</b> Having a layer return {@link LayerType#TOP} could result in a plugin being
     * rejected if it is decided that the layer is not important enough to be the top most layer.
     * 
     * @return The type of the layer
     */
    public LayerType getLayerType( );

    /**
     * This provides the priority of the layer inside of the given layer type and needs to be a
     * constant that will never change. This value works with the value returned by the
     * {@link #getLayerType()} method to provide a hint for the priority in the default order of
     * layers.<br/>
     * When two layers have the same {@link LayerType} value the priority value provided from this
     * method will be used to determine which one will be on top of the other by default. The layer
     * with the larger value will be placed on top of the layer with the smaller value. If both
     * layers have the same value the top layer will be decided by other means.
     * 
     * @return The priority of the layer where the larger the number means more priority and a value
     *         of zero is the default
     */
    public int getLayerTypePriority( );

    /**
     * This defines the location where the layer falls into on the map
     * 
     * @author SRA
     * @since NW SDK 1.0.34
     */
    public enum LayerType
    {
        /**
         * This is for layers which are considered to provide information on map itself such as grid
         * or latitude and longitude lines
         */
        MAP( 0 ),

        /**
         * This is for layers which provide some type of augmentation to the map, but is not part of
         * a typical map (like grid lines) such as highlighting a specific region
         */
        MAP_AUGMENTATION( 1 ),

        /**
         * This is for layers which provide some type of symbol on the map such as a location marker
         */
        SYMBOL( 2 ),

        /**
         * This is for layers which contain very important information which should never be covered
         * up by lower priority layers
         */
        TOP( 3 );

        /**
         * This is the priority for the layer type where the higher the number the higher the
         * priority and the more likely it will be on top of lower priority layers
         */
        public final int priority;

        private LayerType( int priority )
        {
            this.priority = priority;
        }
    }
}

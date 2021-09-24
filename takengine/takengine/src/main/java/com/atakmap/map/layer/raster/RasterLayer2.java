package com.atakmap.map.layer.raster;

import java.util.Collection;

import com.atakmap.map.layer.Layer2;
import com.atakmap.map.layer.feature.geometry.Geometry;
import com.atakmap.map.projection.Projection;

/**
 * {@link com.atakmap.map.layer.Layer2 Layer2} subinterface for raster data.
 * 
 * <H2>Selection</H2>
 * 
 * <P>The <code>RasterLayer</code> manages a <I>selection</I> property. This
 * property instructs the layer what content should currently be rendered. For
 * very simple <code>RasterLayer</code> implementations, there may only be one
 * selection value (e.g. a single image). More complex layers may contain
 * multiple datasets and/or multiple imagery types providing potentially
 * worldwide coverage. In this case, the selection may be used to explicitly
 * request a single dataset or type be rendered (a "lock" function).
 * 
 * <H2>Preferred Projection</H2>
 * 
 * <P>The <code>RasterLayer</code> exposes a <I>preferred projection</I> that is
 * the {@link com.atakmap.map.projection.Projection Projection} it currently
 * would prefer to be displayed at. This value may be <code>null</code> in the
 * event that the layer has no preference. The value may change periodically as
 * it should be driven by the currently displayed data (i.e. selection changes).
 * 
 * <H2>Associated Extensions</H2>
 * 
 * <H2>Associated Controls</H2>
 *  
 * @author Developer
 */
public interface RasterLayer2 extends Layer2 {
    
    /**
     * Callback interface for changes to the preferred projection.
     * 
     * @author Developer
     */
    public static interface OnPreferredProjectionChangedListener {
        /**
         * This method is invoked when the preferred projection for the layer
         * changes.
         * 
         * @param layer The layer
         */
        public void onPreferredProjectionChanged(RasterLayer2 layer);
    } // OnPreferredProjectionChangedListener
    
    /**
     * Callback interface for changes to the current selection.
     * 
     * @author Developer
     */
    public static interface OnSelectionChangedListener {
        /**
         * This method is invoked when the <B>user</B> specifies a new selection
         * value for the layer.
         *
         * <P>Notification for changes to the auto-select value need to be
         * received via the
         * {@link com.atakmap.map.layer.raster.service.AutoSelectService.OnAutoSelectValueChangedListener OnAutoSelectValueChangedListener}
         * callback.
         * 
         * @param layer The layer
         */
        public void onSelectionChanged(RasterLayer2 layer);
    } // OnSelectionChangedListener
    
    public static interface OnSelectionVisibleChangedListener {
        public void onSelectionVisibleChanged(RasterLayer2 layer);
    }
    
    public static interface OnSelectionTransparencyChangedListener {
        public void onTransparencyChanged(RasterLayer2 control);
    }
    
    /**************************************************************************/
    
    /**
     * Specifies the <I>selection</I> to be displayed. If <code>null</code>, the
     * layer will automatically select the imagery based on the current
     * resolution.
     *  
     * @param type  The selection. Should be one of the values returned by
     *              {@link #getSelectionOptions()}.
     *              
     * @see #getSelectionOptions()
     */
    public void setSelection(String type);
    
    /**
     * Returns the current selection. If the layer is currently in auto-select
     * mode, the value returned should be the content currently displayed. In
     * the case where multiple content is displayed, the recommendation is to
     * return the value for the top-most content.
     * 
     * @return  The current selection
     */
    public String getSelection();
    
    /**
     * Returns <code>true</code> if the layer is in auto-select mode,
     * <code>false</code> otherwise.
     * 
     * @return  <code>true</code> if the layer is in auto-select mode,
     *          <code>false</code> otherwise.
     */
    public boolean isAutoSelect();
    
    /**
     * Returns the list of available selection options for all data contained in
     * this layer. The available selection options are recommended to be a
     * single logical index over the data (e.g. dataset names, imagery types).
     * 
     * <P>The <code>null</code> option is always implicit and does not need to
     * be specified.
     * 
     * @return  The available selection options
     */
    public Collection<String> getSelectionOptions();
    
    /**
     * Adds the specified {@link OnSelectionChangedListener}
     * 
     * @param l The listener
     */
    public void addOnSelectionChangedListener(OnSelectionChangedListener l);
    
    /**
     * Removes the specified {@link OnSelectionChangedListener}
     * 
     * @param l The listener
     */
    public void removeOnSelectionChangedListener(OnSelectionChangedListener l);

    /**
     * Returns the coverage geometry associated with the specified selection.

     * @param selection The selection value, may not be <code>null</code>
     * 
     * @return  The coverage geometry associated with the specified selection
     */
    public Geometry getGeometry(String selection);
    
    public double getMinimumResolution(String selection);
    public double getMaximumResolution(String selection);

    /**
     * Returns the current preferred projection for the layer. This method will
     * generally returned the native projection for the data that is currently
     * being rendered. This value is expected to change if the current selection
     * is manually or automatically updated.
     * 
     * @return  The current preferred projection for the layer
     * 
     * @see #addOnPreferredProjectionChangedListener(OnPreferredProjectionChangedListener)
     */
    public Projection getPreferredProjection();
    
    /**
     * Adds the specified listener to receive notification when the preferred
     * projection changes.
     * 
     * @param l The listener to add
     */
    public void addOnPreferredProjectionChangedListener(OnPreferredProjectionChangedListener l);
    
    /**
     * Removes the specified listener from receivign notification when the
     * preferred projection changes.
     * 
     * @param l The listener to remove
     */
    public void removeOnPreferredProjectionChangedListener(OnPreferredProjectionChangedListener l);

    /**
     * Sets the visibility for the specified selection option.
     * 
     * @param selection The selection option
     * @param visible   The visibility state
     */
    public void setVisible(String selection, boolean visible);
    
    /**
     * Returns the current visibility state for the specified selection option.
     * 
     * @param selection The selection option
     * 
     * @return  The current visibility state for the specified selection option.
     */
    public boolean isVisible(String selection);
    
    /**
     * Adds the specified listener to receive notification when selection
     * visibility states are modified.
     * 
     * @param l The listener
     */
    public void addOnSelectionVisibleChangedListener(OnSelectionVisibleChangedListener l);
    
    /**
     * Removes the specified listener from receiving notifications when selection
     * visibility states are modified.
     * 
     * @param l The listener
     */
    public void removeOnSelectionVisibleChangedListener(OnSelectionVisibleChangedListener l);
    
    /**
     * Obtains the current transparency value for the specified selection.
     * 
     * @param selection    The selection
     * 
     * @return    The current transparency value.
     */
    public float getTransparency(String selection);
    
    /**
     * Sets the transparency value for the specified selection.
     * 
     * @param selection    The selection
     * @param value           The transparency value
     */
    public void setTransparency(String selection, float value);
    
    public void addOnSelectionTransparencyChangedListener(OnSelectionTransparencyChangedListener l);
    public void removeOnSelectionTransparencyChangedListener(OnSelectionTransparencyChangedListener l);
} // RasterLayer2


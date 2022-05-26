package com.atakmap.map.layer.raster.tilematrix;

public interface TileContainerSpi {
    /**
     * Returns the provider name.
     * @return
     */
    public String getName();
    /**
     * Returns the default extension for the associated file type.
     * @return
     */
    public String getDefaultExtension();
    /**
     * Creates a new tile container at the specified location, modeling its own
     * tile matrix definition from the supplied {@link TileMatrix} instance.
     * 
     * @param name  The name for the container content; adopted from
     *              <code>spec</code> if <code>null</code>
     * @param path  The location for the new container
     * @param spec  A <code>TileMatrix</code> that the new container's tile
     *              matrix should be modeled after
     * @return
     */
    public TileContainer create(String name, String path, TileMatrix spec);
    /**
     * Opens a new {@link TileContainer} instance that will read from (and
     * possibly write to) the specified location.
     * 
     * @param path      The location of the container
     * @param spec      If non-<code>null</code> specifies a tile matrix
     *                  definition that the container must be compatible with.
     *                  For the read-only case, this parameter should always
     *                  be <code>null</code>.
     * @param readOnly  <code>true</code> for read-only, <code>false</code> for
     *                  read-write
     * @return
     */
    public TileContainer open(String path, TileMatrix spec, boolean readOnly);
    /**
     * Returns <code>true</code> if this <code>TileContainerSpi</code> can
     * create a {@link TileContainer} instance that can store tiles from the
     * specified tile matrix definition.
     * 
     * @param spec  A tile matrix definition
     * 
     * @return  <code>true</code> if this <code>TileContainerSpi</code> can
     *          create a {@link TileContainer} instance that can store tiles
     *          from the specified tile matrix definition, <code>false</code>
     *          otherwise.
     */
    public boolean isCompatible(TileMatrix spec);
}

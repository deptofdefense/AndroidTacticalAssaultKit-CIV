package gov.tak.api.engine.map;

public interface IMapRendererSpi {
    /**
     * @param parent parent for the surface instance to be created
     * @param globe the associated globe
     * @param surfaceType  The desired surface type
     */
    MapRenderer create(IGlobe globe, Object parent, Class<?> surfaceType);
}

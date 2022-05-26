package gov.tak.api.engine.map;

public interface IRenderContextSpi {
    /**
     * Creates a new render context based on the specified object. The returned context will have an associated surface depending on the input.
     * @return The render context or null if the source object is not supported
     */
    RenderContext create(Object parent);
}

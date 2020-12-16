#ifndef TAK_ENGINE_RENDERER_CORE_GLTERRAIN_H_INCLUDED
#define TAK_ENGINE_RENDERER_CORE_GLTERRAIN_H_INCLUDED

#include "core/GeoPoint2.h"
#include "renderer/map/GLMapView.h"
#include "renderer/map/GLResolvableMapRenderable.h"
#include "renderer/core/GLOffscreenVertex.h"
#include "util/Memory.h"


namespace TAK
{
    namespace Engine
    {
        namespace Renderer
        {
            namespace Core
            {
                /**
                * Interface for controlling terrain loading for the renderer.
                *
                * @author Developer
                */
                class GLTerrain
                {
                protected :
                    virtual ~GLTerrain() NOTHROWS = 0;
                public :
                    /**
                    * Initiates terrain fetch given the current view. This method should be
                    * invoked at the start of the render pump.
                    *
                    * @param view  The current view
                    */
                    virtual void update(const TAK::Engine::Renderer::Core::GLMapView2& view) NOTHROWS = 0;

                    /**
                    * This will get the current version of the terrain information. This number can be used to
                    * tell if new terrain information is available. When the number is the same it is assumed
                    * that any elevation requests will always return the same value.
                    * @return The current terrain version
                    */
                    virtual int getTerrainVersion() NOTHROWS = 0;

                    /**
                    * Retrieves the elevation value at the specified point.
                    *
                    * @param g The point
                    * @param state If non-<code>null</code>, returns the status of the
                    *              elevation value returned. Results should be interpreted as
                    *              follows
                    *              <UL>
                    *                  <LI>{@link GLResolvableMapRenderable.State#RESOLVING} - The derived value (if any) was interpolated, but may be further refined
                    *                  <LI>{@link GLResolvableMapRenderable.State#RESOLVED} - The derived value will not be refined further
                    *                  <LI>{@link GLResolvableMapRenderable.State#UNRESOLVABLE} - The value cannot be computed for the specified location
                    *              </UL>
                    *
                    * @param value The elevation value, as meters HAE at the specified location or
                    *          <code>Double.NaN</code> if the value is not available. The value
                    *          returned by <code>state</code> should be evaluated to determine
                    *          how the value should be treated or if this method should be
                    *          invoked with the given point again in the future.
                    */
                    virtual Util::TAKErr getElevation(double *value, atakmap::renderer::map::GLResolvableMapRenderable::State* state, const TAK::Engine::Core::GeoPoint2 &g) NOTHROWS = 0;

                    /**
                    * This method will try to lookup the altitude/elevation for the vertex updating the
                    * properties when the altitude has been found which includes incrementing the version
                    * values
                    *
                    * @param vertex
                    *             The vertex to update the altitude for
                    */
                    virtual Util::TAKErr updateAltitude(TAK::Engine::Renderer::Core::GLOffscreenVertex *vertex) NOTHROWS = 0;

                    /**
                    * This method will try to lookup the altitude/elevation for the vertices in the array
                    * starting at index 0 and going up to but excluding the vertex at index amount. Each
                    * vertex processed will have its properties updated when the altitude has been found
                    * which includes incrementing the version values. A vertex will only be updated if the
                    * {@link GLOffscreenVertex#geoVersion} does not equal the {@link GLOffscreenVertex#altVersion}
                    * value
                    *
                    * @param vertices
                    *             The array of the vertices to update the altitudes for
                    * @param amount
                    *             The amount of the vertices to update
                    * @return The current terrain version {@link #getTerrainVersion()}
                    */
                    virtual Util::TAKErr updateAltitude(int *retVersion, TAK::Engine::Renderer::Core::GLOffscreenVertex *vertices, const std::size_t amount) NOTHROWS = 0;
#if 0
                    /**
                    * When called this will try to free any memory that is not being used. The goal of this
                    * method is to reduce the memory footprint of the class, but it is not guaranteed to
                    * free anything when the method is called.
                    */
                    virtual void tryToFreeUnusedMemory() NOTHROWS = 0;
#endif
                };

                typedef std::unique_ptr<GLTerrain, void(*)(const GLTerrain *)> GLTerrainPtr;
            }
        }
    }
}

#endif
#ifndef ATAKMAP_RENDERER_GLMAPVIEW_H_INCLUDED
#define ATAKMAP_RENDERER_GLMAPVIEW_H_INCLUDED

#include "core/RenderContext.h"
#include "renderer/map/GLMapRenderable.h"
#include "renderer/map/layer/GLLayer.h"
#include "core/AtakMapView.h"
#include "math/Point.h"
#include "core/GeoPoint.h"
#include "core/Layer.h"
#include "core/Layer2.h"
#include "core/AtakMapController.h"
#include "core/MapSceneModel.h"
#include "math/Matrix.h"
#include "port/Platform.h"

#include <cstdint>
#include <list>

namespace TAK {
    namespace Engine {
        namespace Renderer {
            namespace Core {
                class ENGINE_API GLMapView2;
                class ENGINE_API GLLayer2;
            }
        }
    }
}

namespace atakmap
{
    namespace renderer
    {
        namespace map {

            class ENGINE_API GLMapView : public core::AtakMapView::MapMovedListener,
                public core::AtakMapView::MapProjectionChangedListener,
                public core::AtakMapView::MapLayersChangedListener,
                public core::MapControllerFocusPointChangedListener {


            public:
                class ENGINE_API OnAnimationSettledCallback {
                public:
                    virtual ~OnAnimationSettledCallback() {};
                    virtual void onAnimationSettled() = 0;
                };

                static const double recommendedGridSampleDistance;

            private :
                std::unique_ptr<TAK::Engine::Renderer::Core::GLMapView2, void(*)(const TAK::Engine::Renderer::Core::GLMapView2 *)> implPtr;
            public :
                TAK::Engine::Renderer::Core::GLMapView2* impl;

                int &left;
                int &bottom;
                int &right;
                int &top;

                double &drawLat;
                double &drawLng;
                double &drawRotation;
                double &drawMapScale;
                double &drawMapResolution;
                double &drawTilt;
                double &animationFactor;
                int &drawVersion;
                bool &targeting;
                double &westBound;
                double &southBound;
                double &northBound;
                double &eastBound;

                int &drawSrid;
                float &focusx;
                float &focusy;

                core::GeoPoint upperLeft;
                core::GeoPoint upperRight;
                core::GeoPoint lowerRight;
                core::GeoPoint lowerLeft;

                bool &settled;

                int &renderPump;

                core::MapSceneModel scene;

                float *sceneModelForwardMatrix;

                int64_t &animationLastTick;
                int64_t &animationDelta;
                int64_t &animationLastUpdate;

                bool &drawHorizon;
                bool &crossesIDL;
                bool &continuousSrollEnabled;

                /**
                * Library private data. This pointer should NEVER be accessed
                * or modified by client code.
                */
                void *privateData;
                    
                double &pixelDensity;
            public :
                GLMapView(TAK::Engine::Core::RenderContext *ctx, core::AtakMapView *aview, int left, int bottom, int right, int top);
                GLMapView(std::unique_ptr<TAK::Engine::Renderer::Core::GLMapView2, void(*)(const TAK::Engine::Renderer::Core::GLMapView2 *)> &&opaque) NOTHROWS;
                ~GLMapView();

                void dispose();

                TAK::Engine::Core::RenderContext *getRenderContext() const;
                core::AtakMapView *getView() const;

                // Offload to GL thread
                void setBaseMap(GLMapRenderable *map);

                void startAnimating(double lat, double lng, double scale, double rotation,
                    double animateFactor);
                void startAnimatingFocus(float x, float y, double animateFactor);

                void getMapRenderables(std::list<GLMapRenderable *> *retval) const;

                void setOnAnimationSettledCallback(OnAnimationSettledCallback *c);

                math::Point<float> forward(core::GeoPoint p) const;
                math::Point<float> *forward(core::GeoPoint p, math::Point<float> *retval) const;
                math::Point<float> *forward(core::GeoPoint *p, size_t count, math::Point<float> *retval) const;

                // count is # of pairs (lat/lon) in src & dst
                void forward(const float *src, const size_t count, float *dst) const;
                void forward(const double *src, const size_t count, float *dst) const;

                void inverse(const float *src, const size_t count, float *dst) const;

                void inverse(const float *src, const size_t count, double *dst) const;
                core::GeoPoint inverse(math::Point<float> p) const;

                core::GeoPoint *inverse(math::Point<float> p, core::GeoPoint *retval) const;
                core::GeoPoint *inverse(math::Point<float> *p, size_t count, core::GeoPoint *retval) const;

                /**
                * Renders the map. The scene is computed via 'prepareScene'
                * and then rendered via 'drawRenderables'
                */
                void render();

                int getLeft() const;
                int getRight() const;
                int getTop() const;
                int getBottom() const;

                void mapMoved(core::AtakMapView *map_view, const bool animate) override;

                void mapProjectionChanged(core::AtakMapView *map_view) override;

                void mapLayerAdded(core::AtakMapView *mapView, atakmap::core::Layer *layer) override;

                void mapLayerRemoved(core::AtakMapView *mapView, atakmap::core::Layer *layer) override;
                void mapLayerPositionChanged(core::AtakMapView *mapView, atakmap::core::Layer *layer, const int oldPosition, const int newPosition) override;

                void mapControllerFocusPointChanged(core::AtakMapController *controller, const atakmap::math::Point<float> * const focus) override;
            protected :
                /** prepares the scene for rendering */
                void prepareScene();
                /** renders the current scene */
                void drawRenderables();
            private:
                core::AtakMapView * const view;

                int &sceneModelVersion;

                void forwardImpl(core::GeoPoint *p, math::Point<float> *retval) const;
                void inverseImpl(math::Point<float> *p, core::GeoPoint *retval) const;
            };
        }
    }
}


#endif

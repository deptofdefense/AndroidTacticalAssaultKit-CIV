#pragma once

#include "port/Platform.h"
#include "util/Error.h"

#include "raster/mosaic/MosaicDatabase2.h"
#include "raster/MosaicDatasetDescriptor.h"
#include "raster/tilereader/TileReader2.h"
#include "renderer/core/GLAsynchronousMapRenderable3.h"
#include "renderer/raster/tilereader/GLQuadTileNode2.h"
#include "renderer/raster/GLMapLayer2.h"
#include "renderer/raster/ImagerySelectionControl.h"
#include "core/MapRenderer.h"
#include "core/RenderContext.h"


namespace TAK {
    namespace Engine {
        namespace Renderer {
            namespace Raster {
                namespace Mosaic {

                    class ENGINE_API GLMosaicMapLayer : public Core::GLAsynchronousMapRenderable3, public GLMapLayer2
                    {
                       public:
                        // GLMosaicMapLayer's own API
                        GLMosaicMapLayer(TAK::Engine::Core::RenderContext *context, atakmap::raster::MosaicDatasetDescriptor *info) NOTHROWS;
                        GLMosaicMapLayer(TAK::Engine::Core::MapRenderer *renderer, atakmap::raster::MosaicDatasetDescriptor *info) NOTHROWS;
                        virtual ~GLMosaicMapLayer() NOTHROWS;
                        Util::TAKErr toString(TAK::Engine::Port::String &value) NOTHROWS;
                        virtual void release() NOTHROWS;

                       protected:
                        virtual Util::TAKErr createRootNode(TileReader::GLQuadTileNode2Ptr &value, const TAK::Engine::Raster::Mosaic::MosaicDatabase2::Frame &frame) NOTHROWS;

                       public:
                        // GLMapLayer2 methods
                        const char *getLayerUri() const NOTHROWS override;
                        const atakmap::raster::DatasetDescriptor *getInfo() const NOTHROWS override;
                        Util::TAKErr getControl(void **ctrl, const char *type) const NOTHROWS override;
                        void draw(const Core::GLMapView2 &view, const int renderPass) NOTHROWS override;
                        int getRenderPass() NOTHROWS override;
                        void start() NOTHROWS override;
                        void stop() NOTHROWS override;

                       protected:
                        // GLAsynchronousMapRenderable3 methods
                        // Java - xxxPendingData()
                        Util::TAKErr createQueryContext(QueryContextPtr &value) NOTHROWS override;
                        Util::TAKErr resetQueryContext(QueryContext &pendingData) NOTHROWS override;
                        // Java - updateRenderableReleaseLists()
                        Util::TAKErr updateRenderableLists(QueryContext &pendingData) NOTHROWS override;
                        Util::TAKErr releaseImpl() NOTHROWS override;
                        Util::TAKErr query(QueryContext &result, const ViewState &state) NOTHROWS override;
                        Util::TAKErr newViewStateInstance(ViewStatePtr &value) NOTHROWS override;
                        Util::TAKErr getBackgroundThreadName(TAK::Engine::Port::String &value) NOTHROWS override;
                        void initImpl(const Core::GLMapView2 &view) NOTHROWS override;
                        // Java - checkState()
                        bool shouldQuery() NOTHROWS override;
                        bool shouldCancel() NOTHROWS override;
                       private:
                        // Java - getRenderList()
                        Util::TAKErr getRenderables(Port::Collection<GLMapRenderable2 *>::IteratorPtr &iter) NOTHROWS override;

                       private:
                        class GLQuadTileNodeInitializer;
                        class QueryArgument;
                        class RenderableListIter;
                        class RasterDataAccessControlImpl;
                        class SelectControlImpl;

                        void commonInit(TAK::Engine::Core::RenderContext *context, atakmap::raster::MosaicDatasetDescriptor *info) NOTHROWS;
                        Util::TAKErr constructQueryParams(std::list<TAK::Engine::Raster::Mosaic::MosaicDatabase2::QueryParameters> *retval, 
                            const QueryArgument &localQuery) NOTHROWS;
                        Util::TAKErr queryImpl(QueryContext &result, const QueryArgument &localQuery) NOTHROWS;
                        Util::TAKErr resolvePath(Port::String &value, const Port::String &path) NOTHROWS;

                       public:
                        static bool frameSort(const TAK::Engine::Raster::Mosaic::MosaicDatabase2::Frame &f0,
                                              const TAK::Engine::Raster::Mosaic::MosaicDatabase2::Frame &f1);
                        typedef std::map<TAK::Engine::Raster::Mosaic::MosaicDatabase2::Frame, std::shared_ptr<TileReader::GLQuadTileNode2>,
                                         decltype(frameSort) *> SortedFrameMap;

                       protected:
                        std::shared_ptr<TAK::Engine::Raster::TileReader::TileReader2::AsynchronousIO> asyncio;
                        bool ownsIO;
                        bool textureCacheEnabled;
                        std::map<std::string, void *> controls;
                        
                        /** the currently visible frames */
                        SortedFrameMap visibleFrames;

                       private:
                        /**
                         * previously visible frames that are still in the ROI and should be released once all
                         * {@link #visibleFrames} are resolved.
                         */
                        SortedFrameMap zombie_frames_;

                        /**
                         * A subset of {@link #visibleFrames} that were in the zombie list during the
                         * previous call to {@link #draw}, but have been moved into the visible list. Each member should
                         * have its <code>resume</code> method invoked on the GL thread before the next call
                         * <code>super.draw</code>.
                         */
                        std::list<std::shared_ptr<TileReader::GLQuadTileNode2>> resurrected_frames_;

                        atakmap::raster::MosaicDatasetDescriptor *info_;
                        Port::String selected_type_;

                        TAK::Engine::Core::RenderContext *surface_;

                        //std::unique_ptr<ColorControlImpl> colorControlImpl;
                        // MosaicFrameColorControl *frameColorControl;
                        std::unique_ptr<RasterDataAccessControlImpl> raster_access_control_;
                        std::unique_ptr<SelectControlImpl> select_control_;
                        ImagerySelectionControl::Mode resolution_select_mode_;
                        std::set<Port::String> imagery_type_filter_;
                    };

                }  // namespace Mosaic
            }  // namespace Raster
        }  // namespace Renderer
    }  // namespace Engine
}  // namespace TAK

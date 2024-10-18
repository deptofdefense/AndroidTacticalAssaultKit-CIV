#ifndef TAK_ENGINE_RENDERER_RASTER_TILEREADER_PRECISEVERTEXRESOLVER_H_INCLUDED
#define TAK_ENGINE_RENDERER_RASTER_TILEREADER_PRECISEVERTEXRESOLVER_H_INCLUDED

#include "port/Platform.h"
#include "raster/DatasetProjection2.h"
#include "renderer/core/GLGlobeBase.h"
#include "renderer/raster/tilereader/GLQuadTileNode3.h"
#include "renderer/raster/tilereader/VertexResolver.h"
#include "thread/Mutex.h"
#include "util/Error.h"

namespace TAK {
    namespace Engine {
        namespace Renderer {
            namespace Raster {
                namespace TileReader {
                    class PreciseVertexResolver : public VertexResolver
                    {
                    private :
                        PreciseVertexResolver(GLQuadTileNode3 &owner) NOTHROWS;
                    public :
                        ~PreciseVertexResolver() NOTHROWS;
                    private :
                        virtual void beginDraw(const Core::GLGlobeBase &view) NOTHROWS;
                        virtual void endDraw(const Core::GLGlobeBase &view) NOTHROWS;
                        virtual void beginNode(const GLQuadTileNode3 &node) NOTHROWS;
                        virtual void endNode(const GLQuadTileNode3 &node) NOTHROWS;
                        virtual Util::TAKErr project(TAK::Engine::Core::GeoPoint2 *value, bool *resolved, const int64_t imgSrcX, const int64_t imgSrcY) NOTHROWS;
                    private :
                        // Porting notes: Java comparitor is <0 if n1 before n2, 0 if equal or >0 if n2 before n1
                        // std sort comparitor wants true iff n1 before n2
                        static bool pointSort(const Math::Point2<int64_t> &p0, const Math::Point2<int64_t> &p1)
                        {
                            int64_t retval = p0.y - p1.y;
                            if (retval == 0)
                                retval = p0.x - p1.x;
                            return retval < 0;
                        }

                        typedef std::map<Math::Point2<int64_t>, TAK::Engine::Core::GeoPoint2, decltype(pointSort) *> SortedPointMap;
                        typedef std::set<Math::Point2<int64_t>, decltype(pointSort) *> SortedPointSet;

                        struct RenderRunnableOpaque
                        {
                            PreciseVertexResolver &owner;
                            enum
                            {
                                END_DRAW,
                                RUN_RESULT
                            } eventType;

                            bool invalid;
                            std::weak_ptr<GLQuadTileNode3> node;
                            std::size_t targetGridWidth;
                            std::shared_ptr<Thread::Mutex> mutex;
                            RenderRunnableOpaque(PreciseVertexResolver &owner, const std::shared_ptr<GLQuadTileNode3> &node, const std::size_t targetGridWidth) NOTHROWS;
                            RenderRunnableOpaque(PreciseVertexResolver &owner) NOTHROWS;
                        };

                        Util::TAKErr preciseImageToGround(TAK::Engine::Core::GeoPoint2 *ground, const Math::Point2<double> &image);
                        Util::TAKErr resolve();
                        Util::TAKErr getCacheKey(std::string *value);
                        static Util::TAKErr deserialize(Util::MemBuffer2 &buf, SortedPointMap &precise, SortedPointSet &unresolvable);
                        static Util::TAKErr serialize(Util::MemBuffer2Ptr &buf, const SortedPointMap &precise, const SortedPointSet &unresolvable);

                        static void renderThreadRunnable(void *opaque) NOTHROWS;
                        void queueGLCallback(RenderRunnableOpaque *runnableInfo);

                        static void *threadRun(void *opaque);
                        void threadRunImpl();

                        bool targeting;

                        GLQuadTileNode3 &owner;
                        Thread::Monitor monitor;
                        Thread::ThreadPtr thread;
                        Thread::ThreadID activeID;
                        int threadCounter;
                        std::list<Math::Point2<int64_t>> queue;
                        Math::Point2<int64_t> query;
                        SortedPointSet pending;
                        SortedPointSet unresolvable;
                        SortedPointMap precise;
                        const GLQuadTileNode3 *currentNode;

                        SortedPointSet currentRequest;
                        std::vector<std::weak_ptr<GLQuadTileNode3>> requestNodes;

                        TAK::Engine::Core::GeoPoint2 scratchGeo;
                        Math::Point2<double> scratchImg;

                        int needsResolved;
                        int requested;
                        int numNodesPending;

                        bool initialized;

                        struct {
                            std::shared_ptr<Thread::Mutex> mutex;
                            std::vector<RenderRunnableOpaque*> queued;
                        } renderRunnables;

                        struct PointSetPredicate
                        {
                            const SortedPointSet &ref;
                            PointSetPredicate(const SortedPointSet &ref) : ref(ref) {}
                            bool operator()(const Math::Point2<int64_t> &v) { return ref.find(v) != ref.end(); }
                        };

                        struct PointMapPredicate
                        {
                            const SortedPointMap &ref;
                            PointMapPredicate(const SortedPointMap &ref) : ref(ref) {}
                            bool operator()(const Math::Point2<int64_t> &v) { return ref.find(v) != ref.end(); }
                        };
                    private :
                        friend class GLQuadTileNode3;
                        friend class NodeCore;
                    };
                }
            }
        }
    }
}
#endif

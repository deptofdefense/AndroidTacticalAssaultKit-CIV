#ifndef TAK_ENGINE_RENDERER_FEATURE_GLGEOMETRYBATCHBUILDER_H_INCLUDED
#define TAK_ENGINE_RENDERER_FEATURE_GLGEOMETRYBATCHBUILDER_H_INCLUDED

#include <map>
#include <vector>

#include "core/Projection2.h"
#include "feature/Feature2.h"
#include "feature/FeatureDefinition2.h"
#include "math/Point2.h"
#include "port/Platform.h"
#include "renderer/feature/GLBatchGeometryRenderer4.h"
#include "util/DataOutput2.h"
#include "util/Error.h"

namespace TAK {
    namespace Engine {
        namespace Renderer {
            namespace Feature {
                class ENGINE_API GLGeometryBatchBuilder
                {
                public :
                    class Callback;
                public :
                    class VertexWriter;
                public :
                    GLGeometryBatchBuilder() NOTHROWS;
                    ~GLGeometryBatchBuilder() NOTHROWS;
                public :
                    /**
                    * Prepares the builder for the next batch of data. This function must always be invoked prior to calling `push`
                     */
                    Util::TAKErr reset(const int surfaceSrid, const int spritesSrid, const TAK::Engine::Core::GeoPoint2  &relativeToCenter, const double resolution, Callback &calback) NOTHROWS;
                    Util::TAKErr push(TAK::Engine::Feature::FeatureDefinition2 &def, const bool processLabels) NOTHROWS;
                    Util::TAKErr push(const TAK::Engine::Feature::Feature2 &def, const bool processLabels) NOTHROWS;
                    /**
                     * Flushes any data that is in mapped buffers and unmaps. This function should always be called prior to `setBatch`.
                     */
                    Util::TAKErr flush() NOTHROWS;
                    /**
                     */
                    Util::TAKErr setBatch(GLBatchGeometryRenderer4 &sink) const NOTHROWS;
                private :
                    struct Builder {
                        Util::MemoryOutput2 buffer;
                        GLBatchGeometryRenderer4::PrimitiveBuffer pb;
                    };
                    struct {
                        struct {
                            Builder lines;
                            Builder antiAliasedLines;
                            Builder polygons;
                            Builder strokedPolygons;
                            Builder points;
                        } surface;
                        struct {
                            Builder lines;
                            Builder antiAliasedLines;
                            Builder polygons;
                            Builder strokedPolygons;
                            Builder points;
                        } sprites;
                    } builders;
                    struct {
                        struct {
                            std::vector<GLBatchGeometryRenderer4::PrimitiveBuffer> lines;
                            std::vector<GLBatchGeometryRenderer4::PrimitiveBuffer> antiAliasedLines;
                            std::vector<GLBatchGeometryRenderer4::PrimitiveBuffer> polygons;
                            std::vector<GLBatchGeometryRenderer4::PrimitiveBuffer> strokedPolygons;
                            std::vector<GLBatchGeometryRenderer4::PrimitiveBuffer> points;
                        } surface;
                        struct {
                            std::vector<GLBatchGeometryRenderer4::PrimitiveBuffer> lines;
                            std::vector<GLBatchGeometryRenderer4::PrimitiveBuffer> antiAliasedLines;
                            std::vector<GLBatchGeometryRenderer4::PrimitiveBuffer> polygons;
                            std::vector<GLBatchGeometryRenderer4::PrimitiveBuffer> strokedPolygons;
                            std::vector<GLBatchGeometryRenderer4::PrimitiveBuffer> points;
                        } sprites;
                    } buffers;

                    struct {
                        TAK::Engine::Core::Projection2Ptr surface{ TAK::Engine::Core::Projection2Ptr(nullptr, nullptr) };
                        TAK::Engine::Core::Projection2Ptr sprites{ TAK::Engine::Core::Projection2Ptr(nullptr, nullptr) };
                    } proj;
                    struct {
                        Math::Point2<double> surface;
                        Math::Point2<double> sprites;
                    } relativeToCenter;
                    std::map<std::string, TAK::Engine::Feature::StylePtr_const> parsedStyles;
                    double resolution;
                    Callback *callback;
                };

                class GLGeometryBatchBuilder::Callback
                {
                protected :
                    virtual ~Callback() NOTHROWS = 0;
                public :
                    virtual Util::TAKErr mapBuffer(GLuint *handle, void **buffer, const std::size_t size) NOTHROWS = 0;
                    virtual Util::TAKErr unmapBuffer(const GLuint handle) NOTHROWS = 0;
                    virtual Util::TAKErr getElevation(double *value, const double latitude, const double longitude) NOTHROWS = 0;
                    virtual Util::TAKErr getIcon(GLuint *id, float *u0, float *v0, float *u1, float *v1, std::size_t *w, std::size_t *h, float *rotation, bool *isAbsoluteRotation, const char* uri) NOTHROWS = 0;
                    /**
                     * Adds a label to the feature currently being processed.
                     */
                    virtual Util::TAKErr addLabel(const TAK::Engine::Renderer::Core::GLLabel &label) NOTHROWS  = 0;
                    virtual uint32_t reserveHitId() NOTHROWS = 0;
                };
            }
        }
    }
}

#endif

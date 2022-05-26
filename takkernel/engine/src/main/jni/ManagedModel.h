#ifndef TAKENGINE_MODEL_IMPL_MANAGEDMODEL_H_INCLUDED
#define TAKENGINE_MODEL_IMPL_MANAGEDMODEL_H_INCLUDED

#include <jni.h>

#include <vector>

#include <model/Mesh.h>
#include <port/String.h>

namespace TAKEngine {
    namespace Model {
        namespace Impl {
            class ManagedModel : public TAK::Engine::Model::Mesh
            {
            public :
                ManagedModel(JNIEnv *env,
                             jobject jmodel,
                             const char *textureUri,
                             const std::size_t numVertices,
                             const std::size_t numFaces,
                             const void *positions,
                             const void *texCoords,
                             const void *normals,
                             const void *colors,
                             const TAK::Engine::Model::WindingOrder &windingOrder,
                             const TAK::Engine::Model::DrawMode &drawMode,
                             const TAK::Engine::Feature::Envelope2 &aabb,
                             const TAK::Engine::Model::VertexDataLayout &layout) NOTHROWS;
                ManagedModel(JNIEnv *env,
                             jobject jmodel,
                             const char *textureUri,
                             const std::size_t numVertices,
                             const std::size_t numFaces,
                             const void *positions,
                             const void *texCoords,
                             const void *normals,
                             const void *colors,
                             const TAK::Engine::Model::WindingOrder &windingOrder,
                             const TAK::Engine::Model::DrawMode &drawMode,
                             const TAK::Engine::Feature::Envelope2 &aabb,
                             const TAK::Engine::Model::VertexDataLayout &layout,
                             const TAK::Engine::Port::DataType &indexType,
                             const std::size_t numIndices,
                             const void *indices,
                             const std::size_t indexOffset) NOTHROWS;
                ~ManagedModel() NOTHROWS;
            public :
                bool valid() NOTHROWS;
            public :
                virtual std::size_t getNumMaterials() const NOTHROWS;
                virtual TAK::Engine::Util::TAKErr getMaterial(TAK::Engine::Model::Material *value, const std::size_t index) const NOTHROWS;
                virtual std::size_t getNumVertices() const NOTHROWS;
                virtual std::size_t getNumFaces() const NOTHROWS;
                virtual bool isIndexed() const NOTHROWS;
                virtual TAK::Engine::Util::TAKErr getPosition(TAK::Engine::Math::Point2<double> *value, const std::size_t index) const NOTHROWS;
                virtual TAK::Engine::Util::TAKErr getTextureCoordinate(TAK::Engine::Math::Point2<float> *value, const TAK::Engine::Model::VertexAttribute texCoord, const std::size_t index) const NOTHROWS;
                virtual TAK::Engine::Util::TAKErr getNormal(TAK::Engine::Math::Point2<float> *value, const std::size_t index) const NOTHROWS;
                virtual TAK::Engine::Util::TAKErr getColor(unsigned int *value, const std::size_t index) const NOTHROWS;
                virtual TAK::Engine::Util::TAKErr getVertexAttributeType(TAK::Engine::Port::DataType *value, const unsigned int attr) const NOTHROWS;
                virtual TAK::Engine::Util::TAKErr getIndexType(TAK::Engine::Port::DataType *value) const NOTHROWS;
                virtual TAK::Engine::Util::TAKErr getIndex(std::size_t *value, const std::size_t index) const NOTHROWS;
                virtual const void *getIndices() const NOTHROWS;
                virtual std::size_t getIndexOffset() const NOTHROWS;
                virtual std::size_t getNumIndices() const NOTHROWS;
                virtual TAK::Engine::Util::TAKErr getVertices(const void **value, const std::size_t attr) const NOTHROWS;
                virtual TAK::Engine::Model::WindingOrder getFaceWindingOrder() const NOTHROWS;
                virtual TAK::Engine::Model::DrawMode getDrawMode() const NOTHROWS;
                virtual const TAK::Engine::Feature::Envelope2 &getAABB() const NOTHROWS;
                virtual const TAK::Engine::Model::VertexDataLayout &getVertexDataLayout() const NOTHROWS;
                virtual std::size_t getNumBuffers() const NOTHROWS;
                virtual TAK::Engine::Util::TAKErr getBuffer(const TAK::Engine::Util::MemBuffer2 **value, const std::size_t idx) const NOTHROWS;
            public :
                jobject jmodel;
            private :
                std::vector<TAK::Engine::Model::Material> materials;
                std::size_t numVertices;
                std::size_t numFaces;
                bool indexed;
                std::size_t indexOffset;
                TAK::Engine::Port::DataType indexType;
                const void *indices;
                std::size_t numIndices;
                const void *positionVertices;
                const void *texCoord0Vertices;
                const void *texCoord1Vertices;
                const void *texCoord2Vertices;
                const void *texCoord3Vertices;
                const void *texCoord4Vertices;
                const void *texCoord5Vertices;
                const void *texCoord6Vertices;
                const void *texCoord7Vertices;
                const void *normalVertices;
                const void *colorVertices;
                TAK::Engine::Model::WindingOrder windingOrder;
                TAK::Engine::Model::DrawMode drawMode;
                TAK::Engine::Feature::Envelope2 aabb;
                TAK::Engine::Model::VertexDataLayout layout;

                bool initErr;
            };
        }
    }
}
#endif

#ifndef TAK_ENGINE_MODEL_MESH_H_INCLUDED
#define TAK_ENGINE_MODEL_MESH_H_INCLUDED

#include <memory>

#include "feature/Envelope2.h"
#include "math/Point2.h"
#include "model/Material.h"
#include "model/VertexDataLayout.h"
#include "port/Platform.h"
#include "util/Error.h"

namespace TAK {
    namespace Engine {
        namespace Util {
            class MemBuffer2;
        }

        namespace Model {
            enum WindingOrder
            {
                TEWO_Clockwise,
                TEWO_CounterClockwise,
                TEWO_Undefined,
            };

            enum DrawMode
            {
                TEDM_Triangles,
                TEDM_TriangleStrip,
                TEDM_Points,
                TEDM_Lines,
                TEDM_LineStrip,
            };

            class ENGINE_API Mesh
            {
            public :
                virtual ~Mesh() NOTHROWS = 0;

                /**
                 * Returns the number of vertices in the mesh.
                 *
                 * @return  The number of vertices in the mesh.
                 */
                virtual std::size_t getNumVertices() const NOTHROWS = 0;

                /**
                 * Returns the number of faces (triangles) in the mesh.
                 *
                 * @return  The number of faces in the mesh.
                 */
                virtual std::size_t getNumFaces() const NOTHROWS = 0;

                /**
                 * Returns a flag indicating whether or not the mesh has indexed vertex
                 * data.
                 *
                 * @return  <code>true</code> if the mesh has indexed vertex data,
                 *          <code>false</code> otherwise.
                 */
                virtual bool isIndexed() const NOTHROWS = 0;

                /**
                 * Returns the position attribute data for the specified vertex.
                 *
                 * @param i     The vertex number
                 * @param xyz   Returns the vertex position
                 */
                virtual Util::TAKErr getPosition(Math::Point2<double> *value, const std::size_t index) const NOTHROWS = 0;

                /**
                 * Returns the texture coordinate attribute data for the specified vertex.
                 *
                 * @param i     The vertex number
                 * @param uv    Returns the vertex texture coordinate. The <I>u</I>
                 *              component is returned via <code>uv.x</code> and the
                 *              <I>v</I> component is returned via <code>uv.y</code>
                 */
                virtual Util::TAKErr getTextureCoordinate(Math::Point2<float> *value, const VertexAttribute texAttr, const std::size_t index) const NOTHROWS = 0;
                /**
                 * Returns the normal attribute data for the specified vertex.
                 *
                 * @param i     The vertex number
                 * @param xyz   Returns the vertex normal
                 */
                virtual Util::TAKErr getNormal(Math::Point2<float> *value, const std::size_t index) const NOTHROWS = 0;

                /**
                 * Returns the color attribute data for the specified vertex as a 32-bit
                 * packed ARGB value.
                 *
                 * @param i The vertex number
                 *
                 * @return  The color attribute data for the specified vertex as a 32-bit
                 *          packed ARGB value.
                 */
                virtual Util::TAKErr getColor(unsigned int *value, const std::size_t index) const NOTHROWS = 0;
                virtual Util::TAKErr getVertexAttributeType(Port::DataType *value, const unsigned int attr) const NOTHROWS = 0;

                /**
                 * Returns the index type. Must be one of:
                 * <UL>
                 *     <LI>{@link Short#TYPE}</LI>
                 *     <LI>{@link Integer#TYPE}</LI>
                 *     <LI><code>null</code></LI>
                 * </UL>
                 * @return The index type, or <code>null</code> if the mesh is not
                 *         indexed.
                 */
                virtual Util::TAKErr getIndexType(Port::DataType *value) const NOTHROWS = 0;

                /**
                 * Returns the specified index element.
                 *
                 * @param i The index offset
                 *
                 * @return  The specified index element.
                 */
                virtual Util::TAKErr getIndex(std::size_t *value, const std::size_t index) const NOTHROWS = 0;

                /**
                 * Returns a {@link Buffer} containing the index data. The first element of
                 * the indices is expected to start at the <I>base pointer of the buffer,
                 * ignoring position, plus the index offset.</I> All components per vertex
                 * for the attribute data are expected to be packed contiguously.
                 *
                 * <P>The returned {@link Buffer} MUST:
                 * <UL>
                 *     <LI>Be direct</LI>
                 *     <LI>Be in native order</LI>
                 *     <LI>Not be a slice OR be a slice created from a
                 *         <code>Buffer</code> whose <I>position</I> was
                 *         <code>0</code></LI>
                 * </UL>
                 * @return
                 */
                virtual const void *getIndices() const NOTHROWS = 0;

                /**
                 * Returns the offset, in bytes, from the first byte of the {@link Buffer}
                 * returned by {@link #getIndices()} of the first index element.
                 * @return
                 */
                virtual std::size_t getIndexOffset() const NOTHROWS = 0;

                virtual std::size_t getNumIndices() const NOTHROWS = 0;

                /**
                 * Returns a {@link Buffer} containing the specified vertex data. The first
                 * byte of the data of the specified vertex attribute is expected to start
                 * at the <I>base pointer of the buffer, ignoring position, plus the vertex
                 * offset for the attribute type.</I> All components per vertex for the
                 * attribute data are expected to be packed contiguously; attribute data
                 * for consecutive components will be separated by the respective stride
                 * bytes as defined by the {@link VertexDataLayout}.
                 *
                 * <P>The returned {@link Buffer} MUST:
                 * <UL>
                 *     <LI>Be direct</LI>
                 *     <LI>Be in native order</LI>
                 *     <LI>Not be a slice OR be a slice created from a
                 *         <code>Buffer</code> whose <I>position</I> was
                 *         <code>0</code></LI>
                 * </UL>
                 * @param attr  The attribute type. Must be only one of
                 *              <UL>
                 *                  <LI>{@link #VERTEX_ATTR_COLOR}</LI>
                 *                  <LI>{@link #VERTEX_ATTR_NORMAL}</LI>
                 *                  <LI>{@link #VERTEX_ATTR_POSITION}</LI>
                 *                  <LI>{@link #VERTEX_ATTR_TEXCOORD}</LI>
                 *              </UL>
                 * @return  The {@link Buffer} that contains the specified vertex attribute.
                 */
                virtual Util::TAKErr getVertices(const void **value, const std::size_t attr) const NOTHROWS = 0;
                virtual WindingOrder getFaceWindingOrder() const NOTHROWS = 0;
                virtual DrawMode getDrawMode() const NOTHROWS = 0;
                virtual const Feature::Envelope2 &getAABB() const NOTHROWS = 0;
                virtual const VertexDataLayout &getVertexDataLayout() const NOTHROWS = 0;
                virtual std::size_t getNumMaterials() const NOTHROWS = 0;
                virtual Util::TAKErr getMaterial(Material *value, const std::size_t index) const NOTHROWS = 0;

                virtual Util::TAKErr getBuffer(const Util::MemBuffer2 **buffer, size_t index) const NOTHROWS = 0;
                virtual size_t getNumBuffers() const NOTHROWS = 0;
            };

            typedef std::unique_ptr<Mesh, void(*)(const Mesh *)> MeshPtr;
            typedef std::unique_ptr<const Mesh, void(*)(const Mesh *)> MeshPtr_const;

            ENGINE_API Util::TAKErr Mesh_transform(MeshPtr &value, const Mesh &src, const VertexDataLayout &dstLayout) NOTHROWS;
        }
    }
}
#endif

#ifndef TAK_ENGINE_RENDERER_SKIRT_H_INCLUDED
#define TAK_ENGINE_RENDERER_SKIRT_H_INCLUDED

#include "port/Platform.h"
#include "renderer/GL.h"
#include "util/Error.h"
#include "util/MemBuffer2.h"

namespace TAK {
    namespace Engine {
        namespace Renderer {
            template<class IndexType>
            ENGINE_API inline Util::TAKErr Skirt_createIndices(Util::MemBuffer2 &skirtIndices, const int mode, const Util::MemBuffer2 *edgeIndices, const std::size_t edgeCount, const IndexType nextIdx) NOTHROWS
            {
                Util::TAKErr code(Util::TE_Ok);
                switch(mode) {
                    case GL_TRIANGLE_STRIP :
                    case GL_TRIANGLES :
                        break;
                    default :
                        return Util::TE_InvalidArg;
                }
                if(edgeCount < 2u)
                    return Util::TE_InvalidArg;

                // emit indices for skirt mesh geometry
                if(mode == GL_TRIANGLE_STRIP) {
                    // for a triangle strip, we emit the top and bottom for each vertex
                    // in the exterior edge
                    for (std::size_t i = 0; i < edgeCount; i++) {
                        std::size_t index;
                        if(edgeIndices) {
                            index = (std::size_t)reinterpret_cast<const IndexType *>(edgeIndices->get()+edgeIndices->position())[i];
                        } else{
                            index = i;
                        }

                        code = skirtIndices.put<IndexType>((IndexType)index);
                        TE_CHECKBREAK_CODE(code);
                        code = skirtIndices.put<IndexType>((IndexType)(nextIdx+i));
                        TE_CHECKBREAK_CODE(code);
                    }
                    TE_CHECKRETURN_CODE(code);
                } else if(mode == GL_TRIANGLES) {
                    // for triangles, we emit two triangles for each segment in the
                    // exterior edge
                    for (std::size_t i = 0; i < edgeCount-1; i++) {
                        std::size_t index0;
                        std::size_t index1;
                        if(edgeIndices) {
                            const IndexType *edgeIndicesPtr = reinterpret_cast<const IndexType *>(edgeIndices->get()+edgeIndices->position());
                            index0 = (std::size_t)edgeIndicesPtr[i];
                            index1 = (std::size_t)edgeIndicesPtr[i+1u];
                        } else{
                            index0 = i;
                            index1 = i+1u;
                        }

                        // a --- b
                        // | \   |
                        // |  \  |
                        // |   \ |
                        // c --- d
                        const std::size_t aidx = index0;
                        const std::size_t bidx = index1;
                        const std::size_t cidx = nextIdx+i;
                        const std::size_t didx = nextIdx+(i+1);

                        code = skirtIndices.put<IndexType>((IndexType)cidx);
                        TE_CHECKBREAK_CODE(code);
                        code = skirtIndices.put<IndexType>((IndexType)didx);
                        TE_CHECKBREAK_CODE(code);
                        code = skirtIndices.put<IndexType>((IndexType)aidx);
                        TE_CHECKBREAK_CODE(code);

                        code = skirtIndices.put<IndexType>((IndexType)aidx);
                        TE_CHECKBREAK_CODE(code);
                        code = skirtIndices.put<IndexType>((IndexType)didx);
                        TE_CHECKBREAK_CODE(code);
                        code = skirtIndices.put<IndexType>((IndexType)bidx);
                        TE_CHECKBREAK_CODE(code);
                    }
                    TE_CHECKRETURN_CODE(code);
                } // else illegal state

                skirtIndices.flip();

                return code;
            }
            /**
             * Creates a new skirt.
             *
             * <P>Skirt vertices will be appended to the supplied vertex buffer,
             * starting at the current limit. On return, the <I>position</I> of the
             * vertex buffer will be unmodified, but the <I>limit</I> will be updated,
             * reflecting the vertex data for skirt that was appended.
             *
             * <P>If the exterior edge forms a loop, the supplied
             * <code>edgeIndices</code> should repeat the first-index-as-last-index to
             * close.
             *
             * <P>If the <code>mode</code> is {@link GLES30#GL_TRIANGLE_STRIP} and the
             * skirt indices are being appended to draw indices for the entire mesh,
             * the caller <B>MUST</B> insert the indices for a degenerate triangle
             * before invoking this method. The first index emitted will always be
             * the first edge vertex index (<code>0</code> if <code>edgeIndices</code>
             * is <code>null</code> or <code>edgeIndices.get(0)</code> otherwise).
             *
             * @param mode          The mode, accepts {@link GLES30#GL_TRIANGLES} or {@link GLES30#GL_TRIANGLE_STRIP}
             * @param stride        The stride, in bytes between consecutive vertices. If <code>0</code> a stride of <code>3</code> elements is assumed.
             * @param vertices      The vertices, may not be <code>null</code>. Holds the source vertices for the mesh and will accept the output skirt vertices.
             * @param edgeIndices   The indices defining the exterior edge of the mesh, if <code>null</code>, the first <code>count</code> vertices are assumed to be the exterior edge vertices
             * @param count         The exterior edge vertex count, must be greater than <code>1</code>.
             * @param skirtIndices  The buffer to store the generated indices for the skirt mesh geometry, may not be <code>null</code>
             */
            template<class VertexType, class IndexType>
            ENGINE_API inline Util::TAKErr Skirt_createVertices(Util::MemBuffer2 &vertices, const int mode, const std::size_t stride_, const Util::MemBuffer2 *edgeIndices, const std::size_t count, const VertexType height) NOTHROWS
            {
                Util::TAKErr code(Util::TE_Ok);
                switch(mode) {
                    case GL_TRIANGLE_STRIP :
                    case GL_TRIANGLES :
                        break;
                    default :
                        return Util::TE_InvalidArg;
                }
                if(count < 2u)
                    return Util::TE_InvalidArg;
                std::size_t stride = stride_;
                if(!stride)
                    stride = 3u*sizeof(VertexType);
                else if(stride < (3u*sizeof(VertexType)))
                    return Util::TE_InvalidArg;

                const std::size_t basePos = vertices.position();
                const std::size_t baseLim = vertices.limit();
                code = vertices.position(vertices.limit());
                TE_CHECKRETURN_CODE(code);
                code = vertices.limit(vertices.size());
                TE_CHECKRETURN_CODE(code);

                const std::size_t skip = stride-(3u*sizeof(VertexType));

                Util::MemBuffer2 srcVertices(vertices.get(), vertices.size());
                code = srcVertices.position(basePos);
                TE_CHECKRETURN_CODE(code);

                // write skirt vertices to vertex buffer
                for (std::size_t i = 0; i < count; i++) {
                    std::size_t index;
                    if(edgeIndices) {
                        index = (std::size_t)reinterpret_cast<const IndexType *>(edgeIndices->get()+edgeIndices->position())[i];
                    } else{
                        index = i;
                    }

                    // position 'srcVertices' at current index
                    code = srcVertices.position(index*stride);
                    TE_CHECKBREAK_CODE(code);

                    // top vertex (from exterior edge)
                    VertexType x_t;
                    code = srcVertices.get<VertexType>(&x_t);
                    TE_CHECKBREAK_CODE(code);
                    VertexType y_t;
                    code = srcVertices.get<VertexType>(&y_t);
                    TE_CHECKBREAK_CODE(code);
                    VertexType z_t;
                    code = srcVertices.get<VertexType>(&z_t);
                    TE_CHECKBREAK_CODE(code);

                    // emit skirt vertex
                    const VertexType x_b = x_t;
                    const VertexType y_b = y_t;
                    const VertexType z_b = z_t - height;

                    code = vertices.put<VertexType>(x_b);
                    TE_CHECKBREAK_CODE(code);
                    code = vertices.put<VertexType>(y_b);
                    TE_CHECKBREAK_CODE(code);
                    code = vertices.put<VertexType>(z_b);
                    TE_CHECKBREAK_CODE(code);
                    if(skip > 0) {
                        code = vertices.position(vertices.position() + skip);
                        TE_CHECKBREAK_CODE(code);
                    }
                }
                TE_CHECKRETURN_CODE(code);

                vertices.flip();
                code = vertices.position(basePos);
                TE_CHECKRETURN_CODE(code);

                return code;
            }

            /**
             * Creates a new skirt.
             *
             * <P>Skirt vertices will be appended to the supplied vertex buffer,
             * starting at the current limit. On return, the <I>position</I> of the
             * vertex buffer will be unmodified, but the <I>limit</I> will be updated,
             * reflecting the vertex data for skirt that was appended.
             *
             * <P>If the exterior edge forms a loop, the supplied
             * <code>edgeIndices</code> should repeat the first-index-as-last-index to
             * close.
             *
             * <P>If the <code>mode</code> is {@link GLES30#GL_TRIANGLE_STRIP} and the
             * skirt indices are being appended to draw indices for the entire mesh,
             * the caller <B>MUST</B> insert the indices for a degenerate triangle
             * before invoking this method. The first index emitted will always be
             * the first edge vertex index (<code>0</code> if <code>edgeIndices</code>
             * is <code>null</code> or <code>edgeIndices.get(0)</code> otherwise).
             *
             * @param mode          The mode, accepts {@link GLES30#GL_TRIANGLES} or {@link GLES30#GL_TRIANGLE_STRIP}
             * @param stride        The stride, in bytes between consecutive vertices. If <code>0</code> a stride of <code>3</code> elements is assumed.
             * @param vertices      The vertices, may not be <code>null</code>. Holds the source vertices for the mesh and will accept the output skirt vertices.
             * @param edgeIndices   The indices defining the exterior edge of the mesh, if <code>null</code>, the first <code>count</code> vertices are assumed to be the exterior edge vertices
             * @param count         The exterior edge vertex count, must be greater than <code>1</code>.
             * @param skirtIndices  The buffer to store the generated indices for the skirt mesh geometry, may not be <code>null</code>
             */
            template<class VertexType, class IndexType>
            ENGINE_API inline Util::TAKErr Skirt_create(Util::MemBuffer2 &vertices, Util::MemBuffer2 &skirtIndices, const int mode, const std::size_t stride_, const Util::MemBuffer2 *edgeIndices, const std::size_t count, const VertexType height) NOTHROWS
            {
                Util::TAKErr code(Util::TE_Ok);
                switch(mode) {
                    case GL_TRIANGLE_STRIP :
                    case GL_TRIANGLES :
                        break;
                    default :
                        return Util::TE_InvalidArg;
                }
                if(count < 2u)
                    return Util::TE_InvalidArg;
                std::size_t stride = stride_;
                if(!stride)
                    stride = 3u*sizeof(VertexType);
                else if(stride < (3u*sizeof(VertexType)))
                    return Util::TE_InvalidArg;

                const std::size_t baseLim = vertices.limit();

                // emit vertices for skirt mesh geometry
                code = Skirt_createVertices<VertexType, IndexType>(vertices, mode, stride_, edgeIndices, count, height);
                TE_CHECKRETURN_CODE(code);

                // emit indices for skirt mesh geometry
                code = Skirt_createIndices<IndexType>(skirtIndices, mode, edgeIndices, count, static_cast<IndexType>(baseLim / stride));
                TE_CHECKRETURN_CODE(code);

                return code;
            }

            /**
             * Returns the number of skirt vertices that will be output.
             * @param count The number of exterior edge vertices
             * @return  The number of skirt vertices that will be output to the vertex
             *          buffer as a result of
             *          {@link #create(int, int, FloatBuffer, ShortBuffer, int, ShortBuffer, float)}}
             */
            ENGINE_API std::size_t Skirt_getNumOutputVertices(std::size_t count) NOTHROWS;

            /**
             * Returns the number of indices that will be output to define the skirt mesh.
             * @param mode  The mesh draw mode
             * @param count The number of exterior edge vertices
             * @return
             */
            ENGINE_API Util::TAKErr Skirt_getNumOutputIndices(std::size_t *value, const int mode, const std::size_t count) NOTHROWS;

        }
    }
}

#endif // TAK_ENGINE_RENDERER_SKIRT_H_INCLUDED

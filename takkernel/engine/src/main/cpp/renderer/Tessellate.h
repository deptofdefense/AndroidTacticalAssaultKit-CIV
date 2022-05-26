#ifndef TAK_ENGINE_RENDERER_TESSELLATE_H_INCLUDED
#define TAK_ENGINE_RENDERER_TESSELLATE_H_INCLUDED

#include <memory>

#include "math/Point2.h"
#include "port/Platform.h"
#include "util/Error.h"
#include "util/Memory.h"
#include "util/MemBuffer2.h"

namespace TAK {
    namespace Engine {
        namespace Renderer {
            struct ENGINE_API VertexData {
                void *data;
                std::size_t stride;
                std::size_t size;
            };
            typedef std::unique_ptr<VertexData, void(*)(const VertexData *)> VertexDataPtr;
            typedef std::unique_ptr<VertexData, void(*)(const VertexData *)> VertexDataPtr_const;

            ENGINE_API Util::TAKErr VertexData_allocate(VertexDataPtr &value, const std::size_t stride, const std::size_t size, const std::size_t count) NOTHROWS;

            /**
             * Reads the next vertex, advancing the pointer through the full vertex stride
             */
            typedef Util::TAKErr (*ReadVertexFn)(Math::Point2<double> *value, Util::MemBuffer2 &membuf, const VertexData &layout);

            template<class T>
            Util::TAKErr readVertexImpl_stride(Math::Point2<double> *value, Util::MemBuffer2 &membuf, const VertexData &layout) NOTHROWS
            {
                Util::TAKErr code(Util::TE_Ok);
                T xyz[3u];
                xyz[2u] = (T)0.0;
                code = membuf.get<T>(xyz, layout.size);
                TE_CHECKRETURN_CODE(code);
                code = membuf.position(membuf.position() + (layout.stride - (layout.size - sizeof(T))));
                TE_CHECKRETURN_CODE(code);
                value->x = xyz[0];
                value->y = xyz[1];
                value->z = xyz[2];
                return code;
            }

            template<class T>
            Util::TAKErr readVertexImpl_nostride(Math::Point2<double> *value, Util::MemBuffer2 &membuf, const VertexData &layout) NOTHROWS
            {
                Util::TAKErr code(Util::TE_Ok);
                T xyz[3u];
                xyz[2u] = (T)0.0;
                code = membuf.get<T>(xyz, layout.size);
                TE_CHECKRETURN_CODE(code);
                value->x = xyz[0];
                value->y = xyz[1];
                value->z = xyz[2];
                return code;
            }

            /**
             * Reads the next vertex, advancing the pointer through the full vertex stride
             */
            typedef Util::TAKErr (*WriteVertexFn)(Util::MemBuffer2 &membuf, const VertexData &layout, const Math::Point2<double> &xyz);

            template<class T>
            Util::TAKErr writeVertexImpl_stride(Util::MemBuffer2 &membuf, const VertexData &layout, const Math::Point2<double> &p) NOTHROWS
            {
                Util::TAKErr code(Util::TE_Ok);
                T xyz[3u];
                xyz[0] = static_cast<T>(p.x);
                xyz[1] = static_cast<T>(p.y);
                xyz[2] = static_cast<T>(p.z);
                code = membuf.put<T>(xyz, layout.size);
                TE_CHECKRETURN_CODE(code);
                code = membuf.position(membuf.position() + (layout.stride - (layout.size - sizeof(T))));
                TE_CHECKRETURN_CODE(code);

                return code;
            }

            template<class T>
            Util::TAKErr writeVertexImpl_nostride(Util::MemBuffer2 &membuf, const VertexData &layout, const Math::Point2<double> &p) NOTHROWS
            {
                Util::TAKErr code(Util::TE_Ok);
                T xyz[3u];
                xyz[0] = static_cast<T>(p.x);
                xyz[1] = static_cast<T>(p.y);
                xyz[2] = static_cast<T>(p.z);
                code = membuf.put<T>(xyz, layout.size);
                TE_CHECKRETURN_CODE(code);

                return code;
            }

            typedef double(*DistanceFn)(const Math::Point2<double> &a, const Math::Point2<double> &b);
            typedef Math::Point2<double> (*DirectionFn)(const Math::Point2<double> &a, const Math::Point2<double> &b);
            typedef Math::Point2<double> (*InterpolateFn)(const Math::Point2<double> &origin, const Math::Point2<double> &dir, const double distance);
			typedef Util::TAKErr (*IntersectFn) (Math::Point2<double> &intersect,
				const Math::Point2<double> &origin1, const Math::Point2<double> &dir1,
				const Math::Point2<double> &origin2, const Math::Point2<double> &dir2);

            struct ENGINE_API Algorithm {
                DistanceFn distance;
                DirectionFn direction;
                InterpolateFn interpolate;
				IntersectFn intersect;
            };

            ENGINE_API Algorithm &Tessellate_CartesianAlgorithm() NOTHROWS;
            ENGINE_API Algorithm &Tessellate_WGS84Algorithm() NOTHROWS;

            /**
             * Tessellates the input linestring
             *
             * @param value
             * @param dstCount
             * @param src
             * @param count
             * @param threshold
             * @param algorithm
             */
            template<class T>
            Util::TAKErr Tessellate_linestring(VertexDataPtr &value, std::size_t *dstCount, const VertexData &src, const std::size_t count, const double threshold, Algorithm &algorithm) NOTHROWS
            {
                if(!dstCount)
                    return Util::TE_InvalidArg;
                if(!src.data)
                    return Util::TE_InvalidArg;
                if(count < 2u)
                    return Util::TE_Done;
                if(sizeof(T)*src.size > src.stride)
                    return Util::TE_InvalidArg;

                Util::TAKErr code(Util::TE_Ok);
                const uint8_t *srcData = reinterpret_cast<const uint8_t *>(src.data);

                *dstCount = count;
#define __TE_TESSELLATE_COMPUTE_DST_COUNT(axv, ayv, azv, bxv, byv, bzv) \
    for(std::size_t i = 1u; i < count; i++) { \
        const T *srca = reinterpret_cast<const T *>(srcData + (src.stride*(i-1u))); \
        const Math::Point2<double> a(axv, ayv, azv); \
        const T *srcb = reinterpret_cast<const T *>(srcData + (src.stride*i)); \
        const Math::Point2<double> b(bxv, byv, bzv); \
        const double distance = algorithm.distance(a, b); \
        *dstCount += (std::size_t)(distance/threshold); \
    }

                if(src.size == 2u) {
                    __TE_TESSELLATE_COMPUTE_DST_COUNT(srca[0], srca[1], 0.0, srcb[0], srcb[1], 0.0)
                } else if(src.size == 3u) {
                    __TE_TESSELLATE_COMPUTE_DST_COUNT(srca[0], srca[1], srca[2], srcb[0], srcb[1], srcb[2])
                } else {
                    return Util::TE_IllegalState;
                }
#undef __TE_TESSELLATE_COMPUTE_DST_COUNT

                if(*dstCount == count)
                    return Util::TE_Done;

                code = VertexData_allocate(value, src.stride, src.size, *dstCount);
                uint8_t *dst = reinterpret_cast<uint8_t *>(value->data);

                if(src.size == 2u) {
                    for(std::size_t i = 1u; i < count; i++) {
                        const T *srca = reinterpret_cast<const T *>(srcData + (src.stride*(i-1u)));
                        const Math::Point2<double> a(srca[0], srca[1], 0.0);
                        const T *srcb = reinterpret_cast<const T *>(srcData + (src.stride*i));
                        const Math::Point2<double> b(srcb[0], srcb[1], 0.0);
                        /* emit 'a' */
                        T *dstt = reinterpret_cast<T *>(dst);
                        dstt[0] = srca[0];
                        dstt[1] = srca[1];
                        /* advance 'dst' */
                        dst += value->stride;
                        const double distance = algorithm.distance(a, b);
                        if(distance > threshold) {
                            /* interpolate between 'a' and 'b' */
                            const std::size_t numPts = (std::size_t)(distance / threshold);
                            const Math::Point2<double> dir = algorithm.direction(a, b);
                            for(std::size_t j = 0u; j < numPts; j++) {
                                Math::Point2<double> p;
                                p = algorithm.interpolate(a, dir, (j+1u)*(distance/(numPts+1u)));

                                /* emit interpolated point */
                                dstt = reinterpret_cast<T *>(dst);
                                dstt[0] = static_cast<T>(p.x);
                                dstt[1] = static_cast<T>(p.y);
                                /* advance 'dst' */
                                dst += value->stride;
                            }
                        }
                    }

                    const T *srcb = reinterpret_cast<const T *>(srcData + (src.stride*(count-1u)));

                    // emit last point
                    T *dstt = reinterpret_cast<T *>(dst);
                    dstt[0] = srcb[0];
                    dstt[1] = srcb[1];
                } else if(src.size == 3u) {
                    for(std::size_t i = 1u; i < count; i++) {
                        const T *srca = reinterpret_cast<const T *>(srcData + (src.stride*(i-1u)));
                        const Math::Point2<double> a(srca[0], srca[1], srca[2]);
                        const T *srcb = reinterpret_cast<const T *>(srcData + (src.stride*i));
                        const Math::Point2<double> b(srcb[0], srcb[1], srcb[2]);
                        /* emit 'a' */
                        T *dstt = reinterpret_cast<T *>(dst);
                        dstt[0u] = srca[0u];
                        dstt[1u] = srca[1u];
                        dstt[2u] = srca[2u];
                        /* advance 'dst' */
                        dst += value->stride;
                        const double distance = algorithm.distance(a, b);
                        if(distance > threshold) {
                            /* interpolate between 'a' and 'b' */
                            const std::size_t numPts = (std::size_t)(distance / threshold);
                            const Math::Point2<double> dir = algorithm.direction(a, b);
                            for(std::size_t j = 0u; j < numPts; j++) {
                                Math::Point2<double> p;
                                p = algorithm.interpolate(a, dir, (j+1u)*(distance/(numPts+1u)));

                                /* emit interpolated point */
                                dstt = reinterpret_cast<T *>(dst);
                                dstt[0u] = static_cast<T>(p.x);
                                dstt[1u] = static_cast<T>(p.y);
                                dstt[2u] = static_cast<T>(p.z);
                                /* advance 'dst' */
                                dst += value->stride;
                            }
                        }
                    }

                    const T *srcb = reinterpret_cast<const T *>(srcData + (src.stride*(count-1u)));

                    // emit last point
                    T *dstt = reinterpret_cast<T *>(dst);
                    dstt[0] = srcb[0];
                    dstt[1] = srcb[1];
                    dstt[2] = srcb[2];
                } else {
                    return Util::TE_IllegalState;
                }

                return Util::TE_Ok;
            } // Tessellate_linestring

            ENGINE_API Util::TAKErr Tessellate_polygon(VertexDataPtr &value, std::size_t *dstCount, const VertexData &src, const std::size_t count, const double threshold, Algorithm &algorithm, ReadVertexFn vertRead, WriteVertexFn vertWrite) NOTHROWS;
            ENGINE_API Util::TAKErr Tessellate_polygon(VertexDataPtr &value, std::size_t *dstCount, const VertexData &src, const int *counts, const int *startIndices, const int numPolygons, const double threshold, Algorithm &algorithm, ReadVertexFn vertRead, WriteVertexFn vertWrite) NOTHROWS;

            template<class T>
            Util::TAKErr Tessellate_polygon(VertexDataPtr &value, std::size_t *dstCount, const VertexData &src, const std::size_t count, const double threshold, Algorithm &algorithm) NOTHROWS
            {
                if(sizeof(T)*src.size > src.stride)
                    return Util::TE_InvalidArg;

                ReadVertexFn readV;
                WriteVertexFn writeV;
                if (src.stride > sizeof(T)*src.size) {
                    readV = readVertexImpl_stride<T>;
                    writeV = writeVertexImpl_stride<T>;
                } else {
                    readV = readVertexImpl_nostride<T>;
                    writeV = writeVertexImpl_nostride<T>;
                }

                return Tessellate_polygon(value, dstCount, src, count, threshold, algorithm, readV, writeV);
            }

            template<class T>
            Util::TAKErr Tessellate_polygon(VertexDataPtr &value, std::size_t *dstCount, const VertexData &src, const int *counts, const int *startIndices, const int numPolygons, const double threshold, Algorithm &algorithm) NOTHROWS
            {
                if(sizeof(T)*src.size > src.stride)
                    return Util::TE_InvalidArg;

                ReadVertexFn readV;
                WriteVertexFn writeV;
                if (src.stride > sizeof(T)*src.size) {
                    readV = readVertexImpl_stride<T>;
                    writeV = writeVertexImpl_stride<T>;
                } else {
                    readV = readVertexImpl_nostride<T>;
                    writeV = writeVertexImpl_nostride<T>;
                }

                return Tessellate_polygon(value, dstCount, src, counts, startIndices, numPolygons, threshold, algorithm, readV, writeV);
            }
        }
    }
}
#endif

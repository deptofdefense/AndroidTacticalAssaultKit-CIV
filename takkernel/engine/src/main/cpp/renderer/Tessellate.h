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
                void* data{ nullptr };
                /** The amount of bytes needed to increment the data pointer to advance to the next vertex */
                std::size_t stride{ 0u };
                /** The number of components each vertex contains. Should be 2 or 3. */
                std::size_t size{ 0u };
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

            class ENGINE_API TessellateCallback
            {
            protected :
                ~TessellateCallback() NOTHROWS;
            public :
                virtual Util::TAKErr point(const TAK::Engine::Math::Point2<double> &xyz) NOTHROWS = 0;
                virtual Util::TAKErr point(const TAK::Engine::Math::Point2<double> &xyz, const bool isExteriorVertex) NOTHROWS { return point(xyz); };
            };


            template<class T>
            Util::TAKErr Tessellate_linestring(TessellateCallback &value, const VertexData &src, const std::size_t count, const double threshold, Algorithm &algorithm) NOTHROWS
            {
                if(!src.data)
                    return Util::TE_InvalidArg;
                if(count < 2u)
                    return Util::TE_Done;
                if(sizeof(T)*src.size > src.stride)
                    return Util::TE_InvalidArg;

                Util::TAKErr code(Util::TE_Ok);
                const uint8_t *srcData = reinterpret_cast<const uint8_t *>(src.data);

                if(src.size == 2u) {
                    for(std::size_t i = 1u; i < count; i++) {
                        const T *srca = reinterpret_cast<const T *>(srcData + (src.stride*(i-1u)));
                        const Math::Point2<double> a(srca[0], srca[1], 0.0);
                        const T *srcb = reinterpret_cast<const T *>(srcData + (src.stride*i));
                        const Math::Point2<double> b(srcb[0], srcb[1], 0.0);
                        /* emit 'a' */
                        code = value.point(a);
                        TE_CHECKRETURN_CODE(code);
                        const double distance = algorithm.distance(a, b);
                        if(distance > threshold) {
                            /* interpolate between 'a' and 'b' */
                            const std::size_t numPts = (std::size_t)(distance / threshold);
                            const Math::Point2<double> dir = algorithm.direction(a, b);
                            for(std::size_t j = 0u; j < numPts; j++) {
                                Math::Point2<double> p;
                                p = algorithm.interpolate(a, dir, (j+1u)*(distance/(numPts+1u)));
                                /* emit interpolated point */
                                code = value.point(p);
                                TE_CHECKBREAK_CODE(code);
                            }
                            TE_CHECKRETURN_CODE(code);
                        }
                    }
                    const T *srcb = reinterpret_cast<const T *>(srcData + (src.stride*(count-1u)));

                    // emit last point
                    code = value.point(Math::Point2<double>((double)srcb[0], (double)srcb[1]));
                } else if(src.size == 3u) {
                    for(std::size_t i = 1u; i < count; i++) {
                        const T *srca = reinterpret_cast<const T *>(srcData + (src.stride*(i-1u)));
                        const Math::Point2<double> a(srca[0], srca[1], srca[2]);
                        const T *srcb = reinterpret_cast<const T *>(srcData + (src.stride*i));
                        const Math::Point2<double> b(srcb[0], srcb[1], srcb[2]);
                        /* emit 'a' */
                        code = value.point(a);
                        TE_CHECKRETURN_CODE(code);
                        const double distance = algorithm.distance(a, b);
                        if(distance > threshold) {
                            /* interpolate between 'a' and 'b' */
                            const std::size_t numPts = (std::size_t)(distance / threshold);
                            const Math::Point2<double> dir = algorithm.direction(a, b);
                            for(std::size_t j = 0u; j < numPts; j++) {
                                Math::Point2<double> p;
                                p = algorithm.interpolate(a, dir, (j+1u)*(distance/(numPts+1u)));
                                /* emit interpolated point */
                                code = value.point(p);
                                TE_CHECKBREAK_CODE(code);
                            }
                            TE_CHECKRETURN_CODE(code);
                        }
                    }

                    const T *srcb = reinterpret_cast<const T *>(srcData + (src.stride*(count-1u)));

                    // emit last point
                    code = value.point(Math::Point2<double>((double)srcb[0], (double)srcb[1], (double)srcb[2]));
                } else {
                    return Util::TE_IllegalState;
                }

                return code;
            } // Tessellate_linestring

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
            Util::TAKErr Tessellate_linestring(VertexDataPtr& value, std::size_t* dstCount, const VertexData& src, const std::size_t count, const double threshold, Algorithm& algorithm) NOTHROWS
            {
                Util::TAKErr code(Util::TE_Ok);
                *dstCount = count;
                const uint8_t *srcData = static_cast<const uint8_t *>(src.data);
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
                

                if (src.size == 2u) {
                    struct VertexWriter2D : public TessellateCallback
                    {
                        virtual Util::TAKErr point(const TAK::Engine::Math::Point2<double> &xyz) NOTHROWS
                        {
                            /* emit 'a' */
                            T *dstt = reinterpret_cast<T *>(dst);
                            dstt[0] = (T)xyz.x;
                            dstt[1] = (T)xyz.y;
                            /* advance 'dst' */
                            dst += stride;
                            return Util::TE_Ok;
                        }

                        uint8_t *dst{ nullptr };
                        std::size_t stride{ 0u };
                    } cb;
                    cb.dst = reinterpret_cast<uint8_t *>(value->data);
                    cb.stride = src.stride;
                    return Tessellate_linestring<T>(cb, src, count, threshold, algorithm);
                } else if (src.size == 3u) {
                    struct VertexWriter3D : public TessellateCallback
                    {
                        virtual Util::TAKErr point(const TAK::Engine::Math::Point2<double> &xyz) NOTHROWS
                        {
                            /* emit 'a' */
                            T *dstt = reinterpret_cast<T *>(dst);
                            dstt[0] = (T)xyz.x;
                            dstt[1] = (T)xyz.y;
                            dstt[2] = (T)xyz.z;
                            /* advance 'dst' */
                            dst += stride;
                            return Util::TE_Ok;

                        }
                        uint8_t *dst{ nullptr };
                        std::size_t stride{ 0u };
                    } cb;
                    cb.dst = reinterpret_cast<uint8_t *>(value->data);
                    cb.stride = src.stride;
                    return Tessellate_linestring<T>(cb, src, count, threshold, algorithm);
                } else {
                    return Util::TE_IllegalState;
                }
            }

            ENGINE_API Util::TAKErr Tessellate_polygon(VertexDataPtr &value, std::size_t *dstCount, const VertexData &src, const std::size_t count, const double threshold, Algorithm &algorithm, ReadVertexFn vertRead, WriteVertexFn vertWrite) NOTHROWS;
            ENGINE_API Util::TAKErr Tessellate_polygon(TessellateCallback &value, const VertexData &src, const std::size_t count, const double threshold, Algorithm &algorithm, ReadVertexFn vertRead) NOTHROWS;
            ENGINE_API Util::TAKErr Tessellate_polygon(VertexDataPtr &value, std::size_t *dstCount, const VertexData &src, const int *counts, const int *startIndices, const int numPolygons, const double threshold, Algorithm &algorithm, ReadVertexFn vertRead, WriteVertexFn vertWrite) NOTHROWS;
            ENGINE_API Util::TAKErr Tessellate_polygon(TessellateCallback &value, const VertexData &src, const int *counts, const int *startIndices, const int numPolygons, const double threshold, Algorithm &algorithm, ReadVertexFn vertRead) NOTHROWS;

            template<class T>
            Util::TAKErr Tessellate_polygon(TessellateCallback &value, const VertexData& src, const std::size_t count, const double threshold, Algorithm& algorithm) NOTHROWS
            {
                if(sizeof(T)*src.size > src.stride)
                    return Util::TE_InvalidArg;

                ReadVertexFn readV;
                if (src.stride > sizeof(T)*src.size) {
                    readV = readVertexImpl_stride<T>;
                } else {
                    readV = readVertexImpl_nostride<T>;
                }

                return Tessellate_polygon(value, src, count, threshold, algorithm, readV);
            }

            template<class T>
            Util::TAKErr Tessellate_polygon(VertexDataPtr &value, std::size_t *dstCount, const VertexData &src, const std::size_t count, const double threshold, Algorithm &algorithm) NOTHROWS
            {
                if (!dstCount) return Util::TE_InvalidArg;

                Util::TAKErr code(Util::TE_Ok);
                ReadVertexFn readV;
                if (src.stride > sizeof(T)*src.size) {
                    readV = readVertexImpl_stride<T>;
                } else {
                    readV = readVertexImpl_nostride<T>;
                }

                struct Count : public TessellateCallback
                {
                    virtual Util::TAKErr point(const Math::Point2<double> &) NOTHROWS override
                    {
                        n++;
                        return Util::TE_Ok;
                    }
                    std::size_t n{ 0u };
                } countcb;

                code = Tessellate_polygon(countcb, src, count, threshold, algorithm, readV);
                TE_CHECKRETURN_CODE(code);

                code = VertexData_allocate(value, src.stride, src.size, countcb.n);
                struct Assemble : public TessellateCallback
                {
                    virtual Util::TAKErr point(const Math::Point2<double>& xyz) NOTHROWS override
                    {
                        writeV(*dst, layout, xyz);
                        return Util::TE_Ok;
                    }
                    Util::MemBuffer2 *dst{ nullptr };
                    VertexData layout;
                    WriteVertexFn writeV{ nullptr };
                } assemblecb;
                if (src.stride > sizeof(T)*src.size)
                    assemblecb.writeV = writeVertexImpl_stride<T>;
                else
                    assemblecb.writeV = writeVertexImpl_nostride<T>;
                assemblecb.layout = *value;
                Util::MemBuffer2 dstbuf((uint8_t*)value->data, countcb.n* src.stride);
                assemblecb.dst = &dstbuf;

                code = Tessellate_polygon(assemblecb, src, count, threshold, algorithm, readV);
                TE_CHECKRETURN_CODE(code);

                *dstCount = countcb.n;
                return code;
            }

            template<class T>
            Util::TAKErr Tessellate_polygon(TessellateCallback &value, const VertexData &src, const int *counts, const int *startIndices, const int numPolygons, const double threshold, Algorithm &algorithm) NOTHROWS
            {
                if(sizeof(T)*src.size > src.stride)
                    return Util::TE_InvalidArg;

                ReadVertexFn readV;
                if (src.stride > sizeof(T)*src.size) {
                    readV = readVertexImpl_stride<T>;
                } else {
                    readV = readVertexImpl_nostride<T>;
                }

                return Tessellate_polygon(value, src, counts, startIndices, numPolygons, threshold, algorithm, readV);
            }
            template<class T>
            Util::TAKErr Tessellate_polygon(VertexDataPtr &value, std::size_t *dstCount, const VertexData &src, const int *counts, const int *startIndices, const int numPolygons, const double threshold, Algorithm &algorithm) NOTHROWS
            {
                Util::TAKErr code(Util::TE_Ok);
                struct Count : public TessellateCallback
                {
                    Util::TAKErr point(const Math::Point2<T>& xyz) NOTHROWS override
                    {
                        n++;
                        return Util::TE_Ok;
                    }
                    std::size_t n{ 0u };
                } countcb;

                code = Tessellate_polygon<T>(countcb, src, counts, startIndices, numPolygons, threshold, algorithm);
                TE_CHECKRETURN_CODE(code);

                code = VertexData_allocate(value, src.stride, src.size, countcb.n);
                struct Assemble : public TessellateCallback
                {
                    Util::TAKErr point(const Math::Point2<T>& xyz) NOTHROWS override
                    {
                        return writeV(*dst, layout, Math::Point2<double>((double)xyz.x, (double)xyz.y, (double)xyz.z));
                    }
                    Util::MemBuffer2 *dst{ nullptr };
                    VertexData layout;
                    WriteVertexFn writeV{ nullptr };
                } assemblecb;
                if (src.stride > sizeof(T)*src.size)
                    assemblecb.writeV = writeVertexImpl_stride<T>;
                else
                    assemblecb.writeV = writeVertexImpl_nostride<T>;
                assemblecb.layout = *value;
                Util::MemBuffer2 dstbuf((uint8_t*)value->data, countcb.n*src.stride);
                assemblecb.dst = &dstbuf;

                code = Tessellate_polygon<T>(assemblecb, src, counts, startIndices, numPolygons, threshold, algorithm);
                TE_CHECKRETURN_CODE(code);

                *dstCount = countcb.n;
                return code;                
            }

            Util::TAKErr Tessellate_polygon(TessellateCallback &callback, const VertexData *polygons, const int *polygonSizes, const int polygonCount, const size_t totalVertexCount, const double threshold, Algorithm &algorithm, ReadVertexFn vertRead);


            /*
             * Tessellates polygons with holes. 
             * 
             * @param callback The callback that will be supplied with the tessellated point data.
             * @param polygons The array of VertexData containing the polygon's ring information.
             * @param polygonSizes The array containing the number of vertices in each polygon ring.
             * @param polygonCount The total number of polygons. Should be the same as the length of `polygons` and `polygonSizes`.
             * @param winding The winding order of the polygons.
             * @param threshold The threshold used during tessellation.
             * @param algorithm The tessellation algorithm that will be used during tessellation. Can be retrieved from `Tessellate_CartesianAlgorithm()` or `Tessellate_WGS84Algorithm()`.
             *
             * @return TE_Ok on success.
             */
            template<typename T>
            Util::TAKErr Tessellate_polygon(TessellateCallback &callback, const VertexData *polygons, const int *polygonSizes, const int polygonCount, const double threshold, Algorithm &algorithm)
            {
                if (!polygons || !polygonSizes) {
                    return Util::TE_InvalidArg;
                }

                size_t totalVertexCount = 0;
                for (int i = 0; i < polygonCount; i++) {
                    if((sizeof(T) * polygons[i].size) > polygons[i].stride) {
                        return Util::TE_InvalidArg;
                    }
                    totalVertexCount += polygonSizes[i];
                }

                ReadVertexFn readV;
                // Assuming that all entries in the polygons array have the same stride and size
                if (polygons[0].stride > sizeof(T) * polygons[0].size) {
                    readV = readVertexImpl_stride<T>;
                } else {
                    readV = readVertexImpl_nostride<T>;
                }
                auto code = Tessellate_polygon(callback, polygons, polygonSizes, polygonCount, totalVertexCount, threshold, algorithm, readV);
                return code;
            }
        }
    }
}
#endif

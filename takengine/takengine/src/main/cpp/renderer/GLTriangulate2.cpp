#include "renderer/GLTriangulate2.h"

#include <list>
#include <memory>

#include "util/NonCopyable.h"

using namespace TAK::Engine::Renderer;

using namespace TAK::Engine::Util;

using namespace atakmap::util;

namespace
{
    enum WindingType
    {
        TEWT_Clockwise,
        TEWT_CounterClockwise,
    };

    template<class T>
    struct Vertex : TAK::Engine::Util::NonCopyable
    {
    public :
        Vertex(T x, T y);
    public :
        const T x;
        const T y;
        Vertex *next;
        Vertex *previous;
        int index;
        bool convex;
    };

    template<class T>
    class Polygon : TAK::Engine::Util::NonCopyable
    {
    public :
        Polygon() NOTHROWS;
    public :
        TAKErr init(const T *verts, const std::size_t vertStride, const std::size_t numVerts) NOTHROWS;
    public :
        Vertex<T> *vertices;
        std::size_t numVertices;
        bool convex;
        WindingType winding;
    private :
        Vertex<T> *createVertex(const T x, const T y) NOTHROWS;
    private :
        std::list<std::unique_ptr<Vertex<T>>> verticesPtrs;
    };

    template<class T>
    TAKErr pointInTriangle(bool *value, const Vertex<T> &pt, const Vertex<T> &v1, const Vertex<T> &v2, const Vertex<T> &v3) NOTHROWS;
    template<class T>
    TAKErr isConvex(bool *value, const Vertex<T> &vertex, const WindingType winding) NOTHROWS;
    template<class T>
    TAKErr sign(float *value, const Vertex<T> &p1, const Vertex<T> &p2, const Vertex<T> &p3) NOTHROWS;
    template<class T>
    TAKErr isEar(bool *value, const Vertex<T> *test, const Vertex<T> *vertices) NOTHROWS;

    template<class T>
    TAKErr triangulateImpl(uint16_t *indices, std::size_t *idxCount, Polygon<T> &poly) NOTHROWS;
}

TAKErr TAK::Engine::Renderer::GLTriangulate2_triangulate(uint16_t *indices, std::size_t *idxCount, const float *vertices, const std::size_t stride, const std::size_t numVerts) NOTHROWS
{
    TAKErr code(TE_Ok);

    Polygon<float> poly;
    code = poly.init(vertices, stride, numVerts);
    TE_CHECKRETURN_CODE(code);
    code = triangulateImpl(indices, idxCount, poly);
    TE_CHECKRETURN_CODE(code);

    return code;
}

TAKErr TAK::Engine::Renderer::GLTriangulate2_triangulate(uint16_t *indices, std::size_t *idxCount, const double *vertices, const std::size_t stride, const std::size_t numVerts) NOTHROWS
{
    TAKErr code(TE_Ok);

    Polygon<double> poly;
    code = poly.init(vertices, stride, numVerts);
    TE_CHECKRETURN_CODE(code);
    code = triangulateImpl(indices, idxCount, poly);
    TE_CHECKRETURN_CODE(code);

    return code;
}

namespace
{
    template<class T>
    Vertex<T>::Vertex(const T x_, const T y_) :
        x(x_),
        y(y_),
        next(nullptr),
        previous(nullptr),
        index(-1),
        convex(false)
    {}


    template<class T>
    Polygon<T>::Polygon() NOTHROWS :
        vertices(nullptr),
        numVertices(0u),
        convex(false),
        winding(TEWT_CounterClockwise)
    {}

    template<class T>
    TAKErr Polygon<T>::init(const T *verts, const std::size_t vertStride, const std::size_t numVerts) NOTHROWS
    {
        TAKErr code(TE_Ok);

        if (this->vertices)
            return TE_IllegalState;

        this->numVertices = numVerts;

        int vIdx = 0;

        std::size_t idx = 0;
        // fixed unsequenced modifications: parameter resolve order is undefined in C++
        auto x = static_cast<float>(verts[vIdx++]);
        auto y = static_cast<float>(verts[vIdx++]);
        vertices = createVertex(x, y);
        vertices->index = static_cast<int>(idx++);

        int convexness = 0;

        float edgeSum = 0.0f;
        float dx;
        float dy;

        WindingType order = TEWT_CounterClockwise;

        Vertex<T> *pointer = vertices;
        for (; idx < numVertices; idx++) {
            // fixed unsequenced modifications: parameter resolve order is undefined in C++
            x = static_cast<float>(verts[vIdx++]);
            y = static_cast<float>(verts[vIdx++]);
            pointer->next = createVertex(x, y);
            pointer->next->index = static_cast<int>(idx);
            pointer->next->previous = pointer;

            pointer = pointer->next;

            if (idx > 1) {
                code = isConvex(&pointer->previous->convex, *pointer->previous, order);
                TE_CHECKBREAK_CODE(code);
                convexness |= pointer->previous->convex ? 0x01 : 0x02;
            }

            dx = static_cast<float>(pointer->previous->x + pointer->x);
            dy = static_cast<float>(pointer->previous->y - pointer->y);

            edgeSum += dx * dy;
        }
        TE_CHECKRETURN_CODE(code);


        // link the tail to the head
        pointer->next = vertices;
        vertices->previous = pointer;

        // compute the convexness of the first two vertices
        code = isConvex(&vertices->convex, *vertices, order);
        TE_CHECKRETURN_CODE(code);
        convexness |= vertices->convex ? 0x01 : 0x02;
        code = isConvex(&pointer->convex, *pointer, order);
        TE_CHECKRETURN_CODE(code);
        convexness |= pointer->convex ? 0x01 : 0x02;

        // compute overall polygon convexness
        convex = (convexness < 3);

        // sum the last edge
        dx = static_cast<float>(vertices->previous->x + vertices->x);
        dy = static_cast<float>(vertices->previous->y - vertices->y);

        edgeSum += dx * dy;

        // determine polygon winding
        winding = (edgeSum >= 0) ? TEWT_Clockwise :
            TEWT_CounterClockwise;

        return code;
    }

    template<class T>
    Vertex<T> *Polygon<T>::createVertex(const T x, const T y) NOTHROWS
    {
        std::unique_ptr<Vertex<T>> retvalPtr;
        retvalPtr.reset(new Vertex<T>(x, y));

        Vertex<T> *retval = retvalPtr.get();
        verticesPtrs.push_back(std::move(retvalPtr));
        return retval;
    }

    template<class T>
    TAKErr isEar(bool *value, const Vertex<T> *test, const Vertex<T> *vertices) NOTHROWS
    {
        TAKErr code(TE_Ok);

        const Vertex<T> *iter = vertices;
        const Vertex<T> *v;
        do {
            v = iter;
            iter = iter->next;
            if (v == test || v == test->previous || v == test->next)
                continue;
            bool inTriangle;
            code = pointInTriangle(&inTriangle, *v, *test->previous, *test, *test->next);
            TE_CHECKBREAK_CODE(code);
            if (inTriangle) {
                *value = false;
                return TE_Ok;
            }
        } while (iter != vertices);
        TE_CHECKRETURN_CODE(code);

        *value = true;

        return code;
    }

    template<class T>
    TAKErr sign(float *value, const Vertex<T> &p1, const Vertex<T> &p2, const Vertex<T> &p3) NOTHROWS
    {
        *value = static_cast<float>((p1.x - p3.x) * (p2.y - p3.y) - (p2.x - p3.x) * (p1.y - p3.y));
        return TE_Ok;
    }

    template<class T>
    TAKErr pointInTriangle(bool *value, const Vertex<T> &pt, const Vertex<T> &v1, const Vertex<T> &v2, const Vertex<T> &v3) NOTHROWS
    {
        TAKErr code(TE_Ok);

        float s;

        code = sign(&s, pt, v1, v2);
        TE_CHECKRETURN_CODE(code);
        const bool b1 = s < 0.0f;
        code = sign(&s, pt, v2, v3);
        TE_CHECKRETURN_CODE(code);
        const bool b2 = s < 0.0f;
        code = sign(&s, pt, v3, v1);
        TE_CHECKRETURN_CODE(code);
        const bool b3 = s < 0.0f;

        *value = ((b1 == b2) && (b2 == b3));
        return code;
    }

    template<class T>
    TAKErr isConvex(bool *value, const Vertex<T> &vertex, const WindingType winding) NOTHROWS
    {
        const Vertex<T> &a = *vertex.previous;
        const Vertex<T> &b = vertex;
        const Vertex<T> &c = *vertex.next;

        // counter-clockwise, polygon interior is on left; clockwise, polygon
        // interior is on right

        // side greater than zero, point to left of line; side less than zero,
        // point to right of line
        const T side = (c.x - a.x) * (b.y - a.y) - (c.y - a.y) * (b.x - a.x);

        if (winding == TEWT_CounterClockwise && side > 0)
            *value = false;
        else if (winding == TEWT_Clockwise && side < 0)
            *value = false;
        else
            *value = true;

        return TE_Ok;
    }

    template<class T>
    TAKErr triangulateImpl(uint16_t *indices, std::size_t *idxCount, Polygon<T> &poly) NOTHROWS
    {
        TAKErr code(TE_Ok);

        const uint16_t *head = indices;
        Vertex<T> *iter = poly.vertices;
        Vertex<T> *start = iter;
        while (poly.numVertices > 3) {
            bool vertIsConvex;
            code = isConvex(&vertIsConvex, *iter, poly.winding);
            TE_CHECKBREAK_CODE(code);
            bool vertIsEar;
            code = isEar(&vertIsEar, iter, poly.vertices);
            TE_CHECKBREAK_CODE(code);

            if (vertIsConvex && vertIsEar) {
                Vertex<T> *v = iter;

                *indices++ = v->previous->index;
                *indices++ = v->index;
                *indices++ = v->next->index;

                // remove from the linked list
                v->previous->next = v->next;
                v->next->previous = v->previous;
                // recompute convexness
                code = isConvex(&v->previous->convex, *v->previous, poly.winding);
                TE_CHECKBREAK_CODE(code);
                code = isConvex(&v->next->convex, *v->next, poly.winding);
                TE_CHECKBREAK_CODE(code);

                // if we removed the head of the list, update the head
                if (iter == poly.vertices)
                    poly.vertices = iter->next;

                // decrement the vertices
                poly.numVertices--;

                // move back to the previous vertex since its convexness may
                // have changed
                iter = v->previous;
                start = iter;
            } else {
                iter = iter->next;
                // XXX - THIS NEEDS TO BE FIXED !!!
                // we're going to enter an infinite loop so indicate that
                // legacy fill should be used.
                if (iter == start) {
                    Logger::log(Logger::Error, "GLTriangulate2: encountered infinite loop condition");
                    return TE_IllegalState;
                }
            }
        }
        TE_CHECKRETURN_CODE(code);

        // three vertices left, make the final triangle
        *indices++ = poly.vertices->previous->index;
        *indices++ = poly.vertices->index;
        *indices++ = poly.vertices->next->index;

        *idxCount = (indices - head);

        return code;
    }
}



#include "GLTriangulate.h"
#include <vector>
#include <stdlib.h>
#include "core/GeoPoint2.h"

using namespace atakmap::renderer;

using namespace atakmap::core;

namespace
{
    typedef enum {
        WINDING_CLOCKWISE,
        WINDING_COUNTER_CLOCKWISE
    } WindingType;

    struct Vertex {
        double x;
        double y;
        double z;
        Vertex *next;
        Vertex *previous;
        int index;
        bool convex;

        Vertex(double x, double y, double z);
    };

    struct Polygon {
        template<class T>
        Polygon(T *verts, size_t vertexCount, size_t size, size_t stride);
        Polygon(GeoPoint **verts, size_t count);
        ~Polygon();

        Vertex *vertices;
        size_t numVertices;
        bool convex;
        WindingType winding;
		std::vector<short> duplicateVertexIndicies;
    };

    bool isEar(Vertex *test, Vertex *vertices);
    double sign(Vertex *p1, Vertex *p2, Vertex *p3);
    bool pointInTriangle(Vertex *pt, Vertex *v1, Vertex *v2, Vertex *v3);
    bool isConvex(Vertex *vertex, WindingType winding);
	bool realTriangle(Polygon *poly, Vertex* vertex);
    size_t triangulateImpl(Polygon *poly, short *indices);
    void triangleFan(short* indices, std::size_t vertexCount);
    struct edge {
        uint16_t edgeCount;
        uint16_t vertexStartIndex;
        uint16_t aIndex;
        uint16_t bIndex;
        //Refers to if this is an AB (true) or a BA (false)
        bool direction;
        size_t size;
        size_t stride;


        edge();
        uint16_t access(int index, bool reverse = false);
        uint16_t access(int index, int desiredEdgeCount, bool reverse = false);
        void build(double* vertices, size_t& vertexCount, bool useGeoPoint = false);
    };

    struct edgeMap {
    private:
        std::map<int, edge> edges = std::map<int, edge>();
    public:
        size_t vertexSize {0};
        size_t vertexStride {0};
        edge* edgeAccess(uint16_t pointA, uint16_t pointB);
    };


    //Tessellates given shape. Returned vertices: All edge verticies, all internal edge vertices, all aditional internal vertices. Returned Indicies: Triangle indicies, perimeter indicies
    void tessellate(std::unique_ptr<double, void(*)(const double *)> &vertices, size_t& vertexCount, const size_t size, size_t vertexStride, std::unique_ptr<uint16_t, void(*)(const uint16_t *)> & indices, size_t& indexCount, size_t& perimeterCount, float tessellationThreshold, bool useGeoPoint = false);
}

int GLTriangulate::triangulate(GeoPoint *verts[], size_t count, short *indices) {
	Polygon poly = Polygon(verts, count);
	if (poly.convex) {
		triangleFan(indices, count);
		return INDEXED;
	}
	if (triangulateImpl(&poly, indices))
		return INDEXED;
	else
		return STENCIL;
}

int GLTriangulate::triangulate(float *verts, size_t count, short *indices) {
	Polygon poly = Polygon(verts, count, 2, 2);
	if (poly.convex) {
		triangleFan(indices, count);
		return INDEXED;
	}
	if (triangulateImpl(&poly, indices))
		return INDEXED;
	else
		return STENCIL;
}

int GLTriangulate::triangulate(std::unique_ptr<double, void(*)(const double *)>& verticies, size_t& vertexCount, const size_t size, size_t stride, std::unique_ptr<uint16_t, void(*)(const uint16_t *)>& indicies, size_t& indexCount, std::unique_ptr<uint16_t, void(*)(const uint16_t *)>& perimeterIndicies, size_t& perimeterCount, float tessellationThreshold, bool useGeoPoint) {
	indexCount = (vertexCount - 2) * 3;
			 
	std::unique_ptr<uint16_t, void(*)(const uint16_t *)> indexPtr(new uint16_t[indexCount], TAK::Engine::Util::Memory_array_deleter_const<uint16_t>);

	Polygon poly(verticies.get(), vertexCount, size, stride);

    // the returned value will always be indexed
	int value = INDEXED;

	//Reinterprets used here, as the extra space from unsigned will only ever be used for tessellating (hopefully)
	if (poly.convex) {
        // the polygon is convex and can be simply represented using at
        // triangle fan. we will still index for tessellation purposes.
		triangleFan(reinterpret_cast<short*>(indexPtr.get()), vertexCount);
	}
	else {
		size_t temp = triangulateImpl(&poly, reinterpret_cast<short*>(indexPtr.get()));
		if (temp <= 0) {
			// first-order tessellation of the polygon failed -- our only recourse
			// for rendering is to stencil the geometry per current implementation
			return STENCIL;
		}
		else
		{
			indexCount = temp;
		}
	}

	if (tessellationThreshold > 0 && vertexCount > 2) {
		tessellate(verticies, vertexCount, size, stride, indexPtr, indexCount, perimeterCount, tessellationThreshold, useGeoPoint);

        //Leaks, as memory is managed and dealocated by indicies pointer
		perimeterIndicies = std::unique_ptr<uint16_t, void(*)(const uint16_t *)>(new uint16_t[perimeterCount], TAK::Engine::Util::Memory_array_deleter_const<uint16_t>);
        memcpy(perimeterIndicies.get(), indexPtr.get() + indexCount, perimeterCount*sizeof(uint16_t));
	}

	indicies = std::move(indexPtr);

	return value;
}	

namespace
{
    Vertex::Vertex(double x, double y, double z) : x(x), y(y), z(z), next(nullptr),
        previous(nullptr), index(-1),
        convex(false)
    {
    }

	bool operator==(Vertex v1, Vertex v2) {
		return v1.x == v2.x && v2.y == v2.y && v1.index == v2.index;
	}

    template<class T>
    Polygon::Polygon(T *verts, size_t vertexCount, size_t size, size_t stride) : numVertices(vertexCount)
    {
        int vIdx = 0;
        std::size_t idx = 0;
        // fixed unsequenced modifications: parameter resolve order is undefined in C++
        T x = verts[vIdx++];
        T y = verts[vIdx++];
        T z = 0;
        if (size == 3)
            z = verts[vIdx++];
        vIdx += static_cast<int>(stride - size);

        vertices = new Vertex(x, y, z);
        vertices->index = static_cast<int>(idx);
        idx++;

        int convexness = 0;

        T edgeSum = 0.0f;
        T dx;
        T dy;

        WindingType order = WINDING_COUNTER_CLOCKWISE;

        Vertex *pointer = vertices;
        for (; idx < numVertices; idx++) {
            // fixed unsequenced modifications: parameter resolve order is undefined in C++
            x = verts[vIdx++];
            y = verts[vIdx++];
            z = 0;
            if (size == 3)
                z = verts[vIdx++];
            vIdx += static_cast<int>(stride - size);

            pointer->next = new Vertex(x, y, z);
            pointer->next->index = static_cast<int>(idx);
            pointer->next->previous = pointer;

            pointer = pointer->next;

            if (idx == 2) {
                if (!isConvex(pointer->previous, order))
                    order = WINDING_CLOCKWISE;
            }
            if (idx > 1) {
                pointer->previous->convex = isConvex(pointer->previous, order);
                convexness |= pointer->previous->convex ? 0x01 : 0x02;
            }

            dx = static_cast<T>(pointer->previous->x + pointer->x);
            dy = static_cast<T>(pointer->previous->y - pointer->y);

            edgeSum += dx * dy;
        }


        // link the tail to the head
        pointer->next = vertices;
        vertices->previous = pointer;

        // compute the convexness of the first two vertices
        vertices->convex = isConvex(vertices, order);
        convexness |= vertices->convex ? 0x01 : 0x02;
        pointer->convex = isConvex(pointer, order);
        convexness |= pointer->convex ? 0x01 : 0x02;

        // compute overall polygon convexness
        convex = (convexness < 3);

        // sum the last edge
        dx = static_cast<T>(vertices->previous->x + vertices->x);
        dy = static_cast<T>(vertices->previous->y - vertices->y);

        edgeSum += dx * dy;

        // determine polygon winding
        winding = (edgeSum >= 0) ? WINDING_CLOCKWISE :
            WINDING_COUNTER_CLOCKWISE;


		duplicateVertexIndicies = std::vector<short>();

		for (short i = 0; i < static_cast<short>(vertexCount); i++) {
			for (short j = i + 1; j < static_cast<short>(vertexCount); j++) {
				if (verts[stride * i] == verts[stride * j] &&
					verts[stride * i + 1] == verts[stride * j + 1]) {
					duplicateVertexIndicies.push_back(i);
					duplicateVertexIndicies.push_back(j);
				}
			}
		}
    }

    Polygon::Polygon(GeoPoint **verts, size_t count) : numVertices(count)
    {
        std::size_t idx = 0;
        vertices = new Vertex(verts[idx]->longitude,
            verts[idx]->latitude, verts[idx]->altitude);
        vertices->index = static_cast<int>(idx);
        idx++;

        int convexness = 0;

        double edgeSum = 0.0f;
        double dx;
        double dy;

        WindingType order = WINDING_COUNTER_CLOCKWISE;

        Vertex *pointer = vertices;
        for (; idx < numVertices; idx++) {
            pointer->next = new Vertex(verts[idx]->longitude,
                verts[idx]->latitude, verts[idx]->altitude);
            pointer->next->index = static_cast<int>(idx);
            pointer->next->previous = pointer;

            pointer = pointer->next;

            if (idx > 1) {
                pointer->previous->convex = isConvex(pointer->previous, order);
                convexness |= pointer->previous->convex ? 0x01 : 0x02;
            }

            dx = pointer->previous->x + pointer->x;
            dy = pointer->previous->y - pointer->y;

            edgeSum += dx * dy;
        }

        // link the tail to the head
        pointer->next = vertices;
        vertices->previous = pointer;

        // compute the convexness of the first two vertices
        vertices->convex = isConvex(vertices, order);
        convexness |= vertices->convex ? 0x01 : 0x02;
        pointer->convex = isConvex(pointer, order);
        convexness |= pointer->convex ? 0x01 : 0x02;

        // compute overall polygon convexness
        convex = (convexness < 3);

        // sum the last edge
        dx = vertices->previous->x + vertices->x;
        dy = vertices->previous->y - vertices->y;

        edgeSum += dx * dy;


        // determine polygon winding
        winding = (edgeSum >= 0) ? WINDING_CLOCKWISE :
            WINDING_COUNTER_CLOCKWISE;

		duplicateVertexIndicies = std::vector<short>();

		for (short i = 0; i < static_cast<short>(count); i++) {
			for (short j = i + 1; j < static_cast<short>(count); j++) {
				if (verts[0][i].latitude == verts[0][j].latitude &&
					verts[0][i].longitude == verts[0][j].longitude) {
					duplicateVertexIndicies.push_back(i);
					duplicateVertexIndicies.push_back(j);
				}
			}
		}
    }

    Polygon::~Polygon()
    {
        Vertex *v = vertices;
        Vertex *first = v;
        while (true) {
            Vertex *next = v->next;
            delete v;

            v = next;
            if (v == first)
                break;
        }
    }

    bool isEar(Vertex *test, Vertex *vertices)
    {
        Vertex *iter = vertices;
        Vertex *v;
        do {
            v = iter;
            iter = iter->next;
            if (v == test || v == test->previous || v == test->next)
                continue;
            if (pointInTriangle(v, test->previous, test, test->next))
                return false;
        } while (iter != vertices);
        return true;
    }

    double sign(Vertex *p1, Vertex *p2, Vertex *p3)
    {
        return (p1->x - p3->x) * (p2->y - p3->y) - (p2->x - p3->x) * (p1->y - p3->y);
    }

    bool pointInTriangle(Vertex *pt, Vertex *v1, Vertex *v2, Vertex *v3)
    {
        bool b1, b2, b3;

        b1 = sign(pt, v1, v2) <= 0.0f;
        b2 = sign(pt, v2, v3) <= 0.0f;
        b3 = sign(pt, v3, v1) <= 0.0f;

        return ((b1 == b2) && (b2 == b3));
    }

    bool isConvex(Vertex *vertex, WindingType winding)
    {
        Vertex *a = vertex->previous;
        Vertex *b = vertex;
        Vertex *c = vertex->next;

        // counter-clockwise, polygon interior is on left; clockwise, polygon
        // interior is on right

        // side greater than zero, point to left of line; side less than zero,
        // point to right of line
        double side = (c->x - a->x) * (b->y - a->y) - (c->y - a->y) * (b->x - a->x);

        if (winding == WINDING_COUNTER_CLOCKWISE && side >= 0)
            return false;
        else if (winding == WINDING_CLOCKWISE && side <= 0)
            return false;
        return true;
    }

	//Catches degenerate triangles
	bool realTriangle(Polygon *poly, Vertex *vertex) {
		short a = vertex->previous->index;
		short b = vertex->index;
		short c = vertex->next->index;


		for (std::size_t i = 0; i < poly->duplicateVertexIndicies.size(); i += 2) {
			if (poly->duplicateVertexIndicies[i] == a) {
				if (b == poly->duplicateVertexIndicies[i + 1] || c == poly->duplicateVertexIndicies[i + 1]) return false;
			}
			if (poly->duplicateVertexIndicies[i] == b) {
				if (a == poly->duplicateVertexIndicies[i + 1] || c == poly->duplicateVertexIndicies[i + 1]) return false;
			}
			if (poly->duplicateVertexIndicies[i] == c) {
				if (a == poly->duplicateVertexIndicies[i + 1] || b == poly->duplicateVertexIndicies[i + 1]) return false;
			}
		}
		return true;
	}
	 
	size_t triangulateImpl(Polygon *poly, short *indices) {
		Vertex *v = poly->vertices;
		std::size_t counter = 0;
		size_t indexCount = 0;
		while (poly->numVertices > 3 && counter <= poly->numVertices) {
			if (isConvex(v, poly->winding) && isEar(v, poly->vertices) && realTriangle(poly, v)) {

				*indices++ = v->previous->index;
				*indices++ = v->index;
				*indices++ = v->next->index;

				indexCount += 3;

				// remove from the linked list
				v->previous->next = v->next;
				v->next->previous = v->previous;

				//Remove degenerate points
				if (!realTriangle(poly, v->previous)) {
					Vertex* temp = v->previous;
					temp->previous->next = v->next;
					v->next->previous = temp->previous;
					v->previous = temp->previous;
					delete temp;
				}
				if (!realTriangle(poly, v->next)) {
					Vertex* temp = v->next;
					temp->next->previous = v->previous;
					v->previous->next = temp->next;
					v->next = temp->next;
					delete temp;
				}

				// recompute convexness
				v->previous->convex = isConvex(v->previous, poly->winding);
				v->next->convex = isConvex(v->next, poly->winding);

				// update the head in case we removed it
				poly->vertices = v->next;

				// decrement the vertices
				poly->numVertices--;

				// move back to the previous vertex since its convexness may
				// have changed
				Vertex* temp = v->previous;
				delete v;
				v = temp;

				counter = 0;
			}
			else {
				v = v->next;
				counter++;
			}
		}

		if (poly->numVertices == 3 && realTriangle(poly, poly->vertices)) {
			//// three vertices left, make the final triangle
			*indices++ = poly->vertices->previous->index;
			*indices++ = poly->vertices->index;
			*indices++ = poly->vertices->next->index;

			indexCount += 3;
		}

		return indexCount;
	}
	/*bool triangulateImpl(Polygon *poly, short *indices) {
        Vertex *iter = poly->vertices;
        Vertex *start = iter;
        while (poly->numVertices > 3) {
            if (isConvex(iter, poly->winding) && isEar(iter, poly->vertices)) {
                Vertex *v = iter;

                *indices++ = v->previous->index;
                *indices++ = v->index;

                *indices++ = v->next->index;

                // remove from the linked list
                v->previous->next = v->next;
                v->next->previous = v->previous;
                // recompute convexness
                v->previous->convex = isConvex(v->previous, poly->winding);
                v->next->convex = isConvex(v->next, poly->winding);

                // if we removed the head of the list, update the head
                if (iter == poly->vertices)
                    poly->vertices = iter->next;

                // decrement the vertices
                poly->numVertices--;

                // move back to the previous vertex since its convexness may
                // have changed
                iter = v->previous;
                start = iter;
                delete v;
            }
            else {
                iter = iter->next;
                // XXX - THIS NEEDS TO BE FIXED !!!
                // we're going to enter an infinite loop so indicate that
                // legacy fill should be used.
                if (iter == start)
                    return false;

            }
        }

        // three vertices left, make the final triangle
        *indices++ = poly->vertices->previous->index;
        *indices++ = poly->vertices->index;
        *indices++ = poly->vertices->next->index;

        return true;
    }*/

    void triangleFan(short* indices, std::size_t vertexCount) {
        if (vertexCount >= 2) {
            for (std::size_t i = 0; i < vertexCount - 2; i++) {
                indices[i * 3] = 0;
                indices[i * 3 + 1] = static_cast<short>(i + 1);
                indices[i * 3 + 2] = static_cast<short>(i + 2);
            }
        }

        return;
    }

    edge::edge() {
        direction = true;
        edgeCount = 0;
        vertexStartIndex = USHRT_MAX;
        aIndex = 0;
        bIndex = 0;
        size = 0;
        stride = 0;
    }

    uint16_t edge::access(int index, bool reverse) {
        if (reverse ? !direction : direction) {
            if (index == 0) return aIndex;
            if (index == edgeCount + 1) return bIndex;
            return vertexStartIndex + index;
        }
        else {
            if (index == 0) return bIndex;
            if (index == edgeCount + 1) return aIndex;
            return vertexStartIndex + edgeCount + 1 - index;
        }
    }
    uint16_t edge::access(int index, int desiredEdgeCount, bool reverse) {
        int multiple = static_cast<int>((edgeCount + 1) / (double)(desiredEdgeCount + 1));

        if (reverse ? !direction : direction) {
            if (index == 0) return aIndex;
            if (index == desiredEdgeCount + 1) return bIndex;
            return vertexStartIndex + index * multiple;
        }
        else {
            if (index == 0) return bIndex;
            if (index == desiredEdgeCount + 1) return aIndex;
            return vertexStartIndex + edgeCount + 1 - index * multiple;
        }
    }
    void edge::build(double* vertices, size_t& vertexCount, bool useGeoPoint) {
        vertexStartIndex = static_cast<uint16_t>(vertexCount - 1);

        if (useGeoPoint) {
            TAK::Engine::Core::GeoPoint2 aGeo = TAK::Engine::Core::GeoPoint2(vertices[aIndex * stride + 1], vertices[aIndex * stride]);
            if (size == 3)
                aGeo.altitude = vertices[aIndex*stride + 2];
            TAK::Engine::Core::GeoPoint2 bGeo = TAK::Engine::Core::GeoPoint2(vertices[bIndex * stride + 1], vertices[bIndex * stride]);
            if (size == 3)
                bGeo.altitude = vertices[bIndex*stride + 2];

            double bearing = TAK::Engine::Core::GeoPoint2_bearing(aGeo, bGeo, true);
            const double range = TAK::Engine::Core::GeoPoint2_distance(aGeo, bGeo, true);
            double distanceStep = range / (edgeCount + 1);

            for (int i = 1; i < edgeCount + 1; i++) {
                TAK::Engine::Core::GeoPoint2 value = TAK::Engine::Core::GeoPoint2_pointAtDistance(aGeo, bearing, distanceStep * i, false);

                vertices[vertexCount * stride] = value.longitude;
                vertices[vertexCount * stride + 1] = value.latitude;
                if (size == 3) {
                    // interpolate the altitude
                    vertices[vertexCount*stride + 2] = aGeo.altitude + ((bGeo.altitude - aGeo.altitude)*((distanceStep*i) / range));
                }

                vertexCount++;
            }
        }
        else {
            double dX = (vertices[bIndex * stride] - vertices[aIndex * stride]) / (edgeCount + 1);
            double dY = (vertices[bIndex * stride + 1] - vertices[aIndex * stride + 1]) / (edgeCount + 1);
            double dZ = 0;
            if(size == 3)
                dZ = (vertices[bIndex * stride + 1] - vertices[aIndex * stride + 1]) / (edgeCount + 1);

            for (int i = 1; i < edgeCount + 1; i++) {
                vertices[vertexCount * stride] = vertices[aIndex * stride] + dX * i;
                vertices[vertexCount * stride + 1] = vertices[aIndex * stride + 1] + dY * i;
                if (size == 3) {
                    vertices[vertexCount*stride + 2] = vertices[aIndex*stride + 2] + dZ * i;
                }
                vertexCount++;
            }
        }
    }
    
    edge* edgeMap::edgeAccess(uint16_t pointA, uint16_t pointB) {

        int value = pointA;
        value |= (pointB << 16);

        //Value Exists
        if (edges.count(value)) {
            edges[value].direction = true;
            return &edges[value];
        }
        //Value does not Exist
        else {
            int reverseValue = pointB;
            reverseValue |= (pointA << 16);

            //and Inverse Exists
            if (edges.count(reverseValue)) {
                edges[reverseValue].direction = false;
                return &edges[reverseValue];
            }
            //and Inverse does not Exist
            else {
                edges[value].aIndex = pointA;
                edges[value].bIndex = pointB;
                edges[value].size = vertexSize;
                edges[value].stride = vertexStride;
                return &edges[value];
            }
        }
    }

    //Tessellates given shape. Returned vertices: All edge verticies, all internal edge vertices, all aditional internal vertices. Returned Indicies: Triangle indicies, perimeter indicies
    void tessellate(std::unique_ptr<double, void(*)(const double *)> &verticesPtr, size_t& vertexCount, const size_t vertexSize, size_t vertexStride, std::unique_ptr<uint16_t, void(*)(const uint16_t *)> & indicesPtr, size_t& indexCount, size_t& perimeterCount, float tessellationThreshold, bool useGeoPoint) {
        //Keeps track of which tris need subdivide, and how many times
        TAK::Engine::Util::array_ptr<int> subdivisionsRequiredPerTri(new int[indexCount / vertexStride]);

        const double *vertices = verticesPtr.get();
        const uint16_t *indices = indicesPtr.get();

        std::size_t originalIndexCount = indexCount;
        std::size_t originalVertexCount = vertexCount;

        vertexCount = 0;
        indexCount = 0;

        edgeMap edges;

#pragma region Subdivision Calculation

        int maxSubDiv = 6;

        //An assurence that we do not make too many verticies
        do {
			vertexCount = 0;
			indexCount = 0;

			//Create / reset edgemap
			edges = edgeMap();
			edges.vertexSize = vertexSize;
			edges.vertexStride = vertexStride;

            for (std::size_t i = 0; i < originalIndexCount; i += 3) {
                const double *A = vertices + (3 * indices[i]);
                const double *B = vertices + (3 * indices[i + 1]);
                const double *C = vertices + (3 * indices[i + 2]);

                //Calculate area of current triangle
                double area = (A[0] * (B[1] - C[1]) + B[0] * (C[1] - A[1]) + C[0] * (A[1] - B[1])) / 2.0;

                if (area < 0) area *= -1;

                subdivisionsRequiredPerTri[i / 3] = 0;
                int subDivNum = 0;
                //Determine subdivision count per triangle
                while (area > tessellationThreshold && subDivNum <= maxSubDiv) {
                    area /= 4;
                    subdivisionsRequiredPerTri[i / 3] <<= 1;
                    subdivisionsRequiredPerTri[i / 3] |= 1;
                    subDivNum++;
                }

                if (edges.edgeAccess(indices[i], indices[i + 1])->edgeCount < subdivisionsRequiredPerTri[i / 3])
                    edges.edgeAccess(indices[i], indices[i + 1])->edgeCount = subdivisionsRequiredPerTri[i / 3];

                if (edges.edgeAccess(indices[i + 1], indices[i + 2])->edgeCount < subdivisionsRequiredPerTri[i / 3])
                    edges.edgeAccess(indices[i + 1], indices[i + 2])->edgeCount = subdivisionsRequiredPerTri[i / 3];

                if (edges.edgeAccess(indices[i + 2], indices[i])->edgeCount < subdivisionsRequiredPerTri[i / 3])
                    edges.edgeAccess(indices[i + 2], indices[i])->edgeCount = subdivisionsRequiredPerTri[i / 3];


                //Determine how many new indices and vertices will be needed
                if (subdivisionsRequiredPerTri[i / 3] > 0) {
                    indexCount += static_cast<size_t>(4 * pow(4, subDivNum) + 3 * (subdivisionsRequiredPerTri[i / 3] + 4));
                }
                else {
                    indexCount += 6;
                }
            }

			for (size_t i = 0; i < originalIndexCount / 3; i++) {

				auto subdivisionCount = static_cast<size_t>(log2(subdivisionsRequiredPerTri[i] + 1));

				vertexCount += subdivisionCount
                                    ? static_cast<size_t>((pow(2, subdivisionCount) + 1) * (pow(2, subdivisionCount - 1) + 1)) : 3;
			}

			//Account for the perimeter (for the base of an extrusion)
			for (std::size_t i = 0; i < originalVertexCount; i++) {
				if (i + 1 < originalVertexCount) vertexCount += static_cast<std::size_t>(edges.edgeAccess(static_cast<uint16_t>(i), static_cast<uint16_t>(i + 1))->edgeCount) + 2;
				else vertexCount += static_cast<std::size_t>(edges.edgeAccess(static_cast<uint16_t>(i), 0)->edgeCount) + 2;
			}

            maxSubDiv--;
        } while (vertexCount > USHRT_MAX);

#pragma endregion

        std::unique_ptr<double, void(*)(const double *)> newVerticesPtr(new double[vertexCount * vertexStride], TAK::Engine::Util::Memory_array_deleter_const<double>);
        std::unique_ptr<uint16_t, void(*)(const uint16_t *)> newIndicesPtr(new uint16_t[indexCount + 1], TAK::Engine::Util::Memory_array_deleter_const<uint16_t>);
        double *newVertices = newVerticesPtr.get();
        uint16_t *newIndices = newIndicesPtr.get();

        memcpy(newVertices, vertices, originalVertexCount * vertexStride * sizeof(double));

        vertexCount = originalVertexCount;
        indexCount = 0;

#pragma region Edge Generation
        //Create and move vertices for outer edges
        edge* temp;
        for (std::size_t i = 0; i < originalVertexCount; i++) {
            if (i + 1 < originalVertexCount) edges.edgeAccess(static_cast<uint16_t>(i), static_cast<uint16_t>(i + 1))->build(newVertices, vertexCount, useGeoPoint);
            else edges.edgeAccess(static_cast<uint16_t>(i), 0)->build(newVertices, vertexCount, useGeoPoint);
        }

        //Create vertices for inner edges
        for (std::size_t i = 0; i < originalIndexCount; i++) {
            uint16_t a = indices[i];
            uint16_t b = indices[i != originalIndexCount - 1 ? i + 1 : 0];

            temp = edges.edgeAccess(a, b);

            if (temp->vertexStartIndex != USHRT_MAX) {
                continue;
            }
            temp->build(newVertices, vertexCount, useGeoPoint);
        }
#pragma endregion

#pragma region Triangle Generation
        //For each Triangle
        for (std::size_t i = 0; i < originalIndexCount; i += 3) {
            //      ^ 
            //     / \
            //    C   B
            //   /     \
            //    --A-->
            edge* edgeA = edges.edgeAccess(indices[i], indices[i + 1]);
            edge* edgeB = edges.edgeAccess(indices[i + 1], indices[i + 2]);
            edge* edgeC = edges.edgeAccess(indices[i + 2], indices[i]);

            uint16_t divCount = edgeA->edgeCount;
            if (divCount > edgeB->edgeCount) divCount = edgeB->edgeCount;
            if (divCount > edgeC->edgeCount) divCount = edgeC->edgeCount;

            if (divCount == 0) {
                newIndices[indexCount++] = edgeA->access(0);
                newIndices[indexCount++] = edgeB->access(0);
                newIndices[indexCount++] = edgeC->access(0);

                continue;
            }

            edge top;
            top.edgeCount = divCount - 1;
            top.aIndex = edgeC->access(1, divCount, true);
            top.bIndex = edgeB->access(1, divCount);
            top.size = vertexSize;
            top.stride = vertexStride;
            edge bot = *edgeA;

            int aEdgeCounter = 0;
            int bEdgeCounter = 0;
            int cEdgeCounter = 0;

            //For each layer of the triangle
            for (int iLayer = 0; iLayer < subdivisionsRequiredPerTri[i / 3] + 1; iLayer++) {
                top.size = vertexSize;
                top.stride = vertexStride;
                top.build(newVertices, vertexCount, useGeoPoint);

                //C edge can be build normaly
                if (edgeC->edgeCount == divCount) {
                    //A - Starter Triangle
                    newIndices[indexCount++] = bot.access(0);                                         //Left
                    newIndices[indexCount++] = iLayer == 0 ? bot.access(1, divCount) : bot.access(1); //Right
                    newIndices[indexCount++] = top.access(0);                                         //Top
                }
                //C edge needs built for extra verticies
                else {
                    //A
                    if (iLayer == 0) {
                        for (int itt = (edgeC->edgeCount + 1) / (divCount + 1); itt > 0; itt--) {
                            newIndices[indexCount++] = edgeC->access(cEdgeCounter + itt - 1, true);               //Left
                            newIndices[indexCount++] = bot.access(1, divCount);                                   //Right
                            newIndices[indexCount++] = edgeC->access(cEdgeCounter + itt, true);                   //Top
                        }
                        cEdgeCounter += (edgeC->edgeCount + 1) / (divCount + 1);
                    }
                    else {
                        for (int itt = (edgeC->edgeCount + 1) / (divCount + 1); itt > 0; itt--) {
                            newIndices[indexCount++] = edgeC->access(cEdgeCounter, true);                     //Left
                            newIndices[indexCount++] = bot.access(1);                                         //Right
                            newIndices[indexCount++] = edgeC->access(++cEdgeCounter, true);                   //Top
                        }
                    }
                }

                //A edge can be built normally or bot is not A Edge
                if (iLayer || bot.edgeCount == divCount) {
                    for (int iTriangles = 0; iTriangles < bot.edgeCount; iTriangles++) {
                        //B - Upper Triangle
                        newIndices[indexCount++] = top.access(iTriangles);                                                          //Left
                        newIndices[indexCount++] = iLayer == 0 ? bot.access(iTriangles + 1, divCount) : bot.access(iTriangles + 1); //Bot
                        newIndices[indexCount++] = top.access(iTriangles + 1);													    //Right


                                                                                                                                    //C - Lower Triangle
                        newIndices[indexCount++] = iLayer == 0 ? bot.access(iTriangles + 1, divCount) : bot.access(iTriangles + 1); //Left
                        newIndices[indexCount++] = iLayer == 0 ? bot.access(iTriangles + 2, divCount) : bot.access(iTriangles + 2); //Right
                        newIndices[indexCount++] = top.access(iTriangles + 1);													    //Top
                    }
                }
                //A edge needs built for extra verticies
                else {
                    indexCount -= 3;

                    //A
                    for (int itt = (bot.edgeCount + 1) / (divCount + 1); itt > 0; itt--) {
                        newIndices[indexCount++] = bot.access(aEdgeCounter);																 //Left    
                        newIndices[indexCount++] = bot.access(++aEdgeCounter);																 //Right
                        newIndices[indexCount++] = edgeC->edgeCount == divCount ? edgeC->access(1, divCount, true) : edgeC->access(1, true); //Top
                    }

                    for (int iTriangles = 0; iTriangles < top.edgeCount + 1; iTriangles++) {
                        //B
                        newIndices[indexCount++] = top.access(iTriangles);        //Left
                        uint16_t* midIndex = &newIndices[indexCount++];			  //Bot
                        newIndices[indexCount++] = top.access(iTriangles + 1);	  //Right

                                                                                  //C
                        for (int itt = (bot.edgeCount + 1) / (divCount + 1); itt > 0; itt--) {
                            newIndices[indexCount++] = bot.access(aEdgeCounter);   //Left
                            newIndices[indexCount++] = bot.access(++aEdgeCounter); //Right
                            newIndices[indexCount++] = top.access(iTriangles); 	   //Top
                        }
                        *midIndex = bot.access(aEdgeCounter);
                    }
                }

                //B edge requires rebuild
                if (edgeB->edgeCount != divCount) {
                    indexCount -= 3;
                    uint16_t bottomLeftIndex = newIndices[indexCount];
                    for (int itt = (edgeB->edgeCount + 1) / (divCount + 1); itt > 0; itt--) {
                        newIndices[indexCount++] = bottomLeftIndex;					//Left
                        newIndices[indexCount++] = edgeB->access(aEdgeCounter);		//Right
                        newIndices[indexCount++] = edgeB->access(++aEdgeCounter);	//Top
                    }
                }

                bot = top;
                if (top.edgeCount != 0) top.edgeCount--;
                top.aIndex = edgeC->access(2 + iLayer, divCount, true);
                top.bIndex = edgeB->access(2 + iLayer, divCount);
            }
        }
#pragma endregion

        perimeterCount = indexCount;
        for (std::size_t i = 0; i < originalVertexCount; i++) {
            edge* currentEdge = edges.edgeAccess(static_cast<uint16_t>(i), i == originalVertexCount - 1 ? 0 : static_cast<uint16_t>(i + 1));

            for (int j = 0; j < currentEdge->edgeCount + 1; j++) {
                newIndices[perimeterCount++] = currentEdge->access(j);
            }
        }

        perimeterCount -= indexCount;

        indicesPtr = std::move(newIndicesPtr);
        verticesPtr = std::move(newVerticesPtr);
    }
}

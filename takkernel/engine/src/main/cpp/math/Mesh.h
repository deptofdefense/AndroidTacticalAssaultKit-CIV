#ifndef TAK_ENGINE_MATH_MESH_H_INCLUDED
#define TAK_ENGINE_MATH_MESH_H_INCLUDED

#include "math/Point2.h"
#include "math/Ray2.h"
#include "math/GeometryModel2.h"
#include "math/Matrix2.h"
#include "model/Mesh.h"
#include "util/Memory.h"

namespace TAK
{
    namespace Engine
    {
        namespace Math
        {
            class ENGINE_API Mesh : public GeometryModel2
            {

            public:
                Mesh(const double * vertices, const std::size_t numVertexColumns, const std::size_t numVertexRows) NOTHROWS;
                Mesh(Model::MeshPtr_const &&data, const Matrix2 *localFrame, const bool simdEnabled = false) NOTHROWS;
                Mesh(const std::shared_ptr<const Model::Mesh> &data, const Matrix2 *localFrame, const bool simdEnabled = false) NOTHROWS;
                Mesh(const Mesh &other) NOTHROWS;
                virtual ~Mesh() NOTHROWS;
            public: // GeometryModel interface
                virtual bool intersect(Point2<double> *isectPoint, const Ray2<double> &ray) const NOTHROWS;
                virtual GeometryModel2::GeometryClass getGeomClass() const NOTHROWS;
                virtual void clone(std::unique_ptr<GeometryModel2, void(*)(const GeometryModel2 *)> &value) const NOTHROWS;
            private:
                std::shared_ptr<const Model::Mesh> data;
                Matrix2 localFrame;
                bool hasLocalFrame;
                bool simdEnabled;
            };
        }
    }
}
#endif // TAK_ENGINE_MATH_MESH_H_INCLUDED

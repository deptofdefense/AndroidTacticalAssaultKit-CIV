#include "Frustum.h"
#include "math/Ray2.h"
namespace atakmap
{
    namespace math
    {
        class Frustum::Plane : public GeometryModel
        {
        public:
            Plane();
            Plane(const Vector3<double> *normal, const double dist);
            Plane(const Vector3<double> *normal, const Vector3<double> *point);
            ~Plane() override;
        public:
            bool intersect(const Ray<double> *ray, Point<double> *isectPoint) const override;
            bool intersectV(const Ray<double> *ray, Vector3<double> *result) const;
            double distance(const Vector3<double> *point) const;
            void normalize(Plane *result) const;
            GeometryModel::GeometryClass getGeomClass() const override;
        private:
            Vector3<double> normal;
            double dist;
        };
        
        Frustum::Plane::Plane() :
            normal(0, 0, 0),
            dist(0)
        {

        }


        Frustum::Plane::Plane(const Vector3<double> *n, const double d) :
            normal(n->x, n->y, n->z),
            dist(d)
        {}

        Frustum::Plane::Plane(const Vector3<double> *n, const Vector3<double> *point) :
            normal(0, 0, 0)
        {
            n->normalize(&normal);
            dist = normal.dot(point);
        }

        Frustum::Plane::~Plane()
        {}

        bool Frustum::Plane::intersect(const Ray<double> *ray, Point<double> *isectPoint) const
        {
            const double d = normal.dot(&ray->direction);

            if (d == 0)
                return false;

            Vector3<double> originV(ray->origin.x, ray->origin.y, ray->origin.z);
            const double n = normal.dot(&originV) + dist;
            const double t = -(n / d);

            if (t >= 0) {
                isectPoint->x = ray->origin.x + (ray->direction.x*t);
                isectPoint->y = ray->origin.y + (ray->direction.y*t);
                isectPoint->z = ray->origin.z + (ray->direction.z*t);
                return true;
            }
            else {
                return false;
            }
        }

        bool Frustum::Plane::intersectV(const Ray<double> *ray, Vector3<double> *result) const
        {
            double d = normal.dot(&ray->direction);

            if (d == 0)
                return false;

            Vector3<double> tv(ray->origin.x, ray->origin.y, ray->origin.z);
            double n = normal.dot(&tv);
            double t = -(n / d);

            if (t >= 0) {
                Vector3<double> tv2(0, 0, 0);
                ray->direction.multiply(t, &tv2);
                tv.add(&tv2, result);
                return true;
            }
            else {
                return false;
            }
        }

        double Frustum::Plane::distance(const Vector3<double> *point) const
        {
            return normal.dot(point) + dist;
        }

        void Frustum::Plane::normalize(Plane *result) const
        {
            double m = 1.0 / normal.length();
            normal.multiply(m, &result->normal);
            result->dist = this->dist * m;
        }

        GeometryModel::GeometryClass Frustum::Plane::getGeomClass() const
        {
            return GeometryModel::PLANE;
        }

        Frustum::Frustum(Matrix *proj, Matrix *model) : sphereDirty(true)
        {
            update(proj, model);
        }


        Frustum::Frustum(Matrix *clip)
        {
            update(clip);
        }


        void Frustum::update(Matrix *matrix_clip)
        {
            sphereDirty = true;
            double m[16];
            matrix_clip->get(m, Matrix::COLUMN_MAJOR);
            frustum[0] = new Plane();
            frustum[1] = new Plane();
            frustum[2] = new Plane();
            frustum[3] = new Plane();
            frustum[4] = new Plane();
            frustum[5] = new Plane();
            Vector3<double> vd(0, 0, 0);
            // Right
            vd.x = m[3]  - m[0];
            vd.y = m[7]  - m[4];
            vd.z = m[11] - m[8];
            Plane(&vd, m[15] - m[12]).normalize(frustum[0]);

            // Left
            vd.x = m[3]  + m[0];
            vd.y = m[7]  + m[4];
            vd.z = m[11] + m[8];
            Plane(&vd, m[15] + m[12]).normalize(frustum[1]);

            // Bottom
            vd.x = m[3]  + m[1];
            vd.y = m[7]  + m[5];
            vd.z = m[11] + m[9];
            Plane(&vd, m[15] + m[13]).normalize(frustum[2]);

            // Top
            vd.x = m[3]  - m[1];
            vd.y = m[7]  - m[5];
            vd.z = m[11] - m[9];
            Plane(&vd, m[15] - m[13]).normalize(frustum[3]);

            // Far
            vd.x = m[3]  - m[2];
            vd.y = m[7]  - m[6];
            vd.z = m[11] - m[10];
            Plane(&vd, m[15] - m[14]).normalize(frustum[4]);

            vd.x = m[3]  + m[2];
            vd.y = m[7]  + m[6];
            vd.z = m[11] + m[10];
            Plane(&vd, m[15] + m[14]).normalize(frustum[5]);

            this->clip.set(matrix_clip);
            try {
                matrix_clip->createInverse(&invClip);
            } catch (...) {
            }
        }


        void Frustum::update(Matrix *proj, Matrix *model)
        {
            Matrix pcopy(*proj);
            pcopy.concatenate(model);
            update(&pcopy);
        }


        bool Frustum::intersects(Sphere *s)
        {
            int insideCount = 0;
            double dist;
            Vector3<double> vd(s->center.x, s->center.y, s->center.z);
            for (int x = 0; x < 6; ++x)
            {
                dist = (*frustum)[x].distance(&vd);
                if (dist < -s->radius)
                    return false;
                if (dist > s->radius)
                    insideCount++;
            }
            return true;
        }


        double Frustum::depthIfInside(Sphere *s)
        {
            double dist = NAN;
            Vector3<double> vd(s->center.x, s->center.y, s->center.z);
            for (int x = 0; x < 6; ++x)
            {
                dist = (*frustum)[x].distance(&vd);
                if (dist < -s->radius)
                    return NAN;
            }
            return dist;

        }


        Matrix Frustum::getClip()
        {
            return clip;
        }

    }
}


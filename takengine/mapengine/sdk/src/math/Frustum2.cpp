#include "math/Frustum2.h"

#include "math/Vector4.h"

using namespace TAK::Engine::Math;


Frustum2::Plane::Plane() :
    normal(0, 0, 1),
    dist(0)
{}

Frustum2::Plane::Plane(const Vector4<double> &normal_, double dist_) :
    normal(normal_),
    dist(dist_)
{}

            Frustum2::Frustum2(const Matrix2& proj, const Matrix2& model) NOTHROWS : sphereDirty(true)
            {
                update(proj, model);
            }


            Frustum2::Frustum2(const Matrix2& clip) NOTHROWS
            {
                update(clip);
            }


            void Frustum2::update(const Matrix2& matrix_clip) NOTHROWS
            {
                sphereDirty = true;
                double m[16];
                matrix_clip.get(m, Matrix2::COLUMN_MAJOR);
                Vector4<double> vd(0, 0, 0);
                // Right
                vd.x = m[3] - m[0];
                vd.y = m[7] - m[4];
                vd.z = m[11] - m[8];
                //Plane2(vd, m[15] - m[12]).normalize(frustum[0]);
                normalize(&frustum[0], Plane(vd, m[15] - m[12]));

                // Left
                vd.x = m[3] + m[0];
                vd.y = m[7] + m[4];
                vd.z = m[11] + m[8];
                //Plane2(vd, m[15] + m[12]).normalize(frustum[1]);
                normalize(&frustum[1], Plane(vd, m[15] + m[12]));

                // Bottom
                vd.x = m[3] + m[1];
                vd.y = m[7] + m[5];
                vd.z = m[11] + m[9];
                //Plane2(vd, m[15] + m[13]).normalize(frustum[2]);
                normalize(&frustum[2], Plane(vd, m[15] + m[13]));

                // Top
                vd.x = m[3] - m[1];
                vd.y = m[7] - m[5];
                vd.z = m[11] - m[9];
                //Plane2(vd, m[15] - m[13]).normalize(frustum[3]);
                normalize(&frustum[3], Plane(vd, m[15] - m[13]));

                // Far
                vd.x = m[3] - m[2];
                vd.y = m[7] - m[6];
                vd.z = m[11] - m[10];
                //Plane2(vd, m[15] - m[14]).normalize(frustum[4]);
                normalize(&frustum[4], Plane(vd, m[15] - m[14]));

                vd.x = m[3] + m[2];
                vd.y = m[7] + m[6];
                vd.z = m[11] + m[10];
                //Plane2(vd, m[15] + m[14]).normalize(frustum[5]);
                normalize(&frustum[5], Plane(vd, m[15] + m[14]));

                this->clip.set(matrix_clip);
                matrix_clip.createInverse(&invClip);
            }


            void Frustum2::update(const Matrix2& proj, const Matrix2& model) NOTHROWS
            {
                Matrix2 pcopy(proj);
                pcopy.concatenate(model);
                update(pcopy);
            }


            bool Frustum2::intersects(const Sphere2& s) const NOTHROWS
            {
                const Vector4<double> vd(s.center.x, s.center.y, s.center.z);
                for (int x = 0; x < 6; ++x)
                {
                    const double dist = distance(frustum[x], vd);
                    if (dist < -s.radius)
                        return false;
                }
                return true;
            }

            bool Frustum2::intersects(const AABB& aabb) const NOTHROWS
            {
                // derived from "Foundations of Game Engine Development"
                Vector4<double> vd(0, 0, 0);
                vd.x = (aabb.maxX + aabb.minX) / 2.0;
                vd.y = (aabb.maxY + aabb.minY) / 2.0;
                vd.z = (aabb.maxZ + aabb.minZ) / 2.0;
                Point2<double> size;
                size.x = (aabb.maxX - aabb.minX) / 2.0;
                size.y = (aabb.maxY - aabb.minY) / 2.0;
                size.z = (aabb.maxZ - aabb.minZ) / 2.0;

                for (std::size_t i = 0u; i < 6u; i++) {
                    const Plane &g = frustum[i];
                    // compute radius relative to plane
                    const double rg = fabs(g.normal.x*size.x) + fabs(g.normal.y*size.y) + fabs(g.normal.z*size.z);
                    // perform bounding sphere style test
                    const double dist = distance(g, vd);
                    if (dist < -rg)
                        return false;
                }
                return true;
            }

            double Frustum2::depthIfInside(const Sphere2& s) const NOTHROWS
            {
                double dist = NAN;
                Vector4<double> vd(s.center.x, s.center.y, s.center.z);
                for (int x = 0; x < 6; ++x)
                {
                    dist = distance(frustum[x], vd);
                    if (dist < -s.radius)
                        return NAN;
                }
                return dist;

            }


            Matrix2 Frustum2::getClip() const NOTHROWS
            {
                return clip;
            }

            void Frustum2::normalize(Plane *dst, const Plane &src)
            {
                double m = 1.0 / src.normal.length();
                src.normal.multiply(m, &dst->normal);
                dst->dist = src.dist * m;
            }
            double Frustum2::distance(const Plane &dst, const Vector4<double> &point)
            {
                return dst.normal.dot(&point) + dst.dist;
            }
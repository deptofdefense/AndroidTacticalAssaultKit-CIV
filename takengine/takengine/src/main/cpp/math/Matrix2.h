#ifndef TAK_ENGINE_MATH_MATRIX2_H_INCLUDED
#define TAK_ENGINE_MATH_MATRIX2_H_INCLUDED

#include "math/Point2.h"
#include "port/Platform.h"
#include "util/Error.h"

namespace TAK
{
    namespace Engine 
    {
        namespace Math
        {
            class ENGINE_API Matrix2 {
            public:
                enum MatrixOrder {
                    ROW_MAJOR,
                    COLUMN_MAJOR,
                };

            public:
                Matrix2() NOTHROWS;
                Matrix2(double m00, double m01, double m02, double m03,
                    double m10, double m11, double m12, double m13,
                    double m20, double m21, double m22, double m23,
                    double m30, double m31, double m32, double m33) NOTHROWS;
            public:
                ~Matrix2() NOTHROWS;
            public:
                TAK::Engine::Util::TAKErr transform(TAK::Engine::Math::Point2<double> *dst, const TAK::Engine::Math::Point2<double> &src) const NOTHROWS;
                TAK::Engine::Math::Point2<double> transform(const TAK::Engine::Math::Point2<double> &src) const NOTHROWS;
                TAK::Engine::Util::TAKErr createInverse(Matrix2* t) const NOTHROWS;
                TAK::Engine::Util::TAKErr transpose() NOTHROWS;
                void concatenate(const Matrix2 &t) NOTHROWS;
                void preConcatenate(const Matrix2 &t) NOTHROWS;
                void set(const Matrix2 &t) NOTHROWS;
                TAK::Engine::Util::TAKErr set(const size_t row, const size_t col, const double v) NOTHROWS;
                TAK::Engine::Util::TAKErr get(double *matrix, const MatrixOrder order = ROW_MAJOR) const NOTHROWS;
                TAK::Engine::Util::TAKErr get(double *v, const size_t row, const size_t col) const NOTHROWS;
                void translate(const double tx, const double ty, const double tz = 0.0) NOTHROWS;
                /** @param theta    Rotation in radians */
                Util::TAKErr rotate(const double theta) NOTHROWS;
                /** @param theta    Rotation in radians */
                Util::TAKErr rotate(const double theta, const double anchorX, const double anchorY) NOTHROWS;
                /** @param theta    Rotation in radians */
                TAK::Engine::Util::TAKErr rotate(const double theta, const double axisX, const double axisY, const double axisZ) NOTHROWS;
                /** @param theta    Rotation in radians */
                TAK::Engine::Util::TAKErr rotate(const double theta, const double anchorX, const double anchorY, const double anchorZ, const double axisX, const double axisY, const double axisZ) NOTHROWS;
                void scale(const double scale) NOTHROWS;
                void scale(const double scaleX, const double scaleY, const double scaleZ = 1.0) NOTHROWS;
                void setToIdentity() NOTHROWS;
                void setToTranslate(const double tx, const double ty, const double tz = 0.0) NOTHROWS;
                /** @param theta    Rotation in radians */
                Util::TAKErr setToRotate(const double theta) NOTHROWS;
                /** @param theta    Rotation in radians */
                Util::TAKErr setToRotate(const double theta, const double anchorX, const double anchorY) NOTHROWS;
                /** @param theta    Rotation in radians */
                TAK::Engine::Util::TAKErr setToRotate(const double theta, const double axisX, const double axisY, const double axisZ) NOTHROWS;
                void setToScale(const double scale) NOTHROWS;
                void setToScale(const double scaleX, const double scaleY, const double scaleZ = 1.0) NOTHROWS;
            public :
                bool operator== (const Matrix2 &other) const NOTHROWS;
            private:
                void concatenateImpl(const double tm00, const double tm01, const double tm02, const double tm03,
                    const double tm10, const double tm11, const double tm12, const double tm13,
                    const double tm20, const double tm21, const double tm22, const double tm23,
                    const double tm30, const double tm31, const double tm32, const double tm33) NOTHROWS;
            private:
                double m00;
                double m01;
                double m02;
                double m03;
                double m10;
                double m11;
                double m12;
                double m13;
                double m20;
                double m21;
                double m22;
                double m23;
                double m30;
                double m31;
                double m32;
                double m33;
            };

            typedef std::unique_ptr<Matrix2, void(*)(const Matrix2 *)> Matrix2Ptr;
            typedef std::unique_ptr<const Matrix2, void(*)(const Matrix2 *)> Matrix2Ptr_const;

            ENGINE_API TAK::Engine::Util::TAKErr Matrix2_mapQuads(Matrix2 *xform, const TAK::Engine::Math::Point2<float> &src1, const TAK::Engine::Math::Point2<float> &src2, const TAK::Engine::Math::Point2<float> &src3, const TAK::Engine::Math::Point2<float> &src4,
                const TAK::Engine::Math::Point2<float> &dst1, const TAK::Engine::Math::Point2<float> &dst2, const TAK::Engine::Math::Point2<float> &dst3, const TAK::Engine::Math::Point2<float> &dst4) NOTHROWS;
            ENGINE_API TAK::Engine::Util::TAKErr Matrix2_mapQuads(Matrix2 *xform, const TAK::Engine::Math::Point2<double> &src1, const TAK::Engine::Math::Point2<double> &src2, const TAK::Engine::Math::Point2<double> &src3, const TAK::Engine::Math::Point2<double> &src4,
                const TAK::Engine::Math::Point2<double> &dst1, const TAK::Engine::Math::Point2<double> &dst2, const TAK::Engine::Math::Point2<double> &dst3, const TAK::Engine::Math::Point2<double> &dst4) NOTHROWS;
            ENGINE_API TAK::Engine::Util::TAKErr Matrix2_mapQuads(Matrix2 *xform, const double srcX1, const double srcY1, const double srcX2, const double srcY2, const double srcX3, const double srcY3, const double srcX4, const double srcY4,
                const double dstX1, const double dstY1, const double dstX2, const double dstY2, const double dstX3, const double dstY3, const double dstX4, const double dstY4) NOTHROWS;
        }
    } // end namespace TAK::Engine
} // end namespace TAK

#endif // TAK_ENGINE_MATH_MATRIX2_H_INCLUDED

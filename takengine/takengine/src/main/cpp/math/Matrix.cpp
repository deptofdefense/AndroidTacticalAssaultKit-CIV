#include "math/Matrix.h"

#include <cmath>

#include <stdexcept>

#include "util/Memory.h"

using namespace atakmap::math;

using namespace TAK::Engine::Math;
using namespace TAK::Engine::Util;

Matrix::Matrix() :
    impl(new Matrix2(), Memory_deleter_const<Matrix2>)
{}

Matrix::Matrix(double mx00, double mx01, double mx02, double mx03,
               double mx10, double mx11, double mx12, double mx13,
               double mx20, double mx21, double mx22, double mx23,
               double mx30, double mx31, double mx32, double mx33) :
    impl(new Matrix2(mx00, mx01, mx02, mx03,
                     mx10, mx11, mx12, mx13,
                     mx20, mx21, mx22, mx23,
                     mx30, mx31, mx32, mx33),
         Memory_deleter_const<Matrix2>)
{}

Matrix::Matrix(const Matrix2 &other) :
    impl(new Matrix2(other), Memory_deleter_const<Matrix2>)
{}

Matrix::Matrix(Matrix2Ptr &&impl_) :
    impl(std::move(impl_))
{}

Matrix::Matrix(const Matrix &other) :
    impl(new Matrix2(*other.impl), Memory_deleter_const<Matrix2>)
{}

Matrix::~Matrix()
{}

void Matrix::transform(const Point<double> *src, Point<double> *dst) const
{
    Point2<double> src2(src->x, src->y, src->z);
    Point2<double> dst2;
    TAKErr code = impl->transform(&dst2, src2);
    if (code != TE_Ok) {
        // divide by zero has occurred -- NAN out the values
        dst2.x = NAN;
        dst2.y = NAN;
        dst2.z = NAN;
    }
    dst->x = dst2.x;
    dst->y = dst2.y;
    dst->z = dst2.z;
}

void Matrix::createInverse(Matrix *t) const throw (NonInvertibleTransformException)
{
    TAKErr code = impl->createInverse(t->impl.get());
    if (code != TE_Ok)
            throw NonInvertibleTransformException(
                    "The determinant for the transform is 0.");
}

void Matrix::concatenate(const Matrix *t)
{
    impl->concatenate(*t->impl);
}

void Matrix::preConcatenate(const Matrix *t)
{
    impl->preConcatenate(*t->impl);
}

void Matrix::set(const Matrix *t)
{
    impl->set(*t->impl);
}

void Matrix::set(const size_t row, const size_t col, const double v)
{
    if(impl->set(row, col, v) != TE_Ok)
        throw std::invalid_argument("bad row/column");
}

void Matrix::get(double *matrix, const MatrixOrder order) const
{
    Matrix2::MatrixOrder order2;
    switch(order) {
        case ROW_MAJOR :
            order2 = Matrix2::ROW_MAJOR;
            break;
        case COLUMN_MAJOR :
            order2 = Matrix2::COLUMN_MAJOR;
            break;
        default :
            throw std::invalid_argument("bad MatrixOrder");
    }

    impl->get(matrix, order2);
}

void Matrix::get(const size_t row, const size_t col, double *v) const
{
    if(impl->get(v, row, col) != TE_Ok)
        throw std::invalid_argument("bad row/column");
}

void Matrix::translate(const double tx, const double ty, const double tz)
{
    impl->translate(tx, ty, tz);
}

void Matrix::rotate(const double theta)
{
    impl->rotate(theta);
}

void Matrix::rotate(const double theta, const double anchorx, const double anchory)
{
    impl->rotate(theta, anchorx, anchory);
}

void Matrix::rotate(const double theta, const double axisX, const double axisY, const double axisZ)
{
    impl->rotate(theta, axisX, axisY, axisZ);
}

void Matrix::scale(const double s)
{
    impl->scale(s);
}

void Matrix::scale(const double scaleX, const double scaleY, const double scaleZ)
{
    impl->scale(scaleX, scaleY, scaleZ);
}

void Matrix::setToIdentity()
{
    impl->setToIdentity();
}

void Matrix::setToTranslate(const double tx, const double ty, const double tz)
{
    impl->setToTranslate(tx, ty, tz);
}

void Matrix::setToRotate(const double theta)
{
    impl->setToRotate(theta);
}

void Matrix::setToRotate(const double theta, const double axisX, const double axisY, const double axisZ)
{
    impl->setToRotate(theta, axisX, axisY, axisZ);
}

void Matrix::setToRotate(const double theta, const double anchorx, const double anchory)
{
    impl->setToRotate(theta, anchorx, anchory);
}

void Matrix::setToScale(const double scale)
{
    impl->setToScale(scale);
}

void Matrix::setToScale(const double scaleX, const double scaleY, const double scaleZ)
{
    impl->setToScale(scaleX, scaleY, scaleZ);
}

Matrix &Matrix::operator=(const Matrix &other)
{
    this->impl->set(*other.impl);
    return *this;
}

void Matrix::mapQuads(const Point<double> *src1, const Point<double> *src2, const Point<double> *src3, const Point<double> *src4,
                         const Point<double> *dst1, const Point<double> *dst2, const Point<double> *dst3, const Point<double> *dst4,
                         Matrix *xform)
{
    TAKErr code(TE_Ok);
    code = Matrix2_mapQuads(xform->impl.get(),
                            src1->x, src1->y,
                            src2->x, src2->y,
                            src3->x, src3->y,
                            src4->x, src4->y,
                            dst1->x, dst1->y,
                            dst2->x, dst2->y,
                            dst3->x, dst3->y,
                            dst4->x, dst4->y);

    if (code != TE_Ok)
#ifndef _MSC_VER
        throw std::exception();
#else
        throw std::exception("failed to map quads");
#endif
}

void Matrix::mapQuads(const Point<float> *src1, const Point<float> *src2, const Point<float> *src3, const Point<float> *src4,
                      const Point<float> *dst1, const Point<float> *dst2, const Point<float> *dst3, const Point<float> *dst4,
                      Matrix *xform)
{
    TAKErr code(TE_Ok);
    code = Matrix2_mapQuads(xform->impl.get(),
                            src1->x, src1->y,
                            src2->x, src2->y,
                            src3->x, src3->y,
                            src4->x, src4->y,
                            dst1->x, dst1->y,
                            dst2->x, dst2->y,
                            dst3->x, dst3->y,
                            dst4->x, dst4->y);
    if (code != TE_Ok)
#ifndef _MSC_VER
        throw std::exception();
#else
        throw std::exception("failed to map quads");
#endif
}

void Matrix::mapQuads(const double srcX1, const double srcY1, const double srcX2, const double srcY2, const double srcX3, const double srcY3, const double srcX4, const double srcY4,
                     const double dstX1, const double dstY1, const double dstX2, const double dstY2, const double dstX3, const double dstY3, const double dstX4, const double dstY4,
                     Matrix *xform)
{
    TAKErr code(TE_Ok);
    code = Matrix2_mapQuads(xform->impl.get(),
                            srcX1, srcY1,
                            srcX2, srcY2,
                            srcX3, srcY3,
                            srcX4, srcY4,
                            dstX1, dstY1,
                            dstX2, dstY2,
                            dstX3, dstY3,
                            dstX4, dstY4);
    if (code != TE_Ok)
#ifndef _MSC_VER
        throw std::exception();
#else
        throw std::exception("failed to map quads");
#endif
}

TAKErr atakmap::math::Matrix_adapt(TAK::Engine::Math::Matrix2 *value, const Matrix &legacy) NOTHROWS
{
    if (!value)
        return TE_InvalidArg;
    *value = *legacy.impl;
    return TE_Ok;
}

#include "math/Matrix2.h"

#include <cmath>
#include "util/Error.h"

using namespace TAK::Engine::Util;
using namespace TAK::Engine::Math;

namespace // unnamed namespace
{
    /**
    * | a b |
    * | c d |
    */
    double determinant(const double a, const double b, const double c, const double d);

    double adjoint(double** const matrix, double** adjoint);

    void multiply(double** const m1, double** const m2, double** dst);

    bool mapQuadToQuad(double** const quad1, double** const quad2, double *matrix);

    bool mapQuadToQuad(double** const quad1, double** const quad2, double** q1_q2, double** q2_q1);

    TAKErr mapUnitSquareToQuad(double** const quad, double** square2quad);

    bool isEquivalentToZero(const double n);

} // end unnamed namespace


Matrix2::Matrix2() NOTHROWS :
    m00(1), m01(0), m02(0), m03(0),
    m10(0), m11(1), m12(0), m13(0),
    m20(0), m21(0), m22(1), m23(0),
    m30(0), m31(0), m32(0), m33(1)
{}

Matrix2::Matrix2(double mx00, double mx01, double mx02, double mx03,
    double mx10, double mx11, double mx12, double mx13,
    double mx20, double mx21, double mx22, double mx23,
    double mx30, double mx31, double mx32, double mx33) NOTHROWS :

    m00(mx00), m01(mx01), m02(mx02), m03(mx03),
    m10(mx10), m11(mx11), m12(mx12), m13(mx13),
    m20(mx20), m21(mx21), m22(mx22), m23(mx23),
    m30(mx30), m31(mx31), m32(mx32), m33(mx33)
{}

Matrix2::~Matrix2() NOTHROWS
{}

TAKErr Matrix2::transpose() NOTHROWS
{
    std::swap(m01, m10);
    std::swap(m02, m20);
    std::swap(m03, m30);
    std::swap(m12, m21);
    std::swap(m13, m31);
    std::swap(m23, m32);
    return TE_Ok;
}
TAKErr Matrix2::transform(Point2<double> *dst, const Point2<double> &src) const NOTHROWS
{
    *dst = transform(src);
    return TE_Ok;
}
Point2<double> Matrix2::transform(const Point2<double> &src) const NOTHROWS
{
    const double dstX = src.x * m00 + src.y * m01 + src.z * m02 + m03;
    const double dstY = src.x * m10 + src.y * m11 + src.z * m12 + m13;
    const double dstZ = src.x * m20 + src.y * m21 + src.z * m22 + m23;
    const double dstW = src.x * m30 + src.y * m31 + src.z * m32 + m33;

    return Point2<double>(dstX / dstW, dstY / dstW, dstZ / dstW);
}

TAKErr Matrix2::createInverse(Matrix2 *t) const NOTHROWS//const throw (NonInvertibleTransformException)
{
    if (t == this)
    {
        Matrix2 temp(*this);
        return temp.createInverse(t);
    }
    const double determinant = (m00*m11*m22*m33) + (m00*m12*m23*m31) + (m00*m13*m21*m32)
        + (m01*m10*m23*m32) + (m01*m12*m20*m33) + (m01*m13*m22*m30)
        + (m02*m10*m21*m33) + (m02*m11*m23*m30) + (m02*m13*m20*m31)
        + (m03*m10*m22*m31) + (m03*m11*m20*m32) + (m03*m12*m21*m30)
        - (m00*m11*m23*m32) - (m00*m12*m21*m33) - (m00*m13*m22*m31)
        - (m01*m10*m22*m33) - (m01*m12*m23*m30) - (m01*m13*m20*m32)
        - (m02*m10*m23*m31) - (m02*m11*m20*m33) - (m02*m13*m21*m30)
        - (m03*m10*m21*m32) - (m03*m11*m22*m30) - (m03*m12*m20*m31);

    if (determinant == 0.0)
        return TAKErr::TE_Err;
       // throw NonInvertibleTransformException(
       //     "The determinant for the transform is 0.");

    const double recipDet = 1.0 / determinant;

    t->m00 = recipDet * ((m11*m22*m33) + (m12*m23*m31) + (m13*m21*m32) - (m11*m23*m32) - (m12*m21*m33) - (m13*m22*m31));
    t->m01 = recipDet * ((m01*m23*m32) + (m02*m21*m33) + (m03*m22*m31) - (m01*m22*m33) - (m02*m23*m31) - (m03*m21*m32));
    t->m02 = recipDet * ((m01*m12*m33) + (m02*m13*m31) + (m03*m11*m32) - (m01*m13*m32) - (m02*m11*m33) - (m03*m12*m31));
    t->m03 = recipDet * ((m01*m13*m22) + (m02*m11*m23) + (m03*m12*m21) - (m01*m12*m23) - (m02*m13*m21) - (m03*m11*m22));
    t->m10 = recipDet * ((m10*m23*m32) + (m12*m20*m33) + (m13*m22*m30) - (m10*m22*m33) - (m12*m23*m30) - (m13*m20*m32));
    t->m11 = recipDet * ((m00*m22*m33) + (m02*m23*m30) + (m03*m20*m32) - (m00*m23*m32) - (m02*m20*m33) - (m03*m22*m30));
    t->m12 = recipDet * ((m00*m13*m32) + (m02*m10*m33) + (m03*m12*m30) - (m00*m12*m33) - (m02*m13*m30) - (m03*m10*m32));
    t->m13 = recipDet * ((m00*m12*m23) + (m02*m13*m20) + (m03*m10*m22) - (m00*m13*m22) - (m02*m10*m23) - (m03*m12*m20));
    t->m20 = recipDet * ((m10*m21*m33) + (m11*m23*m30) + (m13*m20*m31) - (m10*m23*m31) - (m11*m20*m33) - (m13*m21*m30));
    t->m21 = recipDet * ((m00*m23*m31) + (m01*m20*m33) + (m03*m21*m30) - (m00*m21*m33) - (m01*m23*m30) - (m03*m20*m31));
    t->m22 = recipDet * ((m00*m11*m33) + (m01*m13*m30) + (m03*m10*m31) - (m00*m13*m31) - (m01*m10*m33) - (m03*m11*m30));
    t->m23 = recipDet * ((m00*m13*m21) + (m01*m10*m23) + (m03*m11*m20) - (m00*m11*m23) - (m01*m13*m20) - (m03*m10*m21));
    t->m30 = recipDet * ((m10*m22*m31) + (m11*m20*m32) + (m12*m21*m30) - (m10*m21*m32) - (m11*m22*m30) - (m12*m20*m31));
    t->m31 = recipDet * ((m00*m21*m32) + (m01*m22*m30) + (m02*m20*m31) - (m00*m22*m31) - (m01*m20*m32) - (m02*m21*m30));
    t->m32 = recipDet * ((m00*m12*m31) + (m01*m10*m32) + (m02*m11*m30) - (m00*m11*m32) - (m01*m12*m30) - (m02*m10*m31));
    t->m33 = recipDet * ((m00*m11*m22) + (m01*m12*m20) + (m02*m10*m21) - (m00*m12*m21) - (m01*m10*m22) - (m02*m11*m20));
    return TAKErr::TE_Ok;
}

void Matrix2::concatenate(const Matrix2 &t) NOTHROWS
{
    concatenateImpl(t.m00, t.m01, t.m02, t.m03,
        t.m10, t.m11, t.m12, t.m13,
        t.m20, t.m21, t.m22, t.m23,
        t.m30, t.m31, t.m32, t.m33);
}

void Matrix2::preConcatenate(const Matrix2 &t) NOTHROWS
{
    Matrix2 tCopy(t);
    tCopy.concatenate(*this);
    set(tCopy);
}

void Matrix2::set(const Matrix2 &t) NOTHROWS
{
    m00 = t.m00;
    m01 = t.m01;
    m02 = t.m02;
    m03 = t.m03;
    m10 = t.m10;
    m11 = t.m11;
    m12 = t.m12;
    m13 = t.m13;
    m20 = t.m20;
    m21 = t.m21;
    m22 = t.m22;
    m23 = t.m23;
    m30 = t.m30;
    m31 = t.m31;
    m32 = t.m32;
    m33 = t.m33;
}

TAKErr Matrix2::set(const size_t row, const size_t col, const double v) NOTHROWS
{
    switch ((row * 4) + col) {
    case 0:  m00 = v; break;
    case 1:  m01 = v; break;
    case 2:  m02 = v; break;
    case 3:  m03 = v; break;
    case 4:  m10 = v; break;
    case 5:  m11 = v; break;
    case 6:  m12 = v; break;
    case 7:  m13 = v; break;
    case 8:  m20 = v; break;
    case 9:  m21 = v; break;
    case 10: m22 = v; break;
    case 11: m23 = v; break;
    case 12: m30 = v; break;
    case 13: m31 = v; break;
    case 14: m32 = v; break;
    case 15: m33 = v; break;
    default: return TAKErr::TE_Err;
    }
    return TAKErr::TE_Ok;
}

TAKErr Matrix2::get(double *matrix, const MatrixOrder order) const NOTHROWS
{
    if (!matrix)
        return TE_InvalidArg;
    switch (order) {
    case ROW_MAJOR:
        matrix[0] = m00;
        matrix[1] = m01;
        matrix[2] = m02;
        matrix[3] = m03;
        matrix[4] = m10;
        matrix[5] = m11;
        matrix[6] = m12;
        matrix[7] = m13;
        matrix[8] = m20;
        matrix[9] = m21;
        matrix[10] = m22;
        matrix[11] = m23;
        matrix[12] = m30;
        matrix[13] = m31;
        matrix[14] = m32;
        matrix[15] = m33;
        break;
    case COLUMN_MAJOR:
        matrix[0] = m00;
        matrix[1] = m10;
        matrix[2] = m20;
        matrix[3] = m30;
        matrix[4] = m01;
        matrix[5] = m11;
        matrix[6] = m21;
        matrix[7] = m31;
        matrix[8] = m02;
        matrix[9] = m12;
        matrix[10] = m22;
        matrix[11] = m32;
        matrix[12] = m03;
        matrix[13] = m13;
        matrix[14] = m23;
        matrix[15] = m33;
        break;
    default:
       return TAKErr::TE_InvalidArg;
    }
    return TAKErr::TE_Ok;
}

TAKErr Matrix2::get(double *v, const size_t row, const size_t col) const NOTHROWS
{
    switch ((row * 4) + col) {
    case 0:  *v = m00; break;
    case 1:  *v = m01; break;
    case 2:  *v = m02; break;
    case 3:  *v = m03; break;
    case 4:  *v = m10; break;
    case 5:  *v = m11; break;
    case 6:  *v = m12; break;
    case 7:  *v = m13; break;
    case 8:  *v = m20; break;
    case 9:  *v = m21; break;
    case 10: *v = m22; break;
    case 11: *v = m23; break;
    case 12: *v = m30; break;
    case 13: *v = m31; break;
    case 14: *v = m32; break;
    case 15: *v = m33; break;
    default: return TAKErr::TE_Err;
    }
    return TAKErr::TE_Ok;
}

void Matrix2::translate(const double tx, const double ty, const double tz) NOTHROWS
{
    concatenateImpl(1, 0, 0, tx,
        0, 1, 0, ty,
        0, 0, 1, tz,
        0, 0, 0, 1);
}

TAKErr Matrix2::rotate(const double theta) NOTHROWS
{
    return rotate(theta, 0, 0, 1);
}

TAKErr Matrix2::rotate(const double theta, const double anchorx, const double anchory) NOTHROWS
{
    TAKErr code(TE_Ok);
    translate(anchorx, anchory);
    code = rotate(theta);
    TE_CHECKRETURN_CODE(code);
    translate(-anchorx, -anchory);
    return code;
}

TAKErr Matrix2::rotate(const double theta, const double axisX, const double axisY, const double axisZ) NOTHROWS
{
    const double sinTheta = sin(theta);
    const double cosTheta = cos(theta);

    const double mag = sqrt((axisX*axisX) + (axisY*axisY) + (axisZ*axisZ));

    if (mag == 0)
        return TE_Err;

    const double nX = axisX / mag;
    const double nY = axisY / mag;
    const double nZ = axisZ / mag;

    const double oneMinusCos = 1 - cosTheta;

    concatenateImpl(nX*nX*(oneMinusCos)+cosTheta, nY*nX*(oneMinusCos)-axisZ*sinTheta, nZ*nX*(oneMinusCos)+axisY*sinTheta, 0,
        nX*nY*(oneMinusCos)+axisZ*sinTheta, nY*nY*(oneMinusCos)+cosTheta, nZ*nY*(oneMinusCos)-axisX*sinTheta, 0,
        nX*nZ*(oneMinusCos)-axisY*sinTheta, nY*nZ*(oneMinusCos)+axisX*sinTheta, nZ*nZ*(oneMinusCos)+cosTheta, 0,
        0, 0, 0, 1);
    return TE_Ok;
}

TAKErr Matrix2::rotate(const double theta, const double anchorX, const double anchorY, const double anchorZ, const double axisX, const double axisY, const double axisZ) NOTHROWS
{
    TAKErr code(TE_Ok);
    this->translate(anchorX, anchorY, anchorZ);
    code = this->rotate(theta, axisX, axisY, axisZ);
    TE_CHECKRETURN_CODE(code);
    this->translate(-anchorX, -anchorY, -anchorZ);
    return code;
}


void Matrix2::scale(const double s) NOTHROWS
{
    scale(s, s, s);
}

void Matrix2::scale(const double scaleX, const double scaleY, const double scaleZ) NOTHROWS
{
    concatenateImpl(scaleX, 0, 0, 0,
        0, scaleY, 0, 0,
        0, 0, scaleZ, 0,
        0, 0, 0, 1);
}

void Matrix2::setToIdentity() NOTHROWS
{
    m00 = 1; m01 = 0; m02 = 0; m03 = 0;
    m10 = 0; m11 = 1; m12 = 0; m13 = 0;
    m20 = 0; m21 = 0; m22 = 1; m23 = 0;
    m30 = 0; m31 = 0; m32 = 0; m33 = 1;
}

void Matrix2::setToTranslate(const double tx, const double ty, const double tz) NOTHROWS
{
    m00 = 1; m01 = 0; m02 = 0; m03 = tx;
    m10 = 0; m11 = 1; m12 = 0; m13 = ty;
    m20 = 0; m21 = 0; m22 = 1; m23 = tz;
    m30 = 0; m31 = 0; m32 = 0; m33 = 1;
}

TAKErr Matrix2::setToRotate(const double theta) NOTHROWS
{
    return setToRotate(theta, 0, 0, 1);
}

TAKErr Matrix2::setToRotate(const double theta, const double axisX, const double axisY, const double axisZ) NOTHROWS
{
    const double sinTheta = sin(theta);
    const double cosTheta = cos(theta);

    const double mag = sqrt((axisX*axisX) + (axisY*axisY) + (axisZ*axisZ));

    if (mag == 0)
        return TE_Err;

    const double nX = axisX / mag;
    const double nY = axisY / mag;
    const double nZ = axisZ / mag;

    const double oneMinusCos = 1 - cosTheta;

    m00 = nX*nX*(oneMinusCos)+cosTheta;
    m01 = nY*nX*(oneMinusCos)-axisZ*sinTheta;
    m02 = nZ*nX*(oneMinusCos)+axisY*sinTheta;
    m03 = 0;
    m10 = nX*nY*(oneMinusCos)+axisZ*sinTheta;
    m11 = nY*nY*(oneMinusCos)+cosTheta;
    m12 = nZ*nY*(oneMinusCos)-axisX*sinTheta;
    m13 = 0;
    m20 = nX*nZ*(oneMinusCos)-axisY*sinTheta;
    m21 = nY*nZ*(oneMinusCos)+axisX*sinTheta;
    m22 = nZ*nZ*(oneMinusCos)+cosTheta;
    m23 = 0;
    m30 = 0;
    m31 = 0;
    m32 = 0;
    m33 = 1;
    return TE_Ok;
}

TAKErr Matrix2::setToRotate(const double theta, const double anchorx, const double anchory) NOTHROWS
{
    TAKErr code(TE_Ok);
    setToTranslate(anchorx, anchory);
    code = rotate(theta);
    TE_CHECKRETURN_CODE(code);
    translate(-anchorx, -anchory);
    return code;
}

void Matrix2::setToScale(const double scale) NOTHROWS
{
    setToScale(scale, scale, scale);
}

void Matrix2::setToScale(const double scaleX, const double scaleY, const double scaleZ) NOTHROWS
{
    m00 = scaleX;
    m01 = 0;
    m02 = 0;
    m03 = 0;
    m10 = 0;
    m11 = scaleY;
    m12 = 0;
    m13 = 0;
    m20 = 0;
    m21 = 0;
    m22 = scaleZ;
    m23 = 0;
    m30 = 0;
    m31 = 0;
    m32 = 0;
    m33 = 1;
}

bool Matrix2::operator==(const Matrix2 &other) const NOTHROWS
{
    return this->m00 == other.m00 &&
           this->m01 == other.m01 &&
           this->m02 == other.m02 &&
           this->m03 == other.m03 &&
           this->m10 == other.m10 &&
           this->m11 == other.m11 &&
           this->m12 == other.m12 &&
           this->m13 == other.m13 &&
           this->m20 == other.m20 &&
           this->m21 == other.m21 &&
           this->m22 == other.m22 &&
           this->m23 == other.m23 &&
           this->m30 == other.m30 &&
           this->m31 == other.m31 &&
           this->m32 == other.m32 &&
           this->m33 == other.m33;
}

void Matrix2::concatenateImpl(const double tm00, const double tm01, const double tm02, const double tm03,
    const double tm10, const double tm11, const double tm12, const double tm13,
    const double tm20, const double tm21, const double tm22, const double tm23,
    const double tm30, const double tm31, const double tm32, const double tm33) NOTHROWS
{
    const double rm00 = m00*tm00 + m01*tm10 + m02*tm20 + m03*tm30;
    const double rm01 = m00*tm01 + m01*tm11 + m02*tm21 + m03*tm31;
    const double rm02 = m00*tm02 + m01*tm12 + m02*tm22 + m03*tm32;
    const double rm03 = m00*tm03 + m01*tm13 + m02*tm23 + m03*tm33;
    const double rm10 = m10*tm00 + m11*tm10 + m12*tm20 + m13*tm30;
    const double rm11 = m10*tm01 + m11*tm11 + m12*tm21 + m13*tm31;
    const double rm12 = m10*tm02 + m11*tm12 + m12*tm22 + m13*tm32;
    const double rm13 = m10*tm03 + m11*tm13 + m12*tm23 + m13*tm33;
    const double rm20 = m20*tm00 + m21*tm10 + m22*tm20 + m23*tm30;
    const double rm21 = m20*tm01 + m21*tm11 + m22*tm21 + m23*tm31;
    const double rm22 = m20*tm02 + m21*tm12 + m22*tm22 + m23*tm32;
    const double rm23 = m20*tm03 + m21*tm13 + m22*tm23 + m23*tm33;
    const double rm30 = m30*tm00 + m31*tm10 + m32*tm20 + m33*tm30;
    const double rm31 = m30*tm01 + m31*tm11 + m32*tm21 + m33*tm31;
    const double rm32 = m30*tm02 + m31*tm12 + m32*tm22 + m33*tm32;
    const double rm33 = m30*tm03 + m31*tm13 + m32*tm23 + m33*tm33;

    m00 = rm00;
    m01 = rm01;
    m02 = rm02;
    m03 = rm03;
    m10 = rm10;
    m11 = rm11;
    m12 = rm12;
    m13 = rm13;
    m20 = rm20;
    m21 = rm21;
    m22 = rm22;
    m23 = rm23;
    m30 = rm30;
    m31 = rm31;
    m32 = rm32;
    m33 = rm33;
}

TAKErr TAK::Engine::Math::Matrix2_mapQuads(Matrix2 *xform, const Point2<double> &src1, const Point2<double> &src2, const Point2<double> &src3, const Point2<double> &src4,
    const Point2<double> &dst1, const Point2<double> &dst2, const Point2<double> &dst3, const Point2<double> &dst4) NOTHROWS
{
    TAKErr code;
    code = TAK::Engine::Math::Matrix2_mapQuads(xform,
        src1.x, src1.y,
        src2.x, src2.y,
        src3.x, src3.y,
        src4.x, src4.y,
        dst1.x, dst1.y,
        dst2.x, dst2.y,
        dst3.x, dst3.y,
        dst4.x, dst4.y);
    return code;
}

TAKErr TAK::Engine::Math::Matrix2_mapQuads(Matrix2 *xform, const Point2<float> &src1, const Point2<float> &src2, const Point2<float> &src3, const Point2<float> &src4,
    const Point2<float> &dst1, const Point2<float> &dst2, const Point2<float> &dst3, const Point2<float> &dst4) NOTHROWS
{
    TAKErr code;
    code = TAK::Engine::Math::Matrix2_mapQuads(xform,
        src1.x, src1.y,
        src2.x, src2.y,
        src3.x, src3.y,
        src4.x, src4.y,
        dst1.x, dst1.y,
        dst2.x, dst2.y,
        dst3.x, dst3.y,
        dst4.x, dst4.y);
    return code;
}

TAKErr TAK::Engine::Math::Matrix2_mapQuads(Matrix2 *xform, const double srcX1, const double srcY1, const double srcX2, const double srcY2, const double srcX3, const double srcY3, const double srcX4, const double srcY4,
    const double dstX1, const double dstY1, const double dstX2, const double dstY2, const double dstX3, const double dstY3, const double dstX4, const double dstY4) NOTHROWS
{
    double matrix[9];

    double quad1Arr[8];
    double *quad1[4];
    for (int i = 0; i < 4; i++)
        quad1[i] = quad1Arr + (i * 2);

    double quad2Arr[8];
    double *quad2[4];
    for (int i = 0; i < 4; i++)
        quad2[i] = quad2Arr + (i * 2);

    quad1[0][0] = srcX1; quad1[0][1] = srcY1;
    quad1[1][0] = srcX2; quad1[1][1] = srcY2;
    quad1[2][0] = srcX3; quad1[2][1] = srcY3;
    quad1[3][0] = srcX4; quad1[3][1] = srcY4;

    quad2[0][0] = dstX1; quad2[0][1] = dstY1;
    quad2[1][0] = dstX2; quad2[1][1] = dstY2;
    quad2[2][0] = dstX3; quad2[2][1] = dstY3;
    quad2[3][0] = dstX4; quad2[3][1] = dstY4;

    if (!mapQuadToQuad(quad1, quad2, matrix))
        return TAKErr::TE_Err; // XXX -

                 // XXX - need to scale the matrix???? 2D projection in the shader fails if
                 //       we don't divide through

    xform->set(0, 0, (matrix[0] / matrix[8]));
    xform->set(0, 1, (matrix[1] / matrix[8]));
    xform->set(0, 2, 0);
    xform->set(0, 3, (matrix[2] / matrix[8]));
    xform->set(1, 0, (matrix[3] / matrix[8]));
    xform->set(1, 1, (matrix[4] / matrix[8]));
    xform->set(1, 2, 0);
    xform->set(1, 3, (matrix[5] / matrix[8]));
    xform->set(2, 0, 0);
    xform->set(2, 1, 0);
    xform->set(2, 2, 1);
    xform->set(2, 3, 0);
    xform->set(3, 0, (matrix[6] / matrix[8]));
    xform->set(3, 1, (matrix[7] / matrix[8]));
    xform->set(3, 2, 0);
    xform->set(3, 3, (matrix[8] / matrix[8]));
    return TAKErr::TE_Ok;
}

namespace // unnamed namespace
{

    double determinant(const double a, const double b, const double c, const double d) {
        return ((a * d) - (b * c));
    }

    double adjoint(double ** const matrix, double **adjoint)
    {
        adjoint[0][0] = determinant(matrix[1][1], matrix[1][2],
            matrix[2][1], matrix[2][2]);
        adjoint[1][0] = determinant(matrix[1][2], matrix[1][0],
            matrix[2][2], matrix[2][0]);
        adjoint[2][0] = determinant(matrix[1][0], matrix[1][1],
            matrix[2][0], matrix[2][1]);
        adjoint[0][1] = determinant(matrix[2][1], matrix[2][2],
            matrix[0][1], matrix[0][2]);
        adjoint[1][1] = determinant(matrix[2][2], matrix[2][0],
            matrix[0][2], matrix[0][0]);
        adjoint[2][1] = determinant(matrix[2][0], matrix[2][1],
            matrix[0][0], matrix[0][1]);
        adjoint[0][2] = determinant(matrix[0][1], matrix[0][2],
            matrix[1][1], matrix[1][2]);
        adjoint[1][2] = determinant(matrix[0][2], matrix[0][0],
            matrix[1][2], matrix[1][0]);
        adjoint[2][2] = determinant(matrix[0][0], matrix[0][1],
            matrix[1][0], matrix[1][1]);

        return matrix[0][0] * adjoint[0][0] +
            matrix[0][1] * adjoint[0][1] +
            matrix[0][2] * adjoint[0][2];
    }

    void multiply(double** const m1, double** const m2, double** dst)
    {
        dst[0][0] = m1[0][0] * m2[0][0] + m1[0][1] * m2[1][0] + m1[0][2] * m2[2][0];
        dst[0][1] = m1[0][0] * m2[0][1] + m1[0][1] * m2[1][1] + m1[0][2] * m2[2][1];
        dst[0][2] = m1[0][0] * m2[0][2] + m1[0][1] * m2[1][2] + m1[0][2] * m2[2][2];
        dst[1][0] = m1[1][0] * m2[0][0] + m1[1][1] * m2[1][0] + m1[1][2] * m2[2][0];
        dst[1][1] = m1[1][0] * m2[0][1] + m1[1][1] * m2[1][1] + m1[1][2] * m2[2][1];
        dst[1][2] = m1[1][0] * m2[0][2] + m1[1][1] * m2[1][2] + m1[1][2] * m2[2][2];
        dst[2][0] = m1[2][0] * m2[0][0] + m1[2][1] * m2[1][0] + m1[2][2] * m2[2][0];
        dst[2][1] = m1[2][0] * m2[0][1] + m1[2][1] * m2[1][1] + m1[2][2] * m2[2][1];
        dst[2][2] = m1[2][0] * m2[0][2] + m1[2][1] * m2[1][2] + m1[2][2] * m2[2][2];
    }

    bool mapQuadToQuad(double ** const quad1, double ** const quad2, double *matrix)
    {
        double q1_q2Arr[9];
        double *q1_q2[3];
        q1_q2[0] = q1_q2Arr + 0;
        q1_q2[1] = q1_q2Arr + 3;
        q1_q2[2] = q1_q2Arr + 6;

        double q2_q1Arr[9];
        double *q2_q1[3];
        q2_q1[0] = q2_q1Arr + 0;
        q2_q1[1] = q2_q1Arr + 3;
        q2_q1[2] = q2_q1Arr + 6;

        if (!mapQuadToQuad(quad1, quad2, q1_q2, q2_q1))
            return false;
        for (int i = 0; i < 9; i++)
            matrix[i] = q1_q2[i % 3][i / 3];
        return true;
    }

    bool mapQuadToQuad(double** const quad1, double** const quad2,
        double** q1_q2, double** q2_q1)
    {
        double s_q1Arr[9];
        double *s_q1[3];
        s_q1[0] = s_q1Arr + 0;
        s_q1[1] = s_q1Arr + 3;
        s_q1[2] = s_q1Arr + 6;

        if (mapUnitSquareToQuad(quad1, s_q1) != TE_Ok)
            return false;

        double s_q2Arr[9];
        double *s_q2[3];
        s_q2[0] = s_q2Arr + 0;
        s_q2[1] = s_q2Arr + 3;
        s_q2[2] = s_q2Arr + 6;
        if (mapUnitSquareToQuad(quad2, s_q2) != TE_Ok)
            return false;

        double q1_sArr[9];
        double *q1_s[3];
        q1_s[0] = q1_sArr + 0;
        q1_s[1] = q1_sArr + 3;
        q1_s[2] = q1_sArr + 6;

        adjoint(s_q1, q1_s);

        multiply(q1_s, s_q2, q1_q2);
        adjoint(q1_q2, q2_q1);

        return true;
    }

    TAKErr mapUnitSquareToQuad(double** const quad, double** square2quad) {
        double px, py;

        px = quad[0][0] - quad[1][0] + quad[2][0] - quad[3][0];
        py = quad[0][1] - quad[1][1] + quad[2][1] - quad[3][1];

        if (isEquivalentToZero(px) && isEquivalentToZero(py)) { /* affine */
            square2quad[0][0] = quad[1][0] - quad[0][0];
            square2quad[1][0] = quad[2][0] - quad[1][0];
            square2quad[2][0] = quad[0][0];
            square2quad[0][1] = quad[1][1] - quad[0][1];
            square2quad[1][1] = quad[2][1] - quad[1][1];
            square2quad[2][1] = quad[0][1];
            square2quad[0][2] = 0.;
            square2quad[1][2] = 0.;
            square2quad[2][2] = 1.;

            return TE_Ok;
        }
        else { /* projective */
            const double dx1 = quad[1][0] - quad[2][0];
            const double dx2 = quad[3][0] - quad[2][0];
            const double dy1 = quad[1][1] - quad[2][1];
            const double dy2 = quad[3][1] - quad[2][1];
            const double del = determinant(dx1, dx2, dy1, dy2);

            if (del == 0)
                return TE_Err;

            square2quad[0][2] = determinant(px, dx2, py, dy2) / del;
            square2quad[1][2] = determinant(dx1, px, dy1, py) / del;
            square2quad[2][2] = 1.;
            square2quad[0][0] = quad[1][0] - quad[0][0] + square2quad[0][2]
                * quad[1][0];
            square2quad[1][0] = quad[3][0] - quad[0][0] + square2quad[1][2]
                * quad[3][0];
            square2quad[2][0] = quad[0][0];
            square2quad[0][1] = quad[1][1] - quad[0][1] + square2quad[0][2]
                * quad[1][1];
            square2quad[1][1] = quad[3][1] - quad[0][1] + square2quad[1][2]
                * quad[3][1];
            square2quad[2][1] = quad[0][1];

            return TE_Ok;
        }
    }

    bool isEquivalentToZero(const double n)
    {
        return ((n < 1e-13) && (n > -1e-13));
    }

}; // end unnamed namespace

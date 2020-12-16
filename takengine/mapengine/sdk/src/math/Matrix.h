#ifndef ATAKMAP_CORE_PROJECTIVE_TRANSFORM_H_INCLUDED
#define ATAKMAP_CORE_PROJECTIVE_TRANSFORM_H_INCLUDED

#include <string>

#include "math/Matrix2.h"
#include "math/Point.h"
#include "port/Platform.h"
#include "util/Error.h"

namespace atakmap
{
namespace math
{

class ENGINE_API NonInvertibleTransformException : public std::exception {
public :
    NonInvertibleTransformException() :
        msg()
    {}

    NonInvertibleTransformException(const std::string &message) :
        msg(message)
    {}

    virtual ~NonInvertibleTransformException() NOTHROWS {}
public :
    virtual const char *what() const throw () override
    {
        return msg.c_str();
    }
private :
    std::string msg;
};

class ENGINE_API Matrix {
public :
    enum MatrixOrder {
        ROW_MAJOR,
        COLUMN_MAJOR,
    };

public:
    Matrix();
    Matrix(double m00, double m01, double m02, double m03,
           double m10, double m11, double m12, double m13,
           double m20, double m21, double m22, double m23,
           double m30, double m31, double m32, double m33);
    Matrix(const TAK::Engine::Math::Matrix2 &m);
    Matrix(TAK::Engine::Math::Matrix2Ptr &&m);
    Matrix(const Matrix &other);
public :
    ~Matrix();
public :
    void transform(const Point<double> *src, Point<double> *dst) const;
    void createInverse(Matrix *t) const throw (NonInvertibleTransformException);
    void concatenate(const Matrix *t);
    void preConcatenate(const Matrix *t);
    void set(const Matrix *t);
    void set(const size_t row, const size_t col, const double v);
    void get(double *matrix, const MatrixOrder order = ROW_MAJOR) const;
    void get(const size_t row, const size_t col, double *v) const;
    void translate(const double tx, const double ty, const double tz = 0.0);
    void rotate(const double theta);
    void rotate(const double theta, const double anchorX, const double anchorY);
    void rotate(const double theta, const double axisX, const double axisY, const double axisZ);
    void scale(const double scale);
    void scale(const double scaleX, const double scaleY, const double scaleZ = 1.0);
    void setToIdentity();
    void setToTranslate(const double tx, const double ty, const double tz = 0.0);
    void setToRotate(const double theta);
    void setToRotate(const double theta, const double anchorX, const double anchorY);
    void setToRotate(const double theta, const double axisX, const double axisY, const double axisZ);
    void setToScale(const double scale);
    void setToScale(const double scaleX, const double scaleY, const double scaleZ = 1.0);
    Matrix &operator=(const Matrix &);
public :
    static void mapQuads(const Point<float> *src1, const Point<float> *src2, const Point<float> *src3, const Point<float> *src4,
                         const Point<float> *dst1, const Point<float> *dst2, const Point<float> *dst3, const Point<float> *dst4,
                         Matrix *xform);
    static void mapQuads(const Point<double> *src1, const Point<double> *src2, const Point<double> *src3, const Point<double> *src4,
                         const Point<double> *dst1, const Point<double> *dst2, const Point<double> *dst3, const Point<double> *dst4,
                         Matrix *xform);
    static void mapQuads(const double srcX1, const double srcY1, const double srcX2, const double srcY2, const double srcX3, const double srcY3, const double srcX4, const double srcY4,
                         const double dstX1, const double dstY1, const double dstX2, const double dstY2, const double dstX3, const double dstY3, const double dstX4, const double dstY4,
                         Matrix *xform);
private :
    TAK::Engine::Math::Matrix2Ptr impl;

    friend ENGINE_API TAK::Engine::Util::TAKErr Matrix_adapt(TAK::Engine::Math::Matrix2 *, const Matrix &) NOTHROWS;
};

/**
* Copies the transform from the specified legacy Matrix instance
* into the provided Matrix2 instance.
*/
ENGINE_API TAK::Engine::Util::TAKErr Matrix_adapt(TAK::Engine::Math::Matrix2 *value, const Matrix &legacy) NOTHROWS;

} // end namespace atakmap::core
} // end namespace atakmap

#endif // ATAKMAP_CORE_PROJECTIVE_TRANSFORM_H_INCLUDED

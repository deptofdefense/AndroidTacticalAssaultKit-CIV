#include "feature/QuadBlob.h"

#include "feature/Point.h"
#include "util/IO.h"

using namespace atakmap::feature;

using namespace atakmap::util;

#define QUAD_BLOB_SIZE 132

namespace
{
    void initBlob(uint8_t *quadblob, Endian endian);

    template<class T>
    struct ByteBuffer
    {
        uint8_t *(*put)(uint8_t *buf, size_t idx, T val);
    };

    template<class T>
    uint8_t *putNative(uint8_t *buf, size_t idx, T val);

    template<class T>
    uint8_t *putNativeReverse(uint8_t *buf, size_t idx, T val);

    template<class T>
    ByteBuffer<T> getByteBuffer(Endian endian);

    double min4(double a, double b, double c, double d);
    double max4(double a, double b, double c, double d);
}

QuadBlob::QuadBlob() :
    endian(PlatformEndian)
{
    initBlob(this->quad, endian);
}

QuadBlob::QuadBlob(Endian endian_) :
    endian(endian_)
{
    initBlob(this->quad, endian);
}

uint8_t *QuadBlob::getBlob(const Point *a, const Point *b, const Point *c, const Point *d)
{
    ByteBuffer<double> doubleBuffer = getByteBuffer<double>(this->endian);

    const double alat = a->y;
    const double alng = a->x;
    const double blat = b->y;
    const double blng = b->x;
    const double clat = c->y;
    const double clng = c->x;
    const double dlat = d->y;
    const double dlng = d->x;

    doubleBuffer.put(this->quad, 51, alng);
    doubleBuffer.put(this->quad, 59, alat);
    doubleBuffer.put(this->quad, 67, blng);
    doubleBuffer.put(this->quad, 75, blat);
    doubleBuffer.put(this->quad, 83, clng);
    doubleBuffer.put(this->quad, 91, clat);
    doubleBuffer.put(this->quad, 99, dlng);
    doubleBuffer.put(this->quad, 107, dlat);
    doubleBuffer.put(this->quad, 115, alng);
    doubleBuffer.put(this->quad, 123, alat);

    const double mbrMinX = min4(alng, blng, clng, dlng);
    const double mbrMinY = min4(alat, blat, clat, dlat);
    const double mbrMaxX = max4(alng, blng, clng, dlng);
    const double mbrMaxY = max4(alat, blat, clat, dlat);

    doubleBuffer.put(this->quad, 6, mbrMinX);
    doubleBuffer.put(this->quad, 14, mbrMinY);
    doubleBuffer.put(this->quad, 22, mbrMaxX);
    doubleBuffer.put(this->quad, 30, mbrMaxY);

    return this->quad;
}

size_t QuadBlob::getQuadBlobSize()
{
    return QUAD_BLOB_SIZE;
}

namespace
{
    void initBlob(uint8_t *quadblob, Endian endian)
    {
        ByteBuffer<int32_t> intBuffer = getByteBuffer<int32_t>(endian);

        const uint8_t blobEndian = (endian == Endian::BIG_ENDIAN) ? 0x00 : 0x01;

        uint8_t *pQuadblob = quadblob;

        *pQuadblob++ = (uint8_t)0x00;
        *pQuadblob++ = blobEndian;
        pQuadblob = intBuffer.put(pQuadblob, 0, 4326);
        pQuadblob += 32;
        *pQuadblob++ = (uint8_t)0x7C;
        pQuadblob = intBuffer.put(pQuadblob, 0, 0x03);

        pQuadblob = intBuffer.put(pQuadblob, 0, 1);
        pQuadblob = intBuffer.put(pQuadblob, 0, 5);

        quadblob[QUAD_BLOB_SIZE - 1] = 0xFE;
    }

    template<class T>
    uint8_t *putNative(uint8_t *buf, size_t idx, T val)
    {
        T *tBuf = reinterpret_cast<T *>(buf + idx);
        *tBuf = val;
        return buf + idx + sizeof(T);
    }

    template<class T>
    uint8_t *putNativeReverse(uint8_t *buf, size_t idx, T val)
    {
        const size_t retval = sizeof(T);
        const auto *valPtr = reinterpret_cast<const uint8_t *>(&val);
        for (size_t i = 0; i < retval; i++) {
            buf[i+idx] = valPtr[retval - i - 1];
        }
        return buf + idx + retval;
    }

    template<class T>
    ByteBuffer<T> getByteBuffer(Endian endian)
    {
        ByteBuffer<T> retval;
        retval.put = (PlatformEndian == endian) ? putNative<T> : putNativeReverse<T>;
        return retval;
    }

    double min4(double a, double b, double c, double d)
    {
        double retval = a;
        if (retval > b)
            retval = b;
        if (retval > c)
            retval = c;
        if (retval > d)
            retval = d;
        return retval;
    }

    double max4(double a, double b, double c, double d)
    {
        double retval = a;
        if (retval < b)
            retval = b;
        if (retval < c)
            retval = c;
        if (retval < d)
            retval = d;
        return retval;
    }
}


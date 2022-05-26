#include <feature/QuadBlob2.h>

#include <cinttypes>

#ifdef MSVC
#include "vscompat.h"
#endif

#include <feature/Point2.h>
#include <util/DataOutput2.h>



using namespace TAK::Engine::Feature;
using namespace TAK::Engine::Util;


namespace
{
    double min4(double a, double b, double c, double d);
    double max4(double a, double b, double c, double d);
}

TAK::Engine::Util::TAKErr TAK::Engine::Feature::QuadBlob2_get(uint8_t *value, const std::size_t &len, const TAKEndian order, const Point2 &a, const Point2 &b, const Point2 &c, const Point2 &d) NOTHROWS
{
    if (TE_QUADBLOB_SIZE > len)
        return TE_IO;

    TAKErr code(TE_Ok);

    MemoryOutput2 output;

    code = output.open(value, len);
    TE_CHECKRETURN_CODE(code);

    output.setSourceEndian2(TE_PlatformEndian);

    const uint8_t blobEndian = (TE_BigEndian == order) ? 0x00 : 0x01;

    const double &alat = a.y;
    const double &alng = a.x;
    const double &blat = b.y;
    const double &blng = b.x;
    const double &clat = c.y;
    const double &clng = c.x;
    const double &dlat = d.y;
    const double &dlng = d.x;

    const double mbrMinX = min4(alng, blng, clng, dlng);
    const double mbrMinY = min4(alat, blat, clat, dlat);
    const double mbrMaxX = max4(alng, blng, clng, dlng);
    const double mbrMaxY = max4(alat, blat, clat, dlat);

    code = output.writeByte(0x00);
    TE_CHECKRETURN_CODE(code);
    code = output.writeByte(blobEndian);
    TE_CHECKRETURN_CODE(code);
    code = output.writeInt(4326);
    TE_CHECKRETURN_CODE(code);
    code = output.writeDouble(mbrMinX);
    TE_CHECKRETURN_CODE(code);
    code = output.writeDouble(mbrMinY);
    TE_CHECKRETURN_CODE(code);
    code = output.writeDouble(mbrMaxX);
    TE_CHECKRETURN_CODE(code);
    code = output.writeDouble(mbrMaxY);
    TE_CHECKRETURN_CODE(code);
    code = output.writeByte(0x7c);
    TE_CHECKRETURN_CODE(code);
    code = output.writeInt(0x03);
    TE_CHECKRETURN_CODE(code);
    code = output.writeInt(1);
    TE_CHECKRETURN_CODE(code);
    code = output.writeInt(5);
    TE_CHECKRETURN_CODE(code);
    code = output.writeDouble(alng);
    TE_CHECKRETURN_CODE(code);
    code = output.writeDouble(alat);
    TE_CHECKRETURN_CODE(code);
    code = output.writeDouble(blng);
    TE_CHECKRETURN_CODE(code);
    code = output.writeDouble(blat);
    TE_CHECKRETURN_CODE(code);
    code = output.writeDouble(clng);
    TE_CHECKRETURN_CODE(code);
    code = output.writeDouble(clat);
    TE_CHECKRETURN_CODE(code);
    code = output.writeDouble(dlng);
    TE_CHECKRETURN_CODE(code);
    code = output.writeDouble(dlat);
    TE_CHECKRETURN_CODE(code);
    code = output.writeDouble(alng);
    TE_CHECKRETURN_CODE(code);
    code = output.writeDouble(alat);
    TE_CHECKRETURN_CODE(code);
    code = output.writeByte(0xfe);

    return code;
}


namespace
{
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


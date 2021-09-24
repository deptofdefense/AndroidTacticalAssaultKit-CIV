#include "formats/dted/DtedSampler.h"

#include <map>
#include <vector>

#include "elevation/ElevationManager.h"
#include "formats/dted/DtedChunkReader.h"
#include "math/Rectangle.h"
#include "thread/Mutex.h"
#include "thread/Lock.h"
#include "util/ConfigOptions.h"
#include "util/DataInput2.h"
#include "util/MathUtils.h"

using namespace TAK::Engine::Formats::DTED;

using namespace TAK::Engine::Elevation;
using namespace TAK::Engine::Util;

#define _NUM_LNG_LINES_OFFSET 47
#define _HEADER_OFFSET 3428
#define _DATA_RECORD_PREFIX_SIZE 8
#define _DATA_RECORD_SUFFIX_SIZE 4

namespace {
/** Checks the sample and interprets it */
double interpretSample(const unsigned short s) NOTHROWS;
/** Actually acquires the height from the given parameters in MSL */
TAKErr getHeight(double *value, FileInput2 &fs, const double latitude, const double longitude) NOTHROWS;
/** Reads and interprets the DTED file. */
TAKErr readAndInterp(double *value, FileInput2 &fs, const int dataRecSize, const double xratio, const double yratio) NOTHROWS;
}  // namespace

DtedSampler::DtedSampler(const char *file_, const double latitude, const double longitude) NOTHROWS :
    file(file_),
    bounds(longitude, latitude-1.0, 0.0, longitude+1.0, latitude, 0.0)
{
}

TAKErr DtedSampler::sample(double *value, const double latitude, const double longitude) NOTHROWS {
    TAKErr code(TE_Ok);
    if (!atakmap::math::Rectangle<double>::contains(bounds.minX, bounds.minY, bounds.maxX, bounds.maxY, longitude, latitude))
        return TE_InvalidArg;

    double msl;
    code = DTED_sample(&msl, file, latitude, longitude);
    TE_CHECKRETURN_CODE(code);
    double msl2hae;
    if (TE_ISNAN(msl) || ElevationManager_getGeoidHeight(&msl2hae, latitude, longitude) != TE_Ok) return TE_Err;
    *value = (msl + msl2hae);
    return code;
}

TAKErr DtedSampler::sample(double *value, const std::size_t count, const double *srcLat, const double *srcLng,
                           const std::size_t srcLatStride, const std::size_t srcLngStride, const std::size_t dstStride) NOTHROWS {
    TAKErr code(TE_Ok);
    if (!value) return TE_InvalidArg;

    FileInput2 fs;
    code = fs.open(file);
    TE_CHECKRETURN_CODE(code);

    //Read the DTED header -From Dt2ElevationData.java
    DtedChunkReader dt(512u, bounds.maxY, bounds.minX);
    code = dt.readHeader(fs);
    TE_CHECKRETURN_CODE(code);

    //Start iterating the points -From Dt2ElevationData.java

    //We use a dictonary in place of the Java Hashmap

    struct PointRecord
    {
        double lat;
        double lng;
        std::size_t idx;
    };

    std::map<uint32_t, std::vector<PointRecord>> chunkedPoints;                
    for(std::size_t i = 0u; i < count; i++) {
        if (!TE_ISNAN(value[i * dstStride]))
            continue;

        PointRecord rec;
        rec.lat = srcLat[i*srcLatStride];
        rec.lng = srcLng[i*srcLngStride];
        rec.idx = i*dstStride;

        if (!atakmap::math::Rectangle<double>::contains(bounds.minX,
            bounds.minY,
            bounds.maxX,
            bounds.maxY,
            rec.lng,
            rec.lat))
        {
            // stays NAN, mark as incomplete
            code = TE_Done;
            continue;
        }

        std::size_t chunkX;
        if (dt.getChunkX(&chunkX, bounds.minX, rec.lng) != TE_Ok)
            continue;
        std::size_t chunkY;
        if (dt.getChunkY(&chunkY, bounds.maxY, rec.lat) != TE_Ok)
            continue;
        const uint32_t key = (uint32_t)((chunkX & 0xFFFF) << 16) | (chunkY & 0xFFFF);
        std::vector<PointRecord>& pts = chunkedPoints[key];
        if (pts.empty())
            pts.reserve(count - i);
        pts.push_back(rec);
    }

    for (auto chunksIter = chunkedPoints.begin(); chunksIter != chunkedPoints.end(); chunksIter++)
    {
        uint32_t key = chunksIter->first;
        const std::size_t chunkX = (key >> 16) & 0xFFFF;
        const std::size_t chunkY = (key & 0xFFFF);
        if (dt.loadChunk(fs, chunkX, chunkY) != TE_Ok) {
            // chunk failed to load, mark as incomplete
            code = TE_Done;
            continue;
        }
                    
        const auto chunkCols = dt.getChunkColumns();
        const auto chunkRows = dt.getChunkRows();
        const auto* chunkData = dt.getChunk();

        const auto& pts = chunksIter->second;
        const std::size_t lim = pts.size();
        for (std::size_t i = 0u; i < lim; i++)
        {
            const PointRecord& pt = pts[i];
            const double chunkPixelX = MathUtils_clamp<double>(dt.chunkLongitudeToPixelX(chunkX, pt.lng), 0.0, (double)chunkCols - 1.0);
            const double chunkPixelY = MathUtils_clamp<double>(dt.chunkLatitudeToPixelY(chunkY, pt.lat), 0.0, (double)chunkRows - 1.0);
            const std::size_t chunkLx = (std::size_t)chunkPixelX;
            const std::size_t chunkRx = (std::size_t)ceil(chunkPixelX);
            const std::size_t chunkTy = (std::size_t)chunkPixelY;
            const std::size_t chunkBy = (std::size_t)ceil(chunkPixelY);

            //These need testing, the bytebuffer in Java has a getshort, we're relying
            //on GetValue to do the same thing here.
#define getShortBE(arr, off) \
    ((short)(((arr[off] & 0xFFu) << 8u) | (arr[off + 1u] & 0xFFu)))

            double chunkUL = interpretSample(
                getShortBE(chunkData, 
                            (chunkTy * 2 * chunkCols) + chunkLx * 2));
            double chunkUR = interpretSample(
                getShortBE(chunkData,
                            (chunkTy * 2 * chunkCols) + chunkRx * 2));
            double chunkLR = interpretSample(
                getShortBE(chunkData,
                            (chunkBy * 2 * chunkCols) + chunkRx * 2));
            double chunkLL = interpretSample(
                getShortBE(chunkData,
                            (chunkBy * 2 * chunkCols) + chunkLx * 2));
#undef getShortBE

            const std::size_t dstIdx = pt.idx;
            if (TE_ISNAN(chunkUL)
                || TE_ISNAN(chunkUR)
                || TE_ISNAN(chunkLR)
                || TE_ISNAN(chunkLL)) {

                // remains NAN, mark as incomplete
                code = TE_Done;
            } else {
                double msl2hae;
                if (ElevationManager_getGeoidHeight(&msl2hae, pt.lat, pt.lng) != TE_Ok)
                    continue;

                const double wR = chunkPixelX
                    - chunkLx;
                const double wL = 1.0f - wR;
                const double wB = chunkPixelY
                    - chunkTy;
                const double wT = 1.0f - wB;

                // XXX - should we divide to average
                //       out nulls??? -From Dt2ElevationData.java
                value[dstIdx] = 
                    ((wL * wT) * chunkUL) +
                    ((wR * wT) * chunkUR) +
                    ((wR * wB) * chunkLR) +
                    ((wL * wB) * chunkLL) +
                    msl2hae;
            }
        }
    }

    return code;
}

TAKErr TAK::Engine::Formats::DTED::DTED_sample(double *value, const char *file, const double latitude, const double longitude) NOTHROWS {
    TAKErr code(TE_Ok);
    FileInput2 fs;
    code = fs.open(file);
    TE_CHECKRETURN_CODE(code);
    return getHeight(value, fs, latitude, longitude);
}

namespace {
double interpretSample(const unsigned short s) NOTHROWS {
    if (s == 0xFFFF) return NAN;
    double val = (1 - (2 * ((s & 0x8000) >> 15))) * (s & 0x7FFF);
    // per MIL-PRF89020B 3.11.2, elevation values should never exceed these values
    if ((val < -12000) || (val > 9000)) return NAN;
    return val;
}

/*
Actually acquires the height from the given parameters in MSL
*/
TAKErr getHeight(double *value, FileInput2 &fs, const double latitude, const double longitude) NOTHROWS {
    TAKErr code(TE_Ok);

    code = fs.seek(_NUM_LNG_LINES_OFFSET);
    TE_CHECKRETURN_CODE(code);
    uint8_t bytes[8u];

    std::size_t numRead;
    if (fs.read(bytes, &numRead, 8u) != TE_Ok || numRead < 8u) {  // read did not get all of the information required
        *value = NAN;
        return TE_EOF;
    }

    char lngBytes[5u];
    char latBytes[5u];
    for (std::size_t i = 0u; i < 4u; i++) {
        lngBytes[i] = bytes[i];
        latBytes[i] = bytes[i + 4u];
    }
    lngBytes[4u] = '\0';
    latBytes[4u] = '\0';

    int lngLines;
    code = TAK::Engine::Port::String_parseInteger(&lngLines, lngBytes);
    TE_CHECKRETURN_CODE(code);
    int latPoints;
    code = TAK::Engine::Port::String_parseInteger(&latPoints, latBytes);
    TE_CHECKRETURN_CODE(code);

    double latRatio = latitude - floor(latitude);
    double lngRatio = longitude - floor(longitude);

    double yd = latRatio * (latPoints - 1);
    double xd = lngRatio * (lngLines - 1);

    int x = (int)xd;
    int y = (int)yd;

    int dataRecSize = _DATA_RECORD_PREFIX_SIZE + (latPoints * 2) + _DATA_RECORD_SUFFIX_SIZE;
    int byteOffset = (_HEADER_OFFSET - _NUM_LNG_LINES_OFFSET - 8) + x * dataRecSize + _DATA_RECORD_PREFIX_SIZE + y * 2;

    code = fs.skip(byteOffset);
    TE_CHECKRETURN_CODE(code);

    return readAndInterp(value, fs, dataRecSize, xd - x, yd - y);
}

/*
Reads and interprets the DTED file.
*/
TAKErr readAndInterp(double *value, FileInput2 &fs, const int dataRecSize, const double xratio, const double yratio) NOTHROWS {
    TAKErr code(TE_Ok);
    uint8_t bb[2];
    unsigned short s;
    std::size_t numRead;

    code = fs.read(bb, &numRead, 2u);
    TE_CHECKRETURN_CODE(code);
    if (numRead < 2u) return TE_EOF;
    s = (bb[0] << 8) | (bb[1]);

    double sw = interpretSample(s);

    code = fs.read(bb, &numRead, 2u);
    TE_CHECKRETURN_CODE(code);
    if (numRead < 2u) return TE_EOF;
    s = (bb[0] << 8) | (bb[1]);

    double nw = interpretSample(s);

    fs.skip(dataRecSize - 4);

    code = fs.read(bb, &numRead, 2u);
    TE_CHECKRETURN_CODE(code);
    if (numRead < 2u) return TE_EOF;
    s = (bb[0] << 8) | (bb[1]);

    double se = interpretSample(s);

    code = fs.read(bb, &numRead, 2u);
    TE_CHECKRETURN_CODE(code);
    if (numRead < 2u) return TE_EOF;
    s = (bb[0] << 8) | (bb[1]);

    double ne = interpretSample(s);

    if (TE_ISNAN(sw) && TE_ISNAN(nw) && TE_ISNAN(se) && TE_ISNAN(ne)) {
        *value = NAN;
        return TE_Ok;
    }

    double mids = 0;
    double midn = 0;

    if (!TE_ISNAN(nw) && !TE_ISNAN(ne) && !TE_ISNAN(se) && !TE_ISNAN(sw)) {
        mids = sw + (se - sw) * xratio;
        midn = nw + (ne - nw) * xratio;
    } else if (TE_ISNAN(nw) && !TE_ISNAN(ne) && !TE_ISNAN(se) && !TE_ISNAN(sw)) {
        mids = sw + (se - sw) * xratio;
        midn = ne;
    } else if (!TE_ISNAN(nw) && TE_ISNAN(ne) && !TE_ISNAN(se) && !TE_ISNAN(sw)) {
        mids = sw + (se - sw) * xratio;
        midn = nw;
    } else if (!TE_ISNAN(nw) && !TE_ISNAN(ne) && TE_ISNAN(se) && !TE_ISNAN(sw)) {
        mids = sw;
        midn = nw + (ne - nw) * xratio;
    } else if (!TE_ISNAN(nw) && !TE_ISNAN(ne) && !TE_ISNAN(se) && TE_ISNAN(sw)) {
        mids = se;
        midn = nw + (ne - nw) * xratio;
    } else {
        *value = NAN;
        return code;
    }

    *value = mids + (midn - mids) * yratio;
    return code;
}
}  // namespace

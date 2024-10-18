#ifdef MSVC
#include "raster/tilematrix/TileMatrixTileReader.h"

#include <algorithm>
#include <cmath>

#include "port/STLVectorAdapter.h"
#include "util/MathUtils.h"
#include "util/Memory.h"

using namespace TAK::Engine::Raster::TileMatrix;

using namespace TAK::Engine::Feature;
using namespace TAK::Engine::Math;
using namespace TAK::Engine::Port;
using namespace TAK::Engine::Renderer;
using namespace TAK::Engine::Util;

TileMatrixTileReader::TileMatrixTileReader(const char *uri, TileMatrixPtr &&tiles) NOTHROWS :
    TileReader2(uri),
    impl(std::move(tiles)),
    compatible(false)
{
    do {
        TAKErr code(TE_Ok);
        STLVectorAdapter<TileMatrix::ZoomLevel> levels_a(levels);
        code = impl->getZoomLevel(levels_a);
        TE_CHECKBREAK_CODE(code);

        if (levels.empty())
            break;

        Envelope2 bounds;
        code = impl->getBounds(&bounds);
        TE_CHECKBREAK_CODE(code);

        r0.zoom = levels.back();

        Point2<double> minxy;
        code = TileMatrix_getTileIndex(&minxy, impl->getOriginX(), impl->getOriginY(), r0.zoom, bounds.minX, bounds.maxY);
        TE_CHECKBREAK_CODE(code);
        Point2<double> maxxy;
        code = TileMatrix_getTileIndex(&maxxy, impl->getOriginX(), impl->getOriginY(), r0.zoom, bounds.maxX, bounds.minY);
        TE_CHECKBREAK_CODE(code);

        r0.minX = (int64_t)(minxy.x * r0.zoom.tileWidth);
        r0.minY = (int64_t)(minxy.y * r0.zoom.tileHeight);
        r0.maxX = (int64_t)std::ceil(maxxy.x * r0.zoom.tileWidth);
        r0.maxY = (int64_t)std::ceil(maxxy.y * r0.zoom.tileHeight);

        compatible = true;
    } while (false);
}
TAKErr TileMatrixTileReader::getWidth(int64_t *value) NOTHROWS
{
    *value = (r0.maxX - r0.minX);
    return compatible ? TE_Ok : TE_IllegalState;
}
TAKErr TileMatrixTileReader::getHeight(int64_t *value) NOTHROWS
{
    *value = (r0.maxY - r0.minY);
    return compatible ? TE_Ok : TE_IllegalState;
}
TAKErr TileMatrixTileReader::getTileWidth(size_t *value) NOTHROWS
{
    *value = r0.zoom.tileWidth;
    return compatible ? TE_Ok: TE_IllegalState;
}
TAKErr TileMatrixTileReader::getTileHeight(size_t *value) NOTHROWS
{
    *value = r0.zoom.tileHeight;
    return compatible ? TE_Ok: TE_IllegalState;
}
TAKErr TileMatrixTileReader::read(uint8_t *buf, const int64_t srcX, const int64_t srcY, const int64_t srcW, const int64_t srcH, const size_t dstW, const size_t dstH) NOTHROWS
{
    if (!compatible)
        return TE_IllegalState;

    if (!dstW || !dstH)
        return TE_InvalidArg;
    if (srcW <= 0 || srcH <= 0)
        return TE_InvalidArg;

    // XXX - short-circuit if request is full tile

    TAKErr code(TE_Ok);

    // determine subsample ratio
    const double ssx = ((double)srcW / (double)dstW);
    const double ssy = ((double)srcH / (double)dstH);
    const double subsampleRatio = std::max(ssx, ssy);

    // find best zoom level
    const TileMatrix::ZoomLevel& zoom_r0 = levels.back();
    TileMatrix::ZoomLevel zoom = levels[0u];
    double zoomRatio = zoom.pixelSizeY / zoom_r0.pixelSizeY;
    for(std::size_t i = 1u; i < levels.size(); i++) {
        double zr = levels[i].pixelSizeY / zoom_r0.pixelSizeY;
        if(std::fabs(zr-subsampleRatio) < std::fabs(zoomRatio-subsampleRatio)) {
            zoom = levels[i];
            zoomRatio = zr;
        }
    }

    const int64_t srcMinX = (srcX + r0.minX);
    const int64_t srcMinY = (srcY + r0.minY);
    const int64_t srcMaxX = (srcMinX + srcW - 1LL);
    const int64_t srcMaxY = (srcMinY + srcH - 1LL);

    // convert source region to projection
    Point2<double> projul;
    code = TileMatrix_getTilePoint(&projul, impl->getOriginX(), impl->getOriginY(), r0.zoom, (int)(srcMinX/r0.zoom.tileWidth), (int)(srcMinY/r0.zoom.tileHeight), srcMinY%r0.zoom.tileWidth, srcMinY%r0.zoom.tileHeight);
    TE_CHECKRETURN_CODE(code);
    Point2<double> projlr;
    code = TileMatrix_getTilePoint(&projlr, impl->getOriginX(), impl->getOriginY(), r0.zoom, (int)(srcMaxX/r0.zoom.tileWidth), (int)(srcMaxY/r0.zoom.tileHeight), srcMaxY%r0.zoom.tileWidth, srcMaxY%r0.zoom.tileHeight);
    TE_CHECKRETURN_CODE(code);
    const Envelope2 srcRegion(projul.x, projlr.y, 0.0, projlr.x, projul.y, 0.0);

    // compute min/max tile region
    Point2<double> st;
    code = TileMatrix_getTileIndex(&st, impl->getOriginX(), impl->getOriginY(), zoom, projul.x, projul.y);
    TE_CHECKRETURN_CODE(code);
    const int stx = (int)st.x;
    const int sty = (int)st.y;
    Point2<double> ft;
    code = TileMatrix_getTileIndex(&ft, impl->getOriginX(), impl->getOriginY(), zoom, projlr.x, projlr.y);
    TE_CHECKRETURN_CODE(code);
    const int ftx = (int)ft.x;
    const int fty = (int)ft.y;

    // create destination bitmap
    Bitmap2::Format dstFmt;
    getFormat(&dstFmt);
    Bitmap2 dst(Bitmap2::DataPtr(buf, Memory_leaker_const<uint8_t>), (std::size_t)dstW, (std::size_t)dstH, dstFmt);

    // iterate all intersecting tiles
    for(int ty = sty; ty <= fty; ty++) {
        for(int tx = stx; tx <= ftx; tx++) {
            // fetch tile
            BitmapPtr tile(nullptr, nullptr);
            code = impl->getTile(tile, zoom.level, tx, ty);
            TE_CHECKBREAK_CODE(code);

            // get tile bounds
            Envelope2 dataRegion;
            TileMatrix_getTileBounds(&dataRegion, impl->getOriginX(), impl->getOriginY(), zoom, tx, ty);

            // intersect tile bounds with source region bounds
            if(dataRegion.minX < srcRegion.minX)    dataRegion.minX = srcRegion.minX;
            if(dataRegion.minY < srcRegion.minY)    dataRegion.minY = srcRegion.minY;
            if(dataRegion.maxX > srcRegion.maxX)    dataRegion.maxX = srcRegion.maxX;
            if(dataRegion.maxY > srcRegion.maxY)    dataRegion.maxY = srcRegion.maxY;

            // chip tile, if necessary
            Point2<double> minxy;
            TileMatrix_getTilePixel(&minxy, impl->getOriginX(), impl->getOriginY(), zoom, tx, ty, dataRegion.minX, dataRegion.maxY);
            Point2<double> maxxy;
            TileMatrix_getTilePixel(&maxxy, impl->getOriginX(), impl->getOriginY(), zoom, tx, ty, dataRegion.maxX, dataRegion.minY);

            const int tileSubMinX = (int)MathUtils_clamp(minxy.x, 0.0, (double)tile->getWidth() - 1.0);
            const int tileSubMinY = (int)MathUtils_clamp(minxy.y, 0.0, (double)tile->getHeight() - 1.0);
            const int tileSubMaxX = (int)MathUtils_clamp(maxxy.x, 0.0, (double)tile->getWidth() - 1.0);
            const int tileSubMaxY = (int)MathUtils_clamp(maxxy.y, 0.0, (double)tile->getHeight() - 1.0);

            if (tileSubMinX == tileSubMaxX || tileSubMinY == tileSubMaxY)
                continue;

            if (tileSubMinX > 0 || tileSubMinY > 0 || tileSubMaxX < (tile->getWidth() - 1) || tileSubMaxY < (tile->getHeight() - 1)) {
                BitmapPtr subimage(nullptr, nullptr);
                tile->subimage(subimage,
                    tileSubMinX,
                    tileSubMinY,
                    (std::size_t)(tileSubMaxX - tileSubMinX + 1u),
                    (std::size_t)(tileSubMaxY - tileSubMinY + 1u),
                    true);
                tile = std::move(subimage);
            }

            // resize tile, if necessary
            const int dstMinX = (int)((dataRegion.minX - srcRegion.minX) * ((double)(dstW-1u) / (srcRegion.maxX - srcRegion.minX)));
            const int dstMinY = (int)((dataRegion.minY - srcRegion.minY) * ((double)(dstH-1u)/ (srcRegion.maxY - srcRegion.minY)));
            const int dstMaxX = (int)((dataRegion.maxX - srcRegion.minX) * ((double)(dstW-1u) / (srcRegion.maxX - srcRegion.minX)));
            const int dstMaxY = (int)((dataRegion.maxY - srcRegion.minY) * ((double)(dstH-1u) / (srcRegion.maxY - srcRegion.minY)));

            // scale tile to dst pixel size
            const int dstRegionW = (dstMaxX - dstMinX + 1);
            const int dstRegionH = (dstMaxY - dstMinY + 1);
            if(dstRegionW != tile->getWidth() || dstRegionH != tile->getHeight()) {
                BitmapPtr scaled(new Bitmap2(*tile, dstRegionW, dstRegionH), Memory_deleter_const<Bitmap2>);
                tile = std::move(scaled);
            }

            code = dst.setRegion(*tile, dstMinX, dstMinY);
            TE_CHECKBREAK_CODE(code);
        }
    }
    TE_CHECKRETURN_CODE(code);
    
    return code;
}
TAKErr TileMatrixTileReader::getFormat(Bitmap2::Format *format) NOTHROWS
{
    *format = Bitmap2::ARGB32;
    return TE_Ok;
}
TAKErr TileMatrixTileReader::isMultiResolution(bool *value) NOTHROWS
{
    *value = (levels.size() > 1);
    return TE_Ok;
}
TAKErr TileMatrixTileReader::getTileVersion(int64_t *value, const size_t level, const int64_t tileColumn, const int64_t tileRow) NOTHROWS
{
    *value = 0LL;
    return TE_Ok;
}
#endif
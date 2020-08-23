#include "raster/tilematrix/TileMatrix.h"

#include "port/STLVectorAdapter.h"

using namespace TAK::Engine::Raster::TileMatrix;
using namespace TAK::Engine::Feature;
using namespace TAK::Engine::Math;
using namespace TAK::Engine::Port;
using namespace TAK::Engine::Util;

namespace
{
    Point2<double> getTilePointImpl(const double originX, const double originY, const TileMatrix::ZoomLevel &zoom,
        const int tileX, const int tileY, const int pixelX,
        const int pixelY) NOTHROWS;
}

TileMatrix::~TileMatrix() NOTHROWS
{}
    
TAKErr TAK::Engine::Raster::TileMatrix::TileMatrix_findZoomLevel(TileMatrix::ZoomLevel* zoomLevel, const TileMatrix& matrix, const std::size_t level) NOTHROWS
{
	if (zoomLevel == nullptr)
		return TE_InvalidArg;

	TAKErr code(TE_Ok);
	bool found = false;
	std::vector<TileMatrix::ZoomLevel> zoomLevels;
	STLVectorAdapter<TileMatrix::ZoomLevel> zoomLevelAdapter(zoomLevels);
	code = matrix.getZoomLevel(zoomLevelAdapter);
	TE_CHECKRETURN_CODE(code);
	std::vector<TileMatrix::ZoomLevel>::iterator it, itEnd = zoomLevels.end();
	for (it = zoomLevels.begin(); it != itEnd; ++it)
	{
		if (it->level == level)
		{
			*zoomLevel = *it;
			found = true;
			break;
		}
	}

	code = (found ? TE_Ok : TE_InvalidArg);
	return code;
}

TAKErr TAK::Engine::Raster::TileMatrix::TileMatrix_getTileIndex(Math::Point2<double>* index, const TileMatrix& matrix, const std::size_t level, const double x,
                                                                const double y) NOTHROWS
{
	if (index == nullptr)
		return TE_InvalidArg;

	TileMatrix::ZoomLevel zoom;
	TAKErr code = TileMatrix_findZoomLevel(&zoom, matrix, level);
	TE_CHECKRETURN_CODE(code);
	code = TileMatrix_getTileIndex(index, matrix.getOriginX(), matrix.getOriginY(), zoom, x, y);
	TE_CHECKRETURN_CODE(code);
	return code;
}

TAKErr TAK::Engine::Raster::TileMatrix::TileMatrix_getTileIndex(Point2<double>* index, double originX, double originY,
                                                                TileMatrix::ZoomLevel zoom, double x, double y) NOTHROWS
{
	if (index == nullptr)
		return TE_InvalidArg;

	index->x = (int)((x - originX) / (zoom.pixelSizeX*zoom.tileWidth));
	index->y = (int)((originY - y) / (zoom.pixelSizeY*zoom.tileHeight));
	return TE_Ok;
}

TAKErr TAK::Engine::Raster::TileMatrix::TileMatrix_getTileBounds(Envelope2* bounds, const TileMatrix& matrix, int level,
	int tileX, int tileY) NOTHROWS 
{
	if (bounds == nullptr)
		return TE_InvalidArg;

	TAKErr code(TE_Ok);
	TileMatrix::ZoomLevel zoom;
	code = TileMatrix_findZoomLevel(&zoom, matrix, level);
	TE_CHECKRETURN_CODE(code);

	//Envelope retval = new Envelope(Double.NaN, Double.NaN, 0, Double.NaN, Double.NaN, 0);

	Point2<double> point;
	point = getTilePointImpl(matrix.getOriginX(), matrix.getOriginY(), zoom, tileX, tileY, 0, zoom.tileHeight);
	bounds->minX = point.x;
	bounds->minY = point.y;
	point = getTilePointImpl(matrix.getOriginX(), matrix.getOriginY(), zoom, tileX, tileY, zoom.tileWidth, 0);
	bounds->maxX = point.x;
	bounds->maxY = point.y;

	return code;
}

TAKErr TAK::Engine::Raster::TileMatrix::TileMatrix_getTilePixel(TAK::Engine::Math::Point2<double>* pixel, const TileMatrix& matrix, const std::size_t level,
                                                                const int tileX, const int tileY,
                                            const double projX, const double projY) NOTHROWS
{
	if (pixel == nullptr)
		return TE_InvalidArg;

	TAKErr code(TE_Ok);
	TileMatrix::ZoomLevel zoom;
	code = TileMatrix_findZoomLevel(&zoom, matrix, level);
	TE_CHECKRETURN_CODE(code);

	const double tileOriginX = matrix.getOriginX() + (tileX*zoom.pixelSizeX*zoom.tileWidth);
	const double tileOriginY = matrix.getOriginY() - (tileY*zoom.pixelSizeY*zoom.tileHeight);

	pixel->x = (projX - tileOriginX) / zoom.pixelSizeX;
	pixel->y = (tileOriginY - projY) / zoom.pixelSizeY;
	return code;
}

TAKErr TAK::Engine::Raster::TileMatrix::TileMatrix_getTilePoint(Math::Point2<double>* point, const TileMatrix& matrix, const std::size_t level, const int tileX,
                                                                const int tileY, const int pixelX,
                                            const int pixelY) NOTHROWS
{
	if (point == nullptr)
		return TE_InvalidArg;

	TAKErr code(TE_Ok);
	TileMatrix::ZoomLevel zoom;
	code = TileMatrix_findZoomLevel(&zoom, matrix, level);
	TE_CHECKRETURN_CODE(code);

	*point = getTilePointImpl(matrix.getOriginX(), matrix.getOriginY(), zoom, tileX, tileY, pixelX, pixelY);
	return code;
}

bool TAK::Engine::Raster::TileMatrix::TileMatrix_isQuadtreeable(const TileMatrix& matrix) NOTHROWS
{
	return false;
}

TAKErr TAK::Engine::Raster::TileMatrix::TileMatrix_createQuadtree(Port::Collection<TileMatrix::ZoomLevel>* value, const TileMatrix::ZoomLevel level0,
                                                                  const std::size_t numLevels) NOTHROWS
{
	if (value == nullptr)
		return TE_InvalidArg;

	TAKErr code(TE_Ok);
	value->add(level0);
	TileMatrix::ZoomLevel old = level0;
	for (std::size_t i = 1; i < numLevels; i++) {
		TileMatrix::ZoomLevel current;
		current.level = old.level + 1;
		current.resolution = old.resolution / 2;
		current.tileWidth = old.tileWidth;
		current.tileHeight = old.tileHeight;
		current.pixelSizeX = old.pixelSizeX / 2;
		current.pixelSizeY = old.pixelSizeY / 2;
		value->add(current);
		old = current;
	}
	return code;
}

namespace
{
    Point2<double> getTilePointImpl(const double originX, const double originY, const TileMatrix::ZoomLevel &zoom,
        const int tileX, const int tileY, const int pixelX,
        const int pixelY) NOTHROWS
    {
        double x = originX + ((tileX*zoom.pixelSizeX*zoom.tileWidth) + (zoom.pixelSizeX*pixelX));
        double y = originY - ((tileY*zoom.pixelSizeY*zoom.tileHeight) + (zoom.pixelSizeY*pixelY));
        Point2<double> point(x, y);
        return point;
    }
}
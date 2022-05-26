#ifndef TAK_ENGINE_GEOPACKAGE_TILEMATRIX_H_INCLUDED
#define TAK_ENGINE_GEOPACKAGE_TILEMATRIX_H_INCLUDED

#include "feature/Envelope2.h"
#include "math/Point2.h"
#include "port/Collection.h"
#include "port/Platform.h"
#include "util/Error.h"
#include "renderer/Bitmap2.h"

namespace TAK {
    namespace Engine {

        namespace Feature {
            class Envelope2;
        }

        namespace Renderer {
            class Bitmap2;
        }

        namespace Raster {
            namespace TileMatrix {

                /**
                 * Object class representing the tables required to access tile information from
                 * a GeoPackage formatted SQLite database. For more information about the tables
                 * related to tile data in a GeoPackage database, see section 2.2 of the GeoPackage
                 * specification at http://www.opengeospatial.org/standards/geopackage.
                 */
                class ENGINE_API TileMatrix {
                public:
                    struct ZoomLevel {
                       public:
                        /**
                         * The level.
                         */
                        int level;
                        /**
                         * Informative value indicating the nominal resolution of a tile at the
                         * zoom level in meters per pixel.
                         */
                        double resolution;
                        /**
                         * The horizontal spacing between adjacent pixels, in projection units.
                         */
                        double pixelSizeX;
                        /**
                         * The vertical spacing between adjacent pixels, in projection units.
                         */
                        double pixelSizeY;
                        /**
                         * The width of a tile at the zoom level, in pixels.
                         */
                        int tileWidth;
                        /**
                         * The height of a tile at the zoom level, in pixels.
                         */
                        int tileHeight;

                        bool operator==(const ZoomLevel& a) const NOTHROWS {
                            return ((this->level == a.level) && (this->pixelSizeX == a.pixelSizeX) && (this->pixelSizeY == a.pixelSizeY) &&
                                    (this->resolution == a.resolution) && (this->tileHeight == a.tileHeight) && (this->tileWidth == a.tileWidth));
                        }
                    };

                    virtual ~TileMatrix() NOTHROWS;
                    virtual const char* getName() const NOTHROWS = 0;
                    virtual int getSRID() const NOTHROWS = 0;
                    virtual Util::TAKErr getZoomLevel(Port::Collection<ZoomLevel>& value) const NOTHROWS = 0;
                    virtual double getOriginX() const NOTHROWS = 0;
                    virtual double getOriginY() const NOTHROWS = 0;
                    virtual Util::TAKErr getTile(Renderer::BitmapPtr& result, const std::size_t zoom, const std::size_t x, const std::size_t y) NOTHROWS = 0;
                    virtual Util::TAKErr getTileData(std::unique_ptr<const uint8_t, void (*)(const uint8_t*)>& value, std::size_t* len,
                                                     const std::size_t zoom, const std::size_t x, const std::size_t y) NOTHROWS = 0;
                    /**
                     * Returns the minimum bounding box of the data containing region per the
                     * native spatial reference of the content.
                     *
                     * <P>Note that the returned value may not <I>snap</I> to the tile grid.
                     *
                     * @return
                     *
                     * @see #getSpatialReferenceId()
                     */
                    virtual Util::TAKErr getBounds(Feature::Envelope2 *value) const NOTHROWS = 0;
                };
                typedef std::unique_ptr<TileMatrix, void (*)(const TileMatrix*)> TileMatrixPtr;

            
                ENGINE_API Util::TAKErr TileMatrix_findZoomLevel(TileMatrix::ZoomLevel* zoomLevel, const TileMatrix& matrix, const std::size_t level) NOTHROWS;

                ENGINE_API Util::TAKErr TileMatrix_getTileIndex(Math::Point2<double>* index, const TileMatrix& matrix, const std::size_t level, const double x,
                                                                const double y) NOTHROWS;

                ENGINE_API Util::TAKErr TileMatrix_getTileIndex(Math::Point2<double>* index, const double originX, const double originY,
                                                                TileMatrix::ZoomLevel zoom, const double x,
                                            const double y) NOTHROWS;

                ENGINE_API Util::TAKErr TileMatrix_getTileBounds(Feature::Envelope2* bounds, const TileMatrix& matrix, const int level, const int tileX,
                                                                 const int tileY) NOTHROWS;

                ENGINE_API Util::TAKErr TileMatrix_getTilePixel(TAK::Engine::Math::Point2<double>* pixel, const TileMatrix& matrix, const std::size_t level,
                                                                const int tileX, const int tileY,
                                            const double projX, const double projY) NOTHROWS;

                ENGINE_API Util::TAKErr TileMatrix_getTilePoint(Math::Point2<double>* point, const TileMatrix& matrix, const std::size_t level, const int tileX,
                                                                const int tileY, const int pixelX,
                                            const int pixelY) NOTHROWS;

                ENGINE_API bool TileMatrix_isQuadtreeable(const TileMatrix& matrix) NOTHROWS;

                ENGINE_API Util::TAKErr TileMatrix_createQuadtree(Port::Collection<TileMatrix::ZoomLevel>* value, const TileMatrix::ZoomLevel level0,
                                                                  const std::size_t numLevels) NOTHROWS;
            }

        }  // namespace Raster
    }  // namespace Engine
}  // namespace TAK

#endif

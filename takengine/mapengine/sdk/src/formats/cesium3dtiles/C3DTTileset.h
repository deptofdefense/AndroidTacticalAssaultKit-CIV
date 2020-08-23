#ifndef TAK_ENGINE_FORMATS_CESIUM3DTILES_C3DTTILESET_H_INCLUDED
#define TAK_ENGINE_FORMATS_CESIUM3DTILES_C3DTTILESET_H_INCLUDED

#include "util/Error.h"
#include "port/String.h"
#include "util/DataInput2.h"

namespace TAK {
    namespace Engine {
        namespace Math {
            class Matrix2;
        }
        namespace Feature {
            class Envelope2;
        }

        namespace Formats {
            namespace Cesium3DTiles {

                struct ENGINE_API C3DTAsset {
                    C3DTAsset();
                    ~C3DTAsset() NOTHROWS;

                    Port::String version;
                    Port::String tilesetVersion;
                };

                struct ENGINE_API C3DTBox {

                    C3DTBox() NOTHROWS;
                    ~C3DTBox() NOTHROWS;

                    double centerX;
                    double centerY;
                    double centerZ;
                    double xDirHalfLen[3];
                    double yDirHalfLen[3];
                    double zDirHalfLen[3];
                };

                struct ENGINE_API C3DTRegion {

                    C3DTRegion() NOTHROWS;
                    ~C3DTRegion() NOTHROWS;

                    double west;
                    double south;
                    double east;
                    double north;
                    double minimumHeight;
                    double maximumHeight;
                };

                struct ENGINE_API D3DTSphere {

                    D3DTSphere() NOTHROWS;
                    ~D3DTSphere() NOTHROWS;

                    double centerX;
                    double centerY;
                    double centerZ;
                    double radius;
                };

                struct ENGINE_API C3DTVolume {

                    C3DTVolume() NOTHROWS;
                    ~C3DTVolume() NOTHROWS;

                    enum Type {
                        Undefined,
                        Box,
                        Region,
                        Sphere
                    };

                    union VolumeObject {
                        VolumeObject() NOTHROWS;
                        ~VolumeObject() NOTHROWS;
                        C3DTBox box;
                        C3DTRegion region;
                        D3DTSphere sphere;
                    } object;
                    Type type;
                };

                enum class C3DTRefine {
                    Undefined,
                    Add,
                    Replace
                };

                struct ENGINE_API C3DTContent {

                    C3DTContent();
                    ~C3DTContent() NOTHROWS;

                    C3DTVolume boundingVolume;
                    Port::String uri;
                };

                struct ENGINE_API C3DTTile {
                    C3DTTile();
                    ~C3DTTile() NOTHROWS;

                    const C3DTTile *parent;
                    C3DTVolume boundingVolume;
                    C3DTVolume viewerRequestVolume;
                    double geometricError;
                    C3DTRefine refine;
                    C3DTContent content;
                    double transform[16];
                    size_t childCount;
                    bool hasTransform;
                };

                struct ENGINE_API C3DTExtras {

                    C3DTExtras() NOTHROWS;
                    ~C3DTExtras() NOTHROWS;

                    Util::TAKErr getString(Port::String *value, const char *name) const NOTHROWS;

                    struct Impl;
                    Impl *impl;
                };

                struct ENGINE_API C3DTTileset {

                    C3DTTileset();
                    ~C3DTTileset() NOTHROWS;

                    C3DTAsset asset;
                    C3DTExtras extras;
                    double geometricError;
                };

                /**
                 * Visitor function pointer definition for Cesium 3D Tiles Tileset.
                 *
                 * @param opaque user defined pointer
                 * @param tilset the tileset container parameters
                 * @param tile the current visited tile parameters
                 */
                typedef Util::TAKErr (* C3DTTilesetVisitor)(void *opaque, const C3DTTileset *tileset, const C3DTTile *tile);

                /**
                 * SAX style parser for Cesium 3D Tiles tileset.json. Each individual tile is visited depth-first. Return TE_Done
                 * from visitor to stop.
                 *
                 * @param input byte stream for tileset.json
                 * @param opaque user defined pointer passed to visitor
                 * @param visitor callback function pointer called for each tile
                 */
                ENGINE_API Util::TAKErr C3DTTileset_parse(Util::DataInput2 *input, void *opaque, C3DTTilesetVisitor visitor) NOTHROWS;

                ENGINE_API Util::TAKErr C3DTTileset_isSupported(bool *result, const char *URI) NOTHROWS;

                ENGINE_API Util::TAKErr C3DTTileset_open(Util::DataInput2Ptr &result, Port::String *baseURI, bool *isStreaming, const char *URI) NOTHROWS;

                ENGINE_API Util::TAKErr C3DTTileset_accumulate(Math::Matrix2* result, const C3DTTile* tile) NOTHROWS;

                ENGINE_API Util::TAKErr C3DTTileset_approximateTileBounds(Feature::Envelope2 *aabb, const C3DTTile *tile) NOTHROWS;
            }
        }
    }
}

#endif

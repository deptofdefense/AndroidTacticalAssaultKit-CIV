#ifndef TAK_ENGINE_FORMATS_CESIUM3DTILES_C3DTTILESET_H_INCLUDED
#define TAK_ENGINE_FORMATS_CESIUM3DTILES_C3DTTILESET_H_INCLUDED

#include "util/Error.h"
#include "port/String.h"
#include "util/DataInput2.h"
#include "math/Point2.h"
#include "math/Matrix2.h"
#include "math/AABB.h"

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

                struct C3DTAsset {
                    Port::String version;
                    Port::String tilesetVersion;
                };

                struct C3DTBox {
                    TAK::Engine::Math::Point2<double> center;
                    TAK::Engine::Math::Point2<double> xDirHalfLen;
                    TAK::Engine::Math::Point2<double> yDirHalfLen;
                    TAK::Engine::Math::Point2<double> zDirHalfLen;
                };

                struct C3DTRegion {
                    double west;
                    double south;
                    double east;
                    double north;
                    double minimumHeight;
                    double maximumHeight;
                };

                /**
                 * Auxiliary data for faster culling math
                 */
                struct C3DTRegionAux {
                    TAK::Engine::Math::Point2<double> swPos;
                    TAK::Engine::Math::Point2<double> nePos;
                    TAK::Engine::Math::Point2<double> southNorm;
                    TAK::Engine::Math::Point2<double> westNorm;
                    TAK::Engine::Math::Point2<double> northNorm;
                    TAK::Engine::Math::Point2<double> eastNorm;
                    C3DTBox boundingBox;
                };

                Util::TAKErr C3DTRegion_calcAux(C3DTRegionAux* aux, const C3DTRegion& reg) NOTHROWS;

                struct C3DTSphere {
                    TAK::Engine::Math::Point2<double> center;
                    double radius;
                };

                struct C3DTVolume {

                    C3DTVolume()
                    : type(Undefined) {}

                    enum Type {
                        Undefined,
                        Box,
                        Region,
                        Sphere,
                        RegionAux
                    };

                    union VolumeObject {
                        VolumeObject() {}
                        ~VolumeObject() {}
                        C3DTBox box;
                        C3DTRegion region;
                        C3DTSphere sphere;
                        struct {
                            C3DTRegion region;
                            C3DTRegionAux aux;
                        } region_aux;
                    } object;
                    Type type;
                };

                Util::TAKErr C3DTVolume_toRegionAABB(TAK::Engine::Feature::Envelope2* aabb, const C3DTVolume* volume) NOTHROWS;
                Util::TAKErr C3DTVolume_distanceSquaredToPosition(double* result, const C3DTVolume& vol, const TAK::Engine::Math::Point2<double>& pos) NOTHROWS;
                Util::TAKErr C3DTVolume_transform(C3DTVolume* result, const C3DTVolume& volume, const Math::Matrix2& transform) NOTHROWS;

                enum class C3DTRefine {
                    Undefined,
                    Add,
                    Replace
                };

                struct C3DTContent {
                    C3DTContent() {}
                    ~C3DTContent() {}
                    C3DTVolume boundingVolume;
                    Port::String uri;
                };

                struct C3DTTile {
                    
                    C3DTTile();

                    const C3DTTile *parent;
                    C3DTVolume boundingVolume;
                    C3DTVolume viewerRequestVolume;
                    double geometricError;
                    C3DTRefine refine;
                    C3DTContent content;
                    Math::Matrix2 transform;
                    size_t childCount;
                    bool hasTransform;
                };

                struct C3DTExtras {
                    C3DTExtras() NOTHROWS;
                    ENGINE_API Util::TAKErr getString(Port::String *value, const char *name) const NOTHROWS;

                    struct Impl;
                    Impl *impl;
                };

                struct C3DTTileset {

                    C3DTTileset();

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

                enum C3DTFileType {
                    C3DTFileType_TilesetJSON,
                    C3DTFileType_B3DM,
                    // TODO-- i3dm, pnt
                };

                Util::TAKErr C3DT_probeSupport(
                    C3DTFileType *type, 
                    Port::String *fileURI,
                    Port::String *tilesetURI,
                    Port::String *baseURI,
                    bool *isStreaming,
                    const char* URI) NOTHROWS;


                Util::TAKErr C3DTTileset_isSupported(bool *result, const char *URI) NOTHROWS;

                Util::TAKErr C3DTTileset_open(Util::DataInput2Ptr &result, Port::String *baseURI, bool *isStreaming, const char *URI) NOTHROWS;

                Util::TAKErr C3DTTileset_accumulate(Math::Matrix2* result, const C3DTTile* tile) NOTHROWS;

                Util::TAKErr C3DTTileset_approximateTileBounds(Feature::Envelope2 *aabb, const C3DTTile *tile, bool accum = true) NOTHROWS;

            }
        }
    }
}

#endif

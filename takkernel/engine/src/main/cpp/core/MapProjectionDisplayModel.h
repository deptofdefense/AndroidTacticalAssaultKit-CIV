#ifndef TAK_ENGINE_CORE_MAPPROJECTIONDISPLAYMODEL_H_INCLUDED
#define TAK_ENGINE_CORE_MAPPROJECTIONDISPLAYMODEL_H_INCLUDED

#include "math/GeometryModel2.h"
#include "port/Platform.h"
#include "util/Error.h"

namespace TAK
{
    namespace Engine
    {
        namespace Core
        {
            class ENGINE_API MapProjectionDisplayModel
            {
            public:
                MapProjectionDisplayModel(const int srid,
                                          Math::GeometryModel2Ptr &&earth,
                                          const double projectionXToNominalMeters,
                                          const double projectionYToNominalMeters,
                                          const double projectionZToNominalMeters,
                                          const bool zIsHeight) NOTHROWS;
            public :
                /**
                * A representation of the earth, in projection units.
                */
                const Math::GeometryModel2Ptr earth;

                /**
                * The Spatial Reference ID of the projection.
                */
                const int srid;

                /**
                * If <code>true</code>, the z component of the projected coordinate space
                * corresponds to elevation/height.
                */
                const bool zIsHeight;

                const double projectionXToNominalMeters;
                const double projectionYToNominalMeters;
                const double projectionZToNominalMeters;
            };

            typedef std::unique_ptr<MapProjectionDisplayModel, void(*)(const MapProjectionDisplayModel *)> MapProjectionDisplayModelPtr;

			ENGINE_API Util::TAKErr MapProjectionDisplayModel_registerModel(MapProjectionDisplayModelPtr &&model) NOTHROWS;
			ENGINE_API Util::TAKErr MapProjectionDisplayModel_registerModel(const std::shared_ptr<MapProjectionDisplayModel> &model) NOTHROWS;
			ENGINE_API Util::TAKErr MapProjectionDisplayModel_unregisterModel(MapProjectionDisplayModel &model) NOTHROWS;
			ENGINE_API Util::TAKErr MapProjectionDisplayModel_getModel(std::shared_ptr<MapProjectionDisplayModel> &value, const int srid) NOTHROWS;
            /**
            * Creates a default model for planar coordinate systems with the horizontal
            * coordinate system specified in degrees Latitude and Longitude and the
            * vertical coordinate system expressed as meters HAE.
            *
            * @param srid  The spatial reference ID of the projection
            *
            * @return  The model
            */
			ENGINE_API Util::TAKErr MapProjectionDisplayModel_createDefaultLLAPlanarModel(MapProjectionDisplayModelPtr &value, const int srid) NOTHROWS;
            /**
            *Creates a default model for planar coordinate systems with the horizontal
            * coordinate system specified in meters Easting and Northing and the
            * vertical coordinate system expressed as meters HAE.
            *
            * @param srid  The spatial reference ID of the projection
            *
            * @return  The model
            */
			ENGINE_API Util::TAKErr MapProjectionDisplayModel_createDefaultENAPlanarModel(MapProjectionDisplayModelPtr &value, const int srid) NOTHROWS;

        }
    }
}

#endif

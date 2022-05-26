#ifndef TAK_ENGINE_FEATURE_FEATURE2_H_INCLUDED
#define TAK_ENGINE_FEATURE_FEATURE2_H_INCLUDED

#include <cstdint>
#include <memory>

#include "feature/AltitudeMode.h"
//#include "feature/Geometry.h"
//#include "feature/Style.h"
#include "port/Platform.h"
#include "port/String.h"
//#include "util/AttributeSet.h"
#include "util/Error.h"

namespace atakmap {
    namespace feature {
        class ENGINE_API Geometry;
        class ENGINE_API Style;
    }
    namespace util {
        class ENGINE_API AttributeSet;
    }
}

namespace TAK {
    namespace Engine {
        namespace Feature {
            class FeatureDefinition2;

            typedef std::unique_ptr<atakmap::feature::Geometry, void(*)(const atakmap::feature::Geometry *)> GeometryPtr;
            typedef std::unique_ptr<const atakmap::feature::Geometry, void(*)(const atakmap::feature::Geometry *)> GeometryPtr_const;

            typedef std::unique_ptr<atakmap::feature::Style, void(*)(const atakmap::feature::Style *)> StylePtr;
            typedef std::unique_ptr<const atakmap::feature::Style, void(*)(const atakmap::feature::Style *)> StylePtr_const;

            typedef std::unique_ptr<atakmap::util::AttributeSet, void(*)(const atakmap::util::AttributeSet *)> AttributeSetPtr;
            typedef std::unique_ptr<const atakmap::util::AttributeSet, void(*)(const atakmap::util::AttributeSet *)> AttributeSetPtr_const;

            /**
            * A map feature composed of geometry, style and attributes (metadata).
            *
            * @author Developer
            */
            class ENGINE_API Feature2
            {
            public :
                Feature2(const Feature2 &other) NOTHROWS;
                Feature2(const int64_t fid, const int64_t fsid, const char *name, const atakmap::feature::Geometry &geom,
                         const TAK::Engine::Feature::AltitudeMode altitudeMode, double extrude, const atakmap::feature::Style &style,
                         const atakmap::util::AttributeSet &attributes, const int64_t version) NOTHROWS;
                Feature2(const int64_t fid, const int64_t fsid, const char *name, GeometryPtr &&geom,
                         const TAK::Engine::Feature::AltitudeMode altitudeMode, double extrude, StylePtr &&style,
                         AttributeSetPtr &&attributes, const int64_t version) NOTHROWS;
                Feature2(const int64_t fid, const int64_t fsid, const char *name, GeometryPtr_const &&geom,
                         const TAK::Engine::Feature::AltitudeMode altitudeMode, double extrude, StylePtr_const &&style,
                         AttributeSetPtr_const &&attributes, const int64_t version) NOTHROWS;
                Feature2(const int64_t fid, const int64_t fsid, const char *name, GeometryPtr_const &&geom,
                         const TAK::Engine::Feature::AltitudeMode altitudeMode, double extrude, StylePtr_const &&style,
                         AttributeSetPtr_const &&attributes, const int64_t timestamp, const int64_t version) NOTHROWS;
            public :
                ~Feature2() NOTHROWS;

                /**
                * Returns the ID of the parent feature set.
                *
                * @return  The ID of the parent feature set.
                */
            public :
                int64_t getFeatureSetId() const NOTHROWS;

                /**
                * Returns the ID of the feature.
                *
                * @return  The version of the feature.
                */
                int64_t getId() const NOTHROWS;

                /**
                * Returns the version of the feature.
                *
                * @return  The version of the feature.
                */
                int64_t getVersion() const NOTHROWS;
                /**
                * Returns the name of the feature.
                *
                * @return  The name of the feature.
                */
                const char *getName() const NOTHROWS;
                /**
                * The geometry of the feature.
                *
                * @return  The geometry of the feature.
                */
                const atakmap::feature::Geometry *getGeometry() const NOTHROWS;
                /**
                 * The altitude mode of the feature.
                 *
                 * @return  The altitude mode of the feature.
                 */
                const TAK::Engine::Feature::AltitudeMode getAltitudeMode() const NOTHROWS;
                /**
                 * Returns the extrusion value. If 0.0, no extrusion occurs. If less than one, the
                 * geometry is extruded down to the terrain surface. If greater than one, the value
                 * is interpreted as the height of the geometry, in meters, and the geometry is
                 * extruded away from the surface of the earth by the specified number of meters.
                 *
                 * @return  The extrusion of the feature.
                 */
                const double getExtrude() const NOTHROWS;
                /**
                * Returns the style of the feature.
                *
                * @return  The style of the feature.
                */
                const atakmap::feature::Style *getStyle() const NOTHROWS;
                /**
                * Returns the attributes of the feature.
                *
                * @return  The attributes of the feature.
                */
                const atakmap::util::AttributeSet *getAttributes() const NOTHROWS;
                /**
                 * Returns the timestamp associated with the feature.
                 *
                 * @return  The timestamp associated with the feature.
                 */
                int64_t getTimestamp() const NOTHROWS;
            private :
                int64_t id;
                int64_t setId;
                int64_t version;
                TAK::Engine::Port::String name;
                GeometryPtr_const geometry;
                TAK::Engine::Feature::AltitudeMode altitudeMode;
                double extrude;
                StylePtr_const style;
                AttributeSetPtr_const attributes;
                int64_t timestamp;
            }; // Feature2

            typedef std::unique_ptr<Feature2, void(*)(const Feature2 *)> FeaturePtr;
            typedef std::unique_ptr<const Feature2, void(*)(const Feature2 *)> FeaturePtr_const;

            ENGINE_API Util::TAKErr Feature_create(FeaturePtr_const &feature, FeatureDefinition2 &def) NOTHROWS;
            ENGINE_API Util::TAKErr Feature_create(FeaturePtr_const &feature, const int64_t fid, const int64_t fsid, FeatureDefinition2 &def, const int64_t vesion) NOTHROWS;
            ENGINE_API bool Feature_isSame(const Feature2 &a, const Feature2 &b) NOTHROWS;
        }
    }
}

#endif

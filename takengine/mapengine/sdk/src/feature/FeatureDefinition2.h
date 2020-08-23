#ifndef TAK_ENGINE_FEATURE_FEATUREDEFINITION2_H_INCLUDED
#define TAK_ENGINE_FEATURE_FEATUREDEFINITION2_H_INCLUDED

#include <memory>

#include <util/NonCopyable.h>

#include "feature/AltitudeMode.h"
#include "port/Platform.h"
#include "util/AttributeSet.h"
#include "util/Error.h"
#include "util/NonCopyable.h"

namespace TAK {
    namespace Engine {
        namespace Feature {
            class Feature2;

            /**
            * The definition of a feature. Feature properties may be recorded as raw,
            * unprocessed data of several well-defined types. Utilization of
            * unprocessed data may yield significant a performance advantage depending
            * on the intended storage.
            *
            * @author Developer
            */
            class FeatureDefinition2 : TAK::Engine::Util::NonCopyable
            {
            public :
                enum GeometryEncoding
                {
                    /** WKT coding as C-string (const char pointer) */
                    GeomWkt,
                    /** WKB coding as a ByteBuffer pointer */
                    GeomWkb,
                    /** SpatiaLite blob coding as a ByteBuffer pointer */
                    GeomBlob,
                    /** A const Geometry pointer */
                    GeomGeometry,
                };
                enum StyleEncoding
                {
                    /** OGR coded style, as C-string (const char pointer) */
                    StyleOgr,
                    /** TAK const Style pointer */
                    StyleStyle,
                };
            public :
                union RawData
                {
                    const char *text;
                    struct
                    {
                        const uint8_t *value;
                        std::size_t len;
                    } binary;
                    const void *object;
                };
            public :
                /** access the raw geometry data. */
                virtual Util::TAKErr getRawGeometry(RawData *value) NOTHROWS = 0;
                /** coding of the geometry data. */
                virtual GeometryEncoding getGeomCoding() NOTHROWS = 0;
                /** access the altitude mode of the geometry. */
                virtual AltitudeMode getAltitudeMode() NOTHROWS = 0;
                /** access the extrude value of the geometry. */
                virtual double getExtrude() NOTHROWS = 0;
                /** feature name */
                virtual Util::TAKErr getName(const char **value) NOTHROWS = 0;
                /** coding of the style data. */
                virtual StyleEncoding getStyleCoding() NOTHROWS = 0;
                /** access raw style data. */
                virtual Util::TAKErr getRawStyle(RawData *value) NOTHROWS = 0;
                /** feature attributes. */
                virtual Util::TAKErr getAttributes(const atakmap::util::AttributeSet **value) NOTHROWS = 0;
                /** obtains the associated feature */
                virtual Util::TAKErr get(const Feature2 **feature) NOTHROWS = 0;

            }; // FeatureDefinition

            typedef std::unique_ptr<FeatureDefinition2, void(*)(const FeatureDefinition2 *)> FeatureDefinitionPtr;
        }
    }
}

#endif

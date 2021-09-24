#ifndef TAK_ENGINE_FEATURE_FILTERFEATURECURSOR2_H_INCLUDED
#define TAK_ENGINE_FEATURE_FILTERFEATURECURSOR2_H_INCLUDED

#include "feature/FeatureCursor2.h"
#include "FeatureDataStore2.h"

namespace TAK {
    namespace Engine {
        namespace Feature {
            class ENGINE_API FilterFeatureCursor2 : public FeatureCursor2
            {
            public :
                FilterFeatureCursor2(FeatureCursorPtr&& impl) NOTHROWS;
                ~FilterFeatureCursor2() NOTHROWS;
            public : // FeatureDefinition2
                Util::TAKErr getRawGeometry(RawData *value) NOTHROWS override;
                GeometryEncoding getGeomCoding() NOTHROWS override;
                AltitudeMode getAltitudeMode() NOTHROWS override;
                double getExtrude() NOTHROWS override;
                Util::TAKErr getName(const char **value) NOTHROWS override;
                StyleEncoding getStyleCoding() NOTHROWS override;
                Util::TAKErr getRawStyle(RawData *value) NOTHROWS override;
                Util::TAKErr getAttributes(const atakmap::util::AttributeSet **value) NOTHROWS override;
                Util::TAKErr get(const Feature2 **feature) NOTHROWS override;
            public :
                TAK::Engine::Util::TAKErr getId(int64_t *value) NOTHROWS override;
                TAK::Engine::Util::TAKErr getFeatureSetId(int64_t *value) NOTHROWS override;
                TAK::Engine::Util::TAKErr getVersion(int64_t *value) NOTHROWS override;
            public :
                Util::TAKErr moveToNext() NOTHROWS override;
            protected :
                FeatureCursorPtr impl;
            }; // FeatureCursor
        }
    }
}

#endif

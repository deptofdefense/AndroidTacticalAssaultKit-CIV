#ifndef TAK_ENGINE_FEATURE_MULTIPLEXINGFEATURECURSOR_H_INCLUDED
#define TAK_ENGINE_FEATURE_MULTIPLEXINGFEATURECURSOR_H_INCLUDED

#include <set>

#include "feature/FeatureCursor2.h"
#include "feature/FeatureDataStore2.h"
#include "port/Platform.h"

namespace TAK {
    namespace Engine {
        namespace Feature {
            class ENGINE_API MultiplexingFeatureCursor : public FeatureCursor2
            {
            private :
                typedef std::vector<FeatureDataStore2::FeatureQueryParameters::Order> OrderVector;
                typedef bool(*Comparator)(std::pair<OrderVector::iterator, OrderVector::iterator> &, FeatureCursor2&, FeatureCursor2&);
            public:
                MultiplexingFeatureCursor() NOTHROWS;
                MultiplexingFeatureCursor(Port::Collection<FeatureDataStore2::FeatureQueryParameters::Order> &order) NOTHROWS;
            public :
                Util::TAKErr add(FeatureCursorPtr &&cursor) NOTHROWS;
            public: // FeatureCursor2
                virtual Util::TAKErr getId(int64_t *value) NOTHROWS override;
                virtual Util::TAKErr getFeatureSetId(int64_t *value) NOTHROWS override;
                virtual Util::TAKErr getVersion(int64_t *value) NOTHROWS override;
            public: // FeatureDefinition2
                virtual Util::TAKErr getRawGeometry(FeatureDefinition2::RawData *value) NOTHROWS override;
                virtual FeatureDefinition2::GeometryEncoding getGeomCoding() NOTHROWS override;
                virtual TAK::Engine::Feature::AltitudeMode getAltitudeMode() NOTHROWS override;
                virtual double getExtrude() NOTHROWS override;
                virtual Util::TAKErr getName(const char **value) NOTHROWS override;
                virtual FeatureDefinition2::StyleEncoding getStyleCoding() NOTHROWS override;
                virtual Util::TAKErr getRawStyle(FeatureDefinition2::RawData *value) NOTHROWS override;
                virtual Util::TAKErr getAttributes(const atakmap::util::AttributeSet **value) NOTHROWS override;
                virtual Util::TAKErr get(const Feature2 **feature) NOTHROWS override;
            public: // RowIterator
                virtual Util::TAKErr moveToNext() NOTHROWS override;
            private:
                std::map<FeatureCursor2 *, std::shared_ptr<FeatureCursor2>> cursors;
                std::set<FeatureCursor2 *> invalid;
                std::vector<FeatureCursor2 *> pendingResults;
                FeatureCursor2 *current;

                std::vector<FeatureDataStore2::FeatureQueryParameters::Order> order;
                Comparator comp;
            };
        }
    }
}

#endif

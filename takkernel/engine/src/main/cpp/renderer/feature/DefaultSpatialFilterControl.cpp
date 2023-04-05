#include "renderer/feature/DefaultSpatialFilterControl.h"

#include "feature/GeometryFactory.h"
#include "feature/LegacyAdapters.h"
#include "thread/Lock.h"
#include "util/DataInput2.h"
#include "util/Memory.h"

using namespace TAK::Engine::Renderer::Feature;

using namespace TAK::Engine::Feature;
using namespace TAK::Engine::Port;
using namespace TAK::Engine::Thread;
using namespace TAK::Engine::Util;

DefaultSpatialFilterControl::DefaultSpatialFilterControl(bool *invalidate_token) NOTHROWS :
    mutex_(TEMT_Recursive),
    invalidate_token_(invalidate_token)
{}
TAKErr DefaultSpatialFilterControl::accept(bool* value, FeatureDefinition2& feature) NOTHROWS
{
    TAKErr code(TE_Ok);

    Lock lock(mutex_);
    bool& fits_filter = *value;
    fits_filter = true;

    // early return on no filters
    if (include_filter_ids_.empty() && exclude_filter_ids_.empty()) {
        return TE_Ok;
    }

    
    GeometryPtr_const geom(nullptr, nullptr);
    Geometry2Ptr geom2(nullptr, nullptr);
    FeatureDefinition2::RawData geom_data;
    code = feature.getRawGeometry(&geom_data);
    TE_CHECKRETURN_CODE(code);
    switch (feature.getGeomCoding()) {
    case FeatureDefinition2::GeomBlob :
    {
        TAK::Engine::Util::MemoryInput2 data;
        data.open(geom_data.binary.value, geom_data.binary.len);
        code = GeometryFactory_fromSpatiaLiteBlob(geom2, data);
        break;
    }
    case FeatureDefinition2::GeomWkb :
    {
        TAK::Engine::Util::MemoryInput2 data;
        data.open(geom_data.binary.value, geom_data.binary.len);
        code = GeometryFactory_fromWkb(geom2, data);
        break;
    }
    case FeatureDefinition2::GeomGeometry:
        geom = GeometryPtr_const(static_cast<const atakmap::feature::Geometry*>(geom_data.object), Memory_leaker_const<atakmap::feature::Geometry>);
        code = LegacyAdapters_adapt(geom2, *geom);
        break;
    default :
        return TE_InvalidArg;
    }
    TE_CHECKRETURN_CODE(code);

    return accept(value, *geom2);
}
TAKErr DefaultSpatialFilterControl::accept(bool* value, const atakmap::feature::Geometry& geom) NOTHROWS
{
    TAKErr code(TE_Ok);
    Geometry2Ptr geom2(nullptr, nullptr);
    code = LegacyAdapters_adapt(geom2, geom);
    TE_CHECKRETURN_CODE(code);

    return accept(value, *geom2);
}
TAKErr DefaultSpatialFilterControl::accept(bool *value, const TAK::Engine::Feature::Geometry2 &geom) NOTHROWS
{
    TAKErr code(TE_Ok);

    Lock lock(mutex_);
    bool& fits_filter = *value;
    fits_filter = true;
    // early return on no filters
    if (include_filter_ids_.empty() && exclude_filter_ids_.empty()) {
        return TE_Ok;
    }

    int64_t geom_id;
    code = spatial_calculator_->createGeometry(&geom_id, geom);
    TE_CHECKRETURN_CODE(code);

    class GeomIdDeleter {
    private:
        const int64_t geom_id_;
        const std::shared_ptr<TAK::Engine::Feature::SpatialCalculator2> &sc_;
        bool deleted_;
    public:
        GeomIdDeleter(const int64_t geom_id, const std::shared_ptr<TAK::Engine::Feature::SpatialCalculator2> &sc) NOTHROWS:
            geom_id_(geom_id),
            sc_(sc),
            deleted_(false)
        {}

        TAKErr del() NOTHROWS {
            if (!deleted_) {
                deleted_ = true;
                return sc_->deleteGeometry(geom_id_);
            }
            return TE_Ok;
        }

        ~GeomIdDeleter() NOTHROWS {
            if (!deleted_) {
                TAKErr code(TE_Ok);
                code = sc_->deleteGeometry(geom_id_);
                TE_CHECKRETURN(code);
            }
        }
    };

    GeomIdDeleter geom_id_deleter(geom_id, spatial_calculator_);

    fits_filter = include_filter_ids_.empty();
    for (const int64_t include_filter_id : include_filter_ids_)
    {
        code = spatial_calculator_->contains(&fits_filter, include_filter_id, geom_id);
        TE_CHECKRETURN_CODE(code);
        if (fits_filter) break;

        code = spatial_calculator_->intersects(&fits_filter, include_filter_id, geom_id);
        TE_CHECKRETURN_CODE(code);
        if (fits_filter) break;
    }

    // No need to check exclude filters if we don't fit in the include filters
    if (fits_filter)
    {
        for (const int64_t exclude_filter_id : exclude_filter_ids_) {
            bool in_exclude_filter;
            code = spatial_calculator_->contains(&in_exclude_filter, exclude_filter_id, geom_id);
            TE_CHECKRETURN_CODE(code);
            if (in_exclude_filter) {
                fits_filter = false;
                break;
            }
            code = spatial_calculator_->intersects(&in_exclude_filter, exclude_filter_id, geom_id);
            TE_CHECKRETURN_CODE(code);
            if (in_exclude_filter) {
                fits_filter = false;
                break;
            }
        }
    }

    code = geom_id_deleter.del();

    return code;
}
Envelope2 DefaultSpatialFilterControl::getIncludeMinimumBoundingBox() const NOTHROWS
{
    Lock lock(mutex_);
    return (lock.status == TE_Ok && include_spatial_filter_envelope_) ?
        *include_spatial_filter_envelope_ :
        Envelope2(-180.0, -90.0, 0.0, 180.0, 90.0,0.0);
}
TAKErr DefaultSpatialFilterControl::setSpatialFilters(Collection<std::shared_ptr<SpatialFilter>>* filters) NOTHROWS
{
    TAKErr code(TE_Ok);

    Lock lock(mutex_);
    TE_CHECKRETURN_CODE(lock.status);

    std::vector<std::shared_ptr<SpatialFilter>> spatial_filters;
    if (!filters->empty()) {
        Collection<std::shared_ptr<SpatialFilter>>::IteratorPtr iterator(nullptr, nullptr);
        code = filters->iterator(iterator);
        TE_CHECKRETURN_CODE(code);
        do {
            std::shared_ptr<SpatialFilter> filter;
            code = iterator->get(filter);
            TE_CHECKBREAK_CODE(code);

            spatial_filters.push_back(filter);

            code = iterator->next();
            TE_CHECKBREAK_CODE(code);
        } while (true);

        // XXX - consistent with legacy impl
        code = TE_Ok;
    }

    include_spatial_filter_envelope_.reset();
    spatial_calculator_.reset();
    include_filter_ids_.clear();
    exclude_filter_ids_.clear();

    if (spatial_filters.empty()) return code;

    spatial_calculator_ = std::shared_ptr<SpatialCalculator2>(new SpatialCalculator2());

    for (const auto &filter : spatial_filters) {
        SpatialFilterType filter_type;
        code = filter->getType(filter_type);
        TE_CHECKBREAK_CODE(code);

        Polygon2 *filter_geometry;
        code = filter->getFilterGeometry(&filter_geometry);
        TE_CHECKBREAK_CODE(code);

        if (filter_type == SpatialFilterType::Include) {
            Envelope2 include_envelope;
            code = filter_geometry->getEnvelope(&include_envelope);
            TE_CHECKBREAK_CODE(code);
            if (include_spatial_filter_envelope_.get() == nullptr) {
                include_spatial_filter_envelope_.reset(new Envelope2(include_envelope));
            } else {
                include_spatial_filter_envelope_->minX = std::min(include_envelope.minX, include_spatial_filter_envelope_->minX);
                include_spatial_filter_envelope_->minY = std::min(include_envelope.minY, include_spatial_filter_envelope_->minY);
                include_spatial_filter_envelope_->maxX = std::max(include_envelope.maxX, include_spatial_filter_envelope_->maxX);
                include_spatial_filter_envelope_->maxY = std::max(include_envelope.maxY, include_spatial_filter_envelope_->maxY);
            }

            int64_t include_handle;
            code = spatial_calculator_->createGeometry(&include_handle, *filter_geometry);
            TE_CHECKBREAK_CODE(code);
            include_filter_ids_.push_back(include_handle);
        } else {
            int64_t exclude_handle;
            code = spatial_calculator_->createGeometry(&exclude_handle, *filter_geometry);
            TE_CHECKBREAK_CODE(code);
            exclude_filter_ids_.push_back(exclude_handle);
        }
    }

    if (invalidate_token_)
        *invalidate_token_ = true;
    return code;
}

#include "feature/LegacyAdapters.h"

#include "feature/Feature2.h"
#include "feature/FeatureDataSource2.h"
#include "feature/FeatureDataStore2.h"
#include "feature/GeometryCollection.h"
#include "feature/GeometryCollection2.h"
#include "feature/LineString.h"
#include "feature/LineString2.h"
#include "feature/Point.h"
#include "feature/Point2.h"
#include "feature/Polygon.h"
#include "feature/Polygon2.h"
#include "feature/Style.h"
#include "util/Memory.h"

using namespace TAK::Engine::Feature;
using namespace TAK::Engine::Util;

typedef atakmap::feature::Feature Feature_Legacy;
typedef atakmap::feature::FeatureDataSource FeatureDataSource_Legacy;
typedef atakmap::feature::FeatureDataSource::FeatureDefinition FeatureDefinition_Legacy;
typedef atakmap::feature::FeatureDataSource::Content Content_Legacy;

namespace
{
    class FeatureDataSourceAdapter : public FeatureDataSource2
    {
    public:
        FeatureDataSourceAdapter(const FeatureDataSource_Legacy &impl) NOTHROWS;
    public :
        TAKErr parse(ContentPtr &content, const char *file) NOTHROWS override;
        const char *getName() const NOTHROWS override;
        int parseVersion() const NOTHROWS override;
    private :
        const FeatureDataSource_Legacy &impl;
    };

    class ContentAdapter : public FeatureDataSource2::Content
    {
    public:
        typedef std::unique_ptr<Content_Legacy, void(*)(const Content_Legacy *)> LegacyPtr;
    public :
        ContentAdapter(LegacyPtr &&impl) NOTHROWS;
    public:
        const char *getType() const NOTHROWS override;
        const char *getProvider() const NOTHROWS override;
        TAK::Engine::Util::TAKErr moveToNextFeature() NOTHROWS override;
        TAK::Engine::Util::TAKErr moveToNextFeatureSet() NOTHROWS override;
        TAK::Engine::Util::TAKErr get(FeatureDefinition2 **feature) const NOTHROWS override;
        TAK::Engine::Util::TAKErr getFeatureSetName(TAK::Engine::Port::String &name) const NOTHROWS override;
        TAK::Engine::Util::TAKErr getFeatureSetVisible(bool *visible) const NOTHROWS override;
        TAK::Engine::Util::TAKErr getMinResolution(double *value) const NOTHROWS override;
        TAK::Engine::Util::TAKErr getMaxResolution(double *value) const NOTHROWS override;
		TAK::Engine::Util::TAKErr getVisible(bool *visible) const NOTHROWS override;
    private :
        LegacyPtr impl;
        std::unique_ptr<FeatureDefinition2, void(*)(const FeatureDefinition2 *)> rowData;
    };

    class FeatureDefinitionAdapter : public FeatureDefinition2
    {
    public :
        typedef std::unique_ptr<const FeatureDefinition_Legacy, void(*)(const FeatureDefinition_Legacy *)> LegacyPtr;
    public:
        FeatureDefinitionAdapter() NOTHROWS;
        FeatureDefinitionAdapter(LegacyPtr &&impl) NOTHROWS;
    public :
        void reset(LegacyPtr &&impl) NOTHROWS;
    public:
        TAK::Engine::Util::TAKErr getRawGeometry(RawData *value) NOTHROWS override;
        GeometryEncoding getGeomCoding() NOTHROWS override;
        AltitudeMode getAltitudeMode() NOTHROWS override;
        double getExtrude() NOTHROWS override;
        TAK::Engine::Util::TAKErr getName(const char **value) NOTHROWS override;
        StyleEncoding getStyleCoding() NOTHROWS override;
        TAK::Engine::Util::TAKErr getRawStyle(RawData *value) NOTHROWS override;
        TAK::Engine::Util::TAKErr getAttributes(const atakmap::util::AttributeSet **value) NOTHROWS override;
        TAK::Engine::Util::TAKErr get(const Feature2 **feature) NOTHROWS override;
    private :
        LegacyPtr impl;
        FeaturePtr_const rowData;
    }; // FeatureDefinition

    void blobDeleter(const std::pair<const uint8_t *, const uint8_t *> *blob);

    template<class Iface, class Impl = Iface>
    void deleter_dbg(const Iface *obj)
    {
        Memory_deleter_const<Iface, Impl>(obj);
    }

    // legacy adapts to Geometry2
    TAKErr adaptPoint(Geometry2Ptr &value, const atakmap::feature::Point &legacy) NOTHROWS;
    TAKErr adaptLineString(Geometry2Ptr &value, const atakmap::feature::LineString &legacy) NOTHROWS;
    TAKErr adaptPolygon(Geometry2Ptr &value, const atakmap::feature::Polygon &legacy) NOTHROWS;
    TAKErr adaptCollection(Geometry2Ptr &value, const atakmap::feature::GeometryCollection &legacy) NOTHROWS;

    // Geometry2 adapts to legacy
    TAKErr adaptPoint(atakmap::feature::UniqueGeometryPtr &value, const Point2 &point) NOTHROWS;
    TAKErr adaptLineString(atakmap::feature::UniqueGeometryPtr &value, const LineString2 &linestring) NOTHROWS;
    TAKErr adaptPolygon(atakmap::feature::UniqueGeometryPtr &value, const Polygon2 &polygon) NOTHROWS;
    TAKErr adaptCollection(atakmap::feature::UniqueGeometryPtr &value, const GeometryCollection2 &collection) NOTHROWS;
}

TAKErr TAK::Engine::Feature::LegacyAdapters_create(FeaturePtr_const &value, const Feature_Legacy &legacy) NOTHROWS
{
    value = FeaturePtr_const(new Feature2(legacy.getID(), legacy.getFeatureSetID(), legacy.getName(),
                                          std::move(GeometryPtr_const(legacy.getGeometry().clone(), atakmap::feature::destructGeometry)),
                                          AltitudeMode::TEAM_ClampToGround, 0.0,
                                          std::move(StylePtr_const(legacy.getStyle() ? legacy.getStyle()->clone() : nullptr,
                                                                   atakmap::feature::Style::destructStyle)),
                                          std::move(AttributeSetPtr_const(new atakmap::util::AttributeSet(legacy.getAttributes()),
                                                                          Memory_deleter_const<atakmap::util::AttributeSet>)),
                                          legacy.getVersion()),
                             Memory_deleter_const<Feature2>);
    return TE_Ok;
}

TAKErr TAK::Engine::Feature::LegacyAdapters_adapt(FeatureDefinitionPtr &value, const FeatureDefinition_Legacy &legacy) NOTHROWS
{
    FeatureDefinitionAdapter::LegacyPtr legacyPtr(&legacy, Memory_leaker_const<FeatureDefinition_Legacy>);
    value = FeatureDefinitionPtr(new FeatureDefinitionAdapter(std::move(legacyPtr)), Memory_deleter_const<FeatureDefinition2, FeatureDefinitionAdapter>);
    return TE_Ok;
}

TAKErr TAK::Engine::Feature::LegacyAdapters_adapt(FeatureDataSourcePtr &value, const FeatureDataSource_Legacy &legacy) NOTHROWS
{
    value = FeatureDataSourcePtr(new FeatureDataSourceAdapter(legacy), Memory_deleter_const<FeatureDataSource2, FeatureDataSourceAdapter>);
    return TE_Ok;
}

TAKErr TAK::Engine::Feature::LegacyAdapters_adapt(FeatureDataSource2::ContentPtr &value, Content_Legacy &legacy) NOTHROWS
{
    ContentAdapter::LegacyPtr legacyPtr(&legacy, Memory_leaker_const<Content_Legacy>);
    return LegacyAdapters_adapt(value, std::move(legacyPtr));
}

TAKErr TAK::Engine::Feature::LegacyAdapters_adapt(FeatureDataSource2::ContentPtr &value, ContentAdapter::LegacyPtr &&legacyPtr) NOTHROWS
{
    value = FeatureDataSource2::ContentPtr(new ContentAdapter(std::move(legacyPtr)), Memory_deleter_const<FeatureDataSource2::Content, ContentAdapter>);
    return TE_Ok;
}

TAKErr TAK::Engine::Feature::LegacyAdapters_adapt(Geometry2Ptr &value, const atakmap::feature::Geometry &legacy) NOTHROWS
{
    TAKErr code(TE_Ok);
    switch (legacy.getType()) {
    case atakmap::feature::Geometry::POINT:
        code = adaptPoint(value, static_cast<const atakmap::feature::Point &>(legacy));
        break;
    case atakmap::feature::Geometry::LINESTRING:
        code = adaptLineString(value, static_cast<const atakmap::feature::LineString &>(legacy));
        break;
    case atakmap::feature::Geometry::POLYGON:
        code = adaptPolygon(value, static_cast<const atakmap::feature::Polygon &>(legacy));
        break;
    case atakmap::feature::Geometry::COLLECTION:
        code = adaptCollection(value, static_cast<const atakmap::feature::GeometryCollection &>(legacy));
        break;
    default:
        code = TE_IllegalState;
        break;
    }
    TE_CHECKRETURN_CODE(code);

    return code;
}
TAKErr TAK::Engine::Feature::LegacyAdapters_adapt(Geometry2Ptr_const &value_const, const atakmap::feature::Geometry &legacy) NOTHROWS
{
    TAKErr code(TE_Ok);
    Geometry2Ptr value(nullptr, nullptr);
    code = LegacyAdapters_adapt(value, legacy);
    TE_CHECKRETURN_CODE(code);

    value_const = Geometry2Ptr_const(value.release(), value.get_deleter());
    return code;
}
TAKErr TAK::Engine::Feature::LegacyAdapters_adapt(GeometryPtr &value, const TAK::Engine::Feature::Geometry2 &geom) NOTHROWS
{
    TAKErr code(TE_Ok);
    switch (geom.getClass()) {
    case TEGC_Point:
        code = adaptPoint(value, static_cast<const Point2 &>(geom));
        break;
    case TEGC_LineString:
        code = adaptLineString(value, static_cast<const LineString2 &>(geom));
        break;
    case TEGC_Polygon:
        code = adaptPolygon(value, static_cast<const Polygon2 &>(geom));
        break;
    case TEGC_GeometryCollection:
        code = adaptCollection(value, static_cast<const GeometryCollection2 &>(geom));
        break;
    default:
        code = TE_IllegalState;
        break;
    }
    TE_CHECKRETURN_CODE(code);

    return code;
}
TAKErr TAK::Engine::Feature::LegacyAdapters_adapt(GeometryPtr_const &value_const, const TAK::Engine::Feature::Geometry2 &geom) NOTHROWS
{
    TAKErr code(TE_Ok);
    GeometryPtr value(nullptr, nullptr);
    code = LegacyAdapters_adapt(value, geom);
    TE_CHECKRETURN_CODE(code);

    value_const = GeometryPtr_const(value.release(), value.get_deleter());
    return code;
}

TAKErr TAK::Engine::Feature::LegacyAdapters_toWkb(BlobPtr &retval, const atakmap::feature::Geometry &geom) NOTHROWS
{
    array_ptr<uint8_t> wkb;
    std::size_t wkbLen;

    try {
        std::ostringstream strm;
        geom.toWKB(strm);

        wkbLen = strm.str().length();
        wkb.reset(new uint8_t[wkbLen]);
        memcpy(wkb.get(), strm.str().c_str(), wkbLen);

        uint8_t *end = wkb.get() + wkbLen;
        retval = BlobPtr(new std::pair<const uint8_t *, const uint8_t *>(wkb.release(), end), blobDeleter);

        return TE_Ok;
    } catch (...) {
        return TE_Err;
    }
}

TAKErr TAK::Engine::Feature::LegacyAdapters_toBlob(BlobPtr &retval, const atakmap::feature::Geometry &geom) NOTHROWS
{
    array_ptr<uint8_t> blob;
    std::size_t blobLen;

    try {
        std::ostringstream strm;
        geom.toBlob(strm);

        blobLen = strm.str().length();
        blob.reset(new uint8_t[blobLen]);
        memcpy(blob.get(), strm.str().c_str(), blobLen);

        uint8_t *end = blob.get() + blobLen;
        retval = BlobPtr(new std::pair<const uint8_t *, const uint8_t *>(blob.release(), end), blobDeleter);

        return TE_Ok;
    }
    catch (...) {
        return TE_Err;
    }
}

namespace
{
    FeatureDataSourceAdapter::FeatureDataSourceAdapter(const FeatureDataSource_Legacy &impl_) NOTHROWS :
        impl(impl_)
    {}

    TAKErr FeatureDataSourceAdapter::parse(FeatureDataSource2::ContentPtr &content, const char *file) NOTHROWS
    {
        try {
            ContentAdapter::LegacyPtr contentImpl(impl.parseFile(file), Memory_deleter_const<Content_Legacy>);
            if (!contentImpl.get())
                return TE_Err;
            content = FeatureDataSource2::ContentPtr(new ContentAdapter(std::move(contentImpl)), Memory_deleter_const<FeatureDataSource2::Content, ContentAdapter>);
            return TE_Ok;
        } catch(std::invalid_argument &) {
            return TE_InvalidArg;
        } catch(...) {
            return TE_Err;
        }
    }

    const char *FeatureDataSourceAdapter::getName() const NOTHROWS
    {
        return impl.getName();
    }

    int FeatureDataSourceAdapter::parseVersion() const NOTHROWS
    {
        return impl.parseVersion();
    }


    ContentAdapter::ContentAdapter(ContentAdapter::LegacyPtr &&impl_) NOTHROWS :
        impl(std::move(impl_)),
        rowData(nullptr, nullptr)
    {}

    const char *ContentAdapter::getType() const NOTHROWS
    {
        return impl->getType();
    }

    const char *ContentAdapter::getProvider() const NOTHROWS
    {
        return impl->getProvider();
    }

    TAKErr ContentAdapter::moveToNextFeature() NOTHROWS
    {
        rowData.reset();
        if (!impl->moveToNextFeature())
            return TE_Done;

        //FeatureDefinitionAdapter::LegacyPtr defnImpl((*impl).get(), Memory_deleter_const<FeatureDefinition_Legacy>);
        FeatureDefinitionAdapter::LegacyPtr defnImpl((*impl).get(), deleter_dbg<FeatureDefinition_Legacy>);
        if (!defnImpl.get())
            return TE_IllegalState;

        rowData = std::unique_ptr<FeatureDefinition2, void(*)(const FeatureDefinition2 *)>(new FeatureDefinitionAdapter(std::move(defnImpl)), deleter_dbg<FeatureDefinition2, FeatureDefinitionAdapter>);
        return TE_Ok;
    }

    TAKErr ContentAdapter::moveToNextFeatureSet() NOTHROWS
    {
        rowData.reset();
        return impl->moveToNextFeatureSet() ? TE_Ok : TE_Done;
    }

    TAKErr ContentAdapter::get(FeatureDefinition2 **feature) const NOTHROWS
    {
        if (!rowData.get())
            return TE_IllegalState;
        *feature = rowData.get();
        return TE_Ok;
    }

    TAKErr ContentAdapter::getFeatureSetName(TAK::Engine::Port::String &name) const NOTHROWS
    {
        name = impl->getFeatureSetName();
        return TE_Ok;
    }

    TAKErr ContentAdapter::getFeatureSetVisible(bool *visible) const NOTHROWS
	{
		*visible = true;
		return TE_Ok;
	}

    TAKErr ContentAdapter::getMinResolution(double *value) const NOTHROWS
    {
        *value = impl->getMinResolution();
        return TE_Ok;
    }

    TAKErr ContentAdapter::getMaxResolution(double *value) const NOTHROWS
    {
        *value = impl->getMaxResolution();
        return TE_Ok;
    }

	TAKErr ContentAdapter::getVisible(bool *visible) const NOTHROWS
	{
		*visible = true;
		return TE_Ok;
	}

    FeatureDefinitionAdapter::FeatureDefinitionAdapter() NOTHROWS :
        impl(nullptr, nullptr),
        rowData(nullptr, nullptr)
    {}

    FeatureDefinitionAdapter::FeatureDefinitionAdapter(FeatureDefinitionAdapter::LegacyPtr &&impl_) NOTHROWS :
        impl(std::move(impl_)),
        rowData(nullptr, nullptr)
    {}

    void FeatureDefinitionAdapter::reset(FeatureDefinitionAdapter::LegacyPtr &&impl_) NOTHROWS
    {
        impl = std::move(impl_);
    }

    TAKErr FeatureDefinitionAdapter::getRawGeometry(FeatureDefinition2::RawData *value) NOTHROWS
    {
        switch (impl->getEncoding())
        {
        case FeatureDefinition_Legacy::BLOB:
        {
            value->binary.value = impl->getGeometryByteBuffer().first;
            value->binary.len = (impl->getGeometryByteBuffer().second-impl->getGeometryByteBuffer().first);
            break;
        }
        case FeatureDefinition_Legacy::WKB:
        {
            value->binary.value = impl->getGeometryByteBuffer().first;
            value->binary.len = (impl->getGeometryByteBuffer().second - impl->getGeometryByteBuffer().first);
            break;
        }
        case FeatureDefinition_Legacy::WKT:
        {
            value->text = (const char *)impl->getRawGeometry();
            break;
        }
        case FeatureDefinition_Legacy::GEOMETRY:
        {
            value->object = impl->getRawGeometry();
            break;
        }
        default:
        {
            return TE_IllegalState;
        }
        }

        return TE_Ok;
    }

    FeatureDefinition2::GeometryEncoding FeatureDefinitionAdapter::getGeomCoding() NOTHROWS
    {
        switch (impl->getEncoding())
        {
        case FeatureDefinition_Legacy::BLOB :
            return FeatureDefinition2::GeomBlob;
        case FeatureDefinition_Legacy::GEOMETRY :
            return FeatureDefinition2::GeomGeometry;
        case FeatureDefinition_Legacy::WKB :
            return FeatureDefinition2::GeomWkb;
        case FeatureDefinition_Legacy::WKT :
            return FeatureDefinition2::GeomWkt;
        default :
            return FeatureDefinition2::GeomGeometry;
        }
    }

    AltitudeMode FeatureDefinitionAdapter::getAltitudeMode() NOTHROWS 
    {
        int altMode = impl->getAltitudeMode();
        switch (altMode)
        {
            case 0:
                return TEAM_ClampToGround;
            case 1:
                return TEAM_Relative;
            case 2:
                return TEAM_Absolute;
            default:
                return TEAM_ClampToGround;
        }
    }

    double FeatureDefinitionAdapter::getExtrude() NOTHROWS 
    {
        return impl->getExtrude();
    }

    TAKErr FeatureDefinitionAdapter::getName(const char **value) NOTHROWS
    {
        *value = impl->getName();
        return TE_Ok;
    }

    FeatureDefinition2::StyleEncoding FeatureDefinitionAdapter::getStyleCoding() NOTHROWS
    {
        switch (impl->getStyling())
        {
        case FeatureDefinition_Legacy::OGR :
            return FeatureDefinition2::StyleOgr;
        case FeatureDefinition_Legacy::STYLE :
            return FeatureDefinition2::StyleStyle;
        default :
            return FeatureDefinition2::StyleStyle;
        }
    }

    TAKErr FeatureDefinitionAdapter::getRawStyle(RawData *value) NOTHROWS
    {
        switch (impl->getStyling())
        {
        case FeatureDefinition_Legacy::OGR:
        {
            value->text = (const char *)impl->getRawStyle();
            break;
        }
        case FeatureDefinition_Legacy::STYLE:
        {
            value->object = impl->getRawStyle();
            break;
        }
        default:
            return TE_IllegalState;
        }

        return TE_Ok;
    }

    TAKErr FeatureDefinitionAdapter::getAttributes(const atakmap::util::AttributeSet **value) NOTHROWS
    {
        *value = &impl->getAttributes();
        return TE_Ok;
    }

    TAKErr FeatureDefinitionAdapter::get(const Feature2 **feature) NOTHROWS
    {
        do {
            if (rowData.get()) {
                *feature = rowData.get();
                return TE_Ok;
            }

            rowData.reset();
            TAKErr code = Feature_create(rowData, *this);
            TE_CHECKRETURN_CODE(code);

            continue;
        } while (true);
        return TE_IllegalState;
    }

    void blobDeleter(const std::pair<const uint8_t *, const uint8_t *> *blob)
    {
        if (blob->first)
            delete[] blob->first;
        delete blob;
    }

    TAKErr adaptPoint(Geometry2Ptr &value, const atakmap::feature::Point &legacy) NOTHROWS
    {
        TAKErr code(TE_Ok);
        std::unique_ptr<Point2> retval(new Point2(legacy.x, legacy.y));
        try {
            if (legacy.getDimension() == atakmap::feature::Geometry::_3D) {
                code = retval->setDimension(3u);
                TE_CHECKRETURN_CODE(code);
                retval->z = legacy.z;
            }
        } catch (...) {
            return TE_Err;
        }

        value = Geometry2Ptr(retval.release(), Memory_deleter_const<Geometry2>);
        return code;
    }
    TAKErr adaptLineString(Geometry2Ptr &value, const atakmap::feature::LineString &legacy) NOTHROWS
    {
        TAKErr code(TE_Ok);
        std::unique_ptr<LineString2> retval(new LineString2());
        try {
            switch (legacy.getDimension())
            {
            case atakmap::feature::Geometry::_2D :
                code = retval->setDimension(2u);
                for (std::size_t i = 0u; i < legacy.getPointCount(); i++) {
                    code = retval->addPoint(legacy.getX(i), legacy.getY(i));
                    TE_CHECKBREAK_CODE(code);
                }
                break;
            case atakmap::feature::Geometry::_3D :
                code = retval->setDimension(3u);
                for (std::size_t i = 0u; i < legacy.getPointCount(); i++) {
                    code = retval->addPoint(legacy.getX(i), legacy.getY(i), legacy.getZ(i));
                    TE_CHECKBREAK_CODE(code);
                }
                break;
            default :
                code = TE_IllegalState;
                break;
            }
            TE_CHECKRETURN_CODE(code);


        } catch (...) {
            return TE_Err;
        }

        value = Geometry2Ptr(retval.release(), Memory_deleter_const<Geometry2>);
        return code;
    }
    TAKErr adaptPolygon(Geometry2Ptr &value, const atakmap::feature::Polygon &legacy) NOTHROWS
    {
        TAKErr code(TE_Ok);
        Geometry2Ptr exteriorRing(nullptr, nullptr);
        try {
            code = adaptLineString(exteriorRing, legacy.getExteriorRing());
            TE_CHECKRETURN_CODE(code);
        } catch (...) {
            return TE_Err;
        }

        std::unique_ptr<Polygon2> retval(new Polygon2(static_cast<LineString2 &>(*exteriorRing)));

        try {
            std::pair<std::vector<atakmap::feature::LineString>::const_iterator, std::vector<atakmap::feature::LineString>::const_iterator> extRings;
            extRings = legacy.getInteriorRings();

            std::vector<atakmap::feature::LineString>::const_iterator it;
            for (it = extRings.first; it != extRings.second; it++) {
                Geometry2Ptr ring(nullptr, nullptr);
                code = adaptLineString(ring, *it);
                TE_CHECKBREAK_CODE(code);
                code = retval->addInteriorRing(static_cast<LineString2 &>(*ring));
                TE_CHECKBREAK_CODE(code);
            }
            TE_CHECKRETURN_CODE(code);
        } catch (...) {
            return TE_Err;
        }

        value = Geometry2Ptr(retval.release(), Memory_deleter_const<Geometry2>);
        return code;
    }
    TAKErr adaptCollection(Geometry2Ptr &value, const atakmap::feature::GeometryCollection &legacy) NOTHROWS
    {
        TAKErr code(TE_Ok);

        std::unique_ptr<GeometryCollection2> retval(new GeometryCollection2());

        try {
            switch (legacy.getDimension())
            {
            case atakmap::feature::Geometry::_2D:
                code = retval->setDimension(2u);
                break;
            case atakmap::feature::Geometry::_3D:
                code = retval->setDimension(3u);
                break;
            default:
                code = TE_IllegalState;
                break;
            }

            std::pair<std::vector<atakmap::feature::Geometry *>::const_iterator, std::vector<atakmap::feature::Geometry *>::const_iterator> children;
            children = legacy.contents();

            std::vector<atakmap::feature::Geometry *>::const_iterator it;
            for (it = children.first; it != children.second; it++) {
                Geometry2Ptr child(nullptr, nullptr);
                code = LegacyAdapters_adapt(child, *(*it));
                TE_CHECKBREAK_CODE(code);
                code = retval->addGeometry(std::move(child));
                TE_CHECKBREAK_CODE(code);
            }
            TE_CHECKRETURN_CODE(code);
        } catch (...) {
            return TE_Err;
        }

        value = Geometry2Ptr(retval.release(), Memory_deleter_const<Geometry2>);
        return code;
    }

    TAKErr adaptPoint(atakmap::feature::UniqueGeometryPtr &value, const Point2 &point) NOTHROWS
    {
        std::unique_ptr<atakmap::feature::Point> retval;
        try {
            retval.reset(new atakmap::feature::Point(point.x, point.y));
            if (point.getDimension() > 2u) {
                retval->setDimension(atakmap::feature::Geometry::_3D);
                retval->z = point.z;
            }
        } catch (...) {
            return TE_Err;
        }
        value = atakmap::feature::UniqueGeometryPtr(retval.release(), atakmap::feature::destructGeometry);
        return TE_Ok;
    }
    TAKErr adaptLineString(atakmap::feature::UniqueGeometryPtr &value, const LineString2 &linestring) NOTHROWS
    {
        TAKErr code(TE_Ok);
        std::unique_ptr<atakmap::feature::LineString> retval;

        atakmap::feature::Geometry::Dimension dimension;
        switch (linestring.getDimension()) {
        case 2u :
            dimension = atakmap::feature::Geometry::_2D;
            break;
        case 3u:
            dimension = atakmap::feature::Geometry::_3D;
            break;
        default :
            return TE_IllegalState;
        }

        try {
            retval.reset(new atakmap::feature::LineString(dimension));
        } catch (...) {
            return TE_Err;
        }

        for (std::size_t i = 0u; i < linestring.getNumPoints(); i++) {
            Point2 pt(NAN, NAN, NAN);
            code = linestring.get(&pt, i);
            TE_CHECKBREAK_CODE(code);

            atakmap::feature::UniqueGeometryPtr legacyPt(nullptr, nullptr);
            code = adaptPoint(legacyPt, pt);
            TE_CHECKBREAK_CODE(code);
            try {
                retval->addPoint(static_cast<atakmap::feature::Point &>(*legacyPt));
            } catch (...) {
                return TE_Err;
            }
        }
        TE_CHECKRETURN_CODE(code);

        value = atakmap::feature::UniqueGeometryPtr(retval.release(), atakmap::feature::destructGeometry);
        return code;
    }
    TAKErr adaptPolygon(atakmap::feature::UniqueGeometryPtr &value, const Polygon2 &polygon) NOTHROWS
    {
        TAKErr code(TE_Ok);
        std::unique_ptr<atakmap::feature::Polygon> retval;

        atakmap::feature::Geometry::Dimension dimension;
        switch (polygon.getDimension()) {
        case 2u :
            dimension = atakmap::feature::Geometry::_2D;
            break;
        case 3u:
            dimension = atakmap::feature::Geometry::_3D;
            break;
        default :
            return TE_IllegalState;
        }

        try {
            retval.reset(new atakmap::feature::Polygon(dimension));
        } catch (...) {
            return TE_Err;
        }

        std::shared_ptr<LineString2> ring;
        atakmap::feature::UniqueGeometryPtr legacyRing(nullptr, nullptr);

        code = polygon.getExteriorRing(ring);
        TE_CHECKRETURN_CODE(code);
        code = adaptLineString(legacyRing, *ring);
        TE_CHECKRETURN_CODE(code);

        ring.reset();
        try {
            retval->addRing(static_cast<atakmap::feature::LineString &>(*legacyRing));
            legacyRing.reset();
        } catch (...) {
            return TE_Err;
        }

        for (std::size_t i = 0u; i < polygon.getNumInteriorRings(); i++) {
            code = polygon.getInteriorRing(ring, i);
            TE_CHECKBREAK_CODE(code);
            code = adaptLineString(legacyRing, *ring);
            TE_CHECKBREAK_CODE(code);

            ring.reset();
            try {
                retval->addRing(static_cast<atakmap::feature::LineString &>(*legacyRing));
                legacyRing.reset();
            } catch (...) {
                return TE_Err;
            }
        }
        TE_CHECKRETURN_CODE(code);

        value = atakmap::feature::UniqueGeometryPtr(retval.release(), atakmap::feature::destructGeometry);
        return code;
    }
    TAKErr adaptCollection(atakmap::feature::UniqueGeometryPtr &value, const GeometryCollection2 &collection) NOTHROWS
    {
        TAKErr code(TE_Ok);
        std::unique_ptr<atakmap::feature::GeometryCollection> retval;

        atakmap::feature::Geometry::Dimension dimension;
        switch (collection.getDimension()) {
        case 2u :
            dimension = atakmap::feature::Geometry::_2D;
            break;
        case 3u:
            dimension = atakmap::feature::Geometry::_3D;
            break;
        default :
            return TE_IllegalState;
        }

        try {
            retval.reset(new atakmap::feature::GeometryCollection(dimension));
        } catch (...) {
            return TE_Err;
        }

        for (std::size_t i = 0u; i < collection.getNumGeometries(); i++) {
            std::shared_ptr<Geometry2> child;
            atakmap::feature::UniqueGeometryPtr legacyChild(nullptr, nullptr);

            code = collection.getGeometry(child, i);
            TE_CHECKBREAK_CODE(code);
            code = LegacyAdapters_adapt(legacyChild, *child);
            TE_CHECKBREAK_CODE(code);

            try {
                retval->add(*legacyChild);
            } catch (...) {
                return TE_Err;
            }
        }
        TE_CHECKRETURN_CODE(code);

        value = atakmap::feature::UniqueGeometryPtr(retval.release(), atakmap::feature::destructGeometry);
        return code;
    }
}

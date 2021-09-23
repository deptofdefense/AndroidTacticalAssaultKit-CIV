#include "feature/Feature2.h"

#include "feature/FeatureDataStore2.h"
#include "feature/FeatureDefinition2.h"
#include "feature/ParseGeometry.h"
#include "feature/Style.h"

using namespace TAK::Engine::Feature;

using namespace TAK::Engine::Util;

namespace
{
    template<class T>
    void deleter(T *obj) {
        delete obj;
    }
}

Feature2::Feature2(const Feature2 &other) NOTHROWS :
    id(other.id),
    setId(other.setId),
    version(other.version),
    name(other.name),
    geometry(other.geometry.get() ? other.geometry.get()->clone() : nullptr, atakmap::feature::destructGeometry),
    altitudeMode(other.altitudeMode),
    extrude(other.extrude),
    style(other.style.get() ? other.style.get()->clone() : nullptr, atakmap::feature::Style::destructStyle),
    attributes(other.attributes.get() ? new atakmap::util::AttributeSet(*other.attributes) : nullptr, deleter<const atakmap::util::AttributeSet>),
    timestamp(other.timestamp)
{}

Feature2::Feature2(const int64_t fid_, const int64_t fsid_, const char *name_, const atakmap::feature::Geometry &geom_,
                   const TAK::Engine::Feature::AltitudeMode altitudeMode_, const double extrude_, const atakmap::feature::Style &style_,
                   const atakmap::util::AttributeSet &attributes_, const int64_t version_) NOTHROWS
    :
    id(fid_),
    setId(fsid_),
    version(version_),
    name(name_),
    geometry(geom_.clone(), atakmap::feature::destructGeometry),
    altitudeMode(altitudeMode_),
    extrude(extrude_),
    style(style_.clone(), atakmap::feature::Style::destructStyle),
    attributes(new atakmap::util::AttributeSet(attributes_), deleter<const atakmap::util::AttributeSet>),
    timestamp(0)
{}

Feature2::Feature2(const int64_t fid_, const int64_t fsid_, const char *name_, GeometryPtr &&geom_,
                   const TAK::Engine::Feature::AltitudeMode altitudeMode_, const double extrude_, StylePtr &&style_, AttributeSetPtr &&attributes_,
                   const int64_t version_) NOTHROWS :
    id(fid_),
    setId(fsid_),
    version(version_),
    name(name_),
    geometry(nullptr, nullptr),
    altitudeMode(altitudeMode_),
    extrude(extrude_),
    style(nullptr, nullptr),
    attributes(nullptr, nullptr),
    timestamp(0)
{
    geometry = GeometryPtr_const(geom_.get(), geom_.get_deleter());
    geom_.release();
    style = StylePtr_const(style_.get(), style_.get_deleter());
    style_.release();
    attributes = AttributeSetPtr_const(attributes_.get(), attributes_.get_deleter());
    attributes_.release();
}

Feature2::Feature2(const int64_t fid_, const int64_t fsid_, const char *name_, GeometryPtr_const &&geom_,
                   const TAK::Engine::Feature::AltitudeMode altitudeMode_, const double extrude_, StylePtr_const &&style_, AttributeSetPtr_const &&attributes_,
                   const int64_t version_) NOTHROWS :
    id(fid_),
    setId(fsid_),
    version(version_),
    name(name_),
    geometry(std::move(geom_)),
    altitudeMode(altitudeMode_),
    extrude(extrude_),
    style(std::move(style_)),
    attributes(std::move(attributes_)),
    timestamp(0)
{
}

Feature2::Feature2(const int64_t fid_, const int64_t fsid_, const char *name_, GeometryPtr_const &&geom_,
                   const TAK::Engine::Feature::AltitudeMode altitudeMode_, const double extrude_, StylePtr_const &&style_, AttributeSetPtr_const &&attributes_,
                   const int64_t timestamp_, const int64_t version_) NOTHROWS :
    id(fid_),
    setId(fsid_),
    version(version_),
    name(name_),
    geometry(std::move(geom_)),
    altitudeMode(altitudeMode_),
    extrude(extrude_),
    style(std::move(style_)),
    attributes(std::move(attributes_)),
    timestamp(timestamp_)
{
}

Feature2::~Feature2() NOTHROWS
{}

int64_t Feature2::getFeatureSetId() const NOTHROWS
{
    return this->setId;
}

int64_t Feature2::getId() const NOTHROWS
{
    return this->id;
}

int64_t Feature2::getVersion() const NOTHROWS
{
    return this->version;
}

const char *Feature2::getName() const NOTHROWS
{
    return this->name;
}

const atakmap::feature::Geometry *Feature2::getGeometry() const NOTHROWS
{
    return this->geometry.get();
}

const TAK::Engine::Feature::AltitudeMode Feature2::getAltitudeMode() const NOTHROWS 
{ 
    return this->altitudeMode;
}

const double Feature2::getExtrude() const NOTHROWS 
{ 
    return this->extrude;
}

const atakmap::feature::Style *Feature2::getStyle() const NOTHROWS
{
    return this->style.get();
}

const atakmap::util::AttributeSet *Feature2::getAttributes() const NOTHROWS
{
    return this->attributes.get();
}

int64_t Feature2::getTimestamp() const NOTHROWS
{
    return this->timestamp;
}

TAKErr TAK::Engine::Feature::Feature_create(FeaturePtr_const &feature, FeatureDefinition2 &def) NOTHROWS
{
    TAKErr code;
    FeatureDefinition2::RawData raw;

    code = def.getRawGeometry(&raw);
    TE_CHECKRETURN_CODE(code);

    GeometryPtr_const geom(nullptr, nullptr);
    switch (def.getGeomCoding()) {
    case FeatureDefinition2::GeomBlob:
    {
        if (raw.binary.value) {
            try {
                atakmap::feature::ByteBuffer blob(raw.binary.value, raw.binary.value + raw.binary.len);
                geom = GeometryPtr_const(atakmap::feature::parseBlob(blob), atakmap::feature::destructGeometry);
            } catch (...) {
                return TE_Err;
            }
        }
        break;
    }
    case FeatureDefinition2::GeomWkb:
    {
        if (raw.binary.value) {
            try {
                atakmap::feature::ByteBuffer blob(raw.binary.value, raw.binary.value + raw.binary.len);
                geom = GeometryPtr_const(atakmap::feature::parseWKB(blob), atakmap::feature::destructGeometry);
            } catch (...) {
                return TE_Err;
            }
        }
        break;
    }
    case FeatureDefinition2::GeomWkt:
    {
        if (raw.text) {
            try {
                geom = GeometryPtr_const(atakmap::feature::parseWKT(raw.text), atakmap::feature::destructGeometry);
            } catch (...) {
                return TE_Err;
            }
        }
        break;
    }
    case FeatureDefinition2::GeomGeometry:
    {
        if (raw.object) {
            try {
                const auto *defGeom = (const atakmap::feature::Geometry *)raw.object;
                geom = GeometryPtr_const(defGeom->clone(), atakmap::feature::destructGeometry);
            } catch (...) {
                return TE_Err;
            }
        }
        break;
    }
    default :
        return TE_IllegalState;
    }

    TAK::Engine::Feature::AltitudeMode altitudeMode = def.getAltitudeMode();
    double extrude = def.getExtrude();

    code = def.getRawStyle(&raw);
    TE_CHECKRETURN_CODE(code);

    StylePtr_const style(nullptr, nullptr);
    switch (def.getStyleCoding()) {
    case FeatureDefinition2::StyleOgr:
    {
        if (raw.text) {
            try {
                style = StylePtr_const(atakmap::feature::Style::parseStyle(raw.text), atakmap::feature::Style::destructStyle);
            } catch (...) {
                return TE_Err;
            }
        }
        break;
    }
    case FeatureDefinition2::StyleStyle:
    {
        if (raw.object) {
            try {
                const auto *defStyle = (const atakmap::feature::Style *)raw.object;
                style = StylePtr_const(defStyle->clone(), atakmap::feature::Style::destructStyle);
            } catch (...) {
                return TE_Err;
            }
        }
        break;
    }
    default:
        return TE_IllegalState;
    }

    const atakmap::util::AttributeSet *defAttributes;
    code = def.getAttributes(&defAttributes);
    TE_CHECKRETURN_CODE(code);

    AttributeSetPtr_const attributes(defAttributes ? new atakmap::util::AttributeSet(*defAttributes) : nullptr, deleter<const atakmap::util::AttributeSet>);

    const char *defName;
    code = def.getName(&defName);
    TE_CHECKRETURN_CODE(code);

    feature = FeaturePtr_const(new Feature2(FeatureDataStore2::FEATURE_ID_NONE,
                                            FeatureDataStore2::FEATURESET_ID_NONE,
                                            defName,
                                            std::move(geom),
                                            altitudeMode,
                                            extrude,
                                            std::move(style),
                                            std::move(attributes),
                                            FeatureDataStore2::FEATURE_VERSION_NONE),
                               deleter<const Feature2>);

    return code;
}

TAKErr TAK::Engine::Feature::Feature_create(FeaturePtr_const &feature, const int64_t fid, const int64_t fsid, FeatureDefinition2 &def, const int64_t version) NOTHROWS
{
    TAKErr code;
    FeatureDefinition2::RawData raw;

    code = def.getRawGeometry(&raw);
    TE_CHECKRETURN_CODE(code);

    GeometryPtr_const geom(nullptr, nullptr);
    switch (def.getGeomCoding()) {
    case FeatureDefinition2::GeomBlob:
    {
        if (raw.binary.value) {
            try {
                atakmap::feature::ByteBuffer blob(raw.binary.value, raw.binary.value + raw.binary.len);
                geom = GeometryPtr_const(atakmap::feature::parseBlob(blob), atakmap::feature::destructGeometry);
            } catch (...) {
                return TE_Err;
            }
        }
        break;
    }
    case FeatureDefinition2::GeomWkb:
    {
        if (raw.binary.value) {
            try {
                atakmap::feature::ByteBuffer blob(raw.binary.value, raw.binary.value + raw.binary.len);
                geom = GeometryPtr_const(atakmap::feature::parseWKB(blob), atakmap::feature::destructGeometry);
            } catch (...) {
                return TE_Err;
            }
        }
        break;
    }
    case FeatureDefinition2::GeomWkt:
    {
        if (raw.text) {
            try {
                geom = GeometryPtr_const(atakmap::feature::parseWKT(raw.text), atakmap::feature::destructGeometry);
            } catch (...) {
                return TE_Err;
            }
        }
        break;
    }
    case FeatureDefinition2::GeomGeometry:
    {
        if (raw.object) {
            try {
                const auto *defGeom = (const atakmap::feature::Geometry *)raw.object;
                geom = GeometryPtr_const(defGeom->clone(), atakmap::feature::destructGeometry);
            } catch (...) {
                return TE_Err;
            }
        }
        break;
    }
    default:
        return TE_IllegalState;
    }

    TAK::Engine::Feature::AltitudeMode altitudeMode = def.getAltitudeMode();
    double extrude = def.getExtrude();

    code = def.getRawStyle(&raw);
    TE_CHECKRETURN_CODE(code);

    StylePtr_const style(nullptr, nullptr);
    switch (def.getStyleCoding()) {
    case FeatureDefinition2::StyleOgr:
    {
        if (raw.text) {
            try {
                style = StylePtr_const(atakmap::feature::Style::parseStyle(raw.text), atakmap::feature::Style::destructStyle);
            } catch (...) {
                // XXX - encountering KML with style "links", just return NULL
                style = StylePtr_const(nullptr, atakmap::feature::Style::destructStyle);
                //return TE_Err;
            }
        }
        break;
    }
    case FeatureDefinition2::StyleStyle:
    {
        if (raw.object) {
            try {
                const auto *defStyle = (const atakmap::feature::Style *)raw.object;
                style = StylePtr_const(defStyle->clone(), atakmap::feature::Style::destructStyle);
            } catch (...) {
                return TE_Err;
            }
        }
        break;
    }
    default:
        return TE_IllegalState;
    }

    const atakmap::util::AttributeSet *defAttributes;
    code = def.getAttributes(&defAttributes);
    TE_CHECKRETURN_CODE(code);

    AttributeSetPtr_const attributes(defAttributes ? new atakmap::util::AttributeSet(*defAttributes) : nullptr, deleter<const atakmap::util::AttributeSet>);

    const char *defName;
    code = def.getName(&defName);
    TE_CHECKRETURN_CODE(code);

    feature = FeaturePtr_const(new Feature2(fid,
                                            fsid,
                                            defName,
                                            std::move(geom),
                                            altitudeMode,
                                            extrude,
                                            std::move(style),
                                            std::move(attributes),
                                            version),
                               deleter<const Feature2>);

    return code;
}

bool TAK::Engine::Feature::Feature_isSame(const Feature2 &a, const Feature2 &b) NOTHROWS
{
    return (a.getId() == b.getId()) &&
           (a.getVersion() == b.getVersion()) &&
           (a.getId() != FeatureDataStore2::FEATURE_ID_NONE) &&
           (a.getVersion() != FeatureDataStore2::FEATURE_ID_NONE);
}

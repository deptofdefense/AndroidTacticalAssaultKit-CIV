
#include "renderer/feature/GLBatchGeometry3.h"

#include "feature/Geometry.h"
#include "thread/Lock.h"

using namespace TAK::Engine::Renderer::Feature;

using namespace TAK::Engine::Core;
using namespace TAK::Engine::Renderer::Core;
using namespace TAK::Engine::Thread;
using namespace TAK::Engine::Util;

using namespace atakmap::renderer;
using namespace atakmap::renderer::map;
using namespace atakmap::util;

GLBatchGeometry3::GLBatchGeometry3(RenderContext &surface_, int zOrder_) NOTHROWS:
    surface(surface_),
    zOrder(zOrder_),
    featureId(0LL),
    lod(0),
    subid(0),
    version(0LL),
    extrude(0.0),
    renderPass(GLMapView2::Surface),
    altitudeMode(TAK::Engine::Feature::AltitudeMode::TEAM_ClampToGround)
{}

GLBatchGeometry3::~GLBatchGeometry3() NOTHROWS
{
    this->stop();
}

TAKErr GLBatchGeometry3::init(const int64_t feature_id, const char *name_val, TAK::Engine::Feature::GeometryPtr_const &&geom,
                              const TAK::Engine::Feature::AltitudeMode altitude_mode, const double extrude_val,
                              const std::shared_ptr<const atakmap::feature::Style> &style) NOTHROWS {
    this->featureId = feature_id;
    this->name = name_val;
    this->altitudeMode = altitude_mode;
    this->extrude = extrude_val;
    if (geom.get()) {
        setGeometry(*geom);
    }
    if (style.get()) setStyle(style);
    return TE_Ok;
}

TAKErr GLBatchGeometry3::init(const int64_t feature_id, const char *name_val, BlobPtr &&geomBlob,
                              const TAK::Engine::Feature::AltitudeMode altitude_mode, const double extrude_val, const int type, const int lod_val,
                              const std::shared_ptr<const atakmap::feature::Style> &style) NOTHROWS {
    this->featureId = feature_id;
    this->name = name_val;
    this->altitudeMode = altitude_mode;
    this->extrude = extrude_val;
    if (style.get()) setStyle(style);
    if (geomBlob.get()) {
        setGeometry(std::move(geomBlob), type, lod_val);
    }
    return TE_Ok;
}

TAKErr GLBatchGeometry3::setName(const char *name_val) NOTHROWS
{
    return this->setNameImpl(name_val);
}

TAKErr GLBatchGeometry3::setNameImpl(const char *name_val) NOTHROWS
{
    this->name = name_val;
    return TE_Ok;
}

TAKErr GLBatchGeometry3::setAltitudeMode(const TAK::Engine::Feature::AltitudeMode altitude_mode) NOTHROWS 
{
    return this->setAltitudeModeImpl(altitude_mode);
}

TAKErr GLBatchGeometry3::setAltitudeModeImpl(const TAK::Engine::Feature::AltitudeMode altitude_mode) NOTHROWS 
{
    this->altitudeMode = altitude_mode;
    return TE_Ok;
}

TAKErr GLBatchGeometry3::setGeometry(BlobPtr &&blob, const int type, const int lod_val) NOTHROWS
{
    this->lod = lod_val;
    return this->setGeometryImpl(std::move(blob), type);
}

TAKErr GLBatchGeometry3::setGeometry(const atakmap::feature::Geometry &geom) NOTHROWS
{
    return this->setGeometryImpl(geom);
}

TAKErr GLBatchGeometry3::setVisible(const bool& visible) NOTHROWS
{
    return TE_Ok;
}

int GLBatchGeometry3::getRenderPass() NOTHROWS
{
    return renderPass;
}

void GLBatchGeometry3::start() NOTHROWS
{}

void GLBatchGeometry3::stop() NOTHROWS
{
}

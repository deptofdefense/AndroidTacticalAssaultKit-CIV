#include "renderer/GL.h"

#include "renderer/core/GLContentIndicator.h"

#include "feature/LegacyAdapters.h"
#include "feature/Point.h"
#include "feature/Point2.h"
#include "renderer/GLES20FixedPipeline.h"
#include "renderer/core/GLMapRenderGlobals.h"
#include "renderer/feature/GLBatchLineString3.h"
#include "renderer/feature/GLBatchPolygon3.h"
#include "renderer/feature/GLBatchGeometryCollection3.h"
#include "renderer/feature/GLBatchPoint3.h"
#include "util/Memory.h"

using namespace TAK::Engine::Renderer::Core;

using namespace TAK::Engine::Core;
using namespace TAK::Engine::Feature;
using namespace TAK::Engine::Renderer::Feature;
using namespace TAK::Engine::Util;

namespace
{



    float progressRingVerts[300];

    bool buildProgressRing() NOTHROWS
    {
        double angle = 90.0 * M_PI / 180.0;
        double step = (360.0 / 100.0) * M_PI / 180.0;

        for (int i = 0; i < 100; i ++) {
            float px = 1.0f * static_cast<float>(cos(angle));
            float py = 1.0f * static_cast<float>(sin(angle));
            progressRingVerts[i*3] = -1 * px;
            progressRingVerts[i*3 + 1] = py;
            progressRingVerts[i*3 + 2] = 0.0;
            angle += step;
        }

        return true;
    }
}
GLContentIndicator::GLContentIndicator(RenderContext &ctx) NOTHROWS :
    ctx_(ctx),
    clamp_to_ground_(true),
    min_resolution_(NAN),
    max_resolution_(NAN)
{
    progress_.show = false;
    progress_.value = -1;
}

void GLContentIndicator::setIcon(const TAK::Engine::Core::GeoPoint2 &location, const char *iconUri) NOTHROWS
{
    if (!icon_.get())
        icon_.reset(new GLBatchPoint3(ctx_));
    icon_->setGeometry(atakmap::feature::Point(location.longitude, location.latitude));
    icon_->setStyle(StylePtr_const(new atakmap::feature::IconPointStyle(-1, iconUri), Memory_deleter_const<atakmap::feature::Style, atakmap::feature::IconPointStyle>));
}
void GLContentIndicator::clearIcon() NOTHROWS
{
    if (icon_.get()) {
        icon_->release();
        icon_.reset();
    }
}
void GLContentIndicator::setBounds(const TAK::Engine::Feature::Geometry2 &geom, const atakmap::feature::Style &style, bool clampToGround) NOTHROWS
{
    switch (geom.getClass()) {
    case TEGC_Point :
        bounds_.reset(new GLBatchPoint3(ctx_));
        break;
    case TEGC_LineString:
        bounds_.reset(new GLBatchLineString3(ctx_));
        break;
    case TEGC_Polygon :
        bounds_.reset(new GLBatchPolygon3(ctx_));
        break;
    case TEGC_GeometryCollection :
        bounds_.reset(new GLBatchGeometryCollection3(ctx_));
        break;
    default :
        bounds_.reset();
        return;
    }

    atakmap::feature::GeometryPtr lgeom(nullptr, nullptr);
    if (LegacyAdapters_adapt(lgeom, geom) != TE_Ok) {
        // XXX - log warning
        bounds_.reset();
        return;
    }
    bounds_->setGeometry(*lgeom);
    bounds_->setStyle(StylePtr_const(style.clone(), atakmap::feature::Style::destructStyle));
}
void GLContentIndicator::clearBounds() NOTHROWS
{
    if (bounds_.get()) {
        bounds_->release();
        bounds_.reset();
    }
}
void GLContentIndicator::showProgress(int value) NOTHROWS
{
    progress_.value = value;
    progress_.show = true;
}
void GLContentIndicator::clearProgress() NOTHROWS
{
    progress_.show = false;
}
void GLContentIndicator::setDisplayThresholds(const double minGsd, const double maxGsd) NOTHROWS
{
    min_resolution_ = minGsd;
    max_resolution_ = maxGsd;
}
void GLContentIndicator::draw(const GLMapView2& view, const int renderPass) NOTHROWS
{
    // XXX - disable draw when sufficient zoomed out, retain AABB to compute screen coverage
    if (bounds_.get() && ((clamp_to_ground_ && (renderPass&GLMapView2::Surface)) || (!clamp_to_ground_ && (renderPass&GLMapView2::Sprites)))) {
        bounds_->draw(view, renderPass);
    }

    if (icon_.get() && (renderPass&GLMapView2::Sprites)) {
        // XXX - progress indicator

        icon_->draw(view, renderPass);
    }

    // XXX - currently requires icon, could do progress bar if bounds is
    //       specified but no icon
    if (progress_.show && icon_.get() && (renderPass&GLMapView2::Sprites)) {
        static bool initProgressRingVerts = buildProgressRing();

        TAK::Engine::Math::Point2<double> scratchPoint;

        view.scene.forwardTransform.transform(&scratchPoint, static_cast<GLBatchPoint3 &>(*icon_).posProjected);
        auto xpos = (float)scratchPoint.x;
        auto ypos = (float)scratchPoint.y;
        auto zpos = (float)scratchPoint.z;

        atakmap::renderer::GLES20FixedPipeline *fp = atakmap::renderer::GLES20FixedPipeline::getInstance();
        fp->glEnableClientState(atakmap::renderer::GLES20FixedPipeline::CS_GL_VERTEX_ARRAY);

        const float ringWidth = 4.f;

        fp->glVertexPointer(3, GL_FLOAT, 0, progressRingVerts);

        fp->glPushMatrix();
        fp->glTranslatef(xpos, ypos, zpos);
        fp->glScalef(34.f, 34.f, 1.f);

        // draw background
        fp->glColor4f(0.f, 0.f, 0.f, 1.f);
        glLineWidth(ringWidth + 2.f);
        fp->glDrawArrays(GL_LINE_LOOP, 0, 100);

        // draw foreground
        fp->glColor4f(0.f, 1.f, 0.f, 1.f);
        glLineWidth(ringWidth);
        fp->glDrawArrays(GL_LINE_STRIP, 0, (int)progress_.value);

        fp->glPopMatrix();
        fp->glDisableClientState(atakmap::renderer::GLES20FixedPipeline::CS_GL_VERTEX_ARRAY);
    }
}
void GLContentIndicator::release() NOTHROWS
{
    if (icon_.get())
        icon_->release();
    if (bounds_.get())
        bounds_->release();
}
int GLContentIndicator::getRenderPass() NOTHROWS
{
    int retval = 0;
    if (icon_.get())
        retval |= GLMapView2::Sprites;
    if (bounds_.get() && clamp_to_ground_)
        retval |= GLMapView2::Surface;
    return retval;
}
void GLContentIndicator::start() NOTHROWS
{}
void GLContentIndicator::stop() NOTHROWS
{}

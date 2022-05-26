#include "renderer/core/GLLabel.h"

#include <algorithm>
#include <cmath>

#include "feature/Geometry2.h"

#include "feature/LineString.h"
#include "feature/LineString2.h"
#include "feature/Point2.h"
#include "util/MathUtils.h"
#include "math/Rectangle.h"
#include "renderer/GLES20FixedPipeline.h"
#include "renderer/GLNinePatch.h"
#include "renderer/GLText2.h"
#include "renderer/core/GLMapView2.h"
#include "util/Distance.h"

// XXX - must come after `GLMapView2` otherwise NDK build barfs
#include "feature/LegacyAdapters.h"
#include "renderer/core/GLMapRenderGlobals.h"
#include "math/Ray2.h"
#include "math/Vector4.h"
#include "math/Plane2.h"


using namespace TAK::Engine::Renderer::Core;

using namespace TAK::Engine;
using namespace TAK::Engine::Core;
using namespace TAK::Engine::Math;
using namespace TAK::Engine::Renderer;
using namespace TAK::Engine::Util;

using namespace atakmap::renderer;

#define ONE_EIGHTY_OVER_PI 57.295779513082320876798154814105
#define LABEL_PADDING_X 10
#define LABEL_PADDING_Y 5

#define CALL_LOCALIZE 1

#define MAX_TEXT_WIDTH 80.f

atakmap::renderer::GLNinePatch* GLLabel::small_nine_patch_ = nullptr;

template<class T>
static T DistanceSquared(T ax, T ay, T bx, T by) {
    T dx = ax - bx;
    T dy = ay - by;
    return dx * dx + dy * dy;
}

template<class T>
static bool IntersectLine(TAK::Engine::Math::Point2<T>& pIntersection, T a1x, T a1y, T a2x, T a2y, T b1x, T b1y,
                          T b2x, T b2y) {
    // code from
    // http://csharphelper.com/blog/2014/08/determine-where-two-lines-intersect-in-c/

    // Get the segments' parameters.
    T dx12 = a2x - a1x;
    T dy12 = a2y - a1y;
    T dx34 = b2x - b1x;
    T dy34 = b2y - b1y;

    // Solve for t1 and t2
    T denominator = (dy12 * dx34 - dx12 * dy34);

    T t1 = ((a1x - b1x) * dy34 + (b1y - a1y) * dx34) / denominator;
    if (std::isinf(t1)) {
        // The lines are parallel (or close enough to it).
        return false;
    }

    // the lines intersect
    T t2 = ((b1x - a1x) * dy12 + (a1y - b1y) * dx12) / -denominator;

    // The segments intersect if t1 and t2 are between 0 and 1.
    if ((t1 >= 0) && (t1 <= 1) && (t2 >= 0) && (t2 <= 1)) {
        pIntersection = TAK::Engine::Math::Point2<T>(a1x + dx12 * t1, a1y + dy12 * t1);
        return true;
    }
    return false;
}

template<class T>
static bool IntersectRectangle(TAK::Engine::Math::Point2<T>& p1, T p2x, T p2y, T rx0, T ry0, T rx1, T ry1) {
    auto isects = std::vector<TAK::Engine::Math::Point2<T>>();
    // intersect left
    TAK::Engine::Math::Point2<T> i1, i2, i3, i4;
    if (IntersectLine(i1, p1.x, p1.y, p2x, p2y, rx0, ry0, rx0, ry1)) {
        isects.push_back(i1);  // intersect left
    }
    if (IntersectLine(i2, p1.x, p1.y, p2x, p2y, rx0, ry0, rx1, ry0)) {
        isects.push_back(i2);  // intersect top
    }
    if (IntersectLine(i3, p1.x, p1.y, p2x, p2y, rx0, ry1, rx1, ry1)) {
        isects.push_back(i3);  // intersect bottom
    }
    if (IntersectLine(i4, p1.x, p1.y, p2x, p2y, rx1, ry0, rx1, ry1)) {
        isects.push_back(i4);  // intersect right
    }

    if (isects.size() < 1) return false;

    auto isect = isects[0];
    auto dist2 = DistanceSquared(isect.x, isect.y, p1.x, p1.y);
    for (size_t i = 1; i < isects.size(); i++) {
        T d2 = DistanceSquared(isects[i].x, isects[i].y, p1.x, p1.y);
        if (d2 < dist2) {
            isect = isects[i];
            dist2 = d2;
        }
    }

    p1 = isect;
    return true;
}

static bool IntersectRotatedRectangle(const std::array<TAK::Engine::Math::Point2<float>,4>& r0, const std::array<TAK::Engine::Math::Point2<float>,4>& r1)
{
    // intersect left

    TAK::Engine::Math::Point2<float> pIntersection;

    for(std::size_t i=0; i < 4;++i)
    {
        std::size_t i1 = (i + 1) & 0x3;
        for(std::size_t j=0;j < 4;j++)
        {
            std::size_t j1 = (j + 1) & 0x3;
            if (IntersectLine(pIntersection, r0[i].x, r0[i].y, r0[i1].x, r0[i1].y, r1[j].x, r1[j].y, r1[j1].x, r1[j1].y))
            {
                return true;
            }
        }
    }

    return false;
}

void GLLabel::LabelPlacement::recompute(const double absoluteRotation, const float width, const float height) NOTHROWS
{
    double rotate = rotation_.angle_;
    if (rotation_.absolute_) {
        rotate = fmod(rotate + absoluteRotation, 360.0);
    }

    rotate *= M_PI / 180.;

    float rsin = 0;
    float rcos = 1;

    if(rotate != 0.0f)
    {
        rsin = sinf(static_cast<float>(rotate));
        rcos = cosf(static_cast<float>(rotate));
    }

    float width2 = width / 2.0f;
    float height2 = height / 2.0f;

    rotatedRectangle[0] = TAK::Engine::Math::Point2<float>(-width2, height2);
    rotatedRectangle[1] = TAK::Engine::Math::Point2<float>(-width2, -height2);
    rotatedRectangle[2] = TAK::Engine::Math::Point2<float>(width2, -height2);
    rotatedRectangle[3] = TAK::Engine::Math::Point2<float>(width2, height2);

    for(int i=0; i < 4;++i)
    {
        float x = rotatedRectangle[i].x * rcos - rotatedRectangle[i].y * rsin;
        float y = rotatedRectangle[i].y * rcos + rotatedRectangle[i].x * rsin;

        rotatedRectangle[i].x = x + static_cast<float>(render_xyz_.x) + width2;
        rotatedRectangle[i].y = y + static_cast<float>(render_xyz_.y) + height2;
    }
}

template<class T>
static bool FindIntersection(TAK::Engine::Math::Point2<T>& p1, TAK::Engine::Math::Point2<T>& p2, T rx0, T ry0, T rx1,
                             T ry1) {
    // if endpoint 1 is not contained, find intersection
    if (!atakmap::math::Rectangle<T>::contains(rx0, ry0, rx1, ry1, p1.x, p1.y) &&
        !IntersectRectangle(p1, p2.x, p2.y, rx0, rx0, rx1, ry1)) {
        // no intersection point
        return false;
    }
    // if endpoint 2 is not contained, find intersection
    if (!atakmap::math::Rectangle<T>::contains(rx0, ry0, rx1, ry1, p2.x, p2.y) &&
        !IntersectRectangle(p2, p1.x, p1.y, rx0, rx0, rx1, ry1)) {
        // no intersection point
        return false;
    }

    return true;
}

namespace
{
    struct FloatingAnchor
    {
        GeoPoint2 lla;
        struct {
            Point2<float> a;
            Point2<float> b;
        } axis;
    };

    Point2<double> nearestPointOnSegment(const Point2<double> &sp, const Point2<double> &ep, const Point2<double> p) NOTHROWS
    {
        Point2<double> v = Vector2_subtract(ep, sp);
        Point2<double> w = Vector2_subtract(p, sp);
        const double c1 = Vector2_dot(w, v);
        if (c1 <= 0.0)
            return sp;
        const double c2 = Vector2_dot(v, v);
        if (c2 <= c1)
            return ep;
        const double b = c1 / c2;
        return Point2<double>(sp.x + b * v.x, sp.y + b * v.y, sp.z + b
                * v.z);
    }

    GeoPoint2 closestSurfacePointToFocus(const GLGlobeBase &ortho, const float xmid, const float ymid, const GeoPoint2 &a, const GeoPoint2 &b) NOTHROWS
    {
        struct
        {
            GeoPoint2 geo;
            Point2<double> pointD;
        } scratch;

        ortho.renderPass->scene.projection->inverse(&scratch.geo, ortho.renderPass->scene.camera.target);
        if(TE_ISNAN(scratch.geo.altitude))
            scratch.geo.altitude = 100.0;
        else
            scratch.geo.altitude += 100.0;
        ortho.renderPass->scene.projection->forward(&scratch.pointD, scratch.geo);

        // compute focus as location as screen point at x,y on the plane
        // passing through the camera focus with the local up as the normal
        // vector
        Vector4<double> normal (
                (scratch.pointD.x-ortho.renderPass->scene.camera.target.x),
                (scratch.pointD.y-ortho.renderPass->scene.camera.target.y),
                (scratch.pointD.z-ortho.renderPass->scene.camera.target.z));
        Plane2 focusPlane(normal, ortho.renderPass->scene.camera.target);
        GeoPoint2 focus;
        if(ortho.renderPass->scene.inverse(&focus, Point2<float>(xmid, ymid), focusPlane) != TE_Ok)
            ortho.renderPass->scene.projection->inverse(&focus, ortho.renderPass->scene.camera.target);

        if(ortho.renderPass->drawSrid == 4978) {
            // compute the interpolation weight for the surface point
            scratch.geo = focus;
            const double dpts = GeoPoint2_distance(a, b, true);
            double dtofocus = GeoPoint2_alongTrackDistance(a, b, scratch.geo, true);
            if(dtofocus < dpts &&
                    GeoPoint2_distance(scratch.geo, a, true) <
                    GeoPoint2_distance(scratch.geo, GeoPoint2_pointAtDistance(a, b,
                        dtofocus/dpts, true), true)) {
                dtofocus *= -1.0;
            }

            const double weight = MathUtils_clamp(dtofocus / dpts, 0.0, 1.0);

            return GeoPoint2_pointAtDistance(a, b,
                        weight, true);
        } else {
            // execute closest-point-on-line as cartesian math
            ortho.renderPass->scene.projection->forward(&scratch.pointD, a);
            const double ax = scratch.pointD.x*ortho.renderPass->scene.displayModel->projectionXToNominalMeters;
            const double ay = scratch.pointD.y*ortho.renderPass->scene.displayModel->projectionYToNominalMeters;
            const double az = scratch.pointD.z*ortho.renderPass->scene.displayModel->projectionZToNominalMeters;
            ortho.renderPass->scene.projection->forward(&scratch.pointD, b);
            const double bx = scratch.pointD.x*ortho.renderPass->scene.displayModel->projectionXToNominalMeters;
            const double by = scratch.pointD.y*ortho.renderPass->scene.displayModel->projectionYToNominalMeters;
            const double bz = scratch.pointD.z*ortho.renderPass->scene.displayModel->projectionZToNominalMeters;

            ortho.renderPass->scene.projection->forward(&scratch.pointD, focus);
            const double dx = scratch.pointD.x*ortho.renderPass->scene.displayModel->projectionXToNominalMeters;
            const double dy = scratch.pointD.y*ortho.renderPass->scene.displayModel->projectionYToNominalMeters;
            const double dz = scratch.pointD.z*ortho.renderPass->scene.displayModel->projectionZToNominalMeters;

            Point2<double> p = nearestPointOnSegment(Point2<double>(ax, ay, az), Point2<double>(bx, by, bz), Point2<double>(dx, dy, dz));

            scratch.pointD.x = p.x / ortho.renderPass->scene.displayModel->projectionXToNominalMeters;
            scratch.pointD.y = p.y / ortho.renderPass->scene.displayModel->projectionYToNominalMeters;
            scratch.pointD.z = p.z / ortho.renderPass->scene.displayModel->projectionZToNominalMeters;

            ortho.renderPass->scene.projection->inverse(&scratch.geo, scratch.pointD);
            return scratch.geo;
        }
    }

    GeoPoint2 adjustSurfaceLabelAnchor(const GLGlobeBase &view, const GeoPoint2 &geo, const Feature::AltitudeMode altitudeMode) NOTHROWS
    {
        // Z/altitude
        if (view.renderPass->drawTilt == 0.0 && view.renderPass->scene.camera.mode == MapCamera2::Scale)
            return geo;

        bool belowTerrain = false;
        double posEl = 0.0;
        // XXX - altitude
        double alt = geo.altitude;
        double terrain;
        view.getTerrainMeshElevation(&terrain, geo.latitude, geo.longitude);
        if (TE_ISNAN(alt) || altitudeMode == TAK::Engine::Feature::AltitudeMode::TEAM_ClampToGround) {
            alt = terrain;
        } else if (altitudeMode == TAK::Engine::Feature::AltitudeMode::TEAM_Relative) {
            alt += terrain;
        } else if (alt < terrain) {
            // if the explicitly specified altitude is below the terrain,
            // float above and annotate appropriately
            belowTerrain = true;
            alt = terrain;
        }

        // note: always NaN if source alt is NaN
        double adjustedAlt = alt * view.elevationScaleFactor;

        // move up ~5 pixels from surface
#ifndef __ANDROID__
        if(view.renderPass->drawTilt)
            adjustedAlt += view.renderPass->drawMapResolution * 25.0;
#endif
        return GeoPoint2(geo.latitude, geo.longitude, adjustedAlt, AltitudeReference::HAE);
    }

    void intersectWithNearPlane(Point2<float> &axy, Point2<float> &bxy, const GLGlobeBase &view, const GeoPoint2 &alla, const GeoPoint2 &blla) NOTHROWS
    {
        // both are in front of the camera
        if(axy.z < 1.f && bxy.z < 1.f)
            return;
        // both are behind the camera
        if(axy.z > 1.f && bxy.z > 1.f)
            return;

        // find the intersect of the segment with the near plane
        const double losdx = view.renderPass->scene.camera.location.x-view.renderPass->scene.camera.target.x;
        const double losdy = view.renderPass->scene.camera.location.y-view.renderPass->scene.camera.target.y;
        const double losdz = view.renderPass->scene.camera.location.z-view.renderPass->scene.camera.target.z;

        const double losm = Vector2_length(Point2<double>(losdx, losdy, losdz));
        const Plane2 near(Vector4<double>(losdx, losdy, losdz),
                Point2<double>(view.renderPass->scene.camera.location.x-(losdx/losm)*view.renderPass->scene.camera.nearMeters,
                        view.renderPass->scene.camera.location.y-(losdy/losm)*view.renderPass->scene.camera.nearMeters,
                        view.renderPass->scene.camera.location.z-(losdz/losm)*view.renderPass->scene.camera.nearMeters
                        ));

        Point2<double> p0;
        view.renderPass->scene.projection->forward(&p0, alla);
        Point2<double> p1;
        view.renderPass->scene.projection->forward(&p1, blla);

        // create segment
        Point2<double> isect;
        if(near.intersect(&isect, Ray2<double>(p0, Vector4<double>(p1.x-p0.x, p1.y-p0.y, p1.z-p0.z)))) {
            view.renderPass->scene.forwardTransform.transform(&isect, isect);
            Point2<float> *xyzpos = (axy.z > 1.f) ? &axy : &bxy;
            xyzpos->x = (float)isect.x;
            xyzpos->y = (float)isect.y;
            xyzpos->z = (float)isect.z;
        }
    }
    void getRenderPoint(GeoPoint2 *value, const GLGlobeBase &ortho, const Feature::LineString2 &points, const std::size_t idx, const Feature::AltitudeMode altitudeMode) NOTHROWS
    {
        if(idx >= points.getNumPoints())
            return;
        points.getY(&value->latitude, idx);
        points.getX(&value->longitude, idx);


        // source altitude is populated for absolute or relative IF 3 elements specified
        if(points.getDimension() == 3 && altitudeMode != Feature::TEAM_ClampToGround)
            points.getZ(&value->altitude, idx);
        else
            value->altitude = 0.0;
        // terrain is populated for clamp-to-ground or relative
        double terrain = 0.0;
        if(altitudeMode != Feature::TEAM_Absolute) {
            ortho.getTerrainMeshElevation(&terrain, value->latitude, value->longitude);
            if(TE_ISNAN(terrain))
                terrain = 0.0;
        }
        value->altitude += terrain;
    }
    void validateSurfaceFloatingLabel(std::vector<FloatingAnchor> &anchors, Point2<double> *midpoint, const GLGlobeBase &ortho, const Feature::LineString2 &points, const float labelWidth) NOTHROWS {
        const std::size_t numPoints = points.getNumPoints();
        if (!numPoints)
            return;

        double stripLength = 0.0;
        std::vector<double> segmentLengths;
        segmentLengths.reserve(numPoints - 1u);
        double xmin = std::numeric_limits<double>::max(), ymin = std::numeric_limits<double>::max(),
                xmax = std::numeric_limits<double>::min(), ymax = std::numeric_limits<double>::min();

        double txmin = xmin;
        double txmax = xmax;
        double tymin = ymin;
        double tymax = ymax;

        GeoPoint2 startGeo;
        GeoPoint2 endGeo;

        getRenderPoint(&endGeo, ortho, points, 0u, Feature::TEAM_ClampToGround);

        Point2<float> start;
        Point2<float> end;
        ortho.renderPass->scene.forward(&end, endGeo);

        int stripStartIdx = -1;
        for (std::size_t i = 0u; i < numPoints - 1u; i++) {
            startGeo = endGeo;
            getRenderPoint(&endGeo, ortho, points, i + 1u, Feature::TEAM_ClampToGround);

            Point2<float> xy0, xy1;
            ortho.renderPass->scene.forward(&xy0, startGeo);
            ortho.renderPass->scene.forward(&xy1, endGeo);

            // emit the strip currently being processed if it will be clipped
            const bool emit =
                    !atakmap::math::Rectangle<float>::contains(static_cast<float>(ortho.renderPass->left), static_cast<float>(ortho.renderPass->bottom),
                        static_cast<float>(ortho.renderPass->right), static_cast<float>(ortho.renderPass->top), xy1.x, xy1.y);

            // clip the segment
            if(!FindIntersection<float>(xy0, xy1, static_cast<float>(ortho.renderPass->left), static_cast<float>(ortho.renderPass->bottom),
                    static_cast<float>(ortho.renderPass->right), static_cast<float>(ortho.renderPass->top)))
                continue; // segment was completely outside region

            // start a new strip if necessary
            if(stripStartIdx == -1) {
                stripStartIdx = static_cast<int>(i);
            }

            // record the segment length
            segmentLengths.push_back(Vector2_length(
                    Point2<double>(xy0.x-xy1.x, xy0.y-xy1.y)));
            // update total strip length
            stripLength += segmentLengths[i - stripStartIdx];

            // update bounds for current strip
            Point2<float> clippedSegment[2];

            clippedSegment[0] = xy0;
            clippedSegment[1] = xy1;
            for (int j = 0; j < 2; j++) {
                const float x = clippedSegment[j].x;
                const float y = clippedSegment[j].y;
                if (x > xmax)
                    xmax = x;
                if (x < xmin)
                    xmin = x;
                if (y > ymax)
                    ymax = y;
                if (y < ymin)
                    ymin = y;
            }

            if(xmin < txmin) txmin = xmin;
            if(ymin < tymin) tymin = ymin;
            if(xmax > txmax) txmax = xmax;
            if(ymax > tymax) tymax = ymax;

            // check emit
            if (emit && stripStartIdx != -1 &&
                (std::max(xmax - xmin, ymax - ymin) >= labelWidth)) {

                // locate the segment that contains the midpoint of the current strip
                const double halfLength = stripLength * 0.5;
                double t = 0.0;
                int containingSegIdx = stripStartIdx;
                for (int j = stripStartIdx; j <= i; j++) {
                    t += segmentLengths[j-stripStartIdx];
                    if (t > halfLength) {
                        containingSegIdx = j;
                        break;
                    }
                }

                const double segStart = t - segmentLengths[containingSegIdx - stripStartIdx];
                const double segPercent = (halfLength - segStart)
                                          / segmentLengths[containingSegIdx - stripStartIdx];

                GeoPoint2 segStartLLA, segEndLLA;
                Point2<float> segStartXY, segEndXY;
                getRenderPoint(&segStartLLA, ortho, points, containingSegIdx, Feature::TEAM_ClampToGround);
                ortho.renderPass->scene.forward(&segStartXY, segStartLLA);
                const float segStartX = segStartXY.x;
                const float segStartY = segStartXY.y;
                getRenderPoint(&segEndLLA, ortho, points, containingSegIdx+1u, Feature::TEAM_ClampToGround);
                ortho.renderPass->scene.forward(&segEndXY, segEndLLA);
                const float segEndX = segEndXY.x;
                const float segEndY = segEndXY.y;

                Point2<float> clippedSegStartXY(segStartXY);
                Point2<float> clippedSegEndXY(segEndXY);
                FindIntersection<float>(clippedSegStartXY, clippedSegEndXY,
                    static_cast<float>(ortho.renderPass->left),
                    static_cast<float>(ortho.renderPass->bottom),
                    static_cast<float>(ortho.renderPass->right),
                    static_cast<float>(ortho.renderPass->top));

                const float px = clippedSegStartXY.x + (clippedSegEndXY.x - clippedSegStartXY.x) * (float) segPercent;
                const float py = clippedSegStartXY.y + (clippedSegEndXY.y - clippedSegStartXY.y) * (float) segPercent;

                const double segNormalX =
                        (clippedSegEndXY.x - clippedSegStartXY.x) / segmentLengths[containingSegIdx - stripStartIdx];
                const double segNormalY =
                        (clippedSegEndXY.y - clippedSegStartXY.y) / segmentLengths[containingSegIdx - stripStartIdx];

                start.x = (float) (px - segNormalX);
                start.y = (float) (py - segNormalY);
                end.x = (float) (px + segNormalX);
                end.y = (float) (py + segNormalY);

                const Point2<float> textPoint((start.x+end.x)/2.0f, (start.y+end.y)/2.f);

                // recompute LLA
                const double weight =
                        Vector2_length<double>(Point2<double>(textPoint.x-segStartX, textPoint.y-segStartY)) /
                        Vector2_length(Point2<double>(segStartX-segEndX, segStartY-segEndY));
                GeoPoint2 label_lla = GeoPoint2_pointAtDistance(segStartLLA, segEndLLA,
                        weight, true);

                ortho.getTerrainMeshElevation(
                      &label_lla.altitude,
                      label_lla.latitude,
                      label_lla.longitude);

                FloatingAnchor anchor;
                anchor.lla = label_lla;
                anchor.axis.a = start;
                anchor.axis.b = end;

                anchors.push_back(anchor);

                // reset for next strip
                stripStartIdx = -1;

                stripLength = 0.0;
                segmentLengths.clear();

                xmin = std::numeric_limits<double>::max();
                ymin = std::numeric_limits<double>::max();
                xmax = std::numeric_limits<double>::min();
                ymax = std::numeric_limits<double>::min();
            }
        }

        if(midpoint) {
            midpoint->x = (txmin+txmax) / 2.0;
            midpoint->y = (tymin+tymax) / 2.0;
        }

        // check emit
        if (stripStartIdx != -1 &&
            (std::max(xmax - xmin, ymax - ymin) >= labelWidth)) {

            // locate the segment that contains the midpoint of the current strip
            const double halfLength = stripLength * 0.5;
            double t = 0.0;
            int containingSegIdx = -1;
            for (int j = 0; j < (numPoints-1u)-stripStartIdx; j++) {
                t += segmentLengths[j];
                if (t > halfLength) {
                    containingSegIdx = j + stripStartIdx;
                    break;
                }
            }
            if (containingSegIdx >= 0) {
                const double segStart = t - segmentLengths[containingSegIdx - stripStartIdx];
                const double segPercent = (halfLength - segStart)
                                          / segmentLengths[containingSegIdx - stripStartIdx];

                GeoPoint2 segStartLLA, segEndLLA;
                Point2<float> segStartXY, segEndXY;
                getRenderPoint(&segStartLLA, ortho, points, containingSegIdx, Feature::TEAM_ClampToGround);
                ortho.renderPass->scene.forward(&segStartXY, segStartLLA);
                const float segStartX = segStartXY.x;
                const float segStartY = segStartXY.y;
                getRenderPoint(&segEndLLA, ortho, points, containingSegIdx+1u, Feature::TEAM_ClampToGround);
                ortho.renderPass->scene.forward(&segEndXY, segEndLLA);
                const float segEndX = segEndXY.x;
                const float segEndY = segEndXY.y;

                Point2<float> clippedSegStartXY(segStartXY);
                Point2<float> clippedSegEndXY(segEndXY);
                FindIntersection<float>(clippedSegStartXY, clippedSegEndXY,
                    static_cast<float>(ortho.renderPass->left),
                    static_cast<float>(ortho.renderPass->bottom),
                    static_cast<float>(ortho.renderPass->right),
                    static_cast<float>(ortho.renderPass->top));

                const float px = clippedSegStartXY.x + (clippedSegEndXY.x - clippedSegStartXY.x) * (float) segPercent;
                const float py = clippedSegStartXY.y + (clippedSegEndXY.y - clippedSegStartXY.y) * (float) segPercent;

                const double segNormalX =
                        (clippedSegEndXY.x - clippedSegStartXY.x) / segmentLengths[containingSegIdx - stripStartIdx];
                const double segNormalY =
                        (clippedSegEndXY.y - clippedSegStartXY.y) / segmentLengths[containingSegIdx - stripStartIdx];

                start.x = (float) (px - segNormalX);
                start.y = (float) (py - segNormalY);
                end.x = (float) (px + segNormalX);
                end.y = (float) (py + segNormalY);

                const Point2<float> textPoint((start.x+end.x)/2.0f, (start.y+end.y)/2.f);

                // XXX - rotation

                // recompute LLA
                const double weight =
                        Vector2_length<double>(Point2<double>(textPoint.x-segStartX, textPoint.y-segStartY)) /
                        Vector2_length(Point2<double>(segStartX-segEndX, segStartY-segEndY));
                GeoPoint2 label_lla = GeoPoint2_pointAtDistance(
                        segStartLLA,
                        segEndLLA,
                        weight, true);

                ortho.getTerrainMeshElevation(
                      &label_lla.altitude,
                      label_lla.latitude,
                      label_lla.longitude);

                FloatingAnchor anchor;
                anchor.lla = label_lla;
                anchor.axis.a = start;
                anchor.axis.b = end;

                anchors.push_back(anchor);
            }

            stripStartIdx = -1;
        }
    }
}

GLLabel::GLLabel()
    : geometry_(nullptr, nullptr),
      altitude_mode_(TAK::Engine::Feature::AltitudeMode::TEAM_ClampToGround),
      visible_(true),
      always_render_(false),
      max_draw_resolution_(0.0),
      alignment_(TextAlignment::TETA_Center),
      vertical_alignment_(VerticalAlignment::TEVA_Top),
      priority_(Priority::TEP_Standard),
      color_r_(1),
      color_g_(1),
      color_b_(1),
      color_a_(1),
      back_color_r_(0),
      back_color_g_(0),
      back_color_b_(0),
      back_color_a_(0),
      fill_(false),
      projected_size_(std::numeric_limits<double>::quiet_NaN()),   
      mark_dirty_(true),
      draw_version_(-1),
      hints_(Hints::AutoSurfaceOffsetAdjust|Hints::WeightedFloat),
      float_weight_(0.5f),
      gltext_(nullptr)
{
    rotation_.angle_ = 0.0f;
    rotation_.absolute_ = false;
    rotation_.explicit_ = false;
}

GLLabel::GLLabel(const GLLabel& rhs) : GLLabel() {
    Feature::Geometry_clone(geometry_, *(rhs.geometry_.get()));
    altitude_mode_ = rhs.altitude_mode_;
    text_ = rhs.text_;
    desired_offset_ = rhs.desired_offset_;
    max_draw_resolution_ = rhs.max_draw_resolution_;
    visible_ = rhs.visible_;
    always_render_ = rhs.always_render_;
    alignment_ = rhs.alignment_;
    vertical_alignment_ = rhs.vertical_alignment_;
    priority_ = rhs.priority_;
    rotation_ = rhs.rotation_;
    insets_ = rhs.insets_;
    float_weight_ = rhs.float_weight_;
    transformed_anchor_ = rhs.transformed_anchor_;
    draw_version_ = rhs.draw_version_;
    color_a_ = rhs.color_a_;
    color_r_ = rhs.color_r_;
    color_g_ = rhs.color_g_;
    color_b_ = rhs.color_b_;
    back_color_a_ = rhs.back_color_a_;
    back_color_r_ = rhs.back_color_r_;
    back_color_g_ = rhs.back_color_g_;
    back_color_b_ = rhs.back_color_b_;
    hints_ = rhs.hints_;
    fill_ = rhs.fill_;
    gltext_ = rhs.gltext_;
}

GLLabel::GLLabel(GLLabel&& rhs) NOTHROWS : GLLabel() {
    geometry_ = std::move(rhs.geometry_);
    altitude_mode_ = rhs.altitude_mode_;
    text_ = rhs.text_;
    desired_offset_ = rhs.desired_offset_;
    max_draw_resolution_ = rhs.max_draw_resolution_;
    visible_ = rhs.visible_;
    always_render_ = rhs.always_render_;
    alignment_ = rhs.alignment_;
    vertical_alignment_ = rhs.vertical_alignment_;
    priority_ = rhs.priority_;
    rotation_ = rhs.rotation_;
    insets_ = rhs.insets_;
    float_weight_ = rhs.float_weight_;
    hints_ = rhs.hints_;
    transformed_anchor_ = rhs.transformed_anchor_;
    draw_version_ = rhs.draw_version_;
    color_a_ = rhs.color_a_;
    color_r_ = rhs.color_r_;
    color_g_ = rhs.color_g_;
    color_b_ = rhs.color_b_;
    back_color_a_ = rhs.back_color_a_;
    back_color_r_ = rhs.back_color_r_;
    back_color_g_ = rhs.back_color_g_;
    back_color_b_ = rhs.back_color_b_;
    fill_ = rhs.fill_;
    gltext_ = rhs.gltext_;
}

GLLabel::GLLabel(Feature::Geometry2Ptr_const&& geometry, TAK::Engine::Port::String text, Point2<double> desired_offset,
                 double max_draw_resolution, TextAlignment alignment, VerticalAlignment vertical_alignment, int color, int backColor,
                 bool fill, TAK::Engine::Feature::AltitudeMode altitude_mode, Priority priority)
    : geometry_(std::move(geometry)),
      altitude_mode_(altitude_mode),
      text_(text.get() != nullptr ? text.get() : ""),
      desired_offset_(desired_offset),
      visible_(true),
      max_draw_resolution_(max_draw_resolution),
      alignment_(alignment),
      vertical_alignment_(vertical_alignment),
      priority_(priority),
      always_render_(false),
      color_r_(1),
      color_g_(1),
      color_b_(1),
      color_a_(1),
      back_color_r_(0),
      back_color_g_(0),
      back_color_b_(0),
      back_color_a_(0),
      fill_(fill),
      hints_(Hints::AutoSurfaceOffsetAdjust),
      float_weight_(0.5f),
      projected_size_(std::numeric_limits<double>::quiet_NaN()),
      mark_dirty_(true),
      draw_version_(-1),
      gltext_(nullptr)
{
    rotation_.angle_ = 0.0f;
    rotation_.absolute_ = false;
    rotation_.explicit_ = false;

#if CALL_LOCALIZE
    if (!text_.empty()) {
        Port::String localizedText;
        GLText2_localize(&localizedText, text_.c_str());
        text_ = localizedText.get() ? localizedText.get() : "";
    }
#endif
    setColor(color);
    setBackColor(backColor);
}


GLLabel::GLLabel(Feature::Geometry2Ptr_const&& geometry, TAK::Engine::Port::String text, Point2<double> desired_offset,
                 double max_draw_resolution, TextAlignment alignment, VerticalAlignment vertical_alignment, int color, int fill_color,
                 bool fill, TAK::Engine::Feature::AltitudeMode altitude_mode, float rotation, bool absolute_rotation, Priority priority)
    : GLLabel(std::move(geometry), text, desired_offset, max_draw_resolution, alignment, vertical_alignment, color, fill_color, fill, altitude_mode, priority)
{
    rotation_.angle_ = rotation;
    rotation_.absolute_ = absolute_rotation;
    rotation_.explicit_ = true;
}

GLLabel::GLLabel(const TextFormatParams &fmt,
                 TAK::Engine::Feature::Geometry2Ptr_const&& geometry, TAK::Engine::Port::String text,
                 Math::Point2<double> desired_offset, double max_draw_resolution,
                 TextAlignment alignment,
                 VerticalAlignment vertical_alignment, int color,
                 int fill_color, bool fill,
                 TAK::Engine::Feature::AltitudeMode altitude_mode,
                 Priority priority) :
    GLLabel(std::move(geometry), text, desired_offset, max_draw_resolution, alignment, vertical_alignment, color, fill_color, fill, altitude_mode, priority)
{
    gltext_ = GLText2_intern(fmt);
    rotation_.angle_ = 0.0f;
    rotation_.absolute_ = false;
    rotation_.explicit_ = false;
}

GLLabel::GLLabel(const TextFormatParams &fmt,
                 TAK::Engine::Feature::Geometry2Ptr_const&& geometry, TAK::Engine::Port::String text,
                 Math::Point2<double> desired_offset, double max_draw_resolution,
                 TextAlignment alignment,
                 VerticalAlignment vertical_alignment, int color,
                 int fill_color, bool fill,
                 TAK::Engine::Feature::AltitudeMode altitude_mode,
                 float rotation, bool rotationAbsolute,
                 Priority priority) :
    GLLabel(std::move(geometry), text, desired_offset, max_draw_resolution, alignment, vertical_alignment, color, fill_color, fill, altitude_mode, priority)
{
    gltext_ = GLText2_intern(fmt);
    rotation_.angle_ = rotation;
    rotation_.absolute_ = rotationAbsolute;
    rotation_.explicit_ = true;
}

GLLabel::~GLLabel() NOTHROWS
{}

GLLabel& GLLabel::operator=(GLLabel&& rhs) NOTHROWS {
    if (this != &rhs) {
        geometry_ = std::move(rhs.geometry_);
        altitude_mode_ = rhs.altitude_mode_;
        text_ = rhs.text_;
        desired_offset_ = rhs.desired_offset_;
        max_draw_resolution_ = rhs.max_draw_resolution_;
        visible_ = rhs.visible_;
        always_render_ = rhs.always_render_;
        alignment_ = rhs.alignment_;
        vertical_alignment_ = rhs.vertical_alignment_;
        priority_ = rhs.priority_;
        rotation_ = rhs.rotation_;
        insets_ = rhs.insets_;
        transformed_anchor_ = rhs.transformed_anchor_;
        draw_version_ = rhs.draw_version_;
        color_a_ = rhs.color_a_;
        color_r_ = rhs.color_r_;
        color_g_ = rhs.color_g_;
        color_b_ = rhs.color_b_;
        back_color_a_ = rhs.back_color_a_;
        back_color_r_ = rhs.back_color_r_;
        back_color_g_ = rhs.back_color_g_;
        back_color_b_ = rhs.back_color_b_;
        fill_ = rhs.fill_;
        gltext_ = rhs.gltext_;
        hints_ = rhs.hints_;
        float_weight_ = rhs.float_weight_;
    }

    return *this;
}

void GLLabel::setGeometry(const TAK::Engine::Feature::Geometry2& geometry) NOTHROWS {
    geometry_.reset();
    TAK::Engine::Feature::Geometry_clone(geometry_, geometry);

    // need to invalidate the projected point
    mark_dirty_ = true;
}

const TAK::Engine::Feature::Geometry2* GLLabel::getGeometry() const NOTHROWS { return (geometry_.get()); }

void GLLabel::setAltitudeMode(const TAK::Engine::Feature::AltitudeMode altitude_mode) NOTHROWS { altitude_mode_ = altitude_mode; }

void GLLabel::setText(TAK::Engine::Port::String text) NOTHROWS {
    if (text.get() != nullptr) {
#if CALL_LOCALIZE
        Port::String localizedText;
        GLText2_localize(&localizedText, text.get());
        text_ = localizedText.get() ? localizedText.get() : "";
#else
        text_ = text.get();
#endif
    } else
        text_.clear();
}

void GLLabel::setTextFormat(const TextFormatParams* fmt) NOTHROWS {
    if (fmt != nullptr)
        gltext_ = GLText2_intern(*fmt);
    else
        gltext_ = nullptr;
}

void GLLabel::setVisible(const bool visible) NOTHROWS { visible_ = visible; }

void GLLabel::setAlwaysRender(const bool always_render) NOTHROWS { always_render_ = always_render; }

void GLLabel::setMaxDrawResolution(const double max_draw_resolution) NOTHROWS { max_draw_resolution_ = max_draw_resolution; }

void GLLabel::setAlignment(const TextAlignment alignment) NOTHROWS { 
    alignment_ = alignment; 
}

void GLLabel::setVerticalAlignment(const VerticalAlignment vertical_alignment) NOTHROWS { vertical_alignment_ = vertical_alignment; }

void GLLabel::setDesiredOffset(const Math::Point2<double>& desired_offset) NOTHROWS { desired_offset_ = desired_offset; }

void GLLabel::setColor(const int color) NOTHROWS {
    color_a_ = static_cast<float>((color >> 24) & 0xFF) / 255.0f;
    color_r_ = static_cast<float>((color >> 16) & 0xFF) / 255.0f;
    color_g_ = static_cast<float>((color >> 8) & 0xFF) / 255.0f;
    color_b_ = static_cast<float>((color >> 0) & 0xFF) / 255.0f;
}

void GLLabel::setBackColor(const int color) NOTHROWS {
    back_color_a_ = static_cast<float>((color >> 24) & 0xFF) / 255.0f;
    back_color_r_ = static_cast<float>((color >> 16) & 0xFF) / 255.0f;
    back_color_g_ = static_cast<float>((color >> 8) & 0xFF) / 255.0f;
    back_color_b_ = static_cast<float>((color >> 0) & 0xFF) / 255.0f;
}

void GLLabel::setFill(const bool fill) NOTHROWS { fill_ = fill; }

void GLLabel::getRotation(float &angle, bool &absolute) const NOTHROWS {
    angle = rotation_.angle_;
    absolute = rotation_.absolute_;
}

void GLLabel::setRotation(const float rotation, const bool absolute_rotation) NOTHROWS {
    rotation_.angle_ = rotation;
    rotation_.absolute_ = absolute_rotation;
    rotation_.explicit_ = true;
}

void GLLabel::setPriority(const Priority priority) NOTHROWS { priority_ = priority; }

bool GLLabel::shouldRenderAtResolution(const double draw_resolution) const NOTHROWS {
    if (!visible_) return false;
    if (max_draw_resolution_ != 0.0 && draw_resolution >= max_draw_resolution_ && !always_render_) return false;
    return true;
}

bool GLLabel::place(GLLabel::LabelPlacement &placement, const GLGlobeBase& view, const GLText2& gl_text, const std::vector<LabelPlacement>& label_rects, bool &rePlaced) NOTHROWS {

    rePlaced = false;

    const auto xpos = static_cast<float>(placement.anchor_xyz_.x);
    const auto ypos = static_cast<float>(placement.anchor_xyz_.y);

    placement.render_xyz_.x = xpos;
    placement.render_xyz_.y = ypos;

    const auto textDescent = gl_text.getTextFormat().getDescent();
    const auto textBaseline = gl_text.getTextFormat().getBaselineSpacing();

    float textWidth = 0.f;
    float textHeight = 0.f;
    if (!text_.empty()) {
        float offy = 0;
        float offtx = 0;
        float offz = 0;
        textWidth = std::min(labelSize.width, (float)(view.renderPass->right - 20));
        textHeight = labelSize.height;

        if (!TE_ISNAN(projected_size_)) {
            double availableWidth = projected_size_ / view.renderPass->drawMapResolution;
            if (availableWidth < textWidth) {
                placement.can_draw_ = false;
                return false;
            }
        }

        offtx = (float)desired_offset_.x;
        offy = (float)desired_offset_.y;
        offz = (float)desired_offset_.z;


#ifndef __ANDROID__
        if (offy != 0.0 && view.renderPass->drawTilt > 0.0) {
            offy *= (float)(1.0f + view.renderPass->drawTilt / 100.0f);
        }
#else
        if((hints_&AutoSurfaceOffsetAdjust) && altitude_mode_ == Feature::TEAM_ClampToGround) {
            const double sin_tilt = sin(view.renderPass->drawTilt/180.0*M_PI);
            offy += (float)(textHeight*sin_tilt*sin_tilt);
        }
#endif
        switch (vertical_alignment_) {
            case VerticalAlignment::TEVA_Top:
                offy += textDescent + textHeight;
                break;
            case VerticalAlignment::TEVA_Middle:
                offy += ((textDescent + textHeight) / 2.0f);
                break;
            case VerticalAlignment::TEVA_Bottom:
                break;
        }

        // initial placement position
        placement.render_xyz_.x += (offtx - textWidth / 2.0);
        placement.render_xyz_.y += static_cast<double>(offy) - static_cast<double>(textBaseline);
        placement.render_xyz_.z += offz;
    }

    const float alignOffX = static_cast<float>(placement.render_xyz_.x) - xpos;
    const float alignOffY = static_cast<float>(placement.render_xyz_.y) - ypos;

    placement.recompute(view.renderPass->drawRotation, labelSize.width, labelSize.height);

    bool overlaps = false;
    int replace_idx = -1;
    int itr_idx = 0;
    for (auto itr = label_rects.begin(); itr != label_rects.end(); itr++) {
        overlaps = IntersectRotatedRectangle(placement.rotatedRectangle, (*itr).rotatedRectangle);
        if (overlaps && !rePlaced) {
            replace_idx = itr_idx;
            rePlaced = true;
            double leftShift = abs((placement.render_xyz_.x + textWidth) - itr->render_xyz_.x);
            double rightShift = abs(placement.render_xyz_.x - (itr->render_xyz_.x + textWidth));
            if (rightShift < leftShift && rightShift < (textWidth / 2.0f)) {
                // shift right of compared label rect
                placement.render_xyz_.x = itr->render_xyz_.x + textWidth + LABEL_PADDING_X;
            } else if (leftShift < (textWidth / 2.0f)) {
                // shift left of compared label rect
                placement.render_xyz_.x = itr->render_xyz_.x - textWidth - LABEL_PADDING_X;
            } else {
                break;
            }
            placement.recompute(view.renderPass->drawRotation, labelSize.width, labelSize.height);
            overlaps = IntersectRotatedRectangle(placement.rotatedRectangle, (*itr).rotatedRectangle);
        }

        itr_idx++;
        if (overlaps) break;
    }

    if (!overlaps && rePlaced) {
        for (auto reverse_itr = (label_rects.rbegin() + replace_idx); reverse_itr != label_rects.rbegin(); --reverse_itr) {
            overlaps = IntersectRotatedRectangle(placement.rotatedRectangle, (*reverse_itr).rotatedRectangle);

            if (overlaps) break;
        }
    }

    placement.can_draw_ &= !overlaps;
    return placement.can_draw_;
}

void GLLabel::draw(const GLGlobeBase& view, GLText2& gl_text) NOTHROWS {
    if (!text_.empty()) {
        const char* text = text_.c_str();

        for(const auto &a : transformed_anchor_) {
            try {
                GLES20FixedPipeline::getInstance()->glPushMatrix();

                const auto xpos = static_cast<float>(a.anchor_xyz_.x);
                const auto ypos = static_cast<float>(a.anchor_xyz_.y);
                const auto zpos = static_cast<float>(a.anchor_xyz_.z);
                GLES20FixedPipeline::getInstance()->glTranslatef(xpos, ypos, zpos);
                float rotate = static_cast<float>(a.rotation_.angle_);
                if (a.rotation_.absolute_) {
                    rotate = (float)fmod(rotate + view.renderPass->drawRotation, 360.0);
                }
                GLES20FixedPipeline::getInstance()->glRotatef(rotate, 0.0f, 0.0f, 1.0f);
                GLES20FixedPipeline::getInstance()->glTranslatef((float)a.anchor_xyz_.x - xpos, (float)a.anchor_xyz_.y - ypos, 0.0f - zpos);

                if(hints_&GLLabel::ScrollingText && (labelSize.width > (MAX_TEXT_WIDTH*GLMapRenderGlobals_getRelativeDisplayDensity()))) {
                    GLES20FixedPipeline::getInstance()->glPushMatrix();
                    GLES20FixedPipeline::getInstance()->glTranslatef(marquee_.offset_, 0.f, 0.f);
                    gl_text.draw(text,
                            color_r_,
                            color_g_,
                            color_b_,
                            color_a_,
                            -marquee_.offset_, -marquee_.offset_ + labelSize.width);
                    GLES20FixedPipeline::getInstance()->glPopMatrix();

                    marqueeAnimate(view.animationDelta);
                } else {
                    gl_text.draw(text, color_r_, color_g_, color_b_, color_a_);
                }

                GLES20FixedPipeline::getInstance()->glPopMatrix();
            } catch (std::out_of_range&) {
                // ignored
            }
        }
    }
}

void GLLabel::batch(const GLGlobeBase& view, GLText2& gl_text, GLRenderBatch2& batch, int render_pass) NOTHROWS
{
    if (text_.empty())
        return;
    for(const auto &a : transformed_anchor_)
        if(a.can_draw_) this->batch(view, gl_text, batch, a, render_pass);
}
void GLLabel::batch(const GLGlobeBase& view, GLText2& gl_text, GLRenderBatch2& batch, const LabelPlacement &anchor, int render_pass) NOTHROWS
{
    did_animate_ = false;
    if (!text_.empty()) {
        const char* text = text_.c_str();
#if 0
        float zpos = (float)std::max(transformedAnchor.z, 0.0);

        if (view.drawTilt > 0.0) {
            zpos -= 1;
        }
#else
        auto zpos = (float)anchor.anchor_xyz_.z;
#endif

        float alpha = color_a_;
        float back_alpha = back_color_a_;

        if(render_pass & GLGlobeBase::XRay)
        {
            alpha = std::min(alpha, .4f);
            back_alpha = std::min(back_alpha, .4f);
        }

        double rotate = anchor.rotation_.angle_;
        if (anchor.rotation_.absolute_) {
            rotate = fmod(rotate + view.renderPass->drawRotation, 360.0);
        }

        size_t lineCount = gl_text.getLineCount(text);
        float lineHeight = gl_text.getTextFormat().getCharHeight();
        float lineWidth = textSize.width;
        float ninePatchYShift = ((lineCount - 1) * lineHeight);
        float ninePatchHeightShift = 0.f;

        // may find that Windows/other plats needs this too. If so, incorporate.
#if __ANDROID__
        // shift down descent minus some padding to to look centered in background
        ninePatchYShift += std::max(0.f, gl_text.getTextFormat().getDescent() - 2.f);

        // involve descent in background ninepatch height (correct for multiline labels)
        ninePatchHeightShift = (lineCount - 1) * (gl_text.getTextFormat().getDescent() - 2.f);
#endif
        // adjust the transforms and render location if rotation should be applied
        Math::Point2<double> labelCorner(anchor.render_xyz_);
        if (rotate) {
            batch.pushMatrix(GL_MODELVIEW);
            float mx[16];
            GLES20FixedPipeline::getInstance()->readMatrix(GLES20FixedPipeline::MatrixMode::MM_GL_MODELVIEW, mx);
            Matrix2 matrix(mx[0], mx[4], mx[8], mx[12],
                           mx[1], mx[5], mx[9], mx[13],
                           mx[2], mx[6],mx[10], mx[14],
                           mx[3], mx[7], mx[11], mx[15]);
            matrix.rotate((360 - rotate) / ONE_EIGHTY_OVER_PI, 0, 0, 1);

            double v[16u];
            matrix.get(v, Matrix2::ROW_MAJOR);
            for(std::size_t i = 0u; i < 16u; i++)
                mx[i] = (float)v[i];
            batch.setMatrix(GL_MODELVIEW, mx);

            const auto xpos = static_cast<float>(anchor.anchor_xyz_.x);
            const auto ypos = static_cast<float>(anchor.anchor_xyz_.y);
            matrix.transform(&labelCorner, Math::Point2<double>(xpos, ypos));

            labelCorner.x += anchor.render_xyz_.x - xpos;
            labelCorner.y += anchor.render_xyz_.y - ypos;
        }

        if (fill_) {
            getSmallNinePatch(view.context);
            if (small_nine_patch_ != nullptr) {
                small_nine_patch_->batch(batch, (float)labelCorner.x - 4.0f, (float)labelCorner.y - ninePatchYShift - 4.0f,
                                      zpos, (float)labelSize.width + 8.0f, (float)labelSize.height + ninePatchHeightShift, back_color_r_, back_color_g_,
                                      back_color_b_, back_alpha);
            }
        }

        const float maxTextWidth = MAX_TEXT_WIDTH*GLMapRenderGlobals_getRelativeDisplayDensity();
        if((hints_&GLLabel::ScrollingText) && (textSize.width > maxTextWidth)) {
            // apply scrolling effect
            float scissorX0 = 0.0f;
            float scissorX1 = std::numeric_limits<float>::max();

            scissorX0 = -marquee_.offset_;
            scissorX1 = -marquee_.offset_ + maxTextWidth;
            labelCorner.x += marquee_.offset_;

            gl_text.batch(batch,
                    text,
                    (float)labelCorner.x,
                    (float)labelCorner.y + gl_text.getTextFormat().getBaselineSpacing(),
                    zpos,
                    color_r_,
                    color_g_,
                    color_b_,
                    color_a_,
                    scissorX0, scissorX1);

            marqueeAnimate(view.animationDelta);
        } else
        if (lineCount == 1 || alignment_ == TextAlignment::TETA_Left && lineWidth <= labelSize.width) {
            gl_text.batch(batch, text, (float)labelCorner.x, (float)labelCorner.y, zpos, color_r_, color_g_, color_b_, alpha);
        } else {
            size_t pos = 0;
            std::string token;
            std::string strText = text_;
            float offx = 0.0;
            float offy = 0.0;
            while ((pos = strText.find("\n")) != std::string::npos) {
                token = strText.substr(0, pos);
                const char* token_text = token.c_str();
                float tokenLineWidth = gl_text.getTextFormat().getStringWidth(token_text);
                if (tokenLineWidth > labelSize.width) {
                    size_t numChars = (size_t)((labelSize.width / tokenLineWidth) * token.length()) - 2;
                    token = token.substr(0, numChars);
                    token += "...";
                    token_text = token.c_str();
                    tokenLineWidth = gl_text.getTextFormat().getStringWidth(token_text);
                }
                switch (alignment_) {
                    case TextAlignment::TETA_Left:
                        offx = 0;
                        break;
                    case TextAlignment::TETA_Center:
                        offx = ((float)labelSize.width - tokenLineWidth) / 2.0f;
                        break;
                    case TextAlignment::TETA_Right:
                        offx = (float)labelSize.width - tokenLineWidth;
                        break;
                }
                gl_text.batch(batch, token.c_str(), (float)labelCorner.x + offx, (float)labelCorner.y - offy, zpos, color_r_, color_g_,
                             color_b_, alpha);
                offy += gl_text.getTextFormat().getStringHeight(token.c_str());
                strText.erase(0, pos + 1);
            }

            text = strText.c_str();
            lineWidth = gl_text.getTextFormat().getStringWidth(text);
            if (lineWidth > labelSize.width) {
                size_t numChars = (size_t)((labelSize.width / lineWidth) * strText.length()) - 1;
                strText = strText.substr(0, numChars);
                strText += "...";
                text = strText.c_str();
                lineWidth = gl_text.getTextFormat().getStringWidth(text);
            }
            switch (alignment_) {
                case TextAlignment::TETA_Left:
                    offx = 0;
                    break;
                case TextAlignment::TETA_Center:
                    offx = ((float)labelSize.width - lineWidth) / 2.0f;
                    break;
                case TextAlignment::TETA_Right:
                    offx = (float)labelSize.width - lineWidth;
                    break;
            }
            gl_text.batch(batch, text, (float)labelCorner.x + offx, (float)labelCorner.y - offy, zpos, color_r_, color_g_, color_b_,
                         alpha);
        }
        // reset the transforms if rotated
        if(rotate)
            batch.popMatrix(GL_MODELVIEW);
    }
}

atakmap::renderer::GLNinePatch* GLLabel::getSmallNinePatch(TAK::Engine::Core::RenderContext &surface) NOTHROWS {
    if (this->small_nine_patch_ == nullptr) {
        GLTextureAtlas2* atlas;
        GLMapRenderGlobals_getTextureAtlas2(&atlas, surface);
        small_nine_patch_ = new GLNinePatch(atlas, GLNinePatch::Size::SMALL, 16, 16, 5, 5, 10, 10);
    }
    return small_nine_patch_;
}

void GLLabel::validate(const GLGlobeBase& view, const GLText2 &gl_text) NOTHROWS
{
    if (!text_.empty()) {
        const char* text = text_.c_str();
        textSize.width = gl_text.getTextFormat().getStringWidth(text);
        textSize.height = gl_text.getTextFormat().getStringHeight(text);
    } else {
        textSize.width = 0.f;
        textSize.height = 0.f;
    }

    labelSize.width = (hints_&GLLabel::ScrollingText) ?
            std::min(textSize.width, MAX_TEXT_WIDTH*GLMapRenderGlobals_getRelativeDisplayDensity()) :
            textSize.width;
    labelSize.height = textSize.height;

    if (!mark_dirty_ && draw_version_ == view.drawVersion) return;
    if (geometry_.get() == nullptr) return;

    mark_dirty_ = false;
    draw_version_ = view.drawVersion;

    switch (geometry_->getClass()) {
        case Feature::TEGC_Point: {
            const auto point = dynamic_cast<const Feature::Point2&>(*geometry_.get());
            GeoPoint2 textLoc(point.y, point.x, point.z, AltitudeReference::HAE);
            // if rotation was not explicitly specified, make relative
            if (!rotation_.explicit_)
                rotation_.absolute_ = false;

            textLoc = adjustSurfaceLabelAnchor(view, textLoc, altitude_mode_);

            transformed_anchor_.clear();
            transformed_anchor_.push_back(LabelPlacement());

            Point2<double> pos_projected;
            view.renderPass->scene.projection->forward(&pos_projected, textLoc);
            view.renderPass->scene.forwardTransform.transform(&transformed_anchor_[0].anchor_xyz_, pos_projected);
            if (rotation_.explicit_) {
                transformed_anchor_[0].rotation_.angle_ = rotation_.angle_;
                transformed_anchor_[0].rotation_.absolute_ = rotation_.absolute_;
            }
        } break;
        case Feature::TEGC_LineString: {
            const auto lineString = dynamic_cast<const Feature::LineString2&>(*geometry_.get());

            size_t numPoints = lineString.getNumPoints();
            
            {
                GeoPoint2 sp;
                getRenderPoint(&sp, view, lineString, 0u, altitude_mode_);
                GeoPoint2 ep;
                getRenderPoint(&ep, view, lineString, 1u, altitude_mode_);

                Point2<float> spxy, epxy;
                view.renderPass->scene.forward(&spxy, sp);
                view.renderPass->scene.forward(&epxy, ep);

                // adjust points on other side of near plane
                intersectWithNearPlane(spxy, epxy, view, sp, ep);

                // LLA location of text anchor
                GeoPoint2 textLoc = GeoPoint2_midpoint(sp, ep, true);

                // forms rotated axis for text baseline
                Point2<float> axisa(spxy);
                Point2<float> axisb(epxy);
                FindIntersection<float>(axisa, axisb, static_cast<float>(view.renderPass->left), static_cast<float>(view.renderPass->bottom), 
                    static_cast<float>(view.renderPass->right), static_cast<float>(view.renderPass->top));

                if(altitude_mode_ == Feature::TEAM_ClampToGround && numPoints == 2u) {
                    transformed_anchor_.clear();

                    // ensure the screenspace size of the line is sufficient for render
                    if(std::max(labelSize.width, labelSize.height) <=
                                Vector2_length(Point2<double>(axisa.x-axisb.x, axisa.y-axisb.y))) {

                        textLoc = closestSurfacePointToFocus(view, (float)(axisa.x+axisb.x)/2.f, (float)(axisa.y+axisb.y)/2.f, sp, ep);

                        // use terrain mesh elevation
                        view.getTerrainMeshElevation(&textLoc.altitude, textLoc.latitude, textLoc.longitude);

                        if(!rotation_.explicit_) {
                           const double weight = GeoPoint2_distance(sp, textLoc, true) /
                                    GeoPoint2_distance(sp, ep, true);
                            const double startBrg = GeoPoint2_bearing(textLoc, sp, true);
                            const double endBrg = GeoPoint2_bearing(textLoc, ep, true);

#define SS_ROTATION_AXIS_RADIUS 16.0

                            const double rotationAxisRadius = (!!labelSize.width) ?
                                    labelSize.width / 2.0 : SS_ROTATION_AXIS_RADIUS;

                            // recompute `points` as small segment within screen bounds
                            // based on surface text location to ensure proper label
                            // rotation
                            GeoPoint2 rotAxisSP = GeoPoint2_pointAtDistance(textLoc,
                                                                              weight > 0.0 ? startBrg
                                                                                          : -endBrg,
                                                                              rotationAxisRadius *
                                                                              view.renderPass->scene.gsd,
                                                                              true);
                            view.getTerrainMeshElevation(&rotAxisSP.altitude, rotAxisSP.latitude,
                                                                  rotAxisSP.longitude);
                            view.renderPass->scene.forward(&axisa, rotAxisSP);

                            GeoPoint2 rotAxisEP = GeoPoint2_pointAtDistance(textLoc,
                                                                              weight < 1.0 ? endBrg
                                                                                          : -startBrg,
                                                                              rotationAxisRadius *
                                                                              view.renderPass->scene.gsd,
                                                                              true);
                            view.getTerrainMeshElevation(&rotAxisEP.altitude, rotAxisEP.latitude,
                                                                  rotAxisEP.longitude);
                            view.renderPass->scene.forward(&axisb, rotAxisEP);
                        }

                        textLoc = adjustSurfaceLabelAnchor(view, textLoc, altitude_mode_);

                        transformed_anchor_.push_back(LabelPlacement());

                        Point2<double> pos_projected;
                        view.renderPass->scene.projection->forward(&pos_projected, textLoc);
                        view.renderPass->scene.forwardTransform.transform(&transformed_anchor_.back().anchor_xyz_, pos_projected);
                    }
                } else if(altitude_mode_ == Feature::TEAM_ClampToGround) {
                    std::vector<FloatingAnchor> anchors;
                    Point2<double> midpointxy;
                    validateSurfaceFloatingLabel(anchors, &midpointxy, view, lineString, labelSize.width);
                    // NOTE: `validateSurfaceFloatingLabel` only returns those
                    //       labels with adequately sized screenspace geometry
                    transformed_anchor_.clear();
                    for(auto &a : anchors) {
                        textLoc = adjustSurfaceLabelAnchor(view, a.lla, altitude_mode_);

                        LabelPlacement placement;
                        Point2<double> pos_projected;
                        view.renderPass->scene.projection->forward(&pos_projected, a.lla);
                        view.renderPass->scene.forwardTransform.transform(&placement.anchor_xyz_, pos_projected);

                        if(rotation_.explicit_) {
                            placement.rotation_.angle_ = rotation_.angle_;
                            placement.rotation_.absolute_ = rotation_.absolute_;
                        } else {
                            placement.rotation_.angle_ =
                                    (float) (atan2(a.axis.a.y - a.axis.b.y, a.axis.a.x - a.axis.b.x)
                                             * 180.0 / M_PI);
                            if (placement.rotation_.angle_ > 90 || placement.rotation_.angle_ < -90)
                                placement.rotation_.angle_ += 180.0;
                            placement.rotation_.absolute_ = false;
                        }

                        if((hints_&DuplicateOnSplit) || transformed_anchor_.empty()) {
                            transformed_anchor_.push_back(placement);
                            axisa = a.axis.a;
                            axisb = a.axis.b;
                        } else if(placement.anchor_xyz_.z < 1.f &&
                                  DistanceSquared<double>(placement.anchor_xyz_.x, placement.anchor_xyz_.y, midpointxy.x, midpointxy.y) <
                                    DistanceSquared<double>(transformed_anchor_[0].anchor_xyz_.x, transformed_anchor_[0].anchor_xyz_.y, midpointxy.x, midpointxy.y)) {

                            // no duplication is occurring, output will be
                            // segment label closest to focus
                            transformed_anchor_[0] = placement;
                            axisa = a.axis.a;
                            axisb = a.axis.b;
                        }
                    }
                } else {
                    const Point2<double> midpointxy((axisa.x+axisb.x)/2.0, (axisa.y+axisb.y)/2.0);

                    transformed_anchor_.clear();
                    if(std::max(labelSize.width, labelSize.height) <=
                                Vector2_length(Point2<double>(axisa.x-axisb.x, axisa.y-axisb.y))) {

                        transformed_anchor_.push_back(LabelPlacement());
                        transformed_anchor_.back().anchor_xyz_ = midpointxy;
                        transformed_anchor_.back().anchor_xyz_.z = std::min(spxy.z, epxy.z);
                    }
                }

                // if the rotation is not explicit, compute from the screen-space rotation axis
                if(!rotation_.explicit_) {
                    rotation_.angle_ = (float) (atan2(axisa.y - axisb.y, axisa.x - axisb.x)
                                          * 180.0 / M_PI);
                    if (rotation_.angle_ > 90 || rotation_.angle_ < -90) rotation_.angle_ += 180.0;
                    rotation_.absolute_ = false;
                }
                if(transformed_anchor_.size() == 1u) {
                    transformed_anchor_.back().rotation_.angle_ = rotation_.angle_;
                    transformed_anchor_.back().rotation_.absolute_ = rotation_.absolute_;
                }

                projected_size_ = NAN;
            }

        } break;
            // case TEGC_Polygon:
            //	value = Geometry2Ptr(new Polygon2(static_cast<const
            // Polygon2&>(geometry)), Memory_deleter_const<Geometry2>);
            // break;
            // case TEGC_GeometryCollection:
            //	value = Geometry2Ptr(new GeometryCollection2(static_cast<const
            // GeometryCollection2&>(geometry)),
            // Memory_deleter_const<Geometry2>); 	break;
    }

    // sync render locations with anchor locations; to be updated via subsequent placement
    for(auto &a : transformed_anchor_)
        a.render_xyz_ = a.anchor_xyz_;
}
void GLLabel::setHints(const unsigned int hints) NOTHROWS
{
    hints_ = hints;
}
void GLLabel::setPlacementInsets(const float left, const float right, const float bottom, const float top) NOTHROWS
{
    insets_.left_ = left;
    insets_.right_ = right;
    insets_.bottom_ = bottom;
    insets_.top_ = top;
}
TAKErr GLLabel::setFloatWeight(const float weight) NOTHROWS
{
    if(weight < 0.f || weight > 1.f)
        return TE_InvalidArg;
    float_weight_ = weight;
    return TE_Ok;
}
void GLLabel::marqueeAnimate(const int64_t animDelta) NOTHROWS
{
    const float maxTextWidth = MAX_TEXT_WIDTH*GLMapRenderGlobals_getRelativeDisplayDensity();
    float textEndX = marquee_.offset_ + textSize.width;
    if (marquee_.timer_ <= 0) {
        // return to neutral scroll and wait 3 seconds
        if (textEndX <= maxTextWidth) {
            marquee_.timer_ = 3000LL;
            marquee_.offset_ = 0.f;
        } else {
            // animate at 10 pixels per second
            marquee_.offset_ -= (animDelta * 0.02f);
            if (marquee_.offset_ + textSize.width <= maxTextWidth) {
                marquee_.offset_ = maxTextWidth - textSize.width;
                marquee_.timer_ = 2000LL;
            }
        }
    } else {
        marquee_.timer_ -= animDelta;
    }

    did_animate_ = true;
}

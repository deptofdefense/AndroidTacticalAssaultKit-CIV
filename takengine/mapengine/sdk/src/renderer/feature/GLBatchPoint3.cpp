#include "renderer/GL.h"

#include "renderer/feature/GLBatchPoint3.h"

#include "core/GeoPoint.h"
#include "feature/Style.h"
#include "feature/Point.h"
#include "feature/Point2.h"
#include "math/Point.h"
#include "renderer/GLTexture2.h"
#include "renderer/GLTextureAtlas.h"
#include "renderer/AsyncBitmapLoader.h"
#include "renderer/GLES20FixedPipeline.h"
#include "renderer/GLNinePatch.h"
#include "renderer/GLText2.h"
#include "renderer/core/GLMapRenderGlobals.h"
#include "thread/Lock.h"
#include "util/AttributeSet.h"
#include "util/ConfigOptions.h"
#include "util/IO.h"
#include "util/MemBuffer.h"
#include "util/Memory.h"


using namespace TAK::Engine::Renderer::Feature;

using namespace TAK::Engine::Core;
using namespace TAK::Engine::Feature;
using namespace TAK::Engine::Math;
using namespace TAK::Engine::Renderer;
using namespace TAK::Engine::Renderer::Core;
using namespace TAK::Engine::Thread;
using namespace TAK::Engine::Util;

using namespace atakmap::util;

using namespace atakmap::feature;

using namespace atakmap::renderer;

namespace
{
    GLText2 *defaultText(nullptr);

    float computeLabelAlpha(const GLMapView2 &view, const int64_t labelFadeTimer) NOTHROWS;
}

GLNinePatch *GLBatchPoint3::smallNinePatch = nullptr;
float GLBatchPoint3::iconAtlasDensity = atakmap::core::AtakMapView::DENSITY;
const double GLBatchPoint3::defaultLabelRenderScale = (1.0 / 250000.0);
GLBatchPoint3::IconLoadersMap GLBatchPoint3::iconLoaders;
Mutex GLBatchPoint3::staticMutex;
float GLBatchPoint3::defaultFontSize = 0;

GLBatchPoint3::GLBatchPoint3(RenderContext &surface) :
    GLBatchGeometry3(surface, 0),
    latitude(0),
    longitude(0),
    screen_x(0),
    screen_y(0),
    altitude(NAN),
    posProjectedEl(NAN),
    posProjectedSrid(-1),
    surfaceProjectedSrid(-1),
    color(0xFFFFFFFF),
    colorR(1),
    colorG(1),
    colorB(1),
    colorA(1),
    iconRotation(0.0),
    absoluteIconRotation(false),
    rotatedLabels(false),
    labelFadeTimer(-1LL),
    textureKey(0LL),
    textureId(0),
    textureIndex(0),
    texCoords(nullptr),
    verts(nullptr),
    iconAtlas(nullptr),
    iconUri(nullptr),
    iconDirty(false),
    terrainVersion(0),
    labelId(GLLabelManager::NO_ID),
    iconLoaderUri(nullptr),
    iconOffsetX(0.0),
    iconOffsetY(0.0)
{
    GLMapRenderGlobals_getIconAtlas2(&this->iconAtlas, surface);
}

GLBatchPoint3::~GLBatchPoint3()
{
    this->stop();
}

int64_t GLBatchPoint3::getTextureKey() const { return textureKey; }

TAKErr GLBatchPoint3::init(const int64_t feature_id, const char *name_val, TAK::Engine::Feature::GeometryPtr_const &&geom, const TAK::Engine::Feature::AltitudeMode altitude_mode, const double extrude_val, const std::shared_ptr<const atakmap::feature::Style> &style) NOTHROWS
{
    this->featureId = feature_id;
    this->name = name_val;
    this->altitudeMode = altitude_mode;
    this->extrude = extrude_val;
    if (geom.get())
        GLBatchGeometry3::setGeometry(*geom);
    if (style.get())
        setStyleImpl(style.get());
    return TE_Ok;
}

TAKErr GLBatchPoint3::init(const int64_t feature_id, const char *name_val, BlobPtr &&geomBlob, const TAK::Engine::Feature::AltitudeMode altitude_mode, const double extrude_val, const int type, const int lod_val, const std::shared_ptr<const atakmap::feature::Style> &style) NOTHROWS
{
    this->featureId = feature_id;
    this->name = name_val;
    this->altitudeMode = altitude_mode;
    this->extrude = extrude_val;
    if (geomBlob.get())
        GLBatchGeometry3::setGeometry(std::move(geomBlob), type, lod_val);
    if (style.get())
        setStyleImpl(style.get());
    return TE_Ok;
}

TAKErr GLBatchPoint3::setIcon(const char *uri, const int icon_color) NOTHROWS
{
    // update the render color
    this->color = icon_color;
    this->colorR = (float)((this->color >> 16) & 0xff) / (float)255;
    this->colorG = (float)((this->color >> 8) & 0xff) / (float)255;
    this->colorB = (float)((this->color) & 0xff) / (float)255;
    this->colorA = (float)((this->color >> 24) & 0xff) / (float)255;

    Port::String icon_uri(uri);
    if (uri != nullptr && (this->iconUri == icon_uri || (this->iconUri == nullptr && strlen(icon_uri) == 0))) {
        return TE_Ok;
    }

    // the icon URI has changed -- cancel the current load and mark dirty
    if (this->iconLoader.get()) {
        this->iconLoader.reset();
        dereferenceIconLoader(this->iconUri);
    }

    this->iconUri = icon_uri;
    this->iconDirty = true;

    return TE_Ok;
}

TAKErr GLBatchPoint3::checkIcon(RenderContext &render_context) NOTHROWS
{

    if (this->textureKey == 0LL || this->iconDirty) {
        getOrFetchIcon(render_context, *this);
    }

    return TE_Ok;
}

static bool SETTING_displayLabels = true;

TAKErr GLBatchPoint3::getIconSize(size_t *width, size_t *height) const NOTHROWS {
    
    TAKErr code;
    std::size_t iconWi;
    code = iconAtlas->getImageWidth(&iconWi, this->textureKey);
    std::size_t iconHi;
    code = iconAtlas->getImageHeight(&iconHi, this->textureKey);
    
    if (width) *width = iconWi;
    if (height) *height = iconHi;
    
    return code;
}

void GLBatchPoint3::draw(const GLMapView2 &ortho, const int render_pass) NOTHROWS
{
    if (!(render_pass&GLMapView2::Sprites))
        return;

    this->validateProjectedLocation(ortho);

    Math::Point2<double> scratchPoint;
    ortho.scene.forwardTransform.transform(&scratchPoint, posProjected);
    auto xpos = (float)scratchPoint.x + iconOffsetX;
    auto ypos = (float)scratchPoint.y - iconOffsetY; // GL LL origin
    auto zpos = (float)scratchPoint.z;
    this->screen_x = scratchPoint.x;
    this->screen_y = scratchPoint.y;

    // if tilted, draw a line segment from the center of the point into the
    // earth's surface
    if (extrude < 0.0 && altitudeMode !=TEAM_ClampToGround && ortho.drawTilt > 0.0) {
        ortho.scene.forwardTransform.transform(&scratchPoint, surfaceProjected);
        auto xsurface = (float)scratchPoint.x + iconOffsetX;
        auto ysurface = (float)scratchPoint.y - iconOffsetY; // GL LL origin
        auto zsurface = (float)scratchPoint.z;

        float line[6];
        line[0] = xpos;
        line[1] = ypos;
        line[2] = zpos;
        line[3] = xsurface;
        line[4] = ysurface;
        line[5] = zsurface;

        glLineWidth(2.0);
        GLES20FixedPipeline::getInstance()->glColor4f(this->colorR, this->colorG, this->colorB, this->colorA);
        GLES20FixedPipeline::getInstance()->glEnableClientState(GLES20FixedPipeline::ClientState::CS_GL_VERTEX_ARRAY);
        GLES20FixedPipeline::getInstance()->glVertexPointer(3, GL_FLOAT, 0, line);
        GLES20FixedPipeline::getInstance()->glDrawArrays(GL_LINES, 0, 2);
        GLES20FixedPipeline::getInstance()->glDisableClientState(GLES20FixedPipeline::ClientState::CS_GL_VERTEX_ARRAY);
    }

    try {
        GLES20FixedPipeline::getInstance()->glPushMatrix();
        GLES20FixedPipeline::getInstance()->glTranslatef(xpos, ypos, zpos);

        if (this->iconUri && (this->textureKey == 0LL || this->iconDirty)) {
            this->checkIcon(this->surface);
        }

        if (this->textureKey != 0LL) {
            glEnable(GL_BLEND);
            glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

            GLES20FixedPipeline::getInstance()->glEnableClientState(GLES20FixedPipeline::ClientState::CS_GL_VERTEX_ARRAY);
            GLES20FixedPipeline::getInstance()->glEnableClientState(GLES20FixedPipeline::ClientState::CS_GL_TEXTURE_COORD_ARRAY);

            if (!this->verts.get()) {
                TAKErr code;
                this->texCoords.reset(new float[4 * 2]);  //= gcnew array<float>(4*2);
                std::size_t iconXi;
                code = iconAtlas->getImageTextureOffsetX(&iconXi, this->textureKey);
                std::size_t iconYi;
                code = iconAtlas->getImageTextureOffsetY(&iconYi, this->textureKey);
                auto textureSize = static_cast<float>(iconAtlas->getTextureSize());
                std::size_t iconWi;
                code = iconAtlas->getImageWidth(&iconWi, this->textureKey);
                std::size_t iconHi;
                code = iconAtlas->getImageHeight(&iconHi, this->textureKey);

                auto iconX = static_cast<float>(iconXi);
                auto iconY = static_cast<float>(iconYi);
                auto iconW = static_cast<float>(iconWi);
                auto iconH = static_cast<float>(iconHi);

                auto densityMultiplier = static_cast<float>(1.0f / ortho.pixelDensity);
                float iconWc = iconWi * densityMultiplier;
                float iconHc = iconHi * densityMultiplier;

                this->texCoords[0] = iconX / textureSize;
                this->texCoords[1] = (iconY + iconH - 1.0f) / textureSize;

                this->texCoords[2] = (iconX + iconW - 1.0f) / textureSize;
                this->texCoords[3] = (iconY + iconH - 1.0f) / textureSize;

                this->texCoords[4] = (iconX + iconW - 1.0f) / textureSize;
                this->texCoords[5] = iconY / textureSize;

                this->texCoords[6] = iconX / textureSize;
                this->texCoords[7] = iconY / textureSize;

                this->verts.reset(new float[4 * 2]);

                this->verts[0] = -iconWc / 2.f;
                this->verts[1] = -iconHc / 2.f;

                this->verts[2] = iconWc / 2.f;
                this->verts[3] = -iconHc / 2.f;

                this->verts[4] = iconWc / 2.f;
                this->verts[5] = iconHc / 2.f;

                this->verts[6] = -iconWc / 2.f;
                this->verts[7] = iconHc / 2.f;
            }

            GLES20FixedPipeline::getInstance()->glPushMatrix();
            float icon_rotation = this->iconRotation;
            // if icon rotation is absolute we want to rotate the icon with the map
            if (this->absoluteIconRotation) {
                icon_rotation = static_cast<float>(fmod(icon_rotation + ortho.drawRotation, 360.0));
            }
            GLES20FixedPipeline::getInstance()->glRotatef(icon_rotation, 0.0f, 0.0f, 1.0f);

            GLES20FixedPipeline::getInstance()->glVertexPointer(2, GL_FLOAT, 0, this->verts.get());

            GLES20FixedPipeline::getInstance()->glTexCoordPointer(2, GL_FLOAT, 0, this->texCoords.get());

            glBindTexture(GL_TEXTURE_2D, this->textureId);

            GLES20FixedPipeline::getInstance()->glColor4f(this->colorR, this->colorG, this->colorB, this->colorA);

            GLES20FixedPipeline::getInstance()->glDrawArrays(GL_TRIANGLE_FAN, 0, 4);

            GLES20FixedPipeline::getInstance()->glDisableClientState(GLES20FixedPipeline::ClientState::CS_GL_TEXTURE_COORD_ARRAY);
            GLES20FixedPipeline::getInstance()->glDisableClientState(GLES20FixedPipeline::ClientState::CS_GL_VERTEX_ARRAY);

            glDisable(GL_BLEND);

            GLES20FixedPipeline::getInstance()->glPopMatrix();
        }

#if 0
        // if the displayLables preference is checked display the text if
        // the marker requested to always have the text show or if the scale is zoomed in enough
        if (!this->iconUri || (/*SETTING_displayLabels*/ true && ortho.drawMapScale >= defaultLabelRenderScale)) {
            const char *text = this->name;
            if (text != nullptr && strlen(text) > 0) {
                if (defaultText == nullptr) {
                    TAKErr code;
                    TextFormat2Ptr fmt(nullptr, nullptr);
                    if (!defaultFontSize) {
                        Port::String opt;
                        code = ConfigOptions_getOption(opt, "default-font-size");
                        if (code == TE_Ok)
                            defaultFontSize = static_cast<float>(atof(opt));
                        else
                            defaultFontSize = 14.0f;
                    }
                    code = TextFormat2_createDefaultSystemTextFormat(fmt, defaultFontSize);
                    if (code == TE_Ok) {
                        std::shared_ptr<TextFormat2> sharedFmt = std::move(fmt);
                        defaultText = GLText2_intern(sharedFmt);
                    }
                }

                if (defaultText) {
                    float offy = 0;
                    float offtx = 0;
                    float textWidth = defaultText->getTextFormat().getStringWidth(text);
                    float textHeight = defaultText->getTextFormat().getStringHeight(text);
                    float textDescent = defaultText->getTextFormat().getDescent();
                    float textBaseline = defaultText->getTextFormat().getBaselineSpacing();
                    if (this->textureKey != 0LL) {
                        std::size_t offyi;
                        TAKErr code = iconAtlas->getImageHeight(&offyi, this->textureKey);
                        offy = (float)offyi / (float)-2.0;
                        offtx = 0.0f;
                    } else {
                        offy = textDescent + textHeight / 2.0f;
                    }

                    GLES20FixedPipeline::getInstance()->glPushMatrix();
                    float label_rotation = this->labelRotation;
                    if (!this->absoluteLabelRotation) {
                        label_rotation = static_cast<float>(fmod(label_rotation + ortho.drawRotation, 360.0));
                    }
                    GLES20FixedPipeline::getInstance()->glRotatef(label_rotation, 0.0f, 0.0f, 1.0f);

                    GLES20FixedPipeline::getInstance()->glTranslatef(offtx - textWidth / 2.0f, offy - textBaseline, 0.0f);

                    const float labelAlpha = computeLabelAlpha(ortho, labelFadeTimer);

                    if (labelBackground) {
                        GLNinePatch *small_nine_patch = this->getSmallNinePatch();
                        if (small_nine_patch != nullptr) {
                            GLES20FixedPipeline::getInstance()->glColor4f(0.0f, 0.0f, 0.0f, 0.6f * labelAlpha);
                            GLES20FixedPipeline::getInstance()->glPushMatrix();
                            GLES20FixedPipeline::getInstance()->glTranslatef(-4.0f, (textBaseline - textHeight) - textDescent, 0.0f);
                            small_nine_patch->draw(textWidth + 8.0f, textHeight);
                            GLES20FixedPipeline::getInstance()->glPopMatrix();
                        }
                    }

                    defaultText->draw(text, 1.0f, 1.0f, 1.0f, labelAlpha);

                    GLES20FixedPipeline::getInstance()->glPopMatrix();
                }
            }
        }
#endif

        GLES20FixedPipeline::getInstance()->glPopMatrix();
    }
    catch (std::out_of_range& e) {
        Logger::log(Logger::Error, "GLBatchPoint3: Error drawing point %s", e.what());
    }
}

void GLBatchPoint3::release() NOTHROWS {
    this->texCoords.reset();
    this->verts.reset();
    this->textureKey = 0LL;
    if (this->iconLoader.get())
    {
        this->iconLoader.reset();
        //    this->iconLoader = nullptr;
        dereferenceIconLoader(this->iconUri);
    }
    if (this->labelId != GLLabelManager::NO_ID) {
        GLLabelManager *labelManager;
        TAKErr code = GLMapRenderGlobals_getLabelManager(&labelManager, this->surface);
        if (code == TE_Ok && labelManager != nullptr) {
            labelManager->removeLabel(this->labelId);
        }
    }
}

TAKErr GLBatchPoint3::setGeometryImpl(BlobPtr &&blob, const int type) NOTHROWS
{
    TAKErr code;
    code = blob->readDouble(&this->longitude);
    TE_CHECKRETURN_CODE(code);
    code = blob->readDouble(&this->latitude);
    TE_CHECKRETURN_CODE(code);

    if ((type / 1000) % 1000) {
        code = blob->readDouble(&this->altitude);
        TE_CHECKRETURN_CODE(code);
    }

    this->posProjectedSrid = -1;
    this->surfaceProjectedSrid = -1;

    if (this->labelId != GLLabelManager::NO_ID) {
        GLLabelManager* labelManager;
        code = GLMapRenderGlobals_getLabelManager(&labelManager, this->surface);
        if (code == TE_Ok && labelManager != nullptr) {
            TAK::Engine::Feature::Point2 point(this->longitude, this->latitude, this->altitude);
            labelManager->setGeometry(this->labelId, point);
        }
    }

    return code;
}

TAKErr GLBatchPoint3::setAltitudeModeImpl(const TAK::Engine::Feature::AltitudeMode altitude_mode) NOTHROWS 
{
    TAKErr code(TE_Ok);

    if (this->labelId != GLLabelManager::NO_ID && this->altitudeMode != altitude_mode) {
        GLLabelManager *labelManager;
        code = GLMapRenderGlobals_getLabelManager(&labelManager, this->surface);
        if (code == TE_Ok && labelManager != nullptr) {
            labelManager->setAltitudeMode(this->labelId, altitude_mode);
        }
    }

    return GLBatchGeometry3::setAltitudeModeImpl(altitude_mode);
}

TAKErr GLBatchPoint3::setNameImpl(const char* name_val) NOTHROWS
{
    if (this->labelId != GLLabelManager::NO_ID && this->name != name_val)
    {
        GLLabelManager* labelManager;
        TAKErr code = GLMapRenderGlobals_getLabelManager(&labelManager, this->surface);
        if (code == TE_Ok && labelManager != nullptr)
        {
            labelManager->setText(this->labelId, name_val);
        }
    }
    return GLBatchGeometry3::setNameImpl(name_val);
}

TAKErr GLBatchPoint3::setStyle(TAK::Engine::Feature::StylePtr_const &&value) NOTHROWS
{
    return setStyle(std::shared_ptr<const atakmap::feature::Style>(std::move(value)));
}

TAKErr GLBatchPoint3::setStyle(const std::shared_ptr<const atakmap::feature::Style> &value) NOTHROWS
{
    return this->setStyleImpl(value.get());
}

TAKErr GLBatchPoint3::setStyleImpl(const atakmap::feature::Style *style) NOTHROWS
{
    int iconColor = -1;
    const char *icon_uri = nullptr;
    const IconPointStyle *iconStyle = nullptr;
    const BasicPointStyle *basicStyle = nullptr;
    const LabelPointStyle *labelStyle = nullptr;

    if (style->getClass() == TESC_CompositeStyle) {
        const auto *cs = static_cast<const CompositeStyle*>(style);
        iconStyle = static_cast<const IconPointStyle *>(cs->findStyle<IconPointStyle>().get());
        basicStyle = static_cast<const BasicPointStyle *>(cs->findStyle<BasicPointStyle>().get());
        labelStyle = static_cast<const LabelPointStyle *>(cs->findStyle<LabelPointStyle>().get());
    } else {
        if(style->getClass() == TESC_IconPointStyle)
            iconStyle = static_cast<const IconPointStyle *>(style);
        if(style->getClass() == TESC_BasicPointStyle)
            basicStyle = static_cast<const BasicPointStyle *>(style);
        if(style->getClass() == TESC_LabelPointStyle)
            labelStyle = static_cast<const LabelPointStyle *>(style);
    }

    if (iconStyle) {
        iconColor = iconStyle->getColor();
        icon_uri = iconStyle->getIconURI();
        this->iconRotation = iconStyle->getRotation();
        this->absoluteIconRotation = iconStyle->isRotationAbsolute();
        this->iconOffsetX = iconStyle->getOffsetX();
        this->iconOffsetY = iconStyle->getOffsetY();
    } else if (basicStyle) {
        iconColor = basicStyle->getColor();
    }
    labels.clear();
    rotatedLabels = false;
    if (labelStyle) {
#if _MSC_VER
        if (labelStyle->getText() && labelStyle->getText()[0])
#else
        if (labelStyle->getText())
#endif
            this->name = labelStyle->getText();

        TextFormatParams fmt(labelStyle->getFontFace(), labelStyle->getTextSize());
        fmt.bold = !!(labelStyle->getStyle() & atakmap::feature::LabelPointStyle::BOLD);
        fmt.italic = !!(labelStyle->getStyle() & atakmap::feature::LabelPointStyle::ITALIC);
        fmt.underline = !!(labelStyle->getStyle() & atakmap::feature::LabelPointStyle::UNDERLINE);
        fmt.strikethrough = !!(labelStyle->getStyle() & atakmap::feature::LabelPointStyle::STRIKETHROUGH);

        labels.push_back(GLLabel(
            fmt,
            Geometry2Ptr_const(new TAK::Engine::Feature::Point2(longitude, latitude, altitude), Memory_deleter_const<Geometry2, TAK::Engine::Feature::Point2>),
            this->name,
            TAK::Engine::Math::Point2<double>(labelStyle->getOffsetX(), labelStyle->getOffsetY()),
            labelStyle->getLabelMinRenderResolution(),
            TETA_Center, TEVA_Top,
            labelStyle->getTextColor(),
            labelStyle->getBackgroundColor(),
            !!(labelStyle->getBackgroundColor()&0xFF000000),
            altitudeMode,
            labelStyle->getRotation(),
            labelStyle->isRotationAbsolute()));

        rotatedLabels = !!labelStyle->getRotation() || labelStyle->isRotationAbsolute();
    } else {
        labels.push_back(GLLabel(Geometry2Ptr_const(new TAK::Engine::Feature::Point2(longitude, latitude, altitude), Memory_deleter_const<Geometry2, TAK::Engine::Feature::Point2>), this->name, TAK::Engine::Math::Point2<double>(), 0.0, TETA_Center, TEVA_Top, -1, 0, false, altitudeMode));
    }
    
    if (!icon_uri && !this->name) {
        //TODO--    iconUri = defaultIconUri;
    }

    return this->setIcon(icon_uri, iconColor);
}

TAKErr GLBatchPoint3::setVisible(const bool& visible) NOTHROWS 
{
    if (this->labelId != GLLabelManager::NO_ID) {
        GLLabelManager *labelManager;
        TAKErr code = GLMapRenderGlobals_getLabelManager(&labelManager, this->surface);
        if (code == TE_Ok && labelManager != nullptr) {
            labelManager->setVisible(this->labelId, visible);
        }
    }
    return TE_Ok;
}

TAKErr GLBatchPoint3::setGeometry(const atakmap::feature::Point &point) NOTHROWS
{
    return GLBatchGeometry3::setGeometry(point);
}

TAKErr GLBatchPoint3::setGeometryImpl(const atakmap::feature::Geometry &geom) NOTHROWS
{
    const auto &point = static_cast<const atakmap::feature::Point &>(geom);
    this->latitude = point.y;
    this->longitude = point.x;
    this->posProjectedSrid = -1;
    this->surfaceProjectedSrid = -1;
	
    if (this->labelId != GLLabelManager::NO_ID)
    {
        GLLabelManager* labelManager;
        TAKErr code = GLMapRenderGlobals_getLabelManager(&labelManager, this->surface);
        if (code == TE_Ok && labelManager != nullptr)
        {
            TAK::Engine::Feature::Point2 geomPoint(this->longitude, this->latitude);
            labelManager->setGeometry(this->labelId, geomPoint);
        }
    }

    return TE_Ok;
}

bool GLBatchPoint3::hasBatchProhibitiveAttributes() const NOTHROWS {
    return this->iconRotation || // icon rotation
           this->rotatedLabels || // label rotation
           this->iconOffsetX || this->iconOffsetY; // icon offset
}

bool GLBatchPoint3::validateProjectedLocation(const GLMapView2 &view) NOTHROWS
{
    bool valid = true;

    // RWI - TODO need to use this->altitude
    GeoPoint2 scratchGeo(this->latitude, this->longitude);

    // Z/altitude
    bool belowTerrain = false;
    double posEl = 0.0;
    if (view.drawTilt > 0.0) {
        // XXX - altitude
        double alt = this->altitude;
        double terrain;
        view.getTerrainMeshElevation(&terrain, this->latitude, this->longitude);
        if (isnan(alt) || this->altitudeMode == TEAM_ClampToGround) {
            alt = terrain;
        } else if (this->altitudeMode == TEAM_Relative) {
            alt += terrain;
        } else if (alt < terrain) {
            // if the explicitly specified altitude is below the terrain,
            // float above and annotate appropriately
            belowTerrain = true;
            alt = terrain;
        }

        // note: always NaN if source alt is NaN
        double adjustedAlt = alt * view.elevationScaleFactor;

        // move up half icon height
        if (this->textureKey != 0L) {
            std::size_t iconH;
            iconAtlas->getImageHeight(&iconH, this->textureKey);
            if (isnan(adjustedAlt))
                adjustedAlt = 0;
            adjustedAlt += view.drawMapResolution*((double)iconH / 2.0);
        }

        // move up ~5 pixels from surface
        adjustedAlt += view.drawMapResolution * 10.0;

        scratchGeo.altitude = adjustedAlt;
        scratchGeo.altitudeRef = AltitudeReference::HAE;
        posEl = isnan(adjustedAlt) ? 0.0 : adjustedAlt;
    }

    if (posProjectedEl != posEl || posProjectedSrid != view.drawSrid || terrainVersion != view.getTerrainVersion()) {
        view.scene.projection->forward(&posProjected, scratchGeo);
        posProjectedEl = posEl;
        posProjectedSrid = view.drawSrid;
        terrainVersion = view.getTerrainVersion();
        valid = false;
    }

    if (surfaceProjectedSrid != view.drawSrid) {
        scratchGeo.altitude = 0.0;
        scratchGeo.altitudeRef = AltitudeReference::HAE;
        view.scene.projection->forward(&surfaceProjected, scratchGeo);
        surfaceProjectedSrid = view.drawSrid;
        valid = false;
    }

    return valid;
}

TAKErr GLBatchPoint3::batch(const GLMapView2 &view, const int render_pass, GLRenderBatch2 &batch) NOTHROWS {
    TAKErr code(TE_Ok);
    if (!(render_pass&GLMapView2::Sprites))
        return code;

    if (this->iconUri && !this->textureKey) {
        this->checkIcon(surface);
        if (!this->textureKey != 0LL)
            return TE_Ok;
    }

    this->validateProjectedLocation(view);

    Math::Point2<double> scratchPoint;
    view.scene.forwardTransform.transform(&scratchPoint, posProjected);
    auto xpos = (float)scratchPoint.x + iconOffsetX;
    auto ypos = (float)scratchPoint.y - iconOffsetY; // GL LL origin
    auto zpos = (float)scratchPoint.z;
    this->screen_x = scratchPoint.x;
    this->screen_y = scratchPoint.y;

    // if tilted, draw a line segment from the center of the point into the
    // earth's surface
    if (extrude < 0.0 && altitudeMode !=TEAM_ClampToGround && view.drawTilt > 0.0) {
        view.scene.forwardTransform.transform(&scratchPoint, surfaceProjected);
        auto xsurface = (float)scratchPoint.x + iconOffsetX;
        auto ysurface = (float)scratchPoint.y - iconOffsetY; // GL LL origin
        auto zsurface = (float)scratchPoint.z;

        float line[6];
        line[0] = xpos;
        line[1] = ypos;
        line[2] = zpos;
        line[3] = xsurface;
        line[4] = ysurface;
        line[5] = zsurface;

        batch.setLineWidth(2.0);
        batch.batch(-1,
                    GL_LINES,
                    2,
                    3,
                    0, line,
                    0, nullptr,
                    this->colorR,
                    this->colorG,
                    this->colorB,
                    this->colorA);
    }

    if (this->iconUri && (this->textureKey == 0LL || this->iconDirty)) {
        this->checkIcon(this->surface);
    }

    if (this->textureKey != 0LL)
    {
        std::size_t iconXi;
        code = iconAtlas->getImageTextureOffsetX(&iconXi, this->textureIndex);
        std::size_t iconYi;
        code = iconAtlas->getImageTextureOffsetY(&iconYi, this->textureIndex);
        TE_CHECKRETURN_CODE(code);

        auto textureSize = static_cast<float>(iconAtlas->getTextureSize());
        std::size_t iconWidthi;
        code = iconAtlas->getImageWidth(&iconWidthi, this->textureKey);
        TE_CHECKRETURN_CODE(code);

        std::size_t iconHeighti;
        code = iconAtlas->getImageHeight(&iconHeighti, this->textureKey);
        TE_CHECKRETURN_CODE(code);

        auto iconX = static_cast<float>(iconXi);
        auto iconY = static_cast<float>(iconYi);
        auto iconWidth = static_cast<float>(iconWidthi);
        auto iconHeight = static_cast<float>(iconHeighti);

        auto densityMultiplier = static_cast<float>(1.0f / view.pixelDensity);
        float iconRenderWidth = iconWidth * densityMultiplier;
        float iconRenderHeight = iconHeight * densityMultiplier;
        
        float vertexCoords[12];
        vertexCoords[0] = xpos - (iconRenderWidth / 2);
        vertexCoords[1] = ypos - (iconRenderHeight / 2);
        vertexCoords[2] = zpos;
        vertexCoords[3] = xpos - (iconRenderWidth / 2);
        vertexCoords[4] = ypos + (iconRenderHeight / 2);
        vertexCoords[5] = zpos;
        vertexCoords[6] = xpos + (iconRenderWidth / 2);
        vertexCoords[7] = ypos - (iconRenderHeight / 2);
        vertexCoords[8] = zpos;
        vertexCoords[9] = xpos + (iconRenderWidth / 2);
        vertexCoords[10] = ypos + (iconRenderHeight / 2);
        vertexCoords[11] = zpos;

        float tex_coords[8];
        tex_coords[0] = iconX / textureSize;
        tex_coords[1] = (iconY + iconHeight - 1.0f) / textureSize;
        tex_coords[2] = iconX / textureSize;
        tex_coords[3] = iconY / textureSize;
        tex_coords[4] = (iconX + iconWidth - 1.0f) / textureSize;
        tex_coords[5] = (iconY + iconHeight - 1.0f) / textureSize;
        tex_coords[6] = (iconX + iconWidth - 1.0f) / textureSize;
        tex_coords[7] = iconY / textureSize;

        code = batch.batch(this->textureId,
                           GL_TRIANGLE_STRIP,
                           4,
                           3,
                           0, vertexCoords,
                           0, tex_coords,
                           this->colorR, this->colorG, this->colorB, this->colorA);
        TE_CHECKRETURN_CODE(code);
    }

    this->batchLabels(view, render_pass, batch);

    return code;
}

TAKErr GLBatchPoint3::batchLabels(const TAK::Engine::Renderer::Core::GLMapView2 &view, const int render_pass, GLRenderBatch2 & batch)
{
    TAKErr code(TE_Ok);

    if (!(render_pass&GLMapView2::Sprites))
        return code;

    Math::Point2<double> scratchPoint;
    view.scene.forwardTransform.transform(&scratchPoint, posProjected);
    auto xpos = (float)scratchPoint.x;
    auto ypos = (float)scratchPoint.y;
    auto zpos = (float)scratchPoint.z;

    // if the displayLables preference is checked display the text if
    // the marker requested to always have the text show or if the scale is zoomed in enough
    if (/*TODO: AtakMapView::SETTING_displayLabels*/true && view.drawMapScale >= defaultLabelRenderScale)
    {
        //JAVA TO C# CONVERTER WARNING: The original Java variable was marked 'final':
        //ORIGINAL LINE: final String text = this.name;
        if (this->name)
        {
            if (defaultText == nullptr)
            {
                TextFormat2Ptr fmt(nullptr, nullptr);
                if (!defaultFontSize) {
                    Port::String opt;
                    code = ConfigOptions_getOption(opt, "default-font-size");
                    if (code == TE_Ok) {
                        defaultFontSize = static_cast<float>(atof(opt));
                    }
                    else {
                        defaultFontSize = 14.0f;
                        code = TE_Ok;
                    }
                }
                code = TextFormat2_createDefaultSystemTextFormat(fmt, defaultFontSize);
                TE_CHECKRETURN_CODE(code);

                std::shared_ptr<TextFormat2> sharedFmt = std::move(fmt);
                defaultText = GLText2_intern(sharedFmt);
            }

            if (defaultText) {
                float offy = 0;
                float offtx = 0;
                float textWidth = defaultText->getTextFormat().getStringWidth(this->name);
                float textHeight = defaultText->getTextFormat().getStringHeight(this->name); // _glText.getBaselineSpacing();
                float textBaseline = defaultText->getTextFormat().getBaselineSpacing();
                if (this->textureKey != 0LL)
                {
                    std::size_t offyi;
                    iconAtlas->getImageHeight(&offyi, this->textureKey);
                    offy = ((float)offyi / (float)2.0) + textHeight + defaultText->getTextFormat().getDescent();
                    offtx = 0.0f;
                }
                else
                {
                    offy = defaultText->getTextFormat().getDescent() + textBaseline / 2.0f;
                }

                float textTx = xpos + offtx - textWidth / 2.0f;
                float textTy = ypos + offy - textBaseline;

                const float labelAlpha = computeLabelAlpha(view, this->labelFadeTimer);

                //if (labelBackground)
                {
                    GLNinePatch *small_nine_patch = this->getSmallNinePatch();
                    if (small_nine_patch != nullptr)
                    {
                        //                        smallNinePatch->batch(batch, textTx - 4.0f, textTy - textHeight + textBaseline - defaultText->getTextFormat().getDescent(), textWidth + 8.0f, textHeight, 0.0f, 0.0f, 0.0f, 0.6f*labelAlpha);
                    }
                }

                defaultText->batch(batch, this->name, textTx, textTy, zpos, 1.0f, 1.0f, 1.0f, labelAlpha);
            }
        }
    }

    return code;
}

GLNinePatch *GLBatchPoint3::getSmallNinePatch() NOTHROWS
{
    if (smallNinePatch == nullptr)
    {
        GLTextureAtlas2 *atlas;
        GLMapRenderGlobals_getTextureAtlas2(&atlas, this->surface);
        smallNinePatch = new GLNinePatch(atlas, GLNinePatch::Size::SMALL, 16, 16, 5, 5, 10, 10);
    }
    return smallNinePatch;
}

TAKErr GLBatchPoint3::getOrFetchIcon(RenderContext &surface, GLBatchPoint3 &point) NOTHROWS
{
    TAKErr code;

    Lock lock(staticMutex);
    code = lock.status;
    TE_CHECKRETURN_CODE(code);

    do
    {
        if (!point.iconUri) {
            return TE_Ok;
        }

        int64_t key = 0LL;
        point.iconAtlas->getTextureKey(&key, point.iconUri);
        if (key != 0LL) {
            point.textureKey = key;
            point.iconAtlas->getTexId(&point.textureId, point.textureKey);
            point.iconAtlas->getIndex(&point.textureIndex, point.textureKey);
            point.iconLoader.reset();
            point.iconLoaderUri = nullptr;
            point.iconDirty = false;
            dereferenceIconLoaderNoSync(point.iconUri);
            return TE_Ok;
        }

        if (point.iconLoader.get()) {
            Future<std::shared_ptr<Bitmap2>> bitmapFuture = point.iconLoader->getFuture();
            if (bitmapFuture.isDone()) {
                std::shared_ptr<Bitmap2> bitmap;
                if (bitmapFuture.getState() == SharedState::Canceled) {
                    return TE_Canceled;
                } else if(bitmapFuture.getState() == SharedState::Complete) {
                    bitmap = bitmapFuture.get();
                }

                if (bitmap.get() == nullptr) {
                    TAK::Engine::Port::String defaultIconUri;
                    code = ConfigOptions_getOption(defaultIconUri, "defaultIconUri");
                    TE_CHECKLOGRETURN_CODE(code, Logger::Error, "GLBatchPoint3: Failed to load icon %s, no default icon available", point.iconUri.get());

                    if (defaultIconUri && !strcmp(point.iconUri, defaultIconUri)) {
                        Logger::log(Logger::Error, "GLBatchPoint3: Failed to load default icon %s", defaultIconUri.get());
                        point.iconUri = nullptr;
                        point.iconLoaderUri = nullptr;
                        point.iconLoader.reset();
                        return TE_Err;
                    }

                    Logger::log(Logger::Warning, "GLBatchPoint3: Failed to load icon %s, loading default", point.iconUri.get());

                    // the icon failed to load, switch the the default icon
                    point.iconLoader.reset();
                    dereferenceIconLoaderNoSync(point.iconUri);
                    if (defaultIconUri) {
                        point.iconUri = defaultIconUri;
                        continue;
                    } else {
                        return TE_Err;
                    }
                }

                point.iconAtlas->addImage(&point.textureKey, point.iconUri, *bitmap);
                bitmap.reset();
                point.iconAtlas->getTexId(&point.textureId, point.textureKey);
                point.iconAtlas->getIndex(&point.textureIndex, point.textureKey);
                point.iconLoader.reset();
                dereferenceIconLoaderNoSync(point.iconLoaderUri);
                point.iconLoaderUri = nullptr;
                point.iconDirty = false;
            }
            return TE_Ok;
        }

        auto it = iconLoaders.find(std::string(point.iconUri));
        IconLoaderEntry iconLoader;
        if (it == iconLoaders.end()) {
            AsyncBitmapLoader2 *bitmapLoader;
            GLMapRenderGlobals_getBitmapLoader(&bitmapLoader, point.surface);

            code = bitmapLoader->loadBitmapUri(iconLoader.task, point.iconUri);
            if (code != TE_Ok) {
                // the URI was not accepted, try the default icon
                TAK::Engine::Port::String defaultIconUri;
                code = ConfigOptions_getOption(defaultIconUri, "defaultIconUri");
                TE_CHECKLOGRETURN_CODE(code, Logger::Error, "GLBatchPoint3: Failed to load icon %s, no default icon available", point.iconUri.get());

                if (defaultIconUri && !strcmp(point.iconUri, defaultIconUri)) {
                    Logger::log(Logger::Error, "GLBatchPoint3: Failed to create loader for default icon %s", defaultIconUri.get());
                    point.iconUri = nullptr;
                    point.iconLoaderUri = nullptr;
                    point.iconLoader.reset();
                    return TE_Err;
                } else if (defaultIconUri) {
                    Logger::log(Logger::Warning, "GLBatchPoint3: Failed to find loader for load icon %s, loading default", point.iconUri.get());

                    point.iconUri = defaultIconUri;
                    continue;
                } else {
                    return TE_IllegalState;
                }

            }
            iconLoaders[std::string(point.iconUri)] = iconLoader;
        } else {
            it->second.serialNumber++;
            iconLoader = it->second;
        }

        point.iconLoader = iconLoader.task;
        point.iconLoaderUri = point.iconUri;

        break;
    } while (true);

    return TE_Ok;
}

TAKErr GLBatchPoint3::dereferenceIconLoader(const char *iconUri) NOTHROWS
{
    TAKErr code(TE_Ok);
    Lock lock(staticMutex);
    code = lock.status;
    TE_CHECKRETURN_CODE(code);

    return dereferenceIconLoaderNoSync(iconUri);
}

TAKErr GLBatchPoint3::dereferenceIconLoaderNoSync(const char *iconUri) NOTHROWS
{
    TAKErr code(TE_Ok);
    if (!iconUri)
        return code;

    auto entry = iconLoaders.find(std::string(iconUri));
    if(entry == iconLoaders.end()) {
        return code;
    }

    entry->second.serialNumber--;
    if (entry->second.serialNumber <= 0) {
        if (!entry->second.task->getFuture().isDone() &&
            entry->second.task->getFuture().getState() != atakmap::util::SharedState::Canceled) {

            entry->second.task->getFuture().cancel();
        }
        iconLoaders.erase(entry);
    }

    return TE_Ok;
}

namespace
{
    float computeLabelAlpha(const GLMapView2 &view, const int64_t labelFadeTimer) NOTHROWS
    {
        const int64_t settleDelta = std::max(std::min((view.animationLastTick - labelFadeTimer), 1000LL), 200LL);
        return (float)settleDelta / 1000.0f;
    }
}

#include "renderer/GL.h"

#include "renderer/feature/GLBatchPoint2.h"

#include "core/GeoPoint.h"
#include "feature/Style.h"
#include "feature/Point.h"
#include "math/Point.h"
#include "renderer/GLTexture2.h"
#include "renderer/GLTextureAtlas.h"
#include "renderer/AsyncBitmapLoader.h"
#include "renderer/GLES20FixedPipeline.h"
#include "renderer/GLNinePatch.h"
#include "renderer/GLText2.h"
#include "renderer/core/GLMapRenderGlobals.h"
#include "thread/Lock.h"
#include "util/ConfigOptions.h"
#include "util/IO.h"
#include "util/MemBuffer.h"
#include "util/Memory.h"


using namespace TAK::Engine::Renderer::Feature;

using namespace TAK::Engine::Core;
using namespace TAK::Engine::Feature;
using namespace TAK::Engine::Renderer;
using namespace TAK::Engine::Renderer::Core;
using namespace TAK::Engine::Thread;
using namespace TAK::Engine::Util;

using namespace atakmap::util;

using namespace atakmap::core;

using namespace atakmap::math;

using namespace atakmap::feature;

using namespace atakmap::renderer;
using namespace atakmap::renderer::map;

namespace
{
    GLText2 *defaultText(nullptr);

    float computeLabelAlpha(const GLMapView *view, const int64_t labelFadeTimer) NOTHROWS;
}

GLNinePatch *GLBatchPoint2::small_nine_patch_ = nullptr;
float GLBatchPoint2::iconAtlasDensity = atakmap::core::AtakMapView::DENSITY;
const double GLBatchPoint2::defaultLabelRenderScale = (1.0 / 250000.0);
GLBatchPoint2::IconLoadersMap GLBatchPoint2::iconLoaders;
Mutex GLBatchPoint2::staticMutex;
float GLBatchPoint2::defaultFontSize = 0;

GLBatchPoint2::GLBatchPoint2(RenderContext &surface)
    : GLBatchGeometry2(surface, 0),
    color(0xffffffff),
    iconRotation(0.0),
    labelRotation(0.0),
    labelBackground(true),
    absoluteLabelRotation(false),
    absoluteIconRotation(false),
    labelFadeTimer(-1LL),
    iconDirty(false)
{
    GLMapRenderGlobals_getIconAtlas2(&this->iconAtlas, surface);
}

GLBatchPoint2::~GLBatchPoint2()
{
    this->stop();
}

int64_t GLBatchPoint2::getTextureKey() const { return textureKey; }

TAKErr GLBatchPoint2::setIcon(const char *uri, const int icon_color) NOTHROWS
{

    if (uri != nullptr && this->iconUri == Port::String(uri)) {
        return TE_Ok;
    }

    if (this->iconLoader.get()) {
        this->iconLoader.reset();
        dereferenceIconLoader(this->iconUri);
    }

    this->iconUri = uri;
    this->color = icon_color;
    this->colorR = (float)((this->color >> 16) & 0xff) / (float)255;
    this->colorG = (float)((this->color >> 8) & 0xff) / (float)255;
    this->colorB = (float)((this->color) & 0xff) / (float)255;
    this->colorA = (float)((this->color >> 24) & 0xff) / (float)255;
    this->iconDirty = true;

    return TE_Ok;
}

TAKErr GLBatchPoint2::checkIcon(RenderContext &render_context) NOTHROWS
{

    if (this->textureKey == 0LL || this->iconDirty) {
        getOrFetchIcon(render_context, this);
    }

    return TE_Ok;
}

static bool SETTING_displayLabels = true;

TAKErr GLBatchPoint2::getIconSize(size_t *width, size_t *height) const NOTHROWS {
    
    TAKErr code;
    std::size_t iconWi;
    code = iconAtlas->getImageWidth(&iconWi, this->textureKey);
    std::size_t iconHi;
    code = iconAtlas->getImageHeight(&iconHi, this->textureKey);
    
    if (width) *width = iconWi;
    if (height) *height = iconHi;
    
    return code;
}

void GLBatchPoint2::draw(const GLMapView *ortho) {

    GeoPoint scratchGeo(this->latitude, this->longitude);
    atakmap::math::Point<float> scratchPoint;
    ortho->forward(scratchGeo, &scratchPoint);
    float xpos = scratchPoint.x;
    float ypos = scratchPoint.y;

    GLES20FixedPipeline::getInstance()->glPushMatrix();
    GLES20FixedPipeline::getInstance()->glTranslatef(xpos, ypos, 0.0f);
    
    if (this->iconUri && (this->textureKey == 0LL || this->iconDirty)) {
        this->checkIcon(ortho->impl->context);
    }

    if (this->textureKey != 0LL)
    {
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        GLES20FixedPipeline::getInstance()->glEnableClientState(GLES20FixedPipeline::ClientState::CS_GL_VERTEX_ARRAY);
        GLES20FixedPipeline::getInstance()->glEnableClientState(GLES20FixedPipeline::ClientState::CS_GL_TEXTURE_COORD_ARRAY);

        if (this->verts.capacity() == 0) {

            TAKErr code;
            this->texCoords.resize(4 * 2); //= gcnew array<float>(4*2);
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
            
            auto densityMultiplier = static_cast<float>(1.0f / ortho->pixelDensity);
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

            this->verts.resize(4 * 2);

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
        if (!this->absoluteIconRotation) {
            icon_rotation = static_cast<float>(fmod(icon_rotation + ortho->drawRotation, 360.0));
        }
        GLES20FixedPipeline::getInstance()->glRotatef(icon_rotation, 0.0f, 0.0f, 1.0f);

        GLES20FixedPipeline::getInstance()->glVertexPointer(2, GL_FLOAT, 0, this->verts.access());

        GLES20FixedPipeline::getInstance()->glTexCoordPointer(2, GL_FLOAT, 0, this->texCoords.access());

        glBindTexture(GL_TEXTURE_2D, this->textureId);

        GLES20FixedPipeline::getInstance()->glColor4f(this->colorR, this->colorG, this->colorB, this->colorA);

        GLES20FixedPipeline::getInstance()->glDrawArrays(GL_TRIANGLE_FAN, 0, 4);

        GLES20FixedPipeline::getInstance()->glDisableClientState(GLES20FixedPipeline::ClientState::CS_GL_TEXTURE_COORD_ARRAY);
        GLES20FixedPipeline::getInstance()->glDisableClientState(GLES20FixedPipeline::ClientState::CS_GL_VERTEX_ARRAY);

        glDisable(GL_BLEND);
        
        GLES20FixedPipeline::getInstance()->glPopMatrix();
    }

    // if the displayLables preference is checked display the text if
    // the marker requested to always have the text show or if the scale is zoomed in enough
    if (!this->iconUri || (/*SETTING_displayLabels*/true && ortho->drawMapScale >= defaultLabelRenderScale)) {

        const char *text = this->name;
        if (text != nullptr && strlen(text) > 0)
        {
            if (defaultText == nullptr)
            {
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
                if (this->textureKey != 0LL)
                {
                    std::size_t offyi;
                    TAKErr code = iconAtlas->getImageHeight(&offyi, this->textureKey);
                    offy = (float)offyi / (float)-2.0;
                    offtx = 0.0f;
                }
                else
                {
                    offy = textDescent + textHeight / 2.0f;
                }
                
                GLES20FixedPipeline::getInstance()->glPushMatrix();
                float label_rotation = this->labelRotation;
                if (!this->absoluteLabelRotation) {
                    label_rotation = static_cast<float>(fmod(label_rotation + ortho->drawRotation, 360.0));
                }
                GLES20FixedPipeline::getInstance()->glRotatef(label_rotation, 0.0f, 0.0f, 1.0f);

                GLES20FixedPipeline::getInstance()->glTranslatef(offtx - textWidth / 2.0f, offy - textBaseline, 0.0f);

                const float labelAlpha = computeLabelAlpha(ortho, labelFadeTimer);

                if (labelBackground) {
                    GLNinePatch *smallNinePatch = this->getSmallNinePatch();
                    if (smallNinePatch != nullptr)
                    {
                        GLES20FixedPipeline::getInstance()->glColor4f(0.0f, 0.0f, 0.0f, 0.6f*labelAlpha);
                        GLES20FixedPipeline::getInstance()->glPushMatrix();
                        GLES20FixedPipeline::getInstance()->glTranslatef(-4.0f, (textBaseline - textHeight) - textDescent, 0.0f);
                        smallNinePatch->draw(textWidth + 8.0f, textHeight);
                        GLES20FixedPipeline::getInstance()->glPopMatrix();
                    }
                }

                defaultText->draw(text, 1.0f, 1.0f, 1.0f, labelAlpha);
                
                GLES20FixedPipeline::getInstance()->glPopMatrix();
            }
        }
    }

    GLES20FixedPipeline::getInstance()->glPopMatrix();
}

void GLBatchPoint2::release() {
    this->texCoords.resize(0);
    this->verts.resize(0);
    this->textureKey = 0LL;
    if (this->iconLoader.get())
    {
        this->iconLoader.reset();
        //    this->iconLoader = nullptr;
        dereferenceIconLoader(this->iconUri);
    }
}

TAKErr GLBatchPoint2::setGeometryImpl(BlobPtr &&blob, const int type) NOTHROWS
{
    TAKErr code;
    code = blob->readDouble(&this->longitude);
    TE_CHECKRETURN_CODE(code);
    code = blob->readDouble(&this->latitude);
    TE_CHECKRETURN_CODE(code);

    return code;
}

TAKErr GLBatchPoint2::setStyle(TAK::Engine::Feature::StylePtr_const &&value) NOTHROWS
{
    return setStyle(std::shared_ptr<const atakmap::feature::Style>(std::move(value)));
}

TAKErr GLBatchPoint2::setStyle(std::shared_ptr<const atakmap::feature::Style> value) NOTHROWS
{
    if (this->surface.isRenderThread())
    {
        return this->setStyleImpl(value.get());
    }
    else
    {
        TAKErr code(TE_Ok);
        Lock lock(*sharedQueueMutex);
        code = lock.status;
        TE_CHECKRETURN_CODE(code);
        std::unique_ptr<StyleUpdater> updatePtr(new StyleUpdater(*this, value));
        StyleUpdater *update = updatePtr.get();
        this->updateQueue.insert(updatePtr.release());
        
        
        
        this->surface.queueEvent(setStyleRunnable, std::unique_ptr<void, void(*)(const void *)>(update, Memory_leaker_const<void>));

        return TE_Ok;
    }
}

TAKErr GLBatchPoint2::setStyleImpl(const atakmap::feature::Style *style) NOTHROWS
{
    int iconColor = -1;
    const char *icon_uri = nullptr;
    const IconPointStyle *iconStyle = nullptr;
    const BasicPointStyle *basicStyle = nullptr;
    const LabelPointStyle *labelStyle = nullptr;

    if (const auto *cs = dynamic_cast<const atakmap::feature::CompositeStyle *>(style)) {
        iconStyle = static_cast<const IconPointStyle *>(cs->findStyle<IconPointStyle>().get());
        basicStyle = static_cast<const BasicPointStyle *>(cs->findStyle<BasicPointStyle>().get());
        labelStyle = static_cast<const LabelPointStyle *>(cs->findStyle<LabelPointStyle>().get());
    } else {
        iconStyle = dynamic_cast<const IconPointStyle *>(style);
        basicStyle = dynamic_cast<const BasicPointStyle *>(style);
        labelStyle = dynamic_cast<const LabelPointStyle *>(style);
    }

    if (iconStyle) {
        iconColor = iconStyle->getColor();
        icon_uri = iconStyle->getIconURI();
        this->iconRotation = iconStyle->getRotation();
        this->absoluteIconRotation = iconStyle->isRotationAbsolute();
    } else if (basicStyle) {
        iconColor = basicStyle->getColor();
    }
    if (labelStyle) {
#if MSVC
        if (labelStyle->getText() && labelStyle->getText()[0])
#else
        if (labelStyle->getText())
#endif
            this->name = labelStyle->getText();
        this->labelRotation = labelStyle->getRotation();
        this->absoluteLabelRotation = labelStyle->isRotationAbsolute();
    }
    
    if (!icon_uri && !this->name) {
        //TODO--    iconUri = defaultIconUri;
    }

    return this->setIcon(icon_uri, iconColor);
}

TAKErr GLBatchPoint2::setGeometry(const atakmap::feature::Point &point) NOTHROWS
{
    return GLBatchGeometry2::setGeometry(point);
}

TAKErr GLBatchPoint2::setGeometryImpl(const atakmap::feature::Geometry &geom) NOTHROWS
{
    const auto &point = static_cast<const atakmap::feature::Point &>(geom);
    this->latitude = point.y;
    this->longitude = point.x;
    return TE_Ok;
}

bool GLBatchPoint2::hasBatchProhibitiveAttributes() const NOTHROWS {
    return this->iconRotation != 0 || this->labelRotation != 0;
}

bool GLBatchPoint2::isBatchable(const GLMapView *view) {
    int numTextures = 0;
    
    if (this->hasBatchProhibitiveAttributes()) {
        return false;
    }
    
    if (this->iconUri && (this->textureKey == 0ll || this->iconDirty)) {
        this->checkIcon(view->impl->context);
    }

    if (this->textureKey != 0LL) {
        numTextures++;
    }

    if (/*TODO:AtakMapView::SETTING_displayLabels*/ true && view->drawMapScale >= defaultLabelRenderScale) {
        //JAVA TO C# CONVERTER WARNING: The original Java variable was marked 'final':
        //ORIGINAL LINE: final String text = this.name;

        if (this->name) {
            numTextures += 2;
        }
    }
    int limit = GLRenderBatch::getBatchTextureUnitLimit();
    return (numTextures > 0 && limit >= numTextures);
}

void GLBatchPoint2::batch(const GLMapView *view, GLRenderBatch *batch) {
    atakmap::core::GeoPoint scratchGeo(this->latitude, this->longitude);
    atakmap::math::Point<float> scratchPoint;

    view->forward(scratchGeo, &scratchPoint);
    float xpos = scratchPoint.x;
    float ypos = scratchPoint.y;

    if (this->iconUri && (this->textureKey == 0LL || this->iconDirty)) {
        this->checkIcon(view->impl->context);
    }

    if (this->textureKey != 0LL)
    {
        TAKErr code;

        std::size_t iconXi;
        code = iconAtlas->getImageTextureOffsetX(&iconXi, this->textureIndex);
        std::size_t iconYi;
        code = iconAtlas->getImageTextureOffsetY(&iconYi, this->textureIndex);

        auto textureSize = static_cast<float>(iconAtlas->getTextureSize());
        std::size_t iconWidthi;
        code = iconAtlas->getImageWidth(&iconWidthi, this->textureKey);
        std::size_t iconHeighti;
        code = iconAtlas->getImageHeight(&iconHeighti, this->textureKey);

        auto iconX = static_cast<float>(iconXi);
        auto iconY = static_cast<float>(iconYi);
        auto iconWidth = static_cast<float>(iconWidthi);
        auto iconHeight = static_cast<float>(iconHeighti);

        auto densityMultiplier = static_cast<float>(1.0f / view->pixelDensity);
        float iconRenderWidth = iconWidth * densityMultiplier;
        float iconRenderHeight = iconHeight * densityMultiplier;
        
        batch->addSprite(
            xpos - (iconRenderWidth / 2), ypos - (iconRenderHeight / 2),
            xpos + (iconRenderWidth / 2), ypos + (iconRenderHeight / 2),
            iconX / textureSize, (iconY + iconWidth - 1.0f) / textureSize,
            (iconX + iconHeight - 1.0f) / textureSize, iconY / textureSize,
            this->textureId,
            this->colorR, this->colorG, this->colorB, this->colorA);
    }

    // if the displayLables preference is checked display the text if
    // the marker requested to always have the text show or if the scale is zoomed in enough
    if (/*TODO: AtakMapView::SETTING_displayLabels*/true && view->drawMapScale >= defaultLabelRenderScale)
    {
        //JAVA TO C# CONVERTER WARNING: The original Java variable was marked 'final':
        //ORIGINAL LINE: final String text = this.name;
        if (this->name)
        {
            if (defaultText == nullptr)
            {
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
                float textWidth = defaultText->getTextFormat().getStringWidth(this->name);
                float textHeight = defaultText->getTextFormat().getStringHeight(this->name); // _glText.getBaselineSpacing();
                float textBaseline = defaultText->getTextFormat().getBaselineSpacing();
                if (this->textureKey != 0LL)
                {
                    std::size_t offyi;
                    iconAtlas->getImageHeight(&offyi, this->textureKey);
                    offy = (float)offyi / (float)-2.0;
                    offtx = 0.0f;
                }
                else
                {
                    offy = defaultText->getTextFormat().getDescent() + textBaseline / 2.0f;
                }

                float textTx = xpos + offtx - textWidth / 2.0f;
                float textTy = ypos + offy - textBaseline;

                const float labelAlpha = computeLabelAlpha(view, this->labelFadeTimer);

                if (labelBackground) {
                    GLNinePatch *smallNinePatch = this->getSmallNinePatch();
                    if (smallNinePatch != nullptr)
                    {
                        smallNinePatch->batch(batch, textTx - 4.0f, textTy - textHeight + textBaseline - defaultText->getTextFormat().getDescent(), textWidth + 8.0f, textHeight, 0.0f, 0.0f, 0.0f, 0.6f*labelAlpha);
                    }
                }

                defaultText->batch(*batch, this->name, textTx, textTy, 1.0f, 1.0f, 1.0f, labelAlpha);
            }
        }
    }
}

GLNinePatch *GLBatchPoint2::getSmallNinePatch() NOTHROWS
{
    if (small_nine_patch_ == nullptr)
    {
        GLTextureAtlas2 *atlas;
        GLMapRenderGlobals_getTextureAtlas2(&atlas, this->surface);
        small_nine_patch_ = new GLNinePatch(atlas, GLNinePatch::Size::SMALL, 16, 16, 5, 5, 10, 10);
    }
    return small_nine_patch_;
}

void GLBatchPoint2::setStyleRunnable(void *opaque) NOTHROWS
{
    std::unique_ptr<StyleUpdater> update(static_cast<StyleUpdater *>(opaque));
    Lock cancelLock(*update->sharedMutex);
    if (!update->canceled) {
        auto &owner = static_cast<GLBatchPoint2 &>(update->owner);
        if (update->style.get())
            owner.setStyleImpl(update->style.get());

        owner.updateQueue.erase(update.get());
    }
}

TAKErr GLBatchPoint2::getOrFetchIcon(RenderContext &surface, GLBatchPoint2 *point) NOTHROWS
{
    TAKErr code;

    Lock lock(staticMutex);
    code = lock.status;
    TE_CHECKRETURN_CODE(code);

    do
    {
        if (!point->iconUri) {
            return TE_Ok;
        }

        int64_t key = 0LL;
        point->iconAtlas->getTextureKey(&key, point->iconUri);
        if (key != 0LL) {
            point->textureKey = key;
            point->iconAtlas->getTexId(&point->textureId, point->textureKey);
            point->iconAtlas->getIndex(&point->textureIndex, point->textureKey);
            point->iconLoader.reset();
            point->iconLoaderUri = "";
            point->iconDirty = false;
            dereferenceIconLoaderNoSync(point->iconUri);
            return TE_Ok;
        }

        if (point->iconLoader.get()) {
            Future<std::shared_ptr<Bitmap2>> bitmapFuture = point->iconLoader->getFuture();
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
                    TE_CHECKLOGRETURN_CODE(code, Logger::Error, "GLBatchPoint2: Failed to load icon %s, no default icon available", point->iconUri.get());

                    if (defaultIconUri && !strcmp(point->iconUri, defaultIconUri)) {
                        Logger::log(Logger::Error, "GLBatchPoint2: Failed to load default icon %s", defaultIconUri.get());
                        point->iconUri = nullptr;
                        point->iconLoaderUri = "";
                        point->iconLoader.reset();
                        return TE_Err;
                    }

                    Logger::log(Logger::Warning, "GLBatchPoint2: Failed to load icon %s, loading default", point->iconUri.get());

                    // the icon failed to load, switch the the default icon
                    point->iconLoader.reset();
                    dereferenceIconLoaderNoSync(point->iconUri);
                    if (defaultIconUri) {
                        point->iconUri = defaultIconUri;
                        continue;
                    } else {
                        return TE_Err;
                    }
                }

                point->iconAtlas->addImage(&point->textureKey, point->iconUri, *bitmap);
                bitmap.reset();
                point->iconAtlas->getTexId(&point->textureId, point->textureKey);
                point->iconAtlas->getIndex(&point->textureIndex, point->textureKey);
                point->iconLoader.reset();
                dereferenceIconLoaderNoSync(point->iconLoaderUri.c_str());
                point->iconLoaderUri = "";
                point->iconDirty = false;
            }
            return TE_Ok;
        }

        auto it = iconLoaders.find(std::string(point->iconUri));
        IconLoaderEntry iconLoader;
        if (it == iconLoaders.end()) {
            AsyncBitmapLoader2 *bitmapLoader;
            GLMapRenderGlobals_getBitmapLoader(&bitmapLoader, point->surface);

            code = bitmapLoader->loadBitmapUri(iconLoader.task, point->iconUri);
            if (code != TE_Ok) {
                // the URI was not accepted, try the default icon
                TAK::Engine::Port::String defaultIconUri;
                code = ConfigOptions_getOption(defaultIconUri, "defaultIconUri");
                TE_CHECKLOGRETURN_CODE(code, Logger::Error, "GLBatchPoint2: Failed to load icon %s, no default icon available", point->iconUri.get());

                if (defaultIconUri && !strcmp(point->iconUri, defaultIconUri)) {
                    Logger::log(Logger::Error, "GLBatchPoint2: Failed to create loader for default icon %s", defaultIconUri.get());
                    point->iconUri = nullptr;
                    point->iconLoaderUri = "";
                    point->iconLoader.reset();
                    return TE_Err;
                } else if (defaultIconUri) {
                    Logger::log(Logger::Warning, "GLBatchPoint2: Failed to find loader for load icon %s, loading default", point->iconUri.get());

                    point->iconUri = defaultIconUri;
                    continue;
                } else {
                    return TE_IllegalState;
                }

            }
            iconLoaders[std::string(point->iconUri)] = iconLoader;
        } else {
            it->second.serialNumber++;
            iconLoader = it->second;
        }

        point->iconLoader = iconLoader.task;
        point->iconLoaderUri = point->iconUri;

        break;
    } while (true);

    return TE_Ok;
}

TAKErr GLBatchPoint2::dereferenceIconLoader(const char *iconUri) NOTHROWS
{
    TAKErr code(TE_Ok);
    Lock lock(staticMutex);
    code = lock.status;
    TE_CHECKRETURN_CODE(code);

    return dereferenceIconLoaderNoSync(iconUri);
}

TAKErr GLBatchPoint2::dereferenceIconLoaderNoSync(const char *iconUri) NOTHROWS
{
    TAKErr code;

    code = TE_Ok;

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

GLBatchPoint2::StyleUpdater::StyleUpdater(GLBatchPoint2 &owner_, std::shared_ptr<const atakmap::feature::Style> &style_) :
    Updater(owner_),
    style(style_)
{}


namespace
{
    float computeLabelAlpha(const GLMapView *view, const int64_t labelFadeTimer) NOTHROWS
    {
        const int64_t settleDelta = std::max(std::min((view->animationLastTick - labelFadeTimer), 1000LL), 200LL);
        return (float)settleDelta / 1000.0f;
    }
}


#include "renderer/GL.h"

#include "core/GeoPoint.h"

#include "math/Point.h"

#include "feature/Style.h"
#include "feature/Point.h"

#include "renderer/GLTextureAtlas2.h"
#include "renderer/AsyncBitmapLoader.h"
//TODO--#include "renderer/core/GLMapRenderGlobals.h"
#include "renderer/GLES20FixedPipeline.h"
#include "renderer/GLNinePatch.h"

#include "util/MemBuffer.h"
#include "util/DataInput2.h"

#include "renderer/feature/GLBatchPoint.h"

using namespace atakmap;

using namespace atakmap::util;

using namespace atakmap::core;

using namespace atakmap::math;

using namespace atakmap::feature;

using namespace atakmap::renderer;
using namespace atakmap::renderer::map;
using namespace atakmap::renderer::feature;

using namespace TAK::Engine::Renderer;
using namespace TAK::Engine::Thread;
using namespace TAK::Engine::Util;

namespace
{
    struct SetGeometryArgs {
        GLBatchPoint *glPoint;
        double lat;
        double lng;
    };
}

GLTextureAtlas2 *GLBatchPoint::ICON_ATLAS = NULL;
GLNinePatch *GLBatchPoint::smallNinePatch = NULL;
float GLBatchPoint::iconAtlasDensity = atakmap::core::AtakMapView::DENSITY;
const double GLBatchPoint::defaultLabelRenderScale = (1.0 / 250000.0);
GLBatchPoint::IconLoadersMap GLBatchPoint::iconLoaders;
Mutex GLBatchPoint::staticMutex;

GLBatchPoint::GLBatchPoint(GLRenderContext *surface)
: GLBatchGeometry(surface, 0),
  color(0xffffffff) { }

void GLBatchPoint::setIcon(const char *uri, int color) {
    
    if (uri != NULL && this->iconUri == uri) {
        return;
    }

    if (this->iconLoader.valid()) {
        this->iconLoader = FutureTask<std::shared_ptr<TAK::Engine::Renderer::Bitmap2>>();
        dereferenceIconLoader(this->iconUri.c_str());
    }

    this->iconUri = uri;
    this->color = color;
    this->colorR = ((this->color >> 16) & 0xff) / 255.0f;
    this->colorG = ((this->color >> 8) & 0xff) / 255.0f;
    this->colorB = ((this->color) & 0xff) / 255.0f;
    this->colorA = ((this->color >> 24) & 0xff) / 255.0f;
}

void GLBatchPoint::checkIcon(GLRenderContext *surface) {
    
    if (this->textureKey == 0LL) {
        getOrFetchIcon(surface, this);
    }
}

static bool SETTING_displayLabels = true;

void GLBatchPoint::draw(const GLMapView *ortho) {
    
    GeoPoint scratchGeo(this->latitude, this->longitude);
    math::Point<float> scratchPoint;
    ortho->forward(scratchGeo, &scratchPoint);
    float xpos = scratchPoint.x;
    float ypos = scratchPoint.y;

    GLES20FixedPipeline::getInstance()->glPushMatrix();
    GLES20FixedPipeline::getInstance()->glTranslatef(xpos, ypos, 0.0f);

    if (this->iconUri != "" && this->textureKey == 0LL) {
        this->checkIcon(ortho->getRenderContext());
    }

    if (this->textureKey != 0LL)
    {
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        GLES20FixedPipeline::getInstance()->glEnableClientState(GLES20FixedPipeline::ClientState::CS_GL_VERTEX_ARRAY);
        GLES20FixedPipeline::getInstance()->glEnableClientState(GLES20FixedPipeline::ClientState::CS_GL_TEXTURE_COORD_ARRAY);

        if (this->verts.capacity() == 0) {
            
            this->texCoords.resize(4 * 2); //= gcnew array<float>(4*2);
            
            size_t value;
            ICON_ATLAS->getImageTextureOffsetX(&value, this->textureKey);
            float iconX = value;
            
            ICON_ATLAS->getImageTextureOffsetY(&value, this->textureKey);
            float iconY = value;

            ICON_ATLAS->getImageWidth(&value, this->textureKey);
            float iconW = value;

            ICON_ATLAS->getImageHeight(&value, this->textureKey);
            float iconH = value;
            
            float textureSize = ICON_ATLAS->getTextureSize();

            this->texCoords[0] = iconX / textureSize;
            this->texCoords[1] = (iconY + iconH - 1.0f) / textureSize;

            this->texCoords[2] = (iconX + iconW - 1.0f) / textureSize;
            this->texCoords[3] = (iconY + iconH - 1.0f) / textureSize;

            this->texCoords[4] = (iconX + iconW - 1.0f) / textureSize;
            this->texCoords[5] = iconY / textureSize;

            this->texCoords[6] = iconX / textureSize;
            this->texCoords[7] = iconY / textureSize;

            this->verts.resize(4 * 2);
            
            this->verts[0] = -iconW / 2.f;
            this->verts[1] = -iconH / 2.f;

            this->verts[2] = iconW / 2.f;
            this->verts[3] = -iconH / 2.f;

            this->verts[4] = iconW / 2.f;
            this->verts[5] = iconH / 2.f;

            this->verts[6] = -iconW / 2.f;
            this->verts[7] = iconH / 2.f;
        }

        GLES20FixedPipeline::getInstance()->glVertexPointer(2, GL_FLOAT, 0, this->verts.access());

        GLES20FixedPipeline::getInstance()->glTexCoordPointer(2, GL_FLOAT, 0, this->texCoords.access());

        glBindTexture(GL_TEXTURE_2D, this->textureId);

        GLES20FixedPipeline::getInstance()->glColor4f(this->colorR, this->colorG, this->colorB, this->colorA);

        GLES20FixedPipeline::getInstance()->glDrawArrays(GL_TRIANGLE_FAN, 0, 4);

        GLES20FixedPipeline::getInstance()->glDisableClientState(GLES20FixedPipeline::ClientState::CS_GL_TEXTURE_COORD_ARRAY);
        GLES20FixedPipeline::getInstance()->glDisableClientState(GLES20FixedPipeline::ClientState::CS_GL_VERTEX_ARRAY);

        glDisable(GL_BLEND);
    }

#if 0 // TODO--- GLText
    // if the displayLables preference is checked display the text if
    // the marker requested to always have the text show or if the scale is zoomed in enough
    if (this->iconUri == "" || (SETTING_displayLabels && ortho->drawMapScale >= defaultLabelRenderScale)) {
        
        String ^text = this->name;
        if (text != nullptr && text->Length > 0)
        {
            if (defaultText == nullptr)
            {
                defaultText = GLText::getInstance(AtakMapView::DEFAULT_TEXT_FORMAT);
            }

            float offy = 0;
            float offtx = 0;
            float textWidth = defaultText->getStringWidth(text);
            float textHeight = defaultText->getStringHeight(text);
            float textDescent = defaultText->getDescent();
            if (this->textureKey != 0LL)
            {
                offy = -ICON_ATLAS->getImageHeight(this->textureKey) / 2;
                offtx = 0.0f;
            }
            else
            {
                offy = textDescent + textHeight / 2.0f;
            }

            GLES20FixedPipeline::glTranslatef(offtx - textWidth / 2.0f, offy - textHeight, 0.0f);

            GLNinePatch ^smallNinePatch = this->getSmallNinePatch();
            if (smallNinePatch != nullptr)
            {
                GLES20FixedPipeline::glColor4f(0.0f, 0.0f, 0.0f, 0.6f);
                GLES20FixedPipeline::glPushMatrix();
                GLES20FixedPipeline::glTranslatef(-4.0f, -textDescent, 0.0f);
                smallNinePatch->draw(textWidth + 8.0f, textHeight);
                GLES20FixedPipeline::glPopMatrix();
            }
            defaultText->draw(text, 1.0f, 1.0f, 1.0f, 1.0f);
        }
    }
#endif

    GLES20FixedPipeline::getInstance()->glPopMatrix();
}

void GLBatchPoint::release() {
    this->texCoords.resize(0);
    this->verts.resize(0);
    this->textureKey = 0LL;
    if (this->iconLoader.valid())
    {
    //    this->iconLoader = nullptr;
        dereferenceIconLoader(this->iconUri.c_str());
    }
}

void GLBatchPoint::setGeometryImpl(util::MemBufferT<uint8_t> *blob, int type) {
    TAK::Engine::Util::ByteBufferInput2 input;
    input.open(blob);
    input.readDouble(&this->longitude);
    input.readDouble(&this->latitude);
}

struct SetStyleImplArgs {
    GLBatchPoint *point;
    int color;
    std::string uri;
};

void GLBatchPoint::setStyle(atakmap::feature::Style *value) {
    int iconColor = -1;
    const char *iconUri = "";

    if (CompositeStyle *cs = dynamic_cast<atakmap::feature::CompositeStyle *>(value)) {
        value = cs->findStyle<PointStyle>().get();
    }

    if (IconPointStyle *is = dynamic_cast<IconPointStyle *>(value)) {
        iconColor = is->getColor();
        iconUri = is->getIconURI();
    }
    else if (BasicPointStyle *bs = dynamic_cast<BasicPointStyle *>(value)) {
        iconColor = bs->getColor();
    }

    if (strcmp(iconUri, "") == 0 && this->name == "") {
    //TODO--    iconUri = defaultIconUri;
    }

    SetStyleImplArgs *args = new SetStyleImplArgs;
    args->point = this;
    args->color = iconColor;
    args->uri = iconUri;
    this->surface->runOnGLThread(setStyleRunnable, args);
}

void GLBatchPoint::setStyleRunnable(void *args) {
    SetStyleImplArgs *sargs = static_cast<SetStyleImplArgs *>(args);
    sargs->point->setIcon(sargs->uri.c_str(), sargs->color);
    delete sargs;
}

void GLBatchPoint::setGeometry(atakmap::feature::Point *point) {
    
    SetGeometryArgs *args = new SetGeometryArgs;
    args->glPoint = this;
    args->lat = point->x;
    args->lng = point->y;
    if (this->surface->isGLThread()) {
        setGeometryImpl(args);
    } else {
        this->surface->runOnGLThread(setGeometryImpl, args);
    }
}

void GLBatchPoint::setGeometryImpl(void *opaque) {
    SetGeometryArgs *args = static_cast<SetGeometryArgs *>(opaque);
    args->glPoint->latitude = args->lat;
    args->glPoint->longitude = args->lng;
    delete args;
}

bool GLBatchPoint::isBatchable(const GLMapView *view) {
    int numTextures = 0;

    if (this->iconUri != "" && this->textureKey == 0ll) {
        this->checkIcon(view->getRenderContext());
    }
    
    if (this->textureKey != 0LL) {
        numTextures++;
    }

    if (/*TODO:AtakMapView::SETTING_displayLabels*/ true && view->drawMapScale >= defaultLabelRenderScale) {
                        //JAVA TO C# CONVERTER WARNING: The original Java variable was marked 'final':
                        //ORIGINAL LINE: final String text = this.name;
        
        if (this->name != "") {
            numTextures += 2;
        }
    }

    return (numTextures > 0 && GLRenderBatch::getBatchTextureUnitLimit() >= numTextures);
}

void GLBatchPoint::batch(const GLMapView *view, GLRenderBatch *batch) {
    atakmap::core::GeoPoint scratchGeo(this->latitude, this->longitude);
    atakmap::math::Point<float> scratchPoint;
    
    view->forward(scratchGeo, &scratchPoint);
    float xpos = scratchPoint.x;
    float ypos = scratchPoint.y;

    if (this->iconUri != "" && this->textureKey == 0LL) {
        this->checkIcon(view->getRenderContext());
    }

    if (this->textureKey != 0LL)
    {
        /*float iconX = ICON_ATLAS->getImageTextureOffsetX(this->textureIndex);
        float iconY = ICON_ATLAS->getImageTextureOffsetY(this->textureIndex);

        float textureSize = ICON_ATLAS->getTextureSize();
        float iconWidth = ICON_ATLAS->getImageWidth(this->textureKey);
        float iconHeight = ICON_ATLAS->getImageHeight(this->textureKey);*/

        size_t value;
        ICON_ATLAS->getImageTextureOffsetX(&value, this->textureKey);
        float iconX = value;
        
        ICON_ATLAS->getImageTextureOffsetY(&value, this->textureKey);
        float iconY = value;
        
        ICON_ATLAS->getImageWidth(&value, this->textureKey);
        float iconWidth = value;
        
        ICON_ATLAS->getImageHeight(&value, this->textureKey);
        float iconHeight = value;
        
        float textureSize = ICON_ATLAS->getTextureSize();
        
        batch->addSprite(
            xpos - (iconWidth / 2), ypos - (iconHeight / 2), 
            xpos + (iconWidth / 2), ypos + (iconHeight / 2), 
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
        if (this->name != "")
        {
#if 0
            if (defaultText == nullptr)
            {
                defaultText = GLText::getInstance(AtakMapView::DEFAULT_TEXT_FORMAT);
            }

            float offy = 0;
            float offtx = 0;
            float textWidth = defaultText->getStringWidth(text);
            float textHeight = defaultText->getStringHeight(); // _glText.getBaselineSpacing();
            if (this->textureKey != 0LL)
            {
                offy = -ICON_ATLAS->getImageHeight(this->textureKey) / 2;
                offtx = 0.0f;
            }
            else
            {
                offy = defaultText->getDescent() + textHeight / 2.0f;
            }

            float textTx = xpos + offtx - textWidth / 2.0f;
            float textTy = ypos + offy - textHeight;

            GLNinePatch ^smallNinePatch = this->getSmallNinePatch();
            if (smallNinePatch != nullptr)
            {
                smallNinePatch->batch(batch, textTx - 4.0f, textTy - defaultText->getDescent(), textWidth + 8.0f, textHeight, 0.0f, 0.0f, 0.0f, 0.6f);
            }

            defaultText->batch(batch, text, textTx, textTy, 1.0f, 1.0f, 1.0f, 1.0f);
#endif
        }
    }
}

GLNinePatch *GLBatchPoint::getSmallNinePatch() {
    if (smallNinePatch == nullptr)
    {
        //atlas = gcnew GLTextureAtlas(256);
        smallNinePatch = new GLNinePatch(ICON_ATLAS, GLNinePatch::Size::SMALL, 16, 16, 5, 5, 10, 10);
    }
    return smallNinePatch;
}

void GLBatchPoint::getOrFetchIcon(GLRenderContext *surface, GLBatchPoint *point) {
    
    TAKErr code(TE_Ok);
    LockPtr lock(NULL, NULL);
    code = Lock_create(lock, staticMutex);
    if (code != TE_Ok)
        throw std::runtime_error
        ("GLBatchPoint::getOrFetchIcon: Failed to acquire mutex");
    do
    {
        if (point->iconUri == "") {
            return;
        }

        int64_t key = 0LL;
        ICON_ATLAS->getTextureKey(&key, point->iconUri.c_str());
        
        if (key != 0LL) {
            point->textureKey = key;
            
            ICON_ATLAS->getTexId(&point->textureId, point->textureKey);
            
            //point->textureId = ICON_ATLAS->getTexId(point->textureKey);
            
            ICON_ATLAS->getIndex(&point->textureIndex, point->textureKey);
            
            //point->textureIndex = ICON_ATLAS->getIndex(point->textureKey);
            point->iconLoader.clear();
            point->iconLoaderUri = "";
            dereferenceIconLoaderNoSync(point->iconUri.c_str());
            return;
        }

        if (point->iconLoader.valid()) {
            Future<std::shared_ptr<TAK::Engine::Renderer::Bitmap2>> bitmapFuture = point->iconLoader.getFuture();
            if (bitmapFuture.isDone()) {
                std::shared_ptr<TAK::Engine::Renderer::Bitmap2> bitmap;
                if (bitmapFuture.getState() != SharedState::Canceled) {
                    bitmap = bitmapFuture.get();
                }
                // XXX -
#if 0
                if (bitmap != nullptr && bitmap->Recycled)
                {
                    point->iconLoader = nullptr;
                    continue;
                }
#endif
                if (bitmap) {
                    
#if 0
                    if (point->iconUri->Equals(defaultIconUri)) {
                        throw gcnew System::InvalidOperationException("Failed to load default icon");
                    }
#endif
                    
                    // the icon failed to load, switch the the default icon
                    point->iconLoader.clear();
                    dereferenceIconLoaderNoSync(point->iconUri.c_str());
#if 0
                    point->iconUri = defaultIconUri;
#endif
                    continue;
                }

                //point->textureKey = ICON_ATLAS->addImage(point->iconUri, bitmap);
                ICON_ATLAS->addImage(&point->textureKey, point->iconUri.c_str(), *bitmap);
                
                //point->textureId = ICON_ATLAS->getTexId(point->textureKey);
                ICON_ATLAS->getTexId(&point->textureId, point->textureKey);
                
                //point->textureIndex = ICON_ATLAS->getIndex(point->textureKey);
                ICON_ATLAS->getIndex(&point->textureIndex, point->textureKey);

                point->iconLoader.clear();
                dereferenceIconLoaderNoSync(point->iconLoaderUri.c_str());
                point->iconLoaderUri = "";
            }
            return;
        }

        auto it = iconLoaders.find(point->iconUri);
        IconLoaderEntry iconLoader;

        if (it == iconLoaders.end()) {
            //TODO-- iconLoader.task =
            iconLoader.serialNumber = 1;
            if (iconLoader.task.valid()) {
            //TODO--    point->iconUri = defaultIconUri;
                continue;
            }
            iconLoaders[point->iconUri] = iconLoader;
        } else {
            it->second.serialNumber++;
            iconLoader = it->second;
        }
        
        point->iconLoader = iconLoader.task;
        point->iconLoaderUri = point->iconUri;
        
        break;
    } while (true);
}

void GLBatchPoint::dereferenceIconLoader(const char *iconUri) {
    TAKErr code(TE_Ok);
    LockPtr lock(NULL, NULL);
    code = Lock_create(lock, staticMutex);
    if (code != TE_Ok)
        throw std::runtime_error
        ("GLBatchPoint::dereferenceIconLoader: Failed to acquire mutex");
    dereferenceIconLoaderNoSync(iconUri);
}

void GLBatchPoint::dereferenceIconLoaderNoSync(const char *iconUri) {
    
    /*TODO--System::Tuple<atakmap::cpp_cli::util::FutureTask<System::Drawing::Bitmap^> ^, array<int>^> ^iconLoader = nullptr;
    if(iconLoaders->TryGetValue(iconUri, iconLoader))
    {
        return;
    }
    array<int> ^item2 = iconLoader->Item2;
    item2[0]--;
    if (item2[0] <= 0)
    {
        if (iconLoader->Item1->isDone() && 
            iconLoader->Item1->getState() != atakmap::cpp_cli::util::FutureTask<System::Drawing::Bitmap^>::State::Canceled)
        {
            try
            {
                System::Drawing::Bitmap ^bitmap = iconLoader->Item1->get();
                if (bitmap != nullptr)
                {
                    delete bitmap;
                }
            }
            catch (System::Exception ^e1)
            {
            }
        }
        else if (iconLoader->Item1->getState() != atakmap::cpp_cli::util::FutureTask<System::Drawing::Bitmap^>::State::Canceled)
        {
            iconLoader->Item1->cancel(false);
        }
        iconLoaders->Remove(iconUri);
    }*/
}

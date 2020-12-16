
#include <unordered_map>

#include "renderer/feature/GLIconPointStyle.h"

#include "feature/GeometryCollection.h"
#include "feature/Style.h"
#include "feature/LineString.h"
#include "feature/Point.h"
#include "feature/Polygon.h"
#include "renderer/GLES20FixedPipeline.h"
#include "renderer/RendererUtils.h"
#include "renderer/feature/GLLineString.h"
#include "renderer/feature/GLPoint.h"
#include "renderer/feature/GLPolygon.h"
#include "renderer/feature/GLGeometryCollectionStyle.h"
#include "renderer/GLRenderContext.h"
#include "util/FutureTask.h"
#include "util/URILoader.h"
#include "renderer/map/GLMapView.h"
#include "renderer/GLRenderBatch.h"
#include "renderer/AsyncBitmapLoader.h"
#include "renderer/BitmapFactory.h"
#include "renderer/GLRendererGlobals.h"
#include "util/Logging.h"

#include "GLES2/gl2.h"

using namespace atakmap::util;

using namespace atakmap::renderer;

using namespace atakmap::renderer::feature;

using namespace TAK::Engine::Thread;
using namespace TAK::Engine::Util;

namespace
{
    class IconStyleRenderContext : public StyleRenderContext {
    public:
        IconStyleRenderContext(atakmap::renderer::feature::GLIconPointStyle *owner, const atakmap::feature::IconPointStyle *style);
    public: // internal:
        int64_t checkIcon(const atakmap::renderer::map::GLMapView *view, atakmap::renderer::GLRenderContext *surface, bool draw);
    public:
        void release();
    public: // internal:
        GLIconPointStyle * const owner;
        PGSC::String iconUri;
        int64_t textureKey;
        int textureId;
        int textureIndex;
        Future<atakmap::renderer::Bitmap> iconLoader;
        PGSC::String iconLoaderUri;
        float atlasIconHeight;
        float atlasIconWidth;
        float iconAtlasTextureOffsetX;
        float iconAtlasTextureOffsetY;

        MemBufferT<float> texCoords;
        MemBufferT<float> verts;
    };

    /**************************************************************************/

    class Point : public GLIconPointStyle {
    public: // internal:
        Point(const atakmap::feature::IconPointStyle *style);
    public:
        virtual void draw(const atakmap::renderer::map::GLMapView *view, atakmap::renderer::feature::GLGeometry *geometry, StyleRenderContext *ctx) override;
        virtual void batch(const atakmap::renderer::map::GLMapView *view, atakmap::renderer::GLRenderBatch *batch, atakmap::renderer::feature::GLGeometry *geometry, StyleRenderContext *ctx) override;
    };

    class LineString : public GLIconPointStyle
    {
    public:
        LineString(const atakmap::feature::IconPointStyle *style);
    public:
        virtual void draw(const atakmap::renderer::map::GLMapView *view,
                          atakmap::renderer::feature::GLGeometry *geometry, StyleRenderContext *ctx) override;
        
        virtual void batch(const atakmap::renderer::map::GLMapView *view,
                           atakmap::renderer::GLRenderBatch *batch, atakmap::renderer::feature::GLGeometry *geometry, StyleRenderContext *ctx) override;
    };

    class PolygonS : public GLIconPointStyle
    {
    public:
        PolygonS(const atakmap::feature::IconPointStyle *style);
    public:
        virtual void draw(const atakmap::renderer::map::GLMapView *view, atakmap::renderer::feature::GLGeometry *geometry, StyleRenderContext *ctx) override;
        virtual void batch(const atakmap::renderer::map::GLMapView *view, atakmap::renderer::GLRenderBatch *batch, atakmap::renderer::feature::GLGeometry *geometry, StyleRenderContext *ctx) override;
    };

    class GLIconPointStyleGlobals
    {
    private :
        GLIconPointStyleGlobals();
    public:
        static atakmap::renderer::GLTextureAtlas *getIconAtlasForView(const atakmap::renderer::map::GLMapView *view);
        
    private:
        static std::auto_ptr<atakmap::renderer::GLTextureAtlas> ICON_ATLAS;// = new atakmap::renderer::GLTextureAtlas(1024);
    public:
        // XXX-
        static PGSC::String defaultIconUri; //"asset:/icons/reference_point.png";
        
        struct IconLoaderEntry {
            Future<atakmap::renderer::Bitmap> bitmap;
            int refs;
        };
        
        static std::unordered_map<std::string, IconLoaderEntry> iconLoaders;
        
  /*      static System::Collections::Generic::Dictionary<System::String *, System::Tuple<Future<atakmap::renderer::Bitmap *> *, array<int> *> *> *iconLoaders = new System::Collections::Generic::Dictionary<System::String *, System::Tuple<Future<atakmap::renderer::Bitmap *> *, array<int> *> *>();*/
        static Mutex iconLoaderSync;
    };

    void getOrFetchIcon(const atakmap::renderer::map::GLMapView *view, atakmap::renderer::GLRenderContext *surface, IconStyleRenderContext *point);
    void dereferenceIconLoader(const char *iconUri);
    void dereferenceIconLoaderNoSync(const char *iconUri);
}

GLIconPointStyle::Spi::~Spi() { }

GLStyle *GLIconPointStyle::Spi::create(const GLStyleSpiArg &object) {
    const atakmap::feature::Style *s = object.style;
    const atakmap::feature::Geometry *g = object.geometry;
    if(s == nullptr || g == nullptr)
        return nullptr;
#if 0
    // XXX - capture basic point style here as well to avoid having to
    //       implement a new GLStyle using GL_POINTs rendering right now
    if(s instanceof BasicPointStyle) {
        final BasicPointStyle basic = (BasicPointStyle)s;
        s = new IconPointStyle(basic.getColor(),
                                defaultIconUri,
                                basic.getSize(),
                                basic.getSize(),
                                0,
                                0,
                                0,
                                false);
    }
#endif

    const atakmap::feature::IconPointStyle *is = dynamic_cast<const atakmap::feature::IconPointStyle *>(s);
    if(is == nullptr)
        return nullptr;
    if (dynamic_cast<const atakmap::feature::Point *>(g))
        return new ::Point(is);
    else if (dynamic_cast<const atakmap::feature::LineString *>(g))
        return new ::LineString(is);
    else if (dynamic_cast<const atakmap::feature::Polygon *>(g))
        return new ::PolygonS(is);
    else if (dynamic_cast<const atakmap::feature::GeometryCollection *>(g))
        return new GLGeometryCollectionStyle(s, getSpi());
    return nullptr;
}

GLStyleSpi *GLIconPointStyle::getSpi() {
    static Spi spi;
    return &spi;
}

GLIconPointStyle::GLIconPointStyle(const atakmap::feature::IconPointStyle *style) :
    GLPointStyle(style),
    iconWidth(style->getWidth() * style->getScaling()),
    iconHeight(style->getHeight() * style->getScaling()),
    alignX(style->getVerticalAlignment()),
    alignY(style->getHorizontalAlignment()),
    rotation(style->getRotation()),
    rotationAbsolute(style->isRotationAbsolute()),
#define GET_COLOR(c) atakmap::renderer::Utils::colorExtract(style->getColor(), atakmap::renderer::Utils::Colors::c) / 255.0f
    colorR(GET_COLOR(RED)),
    colorG(GET_COLOR(GREEN)),
    colorB(GET_COLOR(BLUE)),
    colorA(GET_COLOR(ALPHA))
#undef GET_COLOR
{
    if(style->getScaling() != 1.f)
    {
        printf("");
    }
}

    // XXX - NEED TO IMPLEMENT ROTATION  !!!!!
    
void GLIconPointStyle::drawAt(const atakmap::renderer::map::GLMapView *view, float xpos, float ypos, StyleRenderContext *ctx)
{
    IconStyleRenderContext *context = static_cast<IconStyleRenderContext *>(ctx);
    if(context->checkIcon(view, view->getRenderContext(), true) == 0L)
        return;

    GLES20FixedPipeline::getInstance()->glPushMatrix();
    GLES20FixedPipeline::getInstance()->glTranslatef(xpos, ypos, 0.0f);
    GLES20FixedPipeline::getInstance()->glRotatef(this->rotation + 11, 0, 0, 1.f);
    float scaling = ((atakmap::feature::IconPointStyle*)style)->getScaling();
    GLES20FixedPipeline::getInstance()->glScalef(scaling, scaling, 1.f);
    
    glEnable(GL_BLEND);
    glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

    GLES20FixedPipeline::getInstance()->glEnableClientState(GLES20FixedPipeline::ClientState::CS_GL_VERTEX_ARRAY);
    GLES20FixedPipeline::getInstance()->glEnableClientState(GLES20FixedPipeline::ClientState::CS_GL_TEXTURE_COORD_ARRAY);

    GLES20FixedPipeline::getInstance()->glVertexPointer(2, GL_FLOAT, 0, context->verts.access());

    GLES20FixedPipeline::getInstance()->glTexCoordPointer(2, GL_FLOAT, 0, context->texCoords.access());

    glBindTexture(GL_TEXTURE_2D, context->textureId);

    GLES20FixedPipeline::getInstance()->glColor4f(this->colorR, this->colorG, this->colorB, this->colorA);

    GLES20FixedPipeline::getInstance()->glDrawArrays(GL_TRIANGLE_FAN, 0, 4);

    GLES20FixedPipeline::getInstance()->glDisableClientState(GLES20FixedPipeline::ClientState::CS_GL_TEXTURE_COORD_ARRAY);
    GLES20FixedPipeline::getInstance()->glDisableClientState(GLES20FixedPipeline::ClientState::CS_GL_VERTEX_ARRAY);

    glDisable(GL_BLEND);

    GLES20FixedPipeline::getInstance()->glPopMatrix();
}

void GLIconPointStyle::batchAt(const atakmap::renderer::map::GLMapView *view,
                               atakmap::renderer::GLRenderBatch *batch, float xpos, float ypos, StyleRenderContext *ctx) {
    
    IconStyleRenderContext *context = static_cast<IconStyleRenderContext *>(ctx);
    if(context->checkIcon(view, view->getRenderContext(), false) == 0L)
        return;
    
    const float relativeScaling = 1.0 / view->pixelDensity;
    const float textureSize = GLIconPointStyleGlobals::getIconAtlasForView(view)->getTextureSize();
        
    float renderWidth;
    if(this->iconWidth == 0.0f)
        renderWidth = context->atlasIconWidth*relativeScaling;
    else
        renderWidth = this->iconWidth*relativeScaling;
    float renderHeight;
    if(this->iconHeight == 0.0f)
        renderHeight = context->atlasIconHeight*relativeScaling;
    else
        renderHeight = this->iconHeight*relativeScaling;

    float iconOffsetX;
    if(this->alignX < 0)
        iconOffsetX = -renderWidth;
    else if(this->alignX == 0)
        iconOffsetX = -(renderWidth/2);
    else if(this->alignX > 0)
        iconOffsetX = 0.0f;
    else
        throw std::logic_error("alignX");

    float iconOffsetY;
    if(this->alignY < 0)
        iconOffsetY = 0.0f;
    else if(this->alignY == 0)
        iconOffsetY = -(renderHeight/2);
    else if(this->alignY > 0)
        iconOffsetY = -renderHeight;
    else
        throw std::logic_error("alignY");

    batch->addSprite(xpos + iconOffsetX,
                     ypos + iconOffsetY,
                     xpos + (iconOffsetX+renderWidth),
                     ypos + (iconOffsetY+renderHeight),
                     context->iconAtlasTextureOffsetX / textureSize,
                     (context->iconAtlasTextureOffsetY + context->atlasIconHeight - 1.0f) / textureSize,
                     (context->iconAtlasTextureOffsetX + context->atlasIconWidth - 1.0f) / textureSize,
                     context->iconAtlasTextureOffsetY / textureSize,
                     context->textureId,
                     this->colorR, this->colorG, this->colorB, this->colorA);
}

bool GLIconPointStyle::isBatchable(const atakmap::renderer::map::GLMapView *view, GLGeometry *geometry, StyleRenderContext *ctx)
{
    return (this->rotation == 0.0f && !this->rotationAbsolute) && ((atakmap::feature::IconPointStyle*)style)->getScaling() == 1.f;
}

StyleRenderContext *GLIconPointStyle::createRenderContext(const atakmap::renderer::map::GLMapView *view, GLGeometry *geometry)
{
    return new IconStyleRenderContext(this, static_cast<const atakmap::feature::IconPointStyle *>(this->style));
}

void GLIconPointStyle::releaseRenderContext(StyleRenderContext *ctx)
{
    if(ctx != nullptr) {
        IconStyleRenderContext *context = static_cast<IconStyleRenderContext *>(ctx);
        context->release();
        delete ctx;
    }
}

/**************************************************************************/

namespace
{
    std::auto_ptr<atakmap::renderer::GLTextureAtlas> GLIconPointStyleGlobals::ICON_ATLAS;
    
    PGSC::String GLIconPointStyleGlobals::defaultIconUri = "resource:/Map/Icons/MilStd2525C/sugp-----------.png";
    
    std::unordered_map<std::string, GLIconPointStyleGlobals::IconLoaderEntry> GLIconPointStyleGlobals::iconLoaders;
    
    Mutex GLIconPointStyleGlobals::iconLoaderSync;
    
    GLIconPointStyleGlobals::GLIconPointStyleGlobals()
    {}
    
    atakmap::renderer::GLTextureAtlas *GLIconPointStyleGlobals::getIconAtlasForView(const atakmap::renderer::map::GLMapView *view) {
        // TODO-- context local?
        if (!ICON_ATLAS.get()) {
            int iconSize = view->pixelDensity * 32;
            ICON_ATLAS = std::auto_ptr<atakmap::renderer::GLTextureAtlas>(new atakmap::renderer::GLTextureAtlas(1024, iconSize));
        }
        return ICON_ATLAS.get();
    }

    IconStyleRenderContext::IconStyleRenderContext(GLIconPointStyle * o, const atakmap::feature::IconPointStyle *style) :
        owner(o),
        iconUri(style->getIconURI()),
        textureKey(0),
        textureId(0),
        atlasIconWidth(0.f),
        atlasIconHeight(0.f)
    {}

    int64_t IconStyleRenderContext::checkIcon(const atakmap::renderer::map::GLMapView *view,
                                              atakmap::renderer::GLRenderContext *surface, bool draw)
    {
        if (this->textureKey == 0L)
            getOrFetchIcon(view, surface, this);

        draw = draw && (this->textureKey != 0);
        
        if (draw && this->verts.limit() == 0) {
            
            const float relativeScaling = 1.0 / view->pixelDensity;
            this->texCoords.resize(4 * 2);
            
            const float textureSize = GLIconPointStyleGlobals::getIconAtlasForView(view)->getTextureSize();

            float renderWidth;
            if (this->owner->iconWidth == 0.0f)
                renderWidth = this->atlasIconWidth*relativeScaling;
            else
                renderWidth = this->owner->iconWidth*relativeScaling;
            float renderHeight;
            if (this->owner->iconHeight == 0.0f)
                renderHeight = this->atlasIconHeight*relativeScaling;
            else
                renderHeight = this->owner->iconHeight*relativeScaling;

            float iconOffsetX;
            if (this->owner->alignX < 0)
                iconOffsetX = -renderWidth;
            else if (this->owner->alignX == 0)
                iconOffsetX = -(renderWidth / 2);
            else if (this->owner->alignX > 0)
                iconOffsetX = 0.0f;
            else
                throw std::logic_error("alignX");

            float iconOffsetY;
            if (this->owner->alignY < 0)
                iconOffsetY = 0.0f;
            else if (this->owner->alignY == 0)
                iconOffsetY = -(renderHeight / 2);
            else if (this->owner->alignY > 0)
                iconOffsetY = -renderHeight;
            else
                throw std::logic_error("alignY");

            this->texCoords.limit(8);
            this->texCoords[0] = this->iconAtlasTextureOffsetX / textureSize;
            this->texCoords[1] = (this->iconAtlasTextureOffsetY + this->atlasIconWidth - 1.0f) / textureSize;

            this->texCoords[2] = (this->iconAtlasTextureOffsetX + this->atlasIconWidth - 1.0f) / textureSize;
            this->texCoords[3] = (this->iconAtlasTextureOffsetY + this->atlasIconHeight - 1.0f) / textureSize;

            this->texCoords[4] = (this->iconAtlasTextureOffsetX + this->atlasIconWidth - 1.0f) / textureSize;
            this->texCoords[5] = this->iconAtlasTextureOffsetY / textureSize;

            this->texCoords[6] = this->iconAtlasTextureOffsetX / textureSize;
            this->texCoords[7] = this->iconAtlasTextureOffsetY / textureSize;

            this->verts.resize(4 * 2);

            this->verts.limit(8);
            this->verts[0] = iconOffsetX;
            this->verts[1] = iconOffsetY;

            this->verts[2] = iconOffsetX + renderWidth;
            this->verts[3] = iconOffsetY;

            this->verts[4] = iconOffsetX + renderWidth;
            this->verts[5] = iconOffsetY + renderHeight;

            this->verts[6] = iconOffsetX;
            this->verts[7] = iconOffsetY + renderHeight;
        }
        return this->textureKey;
    }

    void IconStyleRenderContext::release()
    {
        this->texCoords.resize(0);// = nullptr;
        this->verts.resize(0);// = nullptr;
        this->textureKey = 0L;
#if 0
        if (this->iconLoader != nullptr) {
            this->iconLoader = nullptr;
            dereferenceIconLoader(this->iconUri);
        }
#endif
    }

    /**************************************************************************/

    void getOrFetchIcon(const atakmap::renderer::map::GLMapView *view, atakmap::renderer::GLRenderContext *surface, IconStyleRenderContext *point)
    {
        TAKErr code(TE_Ok);
        LockPtr lock(NULL, NULL);
        code = Lock_create(lock, GLIconPointStyleGlobals::iconLoaderSync);
        if (code != TE_Ok)
            throw std::runtime_error
            ("GLIconPointStyle::getOrFetchIcon: Failed to acquire mutex");

        do {
            if ((const char *)point->iconUri == NULL)
                return;

            atakmap::renderer::GLTextureAtlas *atlas = GLIconPointStyleGlobals::getIconAtlasForView(view);
            int64_t key = atlas->getTextureKey(std::string(point->iconUri));
            if (key != 0L) {
                point->textureKey = key;
                point->textureId = atlas->getTexId(point->textureKey);
                point->textureIndex = atlas->getIndex(point->textureKey);
                point->atlasIconWidth = atlas->getImageWidth(point->textureKey);
                point->atlasIconHeight = atlas->getImageHeight(point->textureKey);
                point->iconAtlasTextureOffsetX = atlas->getImageTextureOffsetX(point->textureKey);
                point->iconAtlasTextureOffsetY = atlas->getImageTextureOffsetY(point->textureKey);
                point->iconLoader.clear();
                point->iconLoaderUri = "";
                dereferenceIconLoaderNoSync(point->iconUri);
                return;
            }

            if (point->iconLoader.valid()) {
                if (point->iconLoader.isDone()) {
                    
                    atakmap::renderer::Bitmap bitmap;
                    bitmap.data = NULL;
                    if (!(point->iconLoader.getState() == atakmap::util::SharedState::Canceled)) {
                        try {
                            bitmap = point->iconLoader.get();
                        }
                        catch (const std::exception &e) {
                            // ignore
                        }
                    }
                    if (bitmap.data == NULL) {
                        if (point->iconUri == PGSC::String(GLIconPointStyleGlobals::defaultIconUri))
                            throw std::logic_error("Failed to load default icon");

                        // the icon failed to load, switch the the default icon
                        point->iconLoader.clear();
                        dereferenceIconLoaderNoSync(point->iconUri);
                        point->iconUri = GLIconPointStyleGlobals::defaultIconUri;

                        continue;
                    }

                    //try {
                        point->textureKey = atlas->addImage(std::string(point->iconUri), bitmap);
                    
                    Logger::log(Logger::Debug, "icon url %s, key=%" PRId64 "\n", point->iconUri.get(), point->textureKey);
                    
                    //}
                    /*finally {
                        bitmap->~Bitmap();
                    }*/
                    point->textureId = atlas->getTexId(point->textureKey);
                    point->textureIndex = atlas->getIndex(point->textureKey);

                    point->atlasIconWidth = atlas->getImageWidth(point->textureKey);
                    point->atlasIconHeight = atlas->getImageWidth(point->textureKey);
                    
                    point->iconAtlasTextureOffsetX = atlas->getImageTextureOffsetX(point->textureKey);
                    point->iconAtlasTextureOffsetY = atlas->getImageTextureOffsetY(point->textureKey);
                    
                    point->iconLoader.clear();
                    dereferenceIconLoaderNoSync(point->iconLoaderUri);
                    point->iconLoaderUri = "";
                }
                return;
            }

            auto it = GLIconPointStyleGlobals::iconLoaders.find(std::string(point->iconUri));
            if (it == GLIconPointStyleGlobals::iconLoaders.end()) {
                // XXX - load image
                std::string iconURI = point->iconUri.get();
                FutureTask<atakmap::renderer::Bitmap> task([=] () {
                    std::unique_ptr<DataInput> input(URILoader::openURI(iconURI.c_str(), nullptr));
                    if (input) {
                        atakmap::renderer::Bitmap b;
                        b.data = nullptr;
                        BitmapFactory::DecodeResult result = BitmapFactory::decode(*input, b, nullptr);
                        if (result != BitmapFactory::Success || b.data == nullptr) {
                            std::stringstream ss;
                            ss << "failed to decode icon for uri: '" << iconURI.c_str() << "'";
                            throw std::runtime_error(ss.str().c_str());
                        }
                        input->close();
                        return b;
                    }
                    std::stringstream ss;
                    ss << "no URILoader available for uri: '" << iconURI.c_str() << "'";
                    throw std::runtime_error(ss.str().c_str());
                });
                
                GLRendererGlobals::getAsyncBitmapLoader()->loadBitmap(task);
                
                GLIconPointStyleGlobals::IconLoaderEntry entry;
                entry.refs = 1;
                entry.bitmap = task.getFuture();
                
                it = GLIconPointStyleGlobals::iconLoaders
                    .insert(std::pair<std::string, GLIconPointStyleGlobals::IconLoaderEntry>(std::string(point->iconUri),
                                                                                             entry)).first;
            }
            else {
                it->second.refs++;
            }
            point->iconLoader = it->second.bitmap;
            point->iconLoaderUri = point->iconUri;

            break;
        } while (true);
    }

    void dereferenceIconLoader(const char *iconUri) {
        TAKErr code(TE_Ok);
        LockPtr lock(NULL, NULL);
        code = Lock_create(lock, GLIconPointStyleGlobals::iconLoaderSync);
        if (code != TE_Ok)
            throw std::runtime_error
            ("GLIconPointstyle::dereferenceIconLoader: Failed to acquire mutex");
        dereferenceIconLoaderNoSync(iconUri);
    }

    void dereferenceIconLoaderNoSync(const char *iconUri) {
        auto it = GLIconPointStyleGlobals::iconLoaders.find(iconUri);
        if (it == GLIconPointStyleGlobals::iconLoaders.end()) {
            return;
        }
        
        int *cnt = &it->second.refs;
        cnt[0]--;
        if (cnt[0] <= 0) {
            if (it->second.bitmap.isDone() && !(it->second.bitmap.getState() == SharedState::Canceled)) {
                try {
                    atakmap::renderer::Bitmap bitmap = it->second.bitmap.get();
                    if (bitmap.data != nullptr && bitmap.releaseData != NULL) {
                        bitmap.releaseData(bitmap);//->~Bitmap();
                    }
                }
                catch (const FutureError &e) {
                    Logger::log(Logger::Debug, "failed to resolve bitmap for IconStyle with uri '%s'", iconUri);
                }
            }
            else if (!(it->second.bitmap.getState() == SharedState::Canceled)) {
                it->second.bitmap.cancel();
            }
            GLIconPointStyleGlobals::iconLoaders.erase(it);//->Remove(iconUri);
        }
    }


    /**************************************************************************/

    Point::Point(const atakmap::feature::IconPointStyle *style) :
        GLIconPointStyle(style)
    {}

    void Point::draw(const atakmap::renderer::map::GLMapView *view, GLGeometry *geometry, StyleRenderContext *ctx) {
        
        GLPoint *p = static_cast<GLPoint *>(geometry);
        
        atakmap::math::Point<double> scratchPoint;
        p->getVertex(view, GLGeometry::VERTICES_PIXEL, &scratchPoint);
        this->drawAt(view,
            (float)scratchPoint.x,
            (float)scratchPoint.y,
            static_cast<IconStyleRenderContext *>(ctx));
    }

    void Point::batch(const atakmap::renderer::map::GLMapView *view, atakmap::renderer::GLRenderBatch *batch, atakmap::renderer::feature::GLGeometry *geometry, StyleRenderContext *ctx) {
        
        GLPoint *p = static_cast<GLPoint *>(geometry);
        
        atakmap::math::Point<double> scratchPoint;
        p->getVertex(view, GLGeometry::VERTICES_PIXEL, &scratchPoint);
        this->batchAt(view,
            batch,
            (float)scratchPoint.x,
            (float)scratchPoint.y,
            static_cast<IconStyleRenderContext *>(ctx));
    }

    LineString::LineString(const atakmap::feature::IconPointStyle *style) :
        GLIconPointStyle(style)
    {}

    void LineString::draw(const atakmap::renderer::map::GLMapView *view, atakmap::renderer::feature::GLGeometry *geometry, StyleRenderContext *ctx)
    {
        GLLineString *p = static_cast<GLLineString *>(geometry);
        const float *buffer = p->getVertices(view, GLGeometry::VERTICES_PIXEL);
        for (int i = 0; i < p->getNumVertices(); i++) {
            this->drawAt(view,
                buffer[i * 2],
                buffer[i * 2 + 1],
                static_cast<IconStyleRenderContext *>(ctx));
        }
    }

    void LineString::batch(const atakmap::renderer::map::GLMapView *view, atakmap::renderer::GLRenderBatch *batch,
                           atakmap::renderer::feature::GLGeometry *geometry, StyleRenderContext *ctx) {
        
        GLLineString *p = static_cast<GLLineString *>(geometry);
        const float *buffer = p->getVertices(view, GLGeometry::VERTICES_PIXEL);
        for (int i = 0; i < p->getNumVertices(); i++) {
            this->batchAt(view,
                batch,
                buffer[i * 2],
                buffer[i * 2 + 1],
                static_cast<IconStyleRenderContext *>(ctx));
        }
    }

    PolygonS::PolygonS(const atakmap::feature::IconPointStyle *style) :
        GLIconPointStyle(style)
    {}

    void PolygonS::draw(const atakmap::renderer::map::GLMapView *view, atakmap::renderer::feature::GLGeometry *geometry, StyleRenderContext *ctx)
    {
        GLPolygon *p = static_cast<GLPolygon *>(geometry);
        for (int i = 0; i < p->getNumInteriorRings() + 1; i++) {
            std::pair<float *, size_t> buffer = p->getVertices(view, GLGeometry::VERTICES_PIXEL, i);
            for (int j = 0; j < p->getNumVertices(i); j++) {
                this->drawAt(view,
                    buffer.first[j * 2],
                    buffer.first[j * 2 + 1],
                    static_cast<IconStyleRenderContext *>(ctx));
            }
        }
    }

    void PolygonS::batch(const atakmap::renderer::map::GLMapView *view, atakmap::renderer::GLRenderBatch *batch,
                         atakmap::renderer::feature::GLGeometry *geometry, StyleRenderContext *ctx)
    {

        GLPolygon *p = static_cast<GLPolygon *>(geometry);
        for (int i = 0; i < p->getNumInteriorRings() + 1; i++) {
            //for(int i = 0; i < 1; i++) {
            std::pair<float *, size_t> buffer = p->getVertices(view, GLGeometry::VERTICES_PIXEL, i);
            for (int j = 0; j < p->getNumVertices(i); j++) {
                this->batchAt(view,
                    batch,
                    buffer.first[j * 2],
                    buffer.first[j * 2 + 1],
                    static_cast<IconStyleRenderContext *>(ctx));
            }
        }
    }
}

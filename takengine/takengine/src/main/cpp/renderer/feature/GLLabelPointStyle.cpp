
#include <cfloat>

#include "math/Point.h"

#include "feature/GeometryCollection.h"
#include "feature/Style.h"

#include "renderer/feature/GLLabelPointStyle.h"
#include "renderer/feature/GLGeometryCollectionStyle.h"
#include "renderer/GLNinePatch.h"
#include "renderer/GLText.h"
#include "renderer/RendererUtils.h"
#include "renderer/GLES20FixedPipeline.h"

#include "renderer/map/GLMapView.h"

#include "renderer/feature/GLPoint.h"
#include "renderer/feature/GLLineString.h"
#include "renderer/feature/GLPolygon.h"

#include "feature/Point.h"
#include "feature/LineString.h"
#include "feature/Polygon.h"

#define DISABLE_LABEL_BATCHING 0

using namespace atakmap::feature;

using namespace atakmap::renderer;
using namespace atakmap::renderer::map;
using namespace atakmap::renderer::feature;

const int MAX_TEXT_WIDTH = 80; // XXX - was multiplied by AtakMapView::DENSITY

class Point : public GLLabelPointStyle {
public:
    Point(const atakmap::feature::LabelPointStyle *style)
    : GLLabelPointStyle(style) { }
    
    virtual ~Point() {};

    virtual void draw(const GLMapView *view, GLGeometry *geometry, StyleRenderContext *ctx) override {
        GLPoint *p = (GLPoint *)geometry;
        
        atakmap::math::Point<double> scratchPoint;
        p->getVertex(view, GLGeometry::VERTICES_PIXEL, &scratchPoint);
        
        drawAt(view,
               (float)scratchPoint.x,
               (float)scratchPoint.y,
               ctx);
    };

    virtual void batch(const GLMapView *view, GLRenderBatch *batch, GLGeometry *geometry,
        StyleRenderContext *ctx) override {
        GLPoint *p = (GLPoint *)geometry;
        
        atakmap::math::Point<double> scratchPoint;
        p->getVertex(view, GLGeometry::VERTICES_PIXEL, &scratchPoint);
        batchAt(view,
                batch,
                (float)scratchPoint.x,
                (float)scratchPoint.y,
                ctx);
    };
};

class LineString : public GLLabelPointStyle {

public:
    LineString(const atakmap::feature::LabelPointStyle *style)
    : GLLabelPointStyle(style) { }
    
    virtual ~LineString() { }

    virtual void draw(const GLMapView *view, GLGeometry *geometry, StyleRenderContext *ctx) override {
        GLLineString *p = (GLLineString *)geometry;
        const float *buffer = p->getVertices(view, GLGeometry::VERTICES_PIXEL);
        for (int i = 0; i < p->getNumVertices(); i++) {
            drawAt(view, buffer[2*i + 0], buffer[2*i + 1], ctx);
        }
    }

    virtual void batch(const GLMapView *view, GLRenderBatch *batch, GLGeometry *geometry,
                       StyleRenderContext *ctx) override {
        GLLineString *p = (GLLineString *)geometry;
        const float *buffer = p->getVertices(view, GLGeometry::VERTICES_PIXEL);
        for (int i = 0; i < p->getNumVertices(); i++) {
            batchAt(view,
                    batch,
                    buffer[2 * i],
                    buffer[2 * i + 1],
                    ctx);
        }
    }
};

class Polygon : public GLLabelPointStyle {

public:
    Polygon(const LabelPointStyle *style) : GLLabelPointStyle(style) { }

    virtual void draw(const GLMapView *view, GLGeometry *geometry, StyleRenderContext *ctx) override
    {
        GLPolygon *p = (GLPolygon *)geometry;
        for (int i = 0; i < p->getNumInteriorRings() + 1; i++) {
            std::pair<float *, size_t> buffer = p->getVertices(view, GLGeometry::VERTICES_PIXEL, i);
            for (int j = 0; j < p->getNumVertices(i); j++) {
                drawAt(view,
                       buffer.first[2 * j],
                       buffer.first[2 * j + 1],
                       ctx);
            }
        }
    }

    virtual void batch(const GLMapView *view, GLRenderBatch *batch, GLGeometry *geometry,
                       StyleRenderContext *ctx) override {
        GLPolygon *p = (GLPolygon *)geometry;
        for (int i = 0; i < p->getNumInteriorRings() + 1; i++) {
            std::pair<float *, size_t> buffer = p->getVertices(view, GLGeometry::VERTICES_PIXEL, i);
            for (int j = 0; j < p->getNumVertices(i); j++) {
                batchAt(view,
                        batch,
                        buffer.first[2 * j],
                        buffer.first[2 * j + 1],
                        ctx);
            }
        }
    }
};

class LabelRenderContext : public StyleRenderContext
{
public: //internal:
    float labelWidth;
    float labelHeight;
    float marqueeOffset;
    long marqueeTimer;
    bool shouldMarquee;
    TAK::Engine::Renderer::GLTextureAtlas2 *atlas;
    static GLNinePatch *background;
    GLText *text;
    TextFormat *textFormat;
public:
    LabelRenderContext(GLLabelPointStyle *owner, const GLMapView *view) {
        marqueeOffset = 0;
        marqueeTimer = 0;

        textFormat = TextFormat::createDefaultSystemTextFormat(owner->getTextSize());
        text = GLText::getInstance(textFormat);
        
        labelWidth = textFormat->getStringWidth(owner->getText());
        labelHeight = textFormat->getStringHeight();
        
        if (background == nullptr) {
            atlas = new TAK::Engine::Renderer::GLTextureAtlas2(256);
            background = new GLNinePatch(atlas, GLNinePatch::Size::SMALL, 16, 16, 5, 5, 10, 10);
        }
    }
    
    virtual ~LabelRenderContext();
};

GLNinePatch *LabelRenderContext::background = nullptr;

LabelRenderContext::~LabelRenderContext() {
//    delete textFormat;
}

GLStyleSpi *GLLabelPointStyle::getSpi() {
    static GLLabelPointStyle::Spi spi;
    return &spi;
}

GLLabelPointStyle::Spi::~Spi() { }

GLStyle *GLLabelPointStyle::Spi::create(const GLStyleSpiArg &object) {
    const Style *s = object.style;
    const Geometry *g = object.geometry;
    if (s == nullptr || g == nullptr)
        return nullptr;
    
    const LabelPointStyle *lps = dynamic_cast<const LabelPointStyle *>(s);
    if (lps == nullptr)
        return nullptr;
    
    if (dynamic_cast<const atakmap::feature::Point *>(g) != nullptr)
        return new ::Point(lps);
    else if (dynamic_cast<const atakmap::feature::LineString *>(g) != nullptr)
        return new ::LineString(lps);
    else if (dynamic_cast<const atakmap::feature::Polygon *>(g) != nullptr)
        return new ::Polygon(lps);
    else if (dynamic_cast<const GeometryCollection *>(g) != nullptr)
        return new GLGeometryCollectionStyle(s, this);
    return nullptr;
}

GLLabelPointStyle::GLLabelPointStyle(const LabelPointStyle *style)
: GLPointStyle(style) {
    text = style->getText();
    textSize = style->getTextSize();
    alignX = style->getHorizontalAlignment();
    alignY = style->getVerticalAlignment();
    paddingX = style->getPaddingX();
    paddingY = style->getPaddingY();
    int tc = style->getTextColor();

    textColorR = atakmap::renderer::Utils::colorExtract(tc, atakmap::renderer::Utils::Colors::RED) / 255.0f;
    textColorG = atakmap::renderer::Utils::colorExtract(tc, atakmap::renderer::Utils::Colors::GREEN) / 255.0f;
    textColorB = atakmap::renderer::Utils::colorExtract(tc, atakmap::renderer::Utils::Colors::BLUE) / 255.0f;
    textColorA = atakmap::renderer::Utils::colorExtract(tc, atakmap::renderer::Utils::Colors::ALPHA) / 255.0f;

    tc = style->getBackgroundColor();
    bgColorR = atakmap::renderer::Utils::colorExtract(tc, atakmap::renderer::Utils::Colors::RED) / 255.0f;
    bgColorG = atakmap::renderer::Utils::colorExtract(tc, atakmap::renderer::Utils::Colors::GREEN) / 255.0f;
    bgColorB = atakmap::renderer::Utils::colorExtract(tc, atakmap::renderer::Utils::Colors::BLUE) / 255.0f;
    bgColorA = atakmap::renderer::Utils::colorExtract(tc, atakmap::renderer::Utils::Colors::ALPHA) / 255.0f;
    drawBackground = (bgColorA > 0.0f);
    scrollMode = style->getScrollMode();
}

GLLabelPointStyle::~GLLabelPointStyle() { }

void GLLabelPointStyle::drawAt(const GLMapView *view, float x, float y, StyleRenderContext *ctx)
{
    LabelRenderContext *context = (LabelRenderContext *)ctx;

    LabelPointStyle::ScrollMode marquee;
    switch (scrollMode) {
    case LabelPointStyle::ScrollMode::DEFAULT:
        //if (GLMapSurface.SETTING_shortenLabels)
        //    marquee = feature::LabelPointStyle::ScrollMode::ON;
        //else
            marquee = LabelPointStyle::ScrollMode::OFF;
        break;
    case LabelPointStyle::ScrollMode::ON:
    case LabelPointStyle::ScrollMode::OFF:
        marquee = scrollMode;
        break;
    default:
            throw std::logic_error("scrollMode");
    }

    bool willMarquee = (marquee == LabelPointStyle::ScrollMode::ON && context->shouldMarquee);

    float renderLabelWidth;
    if (willMarquee)
        renderLabelWidth = MAX_TEXT_WIDTH;
    else
        renderLabelWidth = context->labelWidth;

    float renderLabelHeight = context->labelHeight;

    float labelOffsetX;
    if (alignX < 0)
        labelOffsetX = -renderLabelWidth - paddingX;
    else if (alignX == 0)
        labelOffsetX = -(renderLabelWidth / 2);
    else if (alignX > 0)
        labelOffsetX = 0.0f + paddingX;
    else
        throw std::logic_error("alignX");

    float labelOffsetY;
    if (alignY < 0)
        labelOffsetY = 0.0f + paddingY;
    else if (alignY == 0)
        labelOffsetY = -(renderLabelHeight / 2);
    else if (alignY > 0)
        labelOffsetY = -renderLabelHeight - paddingY;
    else
        throw std::logic_error("alignY");
    
    GLES20FixedPipeline::getInstance()->glPushMatrix();
    GLES20FixedPipeline::getInstance()->glTranslatef(x + labelOffsetX, y + labelOffsetY, 0);

    if (drawBackground) {
        GLES20FixedPipeline::getInstance()->glColor4f(bgColorR,
                                      bgColorG,
                                      bgColorB,
                                      bgColorA);
        
        context->background->draw(-4.0f,
                                  -context->text->getTextFormat()->getDescent() - context->text->getTextFormat()->getBaselineSpacing() - 2.0f,
                                  renderLabelWidth + 8.0f,
                                  renderLabelHeight + 4.0f,
                                  true);
    }

    if (willMarquee) {
        GLES20FixedPipeline::getInstance()->glPushMatrix();
        GLES20FixedPipeline::getInstance()->glTranslatef(context->marqueeOffset, 0, 0);
        context->text->draw(text,
                          textColorR,
                          textColorG,
                          textColorB,
                          textColorA,
                          context->marqueeOffset,
                          -context->marqueeOffset + MAX_TEXT_WIDTH);

        GLES20FixedPipeline::getInstance()->glPopMatrix();

        long deltaTime = view->animationDelta;
        float textEndX = context->marqueeOffset + context->labelWidth;
        if (context->marqueeTimer <= 0) {

            // return to neutral scroll and wait 3 seconds
            if (textEndX <= MAX_TEXT_WIDTH) {
                context->marqueeTimer = 3000;
                context->marqueeOffset = 0;
            } else {
                // animate at 10 pixels per second
                context->marqueeOffset -= (deltaTime * 0.02f);
                if (context->marqueeOffset + context->labelWidth <= MAX_TEXT_WIDTH) {
                    context->marqueeOffset = MAX_TEXT_WIDTH - context->labelWidth;
                    context->marqueeTimer = 2000;
                }
            }
        } else {
            context->marqueeTimer -= deltaTime;
        }
    } else {
        // XXX - handling of strings with newlines is implementation detail;
        //       user should have single interface for draw vs batch
        context->text->drawSplitString(text,
                                       textColorR,
                                       textColorG,
                                       textColorB,
                                       textColorA);
    }

    GLES20FixedPipeline::getInstance()->glPopMatrix();

}
void GLLabelPointStyle::batchAt(const GLMapView *view, GLRenderBatch *batch, float xpos, float ypos,
                            StyleRenderContext *ctx)
{
    LabelRenderContext *context = (LabelRenderContext *)ctx;

    LabelPointStyle::ScrollMode marquee;
    switch (scrollMode) {
    case LabelPointStyle::ScrollMode::DEFAULT:
        //if (GLMapSurface.SETTING_shortenLabels)
            //marquee = feature::LabelPointStyle::ScrollMode::ON;
        //else
            marquee = LabelPointStyle::ScrollMode::OFF;
        break;
    case LabelPointStyle::ScrollMode::ON:
    case LabelPointStyle::ScrollMode::OFF:
        marquee = scrollMode;
        break;
    default:
        throw std::logic_error("scrollMode");
    }

    bool willMarquee = (marquee == LabelPointStyle::ScrollMode::ON && context->shouldMarquee);

    float renderLabelWidth;
    if (willMarquee)
        renderLabelWidth = MAX_TEXT_WIDTH;
    else
        renderLabelWidth = context->labelWidth;

    float renderLabelHeight = context->labelHeight;

    float labelOffsetX;
    if (alignX < 0)
        labelOffsetX = -renderLabelWidth - paddingX;
    else if (alignX == 0)
        labelOffsetX = -(renderLabelWidth / 2);
    else if (alignX > 0)
        labelOffsetX = 0.0f + paddingX;
    else
        throw std::logic_error("alignX");

    float labelOffsetY;
    if (alignY < 0)
        labelOffsetY = 0.0f + paddingY;
    else if (alignY == 0)
        labelOffsetY = -(renderLabelHeight / 2);
    else if (alignY > 0)
        labelOffsetY = -renderLabelHeight - paddingY;
    else
        throw std::logic_error("alignY");

    float textRenderX = xpos + labelOffsetX;
    float textRenderY = ypos + labelOffsetY - context->textFormat->getBaselineSpacing();

    if (drawBackground) {
        context->background->batch(batch, textRenderX - 4.0f,
                                   textRenderY - context->textFormat->getDescent() - 2.0f,
                                   renderLabelWidth + 8.0f,
                                   context->labelHeight + 4.0f, bgColorR,
                                   bgColorG, bgColorB, bgColorA);
    }

    float scissorX0 = 0.0f;
    float scissorX1 = FLT_MAX;
    if (willMarquee) {
        scissorX0 = -context->marqueeOffset;
        scissorX1 = -context->marqueeOffset + renderLabelWidth;
    }

    context->text->batch(batch,
                       text,
                       textRenderX + context->marqueeOffset,
                       textRenderY,
                       textColorR,
                       textColorG,
                       textColorB,
                       textColorA,
                       scissorX0, scissorX1);

    if (willMarquee) {
        long deltaTime = view->animationDelta;

        float textEndX = context->marqueeOffset + context->labelWidth;
        if (context->marqueeTimer <= 0) {
            // return to neutral scroll and wait 3 seconds
            if (textEndX <= MAX_TEXT_WIDTH) {
                context->marqueeTimer = 3000;
                context->marqueeOffset = 0;
            } else {
                // animate at 10 pixels per second
                context->marqueeOffset -= (deltaTime * 0.02f);
                if (context->marqueeOffset + context->labelWidth <= MAX_TEXT_WIDTH) {
                    context->marqueeOffset = MAX_TEXT_WIDTH - context->labelWidth;
                    context->marqueeTimer = 2000;
                }
            }
        } else {
            context->marqueeTimer -= deltaTime;
        }
    }
}

bool GLLabelPointStyle::isBatchable(const GLMapView *view, GLGeometry *geometry, StyleRenderContext *ctx) {
#if DISABLE_LABEL_BATCHING
    return false;
#else
    return true;
#endif
}

StyleRenderContext *GLLabelPointStyle::createRenderContext(const GLMapView *view, GLGeometry *geometry) {
    return new LabelRenderContext(this, view);
}

void GLLabelPointStyle::releaseRenderContext(StyleRenderContext *ctx) {
    delete ctx;
}

const char *GLLabelPointStyle::getText() const {
    return this->text;
}

float GLLabelPointStyle::getTextSize() const {
    return this->textSize;
}


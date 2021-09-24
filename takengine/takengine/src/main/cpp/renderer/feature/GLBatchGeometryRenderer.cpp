
#include "renderer/GL.h"

#include "core/AtakMapView.h"

#include "renderer/feature/GLGeometry.h"
#include "renderer/feature/GLBatchGeometryRenderer.h"
#include "renderer/feature/GLBatchGeometry.h"
#include "renderer/feature/GLBatchPoint.h"
#include "renderer/feature/GLBatchPolygon.h"
#include "renderer/GLES20FixedPipeline.h"
#include "renderer/GLRenderBatch.h"
#include "renderer/RendererUtils.h"

using namespace atakmap::feature;
using namespace atakmap::renderer;
using namespace atakmap::renderer::map;
using namespace atakmap::renderer::feature;

const char *VECTOR_2D_VERT_SHADER_SRC =
    "uniform mat4 uProjection;\n"
    "uniform mat4 uModelView;\n"
    "attribute vec2 aVertexCoords;\n"
    "void main() {\n"
    "  gl_Position = uProjection * uModelView * vec4(aVertexCoords.xy, 0.0, 1.0);\n"
    "}";

const char *VECTOR_3D_VERT_SHADER_SRC =
    "uniform mat4 uProjection;\n"
    "uniform mat4 uModelView;\n"
    "attribute vec3 aVertexCoords;\n"
    "void main() {\n"
    "  gl_Position = uProjection * uModelView * vec4(aVertexCoords.xyz, 1.0);\n"
    "}";

const char *TEXTURE_2D_VERT_SHADER_SRC =
    "uniform mat4 uProjection;\n"
    "uniform mat4 uModelView;\n"
    "attribute vec2 aVertexCoords;\n"
    "attribute vec2 aTextureCoords;\n"
    "varying vec2 vTexPos;\n"
    "void main() {\n"
    "  vTexPos = aTextureCoords;\n"
    "  gl_Position = uProjection * uModelView * vec4(aVertexCoords.xy, 0.0, 1.0);\n"
    "}";

const char *MODULATED_TEXTURE_FRAG_SHADER_SRC =
    "precision mediump float;\n"
    "uniform sampler2D uTexture;\n"
    "uniform vec4 uColor;\n"
    "varying vec2 vTexPos;\n"
    "void main(void) {\n"
    "  gl_FragColor = uColor * texture2D(uTexture, vTexPos);\n"
    "}";

const char *GENERIC_VECTOR_FRAG_SHADER_SRC =
    "precision mediump float;\n"
    "uniform vec4 uColor;\n"
    "void main(void) {\n"
    "  gl_FragColor = uColor;\n"
    "}";

// XXX - disable render batch as batching for 9-patch is broken
#define RENDERBATCH_ENABLED 0

int loadShader(int type, const char *source) {
    int retval = glCreateShader(type);
    if (retval == GL_FALSE) {
        return 0;
    }

    glShaderSource(retval, 1, &source, nullptr);
    glCompileShader(retval);

    int success;
    glGetShaderiv(retval, GL_COMPILE_STATUS, &success);
    if (success == 0) {
        //Log.d(TAG, "FAILED TO LOAD SHADER: " + source);
        //String ^msg = glGetShaderInfoLog(retval);
        glDeleteShader(retval);
        //throw gcnew Exception(msg);
    }
    return retval;
}

int createProgram(int vertShader, int fragShader) {
    int retval = glCreateProgram();
    if (retval == GL_FALSE)
        return 0;
    glAttachShader(retval, vertShader);
    glAttachShader(retval, fragShader);
    glLinkProgram(retval);

    int success;
    glGetProgramiv(retval, GL_LINK_STATUS, &success);
    if (success == 0) {
        //String ^msg = glGetProgramInfoLog(retval);
        glDeleteProgram(retval);
        //throw new RuntimeException(msg);
    }
    return retval;
}

void GLBatchGeometryRenderer::InitializeInstanceFields() {
    pointsBuffer.resize(MAX_BUFFERED_2D_POINTS * 2);
    pointsVertsTexCoordsBuffer.resize(pointsBuffer.capacity() * 2 * 6 * 2);
    textureAtlasIndiciesBuffer.resize(pointsBuffer.capacity());
}

/*TODO--GLBatchGeometryRenderer::ComparatorAnonymousInnerClassHelper::ComparatorAnonymousInnerClassHelper()
{
}

int GLBatchGeometryRenderer::ComparatorAnonymousInnerClassHelper::Compare(GLBatchPoint ^lhs, GLBatchPoint ^rhs)
{
    int retval = lhs->textureId - rhs->textureId;
    if (retval != 0)
    {
        return retval;
    }
    retval = lhs->color - rhs->color;
    if (retval != 0)
    {
        return retval;
    }
    return safe_cast<int>(lhs->featureId - rhs->featureId);
}

GLBatchGeometryRenderer::ComparatorAnonymousInnerClassHelper2::ComparatorAnonymousInnerClassHelper2()
{
}

int GLBatchGeometryRenderer::ComparatorAnonymousInnerClassHelper2::Compare(GLBatchGeometry ^lhs, GLBatchGeometry ^rhs)
{
    return safe_cast<int>(lhs->featureId - rhs->featureId);
}*/

GLBatchGeometryRenderer::GLBatchGeometryRenderer()
: vectorProgram2d(2),
vectorProgram3d(3) {
    if (!InstanceFieldsInitialized) {
        InitializeInstanceFields();
        InstanceFieldsInitialized = true;
    }
    this->batch = nullptr;
}

GLBatchGeometryRenderer::~GLBatchGeometryRenderer() { }

/*TODO--SortedSet<GLBatchPoint^> ^GLBatchGeometryRenderer::renderablePoints()
{
    // don't bother creating a copy if we don't have any labels
    if (this->labels->Count < 1)
    {
        return this->batchPoints;
    }

    SortedSet<GLBatchPoint^> ^retval = gcnew SortedSet<GLBatchPoint^>(POINT_BATCH_COMPARATOR);
    Collection::AddAll(retval, this->labels);
    Collection::AddAll(retval, this->batchPoints);
    return retval;
}*/

/*TODO--void GLBatchGeometryRenderer::Batch::set(ICollection<GLBatchGeometry^> ^value) {
    sortedPolys->Clear();
    sortedLines->Clear();

    polys->Clear();
    lines->Clear();

    linesPoints = 0;

    loadingPoints->Clear();
    batchPoints->Clear();
    labels->Clear();

    for each (GLBatchGeometry ^g in value)
    {
        switch (g->zOrder)
        {
            case 0:
            {
#if 1
                GLBatchPoint ^point = static_cast<GLBatchPoint^>(g);
                if (point->textureKey != 0LL)
                {
                    batchPoints->Add(point);
                }
                else if (point->iconUri != nullptr)
                {
                    loadingPoints->AddLast(point);
                }
                else if (point->name != nullptr)
                {
                    labels->AddLast(point);
                }
#endif
                break;
            }
            case 2:
            {
                if ((static_cast<GLBatchPolygon^>(g))->fillColorA > 0.0f)
                {
                    sortedPolys->Add(g);
                    break;
                }

                // if the polygon isn't filled, treat it just like a line
            }
            case 1:
            {
                if ((static_cast<GLBatchLineString^>(g))->strokeColorA > 0.0f)
                {
                    sortedLines->Add(g);
                    linesPoints += (static_cast<GLBatchLineString^>(g))->targetNumPoints;
                }
                break;
            }
            default :
                throw gcnew System::ArgumentException();
        }
    }

    IEnumerator<GLBatchGeometry^> ^iter;

    iter = this->sortedLines->GetEnumerator();
    while (iter->MoveNext())
    {
        this->lines->AddLast(static_cast<GLBatchLineString^>(iter->Current));
    //    iter->remove();
    }
    this->sortedLines->Clear();

    iter = this->sortedPolys->GetEnumerator();
    while (iter->MoveNext())
    {
        this->polys->AddLast(static_cast<GLBatchPolygon^>(iter->Current));
    //    iter->remove();
    }
    this->sortedPolys->Clear();
}*/

void GLBatchGeometryRenderer::draw(const GLMapView *view)
{
    glDisable(GL_DEPTH_TEST);

    // reset the state to the defaults
//C# TO C++ CONVERTER TODO TASK: There is no C++ equivalent to 'unchecked' in this context:
//ORIGINAL LINE: this.state.color = unchecked((int)0xFFFFFFFF);
    this->state.color = 0xFFFFFFFF;
    this->state.lineWidth = 1.0f;
    this->state.texId = 0;

    int i = 0;
    glGetIntegerv(GL_ACTIVE_TEXTURE, &i);
    this->state.textureUnit = i;

    // polygons
    if (this->polys.capacity() > 0) {
        if (this->batch == NULL) {
            this->batch = new atakmap::renderer::GLRenderBatch(MAX_VERTS_PER_DRAW_ARRAYS);
        }

        GLES20FixedPipeline::getInstance()->glPushMatrix();

        // XXX - batch currently only supports 2D vertices

                        //JAVA TO C# CONVERTER WARNING: The original Java variable was marked 'final':
                        //ORIGINAL LINE: final int vertType;
        int vertType;
        if (true || view->scene.projection->is3D())
        {
            // XXX - force all polygons projected as pixels as stroking does
            //       not work properly. since vertices are in projected
            //       coordinate space units, width also needs to be
            //       specified as such. attempts to compute some nominal
            //       scale factor produces reasonable results at lower map
            //       resolutions but cause width to converge to zero (32-bit
            //       precision?) at higher resolutions
            vertType = GLGeometry::VERTICES_PIXEL;
        }
        else
        {
            vertType = GLGeometry::VERTICES_PROJECTED;

            GLES20FixedPipeline::getInstance()->glLoadMatrixf(view->sceneModelForwardMatrix/*VectorProgram*/);
        }

        bool inBatch = false;
        GLBatchPolygon *poly;
        auto end = this->polys.end();
        for (auto it = this->polys.begin(); it != end; ++it) {
            poly = static_cast<GLBatchPolygon *>(*it);
#if RENDERBATCH_ENABLED
            if (poly->isBatchable(view))
            {
                if (!inBatch)
                {
                    this->batch->begin();
                    inBatch = true;
                }
                poly->batch(view, this->batch, vertType);
            }
            else
#endif
            {
                // the geometry can't be batched right now -- kick out of
                // batch if necessary and draw
                if (inBatch)
                {
                    this->batch->end();
                    inBatch = false;
                }
                poly->draw(view, vertType);
            }
        }
        if (inBatch)
        {
            this->batch->end();
        }
        GLES20FixedPipeline::getInstance()->glPopMatrix();
    }

    // lines
    if (this->lines.size() > 0)
    {
#if 0
        this->batchDrawLinesProjected(view);
#else
        auto end = this->lines.end();
        for (auto it = this->lines.begin(); it != end; ++it) {
            (*it)->draw(view);
        }
#endif
    }

    // points

    // if the relative scaling has changed we need to reset the default text
    // and clear the texture atlas
    if (GLBatchPoint::iconAtlasDensity != atakmap::core::AtakMapView::DENSITY)
    {
        GLBatchPoint::ICON_ATLAS->release();
        GLBatchPoint::ICON_ATLAS = new TAK::Engine::Renderer::GLTextureAtlas2(1024, (size_t)ceil(32 * atakmap::core::AtakMapView::DENSITY));
    //TODO--    GLBatchPoint::iconLoaders->Clear();
        GLBatchPoint::iconAtlasDensity = atakmap::core::AtakMapView::DENSITY;
    }

    //IEnumerator<GLBatchPoint^> ^iter;

    // check all points with loading icons and move those whose icon has
    // loaded into the batchable list
    for (auto it = this->loadingPoints.begin(); it != this->loadingPoints.end(); ++it) {
        GLBatchPoint *point = *it;
        GLBatchPoint::getOrFetchIcon(view->getRenderContext(), point);
        if (point->getTextureKey() != 0ll) {
            this->batchPoints.add(point);
            *it = this->loadingPoints.back();
            this->loadingPoints.pop_back();
        }
    }

    // render all labels
    if (this->labels.size() > 0) {
        if (this->batch == NULL) {
            this->batch = new GLRenderBatch(MAX_VERTS_PER_DRAW_ARRAYS);
        }

        bool inBatch = false;
        
        for (std::vector<GLBatchPoint *>::iterator it = this->labels.begin(); it != this->labels.end(); ++it) {
            GLBatchPoint *g = *it;
#if RENDERBATCH_ENABLED
            if (g->isBatchable(view)) {
                if (!inBatch)
                {
                    this->batch->begin();
                    inBatch = true;
                }
                g->batch(view, this->batch);
            }
            else
#endif
            {
                // the geometry can't be batched right now -- kick out of
                // batch if necessary and draw
                if (inBatch) {
                    this->batch->end();
                    inBatch = false;
                }
                g->draw(view);
            }
        }
        if (inBatch)
        {
            this->batch->end();
        }
    }

    // render points with icons
    if (this->batchPoints.size() > POINT_BATCHING_THRESHOLD || (this->batchPoints.size() > 1 &&
        (/*TODO: !atakmap::cpp_cli::renderer::GLRenderContext::SETTING_displayLabels*/false || view->drawMapScale < GLBatchPoint::defaultLabelRenderScale)))
    {
        // batch if there are many points on the screen or if we have more
        // than one point and labels are not going to be drawn
        this->batchDrawPoints(view);
    }
    else if (this->batchPoints.size() > 0) {
        if (this->batch == nullptr) {
            this->batch = new GLRenderBatch(2048);
        }

        bool inBatch = false;
        
        for (BatchPointsSet::iterator it = this->batchPoints.begin(); it != this->batchPoints.end(); ++it) {
            GLBatchPoint *point = *it;
            if (point->isBatchable(view)) {
                if(!inBatch) {
                    this->batch->begin();
                    inBatch = true;
                }
                point->batch(view, this->batch);
            }
            else {
                // the point can't be batched right now -- render the point
                // surrounded by end/begin to keep the batch in a valid
                // state
                if (inBatch) {
                    this->batch->end();
                    inBatch = false;
                }
                point->draw(view);
            }
        }
        
        if (inBatch) {
            this->batch->end();
        }
    }

    glEnable(GL_DEPTH_TEST);
}

void GLBatchGeometryRenderer::batchDrawLinesProjected(const GLMapView *view) {
    
    GLES20FixedPipeline::getInstance()->glPushMatrix();
    GLES20FixedPipeline::getInstance()->glLoadMatrixf(view->sceneModelForwardMatrix);

    VectorProgram *vectorProgram;
    int maxBufferedPoints;
    if (view->scene.projection->is3D()) {
        glUseProgram(this->vectorProgram3d.programHandle);
        vectorProgram = &this->vectorProgram3d;
        maxBufferedPoints = MAX_BUFFERED_3D_POINTS;
    }
    else {
        glUseProgram(this->vectorProgram2d.programHandle);
        vectorProgram = &this->vectorProgram2d;
        maxBufferedPoints = MAX_BUFFERED_2D_POINTS;
    }

    float scratchMatrix[16];
    GLES20FixedPipeline::getInstance()->readMatrix(GLES20FixedPipeline::MatrixMode::MM_GL_PROJECTION, scratchMatrix);

    glUniformMatrix4fv(vectorProgram->uProjectionHandle, 1, false, scratchMatrix);

    GLES20FixedPipeline::getInstance()->readMatrix(GLES20FixedPipeline::MatrixMode::MM_GL_MODELVIEW, scratchMatrix);
    glUniformMatrix4fv(vectorProgram->uModelViewHandle, 1, false, scratchMatrix);

    // sync the current color with the shader
    glUniform4f(vectorProgram->uColorHandle,
        ((this->state.color >> 16) & 0xFF) / 255.0f,
        ((this->state.color >> 8) & 0xFF) / 255.0f,
        ((this->state.color) & 0xFF) / 255.0f,
        ((this->state.color >> 24) & 0xFF) / 255.0f);

    // sync the current line width
    glLineWidth(this->state.lineWidth);

//TODO:    this->pointsBuffer->clear();
    //array<float> ^linesBuffer = this->pointsBuffer;
    float *linesBuffer = this->pointsBuffer.access();
    size_t linesBufferLength = this->pointsBuffer.limit();
    
    int position = 0;
    int limit = 0;
    
    for (std::vector<GLBatchLineString *>::iterator it = this->lines.begin(); it != this->lines.end(); ++it) {
        GLBatchLineString *line = *it;
        if (line->getNumPoints() < 2) {
            continue;
        }

        if (line->getStrokeColor() != this->state.color) {
            if (position > 0) {
                limit = position;
                position = 0;
                renderLinesBuffers(vectorProgram, linesBuffer, GL_LINES, limit / vectorProgram->vertSize);
                limit = 0;
            }

            glUniform4f(vectorProgram->uColorHandle,
                Utils::colorExtract(line->getStrokeColor(), Utils::RED) / 255.0f,
                Utils::colorExtract(line->getStrokeColor(), Utils::GREEN) / 255.0f,
                Utils::colorExtract(line->getStrokeColor(), Utils::BLUE) / 255.0f,
                Utils::colorExtract(line->getStrokeColor(), Utils::ALPHA) / 255.0f);

            this->state.color = line->getStrokeColor();
        }

        if (line->getStrokeWidth() != this->state.lineWidth) {
            if (position > 0) {
                limit = position;
                position = 0;
                renderLinesBuffers(vectorProgram, linesBuffer, GL_LINES, limit / vectorProgram->vertSize);
                limit = 0;
            }

            glLineWidth(line->getStrokeWidth());
            this->state.lineWidth = line->getStrokeWidth();
        }

        line->projectVertices(view, GLGeometry::VERTICES_PROJECTED);
        if (((line->getNumPoints() - 1) * 2) > maxBufferedPoints)
        {
            // the line has more points than can be buffered -- render it
            // immediately as a strip and don't touch the buffer.
            // technically, this will violate Z-order, but since we have the
            // same state (color, line-width) with everything currently
            // batched we shouldn't be able to distinguish Z anyway...
            renderLinesBuffers(vectorProgram, line->projectedVertices.access(), GL_LINE_STRIP, line->getNumPoints());
        }
        else
        {
            int remainingSegments = line->getNumPoints() - 1;
            int numSegsToExpand;
            int off = 0;
            while (remainingSegments > 0)
            {
                float *vertsPtr = &line->projectedVertices[0];
                numSegsToExpand = std::min((int)linesBufferLength / (2 * vectorProgram->vertSize), remainingSegments);
                expandLineStringToLines(
                    vectorProgram->vertSize,
                    vertsPtr,
                    off,
                    linesBuffer,
                    position,
                    numSegsToExpand + 1);

                position += (numSegsToExpand * (2 * vectorProgram->vertSize));
                off += numSegsToExpand * vectorProgram->vertSize;
                remainingSegments -= numSegsToExpand;
                if (linesBufferLength < (2 * vectorProgram->vertSize)) {
                    limit = position;
                    position = 0;
                    renderLinesBuffers(vectorProgram, linesBuffer, GL_LINES, limit / vectorProgram->vertSize);
                    limit = 0;
                }
            }
        }
    }

    if (position > 0) {
        limit = position;
        position = 0;
        renderLinesBuffers(vectorProgram, linesBuffer, GL_LINES, linesBufferLength / vectorProgram->vertSize);
        limit = 0;
    }

    // sync the current color with the pipeline
    GLES20FixedPipeline::getInstance()->glColor4f(
        Utils::colorExtract(this->state.color, Utils::RED) / 255.0f,
        Utils::colorExtract(this->state.color, Utils::GREEN) / 255.0f,
        Utils::colorExtract(this->state.color, Utils::BLUE) / 255.0f,
        Utils::colorExtract(this->state.color, Utils::ALPHA) / 255.0f);

    GLES20FixedPipeline::getInstance()->glPopMatrix();
}

void GLBatchGeometryRenderer::expandLineStringToLines(int size, float *verts, int vertsOff, float *lines, int linesOff, int count)
{
    float *pVerts = verts + vertsOff;
    float *pLines = lines + linesOff;
    const int segElems = (2 * size);
    const int cpySize = sizeof(float)*segElems;
    for (int i = 0; i < count - 1; i++) {
        memcpy(pLines, pVerts, cpySize);
        pLines += segElems;
        pVerts += size;
    }
}

void GLBatchGeometryRenderer::fillVertexArrays(float *translations, int *texAtlasIndices, int iconSize, int textureSize, float *vertsTexCoords, int count)
{
    float *pVertsTexCoords = vertsTexCoords;
    float tx;
    float ty;

    float vertices[12];
    vertices[0] = static_cast<float>(-iconSize / 2);  // upper-left
    vertices[1] = static_cast<float>(-iconSize / 2);
    vertices[2] = static_cast<float>(iconSize / 2);   // upper-right
    vertices[3] = static_cast<float>(-iconSize / 2);
    vertices[4] = static_cast<float>(-iconSize / 2);  // lower-left
    vertices[5] = static_cast<float>(iconSize / 2);
    vertices[6] = static_cast<float>(iconSize / 2);   // upper-right
    vertices[7] = static_cast<float>(-iconSize / 2);
    vertices[8] = static_cast<float>(-iconSize / 2);  // lower-left
    vertices[9] = static_cast<float>(iconSize / 2);
    vertices[10] = static_cast<float>(iconSize / 2);  // lower-right
    vertices[11] = static_cast<float>(iconSize / 2);

    float iconX;
    float iconY;

    float fIconSize = static_cast<float>(iconSize - 1);
    float fTextureSize = static_cast<float>(textureSize);

    int numIconsX = textureSize / iconSize;
    int iconIndex;

    for (int i = 0; i < count; i++) {
        tx = translations[i * 2];
        ty = translations[i * 2 + 1];

        iconIndex = texAtlasIndices[i];

        iconX = static_cast<float>((iconIndex % numIconsX) * iconSize);
        iconY = static_cast<float>((iconIndex / numIconsX) * iconSize);

        (*pVertsTexCoords++) = vertices[0] + tx;
        (*pVertsTexCoords++) = vertices[1] + ty;
        (*pVertsTexCoords++) = iconX / fTextureSize;                 // upper-left
        (*pVertsTexCoords++) = (iconY + fIconSize) / fTextureSize;
        (*pVertsTexCoords++) = vertices[2] + tx;
        (*pVertsTexCoords++) = vertices[3] + ty;
        (*pVertsTexCoords++) = (iconX + fIconSize) / fTextureSize;   // upper-right
        (*pVertsTexCoords++) = (iconY + fIconSize) / fTextureSize;
        (*pVertsTexCoords++) = vertices[4] + tx;
        (*pVertsTexCoords++) = vertices[5] + ty;
        (*pVertsTexCoords++) = iconX / fTextureSize;                 // lower-left
        (*pVertsTexCoords++) = iconY / fTextureSize;
        (*pVertsTexCoords++) = vertices[6] + tx;
        (*pVertsTexCoords++) = vertices[7] + ty;
        (*pVertsTexCoords++) = (iconX + fIconSize) / fTextureSize;   // upper-right
        (*pVertsTexCoords++) = (iconY + fIconSize) / fTextureSize;
        (*pVertsTexCoords++) = vertices[8] + tx;
        (*pVertsTexCoords++) = vertices[9] + ty;
        (*pVertsTexCoords++) = iconX / fTextureSize;                 // lower-left
        (*pVertsTexCoords++) = iconY / fTextureSize;
        (*pVertsTexCoords++) = vertices[10] + tx;
        (*pVertsTexCoords++) = vertices[11] + ty;
        (*pVertsTexCoords++) = (iconX + fIconSize) / fTextureSize;   // lower-right
        (*pVertsTexCoords++) = iconY / fTextureSize;
    }
}

void GLBatchGeometryRenderer::renderLinesBuffers(VectorProgram *vectorProgram, const float *buf, int mode, int numPoints)
{
    glVertexAttribPointer(vectorProgram->aVertexCoordsHandle, vectorProgram->vertSize, GL_FLOAT, false, 0, buf);

    glEnableVertexAttribArray(vectorProgram->aVertexCoordsHandle);
    glDrawArrays(mode, 0, numPoints);
    glDisableVertexAttribArray(vectorProgram->aVertexCoordsHandle);
}

void GLBatchGeometryRenderer::batchDrawPoints(const GLMapView *view)
{
    this->state.color = static_cast<int>(0xFFFFFFFF);
    this->state.texId = 0;

    GLES20FixedPipeline::getInstance()->glPushMatrix();
    GLES20FixedPipeline::getInstance()->glLoadIdentity();

    glEnable(GL_BLEND);
    glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

    if (this->textureProgram == 0)
    {
        int vertShader = loadShader(GL_VERTEX_SHADER, TEXTURE_2D_VERT_SHADER_SRC);
        int fragShader = loadShader(GL_FRAGMENT_SHADER, MODULATED_TEXTURE_FRAG_SHADER_SRC);

        this->textureProgram = createProgram(vertShader, fragShader);
        glUseProgram(this->textureProgram);

        this->tex_uProjectionHandle = glGetUniformLocation(this->textureProgram, "uProjection");

        this->tex_uModelViewHandle = glGetUniformLocation(this->textureProgram, "uModelView");

        this->tex_uTextureHandle = glGetUniformLocation(this->textureProgram, "uTexture");

        this->tex_uColorHandle = glGetUniformLocation(this->textureProgram, "uColor");

        this->tex_aVertexCoordsHandle = glGetAttribLocation(this->textureProgram, "aVertexCoords");
        this->tex_aTextureCoordsHandle = glGetAttribLocation(this->textureProgram, "aTextureCoords");
    }
    else
    {
        glUseProgram(this->textureProgram);
    }

    float scratchMatrix[16];
    GLES20FixedPipeline::getInstance()->readMatrix(GLES20FixedPipeline::MatrixMode::MM_GL_PROJECTION, scratchMatrix);
    glUniformMatrix4fv(this->tex_uProjectionHandle, 1, false, scratchMatrix);

    GLES20FixedPipeline::getInstance()->readMatrix(GLES20FixedPipeline::MatrixMode::MM_GL_MODELVIEW, scratchMatrix);
    glUniformMatrix4fv(this->tex_uModelViewHandle, 1, false, scratchMatrix);

    // work with texture0
    GLES20FixedPipeline::getInstance()->glActiveTexture(this->state.textureUnit);
    glUniform1i(this->tex_uTextureHandle, this->state.textureUnit - GL_TEXTURE0);

    // sync the current color with the shader
    glUniform4f(this->tex_uColorHandle,
        Utils::colorExtract(this->state.color, Utils::RED) / 255.0f,
        Utils::colorExtract(this->state.color, Utils::GREEN) / 255.0f,
        Utils::colorExtract(this->state.color, Utils::BLUE) / 255.0f,
        Utils::colorExtract(this->state.color, Utils::ALPHA) / 255.0f);

    for (BatchPointsSet::iterator it = this->batchPoints.begin(); it != this->batchPoints.end(); ++it) {
        GLBatchPoint *point = static_cast<GLBatchPoint *>(*it);

        if (point->iconUri == "") {
            continue;
        }

        if (point->textureKey == 0LL) {
            GLBatchPoint::getOrFetchIcon(view->getRenderContext(), point);
            continue;
        }

        if (this->state.texId != point->textureId) {
            this->renderPointsBuffers(view);

            this->pointsBuffer.position(0);
            this->textureAtlasIndiciesBuffer.position(0);

            this->state.texId = point->textureId;
            glBindTexture(GL_TEXTURE_2D, this->state.texId);
        }
        
        if (this->state.texId == 0) {
            continue;
        }

        if (point->color != this->state.color) {
            this->renderPointsBuffers(view);

            this->pointsBuffer.position(0);
            this->textureAtlasIndiciesBuffer.position(0);

            glUniform4f(tex_uColorHandle,
                Utils::colorExtract(point->color, Utils::RED) / 255.0f,
                Utils::colorExtract(point->color, Utils::GREEN) / 255.0f,
                Utils::colorExtract(point->color, Utils::BLUE) / 255.0f,
                Utils::colorExtract(point->color, Utils::ALPHA) / 255.0f);

            this->state.color = point->color;
        }

        //Unsafe::setFloats(this->pointsBufferPtr + pointsBufferPos, safe_cast<float>(point->longitude), safe_cast<float>(point->latitude));
        pointsBuffer.put(static_cast<float>(point->longitude));
        pointsBuffer.put(static_cast<float>(point->latitude));

        this->textureAtlasIndiciesBuffer.put(point->textureIndex);

        if ((pointsBuffer.position() == pointsBuffer.limit())
            || ((this->textureAtlasIndiciesBuffer.limit() - this->textureAtlasIndiciesBuffer.position()) == 0))
        {
            this->renderPointsBuffers(view);
            this->textureAtlasIndiciesBuffer.position(0);
            this->pointsBuffer.position(0);
        }
    }

    if (textureAtlasIndiciesBuffer.position() > 0) {
        this->renderPointsBuffers(view);
        textureAtlasIndiciesBuffer.position(0);
        pointsBuffer.position(0);
    }

    GLES20FixedPipeline::getInstance()->glPopMatrix();

    glDisable(GL_BLEND);

    if (this->state.texId != 0) {
        glBindTexture(GL_TEXTURE_2D, 0);
    }

    // sync the current color with the pipeline
    GLES20FixedPipeline::getInstance()->glColor4f(
        Utils::colorExtract(this->state.color, Utils::RED) / 255.0f,
        Utils::colorExtract(this->state.color, Utils::GREEN) / 255.0f,
        Utils::colorExtract(this->state.color, Utils::BLUE) / 255.0f,
        Utils::colorExtract(this->state.color, Utils::ALPHA) / 255.0f);
}

void GLBatchGeometryRenderer::renderPointsBuffers(const GLMapView *view) {
    textureAtlasIndiciesBuffer.flip();
    
    if ((textureAtlasIndiciesBuffer.limit() - textureAtlasIndiciesBuffer.position()) < 1) {
        return;
    }

    this->pointsBuffer.flip();
    
    view->forward(pointsBuffer.access(), pointsBuffer.limit() / 2, pointsBuffer.access());

    this->pointsBuffer.position(0);

    float *pointsPtr = &this->pointsBuffer[0];
    int *atlasPtr = &this->textureAtlasIndiciesBuffer[0];
    float *texPtr = &this->pointsVertsTexCoordsBuffer[0];

    size_t imageWidth;
    GLBatchPoint::ICON_ATLAS->getImageWidth(&imageWidth, 0);
    
    fillVertexArrays(
        pointsPtr,
        atlasPtr,
        //GLBatchPoint::ICON_ATLAS->getImageWidth(0),
        imageWidth,
        GLBatchPoint::ICON_ATLAS->getTextureSize(),
        texPtr,
        textureAtlasIndiciesBuffer.limit()); // fixed size

    float *tp = &this->pointsVertsTexCoordsBuffer[0];
    glVertexAttribPointer(this->tex_aVertexCoordsHandle, 2, GL_FLOAT, false, 16, tp);
    glEnableVertexAttribArray(this->tex_aVertexCoordsHandle);

    glVertexAttribPointer(this->tex_aTextureCoordsHandle, 2, GL_FLOAT, false, 16, tp + 2);
    glEnableVertexAttribArray(this->tex_aTextureCoordsHandle);

    GLsizei remaining = textureAtlasIndiciesBuffer.limit() - textureAtlasIndiciesBuffer.position();
    GLsizei iconsPerPass = MAX_VERTS_PER_DRAW_ARRAYS / 6;
    int off = 0;
    do
    {
        // XXX - note that we could use triangle strips here, but we would
        //       need a degenerate triangle for every icon except the last
        //       one, meaning that all icons except the last would require
        //       6 vertices
        glDrawArrays(GL_TRIANGLES, off * 6, std::min(remaining, iconsPerPass) * 6);

        remaining -= iconsPerPass;
        off += iconsPerPass;
    } while (remaining > 0);

    glDisableVertexAttribArray(this->tex_aVertexCoordsHandle);
    glDisableVertexAttribArray(this->tex_aTextureCoordsHandle);

    //this->pointsBuffer->position(this->pointsBuffer->limit());
    //this->textureAtlasIndicesBuffer->position(this->textureAtlasIndicesBuffer->limit());
}

void GLBatchGeometryRenderer::start() { }

void GLBatchGeometryRenderer::stop() { }

void GLBatchGeometryRenderer::release() {
    this->lines.resize(0);
    this->polys.resize(0);

    this->batchPoints.clear();
    this->loadingPoints.resize(0);
    this->labels.resize(0);

    this->batch = nullptr;
}

GLBatchGeometryRenderer::VectorProgram::VectorProgram(int vertSize)
: vertSize(vertSize) {

    const char *vertShaderSrc;
    switch (this->vertSize) {
        case 2 :
            vertShaderSrc = VECTOR_2D_VERT_SHADER_SRC;
            break;
        case 3 :
            vertShaderSrc = VECTOR_3D_VERT_SHADER_SRC;
            break;
        default :
            throw std::logic_error("vertSize");
    }
    int vertShader = loadShader(GL_VERTEX_SHADER, vertShaderSrc);
    int fragShader = loadShader(GL_FRAGMENT_SHADER, GENERIC_VECTOR_FRAG_SHADER_SRC);

    this->programHandle = createProgram(vertShader, fragShader);
    glUseProgram(this->programHandle);

    this->uProjectionHandle = glGetUniformLocation(this->programHandle, "uProjection");

    this->uModelViewHandle = glGetUniformLocation(this->programHandle, "uModelView");

    this->uColorHandle = glGetUniformLocation(this->programHandle, "uColor");

    this->aVertexCoordsHandle = glGetAttribLocation(this->programHandle, "aVertexCoords");
}

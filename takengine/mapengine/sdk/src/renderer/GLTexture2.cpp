#include "renderer/GLTexture2.h"

#include <algorithm>
#include <cassert>

#include <GLES2/gl2ext.h>

#include "formats/s3tc/S3TC.h"
#include "math/Matrix.h"
#include "renderer/GLES20FixedPipeline.h"
#include "util/DataOutput2.h"
#include "util/MemBuffer2.h"
#include "util/Memory.h"

using namespace TAK::Engine::Renderer;

using namespace TAK::Engine::Formats::S3TC;
using namespace TAK::Engine::Util;

namespace {
    int nextPowerOf2(const int v) NOTHROWS
    {
        int value = v;
        --value;
        value = (value >> 1) | value;
        value = (value >> 2) | value;
        value = (value >> 4) | value;
        value = (value >> 8) | value;
        value = (value >> 16) | value;
        ++value;
        return value;
    }

    bool glCheckForError(const GLenum code) NOTHROWS
    {
        bool retval = false;
        while (true) {
            const GLenum errv = glGetError();
            retval |= (errv == code);
            if (errv == GL_NO_ERROR)
                break;
        }
        return retval;
    }
}

/*************************************************************************/
// Private functions

void GLTexture2::apply() NOTHROWS
{
    if (!id)
        return;

    int t;
    glGetIntegerv(GL_TEXTURE_2D, &t);
    glBindTexture(GL_TEXTURE_2D, id);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, magFilter);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, minFilter);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, wrapS);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, wrapT);
    glBindTexture(GL_TEXTURE_2D, t);
}


/*************************************************************************/
// Public API - construction, setup, teardown

GLTexture2::GLTexture2(const int w, const int h, const int format, const int type) NOTHROWS :
    id(0),
    format(format),
    type(type),
    width(nextPowerOf2(w)),
    height(nextPowerOf2(h)),
    minFilter(GL_NEAREST),
    magFilter(GL_LINEAR),
    wrapS(GL_CLAMP_TO_EDGE),
    wrapT(GL_CLAMP_TO_EDGE),
    needsApply(false),
    compressed(false)
{}

GLTexture2::GLTexture2(int w, int h, Bitmap2::Format f) NOTHROWS:
    id(0),
    format(0),
    type(0),
    width(nextPowerOf2(w)),
    height(nextPowerOf2(h)),
    minFilter(GL_NEAREST),
    magFilter(GL_LINEAR),
    wrapS(GL_CLAMP_TO_EDGE),
    wrapT(GL_CLAMP_TO_EDGE),
    needsApply(false),
    compressed(false)
{
    if (f == Bitmap2::ARGB32)
        f = Bitmap2::RGBA32;
    else if (f == Bitmap2::BGRA32)
        f = Bitmap2::RGBA32;
    else if (f == Bitmap2::BGR24)
        f = Bitmap2::RGB24;
    GLTexture2_getFormatAndDataType(&format, &type, f);
}

GLTexture2::~GLTexture2() NOTHROWS
{
    if (id) {
        Logger_log(TELL_Warning, "Texture leaking, width=%u, height=%u", width, height);
    }
}
void GLTexture2::init() NOTHROWS
{
    if (id == 0) {
        int t;
        glGetIntegerv(GL_TEXTURE_2D, &t);

        // clear any error codes
        //glCheckForError(GL_NONE);
        initInternal();
        glTexImage2D(GL_TEXTURE_2D, 0, format,
                     width, height, 0,
                     format, type, NULL);

        // check for out of memory
        if (glCheckForError(GL_OUT_OF_MEMORY)) {
            Logger_log(TELL_Warning, "GLTexture2::init() failed to generate texture");
            if (id) {
                glDeleteTextures(1, &id);
                id = 0;
            }
        }

        // drop through to bind original texture
        glBindTexture(GL_TEXTURE_2D, t);

        needsApply = !id;
    }
}
bool GLTexture2::initInternal() NOTHROWS
{
    // generate the texture
    glGenTextures(1, &id);

    // check for out of memory
    if (glCheckForError(GL_OUT_OF_MEMORY)) {
        Logger_log(TELL_Warning, "GLTexture2::init() failed to generate texture");
        if (id) {
            glDeleteTextures(1, &id);
            id = 0;
        }
        return false;
    }

    glBindTexture(GL_TEXTURE_2D, id);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, magFilter);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, minFilter);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, wrapS);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, wrapT);

    return !!id;
}

void GLTexture2::release() NOTHROWS
{
    if (id) {
        glDeleteTextures(1, &id);
        id = 0;
    }
}


/*************************************************************************/
// Public API - Member get/sets

int GLTexture2::getType() const NOTHROWS
{
    return type;
}
int GLTexture2::getFormat() const NOTHROWS
{
    return format;
}
bool GLTexture2::isCompressed() const NOTHROWS
{
    return compressed;
}
int GLTexture2::getTexId() NOTHROWS
{
    if (needsApply) {
        needsApply = false;
        apply();
    }
    return id;
}

int GLTexture2::getTexWidth() const NOTHROWS
{
    return width;
}

int GLTexture2::getTexHeight() const NOTHROWS
{
    return height;
}

int GLTexture2::getWrapT() const NOTHROWS
{
    return wrapT;
}

int GLTexture2::getWrapS() const NOTHROWS
{
    return wrapS;
}

int GLTexture2::getMagFilter() const NOTHROWS
{
    return magFilter;
}

int GLTexture2::getMinFilter() const NOTHROWS
{
    return minFilter;
}

void GLTexture2::setWrapS(int wrapS) NOTHROWS
{
    this->wrapS = wrapS;
    needsApply = true;
}

void GLTexture2::setWrapT(int wrapT) NOTHROWS
{
    this->wrapT = wrapT;
    needsApply = true;
}

void GLTexture2::setMinFilter(int minFilter) NOTHROWS
{
    this->minFilter = minFilter;
    needsApply = true;
}

void GLTexture2::setMagFilter(int magFilter) NOTHROWS
{
    this->magFilter = minFilter;
    needsApply = true;
}


/*************************************************************************/
// Public API - texture loading

TAKErr GLTexture2::load(const Bitmap2 &bitmap, const int x, const int y) NOTHROWS
{
    TAKErr code;
    Bitmap2::Format tgtFmt;
    
    code = GLTexture2_getBitmapFormat(&tgtFmt, this->format, this->type);
    TE_CHECKRETURN_CODE(code);

    std::size_t pixelSize;
    Bitmap2_formatPixelSize(&pixelSize, bitmap.getFormat());

    if (tgtFmt == bitmap.getFormat() && bitmap.getStride() == (bitmap.getWidth()*pixelSize)) {
        load(bitmap.getData(), x, y, bitmap.getWidth(), bitmap.getHeight());
    } else {
        Bitmap2 converted(bitmap, tgtFmt);
        load(converted.getData(), x, y, converted.getWidth(), converted.getHeight());
    }

    return TE_Ok;
}

TAKErr GLTexture2::load(const Bitmap2 &bitmap) NOTHROWS
{
    return load(bitmap, 0, 0);
}

void GLTexture2::load(const atakmap::renderer::Bitmap &bitmap, const int x, const int y) NOTHROWS
{
    load(bitmap.data, x, y, bitmap.width, bitmap.height);
}

void GLTexture2::load(const atakmap::renderer::Bitmap &bitmap) NOTHROWS
{
    load(bitmap, 0, 0);
}

void GLTexture2::load(const void *data, const int x, const int y, const int w, const int h) NOTHROWS
{
    if (!id && x == 0 && y == 0 && w == width && h == height) {
        if (!initInternal())
            return;

        glBindTexture(GL_TEXTURE_2D, id);
        glTexImage2D(GL_TEXTURE_2D, 0, format,
                     width, height, 0,
                     format, type, data);
        glBindTexture(GL_TEXTURE_2D, 0);
        return;
    }

    init();
    if (id) {
		try {
			glBindTexture(GL_TEXTURE_2D, id);
#if 0
            // XXX - not currently working, only first chunk shows up, rest of texture is black
#ifdef _MSC_VER
            // XXX - D3D is choking on upload of large images. need to stream the data
            if (w > 2048 || h > 2048) {
                Bitmap2::Format tgtFmt;
                GLTexture2_getBitmapFormat(&tgtFmt, this->format, this->type);
                std::size_t size;
                Bitmap2_formatPixelSize(&size, tgtFmt);

                // the transfer buffer -- must hold at least one line
                const std::size_t transferSize = std::max((std::size_t)w*size, 4096u * 256u);

                // compute the number of lines to upload per pass
                const std::size_t linesPerPass = transferSize / ((std::size_t)w*size);

                // transfer the source data to the texture in chunks
                for (std::size_t i = 0u; i < h; i += linesPerPass) {
                    // compute the number of lines to cpoy this pass
                    const std::size_t linesThisPass = std::min(linesPerPass, (std::size_t)h - (i*linesPerPass));

                    // upload the chunk to the texture
                    glTexSubImage2D(GL_TEXTURE_2D, 0, x, y+(i*linesPerPass), w, linesThisPass,
                        format, type, (const uint8_t *)data + (i*linesPerPass*w*size));
                }
            } else
#endif
#endif
            glTexSubImage2D(GL_TEXTURE_2D, 0, x, y, w, h,
                    format, type, data);
			glBindTexture(GL_TEXTURE_2D, 0);
		}
		catch (...)
		{
			Logger_log(TELL_Error, "Error loading texture");
		}
    }
}



/*************************************************************************/
// Public API - Drawing

void GLTexture2::draw(const std::size_t numCoords, const int type, const void *textureCoordinates, const void *vertexCoordinates) const NOTHROWS
{
    draw(numCoords, type, textureCoordinates, type, vertexCoordinates);
}
void GLTexture2::draw(const std::size_t numCoords,
                      const int texType, const void *textureCoordinates,
                      const int vertType, const void *vertexCoordinates) const NOTHROWS
{
    GLTexture2_draw(id, GL_TRIANGLE_FAN, numCoords, texType, textureCoordinates, vertType, vertexCoordinates);
}

/*************************************************************************/
// Public API - Static Drawing

void TAK::Engine::Renderer::GLTexture2_draw(const int texId,
                                            const int mode,
                                            const std::size_t numCoords,
                                            const int texType, const void *textureCoordinates,
                                            const int vertType, const void *vertexCoordinates) NOTHROWS
{
    GLTexture2_draw(texId, mode, numCoords,
                    2, texType, textureCoordinates,
                    2, vertType, vertexCoordinates);
}

void TAK::Engine::Renderer::GLTexture2_draw(const int texId,
                                            const int mode,
                                            const std::size_t numCoords,
                                            const std::size_t texSize, const int texType, const void *textureCoordinates,
                                            const std::size_t vertSize, const int vertType, const void *vertexCoordinates) NOTHROWS
{
    using namespace atakmap::renderer;

    if (texId == 0)
        return;

    GLES20FixedPipeline *fixedPipe = GLES20FixedPipeline::getInstance();

    fixedPipe->glEnableClientState(GLES20FixedPipeline::ClientState::CS_GL_VERTEX_ARRAY);
    fixedPipe->glEnableClientState(GLES20FixedPipeline::ClientState::CS_GL_TEXTURE_COORD_ARRAY);
    glEnable(GL_BLEND);
    glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

    fixedPipe->glVertexPointer(vertSize, vertType, 0, vertexCoordinates);

    fixedPipe->glTexCoordPointer(texSize, texType, 0, textureCoordinates);
    glBindTexture(GL_TEXTURE_2D, texId);

    fixedPipe->glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
    fixedPipe->glDrawArrays(mode, 0, numCoords);

    fixedPipe->glDisableClientState(GLES20FixedPipeline::ClientState::CS_GL_VERTEX_ARRAY);
    fixedPipe->glDisableClientState(GLES20FixedPipeline::ClientState::CS_GL_TEXTURE_COORD_ARRAY);
    glDisable(GL_BLEND);
}

void TAK::Engine::Renderer::GLTexture2_draw(const int texId,
                                            const int mode,
                                            const std::size_t numCoords,
                                            const int texType, const void *textureCoordinates,
                                            const int vertType, const void *vertexCoordinates,
                                            const int idxType, const void *indices) NOTHROWS
{
    GLTexture2_draw(texId,
                    mode,
                    numCoords,
                    2u, texType, textureCoordinates,
                    2u, vertType, vertexCoordinates,
                    idxType, indices);
}

void TAK::Engine::Renderer::GLTexture2_draw(const int texId,
                                            const int mode,
                                            const std::size_t numCoords,
                                            const std::size_t texSize, const int texType, const void *textureCoordinates,
                                            const std::size_t vertSize, const int vertType, const void *vertexCoordinates,
                                            const int idxType, const void *indices) NOTHROWS
{
    using namespace atakmap::renderer;

    if (texId == 0)
        return;

    GLES20FixedPipeline *fixedPipe = GLES20FixedPipeline::getInstance();

    fixedPipe->glEnableClientState(GLES20FixedPipeline::ClientState::CS_GL_VERTEX_ARRAY);
    fixedPipe->glEnableClientState(GLES20FixedPipeline::ClientState::CS_GL_TEXTURE_COORD_ARRAY);
    glEnable(GL_BLEND);
    glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

    fixedPipe->glVertexPointer(vertSize, vertType, 0, vertexCoordinates);

    fixedPipe->glTexCoordPointer(texSize, texType, 0, textureCoordinates);
    glBindTexture(GL_TEXTURE_2D, texId);

    fixedPipe->glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
    fixedPipe->glDrawElements(mode, numCoords, idxType, indices);
    // GLES20FixedPipeline.glDrawArrays(mode, 0, numCoords);

    fixedPipe->glDisableClientState(GLES20FixedPipeline::ClientState::CS_GL_VERTEX_ARRAY);
    fixedPipe->glDisableClientState(GLES20FixedPipeline::ClientState::CS_GL_TEXTURE_COORD_ARRAY);
    glDisable(GL_BLEND);
}


/*************************************************************************/
// Public API - Static utility

std::size_t TAK::Engine::Renderer::GLTexture2_getNumQuadMeshVertices(const std::size_t numCellsX, const std::size_t numCellsY) NOTHROWS
{
    return (numCellsX + 1) * (numCellsY + 1);
}

TAKErr TAK::Engine::Renderer::GLTexture2_createQuadMeshTexCoords(float *buffer,
                                                                 const atakmap::math::Point<float> &upperLeft,
                                                                 const atakmap::math::Point<float> &upperRight,
                                                                 const atakmap::math::Point<float> &lowerRight,
                                                                 const atakmap::math::Point<float> &lowerLeft,
                                                                 const std::size_t numCellsX, const std::size_t numCellsY) NOTHROWS
{
    using namespace atakmap::math;

    Matrix gridToTexCoord = Matrix();
    Matrix::mapQuads(0, 0, // grid UL
        numCellsX, 0, // grid UR
        numCellsX, numCellsY, // gridLR
        0, numCellsY, // gridLL
        upperLeft.x, upperLeft.y,
        upperRight.x, upperRight.y,
        lowerRight.x, lowerRight.y,
        lowerLeft.x, lowerLeft.y, &gridToTexCoord);

    Point<double> gridCoord(0, 0);
    Point<double> texCoord(0, 0);

    for (int y = 0; y <= numCellsY; y++) {
        gridCoord.y = y;
        for (int x = 0; x <= numCellsX; x++) {
            gridCoord.x = x;

            gridToTexCoord.transform(&gridCoord, &texCoord);

            *buffer++ = (float)texCoord.x;
            *buffer++ = (float)texCoord.y;
        }
    }

    return TE_Ok;
}

TAKErr TAK::Engine::Renderer::GLTexture2_createQuadMeshTexCoords(float *buffer,
                                                                 const float width, const float height,
                                                                 std::size_t numCellsX, std::size_t numCellsY) NOTHROWS
{
    if (width <= 0 || height <= 0)
        return TE_InvalidArg;

    for (int y = 0; y <= numCellsY; y++) {
        for (int x = 0; x <= numCellsX; x++) {
            *buffer++ = (width * ((float)x / (float)(numCellsX + 1)));
            *buffer++ = (height * ((float)y / (float)(numCellsY + 1)));
        }
    }

    return TE_Ok;
}

std::size_t TAK::Engine::Renderer::GLTexture2_getNumQuadMeshIndices(std::size_t numCellsX, std::size_t numCellsY) NOTHROWS
{
    if (!numCellsX || !numCellsY)
        return 0;
    return ((2 * (numCellsX + 1)) * numCellsY) + (2 * (numCellsY - 1));
}

void TAK::Engine::Renderer::GLTexture2_createQuadMeshIndexBuffer(uint16_t *buffer, const std::size_t numCellsX, const std::size_t numCellsY) NOTHROWS
{
    if (!numCellsX || !numCellsY)
        return;

    short index = 0;
    const int numVertsX = numCellsX + 1;
    for (int y = 0; y < numCellsY; y++) {
        for (int x = 0; x < numVertsX; x++) {
            *buffer++ = index;
            *buffer++ = ((short)(index + numVertsX));
            index++;
        }
        // the degenerate triangle
        if (y < (numCellsY - 1)) {
            *buffer++ = ((short)((index + numVertsX) - 1));
            *buffer++ = (index);
        }
    }
}

TAKErr TAK::Engine::Renderer::GLTexture2_getFormatAndDataType(int *format, int *datatype, const Bitmap2::Format bitmapFormat) NOTHROWS
{
    switch (bitmapFormat)
    {
    case Bitmap2::RGBA32 :
        *format = GL_RGBA;
        *datatype = GL_UNSIGNED_BYTE;
        break;
    case Bitmap2::RGB24 :
        *format = GL_RGB;
        *datatype = GL_UNSIGNED_BYTE;
        break;
    case Bitmap2::RGB565 :
        *format = GL_RGB;
        *datatype = GL_UNSIGNED_SHORT_5_6_5;
        break;
    case Bitmap2::RGBA5551:
        *format = GL_RGBA;
        *datatype = GL_UNSIGNED_SHORT_5_5_5_1;
        break;
    case Bitmap2::MONOCHROME:
        *format = GL_LUMINANCE;
        *datatype = GL_UNSIGNED_BYTE;
        break;
    case Bitmap2::MONOCHROME_ALPHA:
        *format = GL_LUMINANCE_ALPHA;
        *datatype = GL_UNSIGNED_BYTE;
        break;
    default :
        return TE_InvalidArg;
    }

    return TE_Ok;
}

TAKErr TAK::Engine::Renderer::GLTexture2_getBitmapFormat(Bitmap2::Format *value, const int format, const int datatype) NOTHROWS
{
    switch (format) {
    case GL_RGBA:
        if (datatype != GL_UNSIGNED_BYTE)
            return TE_InvalidArg;
        *value = Bitmap2::RGBA32;
        break;
    case GL_RGB:
        if (datatype != GL_UNSIGNED_BYTE)
            return TE_InvalidArg;
        *value = Bitmap2::RGB24;
        break;
    case GL_RGB565:
        if (datatype != GL_UNSIGNED_SHORT_5_6_5)
            return TE_InvalidArg;
        *value = Bitmap2::RGB565;
        break;
    case GL_RGB5_A1:
        if (datatype != GL_UNSIGNED_SHORT_5_5_5_1)
            return TE_InvalidArg;
        *value = Bitmap2::RGBA5551;
        break;
    case GL_LUMINANCE:
        if (datatype != GL_UNSIGNED_BYTE)
            return TE_InvalidArg;
        *value = Bitmap2::MONOCHROME;
        break;
    case GL_LUMINANCE_ALPHA:
        if (datatype != GL_UNSIGNED_BYTE)
            return TE_InvalidArg;
        *value = Bitmap2::MONOCHROME_ALPHA;
        break;
    default:
        return TE_InvalidArg;
    }

    return TE_Ok;
}

TAKErr TAK::Engine::Renderer::GLTexture2_createCompressedTexture(GLTexture2Ptr &value, const Bitmap2 &bitmap) NOTHROWS
{
    TAKErr code(TE_Ok);
    const S3TCAlgorithm alg = S3TC_getDefaultAlgorithm(bitmap.getFormat());
    const std::size_t compressedSize = S3TC_getCompressedSize(bitmap);
    array_ptr<uint8_t> compressedData(new uint8_t[compressedSize]);
    MemoryOutput2 dst;
    dst.open(compressedData.get(), compressedSize);
    code = S3TC_compress(dst, nullptr, nullptr, bitmap);
    TE_CHECKRETURN_CODE(code);

    GLenum glalg;
    Bitmap2::Format cbfmt;
    switch (alg) {
    case TECA_DXT1 :
        glalg = GL_COMPRESSED_RGB_S3TC_DXT1_EXT;
        cbfmt = Bitmap2::RGB24;
        break;
    case TECA_DXT5 :
        glalg = GL_COMPRESSED_RGBA_S3TC_DXT5_EXT;
        cbfmt = Bitmap2::RGBA32;
        break;
    default :
        return TE_IllegalState;
    }

    std::size_t alignedW = (bitmap.getWidth() + 3u) / 4u * 4u;
    std::size_t alignedH = (bitmap.getHeight() + 3u) / 4u * 4u;
    GLTexture2 tex(alignedW, alignedH, cbfmt);
    tex.initInternal();
    if (!tex.id)
        return TE_OutOfMemory;

    // clear GL errors
    while (glGetError() != GL_NONE)
        ;

    glCompressedTexImage2D(GL_TEXTURE_2D, 0, glalg, alignedW, alignedH, 0, compressedSize, compressedData.get());
    GLenum err = glGetError();
    if (err != GL_NONE) {
        tex.release();
        Logger_log(TELL_Warning, "Failed to create compressed texture, error=0x%X", err);
        return TE_Err;
    }
    tex.compressed = true;
    value = GLTexture2Ptr(new GLTexture2(tex), Memory_deleter_const<GLTexture2>);
    tex.id = 0;
    return code;
}

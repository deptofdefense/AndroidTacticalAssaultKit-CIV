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
    size_t nextPowerOf2(const size_t v) NOTHROWS
    {
        size_t value = v;
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

    template<class T>
    TAKErr createQuadMeshIndexBufferImpl(MemBuffer2 &value, const std::size_t numCellsX, const std::size_t numCellsY) NOTHROWS;
}

/*************************************************************************/
// Private functions

void GLTexture2::apply() NOTHROWS
{
    if (!id_)
        return;

    GLint t;
    glGetIntegerv(GL_TEXTURE_BINDING_2D, &t);
    glBindTexture(GL_TEXTURE_2D, id_);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, mag_filter_);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, min_filter_);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, wrap_s_);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, wrap_t_);
    glBindTexture(GL_TEXTURE_2D, t);
}


/*************************************************************************/
// Public API - construction, setup, teardown

GLTexture2::GLTexture2(const size_t w, const size_t h, const int format, const int type) NOTHROWS :
    id_(0),
    format_(format),
    type_(type),
    width_(nextPowerOf2(w)),
    height_(nextPowerOf2(h)),
    min_filter_(GL_NEAREST),
    mag_filter_(GL_LINEAR),
    wrap_s_(GL_CLAMP_TO_EDGE),
    wrap_t_(GL_CLAMP_TO_EDGE),
    needs_apply_(false),
    compressed_(false)
{}

GLTexture2::GLTexture2(const size_t w, const size_t h, Bitmap2::Format f) NOTHROWS :
    id_(0),
    format_(0),
    type_(0),
    width_(nextPowerOf2(w)),
    height_(nextPowerOf2(h)),
    min_filter_(GL_NEAREST),
    mag_filter_(GL_LINEAR),
    wrap_s_(GL_CLAMP_TO_EDGE),
    wrap_t_(GL_CLAMP_TO_EDGE),
    needs_apply_(false),
    compressed_(false)
{
    if (f == Bitmap2::ARGB32)
        f = Bitmap2::RGBA32;
    else if (f == Bitmap2::BGRA32)
        f = Bitmap2::RGBA32;
    else if (f == Bitmap2::BGR24)
        f = Bitmap2::RGB24;
    GLTexture2_getFormatAndDataType(&format_, &type_, f);
}

GLTexture2::~GLTexture2() NOTHROWS
{
    if (id_) {
        Logger_log(TELL_Warning, "Texture leaking, width=%u, height=%u", width_, height_);
    }
}
void GLTexture2::init() NOTHROWS
{
    if (id_ == 0) {
        GLint t;
        glGetIntegerv(GL_TEXTURE_BINDING_2D, &t);

        // clear any error codes
        //glCheckForError(GL_NONE);
        initInternal();
        glTexImage2D(GL_TEXTURE_2D, 0, format_,
                     static_cast<GLsizei>(width_), static_cast<GLsizei>(height_), 0,
                     format_, type_, nullptr);

        // check for out of memory
        if (glCheckForError(GL_OUT_OF_MEMORY)) {
            Logger_log(TELL_Warning, "GLTexture2::init() failed to generate texture");
            if (id_) {
                glDeleteTextures(1, &id_);
                id_ = 0;
            }
        }

        // drop through to bind original texture
        glBindTexture(GL_TEXTURE_2D, t);

        needs_apply_ = !id_;
    }
}
bool GLTexture2::initInternal() NOTHROWS
{
    // generate the texture
    glGenTextures(1, &id_);

    // check for out of memory
    if (glCheckForError(GL_OUT_OF_MEMORY)) {
        Logger_log(TELL_Warning, "GLTexture2::init() failed to generate texture");
        if (id_) {
            glDeleteTextures(1, &id_);
            id_ = 0;
        }
        return false;
    }

    glBindTexture(GL_TEXTURE_2D, id_);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, mag_filter_);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, min_filter_);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, wrap_s_);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, wrap_t_);

    return !!id_;
}

void GLTexture2::release() NOTHROWS
{
    if (id_) {
        glDeleteTextures(1, &id_);
        id_ = 0;
    }
}


/*************************************************************************/
// Public API - Member get/sets

int GLTexture2::getType() const NOTHROWS
{
    return type_;
}
int GLTexture2::getFormat() const NOTHROWS
{
    return format_;
}
bool GLTexture2::isCompressed() const NOTHROWS
{
    return compressed_;
}
int GLTexture2::getTexId() NOTHROWS
{
    if (needs_apply_) {
        needs_apply_ = false;
        apply();
    }
    return id_;
}
int GLTexture2::getTexId() const NOTHROWS
{
    return id_;
}

size_t GLTexture2::getTexWidth() const NOTHROWS
{
    return width_;
}

size_t GLTexture2::getTexHeight() const NOTHROWS
{
    return height_;
}

int GLTexture2::getWrapT() const NOTHROWS
{
    return wrap_t_;
}

int GLTexture2::getWrapS() const NOTHROWS
{
    return wrap_s_;
}

int GLTexture2::getMagFilter() const NOTHROWS
{
    return mag_filter_;
}

int GLTexture2::getMinFilter() const NOTHROWS
{
    return min_filter_;
}

void GLTexture2::setWrapS(int wrapS) NOTHROWS
{
    this->wrap_s_ = wrapS;
    needs_apply_ = true;
}

void GLTexture2::setWrapT(int wrapT) NOTHROWS
{
    this->wrap_t_ = wrapT;
    needs_apply_ = true;
}

void GLTexture2::setMinFilter(int minFilter) NOTHROWS
{
    this->min_filter_ = minFilter;
    needs_apply_ = true;
}

void GLTexture2::setMagFilter(int magFilter) NOTHROWS
{
    this->mag_filter_ = min_filter_;
    needs_apply_ = true;
}


/*************************************************************************/
// Public API - texture loading

TAKErr GLTexture2::load(const Bitmap2 &bitmap, const int x, const int y) NOTHROWS
{
    TAKErr code;
    Bitmap2::Format tgtFmt;
    
    code = GLTexture2_getBitmapFormat(&tgtFmt, this->format_, this->type_);
    TE_CHECKRETURN_CODE(code);

    std::size_t pixelSize;
    Bitmap2_formatPixelSize(&pixelSize, bitmap.getFormat());

    if (tgtFmt == bitmap.getFormat() && bitmap.getStride() == (bitmap.getWidth()*pixelSize)) {
        load(bitmap.getData(), x, y, static_cast<int>(bitmap.getWidth()), static_cast<int>(bitmap.getHeight()));
    } else {
        Bitmap2 converted(bitmap, tgtFmt);
        load(converted.getData(), x, y, static_cast<int>(converted.getWidth()), static_cast<int>(converted.getHeight()));
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

void GLTexture2::load(const void *data, const int x, const int y, const size_t w, const size_t h) NOTHROWS 
{
    if (!id_ && x == 0 && y == 0 && w == width_ && h == height_) {
        if (!initInternal())
            return;

        glBindTexture(GL_TEXTURE_2D, id_);
        glTexImage2D(GL_TEXTURE_2D, 0, format_,
                     static_cast<GLsizei>(width_), static_cast<GLsizei>(height_), 0,
                     format_, type_, data);
        glBindTexture(GL_TEXTURE_2D, 0);
        return;
    }

    init();
    if (id_) {
		try {
			glBindTexture(GL_TEXTURE_2D, id_);
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
            glTexSubImage2D(GL_TEXTURE_2D, 0, x, y, static_cast<GLsizei>(w), static_cast<GLsizei>(h),
                    format_, type_, data);
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
    GLTexture2_draw(id_, GL_TRIANGLE_FAN, numCoords, texType, textureCoordinates, vertType, vertexCoordinates);
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
    GLTexture2_draw(texId, mode, numCoords, texSize,
                    texType, textureCoordinates, vertSize,
                    vertType, vertexCoordinates, 1.0f,
                    1.0f, 1.0f, 1.0f);
}

void TAK::Engine::Renderer::GLTexture2_draw(const int texId,
                                            const int mode,
                                            const std::size_t numCoords,
                                            const std::size_t texSize, const int texType, const void *textureCoordinates,
                                            const std::size_t vertSize, const int vertType, const void *vertexCoordinates,
                                            const float red, const float green, const float blue, const float alpha) NOTHROWS
{
    using namespace atakmap::renderer;

    if (texId == 0)
        return;

    GLES20FixedPipeline *fixedPipe = GLES20FixedPipeline::getInstance();

    fixedPipe->glEnableClientState(GLES20FixedPipeline::ClientState::CS_GL_VERTEX_ARRAY);
    fixedPipe->glEnableClientState(GLES20FixedPipeline::ClientState::CS_GL_TEXTURE_COORD_ARRAY);
    glEnable(GL_BLEND);
    glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

    fixedPipe->glVertexPointer(static_cast<int>(vertSize), vertType, 0, vertexCoordinates);

    fixedPipe->glTexCoordPointer(static_cast<int>(texSize), texType, 0, textureCoordinates);
    glBindTexture(GL_TEXTURE_2D, texId);

    fixedPipe->glColor4f(red, green, blue, alpha);
    fixedPipe->glDrawArrays(mode, 0, static_cast<int>(numCoords));

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
    GLTexture2_draw(texId, mode, numCoords, texSize, texType, textureCoordinates,
                    vertSize, vertType, vertexCoordinates,
                    idxType, indices, 1.0f, 1.0f, 1.0f, 1.0f);
}

void TAK::Engine::Renderer::GLTexture2_draw(const int texId,
                                            const int mode,
                                            const std::size_t numCoords,
                                            const std::size_t texSize, const int texType, const void *textureCoordinates,
                                            const std::size_t vertSize, const int vertType, const void *vertexCoordinates,
                                            const int idxType, const void *indices,
                                            const float red, const float green, const float blue, const float alpha) NOTHROWS
{
    using namespace atakmap::renderer;

    if (texId == 0)
        return;

    GLES20FixedPipeline *fixedPipe = GLES20FixedPipeline::getInstance();

    fixedPipe->glEnableClientState(GLES20FixedPipeline::ClientState::CS_GL_VERTEX_ARRAY);
    fixedPipe->glEnableClientState(GLES20FixedPipeline::ClientState::CS_GL_TEXTURE_COORD_ARRAY);
    glEnable(GL_BLEND);
    glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

    fixedPipe->glVertexPointer(static_cast<int>(vertSize), vertType, 0, vertexCoordinates);

    fixedPipe->glTexCoordPointer(static_cast<int>(texSize), texType, 0, textureCoordinates);
    glBindTexture(GL_TEXTURE_2D, texId);

    fixedPipe->glColor4f(red, green, blue, alpha);
    fixedPipe->glDrawElements(mode, static_cast<int>(numCoords), idxType, indices);
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
        static_cast<double>(numCellsX), 0, // grid UR
        static_cast<double>(numCellsX), static_cast<double>(numCellsY), // gridLR
        0, static_cast<double>(numCellsY), // gridLL
        upperLeft.x, upperLeft.y,
        upperRight.x, upperRight.y,
        lowerRight.x, lowerRight.y,
        lowerLeft.x, lowerLeft.y, &gridToTexCoord);

    Point<double> gridCoord(0, 0);
    Point<double> texCoord(0, 0);

    for (std::size_t y = 0; y <= numCellsY; y++) {
        gridCoord.y = static_cast<double>(y);
        for (std::size_t x = 0; x <= numCellsX; x++) {
            gridCoord.x = static_cast<double>(x);

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

    for (std::size_t y = 0; y <= numCellsY; y++) {
        for (std::size_t x = 0; x <= numCellsX; x++) {
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

    MemBuffer2 buf(buffer, GLTexture2_getNumQuadMeshIndices(numCellsX, numCellsY));

    // XXX - doesn't check for error codes
    createQuadMeshIndexBufferImpl<uint16_t>(buf, numCellsX, numCellsY);
}

TAKErr TAK::Engine::Renderer::GLTexture2_createQuadMeshIndexBuffer(MemBuffer2 &value, const int type, const std::size_t numCellsX, const std::size_t numCellsY) NOTHROWS
{
    switch(type) {
        case GL_UNSIGNED_SHORT :
            return createQuadMeshIndexBufferImpl<uint16_t>(value, numCellsX, numCellsY);
        case GL_UNSIGNED_INT :
            return createQuadMeshIndexBufferImpl<uint32_t>(value, numCellsX, numCellsY);
        default :
            return TE_InvalidArg;
    }
}

TAKErr TAK::Engine::Renderer::GLTexture2_getFormatAndDataType(int *format, int *datatype, const Bitmap2::Format bitmapFormat) NOTHROWS
{
    switch (bitmapFormat)
    {
    case Bitmap2::RGBA32 :
    case Bitmap2::ARGB32 :
    case Bitmap2::BGRA32 :
        *format = GL_RGBA;
        *datatype = GL_UNSIGNED_BYTE;
        break;
    case Bitmap2::RGB24 :
    case Bitmap2::BGR24 :
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

TAKErr TAK::Engine::Renderer::GLTexture2_createCompressedTextureData(std::unique_ptr<GLCompressedTextureData, void(*)(GLCompressedTextureData *)> &data, const Bitmap2 &bitmap) NOTHROWS {
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
	case TECA_DXT1:
		glalg = GL_COMPRESSED_RGB_S3TC_DXT1_EXT;
		cbfmt = Bitmap2::RGB24;
		break;
	case TECA_DXT5:
		glalg = GL_COMPRESSED_RGBA_S3TC_DXT5_EXT;
		cbfmt = Bitmap2::RGBA32;
		break;
	default:
		return TE_IllegalState;
	}

	std::size_t alignedW = (bitmap.getWidth() + 3u) / 4u * 4u;
	std::size_t alignedH = (bitmap.getHeight() + 3u) / 4u * 4u;

	data = std::unique_ptr<GLCompressedTextureData, void(*)(GLCompressedTextureData *)>(new(std::nothrow) GLCompressedTextureData(), Memory_deleter<GLCompressedTextureData>);
	if (!data)
		return TE_OutOfMemory;

	data->compressedData.reset(compressedData.release());
	data->compressedSize = compressedSize;
	data->alignedW = alignedW;
	data->alignedH = alignedH;
	data->cbfmt = cbfmt;
	data->glalg = glalg;

	return TE_Ok;
}


TAKErr TAK::Engine::Renderer::GLTexture2_createCompressedTexture(GLTexture2Ptr &value, const Bitmap2 &bitmap) NOTHROWS
{
    std::unique_ptr<GLCompressedTextureData, void(*)(GLCompressedTextureData *)> data(nullptr, nullptr);
	TAKErr code = GLTexture2_createCompressedTextureData(data, bitmap);
	TE_CHECKRETURN_CODE(code);
    return GLTexture2_createCompressedTexture(value, *data);
}

TAKErr TAK::Engine::Renderer::GLTexture2_createCompressedTexture(GLTexture2Ptr &value, const GLCompressedTextureData &data) NOTHROWS {
	TAKErr code(TE_Ok);
        GLTexture2 tex(static_cast<int>(data.alignedW), static_cast<int>(data.alignedH), data.cbfmt);
	tex.initInternal();
	if (!tex.id_)
		return TE_OutOfMemory;

	// clear GL errors
	while (glGetError() != GL_NONE)
		;

	glCompressedTexImage2D(GL_TEXTURE_2D, 0, data.glalg, static_cast<GLsizei>(data.alignedW), static_cast<GLsizei>(data.alignedH), 0, static_cast<GLsizei>(data.compressedSize), data.compressedData.get());
	GLenum err = glGetError();
	if (err != GL_NONE) {
		tex.release();
		Logger_log(TELL_Warning, "Failed to create compressed texture, error=0x%X", err);
		return TE_Err;
	}
	tex.compressed_ = true;
	value = GLTexture2Ptr(new GLTexture2(tex), Memory_deleter_const<GLTexture2>);
	tex.id_ = 0;
	return code;
}

TAKErr TAK::Engine::Renderer::GLTexture2_orphan(GLuint* result, GLTexture2& texture) NOTHROWS {

    if (!result)
        return TE_InvalidArg;

    *result = texture.id_;
    texture.id_ = 0;
    return TE_Ok;
}

namespace
{
    template<class T>
    TAKErr createQuadMeshIndexBufferImpl(MemBuffer2 &value, const std::size_t numCellsX, const std::size_t numCellsY) NOTHROWS
    {
        TAKErr code(TE_Ok);
        if (!numCellsX || !numCellsY)
            return TE_Ok;

        T index = 0u;
        const std::size_t numVertsX = numCellsX + 1u;
        for (std::size_t y = 0u; y < numCellsY; y++) {
            for (std::size_t x = 0u; x < numVertsX; x++) {
                //*buffer++ = index;
                code = value.put<T>(index);
                TE_CHECKBREAK_CODE(code)
                //*buffer++ = ((short)(index + numVertsX));
                code = value.put<T>(static_cast<T>(index + numVertsX));
                TE_CHECKBREAK_CODE(code)
                index++;
            }
            TE_CHECKBREAK_CODE(code);
            // the degenerate triangle
            if (y < (numCellsY - 1u)) {
                //*buffer++ = ((short)((index + numVertsX) - 1));
                code = value.put<T>(((T)((index + numVertsX) - 1u)));
                TE_CHECKBREAK_CODE(code);
                //*buffer++ = (index);
                code = value.put<T>(index);
                TE_CHECKBREAK_CODE(code);
            }
        }
        TE_CHECKRETURN_CODE(code);

        return code;
    }
}

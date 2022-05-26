#include "renderer/GLWireframe.h"

#include <cstring>

using namespace TAK::Engine::Util;

namespace
{
    template<class T>
    TAKErr deriveIndices(T *value, const GLenum mode, const GLuint count) NOTHROWS;

    template<class T>
    TAKErr deriveIndices(T *value, std::size_t *outputCount, const T *srcIndices, const GLenum mode, const GLuint count) NOTHROWS;
}

TAKErr TAK::Engine::Renderer::GLWireframe_getNumWireframeElements(std::size_t *value, const GLenum mode, const GLuint count) NOTHROWS
{
    switch (mode) {
    case GL_TRIANGLES :
        *value = (6u * count/3u);
        break;
    case GL_TRIANGLE_FAN :
        *value = (6u * (count - 2u));
        break;
    case GL_TRIANGLE_STRIP :
        *value = (6u * (count - 2u));
        break;
    default :
        return TE_InvalidArg;
    }

    return TE_Ok;
}
TAKErr TAK::Engine::Renderer::GLWireframe_deriveIndices(uint16_t *value, const GLenum mode, const GLuint count) NOTHROWS
{
    return deriveIndices(value, mode, count);
}
TAKErr TAK::Engine::Renderer::GLWireframe_deriveIndices(uint32_t *value, const GLenum mode, const GLuint count) NOTHROWS
{
    return deriveIndices(value, mode, count);
}
TAKErr TAK::Engine::Renderer::GLWireframe_deriveIndices(uint16_t *value, std::size_t *outputCount, const uint16_t *srcIndices, const GLenum mode, const GLuint count) NOTHROWS
{
    return deriveIndices(value, outputCount, srcIndices, mode, count);
}
TAKErr TAK::Engine::Renderer::GLWireframe_deriveIndices(uint32_t *value, std::size_t *outputCount, const uint32_t *srcIndices, const GLenum mode, const GLuint count) NOTHROWS
{
    return deriveIndices(value, outputCount, srcIndices, mode, count);
}
TAKErr TAK::Engine::Renderer::GLWireframe_deriveLines(void *value, std::size_t *outputCount, const GLenum mode, const GLuint stride, const void *data, const GLuint count) NOTHROWS
{
    if (!value)
        return TE_InvalidArg;
    if (!outputCount)
        return TE_InvalidArg;
    if (!data)
        return TE_InvalidArg;

    const auto *pData = static_cast<const uint8_t *>(data);
    auto *pValue = static_cast<uint8_t *>(value);

    switch (mode) {
    case GL_TRIANGLES :
        for (std::size_t i = 0u; i < (count/3u); i++) {
            const uint8_t *a = pData + (i*3u) * stride;
            const uint8_t *b = pData + (i*3u+1u) * stride;
            const uint8_t *c = pData + (i*3u+2u) * stride;

            memcpy(pValue, a, stride);
            pValue += stride;
            memcpy(pValue, b, stride);
            pValue += stride;
            memcpy(pValue, b, stride);
            pValue += stride;
            memcpy(pValue, c, stride);
            pValue += stride;
            memcpy(pValue, c, stride);
            pValue += stride;
            memcpy(pValue, a, stride);
            pValue += stride;
        }
        break;
    case GL_TRIANGLE_FAN :
        for (std::size_t i = 0u; i < count; i++) {
            const uint8_t *a = pData;
            const uint8_t *b = pData + (i-1u) * stride;
            const uint8_t *c = pData + i * stride;

            memcpy(pValue, a, stride);
            pValue += stride;
            memcpy(pValue, b, stride);
            pValue += stride;
            memcpy(pValue, b, stride);
            pValue += stride;
            memcpy(pValue, c, stride);
            pValue += stride;
            memcpy(pValue, c, stride);
            pValue += stride;
            memcpy(pValue, a, stride);
            pValue += stride;
        }
        break;
    case GL_TRIANGLE_STRIP :
        for (std::size_t i = 0u; i < count; i++) {
            const uint8_t *a = pData + (i-2u) * stride;
            const uint8_t *b = pData + (i-1u) * stride;
            const uint8_t *c = pData + i * stride;

            // check for degenerate
            if (memcmp(a, b, stride) == 0 || memcmp(b, c, stride) == 0 || memcmp(c, a, stride) == 0)
                continue;

            memcpy(pValue, a, stride);
            pValue += stride;
            memcpy(pValue, b, stride);
            pValue += stride;
            memcpy(pValue, b, stride);
            pValue += stride;
            memcpy(pValue, c, stride);
            pValue += stride;
            memcpy(pValue, c, stride);
            pValue += stride;
            memcpy(pValue, a, stride);
            pValue += stride;
        }
        break;
    default :
        return TE_InvalidArg;
    }

    *outputCount = (pValue - static_cast<uint8_t *>(value)) / stride;
    return TE_Ok;
}

namespace
{
    template<class T>
    TAKErr deriveIndices(T *value, const GLenum mode, const GLuint count) NOTHROWS
    {
        if (!value)
            return TE_InvalidArg;

        T *pValue = value;
        switch (mode) {
        case GL_TRIANGLES :
            for (std::size_t i = 0u; i < (count/3u); i++) {
                const T a = (T)(i*3u);
                const T b = (T)(i*3u + 1u);
                const T c = (T)(i*3u + 2u);

                *pValue++ = a;
                *pValue++ = b;
                *pValue++ = b;
                *pValue++ = c;
                *pValue++ = c;
                *pValue++ = a;
            }
            break;
        case GL_TRIANGLE_FAN :
            for (std::size_t i = 2u; i < count; i++) {
                const T a = (T)0u;
                const T b = (T)(i - 1u);
                const T c = (T)i;

                *pValue++ = a;
                *pValue++ = b;
                *pValue++ = b;
                *pValue++ = c;
                *pValue++ = c;
                *pValue++ = a;
            }
            break;
        case GL_TRIANGLE_STRIP :
            for (std::size_t i = 2u; i < count; i++) {
                const T a = (T)(i-2u);
                const T b = (T)(i - 1u);
                const T c = (T)i;

                *pValue++ = a;
                *pValue++ = b;
                *pValue++ = b;
                *pValue++ = c;
                *pValue++ = c;
                *pValue++ = a;
            }
            break;
        default :
            return TE_InvalidArg;
        }

        return TE_Ok;
    }
    template<class T>
    TAKErr deriveIndices(T *value, std::size_t *outputCount, const T *srcIndices, const GLenum mode, const GLuint count) NOTHROWS
    {
        if (!value)
            return TE_InvalidArg;
        if (!outputCount)
            return TE_InvalidArg;
        if (!srcIndices)
            return TE_InvalidArg;

        T *pValue = value;
        switch (mode) {
        case GL_TRIANGLES :
            for (std::size_t i = 0u; i < (count/3u); i++) {
                const T a = srcIndices[i*3u];
                const T b = srcIndices[i*3u + 1u];
                const T c = srcIndices[i*3u + 2u];

                *pValue++ = a;
                *pValue++ = b;
                *pValue++ = b;
                *pValue++ = c;
                *pValue++ = c;
                *pValue++ = a;
            }
            break;
        case GL_TRIANGLE_FAN :
            for (std::size_t i = 2u; i < count; i++) {
                const T a = srcIndices[0u];
                const T b = srcIndices[i - 1u];
                const T c = srcIndices[i];

                *pValue++ = a;
                *pValue++ = b;
                *pValue++ = b;
                *pValue++ = c;
                *pValue++ = c;
                *pValue++ = a;
            }
            break;
        case GL_TRIANGLE_STRIP :
            for (std::size_t i = 2u; i < count; i++) {
                const T a = srcIndices[i-2u];
                const T b = srcIndices[i-1u];
                const T c = srcIndices[i];

                // check for degenerate
                if (a == b || b == c || c == a)
                    continue;

                *pValue++ = a;
                *pValue++ = b;
                *pValue++ = b;
                *pValue++ = c;
                *pValue++ = c;
                *pValue++ = a;
            }
            break;
        default :
            return TE_InvalidArg;
        }

        *outputCount = pValue - value;
        return TE_Ok;
    }
}

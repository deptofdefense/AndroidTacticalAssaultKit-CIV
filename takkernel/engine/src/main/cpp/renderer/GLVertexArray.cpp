#include "renderer/GLVertexArray.h"

using namespace TAK::Engine::Renderer;

GLVertexArray::GLVertexArray() NOTHROWS
{}

GLVertexArray::GLVertexArray(const GLint size_, const GLenum type_, const GLboolean normalized_, const GLsizei stride_, const GLsizei offset_) NOTHROWS :
    size(size_),
    type(type_),
    normalized(normalized_),
    stride(stride_),
    offset(offset_)
{}

void TAK::Engine::Renderer::glVertexAttribPointer(GLuint index, const GLVertexArray& layout) NOTHROWS
{
    ::glVertexAttribPointer(index, layout.size, layout.type, layout.normalized, layout.stride, (const void*)((const unsigned char *)nullptr + layout.offset));
}

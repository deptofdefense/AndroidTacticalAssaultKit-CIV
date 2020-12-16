#include <stdlib.h>

#include <GLES3/gl32.h>

GL_APICALL void GL_APIENTRY glActiveTexture (GLenum texture) {}
GL_APICALL void GL_APIENTRY glAttachShader (GLuint program, GLuint shader) {}
GL_APICALL void GL_APIENTRY glBindAttribLocation (GLuint program, GLuint index, const GLchar *name) {}
GL_APICALL void GL_APIENTRY glBindBuffer (GLenum target, GLuint buffer) {}
GL_APICALL void GL_APIENTRY glBindFramebuffer (GLenum target, GLuint framebuffer) {}
GL_APICALL void GL_APIENTRY glBindRenderbuffer (GLenum target, GLuint renderbuffer) {}
GL_APICALL void GL_APIENTRY glBindTexture (GLenum target, GLuint texture) {}
GL_APICALL void GL_APIENTRY glBlendColor (GLfloat red, GLfloat green, GLfloat blue, GLfloat alpha) {}
GL_APICALL void GL_APIENTRY glBlendEquation (GLenum mode) {}
GL_APICALL void GL_APIENTRY glBlendEquationSeparate (GLenum modeRGB, GLenum modeAlpha) {}
GL_APICALL void GL_APIENTRY glBlendFunc (GLenum sfactor, GLenum dfactor) {}
GL_APICALL void GL_APIENTRY glBlendFuncSeparate (GLenum sfactorRGB, GLenum dfactorRGB, GLenum sfactorAlpha, GLenum dfactorAlpha) {}
GL_APICALL void GL_APIENTRY glBufferData (GLenum target, GLsizeiptr size, const void *data, GLenum usage) {}
GL_APICALL void GL_APIENTRY glBufferSubData (GLenum target, GLintptr offset, GLsizeiptr size, const void *data) {}
GL_APICALL GLenum GL_APIENTRY glCheckFramebufferStatus(GLenum target) { return GL_FRAMEBUFFER_UNSUPPORTED; }
GL_APICALL void GL_APIENTRY glClear (GLbitfield mask) {}
GL_APICALL void GL_APIENTRY glClearColor (GLfloat red, GLfloat green, GLfloat blue, GLfloat alpha) {}
GL_APICALL void GL_APIENTRY glClearDepthf (GLfloat d) {}
GL_APICALL void GL_APIENTRY glClearStencil (GLint s) {}
GL_APICALL void GL_APIENTRY glColorMask (GLboolean red, GLboolean green, GLboolean blue, GLboolean alpha) {}
GL_APICALL void GL_APIENTRY glCompileShader (GLuint shader) {}
GL_APICALL void GL_APIENTRY glCompressedTexImage2D (GLenum target, GLint level, GLenum internalformat, GLsizei width, GLsizei height, GLint border, GLsizei imageSize, const void *data) {}
GL_APICALL void GL_APIENTRY glCompressedTexSubImage2D (GLenum target, GLint level, GLint xoffset, GLint yoffset, GLsizei width, GLsizei height, GLenum format, GLsizei imageSize, const void *data) {}
GL_APICALL void GL_APIENTRY glCopyTexImage2D (GLenum target, GLint level, GLenum internalformat, GLint x, GLint y, GLsizei width, GLsizei height, GLint border) {}
GL_APICALL void GL_APIENTRY glCopyTexSubImage2D (GLenum target, GLint level, GLint xoffset, GLint yoffset, GLint x, GLint y, GLsizei width, GLsizei height) {}
GL_APICALL GLuint GL_APIENTRY glCreateProgram(void) { return GL_NONE; }
GL_APICALL GLuint GL_APIENTRY glCreateShader(GLenum type) { return GL_NONE; }
GL_APICALL void GL_APIENTRY glCullFace (GLenum mode) {}
GL_APICALL void GL_APIENTRY glDeleteBuffers (GLsizei n, const GLuint *buffers) {}
GL_APICALL void GL_APIENTRY glDeleteFramebuffers (GLsizei n, const GLuint *framebuffers) {}
GL_APICALL void GL_APIENTRY glDeleteProgram (GLuint program) {}
GL_APICALL void GL_APIENTRY glDeleteRenderbuffers (GLsizei n, const GLuint *renderbuffers) {}
GL_APICALL void GL_APIENTRY glDeleteShader (GLuint shader) {}
GL_APICALL void GL_APIENTRY glDeleteTextures (GLsizei n, const GLuint *textures) {}
GL_APICALL void GL_APIENTRY glDepthFunc (GLenum func) {}
GL_APICALL void GL_APIENTRY glDepthMask (GLboolean flag) {}
GL_APICALL void GL_APIENTRY glDepthRangef (GLfloat n, GLfloat f) {}
GL_APICALL void GL_APIENTRY glDetachShader (GLuint program, GLuint shader) {}
GL_APICALL void GL_APIENTRY glDisable (GLenum cap) {}
GL_APICALL void GL_APIENTRY glDisableVertexAttribArray (GLuint index) {}
GL_APICALL void GL_APIENTRY glDrawArrays (GLenum mode, GLint first, GLsizei count) {}
GL_APICALL void GL_APIENTRY glDrawElements (GLenum mode, GLsizei count, GLenum type, const void *indices) {}
GL_APICALL void GL_APIENTRY glEnable (GLenum cap) {}
GL_APICALL void GL_APIENTRY glEnableVertexAttribArray (GLuint index) {}
GL_APICALL void GL_APIENTRY glFinish (void) {}
GL_APICALL void GL_APIENTRY glFlush (void) {}
GL_APICALL void GL_APIENTRY glFramebufferRenderbuffer (GLenum target, GLenum attachment, GLenum renderbuffertarget, GLuint renderbuffer) {}
GL_APICALL void GL_APIENTRY glFramebufferTexture2D (GLenum target, GLenum attachment, GLenum textarget, GLuint texture, GLint level) {}
GL_APICALL void GL_APIENTRY glFrontFace (GLenum mode) {}
GL_APICALL void GL_APIENTRY glGenBuffers (GLsizei n, GLuint *buffers) {}
GL_APICALL void GL_APIENTRY glGenerateMipmap (GLenum target) {}
GL_APICALL void GL_APIENTRY glGenFramebuffers (GLsizei n, GLuint *framebuffers) {}
GL_APICALL void GL_APIENTRY glGenRenderbuffers (GLsizei n, GLuint *renderbuffers) {}
GL_APICALL void GL_APIENTRY glGenTextures (GLsizei n, GLuint *textures) {}
GL_APICALL void GL_APIENTRY glGetActiveAttrib (GLuint program, GLuint index, GLsizei bufSize, GLsizei *length, GLint *size, GLenum *type, GLchar *name) {}
GL_APICALL void GL_APIENTRY glGetActiveUniform (GLuint program, GLuint index, GLsizei bufSize, GLsizei *length, GLint *size, GLenum *type, GLchar *name) {}
GL_APICALL void GL_APIENTRY glGetAttachedShaders (GLuint program, GLsizei maxCount, GLsizei *count, GLuint *shaders) {}
GL_APICALL GLint GL_APIENTRY glGetAttribLocation(GLuint program, const GLchar *name) { return -1; }
GL_APICALL void GL_APIENTRY glGetBooleanv (GLenum pname, GLboolean *data) {}
GL_APICALL void GL_APIENTRY glGetBufferParameteriv (GLenum target, GLenum pname, GLint *params) {}
GL_APICALL GLenum GL_APIENTRY glGetError(void) { return GL_INVALID_OPERATION; }
GL_APICALL void GL_APIENTRY glGetFloatv (GLenum pname, GLfloat *data) {}
GL_APICALL void GL_APIENTRY glGetFramebufferAttachmentParameteriv (GLenum target, GLenum attachment, GLenum pname, GLint *params) {}
GL_APICALL void GL_APIENTRY glGetIntegerv (GLenum pname, GLint *data) {}
GL_APICALL void GL_APIENTRY glGetProgramiv (GLuint program, GLenum pname, GLint *params) {}
GL_APICALL void GL_APIENTRY glGetProgramInfoLog (GLuint program, GLsizei bufSize, GLsizei *length, GLchar *infoLog) {}
GL_APICALL void GL_APIENTRY glGetRenderbufferParameteriv (GLenum target, GLenum pname, GLint *params) {}
GL_APICALL void GL_APIENTRY glGetShaderiv (GLuint shader, GLenum pname, GLint *params) {}
GL_APICALL void GL_APIENTRY glGetShaderInfoLog (GLuint shader, GLsizei bufSize, GLsizei *length, GLchar *infoLog) {}
GL_APICALL void GL_APIENTRY glGetShaderPrecisionFormat (GLenum shadertype, GLenum precisiontype, GLint *range, GLint *precision) {}
GL_APICALL void GL_APIENTRY glGetShaderSource (GLuint shader, GLsizei bufSize, GLsizei *length, GLchar *source) {}
GL_APICALL const GLubyte *GL_APIENTRY glGetString(GLenum name) { return NULL; }
GL_APICALL void GL_APIENTRY glGetTexParameterfv (GLenum target, GLenum pname, GLfloat *params) {}
GL_APICALL void GL_APIENTRY glGetTexParameteriv (GLenum target, GLenum pname, GLint *params) {}
GL_APICALL void GL_APIENTRY glGetUniformfv (GLuint program, GLint location, GLfloat *params) {}
GL_APICALL void GL_APIENTRY glGetUniformiv (GLuint program, GLint location, GLint *params) {}
GL_APICALL GLint GL_APIENTRY glGetUniformLocation(GLuint program, const GLchar *name) { return -1; }
GL_APICALL void GL_APIENTRY glGetVertexAttribfv (GLuint index, GLenum pname, GLfloat *params) {}
GL_APICALL void GL_APIENTRY glGetVertexAttribiv (GLuint index, GLenum pname, GLint *params) {}
GL_APICALL void GL_APIENTRY glGetVertexAttribPointerv (GLuint index, GLenum pname, void **pointer) {}
GL_APICALL void GL_APIENTRY glHint (GLenum target, GLenum mode) {}
GL_APICALL GLboolean GL_APIENTRY glIsBuffer(GLuint buffer) { return GL_FALSE; }
GL_APICALL GLboolean GL_APIENTRY glIsEnabled (GLenum cap) { return GL_FALSE; }
GL_APICALL GLboolean GL_APIENTRY glIsFramebuffer (GLuint framebuffer) { return GL_FALSE; }
GL_APICALL GLboolean GL_APIENTRY glIsProgram (GLuint program) { return GL_FALSE; }
GL_APICALL GLboolean GL_APIENTRY glIsRenderbuffer (GLuint renderbuffer) {return GL_FALSE; }
GL_APICALL GLboolean GL_APIENTRY glIsShader (GLuint shader) { return GL_FALSE; }
GL_APICALL GLboolean GL_APIENTRY glIsTexture (GLuint texture) { return GL_FALSE; }
GL_APICALL void GL_APIENTRY glLineWidth (GLfloat width) {}
GL_APICALL void GL_APIENTRY glLinkProgram (GLuint program) {}
GL_APICALL void GL_APIENTRY glPixelStorei (GLenum pname, GLint param) {}
GL_APICALL void GL_APIENTRY glPolygonOffset (GLfloat factor, GLfloat units) {}
GL_APICALL void GL_APIENTRY glReadPixels (GLint x, GLint y, GLsizei width, GLsizei height, GLenum format, GLenum type, void *pixels) {}
GL_APICALL void GL_APIENTRY glReleaseShaderCompiler (void) {}
GL_APICALL void GL_APIENTRY glRenderbufferStorage (GLenum target, GLenum internalformat, GLsizei width, GLsizei height) {}
GL_APICALL void GL_APIENTRY glSampleCoverage (GLfloat value, GLboolean invert) {}
GL_APICALL void GL_APIENTRY glScissor (GLint x, GLint y, GLsizei width, GLsizei height) {}
GL_APICALL void GL_APIENTRY glShaderBinary (GLsizei count, const GLuint *shaders, GLenum binaryformat, const void *binary, GLsizei length) {}
GL_APICALL void GL_APIENTRY glShaderSource (GLuint shader, GLsizei count, const GLchar *const*string, const GLint *length) {}
GL_APICALL void GL_APIENTRY glStencilFunc (GLenum func, GLint ref, GLuint mask) {}
GL_APICALL void GL_APIENTRY glStencilFuncSeparate (GLenum face, GLenum func, GLint ref, GLuint mask) {}
GL_APICALL void GL_APIENTRY glStencilMask (GLuint mask) {}
GL_APICALL void GL_APIENTRY glStencilMaskSeparate (GLenum face, GLuint mask) {}
GL_APICALL void GL_APIENTRY glStencilOp (GLenum fail, GLenum zfail, GLenum zpass) {}
GL_APICALL void GL_APIENTRY glStencilOpSeparate (GLenum face, GLenum sfail, GLenum dpfail, GLenum dppass) {}
GL_APICALL void GL_APIENTRY glTexImage2D (GLenum target, GLint level, GLint internalformat, GLsizei width, GLsizei height, GLint border, GLenum format, GLenum type, const void *pixels) {}
GL_APICALL void GL_APIENTRY glTexParameterf (GLenum target, GLenum pname, GLfloat param) {}
GL_APICALL void GL_APIENTRY glTexParameterfv (GLenum target, GLenum pname, const GLfloat *params) {}
GL_APICALL void GL_APIENTRY glTexParameteri (GLenum target, GLenum pname, GLint param) {}
GL_APICALL void GL_APIENTRY glTexParameteriv (GLenum target, GLenum pname, const GLint *params) {}
GL_APICALL void GL_APIENTRY glTexSubImage2D (GLenum target, GLint level, GLint xoffset, GLint yoffset, GLsizei width, GLsizei height, GLenum format, GLenum type, const void *pixels) {}
GL_APICALL void GL_APIENTRY glUniform1f (GLint location, GLfloat v0) {}
GL_APICALL void GL_APIENTRY glUniform1fv (GLint location, GLsizei count, const GLfloat *value) {}
GL_APICALL void GL_APIENTRY glUniform1i (GLint location, GLint v0) {}
GL_APICALL void GL_APIENTRY glUniform1iv (GLint location, GLsizei count, const GLint *value) {}
GL_APICALL void GL_APIENTRY glUniform2f (GLint location, GLfloat v0, GLfloat v1) {}
GL_APICALL void GL_APIENTRY glUniform2fv (GLint location, GLsizei count, const GLfloat *value) {}
GL_APICALL void GL_APIENTRY glUniform2i (GLint location, GLint v0, GLint v1) {}
GL_APICALL void GL_APIENTRY glUniform2iv (GLint location, GLsizei count, const GLint *value) {}
GL_APICALL void GL_APIENTRY glUniform3f (GLint location, GLfloat v0, GLfloat v1, GLfloat v2) {}
GL_APICALL void GL_APIENTRY glUniform3fv (GLint location, GLsizei count, const GLfloat *value) {}
GL_APICALL void GL_APIENTRY glUniform3i (GLint location, GLint v0, GLint v1, GLint v2) {}
GL_APICALL void GL_APIENTRY glUniform3iv (GLint location, GLsizei count, const GLint *value) {}
GL_APICALL void GL_APIENTRY glUniform4f (GLint location, GLfloat v0, GLfloat v1, GLfloat v2, GLfloat v3) {}
GL_APICALL void GL_APIENTRY glUniform4fv (GLint location, GLsizei count, const GLfloat *value) {}
GL_APICALL void GL_APIENTRY glUniform4i (GLint location, GLint v0, GLint v1, GLint v2, GLint v3) {}
GL_APICALL void GL_APIENTRY glUniform4iv (GLint location, GLsizei count, const GLint *value) {}
GL_APICALL void GL_APIENTRY glUniformMatrix2fv (GLint location, GLsizei count, GLboolean transpose, const GLfloat *value) {}
GL_APICALL void GL_APIENTRY glUniformMatrix3fv (GLint location, GLsizei count, GLboolean transpose, const GLfloat *value) {}
GL_APICALL void GL_APIENTRY glUniformMatrix4fv (GLint location, GLsizei count, GLboolean transpose, const GLfloat *value) {}
GL_APICALL void GL_APIENTRY glUseProgram (GLuint program) {}
GL_APICALL void GL_APIENTRY glValidateProgram (GLuint program) {}
GL_APICALL void GL_APIENTRY glVertexAttrib1f (GLuint index, GLfloat x) {}
GL_APICALL void GL_APIENTRY glVertexAttrib1fv (GLuint index, const GLfloat *v) {}
GL_APICALL void GL_APIENTRY glVertexAttrib2f (GLuint index, GLfloat x, GLfloat y) {}
GL_APICALL void GL_APIENTRY glVertexAttrib2fv (GLuint index, const GLfloat *v) {}
GL_APICALL void GL_APIENTRY glVertexAttrib3f (GLuint index, GLfloat x, GLfloat y, GLfloat z) {}
GL_APICALL void GL_APIENTRY glVertexAttrib3fv (GLuint index, const GLfloat *v) {}
GL_APICALL void GL_APIENTRY glVertexAttrib4f (GLuint index, GLfloat x, GLfloat y, GLfloat z, GLfloat w) {}
GL_APICALL void GL_APIENTRY glVertexAttrib4fv (GLuint index, const GLfloat *v) {}
GL_APICALL void GL_APIENTRY glVertexAttribPointer (GLuint index, GLint size, GLenum type, GLboolean normalized, GLsizei stride, const void *pointer) {}
GL_APICALL void GL_APIENTRY glViewport (GLint x, GLint y, GLsizei width, GLsizei height) {}
GL_APICALL void GL_APIENTRY glReadBuffer (GLenum src) {}
GL_APICALL void GL_APIENTRY glDrawRangeElements (GLenum mode, GLuint start, GLuint end, GLsizei count, GLenum type, const void *indices) {}
GL_APICALL void GL_APIENTRY glTexImage3D (GLenum target, GLint level, GLint internalformat, GLsizei width, GLsizei height, GLsizei depth, GLint border, GLenum format, GLenum type, const void *pixels) {}
GL_APICALL void GL_APIENTRY glTexSubImage3D (GLenum target, GLint level, GLint xoffset, GLint yoffset, GLint zoffset, GLsizei width, GLsizei height, GLsizei depth, GLenum format, GLenum type, const void *pixels) {}
GL_APICALL void GL_APIENTRY glCopyTexSubImage3D (GLenum target, GLint level, GLint xoffset, GLint yoffset, GLint zoffset, GLint x, GLint y, GLsizei width, GLsizei height) {}
GL_APICALL void GL_APIENTRY glCompressedTexImage3D (GLenum target, GLint level, GLenum internalformat, GLsizei width, GLsizei height, GLsizei depth, GLint border, GLsizei imageSize, const void *data) {}
GL_APICALL void GL_APIENTRY glCompressedTexSubImage3D (GLenum target, GLint level, GLint xoffset, GLint yoffset, GLint zoffset, GLsizei width, GLsizei height, GLsizei depth, GLenum format, GLsizei imageSize, const void *data) {}
GL_APICALL void GL_APIENTRY glGenQueries (GLsizei n, GLuint *ids) {}
GL_APICALL void GL_APIENTRY glDeleteQueries (GLsizei n, const GLuint *ids) {}
GL_APICALL GLboolean GL_APIENTRY glIsQuery (GLuint id) { return GL_FALSE; }
GL_APICALL void GL_APIENTRY glBeginQuery (GLenum target, GLuint id) {}
GL_APICALL void GL_APIENTRY glEndQuery (GLenum target) {}
GL_APICALL void GL_APIENTRY glGetQueryiv (GLenum target, GLenum pname, GLint *params) {}
GL_APICALL void GL_APIENTRY glGetQueryObjectuiv (GLuint id, GLenum pname, GLuint *params) {}
GL_APICALL GLboolean GL_APIENTRY glUnmapBuffer (GLenum target) { return GL_FALSE; }
GL_APICALL void GL_APIENTRY glGetBufferPointerv (GLenum target, GLenum pname, void **params) {}
GL_APICALL void GL_APIENTRY glDrawBuffers (GLsizei n, const GLenum *bufs) {}
GL_APICALL void GL_APIENTRY glUniformMatrix2x3fv (GLint location, GLsizei count, GLboolean transpose, const GLfloat *value) {}
GL_APICALL void GL_APIENTRY glUniformMatrix3x2fv (GLint location, GLsizei count, GLboolean transpose, const GLfloat *value) {}
GL_APICALL void GL_APIENTRY glUniformMatrix2x4fv (GLint location, GLsizei count, GLboolean transpose, const GLfloat *value) {}
GL_APICALL void GL_APIENTRY glUniformMatrix4x2fv (GLint location, GLsizei count, GLboolean transpose, const GLfloat *value) {}
GL_APICALL void GL_APIENTRY glUniformMatrix3x4fv (GLint location, GLsizei count, GLboolean transpose, const GLfloat *value) {}
GL_APICALL void GL_APIENTRY glUniformMatrix4x3fv (GLint location, GLsizei count, GLboolean transpose, const GLfloat *value) {}
GL_APICALL void GL_APIENTRY glBlitFramebuffer (GLint srcX0, GLint srcY0, GLint srcX1, GLint srcY1, GLint dstX0, GLint dstY0, GLint dstX1, GLint dstY1, GLbitfield mask, GLenum filter) {}
GL_APICALL void GL_APIENTRY glRenderbufferStorageMultisample (GLenum target, GLsizei samples, GLenum internalformat, GLsizei width, GLsizei height) {}
GL_APICALL void GL_APIENTRY glFramebufferTextureLayer (GLenum target, GLenum attachment, GLuint texture, GLint level, GLint layer) {}
GL_APICALL void* GL_APIENTRY glMapBufferRange(GLenum target, GLintptr offset, GLsizeiptr length, GLbitfield access) { return NULL; }
GL_APICALL void GL_APIENTRY glFlushMappedBufferRange (GLenum target, GLintptr offset, GLsizeiptr length) {}
GL_APICALL void GL_APIENTRY glBindVertexArray (GLuint array) {}
GL_APICALL void GL_APIENTRY glDeleteVertexArrays (GLsizei n, const GLuint *arrays) {}
GL_APICALL void GL_APIENTRY glGenVertexArrays (GLsizei n, GLuint *arrays) {}
GL_APICALL GLboolean GL_APIENTRY glIsVertexArray (GLuint array) { return GL_FALSE; }
GL_APICALL void GL_APIENTRY glGetIntegeri_v (GLenum target, GLuint index, GLint *data) {}
GL_APICALL void GL_APIENTRY glBeginTransformFeedback (GLenum primitiveMode) {}
GL_APICALL void GL_APIENTRY glEndTransformFeedback (void) {}
GL_APICALL void GL_APIENTRY glBindBufferRange (GLenum target, GLuint index, GLuint buffer, GLintptr offset, GLsizeiptr size) {}
GL_APICALL void GL_APIENTRY glBindBufferBase (GLenum target, GLuint index, GLuint buffer) {}
GL_APICALL void GL_APIENTRY glTransformFeedbackVaryings (GLuint program, GLsizei count, const GLchar *const*varyings, GLenum bufferMode) {}
GL_APICALL void GL_APIENTRY glGetTransformFeedbackVarying (GLuint program, GLuint index, GLsizei bufSize, GLsizei *length, GLsizei *size, GLenum *type, GLchar *name) {}
GL_APICALL void GL_APIENTRY glVertexAttribIPointer (GLuint index, GLint size, GLenum type, GLsizei stride, const void *pointer) {}
GL_APICALL void GL_APIENTRY glGetVertexAttribIiv (GLuint index, GLenum pname, GLint *params) {}
GL_APICALL void GL_APIENTRY glGetVertexAttribIuiv (GLuint index, GLenum pname, GLuint *params) {}
GL_APICALL void GL_APIENTRY glVertexAttribI4i (GLuint index, GLint x, GLint y, GLint z, GLint w) {}
GL_APICALL void GL_APIENTRY glVertexAttribI4ui (GLuint index, GLuint x, GLuint y, GLuint z, GLuint w) {}
GL_APICALL void GL_APIENTRY glVertexAttribI4iv (GLuint index, const GLint *v) {}
GL_APICALL void GL_APIENTRY glVertexAttribI4uiv (GLuint index, const GLuint *v) {}
GL_APICALL void GL_APIENTRY glGetUniformuiv (GLuint program, GLint location, GLuint *params) {}
GL_APICALL GLint GL_APIENTRY glGetFragDataLocation(GLuint program, const GLchar *name) { return -1; }
GL_APICALL void GL_APIENTRY glUniform1ui (GLint location, GLuint v0) {}
GL_APICALL void GL_APIENTRY glUniform2ui (GLint location, GLuint v0, GLuint v1) {}
GL_APICALL void GL_APIENTRY glUniform3ui (GLint location, GLuint v0, GLuint v1, GLuint v2) {}
GL_APICALL void GL_APIENTRY glUniform4ui (GLint location, GLuint v0, GLuint v1, GLuint v2, GLuint v3) {}
GL_APICALL void GL_APIENTRY glUniform1uiv (GLint location, GLsizei count, const GLuint *value) {}
GL_APICALL void GL_APIENTRY glUniform2uiv (GLint location, GLsizei count, const GLuint *value) {}
GL_APICALL void GL_APIENTRY glUniform3uiv (GLint location, GLsizei count, const GLuint *value) {}
GL_APICALL void GL_APIENTRY glUniform4uiv (GLint location, GLsizei count, const GLuint *value) {}
GL_APICALL void GL_APIENTRY glClearBufferiv (GLenum buffer, GLint drawbuffer, const GLint *value) {}
GL_APICALL void GL_APIENTRY glClearBufferuiv (GLenum buffer, GLint drawbuffer, const GLuint *value) {}
GL_APICALL void GL_APIENTRY glClearBufferfv (GLenum buffer, GLint drawbuffer, const GLfloat *value) {}
GL_APICALL void GL_APIENTRY glClearBufferfi (GLenum buffer, GLint drawbuffer, GLfloat depth, GLint stencil) {}
GL_APICALL const GLubyte *GL_APIENTRY glGetStringi(GLenum name, GLuint index) { return NULL; }
GL_APICALL void GL_APIENTRY glCopyBufferSubData (GLenum readTarget, GLenum writeTarget, GLintptr readOffset, GLintptr writeOffset, GLsizeiptr size) {}
GL_APICALL void GL_APIENTRY glGetUniformIndices (GLuint program, GLsizei uniformCount, const GLchar *const*uniformNames, GLuint *uniformIndices) {}
GL_APICALL void GL_APIENTRY glGetActiveUniformsiv (GLuint program, GLsizei uniformCount, const GLuint *uniformIndices, GLenum pname, GLint *params) {}
GL_APICALL GLuint GL_APIENTRY glGetUniformBlockIndex(GLuint program, const GLchar *uniformBlockName) { return GL_NONE; }
GL_APICALL void GL_APIENTRY glGetActiveUniformBlockiv (GLuint program, GLuint uniformBlockIndex, GLenum pname, GLint *params) {}
GL_APICALL void GL_APIENTRY glGetActiveUniformBlockName (GLuint program, GLuint uniformBlockIndex, GLsizei bufSize, GLsizei *length, GLchar *uniformBlockName) {}
GL_APICALL void GL_APIENTRY glUniformBlockBinding (GLuint program, GLuint uniformBlockIndex, GLuint uniformBlockBinding) {}
GL_APICALL void GL_APIENTRY glDrawArraysInstanced (GLenum mode, GLint first, GLsizei count, GLsizei instancecount) {}
GL_APICALL void GL_APIENTRY glDrawElementsInstanced (GLenum mode, GLsizei count, GLenum type, const void *indices, GLsizei instancecount) {}
GL_APICALL GLsync GL_APIENTRY glFenceSync(GLenum condition, GLbitfield flags) { return GL_NONE; }
GL_APICALL GLboolean GL_APIENTRY glIsSync (GLsync sync) { return GL_FALSE; }
GL_APICALL void GL_APIENTRY glDeleteSync (GLsync sync) {}
GL_APICALL GLenum GL_APIENTRY glClientWaitSync(GLsync sync, GLbitfield flags, GLuint64 timeout) { return GL_WAIT_FAILED; }
GL_APICALL void GL_APIENTRY glWaitSync (GLsync sync, GLbitfield flags, GLuint64 timeout) {}
GL_APICALL void GL_APIENTRY glGetInteger64v (GLenum pname, GLint64 *data) {}
GL_APICALL void GL_APIENTRY glGetSynciv (GLsync sync, GLenum pname, GLsizei bufSize, GLsizei *length, GLint *values) {}
GL_APICALL void GL_APIENTRY glGetInteger64i_v (GLenum target, GLuint index, GLint64 *data) {}
GL_APICALL void GL_APIENTRY glGetBufferParameteri64v (GLenum target, GLenum pname, GLint64 *params) {}
GL_APICALL void GL_APIENTRY glGenSamplers (GLsizei count, GLuint *samplers) {}
GL_APICALL void GL_APIENTRY glDeleteSamplers (GLsizei count, const GLuint *samplers) {}
GL_APICALL GLboolean GL_APIENTRY glIsSampler (GLuint sampler) { return GL_FALSE; }
GL_APICALL void GL_APIENTRY glBindSampler (GLuint unit, GLuint sampler) {}
GL_APICALL void GL_APIENTRY glSamplerParameteri (GLuint sampler, GLenum pname, GLint param) {}
GL_APICALL void GL_APIENTRY glSamplerParameteriv (GLuint sampler, GLenum pname, const GLint *param) {}
GL_APICALL void GL_APIENTRY glSamplerParameterf (GLuint sampler, GLenum pname, GLfloat param) {}
GL_APICALL void GL_APIENTRY glSamplerParameterfv (GLuint sampler, GLenum pname, const GLfloat *param) {}
GL_APICALL void GL_APIENTRY glGetSamplerParameteriv (GLuint sampler, GLenum pname, GLint *params) {}
GL_APICALL void GL_APIENTRY glGetSamplerParameterfv (GLuint sampler, GLenum pname, GLfloat *params) {}
GL_APICALL void GL_APIENTRY glVertexAttribDivisor (GLuint index, GLuint divisor) {}
GL_APICALL void GL_APIENTRY glBindTransformFeedback (GLenum target, GLuint id) {}
GL_APICALL void GL_APIENTRY glDeleteTransformFeedbacks (GLsizei n, const GLuint *ids) {}
GL_APICALL void GL_APIENTRY glGenTransformFeedbacks (GLsizei n, GLuint *ids) {}
GL_APICALL GLboolean GL_APIENTRY glIsTransformFeedback (GLuint id) { return GL_FALSE; }
GL_APICALL void GL_APIENTRY glPauseTransformFeedback (void) {}
GL_APICALL void GL_APIENTRY glResumeTransformFeedback (void) {}
GL_APICALL void GL_APIENTRY glGetProgramBinary (GLuint program, GLsizei bufSize, GLsizei *length, GLenum *binaryFormat, void *binary) {}
GL_APICALL void GL_APIENTRY glProgramBinary (GLuint program, GLenum binaryFormat, const void *binary, GLsizei length) {}
GL_APICALL void GL_APIENTRY glProgramParameteri (GLuint program, GLenum pname, GLint value) {}
GL_APICALL void GL_APIENTRY glInvalidateFramebuffer (GLenum target, GLsizei numAttachments, const GLenum *attachments) {}
GL_APICALL void GL_APIENTRY glInvalidateSubFramebuffer (GLenum target, GLsizei numAttachments, const GLenum *attachments, GLint x, GLint y, GLsizei width, GLsizei height) {}
GL_APICALL void GL_APIENTRY glTexStorage2D (GLenum target, GLsizei levels, GLenum internalformat, GLsizei width, GLsizei height) {}
GL_APICALL void GL_APIENTRY glTexStorage3D (GLenum target, GLsizei levels, GLenum internalformat, GLsizei width, GLsizei height, GLsizei depth) {}
GL_APICALL void GL_APIENTRY glGetInternalformativ (GLenum target, GLenum internalformat, GLenum pname, GLsizei bufSize, GLint *params) {}
GL_APICALL void GL_APIENTRY glDispatchCompute (GLuint num_groups_x, GLuint num_groups_y, GLuint num_groups_z) {}
GL_APICALL void GL_APIENTRY glDispatchComputeIndirect (GLintptr indirect) {}
GL_APICALL void GL_APIENTRY glDrawArraysIndirect (GLenum mode, const void *indirect) {}
GL_APICALL void GL_APIENTRY glDrawElementsIndirect (GLenum mode, GLenum type, const void *indirect) {}
GL_APICALL void GL_APIENTRY glFramebufferParameteri (GLenum target, GLenum pname, GLint param) {}
GL_APICALL void GL_APIENTRY glGetFramebufferParameteriv (GLenum target, GLenum pname, GLint *params) {}
GL_APICALL void GL_APIENTRY glGetProgramInterfaceiv (GLuint program, GLenum programInterface, GLenum pname, GLint *params) {}
GL_APICALL GLuint GL_APIENTRY glGetProgramResourceIndex(GLuint program, GLenum programInterface, const GLchar *name) { return GL_NONE; }
GL_APICALL void GL_APIENTRY glGetProgramResourceName (GLuint program, GLenum programInterface, GLuint index, GLsizei bufSize, GLsizei *length, GLchar *name) {}
GL_APICALL void GL_APIENTRY glGetProgramResourceiv (GLuint program, GLenum programInterface, GLuint index, GLsizei propCount, const GLenum *props, GLsizei bufSize, GLsizei *length, GLint *params) {}
GL_APICALL GLint GL_APIENTRY glGetProgramResourceLocation(GLuint program, GLenum programInterface, const GLchar *name) { return -1; }
GL_APICALL void GL_APIENTRY glUseProgramStages (GLuint pipeline, GLbitfield stages, GLuint program) {}
GL_APICALL void GL_APIENTRY glActiveShaderProgram (GLuint pipeline, GLuint program) {}
GL_APICALL GLuint GL_APIENTRY glCreateShaderProgramv(GLenum type, GLsizei count, const GLchar *const*strings) { return GL_NONE; }
GL_APICALL void GL_APIENTRY glBindProgramPipeline (GLuint pipeline) {}
GL_APICALL void GL_APIENTRY glDeleteProgramPipelines (GLsizei n, const GLuint *pipelines) {}
GL_APICALL void GL_APIENTRY glGenProgramPipelines (GLsizei n, GLuint *pipelines) {}
GL_APICALL GLboolean GL_APIENTRY glIsProgramPipeline (GLuint pipeline) { return GL_FALSE; }
GL_APICALL void GL_APIENTRY glGetProgramPipelineiv (GLuint pipeline, GLenum pname, GLint *params) {}
GL_APICALL void GL_APIENTRY glProgramUniform1i (GLuint program, GLint location, GLint v0) {}
GL_APICALL void GL_APIENTRY glProgramUniform2i (GLuint program, GLint location, GLint v0, GLint v1) {}
GL_APICALL void GL_APIENTRY glProgramUniform3i (GLuint program, GLint location, GLint v0, GLint v1, GLint v2) {}
GL_APICALL void GL_APIENTRY glProgramUniform4i (GLuint program, GLint location, GLint v0, GLint v1, GLint v2, GLint v3) {}
GL_APICALL void GL_APIENTRY glProgramUniform1ui (GLuint program, GLint location, GLuint v0) {}
GL_APICALL void GL_APIENTRY glProgramUniform2ui (GLuint program, GLint location, GLuint v0, GLuint v1) {}
GL_APICALL void GL_APIENTRY glProgramUniform3ui (GLuint program, GLint location, GLuint v0, GLuint v1, GLuint v2) {}
GL_APICALL void GL_APIENTRY glProgramUniform4ui (GLuint program, GLint location, GLuint v0, GLuint v1, GLuint v2, GLuint v3) {}
GL_APICALL void GL_APIENTRY glProgramUniform1f (GLuint program, GLint location, GLfloat v0) {}
GL_APICALL void GL_APIENTRY glProgramUniform2f (GLuint program, GLint location, GLfloat v0, GLfloat v1) {}
GL_APICALL void GL_APIENTRY glProgramUniform3f (GLuint program, GLint location, GLfloat v0, GLfloat v1, GLfloat v2) {}
GL_APICALL void GL_APIENTRY glProgramUniform4f (GLuint program, GLint location, GLfloat v0, GLfloat v1, GLfloat v2, GLfloat v3) {}
GL_APICALL void GL_APIENTRY glProgramUniform1iv (GLuint program, GLint location, GLsizei count, const GLint *value) {}
GL_APICALL void GL_APIENTRY glProgramUniform2iv (GLuint program, GLint location, GLsizei count, const GLint *value) {}
GL_APICALL void GL_APIENTRY glProgramUniform3iv (GLuint program, GLint location, GLsizei count, const GLint *value) {}
GL_APICALL void GL_APIENTRY glProgramUniform4iv (GLuint program, GLint location, GLsizei count, const GLint *value) {}
GL_APICALL void GL_APIENTRY glProgramUniform1uiv (GLuint program, GLint location, GLsizei count, const GLuint *value) {}
GL_APICALL void GL_APIENTRY glProgramUniform2uiv (GLuint program, GLint location, GLsizei count, const GLuint *value) {}
GL_APICALL void GL_APIENTRY glProgramUniform3uiv (GLuint program, GLint location, GLsizei count, const GLuint *value) {}
GL_APICALL void GL_APIENTRY glProgramUniform4uiv (GLuint program, GLint location, GLsizei count, const GLuint *value) {}
GL_APICALL void GL_APIENTRY glProgramUniform1fv (GLuint program, GLint location, GLsizei count, const GLfloat *value) {}
GL_APICALL void GL_APIENTRY glProgramUniform2fv (GLuint program, GLint location, GLsizei count, const GLfloat *value) {}
GL_APICALL void GL_APIENTRY glProgramUniform3fv (GLuint program, GLint location, GLsizei count, const GLfloat *value) {}
GL_APICALL void GL_APIENTRY glProgramUniform4fv (GLuint program, GLint location, GLsizei count, const GLfloat *value) {}
GL_APICALL void GL_APIENTRY glProgramUniformMatrix2fv (GLuint program, GLint location, GLsizei count, GLboolean transpose, const GLfloat *value) {}
GL_APICALL void GL_APIENTRY glProgramUniformMatrix3fv (GLuint program, GLint location, GLsizei count, GLboolean transpose, const GLfloat *value) {}
GL_APICALL void GL_APIENTRY glProgramUniformMatrix4fv (GLuint program, GLint location, GLsizei count, GLboolean transpose, const GLfloat *value) {}
GL_APICALL void GL_APIENTRY glProgramUniformMatrix2x3fv (GLuint program, GLint location, GLsizei count, GLboolean transpose, const GLfloat *value) {}
GL_APICALL void GL_APIENTRY glProgramUniformMatrix3x2fv (GLuint program, GLint location, GLsizei count, GLboolean transpose, const GLfloat *value) {}
GL_APICALL void GL_APIENTRY glProgramUniformMatrix2x4fv (GLuint program, GLint location, GLsizei count, GLboolean transpose, const GLfloat *value) {}
GL_APICALL void GL_APIENTRY glProgramUniformMatrix4x2fv (GLuint program, GLint location, GLsizei count, GLboolean transpose, const GLfloat *value) {}
GL_APICALL void GL_APIENTRY glProgramUniformMatrix3x4fv (GLuint program, GLint location, GLsizei count, GLboolean transpose, const GLfloat *value) {}
GL_APICALL void GL_APIENTRY glProgramUniformMatrix4x3fv (GLuint program, GLint location, GLsizei count, GLboolean transpose, const GLfloat *value) {}
GL_APICALL void GL_APIENTRY glValidateProgramPipeline (GLuint pipeline) {}
GL_APICALL void GL_APIENTRY glGetProgramPipelineInfoLog (GLuint pipeline, GLsizei bufSize, GLsizei *length, GLchar *infoLog) {}
GL_APICALL void GL_APIENTRY glBindImageTexture (GLuint unit, GLuint texture, GLint level, GLboolean layered, GLint layer, GLenum access, GLenum format) {}
GL_APICALL void GL_APIENTRY glGetBooleani_v (GLenum target, GLuint index, GLboolean *data) {}
GL_APICALL void GL_APIENTRY glMemoryBarrier (GLbitfield barriers) {}
GL_APICALL void GL_APIENTRY glMemoryBarrierByRegion (GLbitfield barriers) {}
GL_APICALL void GL_APIENTRY glTexStorage2DMultisample (GLenum target, GLsizei samples, GLenum internalformat, GLsizei width, GLsizei height, GLboolean fixedsamplelocations) {}
GL_APICALL void GL_APIENTRY glGetMultisamplefv (GLenum pname, GLuint index, GLfloat *val) {}
GL_APICALL void GL_APIENTRY glSampleMaski (GLuint maskNumber, GLbitfield mask) {}
GL_APICALL void GL_APIENTRY glGetTexLevelParameteriv (GLenum target, GLint level, GLenum pname, GLint *params) {}
GL_APICALL void GL_APIENTRY glGetTexLevelParameterfv (GLenum target, GLint level, GLenum pname, GLfloat *params) {}
GL_APICALL void GL_APIENTRY glBindVertexBuffer (GLuint bindingindex, GLuint buffer, GLintptr offset, GLsizei stride) {}
GL_APICALL void GL_APIENTRY glVertexAttribFormat (GLuint attribindex, GLint size, GLenum type, GLboolean normalized, GLuint relativeoffset) {}
GL_APICALL void GL_APIENTRY glVertexAttribIFormat (GLuint attribindex, GLint size, GLenum type, GLuint relativeoffset) {}
GL_APICALL void GL_APIENTRY glVertexAttribBinding (GLuint attribindex, GLuint bindingindex) {}
GL_APICALL void GL_APIENTRY glVertexBindingDivisor (GLuint bindingindex, GLuint divisor) {}
GL_APICALL void GL_APIENTRY glBlendBarrier (void) {}
GL_APICALL void GL_APIENTRY glCopyImageSubData (GLuint srcName, GLenum srcTarget, GLint srcLevel, GLint srcX, GLint srcY, GLint srcZ, GLuint dstName, GLenum dstTarget, GLint dstLevel, GLint dstX, GLint dstY, GLint dstZ, GLsizei srcWidth, GLsizei srcHeight, GLsizei srcDepth) {}
GL_APICALL void GL_APIENTRY glDebugMessageControl (GLenum source, GLenum type, GLenum severity, GLsizei count, const GLuint *ids, GLboolean enabled) {}
GL_APICALL void GL_APIENTRY glDebugMessageInsert (GLenum source, GLenum type, GLuint id, GLenum severity, GLsizei length, const GLchar *buf) {}
GL_APICALL void GL_APIENTRY glDebugMessageCallback (GLDEBUGPROC callback, const void *userParam) {}
GL_APICALL GLuint GL_APIENTRY glGetDebugMessageLog(GLuint count, GLsizei bufSize, GLenum *sources, GLenum *types, GLuint *ids, GLenum *severities, GLsizei *lengths, GLchar *messageLog) { return GL_NONE; }
GL_APICALL void GL_APIENTRY glPushDebugGroup (GLenum source, GLuint id, GLsizei length, const GLchar *message) {}
GL_APICALL void GL_APIENTRY glPopDebugGroup (void) {}
GL_APICALL void GL_APIENTRY glObjectLabel (GLenum identifier, GLuint name, GLsizei length, const GLchar *label) {}
GL_APICALL void GL_APIENTRY glGetObjectLabel (GLenum identifier, GLuint name, GLsizei bufSize, GLsizei *length, GLchar *label) {}
GL_APICALL void GL_APIENTRY glObjectPtrLabel (const void *ptr, GLsizei length, const GLchar *label) {}
GL_APICALL void GL_APIENTRY glGetObjectPtrLabel (const void *ptr, GLsizei bufSize, GLsizei *length, GLchar *label) {}
GL_APICALL void GL_APIENTRY glGetPointerv (GLenum pname, void **params) {}
GL_APICALL void GL_APIENTRY glEnablei (GLenum target, GLuint index) {}
GL_APICALL void GL_APIENTRY glDisablei (GLenum target, GLuint index) {}
GL_APICALL void GL_APIENTRY glBlendEquationi (GLuint buf, GLenum mode) {}
GL_APICALL void GL_APIENTRY glBlendEquationSeparatei (GLuint buf, GLenum modeRGB, GLenum modeAlpha) {}
GL_APICALL void GL_APIENTRY glBlendFunci (GLuint buf, GLenum src, GLenum dst) {}
GL_APICALL void GL_APIENTRY glBlendFuncSeparatei (GLuint buf, GLenum srcRGB, GLenum dstRGB, GLenum srcAlpha, GLenum dstAlpha) {}
GL_APICALL void GL_APIENTRY glColorMaski (GLuint index, GLboolean r, GLboolean g, GLboolean b, GLboolean a) {}
GL_APICALL GLboolean GL_APIENTRY glIsEnabledi (GLenum target, GLuint index) { return GL_FALSE; }
GL_APICALL void GL_APIENTRY glDrawElementsBaseVertex (GLenum mode, GLsizei count, GLenum type, const void *indices, GLint basevertex) {}
GL_APICALL void GL_APIENTRY glDrawRangeElementsBaseVertex (GLenum mode, GLuint start, GLuint end, GLsizei count, GLenum type, const void *indices, GLint basevertex) {}
GL_APICALL void GL_APIENTRY glDrawElementsInstancedBaseVertex (GLenum mode, GLsizei count, GLenum type, const void *indices, GLsizei instancecount, GLint basevertex) {}
GL_APICALL void GL_APIENTRY glFramebufferTexture (GLenum target, GLenum attachment, GLuint texture, GLint level) {}
GL_APICALL void GL_APIENTRY glPrimitiveBoundingBox (GLfloat minX, GLfloat minY, GLfloat minZ, GLfloat minW, GLfloat maxX, GLfloat maxY, GLfloat maxZ, GLfloat maxW) {}
GL_APICALL GLenum GL_APIENTRY glGetGraphicsResetStatus (void) { return GL_UNKNOWN_CONTEXT_RESET; }
GL_APICALL void GL_APIENTRY glReadnPixels (GLint x, GLint y, GLsizei width, GLsizei height, GLenum format, GLenum type, GLsizei bufSize, void *data) {}
GL_APICALL void GL_APIENTRY glGetnUniformfv (GLuint program, GLint location, GLsizei bufSize, GLfloat *params) {}
GL_APICALL void GL_APIENTRY glGetnUniformiv (GLuint program, GLint location, GLsizei bufSize, GLint *params) {}
GL_APICALL void GL_APIENTRY glGetnUniformuiv (GLuint program, GLint location, GLsizei bufSize, GLuint *params) {}
GL_APICALL void GL_APIENTRY glMinSampleShading (GLfloat value) {}
GL_APICALL void GL_APIENTRY glPatchParameteri (GLenum pname, GLint value) {}
GL_APICALL void GL_APIENTRY glTexParameterIiv (GLenum target, GLenum pname, const GLint *params) {}
GL_APICALL void GL_APIENTRY glTexParameterIuiv (GLenum target, GLenum pname, const GLuint *params) {}
GL_APICALL void GL_APIENTRY glGetTexParameterIiv (GLenum target, GLenum pname, GLint *params) {}
GL_APICALL void GL_APIENTRY glGetTexParameterIuiv (GLenum target, GLenum pname, GLuint *params) {}
GL_APICALL void GL_APIENTRY glSamplerParameterIiv (GLuint sampler, GLenum pname, const GLint *param) {}
GL_APICALL void GL_APIENTRY glSamplerParameterIuiv (GLuint sampler, GLenum pname, const GLuint *param) {}
GL_APICALL void GL_APIENTRY glGetSamplerParameterIiv (GLuint sampler, GLenum pname, GLint *params) {}
GL_APICALL void GL_APIENTRY glGetSamplerParameterIuiv (GLuint sampler, GLenum pname, GLuint *params) {}
GL_APICALL void GL_APIENTRY glTexBuffer (GLenum target, GLenum internalformat, GLuint buffer) {}
GL_APICALL void GL_APIENTRY glTexBufferRange (GLenum target, GLenum internalformat, GLuint buffer, GLintptr offset, GLsizeiptr size) {}
GL_APICALL void GL_APIENTRY glTexStorage3DMultisample (GLenum target, GLsizei samples, GLenum internalformat, GLsizei width, GLsizei height, GLsizei depth, GLboolean fixedsamplelocations) {}

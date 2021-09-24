/*
 **
 ** Copyright 2009, The Android Open Source Project
 **
 ** Licensed under the Apache License, Version 2.0 (the "License");
 ** you may not use this file except in compliance with the License.
 ** You may obtain a copy of the License at
 **
 **     http://www.apache.org/licenses/LICENSE-2.0
 **
 ** Unless required by applicable law or agreed to in writing, software
 ** distributed under the License is distributed on an "AS IS" BASIS,
 ** WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 ** See the License for the specific language governing permissions and
 ** limitations under the License.
 */
// This source file is automatically generated
package gov.tak.platform.commons.opengl;

/** OpenGL ES 2.0
 */
public class GLES20 implements GLES20Constants {
    // C function void glActiveTexture ( GLenum texture )
    public static void glActiveTexture(
            int texture
    ) { android.opengl.GLES20.glActiveTexture(texture); }
    // C function void glAttachShader ( GLuint program, GLuint shader )
    public static void glAttachShader(
            int program,
            int shader
    ) { android.opengl.GLES20.glAttachShader(program, shader); }
    // C function void glBindAttribLocation ( GLuint program, GLuint index, const char *name )
    public static void glBindAttribLocation(
            int program,
            int index,
            String name
    ) { android.opengl.GLES20.glBindAttribLocation(program, index, name); }
    // C function void glBindBuffer ( GLenum target, GLuint buffer )
    public static void glBindBuffer(
            int target,
            int buffer
    ) { android.opengl.GLES20.glBindBuffer(target, buffer); }
    // C function void glBindFramebuffer ( GLenum target, GLuint framebuffer )
    public static void glBindFramebuffer(
            int target,
            int framebuffer
    ) { android.opengl.GLES20.glBindFramebuffer(target, framebuffer);}
    // C function void glBindRenderbuffer ( GLenum target, GLuint renderbuffer )
    public static void glBindRenderbuffer(
            int target,
            int renderbuffer
    ) {android.opengl.GLES20.glBindRenderbuffer(target, renderbuffer);}
    // C function void glBindTexture ( GLenum target, GLuint texture )
    public static void glBindTexture(
            int target,
            int texture
    ) {android.opengl.GLES20.glBindTexture(target, texture);}
    // C function void glBlendColor ( GLclampf red, GLclampf green, GLclampf blue, GLclampf alpha )
    public static void glBlendColor(
            float red,
            float green,
            float blue,
            float alpha
    ) {android.opengl.GLES20.glBlendColor(red, green, blue, alpha);}
    // C function void glBlendEquation ( GLenum mode )
    public static void glBlendEquation(
            int mode
    ) {android.opengl.GLES20.glBlendEquation(mode);}
    // C function void glBlendEquationSeparate ( GLenum modeRGB, GLenum modeAlpha )
    public static void glBlendEquationSeparate(
            int modeRGB,
            int modeAlpha
    ) {android.opengl.GLES20.glBlendEquationSeparate(modeRGB, modeAlpha);}
    // C function void glBlendFunc ( GLenum sfactor, GLenum dfactor )
    public static void glBlendFunc(
            int sfactor,
            int dfactor
    ) {android.opengl.GLES20.glBlendFunc(sfactor, dfactor);}
    // C function void glBlendFuncSeparate ( GLenum srcRGB, GLenum dstRGB, GLenum srcAlpha, GLenum dstAlpha )
    public static void glBlendFuncSeparate(
            int srcRGB,
            int dstRGB,
            int srcAlpha,
            int dstAlpha
    ) {android.opengl.GLES20.glBlendFuncSeparate(srcRGB, dstRGB, srcAlpha, dstAlpha);}
    // C function void glBufferData ( GLenum target, GLsizeiptr size, const GLvoid *data, GLenum usage )
    public static void glBufferData(
            int target,
            int size,
            java.nio.Buffer data,
            int usage
    ) {android.opengl.GLES20.glBufferData(target, size, data, usage);}
    // C function void glBufferSubData ( GLenum target, GLintptr offset, GLsizeiptr size, const GLvoid *data )
    public static void glBufferSubData(
            int target,
            int offset,
            int size,
            java.nio.Buffer data
    ) {android.opengl.GLES20.glBufferSubData(target, offset, size, data);}
    // C function GLenum glCheckFramebufferStatus ( GLenum target )
    public static int glCheckFramebufferStatus(
            int target
    ) {return android.opengl.GLES20.glCheckFramebufferStatus(target);}
    // C function void glClear ( GLbitfield mask )
    public static void glClear(
            int mask
    ) {android.opengl.GLES20.glClear(mask);}
    // C function void glClearColor ( GLclampf red, GLclampf green, GLclampf blue, GLclampf alpha )
    public static void glClearColor(
            float red,
            float green,
            float blue,
            float alpha
    ) {android.opengl.GLES20.glClearColor(red, green, blue, alpha);}
    // C function void glClearDepthf ( GLclampf depth )
    public static void glClearDepthf(
            float depth
    ) {android.opengl.GLES20.glClearDepthf(depth);}
    // C function void glClearStencil ( GLint s )
    public static void glClearStencil(
            int s
    ) {android.opengl.GLES20.glClearStencil(s);}
    // C function void glColorMask ( GLboolean red, GLboolean green, GLboolean blue, GLboolean alpha )
    public static void glColorMask(
            boolean red,
            boolean green,
            boolean blue,
            boolean alpha
    ) {android.opengl.GLES20.glColorMask(red, green, blue, alpha);}
    // C function void glCompileShader ( GLuint shader )
    public static void glCompileShader(
            int shader
    ) {android.opengl.GLES20.glCompileShader(shader);}
    // C function void glCompressedTexImage2D ( GLenum target, GLint level, GLenum internalformat, GLsizei width, GLsizei height, GLint border, GLsizei imageSize, const GLvoid *data )
    public static void glCompressedTexImage2D(
            int target,
            int level,
            int internalformat,
            int width,
            int height,
            int border,
            int imageSize,
            java.nio.Buffer data
    ) {android.opengl.GLES20.glCompressedTexImage2D(target, level, internalformat, width, height, border, imageSize, data);}
    // C function void glCompressedTexSubImage2D ( GLenum target, GLint level, GLint xoffset, GLint yoffset, GLsizei width, GLsizei height, GLenum format, GLsizei imageSize, const GLvoid *data )
    public static void glCompressedTexSubImage2D(
            int target,
            int level,
            int xoffset,
            int yoffset,
            int width,
            int height,
            int format,
            int imageSize,
            java.nio.Buffer data
    ) {android.opengl.GLES20.glCompressedTexSubImage2D(target, level, xoffset, yoffset, width, height, format, imageSize, data);}
    // C function void glCopyTexImage2D ( GLenum target, GLint level, GLenum internalformat, GLint x, GLint y, GLsizei width, GLsizei height, GLint border )
    public static void glCopyTexImage2D(
            int target,
            int level,
            int internalformat,
            int x,
            int y,
            int width,
            int height,
            int border
    ) {android.opengl.GLES20.glCopyTexImage2D(target, level, internalformat, x, y, width, height, border);}
    // C function void glCopyTexSubImage2D ( GLenum target, GLint level, GLint xoffset, GLint yoffset, GLint x, GLint y, GLsizei width, GLsizei height )
    public static void glCopyTexSubImage2D(
            int target,
            int level,
            int xoffset,
            int yoffset,
            int x,
            int y,
            int width,
            int height
    ) {android.opengl.GLES20.glCopyTexSubImage2D(target, level, xoffset, yoffset, x, y, width, height);}
    // C function GLuint glCreateProgram ( void )
    public static int glCreateProgram(
    ) {return android.opengl.GLES20.glCreateProgram();}
    // C function GLuint glCreateShader ( GLenum type )
    public static int glCreateShader(
            int type
    ) {return android.opengl.GLES20.glCreateShader(type);}
    // C function void glCullFace ( GLenum mode )
    public static void glCullFace(
            int mode
    ) {android.opengl.GLES20.glCullFace(mode);}
    // C function void glDeleteBuffers ( GLsizei n, const GLuint *buffers )
    public static void glDeleteBuffers(
            int n,
            int[] buffers,
            int offset
    ) {android.opengl.GLES20.glDeleteBuffers(n, buffers, offset);}
    // C function void glDeleteBuffers ( GLsizei n, const GLuint *buffers )
    public static void glDeleteBuffers(
            int n,
            java.nio.IntBuffer buffers
    ) {android.opengl.GLES20.glDeleteBuffers(n, buffers);}
    // C function void glDeleteFramebuffers ( GLsizei n, const GLuint *framebuffers )
    public static void glDeleteFramebuffers(
            int n,
            int[] framebuffers,
            int offset
    ) {android.opengl.GLES20.glDeleteFramebuffers(n, framebuffers, offset);}
    // C function void glDeleteFramebuffers ( GLsizei n, const GLuint *framebuffers )
    public static void glDeleteFramebuffers(
            int n,
            java.nio.IntBuffer framebuffers
    ) {android.opengl.GLES20.glDeleteFramebuffers(n, framebuffers);}
    // C function void glDeleteProgram ( GLuint program )
    public static void glDeleteProgram(
            int program
    ) {android.opengl.GLES20.glDeleteProgram(program);}
    // C function void glDeleteRenderbuffers ( GLsizei n, const GLuint *renderbuffers )
    public static void glDeleteRenderbuffers(
            int n,
            int[] renderbuffers,
            int offset
    ) {android.opengl.GLES20.glDeleteRenderbuffers(n, renderbuffers, offset);}
    // C function void glDeleteRenderbuffers ( GLsizei n, const GLuint *renderbuffers )
    public static void glDeleteRenderbuffers(
            int n,
            java.nio.IntBuffer renderbuffers
    ) {android.opengl.GLES20.glDeleteRenderbuffers(n, renderbuffers);}
    // C function void glDeleteShader ( GLuint shader )
    public static void glDeleteShader(
            int shader
    ) {android.opengl.GLES20.glDeleteShader(shader);}
    // C function void glDeleteTextures ( GLsizei n, const GLuint *textures )
    public static void glDeleteTextures(
            int n,
            int[] textures,
            int offset
    ) {android.opengl.GLES20.glDeleteTextures(n, textures, offset);}
    // C function void glDeleteTextures ( GLsizei n, const GLuint *textures )
    public static void glDeleteTextures(
            int n,
            java.nio.IntBuffer textures
    ) {android.opengl.GLES20.glDeleteTextures(n, textures);}
    // C function void glDepthFunc ( GLenum func )
    public static void glDepthFunc(
            int func
    ) {android.opengl.GLES20.glDepthFunc(func);}
    // C function void glDepthMask ( GLboolean flag )
    public static void glDepthMask(
            boolean flag
    ) {android.opengl.GLES20.glDepthMask(flag);}
    // C function void glDepthRangef ( GLclampf zNear, GLclampf zFar )
    public static void glDepthRangef(
            float zNear,
            float zFar
    ) {android.opengl.GLES20.glDepthRangef(zNear, zFar);}
    // C function void glDetachShader ( GLuint program, GLuint shader )
    public static void glDetachShader(
            int program,
            int shader
    ) {android.opengl.GLES20.glDetachShader(program, shader);}
    // C function void glDisable ( GLenum cap )
    public static void glDisable(
            int cap
    ) {android.opengl.GLES20.glDisable(cap);}
    // C function void glDisableVertexAttribArray ( GLuint index )
    public static void glDisableVertexAttribArray(
            int index
    ) { android.opengl.GLES20.glDisableVertexAttribArray(index); }
    // C function void glDrawArrays ( GLenum mode, GLint first, GLsizei count )
    public static void glDrawArrays(
            int mode,
            int first,
            int count
    ) { android.opengl.GLES20.glDrawArrays(mode, first, count); }
    // C function void glDrawElements ( GLenum mode, GLsizei count, GLenum type, GLint offset )
    public static void glDrawElements(
            int mode,
            int count,
            int type,
            int offset
    ) { android.opengl.GLES20.glDrawElements(mode, count, type, offset); }
    // C function void glDrawElements ( GLenum mode, GLsizei count, GLenum type, const GLvoid *indices )
    public static void glDrawElements(
            int mode,
            int count,
            int type,
            java.nio.Buffer indices
    ) { android.opengl.GLES20.glDrawElements(mode, count, type, indices); }
    // C function void glEnable ( GLenum cap )
    public static void glEnable(
            int cap
    ) {android.opengl.GLES20.glEnable(cap);}
    // C function void glEnableVertexAttribArray ( GLuint index )
    public static void glEnableVertexAttribArray(
            int index
    ) { android.opengl.GLES20.glEnableVertexAttribArray(index); }
    // C function void glFinish ( void )
    public static void glFinish(
    ) {android.opengl.GLES20.glFinish();}
    // C function void glFlush ( void )
    public static void glFlush(
    ) {android.opengl.GLES20.glFlush();}
    // C function void glFramebufferRenderbuffer ( GLenum target, GLenum attachment, GLenum renderbuffertarget, GLuint renderbuffer )
    public static void glFramebufferRenderbuffer(
            int target,
            int attachment,
            int renderbuffertarget,
            int renderbuffer
    ) {android.opengl.GLES20.glFramebufferRenderbuffer(target, attachment, renderbuffertarget, renderbuffer);}
    // C function void glFramebufferTexture2D ( GLenum target, GLenum attachment, GLenum textarget, GLuint texture, GLint level )
    public static void glFramebufferTexture2D(
            int target,
            int attachment,
            int textarget,
            int texture,
            int level
    ) {android.opengl.GLES20.glFramebufferTexture2D(target, attachment, textarget, texture, level);}
    // C function void glFrontFace ( GLenum mode )
    public static void glFrontFace(
            int mode
    ) {android.opengl.GLES20.glFrontFace(mode);}
    // C function void glGenBuffers ( GLsizei n, GLuint *buffers )
    public static void glGenBuffers(
            int n,
            int[] buffers,
            int offset
    ) {android.opengl.GLES20.glGenBuffers(n, buffers, offset);}
    // C function void glGenBuffers ( GLsizei n, GLuint *buffers )
    public static void glGenBuffers(
            int n,
            java.nio.IntBuffer buffers
    ) {android.opengl.GLES20.glGenBuffers(n, buffers);}
    // C function void glGenerateMipmap ( GLenum target )
    public static void glGenerateMipmap(
            int target
    ) {android.opengl.GLES20.glGenerateMipmap(target);}
    // C function void glGenFramebuffers ( GLsizei n, GLuint *framebuffers )
    public static void glGenFramebuffers(
            int n,
            int[] framebuffers,
            int offset
    ) {android.opengl.GLES20.glGenFramebuffers(n, framebuffers, offset);}
    // C function void glGenFramebuffers ( GLsizei n, GLuint *framebuffers )
    public static void glGenFramebuffers(
            int n,
            java.nio.IntBuffer framebuffers
    ) {android.opengl.GLES20.glGenFramebuffers(n, framebuffers);}
    // C function void glGenRenderbuffers ( GLsizei n, GLuint *renderbuffers )
    public static void glGenRenderbuffers(
            int n,
            int[] renderbuffers,
            int offset
    ) {android.opengl.GLES20.glGenRenderbuffers(n, renderbuffers, offset);}
    // C function void glGenRenderbuffers ( GLsizei n, GLuint *renderbuffers )
    public static void glGenRenderbuffers(
            int n,
            java.nio.IntBuffer renderbuffers
    ) {android.opengl.GLES20.glGenRenderbuffers(n, renderbuffers);}
    // C function void glGenTextures ( GLsizei n, GLuint *textures )
    public static void glGenTextures(
            int n,
            int[] textures,
            int offset
    ) {android.opengl.GLES20.glGenTextures(n, textures, offset);}
    // C function void glGenTextures ( GLsizei n, GLuint *textures )
    public static void glGenTextures(
            int n,
            java.nio.IntBuffer textures
    ) {android.opengl.GLES20.glGenTextures(n, textures);}
    // C function void glGetActiveAttrib ( GLuint program, GLuint index, GLsizei bufsize, GLsizei *length, GLint *size, GLenum *type, char *name )
    public static void glGetActiveAttrib(
            int program,
            int index,
            int bufsize,
            int[] length,
            int lengthOffset,
            int[] size,
            int sizeOffset,
            int[] type,
            int typeOffset,
            byte[] name,
            int nameOffset
    ) {android.opengl.GLES20.glGetActiveAttrib(program, index, bufsize, length, lengthOffset, size, sizeOffset, type, typeOffset, name, nameOffset);}
    // C function void glGetActiveAttrib ( GLuint program, GLuint index, GLsizei bufsize, GLsizei *length, GLint *size, GLenum *type, char *name )
    public static String glGetActiveAttrib(
            int program,
            int index,
            int[] size,
            int sizeOffset,
            int[] type,
            int typeOffset
     ) {return android.opengl.GLES20.glGetActiveAttrib(program, index, size, sizeOffset, type, typeOffset);}
    // C function void glGetActiveAttrib ( GLuint program, GLuint index, GLsizei bufsize, GLsizei *length, GLint *size, GLenum *type, char *name )
    public static String glGetActiveAttrib(
            int program,
            int index,
            java.nio.IntBuffer size,
            java.nio.IntBuffer type
    ) {return android.opengl.GLES20.glGetActiveAttrib(program, index, size, type);}
    // C function void glGetActiveUniform ( GLuint program, GLuint index, GLsizei bufsize, GLsizei *length, GLint *size, GLenum *type, char *name )
    public static void glGetActiveUniform(
            int program,
            int index,
            int bufsize,
            int[] length,
            int lengthOffset,
            int[] size,
            int sizeOffset,
            int[] type,
            int typeOffset,
            byte[] name,
            int nameOffset
    ) {android.opengl.GLES20.glGetActiveUniform(program, index, bufsize, length, lengthOffset, size, sizeOffset, type, typeOffset, name, nameOffset);}
    // C function void glGetActiveUniform ( GLuint program, GLuint index, GLsizei bufsize, GLsizei *length, GLint *size, GLenum *type, char *name )
    public static String glGetActiveUniform(
            int program,
            int index,
            int[] size,
            int sizeOffset,
            int[] type,
            int typeOffset
    ) {return android.opengl.GLES20.glGetActiveUniform(program, index, size, sizeOffset, type, typeOffset);}
    // C function void glGetActiveUniform ( GLuint program, GLuint index, GLsizei bufsize, GLsizei *length, GLint *size, GLenum *type, char *name )
    public static String glGetActiveUniform(
            int program,
            int index,
            java.nio.IntBuffer size,
            java.nio.IntBuffer type
    ) {return android.opengl.GLES20.glGetActiveUniform(program, index, size, type);}
    // C function void glGetAttachedShaders ( GLuint program, GLsizei maxcount, GLsizei *count, GLuint *shaders )
    public static void glGetAttachedShaders(
            int program,
            int maxcount,
            int[] count,
            int countOffset,
            int[] shaders,
            int shadersOffset
    ) {android.opengl.GLES20.glGetAttachedShaders(program, maxcount, count, countOffset, shaders, shadersOffset);}
    // C function void glGetAttachedShaders ( GLuint program, GLsizei maxcount, GLsizei *count, GLuint *shaders )
    public static void glGetAttachedShaders(
            int program,
            int maxcount,
            java.nio.IntBuffer count,
            java.nio.IntBuffer shaders
    ) {android.opengl.GLES20.glGetAttachedShaders(program, maxcount, count, shaders);}
    // C function GLint glGetAttribLocation ( GLuint program, const char *name )
    public static int glGetAttribLocation(
            int program,
            String name
    ) {return android.opengl.GLES20.glGetAttribLocation(program, name);}
    // C function void glGetBooleanv ( GLenum pname, GLboolean *params )
    public static void glGetBooleanv(
            int pname,
            boolean[] params,
            int offset
    ) {android.opengl.GLES20.glGetBooleanv(pname, params, offset);}
    // C function void glGetBooleanv ( GLenum pname, GLboolean *params )
    public static void glGetBooleanv(
            int pname,
            java.nio.IntBuffer params
    ) {android.opengl.GLES20.glGetBooleanv(pname, params);}
    // C function void glGetBufferParameteriv ( GLenum target, GLenum pname, GLint *params )
    public static void glGetBufferParameteriv(
            int target,
            int pname,
            int[] params,
            int offset
    ) {android.opengl.GLES20.glGetBufferParameteriv(target, pname, params, offset);}
    // C function void glGetBufferParameteriv ( GLenum target, GLenum pname, GLint *params )
    public static void glGetBufferParameteriv(
            int target,
            int pname,
            java.nio.IntBuffer params
    ) {android.opengl.GLES20.glGetBufferParameteriv(target, pname, params);}
    // C function GLenum glGetError ( void )
    public static int glGetError(
    ) {return android.opengl.GLES20.glGetError();}
    // C function void glGetFloatv ( GLenum pname, GLfloat *params )
    public static void glGetFloatv(
            int pname,
            float[] params,
            int offset
    ) {android.opengl.GLES20.glGetFloatv(pname, params, offset);}
    // C function void glGetFloatv ( GLenum pname, GLfloat *params )
    public static void glGetFloatv(
            int pname,
            java.nio.FloatBuffer params
    ) {android.opengl.GLES20.glGetFloatv(pname, params);}
    // C function void glGetFramebufferAttachmentParameteriv ( GLenum target, GLenum attachment, GLenum pname, GLint *params )
    public static void glGetFramebufferAttachmentParameteriv(
            int target,
            int attachment,
            int pname,
            int[] params,
            int offset
    ) {android.opengl.GLES20.glGetFramebufferAttachmentParameteriv(target, attachment, pname, params, offset);}
    // C function void glGetFramebufferAttachmentParameteriv ( GLenum target, GLenum attachment, GLenum pname, GLint *params )
    public static void glGetFramebufferAttachmentParameteriv(
            int target,
            int attachment,
            int pname,
            java.nio.IntBuffer params
    ) {android.opengl.GLES20.glGetFramebufferAttachmentParameteriv(target, attachment, pname, params);}
    // C function void glGetIntegerv ( GLenum pname, GLint *params )
    public static void glGetIntegerv(
            int pname,
            int[] params,
            int offset
    ) {android.opengl.GLES20.glGetIntegerv(pname, params, offset);}
    // C function void glGetIntegerv ( GLenum pname, GLint *params )
    public static void glGetIntegerv(
            int pname,
            java.nio.IntBuffer params
    ) {android.opengl.GLES20.glGetIntegerv(pname, params);}
    // C function void glGetProgramiv ( GLuint program, GLenum pname, GLint *params )
    public static void glGetProgramiv(
            int program,
            int pname,
            int[] params,
            int offset
    ) {android.opengl.GLES20.glGetProgramiv(program, pname, params, offset);}
    // C function void glGetProgramiv ( GLuint program, GLenum pname, GLint *params )
    public static void glGetProgramiv(
            int program,
            int pname,
            java.nio.IntBuffer params
    ) {android.opengl.GLES20.glGetProgramiv(program, pname, params);}
    // C function void glGetProgramInfoLog( GLuint program, GLsizei maxLength, GLsizei * length,
    //     GLchar * infoLog);
    public static String glGetProgramInfoLog(
            int program
    ) {return android.opengl.GLES20.glGetProgramInfoLog(program);}
    // C function void glGetRenderbufferParameteriv ( GLenum target, GLenum pname, GLint *params )
    public static void glGetRenderbufferParameteriv(
            int target,
            int pname,
            int[] params,
            int offset
    ) {android.opengl.GLES20.glGetRenderbufferParameteriv(target, pname, params, offset);}
    // C function void glGetRenderbufferParameteriv ( GLenum target, GLenum pname, GLint *params )
    public static void glGetRenderbufferParameteriv(
            int target,
            int pname,
            java.nio.IntBuffer params
    ) {android.opengl.GLES20.glGetRenderbufferParameteriv(target, pname, params);}
    // C function void glGetShaderiv ( GLuint shader, GLenum pname, GLint *params )
    public static void glGetShaderiv(
            int shader,
            int pname,
            int[] params,
            int offset
    ) {android.opengl.GLES20.glGetShaderiv(shader, pname, params, offset);}
    // C function void glGetShaderiv ( GLuint shader, GLenum pname, GLint *params )
    public static void glGetShaderiv(
            int shader,
            int pname,
            java.nio.IntBuffer params
    ) {android.opengl.GLES20.glGetShaderiv(shader, pname, params);}
    // C function void glGetShaderInfoLog( GLuint shader, GLsizei maxLength, GLsizei * length,
    //     GLchar * infoLog);
    public static String glGetShaderInfoLog(
            int shader
    ) {return android.opengl.GLES20.glGetShaderInfoLog(shader);}
    // C function void glGetShaderPrecisionFormat ( GLenum shadertype, GLenum precisiontype, GLint *range, GLint *precision )
    public static void glGetShaderPrecisionFormat(
            int shadertype,
            int precisiontype,
            int[] range,
            int rangeOffset,
            int[] precision,
            int precisionOffset
    ) {android.opengl.GLES20.glGetShaderPrecisionFormat(shadertype, precisiontype, range, rangeOffset, precision, precisionOffset);}
    // C function void glGetShaderPrecisionFormat ( GLenum shadertype, GLenum precisiontype, GLint *range, GLint *precision )
    public static void glGetShaderPrecisionFormat(
            int shadertype,
            int precisiontype,
            java.nio.IntBuffer range,
            java.nio.IntBuffer precision
    ) {android.opengl.GLES20.glGetShaderPrecisionFormat(shadertype, precisiontype, range, precision);}
    // C function void glGetShaderSource ( GLuint shader, GLsizei bufsize, GLsizei *length, char *source )
    public static void glGetShaderSource(
            int shader,
            int bufsize,
            int[] length,
            int lengthOffset,
            byte[] source,
            int sourceOffset
    ) {android.opengl.GLES20.glGetShaderSource(shader, bufsize, length, lengthOffset, source, sourceOffset);}
    // C function void glGetShaderSource ( GLuint shader, GLsizei bufsize, GLsizei *length, char *source )
    public static String glGetShaderSource(
            int shader
    ) {return android.opengl.GLES20.glGetShaderSource(shader);}
    // C function const GLubyte * glGetString ( GLenum name )
    public static String glGetString(
            int name
    ) {return android.opengl.GLES20.glGetString(name);}
    // C function void glGetTexParameterfv ( GLenum target, GLenum pname, GLfloat *params )
    public static void glGetTexParameterfv(
            int target,
            int pname,
            float[] params,
            int offset
    ) {android.opengl.GLES20.glGetTexParameterfv(target, pname, params, offset);}
    // C function void glGetTexParameterfv ( GLenum target, GLenum pname, GLfloat *params )
    public static void glGetTexParameterfv(
            int target,
            int pname,
            java.nio.FloatBuffer params
    ) {android.opengl.GLES20.glGetTexParameterfv(target, pname, params);}
    // C function void glGetTexParameteriv ( GLenum target, GLenum pname, GLint *params )
    public static void glGetTexParameteriv(
            int target,
            int pname,
            int[] params,
            int offset
    ) {android.opengl.GLES20.glGetTexParameteriv(target, pname, params, offset);}
    // C function void glGetTexParameteriv ( GLenum target, GLenum pname, GLint *params )
    public static void glGetTexParameteriv(
            int target,
            int pname,
            java.nio.IntBuffer params
    ) {android.opengl.GLES20.glGetTexParameteriv(target, pname, params);}
    // C function void glGetUniformfv ( GLuint program, GLint location, GLfloat *params )
    public static void glGetUniformfv(
            int program,
            int location,
            float[] params,
            int offset
    ) {android.opengl.GLES20.glGetUniformfv(program, location, params, offset);}
    // C function void glGetUniformfv ( GLuint program, GLint location, GLfloat *params )
    public static void glGetUniformfv(
            int program,
            int location,
            java.nio.FloatBuffer params
    ) {android.opengl.GLES20.glGetUniformfv(program, location, params);}
    // C function void glGetUniformiv ( GLuint program, GLint location, GLint *params )
    public static void glGetUniformiv(
            int program,
            int location,
            int[] params,
            int offset
    ) {android.opengl.GLES20.glGetUniformiv(program, location, params, offset);}
    // C function void glGetUniformiv ( GLuint program, GLint location, GLint *params )
    public static void glGetUniformiv(
            int program,
            int location,
            java.nio.IntBuffer params
    ) {android.opengl.GLES20.glGetUniformiv(program, location, params);}
    // C function GLint glGetUniformLocation ( GLuint program, const char *name )
    public static int glGetUniformLocation(
            int program,
            String name
    ) {return android.opengl.GLES20.glGetUniformLocation(program, name);}
    // C function void glGetVertexAttribfv ( GLuint index, GLenum pname, GLfloat *params )
    public static void glGetVertexAttribfv(
            int index,
            int pname,
            float[] params,
            int offset
    ) {android.opengl.GLES20.glGetVertexAttribfv(index, pname, params, offset);}
    // C function void glGetVertexAttribfv ( GLuint index, GLenum pname, GLfloat *params )
    public static void glGetVertexAttribfv(
            int index,
            int pname,
            java.nio.FloatBuffer params
    ) {android.opengl.GLES20.glGetVertexAttribfv(index, pname, params);}
    // C function void glGetVertexAttribiv ( GLuint index, GLenum pname, GLint *params )
    public static void glGetVertexAttribiv(
            int index,
            int pname,
            int[] params,
            int offset
    ) {android.opengl.GLES20.glGetVertexAttribiv(index, pname, params, offset);}
    // C function void glGetVertexAttribiv ( GLuint index, GLenum pname, GLint *params )
    public static void glGetVertexAttribiv(
            int index,
            int pname,
            java.nio.IntBuffer params
    ) {android.opengl.GLES20.glGetVertexAttribiv(index, pname, params);}
    // C function void glHint ( GLenum target, GLenum mode )
    public static void glHint(
            int target,
            int mode
    ) {android.opengl.GLES20.glHint(target, mode);}
    // C function GLboolean glIsBuffer ( GLuint buffer )
    public static boolean glIsBuffer(
            int buffer
    ) {return android.opengl.GLES20.glIsBuffer(buffer);}
    // C function GLboolean glIsEnabled ( GLenum cap )
    public static boolean glIsEnabled(
            int cap
    ) {return android.opengl.GLES20.glIsEnabled(cap);}
    // C function GLboolean glIsFramebuffer ( GLuint framebuffer )
    public static boolean glIsFramebuffer(
            int framebuffer
    ) {return android.opengl.GLES20.glIsFramebuffer(framebuffer);}
    // C function GLboolean glIsProgram ( GLuint program )
    public static boolean glIsProgram(
            int program
    ) {return android.opengl.GLES20.glIsProgram(program);}
    // C function GLboolean glIsRenderbuffer ( GLuint renderbuffer )
    public static boolean glIsRenderbuffer(
            int renderbuffer
    ) {return android.opengl.GLES20.glIsRenderbuffer(renderbuffer);}
    // C function GLboolean glIsShader ( GLuint shader )
    public static boolean glIsShader(
            int shader
    ) {return android.opengl.GLES20.glIsShader(shader);}
    // C function GLboolean glIsTexture ( GLuint texture )
    public static boolean glIsTexture(
            int texture
    ) {return android.opengl.GLES20.glIsTexture(texture);}
    // C function void glLineWidth ( GLfloat width )
    public static void glLineWidth(
            float width
    ) {android.opengl.GLES20.glLineWidth(width);}
    // C function void glLinkProgram ( GLuint program )
    public static void glLinkProgram(
            int program
    ) {android.opengl.GLES20.glLinkProgram(program);}
    // C function void glPixelStorei ( GLenum pname, GLint param )
    public static void glPixelStorei(
            int pname,
            int param
    ) {android.opengl.GLES20.glPixelStorei(pname, param);}
    // C function void glPolygonOffset ( GLfloat factor, GLfloat units )
    public static void glPolygonOffset(
            float factor,
            float units
    ) {android.opengl.GLES20.glPolygonOffset(factor, units);}
    // C function void glReadPixels ( GLint x, GLint y, GLsizei width, GLsizei height, GLenum format, GLenum type, GLvoid *pixels )
    public static void glReadPixels(
            int x,
            int y,
            int width,
            int height,
            int format,
            int type,
            java.nio.Buffer pixels
    ) {android.opengl.GLES20.glReadPixels(x, y, width, height, format, type, pixels);}
    // C function void glReleaseShaderCompiler ( void )
    public static void glReleaseShaderCompiler(
    ) {android.opengl.GLES20.glReleaseShaderCompiler();}
    // C function void glRenderbufferStorage ( GLenum target, GLenum internalformat, GLsizei width, GLsizei height )
    public static void glRenderbufferStorage(
            int target,
            int internalformat,
            int width,
            int height
    ) {android.opengl.GLES20.glRenderbufferStorage(target, internalformat, width, height);}
    // C function void glSampleCoverage ( GLclampf value, GLboolean invert )
    public static void glSampleCoverage(
            float value,
            boolean invert
    ) {android.opengl.GLES20.glSampleCoverage(value, invert);}
    // C function void glScissor ( GLint x, GLint y, GLsizei width, GLsizei height )
    public static void glScissor(
            int x,
            int y,
            int width,
            int height
    ) {android.opengl.GLES20.glScissor(x, y, width, height);}
    // C function void glShaderBinary ( GLsizei n, const GLuint *shaders, GLenum binaryformat, const GLvoid *binary, GLsizei length )
    public static void glShaderBinary(
            int n,
            int[] shaders,
            int offset,
            int binaryformat,
            java.nio.Buffer binary,
            int length
    ) {android.opengl.GLES20.glShaderBinary(n, shaders, offset, binaryformat, binary, length);}
    // C function void glShaderBinary ( GLsizei n, const GLuint *shaders, GLenum binaryformat, const GLvoid *binary, GLsizei length )
    public static void glShaderBinary(
            int n,
            java.nio.IntBuffer shaders,
            int binaryformat,
            java.nio.Buffer binary,
            int length
    ) {android.opengl.GLES20.glShaderBinary(n, shaders, binaryformat, binary, length);}
    // C function void glShaderSource ( GLuint shader, GLsizei count, const GLchar ** string, const GLint* length )
    public static void glShaderSource(
            int shader,
            String string
    ) {android.opengl.GLES20.glShaderSource(shader, string);}
    // C function void glStencilFunc ( GLenum func, GLint ref, GLuint mask )
    public static void glStencilFunc(
            int func,
            int ref,
            int mask
    ) {android.opengl.GLES20.glStencilFunc(func, ref, mask);}
    // C function void glStencilFuncSeparate ( GLenum face, GLenum func, GLint ref, GLuint mask )
    public static void glStencilFuncSeparate(
            int face,
            int func,
            int ref,
            int mask
    ) {android.opengl.GLES20.glStencilFuncSeparate(face, func, ref, mask);}
    // C function void glStencilMask ( GLuint mask )
    public static void glStencilMask(
            int mask
    ) {android.opengl.GLES20.glStencilMask(mask);}
    // C function void glStencilMaskSeparate ( GLenum face, GLuint mask )
    public static void glStencilMaskSeparate(
            int face,
            int mask
    ) {android.opengl.GLES20.glStencilMaskSeparate(face, mask);}
    // C function void glStencilOp ( GLenum fail, GLenum zfail, GLenum zpass )
    public static void glStencilOp(
            int fail,
            int zfail,
            int zpass
    ) {android.opengl.GLES20.glStencilOp(fail, zfail, zpass);}
    // C function void glStencilOpSeparate ( GLenum face, GLenum fail, GLenum zfail, GLenum zpass )
    public static void glStencilOpSeparate(
            int face,
            int fail,
            int zfail,
            int zpass
    ) {android.opengl.GLES20.glStencilOpSeparate(face, fail, zfail, zpass);}
    // C function void glTexImage2D ( GLenum target, GLint level, GLint internalformat, GLsizei width, GLsizei height, GLint border, GLenum format, GLenum type, const GLvoid *pixels )
    public static void glTexImage2D(
            int target,
            int level,
            int internalformat,
            int width,
            int height,
            int border,
            int format,
            int type,
            java.nio.Buffer pixels
    ) {android.opengl.GLES20.glTexImage2D(target, level, internalformat, width, height, border, format, type, pixels);}
    // C function void glTexParameterf ( GLenum target, GLenum pname, GLfloat param )
    public static void glTexParameterf(
            int target,
            int pname,
            float param
    ) {android.opengl.GLES20.glTexParameterf(target, pname, param);}
    // C function void glTexParameterfv ( GLenum target, GLenum pname, const GLfloat *params )
    public static void glTexParameterfv(
            int target,
            int pname,
            float[] params,
            int offset
    ) {android.opengl.GLES20.glTexParameterfv(target, pname, params, offset);}
    // C function void glTexParameterfv ( GLenum target, GLenum pname, const GLfloat *params )
    public static void glTexParameterfv(
            int target,
            int pname,
            java.nio.FloatBuffer params
    ) {android.opengl.GLES20.glTexParameterfv(target, pname, params);}
    // C function void glTexParameteri ( GLenum target, GLenum pname, GLint param )
    public static void glTexParameteri(
            int target,
            int pname,
            int param
    ) {android.opengl.GLES20.glTexParameteri(target, pname, param);}
    // C function void glTexParameteriv ( GLenum target, GLenum pname, const GLint *params )
    public static void glTexParameteriv(
            int target,
            int pname,
            int[] params,
            int offset
    ) {android.opengl.GLES20.glTexParameteriv(target, pname, params, offset);}
    // C function void glTexParameteriv ( GLenum target, GLenum pname, const GLint *params )
    public static void glTexParameteriv(
            int target,
            int pname,
            java.nio.IntBuffer params
    ) {android.opengl.GLES20.glTexParameteriv(target, pname, params);}
    // C function void glTexSubImage2D ( GLenum target, GLint level, GLint xoffset, GLint yoffset, GLsizei width, GLsizei height, GLenum format, GLenum type, const GLvoid *pixels )
    public static void glTexSubImage2D(
            int target,
            int level,
            int xoffset,
            int yoffset,
            int width,
            int height,
            int format,
            int type,
            java.nio.Buffer pixels
    ) {android.opengl.GLES20.glTexSubImage2D(target, level, xoffset, yoffset, width, height, format, type, pixels);}
    // C function void glUniform1f ( GLint location, GLfloat x )
    public static void glUniform1f(
            int location,
            float x
    ) {android.opengl.GLES20.glUniform1f(location, x);}
    // C function void glUniform1fv ( GLint location, GLsizei count, const GLfloat *v )
    public static void glUniform1fv(
            int location,
            int count,
            float[] v,
            int offset
    ) {android.opengl.GLES20.glUniform1fv(location, count, v, offset);}
    // C function void glUniform1fv ( GLint location, GLsizei count, const GLfloat *v )
    public static void glUniform1fv(
            int location,
            int count,
            java.nio.FloatBuffer v
    ) {android.opengl.GLES20.glUniform1fv(location, count, v);}
    // C function void glUniform1i ( GLint location, GLint x )
    public static void glUniform1i(
            int location,
            int x
    ) {android.opengl.GLES20.glUniform1i(location, x);}
    // C function void glUniform1iv ( GLint location, GLsizei count, const GLint *v )
    public static void glUniform1iv(
            int location,
            int count,
            int[] v,
            int offset
    ) {android.opengl.GLES20.glUniform1iv(location, count, v, offset);}
    // C function void glUniform1iv ( GLint location, GLsizei count, const GLint *v )
    public static void glUniform1iv(
            int location,
            int count,
            java.nio.IntBuffer v
    ) {android.opengl.GLES20.glUniform1iv(location, count, v);}
    // C function void glUniform2f ( GLint location, GLfloat x, GLfloat y )
    public static void glUniform2f(
            int location,
            float x,
            float y
    ) {android.opengl.GLES20.glUniform2f(location, x, y);}
    // C function void glUniform2fv ( GLint location, GLsizei count, const GLfloat *v )
    public static void glUniform2fv(
            int location,
            int count,
            float[] v,
            int offset
    ) {android.opengl.GLES20.glUniform2fv(location, count, v, offset);}
    // C function void glUniform2fv ( GLint location, GLsizei count, const GLfloat *v )
    public static void glUniform2fv(
            int location,
            int count,
            java.nio.FloatBuffer v
    ) {android.opengl.GLES20.glUniform2fv(location, count, v);}
    // C function void glUniform2i ( GLint location, GLint x, GLint y )
    public static void glUniform2i(
            int location,
            int x,
            int y
    ) {android.opengl.GLES20.glUniform2i(location, x, y);}
    // C function void glUniform2iv ( GLint location, GLsizei count, const GLint *v )
    public static void glUniform2iv(
            int location,
            int count,
            int[] v,
            int offset
    ) {android.opengl.GLES20.glUniform2iv(location, count, v, offset);}
    // C function void glUniform2iv ( GLint location, GLsizei count, const GLint *v )
    public static void glUniform2iv(
            int location,
            int count,
            java.nio.IntBuffer v
    ) {android.opengl.GLES20.glUniform2iv(location, count, v);}
    // C function void glUniform3f ( GLint location, GLfloat x, GLfloat y, GLfloat z )
    public static void glUniform3f(
            int location,
            float x,
            float y,
            float z
    ) {android.opengl.GLES20.glUniform3f(location, x, y, z);}
    // C function void glUniform3fv ( GLint location, GLsizei count, const GLfloat *v )
    public static void glUniform3fv(
            int location,
            int count,
            float[] v,
            int offset
    ) {android.opengl.GLES20.glUniform3fv(location, count, v, offset);}
    // C function void glUniform3fv ( GLint location, GLsizei count, const GLfloat *v )
    public static void glUniform3fv(
            int location,
            int count,
            java.nio.FloatBuffer v
    ) {android.opengl.GLES20.glUniform3fv(location, count, v);}
    // C function void glUniform3i ( GLint location, GLint x, GLint y, GLint z )
    public static void glUniform3i(
            int location,
            int x,
            int y,
            int z
    ) {android.opengl.GLES20.glUniform3i(location, x, y, z);}
    // C function void glUniform3iv ( GLint location, GLsizei count, const GLint *v )
    public static void glUniform3iv(
            int location,
            int count,
            int[] v,
            int offset
    ) {android.opengl.GLES20.glUniform3iv(location, count, v, offset);}
    // C function void glUniform3iv ( GLint location, GLsizei count, const GLint *v )
    public static void glUniform3iv(
            int location,
            int count,
            java.nio.IntBuffer v
    ) {android.opengl.GLES20.glUniform3iv(location, count, v);}
    // C function void glUniform4f ( GLint location, GLfloat x, GLfloat y, GLfloat z, GLfloat w )
    public static void glUniform4f(
            int location,
            float x,
            float y,
            float z,
            float w
    ) {android.opengl.GLES20.glUniform4f(location, x, y, z, w);}
    // C function void glUniform4fv ( GLint location, GLsizei count, const GLfloat *v )
    public static void glUniform4fv(
            int location,
            int count,
            float[] v,
            int offset
    ) {android.opengl.GLES20.glUniform4fv(location, count, v, offset);}
    // C function void glUniform4fv ( GLint location, GLsizei count, const GLfloat *v )
    public static void glUniform4fv(
            int location,
            int count,
            java.nio.FloatBuffer v
    ) {android.opengl.GLES20.glUniform4fv(location, count, v);}
    // C function void glUniform4i ( GLint location, GLint x, GLint y, GLint z, GLint w )
    public static void glUniform4i(
            int location,
            int x,
            int y,
            int z,
            int w
    ) {android.opengl.GLES20.glUniform4i(location, x, y, z, w);}
    // C function void glUniform4iv ( GLint location, GLsizei count, const GLint *v )
    public static void glUniform4iv(
            int location,
            int count,
            int[] v,
            int offset
    ) {android.opengl.GLES20.glUniform4iv(location, count, v, offset);}
    // C function void glUniform4iv ( GLint location, GLsizei count, const GLint *v )
    public static void glUniform4iv(
            int location,
            int count,
            java.nio.IntBuffer v
    ) {android.opengl.GLES20.glUniform4iv(location, count, v);}
    // C function void glUniformMatrix2fv ( GLint location, GLsizei count, GLboolean transpose, const GLfloat *value )
    public static void glUniformMatrix2fv(
            int location,
            int count,
            boolean transpose,
            float[] value,
            int offset
    ) {android.opengl.GLES20.glUniformMatrix2fv(location, count, transpose, value, offset);}
    // C function void glUniformMatrix2fv ( GLint location, GLsizei count, GLboolean transpose, const GLfloat *value )
    public static void glUniformMatrix2fv(
            int location,
            int count,
            boolean transpose,
            java.nio.FloatBuffer value
    ) {android.opengl.GLES20.glUniformMatrix2fv(location, count, transpose, value);}
    // C function void glUniformMatrix3fv ( GLint location, GLsizei count, GLboolean transpose, const GLfloat *value )
    public static void glUniformMatrix3fv(
            int location,
            int count,
            boolean transpose,
            float[] value,
            int offset
    ) {android.opengl.GLES20.glUniformMatrix3fv(location, count, transpose, value, offset);}
    // C function void glUniformMatrix3fv ( GLint location, GLsizei count, GLboolean transpose, const GLfloat *value )
    public static void glUniformMatrix3fv(
            int location,
            int count,
            boolean transpose,
            java.nio.FloatBuffer value
    ) {android.opengl.GLES20.glUniformMatrix3fv(location, count, transpose, value);}
    // C function void glUniformMatrix4fv ( GLint location, GLsizei count, GLboolean transpose, const GLfloat *value )
    public static void glUniformMatrix4fv(
            int location,
            int count,
            boolean transpose,
            float[] value,
            int offset
    ) {android.opengl.GLES20.glUniformMatrix4fv(location, count, transpose, value, offset);}
    // C function void glUniformMatrix4fv ( GLint location, GLsizei count, GLboolean transpose, const GLfloat *value )
    public static void glUniformMatrix4fv(
            int location,
            int count,
            boolean transpose,
            java.nio.FloatBuffer value
    ) {android.opengl.GLES20.glUniformMatrix4fv(location, count, transpose, value);}
    // C function void glUseProgram ( GLuint program )
    public static void glUseProgram(
            int program
    ) {android.opengl.GLES20.glUseProgram(program);}
    // C function void glValidateProgram ( GLuint program )
    public static void glValidateProgram(
            int program
    ) {android.opengl.GLES20.glValidateProgram(program);}
    // C function void glVertexAttrib1f ( GLuint indx, GLfloat x )
    public static void glVertexAttrib1f(
            int indx,
            float x
    ) {android.opengl.GLES20.glVertexAttrib1f(indx, x);}
    // C function void glVertexAttrib1fv ( GLuint indx, const GLfloat *values )
    public static void glVertexAttrib1fv(
            int indx,
            float[] values,
            int offset
    ) {android.opengl.GLES20.glVertexAttrib1fv(indx, values, offset);}
    // C function void glVertexAttrib1fv ( GLuint indx, const GLfloat *values )
    public static void glVertexAttrib1fv(
            int indx,
            java.nio.FloatBuffer values
    ) {android.opengl.GLES20.glVertexAttrib1fv(indx, values);}
    // C function void glVertexAttrib2f ( GLuint indx, GLfloat x, GLfloat y )
    public static void glVertexAttrib2f(
            int indx,
            float x,
            float y
    ) {android.opengl.GLES20.glVertexAttrib2f(indx, x, y);}
    // C function void glVertexAttrib2fv ( GLuint indx, const GLfloat *values )
    public static void glVertexAttrib2fv(
            int indx,
            float[] values,
            int offset
    ) {android.opengl.GLES20.glVertexAttrib2fv(indx, values, offset);}
    // C function void glVertexAttrib2fv ( GLuint indx, const GLfloat *values )
    public static void glVertexAttrib2fv(
            int indx,
            java.nio.FloatBuffer values
    ) {android.opengl.GLES20.glVertexAttrib2fv(indx, values);}
    // C function void glVertexAttrib3f ( GLuint indx, GLfloat x, GLfloat y, GLfloat z )
    public static void glVertexAttrib3f(
            int indx,
            float x,
            float y,
            float z
    ) {android.opengl.GLES20.glVertexAttrib3f(indx, x, y, z);}
    // C function void glVertexAttrib3fv ( GLuint indx, const GLfloat *values )
    public static void glVertexAttrib3fv(
            int indx,
            float[] values,
            int offset
    ) {android.opengl.GLES20.glVertexAttrib3fv(indx, values, offset);}
    // C function void glVertexAttrib3fv ( GLuint indx, const GLfloat *values )
    public static void glVertexAttrib3fv(
            int indx,
            java.nio.FloatBuffer values
    ) {android.opengl.GLES20.glVertexAttrib3fv(indx, values);}
    // C function void glVertexAttrib4f ( GLuint indx, GLfloat x, GLfloat y, GLfloat z, GLfloat w )
    public static void glVertexAttrib4f(
            int indx,
            float x,
            float y,
            float z,
            float w
    ) {android.opengl.GLES20.glVertexAttrib4f(indx, x, y, z, w);}
    // C function void glVertexAttrib4fv ( GLuint indx, const GLfloat *values )
    public static void glVertexAttrib4fv(
            int indx,
            float[] values,
            int offset
    ) {android.opengl.GLES20.glVertexAttrib4fv(indx, values, offset);}
    // C function void glVertexAttrib4fv ( GLuint indx, const GLfloat *values )
    public static void glVertexAttrib4fv(
            int indx,
            java.nio.FloatBuffer values
    ) {android.opengl.GLES20.glVertexAttrib4fv(indx, values);}
    // C function void glVertexAttribPointer ( GLuint indx, GLint size, GLenum type, GLboolean normalized, GLsizei stride, GLint offset )
    public static void glVertexAttribPointer(
            int indx,
            int size,
            int type,
            boolean normalized,
            int stride,
            int offset
    ) { android.opengl.GLES20.glVertexAttribPointer(indx, size, type, normalized, stride, offset); }
    // C function void glVertexAttribPointer ( GLuint indx, GLint size, GLenum type, GLboolean normalized, GLsizei stride, const GLvoid *ptr )
    public static void glVertexAttribPointer(
            int indx,
            int size,
            int type,
            boolean normalized,
            int stride,
            java.nio.Buffer ptr
    ) { android.opengl.GLES20.glVertexAttribPointer(indx, size, type, normalized, stride, ptr); }
    // C function void glViewport ( GLint x, GLint y, GLsizei width, GLsizei height )
    public static void glViewport(
            int x,
            int y,
            int width,
            int height
    ) {android.opengl.GLES20.glViewport(x, y, width, height);}
}
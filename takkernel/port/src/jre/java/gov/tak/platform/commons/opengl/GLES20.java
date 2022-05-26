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

import javax.media.opengl.GL2;

/** OpenGL ES 2.0
 */
public class GLES20 implements GLES20Constants {
    final static ThreadLocal<VertexAttribArrays> vertexAttribArrays = new ThreadLocal<VertexAttribArrays>() {
        @Override
        protected VertexAttribArrays initialValue() {
            return new VertexAttribArrays();
        }
    };

    // C function void glActiveTexture ( GLenum texture )
    public static void glActiveTexture(
            int texture
    ) { JOGLGLES.gl2().glActiveTexture(texture); }
    // C function void glAttachShader ( GLuint program, GLuint shader )
    public static void glAttachShader(
            int program,
            int shader
    ) { JOGLGLES.gl2().glAttachShader(program, shader); }
    // C function void glBindAttribLocation ( GLuint program, GLuint index, const char *name )
    public static void glBindAttribLocation(
            int program,
            int index,
            String name
    ) { JOGLGLES.gl2().glBindAttribLocation(program, index, name); }
    // C function void glBindBuffer ( GLenum target, GLuint buffer )
    public static void glBindBuffer(
            int target,
            int buffer
    ) { JOGLGLES.gl2().glBindBuffer(target, buffer); }
    // C function void glBindFramebuffer ( GLenum target, GLuint framebuffer )
    public static void glBindFramebuffer(
            int target,
            int framebuffer
    ) { JOGLGLES.gl2().glBindFramebuffer(target, framebuffer);}
    // C function void glBindRenderbuffer ( GLenum target, GLuint renderbuffer )
    public static void glBindRenderbuffer(
            int target,
            int renderbuffer
    ) {JOGLGLES.gl2().glBindRenderbuffer(target, renderbuffer);}
    // C function void glBindTexture ( GLenum target, GLuint texture )
    public static void glBindTexture(
            int target,
            int texture
    ) {JOGLGLES.gl2().glBindTexture(target, texture);}
    // C function void glBlendColor ( GLclampf red, GLclampf green, GLclampf blue, GLclampf alpha )
    public static void glBlendColor(
            float red,
            float green,
            float blue,
            float alpha
    ) {JOGLGLES.gl2().glBlendColor(red, green, blue, alpha);}
    // C function void glBlendEquation ( GLenum mode )
    public static void glBlendEquation(
            int mode
    ) {JOGLGLES.gl2().glBlendEquation(mode);}
    // C function void glBlendEquationSeparate ( GLenum modeRGB, GLenum modeAlpha )
    public static void glBlendEquationSeparate(
            int modeRGB,
            int modeAlpha
    ) {JOGLGLES.gl2().glBlendEquationSeparate(modeRGB, modeAlpha);}
    // C function void glBlendFunc ( GLenum sfactor, GLenum dfactor )
    public static void glBlendFunc(
            int sfactor,
            int dfactor
    ) {JOGLGLES.gl2().glBlendFunc(sfactor, dfactor);}
    // C function void glBlendFuncSeparate ( GLenum srcRGB, GLenum dstRGB, GLenum srcAlpha, GLenum dstAlpha )
    public static void glBlendFuncSeparate(
            int srcRGB,
            int dstRGB,
            int srcAlpha,
            int dstAlpha
    ) {JOGLGLES.gl2().glBlendFuncSeparate(srcRGB, dstRGB, srcAlpha, dstAlpha);}
    // C function void glBufferData ( GLenum target, GLsizeiptr size, const GLvoid *data, GLenum usage )
    public static void glBufferData(
            int target,
            int size,
            java.nio.Buffer data,
            int usage
    ) {JOGLGLES.gl2().glBufferData(target, size, data, usage);}
    // C function void glBufferSubData ( GLenum target, GLintptr offset, GLsizeiptr size, const GLvoid *data )
    public static void glBufferSubData(
            int target,
            int offset,
            int size,
            java.nio.Buffer data
    ) {JOGLGLES.gl2().glBufferSubData(target, offset, size, data);}
    // C function GLenum glCheckFramebufferStatus ( GLenum target )
    public static int glCheckFramebufferStatus(
            int target
    ) {return JOGLGLES.gl2().glCheckFramebufferStatus(target);}
    // C function void glClear ( GLbitfield mask )
    public static void glClear(
            int mask
    ) {JOGLGLES.gl2().glClear(mask);}
    // C function void glClearColor ( GLclampf red, GLclampf green, GLclampf blue, GLclampf alpha )
    public static void glClearColor(
            float red,
            float green,
            float blue,
            float alpha
    ) {JOGLGLES.gl2().glClearColor(red, green, blue, alpha);}
    // C function void glClearDepthf ( GLclampf depth )
    public static void glClearDepthf(
            float depth
    ) {JOGLGLES.gl2().glClearDepthf(depth);}
    // C function void glClearStencil ( GLint s )
    public static void glClearStencil(
            int s
    ) {JOGLGLES.gl2().glClearStencil(s);}
    // C function void glColorMask ( GLboolean red, GLboolean green, GLboolean blue, GLboolean alpha )
    public static void glColorMask(
            boolean red,
            boolean green,
            boolean blue,
            boolean alpha
    ) {JOGLGLES.gl2().glColorMask(red, green, blue, alpha);}
    // C function void glCompileShader ( GLuint shader )
    public static void glCompileShader(
            int shader
    ) {JOGLGLES.gl2().glCompileShader(shader);}
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
    ) {JOGLGLES.gl2().glCompressedTexImage2D(target, level, internalformat, width, height, border, imageSize, data);}
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
    ) {JOGLGLES.gl2().glCompressedTexSubImage2D(target, level, xoffset, yoffset, width, height, format, imageSize, data);}
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
    ) {JOGLGLES.gl2().glCopyTexImage2D(target, level, internalformat, x, y, width, height, border);}
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
    ) {JOGLGLES.gl2().glCopyTexSubImage2D(target, level, xoffset, yoffset, x, y, width, height);}
    // C function GLuint glCreateProgram ( void )
    public static int glCreateProgram(
    ) {return JOGLGLES.gl2().glCreateProgram();}
    // C function GLuint glCreateShader ( GLenum type )
    public static int glCreateShader(
            int type
    ) {return JOGLGLES.gl2().glCreateShader(type);}
    // C function void glCullFace ( GLenum mode )
    public static void glCullFace(
            int mode
    ) {JOGLGLES.gl2().glCullFace(mode);}
    // C function void glDeleteBuffers ( GLsizei n, const GLuint *buffers )
    public static void glDeleteBuffers(
            int n,
            int[] buffers,
            int offset
    ) {JOGLGLES.gl2().glDeleteBuffers(n, buffers, offset);}
    // C function void glDeleteBuffers ( GLsizei n, const GLuint *buffers )
    public static void glDeleteBuffers(
            int n,
            java.nio.IntBuffer buffers
    ) {JOGLGLES.gl2().glDeleteBuffers(n, buffers);}
    // C function void glDeleteFramebuffers ( GLsizei n, const GLuint *framebuffers )
    public static void glDeleteFramebuffers(
            int n,
            int[] framebuffers,
            int offset
    ) {JOGLGLES.gl2().glDeleteFramebuffers(n, framebuffers, offset);}
    // C function void glDeleteFramebuffers ( GLsizei n, const GLuint *framebuffers )
    public static void glDeleteFramebuffers(
            int n,
            java.nio.IntBuffer framebuffers
    ) {JOGLGLES.gl2().glDeleteFramebuffers(n, framebuffers);}
    // C function void glDeleteProgram ( GLuint program )
    public static void glDeleteProgram(
            int program
    ) {JOGLGLES.gl2().glDeleteProgram(program);}
    // C function void glDeleteRenderbuffers ( GLsizei n, const GLuint *renderbuffers )
    public static void glDeleteRenderbuffers(
            int n,
            int[] renderbuffers,
            int offset
    ) {JOGLGLES.gl2().glDeleteRenderbuffers(n, renderbuffers, offset);}
    // C function void glDeleteRenderbuffers ( GLsizei n, const GLuint *renderbuffers )
    public static void glDeleteRenderbuffers(
            int n,
            java.nio.IntBuffer renderbuffers
    ) {JOGLGLES.gl2().glDeleteRenderbuffers(n, renderbuffers);}
    // C function void glDeleteShader ( GLuint shader )
    public static void glDeleteShader(
            int shader
    ) {JOGLGLES.gl2().glDeleteShader(shader);}
    // C function void glDeleteTextures ( GLsizei n, const GLuint *textures )
    public static void glDeleteTextures(
            int n,
            int[] textures,
            int offset
    ) {JOGLGLES.gl2().glDeleteTextures(n, textures, offset);}
    // C function void glDeleteTextures ( GLsizei n, const GLuint *textures )
    public static void glDeleteTextures(
            int n,
            java.nio.IntBuffer textures
    ) {JOGLGLES.gl2().glDeleteTextures(n, textures);}
    // C function void glDepthFunc ( GLenum func )
    public static void glDepthFunc(
            int func
    ) {JOGLGLES.gl2().glDepthFunc(func);}
    // C function void glDepthMask ( GLboolean flag )
    public static void glDepthMask(
            boolean flag
    ) {JOGLGLES.gl2().glDepthMask(flag);}
    // C function void glDepthRangef ( GLclampf zNear, GLclampf zFar )
    public static void glDepthRangef(
            float zNear,
            float zFar
    ) {JOGLGLES.gl2().glDepthRangef(zNear, zFar);}
    // C function void glDetachShader ( GLuint program, GLuint shader )
    public static void glDetachShader(
            int program,
            int shader
    ) {JOGLGLES.gl2().glDetachShader(program, shader);}
    // C function void glDisable ( GLenum cap )
    public static void glDisable(
            int cap
    ) {JOGLGLES.gl2().glDisable(cap);}
    // C function void glDisableVertexAttribArray ( GLuint index )
    public static void glDisableVertexAttribArray(
            int index
    ) { JOGLGLES.gl2().glDisableVertexAttribArray(index); }
    // C function void glDrawArrays ( GLenum mode, GLint first, GLsizei count )
    public static void glDrawArrays(
            int mode,
            int first,
            int count
    ) { JOGLGLES.gl2().glDrawArrays(mode, first, count); }
    // C function void glDrawElements ( GLenum mode, GLsizei count, GLenum type, GLint offset )
    public static void glDrawElements(
            int mode,
            int count,
            int type,
            int offset
    ) { JOGLGLES.gl2().glDrawElements(mode, count, type, offset); }
    // C function void glDrawElements ( GLenum mode, GLsizei count, GLenum type, const GLvoid *indices )
    public static void glDrawElements(
            int mode,
            int count,
            int type,
            java.nio.Buffer indices
    ) {
        if(JOGLGLES.gl4() == null) {
            JOGLGLES.gl2().glDrawElements(mode, count, type, indices);
        } else {
            int[] ibo = new int[1];
            glGenBuffers(1, ibo, 0);
            glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, ibo[0]);
            glBufferData(GL_ELEMENT_ARRAY_BUFFER, JOGLGLES.limit(indices), indices, GLES20.GL_STREAM_DRAW);
            JOGLGLES.gl2().glDrawElements(mode, count, type, JOGLGLES.offset(indices));
            glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, GL_NONE);
            glDeleteBuffers(1, ibo, 0);
        }
    }
    // C function void glEnable ( GLenum cap )
    public static void glEnable(
            int cap
    ) {JOGLGLES.gl2().glEnable(cap);}
    // C function void glEnableVertexAttribArray ( GLuint index )
    public static void glEnableVertexAttribArray(
            int index
    ) { JOGLGLES.gl2().glEnableVertexAttribArray(index); }
    // C function void glFinish ( void )
    public static void glFinish(
    ) {JOGLGLES.gl2().glFinish();}
    // C function void glFlush ( void )
    public static void glFlush(
    ) {JOGLGLES.gl2().glFlush();}
    // C function void glFramebufferRenderbuffer ( GLenum target, GLenum attachment, GLenum renderbuffertarget, GLuint renderbuffer )
    public static void glFramebufferRenderbuffer(
            int target,
            int attachment,
            int renderbuffertarget,
            int renderbuffer
    ) {JOGLGLES.gl2().glFramebufferRenderbuffer(target, attachment, renderbuffertarget, renderbuffer);}
    // C function void glFramebufferTexture2D ( GLenum target, GLenum attachment, GLenum textarget, GLuint texture, GLint level )
    public static void glFramebufferTexture2D(
            int target,
            int attachment,
            int textarget,
            int texture,
            int level
    ) {JOGLGLES.gl2().glFramebufferTexture2D(target, attachment, textarget, texture, level);}
    // C function void glFrontFace ( GLenum mode )
    public static void glFrontFace(
            int mode
    ) {JOGLGLES.gl2().glFrontFace(mode);}
    // C function void glGenBuffers ( GLsizei n, GLuint *buffers )
    public static void glGenBuffers(
            int n,
            int[] buffers,
            int offset
    ) {JOGLGLES.gl2().glGenBuffers(n, buffers, offset);}
    // C function void glGenBuffers ( GLsizei n, GLuint *buffers )
    public static void glGenBuffers(
            int n,
            java.nio.IntBuffer buffers
    ) {JOGLGLES.gl2().glGenBuffers(n, buffers);}
    // C function void glGenerateMipmap ( GLenum target )
    public static void glGenerateMipmap(
            int target
    ) {JOGLGLES.gl2().glGenerateMipmap(target);}
    // C function void glGenFramebuffers ( GLsizei n, GLuint *framebuffers )
    public static void glGenFramebuffers(
            int n,
            int[] framebuffers,
            int offset
    ) {JOGLGLES.gl2().glGenFramebuffers(n, framebuffers, offset);}
    // C function void glGenFramebuffers ( GLsizei n, GLuint *framebuffers )
    public static void glGenFramebuffers(
            int n,
            java.nio.IntBuffer framebuffers
    ) {JOGLGLES.gl2().glGenFramebuffers(n, framebuffers);}
    // C function void glGenRenderbuffers ( GLsizei n, GLuint *renderbuffers )
    public static void glGenRenderbuffers(
            int n,
            int[] renderbuffers,
            int offset
    ) {JOGLGLES.gl2().glGenRenderbuffers(n, renderbuffers, offset);}
    // C function void glGenRenderbuffers ( GLsizei n, GLuint *renderbuffers )
    public static void glGenRenderbuffers(
            int n,
            java.nio.IntBuffer renderbuffers
    ) {JOGLGLES.gl2().glGenRenderbuffers(n, renderbuffers);}
    // C function void glGenTextures ( GLsizei n, GLuint *textures )
    public static void glGenTextures(
            int n,
            int[] textures,
            int offset
    ) {JOGLGLES.gl2().glGenTextures(n, textures, offset);}
    // C function void glGenTextures ( GLsizei n, GLuint *textures )
    public static void glGenTextures(
            int n,
            java.nio.IntBuffer textures
    ) {JOGLGLES.gl2().glGenTextures(n, textures);}
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
    ) {JOGLGLES.gl2().glGetActiveAttrib(program, index, bufsize, length, lengthOffset, size, sizeOffset, type, typeOffset, name, nameOffset);}
    // C function void glGetActiveAttrib ( GLuint program, GLuint index, GLsizei bufsize, GLsizei *length, GLint *size, GLenum *type, char *name )
    public static String glGetActiveAttrib(
            int program,
            int index,
            int[] size,
            int sizeOffset,
            int[] type,
            int typeOffset
     ) {
        int[] namelen = new int[1];
        JOGLGLES.gl2().glGetProgramiv(program, GL_ACTIVE_ATTRIBUTE_MAX_LENGTH, namelen, 0);
        byte[] name = new byte[namelen[0]+1];
        namelen[0] = 0;
        glGetActiveAttrib(program, index, name.length-1, namelen, 0, size, sizeOffset, type, typeOffset, name, 0);
        return namelen[0] > 0 ? new String(name, 0, namelen[0]) : null;
    }
    // C function void glGetActiveAttrib ( GLuint program, GLuint index, GLsizei bufsize, GLsizei *length, GLint *size, GLenum *type, char *name )
    public static String glGetActiveAttrib(
            int program,
            int index,
            java.nio.IntBuffer size,
            java.nio.IntBuffer type
    ) {
        java.nio.IntBuffer namelen = JOGLGLES.allocateDirect(1, java.nio.IntBuffer.class);
        JOGLGLES.gl2().glGetProgramiv(program, GL_ACTIVE_ATTRIBUTE_MAX_LENGTH, namelen);
        namelen.clear();
        java.nio.ByteBuffer name = JOGLGLES.allocateDirect(namelen.get(0)+1, java.nio.ByteBuffer.class);
        namelen.put(0, 0);
        JOGLGLES.gl2().glGetActiveAttrib(program, index, name.capacity()-1, namelen, size, type, name);
        final int len = namelen.get(0);
        if(len < 1)
            return null;
        byte[] chars = new byte[len];
        name.clear();
        name.get(chars);
        return new String(chars, 0, chars.length);
    }
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
    ) {JOGLGLES.gl2().glGetActiveUniform(program, index, bufsize, length, lengthOffset, size, sizeOffset, type, typeOffset, name, nameOffset);}
    // C function void glGetActiveUniform ( GLuint program, GLuint index, GLsizei bufsize, GLsizei *length, GLint *size, GLenum *type, char *name )
    public static String glGetActiveUniform(
            int program,
            int index,
            int[] size,
            int sizeOffset,
            int[] type,
            int typeOffset
    ) {
        int[] namelen = new int[1];
        JOGLGLES.gl2().glGetProgramiv(program, GL_ACTIVE_UNIFORM_MAX_LENGTH, namelen, 0);
        byte[] name = new byte[namelen[0]+1];
        namelen[0] = 0;
        glGetActiveUniform(program, index, name.length-1, namelen, 0, size, sizeOffset, type, typeOffset, name, 0);
        return namelen[0] > 0 ? new String(name, 0, namelen[0]) : null;
    }
    // C function void glGetActiveUniform ( GLuint program, GLuint index, GLsizei bufsize, GLsizei *length, GLint *size, GLenum *type, char *name )
    public static String glGetActiveUniform(
            int program,
            int index,
            java.nio.IntBuffer size,
            java.nio.IntBuffer type
    ) {
        java.nio.IntBuffer namelen = JOGLGLES.allocateDirect(1, java.nio.IntBuffer.class);
        JOGLGLES.gl2().glGetProgramiv(program, GL_ACTIVE_UNIFORM_MAX_LENGTH, namelen);
        namelen.clear();
        java.nio.ByteBuffer name = JOGLGLES.allocateDirect(namelen.get(0)+1, java.nio.ByteBuffer.class);
        namelen.put(0, 0);
        JOGLGLES.gl2().glGetActiveUniform(program, index, name.capacity()-1, namelen, size, type, name);
        final int len = namelen.get(0);
        if(len < 1)
            return null;
        byte[] chars = new byte[len];
        name.clear();
        name.get(chars);
        return new String(chars, 0, chars.length);
    }
    // C function void glGetAttachedShaders ( GLuint program, GLsizei maxcount, GLsizei *count, GLuint *shaders )
    public static void glGetAttachedShaders(
            int program,
            int maxcount,
            int[] count,
            int countOffset,
            int[] shaders,
            int shadersOffset
    ) {JOGLGLES.gl2().glGetAttachedShaders(program, maxcount, count, countOffset, shaders, shadersOffset);}
    // C function void glGetAttachedShaders ( GLuint program, GLsizei maxcount, GLsizei *count, GLuint *shaders )
    public static void glGetAttachedShaders(
            int program,
            int maxcount,
            java.nio.IntBuffer count,
            java.nio.IntBuffer shaders
    ) {JOGLGLES.gl2().glGetAttachedShaders(program, maxcount, count, shaders);}
    // C function GLint glGetAttribLocation ( GLuint program, const char *name )
    public static int glGetAttribLocation(
            int program,
            String name
    ) {return JOGLGLES.gl2().glGetAttribLocation(program, name);}
    // C function void glGetBooleanv ( GLenum pname, GLboolean *params )
    public static void glGetBooleanv(
            int pname,
            boolean[] params,
            int offset
    ) {
        // XXX - not sure how many values are going to be read
        byte[] bparams = new byte[params.length-offset];
        JOGLGLES.gl2().glGetBooleanv(pname, bparams, 0);
        for(int i = 0; i < bparams.length; i++)
            params[offset+i] = (bparams[i] == GL_FALSE) ? false : true;
    }
    // C function void glGetBooleanv ( GLenum pname, GLboolean *params )
    public static void glGetBooleanv(
            int pname,
            java.nio.IntBuffer params
    ) {
        // XXX - not sure how many values are going to be read, not sure if impl advances position
        byte[] bparams = new byte[params.remaining()];
        JOGLGLES.gl2().glGetBooleanv(pname, bparams, 0);
        for(int i = 0; i < bparams.length; i++)
            params.put((int)(bparams[i]&0xFF));
    }
    // C function void glGetBufferParameteriv ( GLenum target, GLenum pname, GLint *params )
    public static void glGetBufferParameteriv(
            int target,
            int pname,
            int[] params,
            int offset
    ) {JOGLGLES.gl2().glGetBufferParameteriv(target, pname, params, offset);}
    // C function void glGetBufferParameteriv ( GLenum target, GLenum pname, GLint *params )
    public static void glGetBufferParameteriv(
            int target,
            int pname,
            java.nio.IntBuffer params
    ) {JOGLGLES.gl2().glGetBufferParameteriv(target, pname, params);}
    // C function GLenum glGetError ( void )
    public static int glGetError(
    ) {return JOGLGLES.gl2().glGetError();}
    // C function void glGetFloatv ( GLenum pname, GLfloat *params )
    public static void glGetFloatv(
            int pname,
            float[] params,
            int offset
    ) {JOGLGLES.gl2().glGetFloatv(pname, params, offset);}
    // C function void glGetFloatv ( GLenum pname, GLfloat *params )
    public static void glGetFloatv(
            int pname,
            java.nio.FloatBuffer params
    ) {JOGLGLES.gl2().glGetFloatv(pname, params);}
    // C function void glGetFramebufferAttachmentParameteriv ( GLenum target, GLenum attachment, GLenum pname, GLint *params )
    public static void glGetFramebufferAttachmentParameteriv(
            int target,
            int attachment,
            int pname,
            int[] params,
            int offset
    ) {JOGLGLES.gl2().glGetFramebufferAttachmentParameteriv(target, attachment, pname, params, offset);}
    // C function void glGetFramebufferAttachmentParameteriv ( GLenum target, GLenum attachment, GLenum pname, GLint *params )
    public static void glGetFramebufferAttachmentParameteriv(
            int target,
            int attachment,
            int pname,
            java.nio.IntBuffer params
    ) {JOGLGLES.gl2().glGetFramebufferAttachmentParameteriv(target, attachment, pname, params);}
    // C function void glGetIntegerv ( GLenum pname, GLint *params )
    public static void glGetIntegerv(
            int pname,
            int[] params,
            int offset
    ) {JOGLGLES.gl2().glGetIntegerv(pname, params, offset);}
    // C function void glGetIntegerv ( GLenum pname, GLint *params )
    public static void glGetIntegerv(
            int pname,
            java.nio.IntBuffer params
    ) {JOGLGLES.gl2().glGetIntegerv(pname, params);}
    // C function void glGetProgramiv ( GLuint program, GLenum pname, GLint *params )
    public static void glGetProgramiv(
            int program,
            int pname,
            int[] params,
            int offset
    ) {JOGLGLES.gl2().glGetProgramiv(program, pname, params, offset);}
    // C function void glGetProgramiv ( GLuint program, GLenum pname, GLint *params )
    public static void glGetProgramiv(
            int program,
            int pname,
            java.nio.IntBuffer params
    ) {JOGLGLES.gl2().glGetProgramiv(program, pname, params);}
    // C function void glGetProgramInfoLog( GLuint program, GLsizei maxLength, GLsizei * length,
    //     GLchar * infoLog);
    public static String glGetProgramInfoLog(
            int program
    ) {
        int[] loglen = new int[1];
        glGetProgramiv(program, GL_INFO_LOG_LENGTH, loglen, 0);
        byte[] chars = new byte[loglen[0]+1];
        loglen[0] = 0;
         JOGLGLES.gl2().glGetProgramInfoLog(program, chars.length, loglen, 0, chars, 0);
         if(loglen[0] < 1)
             return null;
         return new String(chars, 0, loglen[0]);
    }
    // C function void glGetRenderbufferParameteriv ( GLenum target, GLenum pname, GLint *params )
    public static void glGetRenderbufferParameteriv(
            int target,
            int pname,
            int[] params,
            int offset
    ) {JOGLGLES.gl2().glGetRenderbufferParameteriv(target, pname, params, offset);}
    // C function void glGetRenderbufferParameteriv ( GLenum target, GLenum pname, GLint *params )
    public static void glGetRenderbufferParameteriv(
            int target,
            int pname,
            java.nio.IntBuffer params
    ) {JOGLGLES.gl2().glGetRenderbufferParameteriv(target, pname, params);}
    // C function void glGetShaderiv ( GLuint shader, GLenum pname, GLint *params )
    public static void glGetShaderiv(
            int shader,
            int pname,
            int[] params,
            int offset
    ) {JOGLGLES.gl2().glGetShaderiv(shader, pname, params, offset);}
    // C function void glGetShaderiv ( GLuint shader, GLenum pname, GLint *params )
    public static void glGetShaderiv(
            int shader,
            int pname,
            java.nio.IntBuffer params
    ) {JOGLGLES.gl2().glGetShaderiv(shader, pname, params);}
    // C function void glGetShaderInfoLog( GLuint shader, GLsizei maxLength, GLsizei * length,
    //     GLchar * infoLog);
    public static String glGetShaderInfoLog(
            int shader
    ) {
        int[] loglen = new int[1];
        glGetShaderiv(shader, GL_INFO_LOG_LENGTH, loglen, 0);
        byte[] chars = new byte[loglen[0]+1];
        loglen[0] = 0;
        JOGLGLES.gl2().glGetShaderInfoLog(shader, chars.length, loglen, 0, chars, 0);
        if(loglen[0] < 1)
            return null;
        return new String(chars, 0, loglen[0]);
    }
    // C function void glGetShaderPrecisionFormat ( GLenum shadertype, GLenum precisiontype, GLint *range, GLint *precision )
    public static void glGetShaderPrecisionFormat(
            int shadertype,
            int precisiontype,
            int[] range,
            int rangeOffset,
            int[] precision,
            int precisionOffset
    ) {JOGLGLES.gl2().glGetShaderPrecisionFormat(shadertype, precisiontype, range, rangeOffset, precision, precisionOffset);}
    // C function void glGetShaderPrecisionFormat ( GLenum shadertype, GLenum precisiontype, GLint *range, GLint *precision )
    public static void glGetShaderPrecisionFormat(
            int shadertype,
            int precisiontype,
            java.nio.IntBuffer range,
            java.nio.IntBuffer precision
    ) {JOGLGLES.gl2().glGetShaderPrecisionFormat(shadertype, precisiontype, range, precision);}
    // C function void glGetShaderSource ( GLuint shader, GLsizei bufsize, GLsizei *length, char *source )
    public static void glGetShaderSource(
            int shader,
            int bufsize,
            int[] length,
            int lengthOffset,
            byte[] source,
            int sourceOffset
    ) {JOGLGLES.gl2().glGetShaderSource(shader, bufsize, length, lengthOffset, source, sourceOffset);}
    // C function void glGetShaderSource ( GLuint shader, GLsizei bufsize, GLsizei *length, char *source )
    public static String glGetShaderSource(
            int shader
    ) {
        int[] shaderlen = new int[1];
        glGetShaderiv(shader, GL_SHADER_SOURCE_LENGTH, shaderlen, 0);
        byte[] chars = new byte[shaderlen[0]+1];
        shaderlen[0] = 0;
        glGetShaderSource(shader, chars.length, shaderlen, 0, chars, 0);
        if(shaderlen[0] < 1)
            return null;
        return new String(chars, 0, shaderlen[0]);
    }
    // C function const GLubyte * glGetString ( GLenum name )
    public static String glGetString(
            int name
    ) {return JOGLGLES.gl2().glGetString(name);}
    // C function void glGetTexParameterfv ( GLenum target, GLenum pname, GLfloat *params )
    public static void glGetTexParameterfv(
            int target,
            int pname,
            float[] params,
            int offset
    ) {JOGLGLES.gl2().glGetTexParameterfv(target, pname, params, offset);}
    // C function void glGetTexParameterfv ( GLenum target, GLenum pname, GLfloat *params )
    public static void glGetTexParameterfv(
            int target,
            int pname,
            java.nio.FloatBuffer params
    ) {JOGLGLES.gl2().glGetTexParameterfv(target, pname, params);}
    // C function void glGetTexParameteriv ( GLenum target, GLenum pname, GLint *params )
    public static void glGetTexParameteriv(
            int target,
            int pname,
            int[] params,
            int offset
    ) {JOGLGLES.gl2().glGetTexParameteriv(target, pname, params, offset);}
    // C function void glGetTexParameteriv ( GLenum target, GLenum pname, GLint *params )
    public static void glGetTexParameteriv(
            int target,
            int pname,
            java.nio.IntBuffer params
    ) {JOGLGLES.gl2().glGetTexParameteriv(target, pname, params);}
    // C function void glGetUniformfv ( GLuint program, GLint location, GLfloat *params )
    public static void glGetUniformfv(
            int program,
            int location,
            float[] params,
            int offset
    ) {JOGLGLES.gl2().glGetUniformfv(program, location, params, offset);}
    // C function void glGetUniformfv ( GLuint program, GLint location, GLfloat *params )
    public static void glGetUniformfv(
            int program,
            int location,
            java.nio.FloatBuffer params
    ) {JOGLGLES.gl2().glGetUniformfv(program, location, params);}
    // C function void glGetUniformiv ( GLuint program, GLint location, GLint *params )
    public static void glGetUniformiv(
            int program,
            int location,
            int[] params,
            int offset
    ) {JOGLGLES.gl2().glGetUniformiv(program, location, params, offset);}
    // C function void glGetUniformiv ( GLuint program, GLint location, GLint *params )
    public static void glGetUniformiv(
            int program,
            int location,
            java.nio.IntBuffer params
    ) {JOGLGLES.gl2().glGetUniformiv(program, location, params);}
    // C function GLint glGetUniformLocation ( GLuint program, const char *name )
    public static int glGetUniformLocation(
            int program,
            String name
    ) {return JOGLGLES.gl2().glGetUniformLocation(program, name);}
    // C function void glGetVertexAttribfv ( GLuint index, GLenum pname, GLfloat *params )
    public static void glGetVertexAttribfv(
            int index,
            int pname,
            float[] params,
            int offset
    ) {JOGLGLES.gl2().glGetVertexAttribfv(index, pname, params, offset);}
    // C function void glGetVertexAttribfv ( GLuint index, GLenum pname, GLfloat *params )
    public static void glGetVertexAttribfv(
            int index,
            int pname,
            java.nio.FloatBuffer params
    ) {JOGLGLES.gl2().glGetVertexAttribfv(index, pname, params);}
    // C function void glGetVertexAttribiv ( GLuint index, GLenum pname, GLint *params )
    public static void glGetVertexAttribiv(
            int index,
            int pname,
            int[] params,
            int offset
    ) {JOGLGLES.gl2().glGetVertexAttribiv(index, pname, params, offset);}
    // C function void glGetVertexAttribiv ( GLuint index, GLenum pname, GLint *params )
    public static void glGetVertexAttribiv(
            int index,
            int pname,
            java.nio.IntBuffer params
    ) {JOGLGLES.gl2().glGetVertexAttribiv(index, pname, params);}
    // C function void glHint ( GLenum target, GLenum mode )
    public static void glHint(
            int target,
            int mode
    ) {JOGLGLES.gl2().glHint(target, mode);}
    // C function GLboolean glIsBuffer ( GLuint buffer )
    public static boolean glIsBuffer(
            int buffer
    ) {return JOGLGLES.gl2().glIsBuffer(buffer);}
    // C function GLboolean glIsEnabled ( GLenum cap )
    public static boolean glIsEnabled(
            int cap
    ) {return JOGLGLES.gl2().glIsEnabled(cap);}
    // C function GLboolean glIsFramebuffer ( GLuint framebuffer )
    public static boolean glIsFramebuffer(
            int framebuffer
    ) {return JOGLGLES.gl2().glIsFramebuffer(framebuffer);}
    // C function GLboolean glIsProgram ( GLuint program )
    public static boolean glIsProgram(
            int program
    ) {return JOGLGLES.gl2().glIsProgram(program);}
    // C function GLboolean glIsRenderbuffer ( GLuint renderbuffer )
    public static boolean glIsRenderbuffer(
            int renderbuffer
    ) {return JOGLGLES.gl2().glIsRenderbuffer(renderbuffer);}
    // C function GLboolean glIsShader ( GLuint shader )
    public static boolean glIsShader(
            int shader
    ) {return JOGLGLES.gl2().glIsShader(shader);}
    // C function GLboolean glIsTexture ( GLuint texture )
    public static boolean glIsTexture(
            int texture
    ) {return JOGLGLES.gl2().glIsTexture(texture);}
    // C function void glLineWidth ( GLfloat width )
    public static void glLineWidth(
            float width
    ) {JOGLGLES.gl2().glLineWidth(width);}
    // C function void glLinkProgram ( GLuint program )
    public static void glLinkProgram(
            int program
    ) {JOGLGLES.gl2().glLinkProgram(program);}
    // C function void glPixelStorei ( GLenum pname, GLint param )
    public static void glPixelStorei(
            int pname,
            int param
    ) {JOGLGLES.gl2().glPixelStorei(pname, param);}
    // C function void glPolygonOffset ( GLfloat factor, GLfloat units )
    public static void glPolygonOffset(
            float factor,
            float units
    ) {JOGLGLES.gl2().glPolygonOffset(factor, units);}
    // C function void glReadPixels ( GLint x, GLint y, GLsizei width, GLsizei height, GLenum format, GLenum type, GLvoid *pixels )
    public static void glReadPixels(
            int x,
            int y,
            int width,
            int height,
            int format,
            int type,
            java.nio.Buffer pixels
    ) {JOGLGLES.gl2().glReadPixels(x, y, width, height, format, type, pixels);}
    // C function void glReleaseShaderCompiler ( void )
    public static void glReleaseShaderCompiler(
    ) {JOGLGLES.gl2().glReleaseShaderCompiler();}
    // C function void glRenderbufferStorage ( GLenum target, GLenum internalformat, GLsizei width, GLsizei height )
    public static void glRenderbufferStorage(
            int target,
            int internalformat,
            int width,
            int height
    ) {JOGLGLES.gl2().glRenderbufferStorage(target, internalformat, width, height);}
    // C function void glSampleCoverage ( GLclampf value, GLboolean invert )
    public static void glSampleCoverage(
            float value,
            boolean invert
    ) {JOGLGLES.gl2().glSampleCoverage(value, invert);}
    // C function void glScissor ( GLint x, GLint y, GLsizei width, GLsizei height )
    public static void glScissor(
            int x,
            int y,
            int width,
            int height
    ) {JOGLGLES.gl2().glScissor(x, y, width, height);}
    // C function void glShaderBinary ( GLsizei n, const GLuint *shaders, GLenum binaryformat, const GLvoid *binary, GLsizei length )
    public static void glShaderBinary(
            int n,
            int[] shaders,
            int offset,
            int binaryformat,
            java.nio.Buffer binary,
            int length
    ) {JOGLGLES.gl2().glShaderBinary(n, shaders, offset, binaryformat, binary, length);}
    // C function void glShaderBinary ( GLsizei n, const GLuint *shaders, GLenum binaryformat, const GLvoid *binary, GLsizei length )
    public static void glShaderBinary(
            int n,
            java.nio.IntBuffer shaders,
            int binaryformat,
            java.nio.Buffer binary,
            int length
    ) {JOGLGLES.gl2().glShaderBinary(n, shaders, binaryformat, binary, length);}
    // C function void glShaderSource ( GLuint shader, GLsizei count, const GLchar ** string, const GLint* length )
    public static void glShaderSource(
            int shader,
            String string
    ) {
        if(JOGLGLES.gl2() != null) {
            // GL instantiated in TAKX for NASAWW globe is constrained to GLSL 120, massage shader
            // source from GLSL 100 to GLSL 120 if that's the supported version
            final String glsl = JOGLGLES.gl2().glGetString(GL2.GL_SHADING_LANGUAGE_VERSION);
            if(glsl != null && glsl.equals("1.20"))
                string = JOGLGLES.glsl120Compatible(string);
        }
        JOGLGLES.gl2().glShaderSource(shader, 1, new String[] {string}, new int[] {string.length()}, 0);
    }
    // C function void glStencilFunc ( GLenum func, GLint ref, GLuint mask )
    public static void glStencilFunc(
            int func,
            int ref,
            int mask
    ) {JOGLGLES.gl2().glStencilFunc(func, ref, mask);}
    // C function void glStencilFuncSeparate ( GLenum face, GLenum func, GLint ref, GLuint mask )
    public static void glStencilFuncSeparate(
            int face,
            int func,
            int ref,
            int mask
    ) {JOGLGLES.gl2().glStencilFuncSeparate(face, func, ref, mask);}
    // C function void glStencilMask ( GLuint mask )
    public static void glStencilMask(
            int mask
    ) {JOGLGLES.gl2().glStencilMask(mask);}
    // C function void glStencilMaskSeparate ( GLenum face, GLuint mask )
    public static void glStencilMaskSeparate(
            int face,
            int mask
    ) {JOGLGLES.gl2().glStencilMaskSeparate(face, mask);}
    // C function void glStencilOp ( GLenum fail, GLenum zfail, GLenum zpass )
    public static void glStencilOp(
            int fail,
            int zfail,
            int zpass
    ) {JOGLGLES.gl2().glStencilOp(fail, zfail, zpass);}
    // C function void glStencilOpSeparate ( GLenum face, GLenum fail, GLenum zfail, GLenum zpass )
    public static void glStencilOpSeparate(
            int face,
            int fail,
            int zfail,
            int zpass
    ) {JOGLGLES.gl2().glStencilOpSeparate(face, fail, zfail, zpass);}
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
    ) {JOGLGLES.gl2().glTexImage2D(target, level, internalformat, width, height, border, format, type, pixels);}
    // C function void glTexParameterf ( GLenum target, GLenum pname, GLfloat param )
    public static void glTexParameterf(
            int target,
            int pname,
            float param
    ) {JOGLGLES.gl2().glTexParameterf(target, pname, param);}
    // C function void glTexParameterfv ( GLenum target, GLenum pname, const GLfloat *params )
    public static void glTexParameterfv(
            int target,
            int pname,
            float[] params,
            int offset
    ) {JOGLGLES.gl2().glTexParameterfv(target, pname, params, offset);}
    // C function void glTexParameterfv ( GLenum target, GLenum pname, const GLfloat *params )
    public static void glTexParameterfv(
            int target,
            int pname,
            java.nio.FloatBuffer params
    ) {JOGLGLES.gl2().glTexParameterfv(target, pname, params);}
    // C function void glTexParameteri ( GLenum target, GLenum pname, GLint param )
    public static void glTexParameteri(
            int target,
            int pname,
            int param
    ) {JOGLGLES.gl2().glTexParameteri(target, pname, param);}
    // C function void glTexParameteriv ( GLenum target, GLenum pname, const GLint *params )
    public static void glTexParameteriv(
            int target,
            int pname,
            int[] params,
            int offset
    ) {JOGLGLES.gl2().glTexParameteriv(target, pname, params, offset);}
    // C function void glTexParameteriv ( GLenum target, GLenum pname, const GLint *params )
    public static void glTexParameteriv(
            int target,
            int pname,
            java.nio.IntBuffer params
    ) {JOGLGLES.gl2().glTexParameteriv(target, pname, params);}
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
    ) {JOGLGLES.gl2().glTexSubImage2D(target, level, xoffset, yoffset, width, height, format, type, pixels);}
    // C function void glUniform1f ( GLint location, GLfloat x )
    public static void glUniform1f(
            int location,
            float x
    ) {JOGLGLES.gl2().glUniform1f(location, x);}
    // C function void glUniform1fv ( GLint location, GLsizei count, const GLfloat *v )
    public static void glUniform1fv(
            int location,
            int count,
            float[] v,
            int offset
    ) {JOGLGLES.gl2().glUniform1fv(location, count, v, offset);}
    // C function void glUniform1fv ( GLint location, GLsizei count, const GLfloat *v )
    public static void glUniform1fv(
            int location,
            int count,
            java.nio.FloatBuffer v
    ) {JOGLGLES.gl2().glUniform1fv(location, count, v);}
    // C function void glUniform1i ( GLint location, GLint x )
    public static void glUniform1i(
            int location,
            int x
    ) {JOGLGLES.gl2().glUniform1i(location, x);}
    // C function void glUniform1iv ( GLint location, GLsizei count, const GLint *v )
    public static void glUniform1iv(
            int location,
            int count,
            int[] v,
            int offset
    ) {JOGLGLES.gl2().glUniform1iv(location, count, v, offset);}
    // C function void glUniform1iv ( GLint location, GLsizei count, const GLint *v )
    public static void glUniform1iv(
            int location,
            int count,
            java.nio.IntBuffer v
    ) {JOGLGLES.gl2().glUniform1iv(location, count, v);}
    // C function void glUniform2f ( GLint location, GLfloat x, GLfloat y )
    public static void glUniform2f(
            int location,
            float x,
            float y
    ) {JOGLGLES.gl2().glUniform2f(location, x, y);}
    // C function void glUniform2fv ( GLint location, GLsizei count, const GLfloat *v )
    public static void glUniform2fv(
            int location,
            int count,
            float[] v,
            int offset
    ) {JOGLGLES.gl2().glUniform2fv(location, count, v, offset);}
    // C function void glUniform2fv ( GLint location, GLsizei count, const GLfloat *v )
    public static void glUniform2fv(
            int location,
            int count,
            java.nio.FloatBuffer v
    ) {JOGLGLES.gl2().glUniform2fv(location, count, v);}
    // C function void glUniform2i ( GLint location, GLint x, GLint y )
    public static void glUniform2i(
            int location,
            int x,
            int y
    ) {JOGLGLES.gl2().glUniform2i(location, x, y);}
    // C function void glUniform2iv ( GLint location, GLsizei count, const GLint *v )
    public static void glUniform2iv(
            int location,
            int count,
            int[] v,
            int offset
    ) {JOGLGLES.gl2().glUniform2iv(location, count, v, offset);}
    // C function void glUniform2iv ( GLint location, GLsizei count, const GLint *v )
    public static void glUniform2iv(
            int location,
            int count,
            java.nio.IntBuffer v
    ) {JOGLGLES.gl2().glUniform2iv(location, count, v);}
    // C function void glUniform3f ( GLint location, GLfloat x, GLfloat y, GLfloat z )
    public static void glUniform3f(
            int location,
            float x,
            float y,
            float z
    ) {JOGLGLES.gl2().glUniform3f(location, x, y, z);}
    // C function void glUniform3fv ( GLint location, GLsizei count, const GLfloat *v )
    public static void glUniform3fv(
            int location,
            int count,
            float[] v,
            int offset
    ) {JOGLGLES.gl2().glUniform3fv(location, count, v, offset);}
    // C function void glUniform3fv ( GLint location, GLsizei count, const GLfloat *v )
    public static void glUniform3fv(
            int location,
            int count,
            java.nio.FloatBuffer v
    ) {JOGLGLES.gl2().glUniform3fv(location, count, v);}
    // C function void glUniform3i ( GLint location, GLint x, GLint y, GLint z )
    public static void glUniform3i(
            int location,
            int x,
            int y,
            int z
    ) {JOGLGLES.gl2().glUniform3i(location, x, y, z);}
    // C function void glUniform3iv ( GLint location, GLsizei count, const GLint *v )
    public static void glUniform3iv(
            int location,
            int count,
            int[] v,
            int offset
    ) {JOGLGLES.gl2().glUniform3iv(location, count, v, offset);}
    // C function void glUniform3iv ( GLint location, GLsizei count, const GLint *v )
    public static void glUniform3iv(
            int location,
            int count,
            java.nio.IntBuffer v
    ) {JOGLGLES.gl2().glUniform3iv(location, count, v);}
    // C function void glUniform4f ( GLint location, GLfloat x, GLfloat y, GLfloat z, GLfloat w )
    public static void glUniform4f(
            int location,
            float x,
            float y,
            float z,
            float w
    ) {JOGLGLES.gl2().glUniform4f(location, x, y, z, w);}
    // C function void glUniform4fv ( GLint location, GLsizei count, const GLfloat *v )
    public static void glUniform4fv(
            int location,
            int count,
            float[] v,
            int offset
    ) {JOGLGLES.gl2().glUniform4fv(location, count, v, offset);}
    // C function void glUniform4fv ( GLint location, GLsizei count, const GLfloat *v )
    public static void glUniform4fv(
            int location,
            int count,
            java.nio.FloatBuffer v
    ) {JOGLGLES.gl2().glUniform4fv(location, count, v);}
    // C function void glUniform4i ( GLint location, GLint x, GLint y, GLint z, GLint w )
    public static void glUniform4i(
            int location,
            int x,
            int y,
            int z,
            int w
    ) {JOGLGLES.gl2().glUniform4i(location, x, y, z, w);}
    // C function void glUniform4iv ( GLint location, GLsizei count, const GLint *v )
    public static void glUniform4iv(
            int location,
            int count,
            int[] v,
            int offset
    ) {JOGLGLES.gl2().glUniform4iv(location, count, v, offset);}
    // C function void glUniform4iv ( GLint location, GLsizei count, const GLint *v )
    public static void glUniform4iv(
            int location,
            int count,
            java.nio.IntBuffer v
    ) {JOGLGLES.gl2().glUniform4iv(location, count, v);}
    // C function void glUniformMatrix2fv ( GLint location, GLsizei count, GLboolean transpose, const GLfloat *value )
    public static void glUniformMatrix2fv(
            int location,
            int count,
            boolean transpose,
            float[] value,
            int offset
    ) {JOGLGLES.gl2().glUniformMatrix2fv(location, count, transpose, value, offset);}
    // C function void glUniformMatrix2fv ( GLint location, GLsizei count, GLboolean transpose, const GLfloat *value )
    public static void glUniformMatrix2fv(
            int location,
            int count,
            boolean transpose,
            java.nio.FloatBuffer value
    ) {JOGLGLES.gl2().glUniformMatrix2fv(location, count, transpose, value);}
    // C function void glUniformMatrix3fv ( GLint location, GLsizei count, GLboolean transpose, const GLfloat *value )
    public static void glUniformMatrix3fv(
            int location,
            int count,
            boolean transpose,
            float[] value,
            int offset
    ) {JOGLGLES.gl2().glUniformMatrix3fv(location, count, transpose, value, offset);}
    // C function void glUniformMatrix3fv ( GLint location, GLsizei count, GLboolean transpose, const GLfloat *value )
    public static void glUniformMatrix3fv(
            int location,
            int count,
            boolean transpose,
            java.nio.FloatBuffer value
    ) {JOGLGLES.gl2().glUniformMatrix3fv(location, count, transpose, value);}
    // C function void glUniformMatrix4fv ( GLint location, GLsizei count, GLboolean transpose, const GLfloat *value )
    public static void glUniformMatrix4fv(
            int location,
            int count,
            boolean transpose,
            float[] value,
            int offset
    ) {JOGLGLES.gl2().glUniformMatrix4fv(location, count, transpose, value, offset);}
    // C function void glUniformMatrix4fv ( GLint location, GLsizei count, GLboolean transpose, const GLfloat *value )
    public static void glUniformMatrix4fv(
            int location,
            int count,
            boolean transpose,
            java.nio.FloatBuffer value
    ) {JOGLGLES.gl2().glUniformMatrix4fv(location, count, transpose, value);}
    // C function void glUseProgram ( GLuint program )
    public static void glUseProgram(
            int program
    ) {JOGLGLES.gl2().glUseProgram(program);}
    // C function void glValidateProgram ( GLuint program )
    public static void glValidateProgram(
            int program
    ) {JOGLGLES.gl2().glValidateProgram(program);}
    // C function void glVertexAttrib1f ( GLuint indx, GLfloat x )
    public static void glVertexAttrib1f(
            int indx,
            float x
    ) {JOGLGLES.gl2().glVertexAttrib1f(indx, x);}
    // C function void glVertexAttrib1fv ( GLuint indx, const GLfloat *values )
    public static void glVertexAttrib1fv(
            int indx,
            float[] values,
            int offset
    ) {JOGLGLES.gl2().glVertexAttrib1fv(indx, values, offset);}
    // C function void glVertexAttrib1fv ( GLuint indx, const GLfloat *values )
    public static void glVertexAttrib1fv(
            int indx,
            java.nio.FloatBuffer values
    ) {JOGLGLES.gl2().glVertexAttrib1fv(indx, values);}
    // C function void glVertexAttrib2f ( GLuint indx, GLfloat x, GLfloat y )
    public static void glVertexAttrib2f(
            int indx,
            float x,
            float y
    ) {JOGLGLES.gl2().glVertexAttrib2f(indx, x, y);}
    // C function void glVertexAttrib2fv ( GLuint indx, const GLfloat *values )
    public static void glVertexAttrib2fv(
            int indx,
            float[] values,
            int offset
    ) {JOGLGLES.gl2().glVertexAttrib2fv(indx, values, offset);}
    // C function void glVertexAttrib2fv ( GLuint indx, const GLfloat *values )
    public static void glVertexAttrib2fv(
            int indx,
            java.nio.FloatBuffer values
    ) {JOGLGLES.gl2().glVertexAttrib2fv(indx, values);}
    // C function void glVertexAttrib3f ( GLuint indx, GLfloat x, GLfloat y, GLfloat z )
    public static void glVertexAttrib3f(
            int indx,
            float x,
            float y,
            float z
    ) {JOGLGLES.gl2().glVertexAttrib3f(indx, x, y, z);}
    // C function void glVertexAttrib3fv ( GLuint indx, const GLfloat *values )
    public static void glVertexAttrib3fv(
            int indx,
            float[] values,
            int offset
    ) {JOGLGLES.gl2().glVertexAttrib3fv(indx, values, offset);}
    // C function void glVertexAttrib3fv ( GLuint indx, const GLfloat *values )
    public static void glVertexAttrib3fv(
            int indx,
            java.nio.FloatBuffer values
    ) {JOGLGLES.gl2().glVertexAttrib3fv(indx, values);}
    // C function void glVertexAttrib4f ( GLuint indx, GLfloat x, GLfloat y, GLfloat z, GLfloat w )
    public static void glVertexAttrib4f(
            int indx,
            float x,
            float y,
            float z,
            float w
    ) {JOGLGLES.gl2().glVertexAttrib4f(indx, x, y, z, w);}
    // C function void glVertexAttrib4fv ( GLuint indx, const GLfloat *values )
    public static void glVertexAttrib4fv(
            int indx,
            float[] values,
            int offset
    ) {JOGLGLES.gl2().glVertexAttrib4fv(indx, values, offset);}
    // C function void glVertexAttrib4fv ( GLuint indx, const GLfloat *values )
    public static void glVertexAttrib4fv(
            int indx,
            java.nio.FloatBuffer values
    ) {JOGLGLES.gl2().glVertexAttrib4fv(indx, values);}
    // C function void glVertexAttribPointer ( GLuint indx, GLint size, GLenum type, GLboolean normalized, GLsizei stride, GLint offset )
    public static void glVertexAttribPointer(
            int indx,
            int size,
            int type,
            boolean normalized,
            int stride,
            int offset
    ) { JOGLGLES.gl2().glVertexAttribPointer(indx, size, type, normalized, stride, offset); }
    // C function void glVertexAttribPointer ( GLuint indx, GLint size, GLenum type, GLboolean normalized, GLsizei stride, const GLvoid *ptr )
    public static void glVertexAttribPointer(
            int indx,
            int size,
            int type,
            boolean normalized,
            int stride,
            java.nio.Buffer ptr
    ) {
        if(JOGLGLES.gl4() == null) {
            JOGLGLES.gl2().glVertexAttribPointer(indx, size, type, normalized, stride, ptr);
        } else {
            final VertexAttribArrays va = vertexAttribArrays.get();
            va.pointer(indx, size, type, normalized, stride, ptr);
        }
    }
    // C function void glViewport ( GLint x, GLint y, GLsizei width, GLsizei height )
    public static void glViewport(
            int x,
            int y,
            int width,
            int height
    ) {JOGLGLES.gl2().glViewport(x, y, width, height);}
}
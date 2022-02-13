/*
 **
 ** Copyright 2013, The Android Open Source Project
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;

/** OpenGL ES 3.0
 */

public class GLES30 extends GLES20 implements GLES30Constants {
    private final static Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    // C function void glReadBuffer ( GLenum mode )
    public static void glReadBuffer(
        int mode
    ) { JOGLGLES.gl2().glReadBuffer(mode);}
    // C function void glDrawRangeElements ( GLenum mode, GLuint start, GLuint end, GLsizei count, GLenum type, const GLvoid *indices )
    public static void glDrawRangeElements(
        int mode,
        int start,
        int end,
        int count,
        int type,
        java.nio.Buffer indices
    ) {JOGLGLES.gl2().glDrawRangeElements(mode, start, end, count, type, indices);}
    // C function void glDrawRangeElements ( GLenum mode, GLuint start, GLuint end, GLsizei count, GLenum type, GLsizei offset )
    public static void glDrawRangeElements(
        int mode,
        int start,
        int end,
        int count,
        int type,
        int offset
    ) {JOGLGLES.gl2().glDrawRangeElements(mode, start, end, count, type, offset);}
    // C function void glTexImage3D ( GLenum target, GLint level, GLint internalformat, GLsizei width, GLsizei height, GLsizei depth, GLint border, GLenum format, GLenum type, const GLvoid *pixels )
    public static void glTexImage3D(
        int target,
        int level,
        int internalformat,
        int width,
        int height,
        int depth,
        int border,
        int format,
        int type,
        java.nio.Buffer pixels
    ) {JOGLGLES.gl2().glTexImage3D(target, level, internalformat, width, height, depth, border, format, type, pixels);}
    // C function void glTexImage3D ( GLenum target, GLint level, GLint internalformat, GLsizei width, GLsizei height, GLsizei depth, GLint border, GLenum format, GLenum type, GLsizei offset )
    public static void glTexImage3D(
        int target,
        int level,
        int internalformat,
        int width,
        int height,
        int depth,
        int border,
        int format,
        int type,
        int offset
    ) {JOGLGLES.gl2().glTexImage3D(target, level, internalformat, width, height, depth, border, format, type, offset);}
    // C function void glTexSubImage3D ( GLenum target, GLint level, GLint xoffset, GLint yoffset, GLint zoffset, GLsizei width, GLsizei height, GLsizei depth, GLenum format, GLenum type, const GLvoid *pixels )
    public static void glTexSubImage3D(
        int target,
        int level,
        int xoffset,
        int yoffset,
        int zoffset,
        int width,
        int height,
        int depth,
        int format,
        int type,
        java.nio.Buffer pixels
    ) {JOGLGLES.gl2().glTexSubImage3D(target, level, xoffset, yoffset, zoffset, width, height, depth, format, type, pixels);}
    // C function void glTexSubImage3D ( GLenum target, GLint level, GLint xoffset, GLint yoffset, GLint zoffset, GLsizei width, GLsizei height, GLsizei depth, GLenum format, GLenum type, GLsizei offset )
    public static void glTexSubImage3D(
        int target,
        int level,
        int xoffset,
        int yoffset,
        int zoffset,
        int width,
        int height,
        int depth,
        int format,
        int type,
        int offset
    ) {JOGLGLES.gl2().glTexSubImage3D(target, level, xoffset, yoffset, zoffset, width, height, depth, format, type, offset);}
    // C function void glCopyTexSubImage3D ( GLenum target, GLint level, GLint xoffset, GLint yoffset, GLint zoffset, GLint x, GLint y, GLsizei width, GLsizei height )
    public static void glCopyTexSubImage3D(
        int target,
        int level,
        int xoffset,
        int yoffset,
        int zoffset,
        int x,
        int y,
        int width,
        int height
    ) {JOGLGLES.gl2().glCopyTexSubImage3D(target, level, xoffset, yoffset, zoffset, x, y, width, height);}
    // C function void glCompressedTexImage3D ( GLenum target, GLint level, GLenum internalformat, GLsizei width, GLsizei height, GLsizei depth, GLint border, GLsizei imageSize, const GLvoid *data )
    public static void glCompressedTexImage3D(
        int target,
        int level,
        int internalformat,
        int width,
        int height,
        int depth,
        int border,
        int imageSize,
        java.nio.Buffer data
    ) {JOGLGLES.gl2().glCompressedTexImage3D(target, level, internalformat, width, height, depth, border, imageSize, data);}
    // C function void glCompressedTexImage3D ( GLenum target, GLint level, GLenum internalformat, GLsizei width, GLsizei height, GLsizei depth, GLint border, GLsizei imageSize, GLsizei offset )
    public static void glCompressedTexImage3D(
        int target,
        int level,
        int internalformat,
        int width,
        int height,
        int depth,
        int border,
        int imageSize,
        int offset
    ) {JOGLGLES.gl2().glCompressedTexImage3D(target, level, internalformat, width, height, depth, border, imageSize, offset);}
    // C function void glCompressedTexSubImage3D ( GLenum target, GLint level, GLint xoffset, GLint yoffset, GLint zoffset, GLsizei width, GLsizei height, GLsizei depth, GLenum format, GLsizei imageSize, const GLvoid *data )
    public static void glCompressedTexSubImage3D(
        int target,
        int level,
        int xoffset,
        int yoffset,
        int zoffset,
        int width,
        int height,
        int depth,
        int format,
        int imageSize,
        java.nio.Buffer data
    ) {JOGLGLES.gl2().glCompressedTexSubImage3D(target, level, xoffset, yoffset, zoffset, width, height, depth, format, imageSize, data);}
    // C function void glCompressedTexSubImage3D ( GLenum target, GLint level, GLint xoffset, GLint yoffset, GLint zoffset, GLsizei width, GLsizei height, GLsizei depth, GLenum format, GLsizei imageSize, GLsizei offset )
    public static void glCompressedTexSubImage3D(
        int target,
        int level,
        int xoffset,
        int yoffset,
        int zoffset,
        int width,
        int height,
        int depth,
        int format,
        int imageSize,
        int offset
    ) {JOGLGLES.gl2().glCompressedTexSubImage3D(target, level, xoffset, yoffset, zoffset, width, height, depth, format, imageSize, offset);}
    // C function void glGenQueries ( GLsizei n, GLuint *ids )
    public static void glGenQueries(
        int n,
        int[] ids,
        int offset
    ) {JOGLGLES.gl2().glGenQueries(n, ids, offset);}
    // C function void glGenQueries ( GLsizei n, GLuint *ids )
    public static void glGenQueries(
        int n,
        java.nio.IntBuffer ids
    ) {JOGLGLES.gl2().glGenQueries(n, ids);}
    // C function void glDeleteQueries ( GLsizei n, const GLuint *ids )
    public static void glDeleteQueries(
        int n,
        int[] ids,
        int offset
    ) {JOGLGLES.gl2().glDeleteQueries(n, ids, offset);}
    // C function void glDeleteQueries ( GLsizei n, const GLuint *ids )
    public static void glDeleteQueries(
        int n,
        java.nio.IntBuffer ids
    ) {JOGLGLES.gl2().glDeleteQueries(n, ids);}
    // C function GLboolean glIsQuery ( GLuint id )
    public static boolean glIsQuery(
        int id
    ) {return JOGLGLES.gl2().glIsQuery(id);}
    // C function void glBeginQuery ( GLenum target, GLuint id )
    public static void glBeginQuery(
        int target,
        int id
    ) {JOGLGLES.gl2().glBeginQuery(target, id);}
    // C function void glEndQuery ( GLenum target )
    public static void glEndQuery(
        int target
    ) {JOGLGLES.gl2().glEndQuery(target);}
    // C function void glGetQueryiv ( GLenum target, GLenum pname, GLint *params )
    public static void glGetQueryiv(
        int target,
        int pname,
        int[] params,
        int offset
    ) {JOGLGLES.gl2().glGetQueryiv(target, pname, params, offset);}
    // C function void glGetQueryiv ( GLenum target, GLenum pname, GLint *params )
    public static void glGetQueryiv(
        int target,
        int pname,
        java.nio.IntBuffer params
    ) {JOGLGLES.gl2().glGetQueryiv(target, pname, params);}
    // C function void glGetQueryObjectuiv ( GLuint id, GLenum pname, GLuint *params )
    public static void glGetQueryObjectuiv(
        int id,
        int pname,
        int[] params,
        int offset
    ) {JOGLGLES.gl2().glGetQueryObjectuiv(id, pname, params, offset);}
    // C function void glGetQueryObjectuiv ( GLuint id, GLenum pname, GLuint *params )
    public static void glGetQueryObjectuiv(
        int id,
        int pname,
        java.nio.IntBuffer params
    ) {JOGLGLES.gl2().glGetQueryObjectuiv(id, pname, params);}
    // C function GLboolean glUnmapBuffer ( GLenum target )
    public static boolean glUnmapBuffer(
        int target
    ) {return JOGLGLES.gl2().glUnmapBuffer(target);}
    // C function void glGetBufferPointerv ( GLenum target, GLenum pname, GLvoid** params )
    public static java.nio.Buffer glGetBufferPointerv(
        int target,
        int pname
    ) {
        // XXX - not supported by JOGL
        //return JOGLGLES.gl2().glGetBufferPointerv(target, pname);
        log.warn("GLES30: glGetBufferPointerv not supported");
        return null;
    }
    // C function void glDrawBuffers ( GLsizei n, const GLenum *bufs )
    public static void glDrawBuffers(
        int n,
        int[] bufs,
        int offset
    ) {JOGLGLES.gl2().glDrawBuffers(n, bufs, offset);}
    // C function void glDrawBuffers ( GLsizei n, const GLenum *bufs )
    public static void glDrawBuffers(
        int n,
        java.nio.IntBuffer bufs
    ) {JOGLGLES.gl2().glDrawBuffers(n, bufs);}
    // C function void glUniformMatrix2x3fv ( GLint location, GLsizei count, GLboolean transpose, const GLfloat *value )
    public static void glUniformMatrix2x3fv(
        int location,
        int count,
        boolean transpose,
        float[] value,
        int offset
    ) {JOGLGLES.gl2().glUniformMatrix2x3fv(location, count, transpose, value, offset);}
    // C function void glUniformMatrix2x3fv ( GLint location, GLsizei count, GLboolean transpose, const GLfloat *value )
    public static void glUniformMatrix2x3fv(
        int location,
        int count,
        boolean transpose,
        java.nio.FloatBuffer value
    ) {JOGLGLES.gl2().glUniformMatrix2x3fv(location, count, transpose, value);}
    // C function void glUniformMatrix3x2fv ( GLint location, GLsizei count, GLboolean transpose, const GLfloat *value )
    public static void glUniformMatrix3x2fv(
        int location,
        int count,
        boolean transpose,
        float[] value,
        int offset
    ) {JOGLGLES.gl2().glUniformMatrix3x2fv(location, count, transpose, value, offset);}
    // C function void glUniformMatrix3x2fv ( GLint location, GLsizei count, GLboolean transpose, const GLfloat *value )
    public static void glUniformMatrix3x2fv(
        int location,
        int count,
        boolean transpose,
        java.nio.FloatBuffer value
    ) {JOGLGLES.gl2().glUniformMatrix3x2fv(location, count, transpose, value);}
    // C function void glUniformMatrix2x4fv ( GLint location, GLsizei count, GLboolean transpose, const GLfloat *value )
    public static void glUniformMatrix2x4fv(
        int location,
        int count,
        boolean transpose,
        float[] value,
        int offset
    ) {JOGLGLES.gl2().glUniformMatrix2x4fv(location, count, transpose, value, offset);}
    // C function void glUniformMatrix2x4fv ( GLint location, GLsizei count, GLboolean transpose, const GLfloat *value )
    public static void glUniformMatrix2x4fv(
        int location,
        int count,
        boolean transpose,
        java.nio.FloatBuffer value
    ) {JOGLGLES.gl2().glUniformMatrix2x4fv(location, count, transpose, value);}
    // C function void glUniformMatrix4x2fv ( GLint location, GLsizei count, GLboolean transpose, const GLfloat *value )
    public static void glUniformMatrix4x2fv(
        int location,
        int count,
        boolean transpose,
        float[] value,
        int offset
    ) {JOGLGLES.gl2().glUniformMatrix4x2fv(location, count, transpose, value, offset);}
    // C function void glUniformMatrix4x2fv ( GLint location, GLsizei count, GLboolean transpose, const GLfloat *value )
    public static void glUniformMatrix4x2fv(
        int location,
        int count,
        boolean transpose,
        java.nio.FloatBuffer value
    ) {JOGLGLES.gl2().glUniformMatrix4x2fv(location, count, transpose, value);}
    // C function void glUniformMatrix3x4fv ( GLint location, GLsizei count, GLboolean transpose, const GLfloat *value )
    public static void glUniformMatrix3x4fv(
        int location,
        int count,
        boolean transpose,
        float[] value,
        int offset
    ) {JOGLGLES.gl2().glUniformMatrix3x4fv(location, count, transpose, value, offset);}
    // C function void glUniformMatrix3x4fv ( GLint location, GLsizei count, GLboolean transpose, const GLfloat *value )
    public static void glUniformMatrix3x4fv(
        int location,
        int count,
        boolean transpose,
        java.nio.FloatBuffer value
    ) {JOGLGLES.gl2().glUniformMatrix3x4fv(location, count, transpose, value);}
    // C function void glUniformMatrix4x3fv ( GLint location, GLsizei count, GLboolean transpose, const GLfloat *value )
    public static void glUniformMatrix4x3fv(
        int location,
        int count,
        boolean transpose,
        float[] value,
        int offset
    ) {JOGLGLES.gl2().glUniformMatrix4x3fv(location, count, transpose, value, offset);}
    // C function void glUniformMatrix4x3fv ( GLint location, GLsizei count, GLboolean transpose, const GLfloat *value )
    public static void glUniformMatrix4x3fv(
        int location,
        int count,
        boolean transpose,
        java.nio.FloatBuffer value
    ) {JOGLGLES.gl2().glUniformMatrix4x3fv(location, count, transpose, value);}
    // C function void glBlitFramebuffer ( GLint srcX0, GLint srcY0, GLint srcX1, GLint srcY1, GLint dstX0, GLint dstY0, GLint dstX1, GLint dstY1, GLbitfield mask, GLenum filter )
    public static void glBlitFramebuffer(
        int srcX0,
        int srcY0,
        int srcX1,
        int srcY1,
        int dstX0,
        int dstY0,
        int dstX1,
        int dstY1,
        int mask,
        int filter
    ) {JOGLGLES.gl2().glBlitFramebuffer(srcX0, srcY0, srcX1, srcY1, dstX0, dstY0, dstX1, dstY1, mask, filter);}
    // C function void glRenderbufferStorageMultisample ( GLenum target, GLsizei samples, GLenum internalformat, GLsizei width, GLsizei height )
    public static void glRenderbufferStorageMultisample(
        int target,
        int samples,
        int internalformat,
        int width,
        int height
    ) {JOGLGLES.gl2().glRenderbufferStorageMultisample(target, samples, internalformat, width, height);}
    // C function void glFramebufferTextureLayer ( GLenum target, GLenum attachment, GLuint texture, GLint level, GLint layer )
    public static void glFramebufferTextureLayer(
        int target,
        int attachment,
        int texture,
        int level,
        int layer
    ) {JOGLGLES.gl2().glFramebufferTextureLayer(target, attachment, texture, level, layer);}
    // C function GLvoid * glMapBufferRange ( GLenum target, GLintptr offset, GLsizeiptr length, GLbitfield access )
    public static java.nio.Buffer glMapBufferRange(
        int target,
        int offset,
        int length,
        int access
    ) {return JOGLGLES.gl2().glMapBufferRange(target, offset ,length, access);}
    // C function void glFlushMappedBufferRange ( GLenum target, GLintptr offset, GLsizeiptr length )
    public static void glFlushMappedBufferRange(
        int target,
        int offset,
        int length
    ) {JOGLGLES.gl2().glFlushMappedBufferRange(target, offset, length);}
    // C function void glBindVertexArray ( GLuint array )
    public static void glBindVertexArray(
        int array
    ) {JOGLGLES.gl2().glBindVertexArray(array);}
    // C function void glDeleteVertexArrays ( GLsizei n, const GLuint *arrays )
    public static void glDeleteVertexArrays(
        int n,
        int[] arrays,
        int offset
    ) {JOGLGLES.gl2().glDeleteVertexArrays(n, arrays, offset);}
    // C function void glDeleteVertexArrays ( GLsizei n, const GLuint *arrays )
    public static void glDeleteVertexArrays(
        int n,
        java.nio.IntBuffer arrays
    ) {JOGLGLES.gl2().glDeleteVertexArrays(n, arrays);}
    // C function void glGenVertexArrays ( GLsizei n, GLuint *arrays )
    public static void glGenVertexArrays(
        int n,
        int[] arrays,
        int offset
    ) {JOGLGLES.gl2().glGenVertexArrays(n, arrays, offset);}
    // C function void glGenVertexArrays ( GLsizei n, GLuint *arrays )
    public static void glGenVertexArrays(
        int n,
        java.nio.IntBuffer arrays
    ) {JOGLGLES.gl2().glGenVertexArrays(n, arrays);}
    // C function GLboolean glIsVertexArray ( GLuint array )
    public static boolean glIsVertexArray(
        int array
    ) {return JOGLGLES.gl2().glIsVertexArray(array);}
    // C function void glGetIntegeri_v ( GLenum target, GLuint index, GLint *data )
    public static void glGetIntegeri_v(
        int target,
        int index,
        int[] data,
        int offset
    ) {JOGLGLES.gl2().glGetIntegeri_v(target, index, data, offset);}
    // C function void glGetIntegeri_v ( GLenum target, GLuint index, GLint *data )
    public static void glGetIntegeri_v(
        int target,
        int index,
        java.nio.IntBuffer data
    ) {JOGLGLES.gl2().glGetIntegeri_v(target, index, data);}
    // C function void glBeginTransformFeedback ( GLenum primitiveMode )
    public static void glBeginTransformFeedback(
        int primitiveMode
    ) {JOGLGLES.gl2().glBeginTransformFeedback(primitiveMode);}
    // C function void glEndTransformFeedback ( void )
    public static void glEndTransformFeedback(
    ) {JOGLGLES.gl2().glEndTransformFeedback();}
    // C function void glBindBufferRange ( GLenum target, GLuint index, GLuint buffer, GLintptr offset, GLsizeiptr size )
    public static void glBindBufferRange(
        int target,
        int index,
        int buffer,
        int offset,
        int size
    ) {JOGLGLES.gl2().glBindBufferRange(target, index, buffer, offset, size);}
    // C function void glBindBufferBase ( GLenum target, GLuint index, GLuint buffer )
    public static void glBindBufferBase(
        int target,
        int index,
        int buffer
    ) {JOGLGLES.gl2().glBindBufferBase(target, index, buffer);}
    // C function void glTransformFeedbackVaryings ( GLuint program, GLsizei count, const GLchar *varyings, GLenum bufferMode )
    public static void glTransformFeedbackVaryings(
        int program,
        String[] varyings,
        int bufferMode
	) {JOGLGLES.gl2().glTransformFeedbackVaryings(program, varyings.length, varyings, bufferMode);}
    // C function void glGetTransformFeedbackVarying ( GLuint program, GLuint index, GLsizei bufSize, GLsizei *length, GLint *size, GLenum *type, GLchar *name )
    public static void glGetTransformFeedbackVarying(
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
    ) {JOGLGLES.gl2().glGetTransformFeedbackVarying(program, index, bufsize, length, lengthOffset, size, sizeOffset, type, typeOffset, name, nameOffset);}
    // C function void glGetTransformFeedbackVarying ( GLuint program, GLuint index, GLsizei bufSize, GLsizei *length, GLint *size, GLenum *type, GLchar *name )
    public static String glGetTransformFeedbackVarying(
        int program,
        int index,
        int[] size,
        int sizeOffset,
        int[] type,
        int typeOffset
    ) {
        int[] namelen = new int[1];
        JOGLGLES.gl2().glGetProgramiv(program, GL_TRANSFORM_FEEDBACK_VARYING_MAX_LENGTH, namelen, 0);
        byte[] name = new byte[namelen[0]+1];
        namelen[0] = 0;
        JOGLGLES.gl2().glGetTransformFeedbackVarying(program, index, name.length, namelen, 0, size, sizeOffset, type, typeOffset, name, 0);
        return namelen[0] > 0 ? new String(name, 0, namelen[0]) : null;
    }
    // C function void glGetTransformFeedbackVarying ( GLuint program, GLuint index, GLsizei bufSize, GLsizei *length, GLint *size, GLenum *type, GLchar *name )
    public static String glGetTransformFeedbackVarying(
        int program,
        int index,
        java.nio.IntBuffer size,
        java.nio.IntBuffer type
    ) {
        java.nio.IntBuffer namelen = JOGLGLES.allocateDirect(1, java.nio.IntBuffer.class);
        JOGLGLES.gl2().glGetProgramiv(program, GL_TRANSFORM_FEEDBACK_VARYING_MAX_LENGTH, namelen);
        namelen.clear();
        java.nio.ByteBuffer name = JOGLGLES.allocateDirect(namelen.get(0)+1, java.nio.ByteBuffer.class);
        namelen.put(0, 0);
        JOGLGLES.gl2().glGetTransformFeedbackVarying(program, index, name.capacity(), namelen, size, type, name);
        final int len = namelen.get(0);
        if(len < 1)
            return null;
        byte[] chars = new byte[len];
        name.clear();
        name.get(chars);
        return new String(chars, 0, chars.length);
    }
    // C function void glVertexAttribIPointer ( GLuint index, GLint size, GLenum type, GLsizei stride, const GLvoid *pointer )
    public static void glVertexAttribIPointer(
        int index,
        int size,
        int type,
        int stride,
        java.nio.Buffer pointer
    ) {JOGLGLES.gl2().glVertexAttribIPointer(index, size, type, stride, pointer);}
    // C function void glVertexAttribIPointer ( GLuint index, GLint size, GLenum type, GLsizei stride, GLsizei offset )
    public static void glVertexAttribIPointer(
        int index,
        int size,
        int type,
        int stride,
        int offset
    ) {JOGLGLES.gl2().glVertexAttribIPointer(index, size, type, stride, offset);}
    // C function void glGetVertexAttribIiv ( GLuint index, GLenum pname, GLint *params )
    public static void glGetVertexAttribIiv(
        int index,
        int pname,
        int[] params,
        int offset
    ) {JOGLGLES.gl2().glGetVertexAttribIiv(index, pname, params, offset);}
    // C function void glGetVertexAttribIiv ( GLuint index, GLenum pname, GLint *params )
    public static void glGetVertexAttribIiv(
        int index,
        int pname,
        java.nio.IntBuffer params
    ) {JOGLGLES.gl2().glGetVertexAttribIiv(index, pname, params);}
    // C function void glGetVertexAttribIuiv ( GLuint index, GLenum pname, GLuint *params )
    public static void glGetVertexAttribIuiv(
        int index,
        int pname,
        int[] params,
        int offset
    ) {JOGLGLES.gl2().glGetVertexAttribIuiv(index, pname, params, offset);}
    // C function void glGetVertexAttribIuiv ( GLuint index, GLenum pname, GLuint *params )
    public static void glGetVertexAttribIuiv(
        int index,
        int pname,
        java.nio.IntBuffer params
    ) {JOGLGLES.gl2().glGetVertexAttribIuiv(index, pname, params);}
    // C function void glVertexAttribI4i ( GLuint index, GLint x, GLint y, GLint z, GLint w )
    public static void glVertexAttribI4i(
        int index,
        int x,
        int y,
        int z,
        int w
    ) {JOGLGLES.gl2().glVertexAttribI4i(index, x, y, z, w);}
    // C function void glVertexAttribI4ui ( GLuint index, GLuint x, GLuint y, GLuint z, GLuint w )
    public static void glVertexAttribI4ui(
        int index,
        int x,
        int y,
        int z,
        int w
    ) {JOGLGLES.gl2().glVertexAttribI4ui(index, x, y, z, w);}
    // C function void glVertexAttribI4iv ( GLuint index, const GLint *v )
    public static void glVertexAttribI4iv(
        int index,
        int[] v,
        int offset
    ) {JOGLGLES.gl2().glVertexAttribI4iv(index, v, offset);}
    // C function void glVertexAttribI4iv ( GLuint index, const GLint *v )
    public static void glVertexAttribI4iv(
        int index,
        java.nio.IntBuffer v
    ) {JOGLGLES.gl2().glVertexAttribI4iv(index, v);}
    // C function void glVertexAttribI4uiv ( GLuint index, const GLuint *v )
    public static void glVertexAttribI4uiv(
        int index,
        int[] v,
        int offset
    ) {JOGLGLES.gl2().glVertexAttribI4uiv(index, v, offset);}
    // C function void glVertexAttribI4uiv ( GLuint index, const GLuint *v )
    public static void glVertexAttribI4uiv(
        int index,
        java.nio.IntBuffer v
    ) {JOGLGLES.gl2().glVertexAttribI4uiv(index, v);}
    // C function void glGetUniformuiv ( GLuint program, GLint location, GLuint *params )
    public static void glGetUniformuiv(
        int program,
        int location,
        int[] params,
        int offset
    ) {JOGLGLES.gl2().glGetUniformuiv(program, location, params, offset);}
    // C function void glGetUniformuiv ( GLuint program, GLint location, GLuint *params )
    public static void glGetUniformuiv(
        int program,
        int location,
        java.nio.IntBuffer params
    ) {JOGLGLES.gl2().glGetUniformuiv(program, location, params);}
    // C function GLint glGetFragDataLocation ( GLuint program, const GLchar *name )
    public static int glGetFragDataLocation(
        int program,
        String name
    ) {return JOGLGLES.gl2().glGetFragDataLocation(program, name);}
    // C function void glUniform1ui ( GLint location, GLuint v0 )
    public static void glUniform1ui(
        int location,
        int v0
    ) {JOGLGLES.gl2().glUniform1ui(location, v0);}
    // C function void glUniform2ui ( GLint location, GLuint v0, GLuint v1 )
    public static void glUniform2ui(
        int location,
        int v0,
        int v1
    ) {JOGLGLES.gl2().glUniform2ui(location, v0, v1);}
    // C function void glUniform3ui ( GLint location, GLuint v0, GLuint v1, GLuint v2 )
    public static void glUniform3ui(
        int location,
        int v0,
        int v1,
        int v2
    ) {JOGLGLES.gl2().glUniform3ui(location, v0, v1, v2);}
    // C function void glUniform4ui ( GLint location, GLuint v0, GLuint v1, GLuint v2, GLuint v3 )
    public static void glUniform4ui(
        int location,
        int v0,
        int v1,
        int v2,
        int v3
    ) {JOGLGLES.gl2().glUniform4ui(location, v0, v1, v2, v3);}
    // C function void glUniform1uiv ( GLint location, GLsizei count, const GLuint *value )
    public static void glUniform1uiv(
        int location,
        int count,
        int[] value,
        int offset
    ) {JOGLGLES.gl2().glUniform1uiv(location, count, value, offset);}
    // C function void glUniform1uiv ( GLint location, GLsizei count, const GLuint *value )
    public static void glUniform1uiv(
        int location,
        int count,
        java.nio.IntBuffer value
    ) {JOGLGLES.gl2().glUniform1uiv(location, count, value);}
    // C function void glUniform2uiv ( GLint location, GLsizei count, const GLuint *value )
    public static void glUniform2uiv(
        int location,
        int count,
        int[] value,
        int offset
    ) {JOGLGLES.gl2().glUniform2uiv(location, count, value, offset);}
    // C function void glUniform2uiv ( GLint location, GLsizei count, const GLuint *value )
    public static void glUniform2uiv(
        int location,
        int count,
        java.nio.IntBuffer value
    ) {JOGLGLES.gl2().glUniform2uiv(location, count, value);}
    // C function void glUniform3uiv ( GLint location, GLsizei count, const GLuint *value )
    public static void glUniform3uiv(
        int location,
        int count,
        int[] value,
        int offset
    ) {JOGLGLES.gl2().glUniform3uiv(location, count, value, offset);}
    // C function void glUniform3uiv ( GLint location, GLsizei count, const GLuint *value )
    public static void glUniform3uiv(
        int location,
        int count,
        java.nio.IntBuffer value
    ) {JOGLGLES.gl2().glUniform3uiv(location, count, value);}
    // C function void glUniform4uiv ( GLint location, GLsizei count, const GLuint *value )
    public static void glUniform4uiv(
        int location,
        int count,
        int[] value,
        int offset
    ) {JOGLGLES.gl2().glUniform4uiv(location, count, value, offset);}
    // C function void glUniform4uiv ( GLint location, GLsizei count, const GLuint *value )
    public static void glUniform4uiv(
        int location,
        int count,
        java.nio.IntBuffer value
    ) {JOGLGLES.gl2().glUniform4uiv(location, count, value);}
    // C function void glClearBufferiv ( GLenum buffer, GLint drawbuffer, const GLint *value )
    public static void glClearBufferiv(
        int buffer,
        int drawbuffer,
        int[] value,
        int offset
    ) {JOGLGLES.gl2().glClearBufferiv(buffer, drawbuffer, value, offset);}
    // C function void glClearBufferiv ( GLenum buffer, GLint drawbuffer, const GLint *value )
    public static void glClearBufferiv(
        int buffer,
        int drawbuffer,
        java.nio.IntBuffer value
    ) {JOGLGLES.gl2().glClearBufferiv(buffer, drawbuffer, value);}
    // C function void glClearBufferuiv ( GLenum buffer, GLint drawbuffer, const GLuint *value )
    public static void glClearBufferuiv(
        int buffer,
        int drawbuffer,
        int[] value,
        int offset
    ) {JOGLGLES.gl2().glClearBufferuiv(buffer, drawbuffer, value, offset);}
    // C function void glClearBufferuiv ( GLenum buffer, GLint drawbuffer, const GLuint *value )
    public static void glClearBufferuiv(
        int buffer,
        int drawbuffer,
        java.nio.IntBuffer value
    ) {JOGLGLES.gl2().glClearBufferuiv(buffer, drawbuffer, value);}
    // C function void glClearBufferfv ( GLenum buffer, GLint drawbuffer, const GLfloat *value )
    public static void glClearBufferfv(
        int buffer,
        int drawbuffer,
        float[] value,
        int offset
    ) {JOGLGLES.gl2().glClearBufferfv(buffer, drawbuffer, value, offset);}
    // C function void glClearBufferfv ( GLenum buffer, GLint drawbuffer, const GLfloat *value )
    public static void glClearBufferfv(
        int buffer,
        int drawbuffer,
        java.nio.FloatBuffer value
    ) {JOGLGLES.gl2().glClearBufferfv(buffer, drawbuffer, value);}
    // C function void glClearBufferfi ( GLenum buffer, GLint drawbuffer, GLfloat depth, GLint stencil )
    public static void glClearBufferfi(
        int buffer,
        int drawbuffer,
        float depth,
        int stencil
    ) {JOGLGLES.gl2().glClearBufferfi(buffer, drawbuffer, depth, stencil);}
    // C function const GLubyte * glGetStringi ( GLenum name, GLuint index )
    public static String glGetStringi(
        int name,
        int index
    ) {return JOGLGLES.gl2().glGetStringi(name, index);}
    // C function void glCopyBufferSubData ( GLenum readTarget, GLenum writeTarget, GLintptr readOffset, GLintptr writeOffset, GLsizeiptr size )
    public static void glCopyBufferSubData(
        int readTarget,
        int writeTarget,
        int readOffset,
        int writeOffset,
        int size
    ) {JOGLGLES.gl2().glCopyBufferSubData(readTarget, writeTarget, readOffset, writeOffset, size);}
    // C function void glGetUniformIndices ( GLuint program, GLsizei uniformCount, const GLchar *const *uniformNames, GLuint *uniformIndices )
    public static void glGetUniformIndices(
        int program,
        String[] uniformNames,
        int[] uniformIndices,
        int uniformIndicesOffset
	) {JOGLGLES.gl2().glGetUniformIndices(program, uniformNames.length, uniformNames, uniformIndices, uniformIndicesOffset);}
    // C function void glGetUniformIndices ( GLuint program, GLsizei uniformCount, const GLchar *const *uniformNames, GLuint *uniformIndices )
    public static void glGetUniformIndices(
        int program,
        String[] uniformNames,
        java.nio.IntBuffer uniformIndices
    ) {JOGLGLES.gl2().glGetUniformIndices(program, uniformNames.length, uniformNames, uniformIndices);}
    // C function void glGetActiveUniformsiv ( GLuint program, GLsizei uniformCount, const GLuint *uniformIndices, GLenum pname, GLint *params )
    public static void glGetActiveUniformsiv(
        int program,
        int uniformCount,
        int[] uniformIndices,
        int uniformIndicesOffset,
        int pname,
        int[] params,
        int paramsOffset
    ) {JOGLGLES.gl2().glGetActiveUniformsiv(program, uniformCount, uniformIndices, uniformIndicesOffset, pname, params, paramsOffset);}
    // C function void glGetActiveUniformsiv ( GLuint program, GLsizei uniformCount, const GLuint *uniformIndices, GLenum pname, GLint *params )
    public static void glGetActiveUniformsiv(
        int program,
        int uniformCount,
        java.nio.IntBuffer uniformIndices,
        int pname,
        java.nio.IntBuffer params
    ) {JOGLGLES.gl2().glGetActiveUniformsiv(program, uniformCount, uniformIndices, pname, params);}
    // C function GLuint glGetUniformBlockIndex ( GLuint program, const GLchar *uniformBlockName )
    public static int glGetUniformBlockIndex(
        int program,
        String uniformBlockName
    ) {return JOGLGLES.gl2().glGetUniformBlockIndex(program, uniformBlockName);}
    // C function void glGetActiveUniformBlockiv ( GLuint program, GLuint uniformBlockIndex, GLenum pname, GLint *params )
    public static void glGetActiveUniformBlockiv(
        int program,
        int uniformBlockIndex,
        int pname,
        int[] params,
        int offset
    ) {JOGLGLES.gl2().glGetActiveUniformBlockiv(program, uniformBlockIndex, pname, params, offset);}
    // C function void glGetActiveUniformBlockiv ( GLuint program, GLuint uniformBlockIndex, GLenum pname, GLint *params )
    public static void glGetActiveUniformBlockiv(
        int program,
        int uniformBlockIndex,
        int pname,
        java.nio.IntBuffer params
    ) {JOGLGLES.gl2().glGetActiveUniformBlockiv(program, uniformBlockIndex, pname, params);}
    // C function void glGetActiveUniformBlockName ( GLuint program, GLuint uniformBlockIndex, GLsizei bufSize, GLsizei *length, GLchar *uniformBlockName )
    public static void glGetActiveUniformBlockName(
        int program,
        int uniformBlockIndex,
        int bufSize,
        int[] length,
        int lengthOffset,
        byte[] uniformBlockName,
        int uniformBlockNameOffset
    ) {JOGLGLES.gl2().glGetActiveUniformBlockName(program, uniformBlockIndex, bufSize, length, lengthOffset, uniformBlockName, uniformBlockNameOffset);}
    // C function void glGetActiveUniformBlockName ( GLuint program, GLuint uniformBlockIndex, GLsizei bufSize, GLsizei *length, GLchar *uniformBlockName )
    public static void glGetActiveUniformBlockName(
        int program,
        int uniformBlockIndex,
        java.nio.Buffer length,
        java.nio.Buffer uniformBlockName
    ) {
        if(length instanceof java.nio.ByteBuffer)
            length = ((java.nio.ByteBuffer)length).asIntBuffer();
        JOGLGLES.gl2().glGetActiveUniformBlockName(program, uniformBlockIndex, ((java.nio.ByteBuffer)uniformBlockName).remaining(), (java.nio.IntBuffer)length, (java.nio.ByteBuffer)uniformBlockName);
    }
    // C function void glGetActiveUniformBlockName ( GLuint program, GLuint uniformBlockIndex, GLsizei bufSize, GLsizei *length, GLchar *uniformBlockName )
    public static String glGetActiveUniformBlockName(
        int program,
        int uniformBlockIndex
    ) {return glGetActiveUniformBlockName(program, uniformBlockIndex);}
    // C function void glUniformBlockBinding ( GLuint program, GLuint uniformBlockIndex, GLuint uniformBlockBinding )
    public static void glUniformBlockBinding(
        int program,
        int uniformBlockIndex,
        int uniformBlockBinding
    ) {JOGLGLES.gl2().glUniformBlockBinding(program, uniformBlockIndex, uniformBlockBinding);}
    // C function void glDrawArraysInstanced ( GLenum mode, GLint first, GLsizei count, GLsizei instanceCount )
    public static void glDrawArraysInstanced(
        int mode,
        int first,
        int count,
        int instanceCount
    ) {JOGLGLES.gl2().glDrawArraysInstanced(mode, first, count, instanceCount);}
    // C function void glDrawElementsInstanced ( GLenum mode, GLsizei count, GLenum type, const GLvoid *indices, GLsizei instanceCount )
    public static void glDrawElementsInstanced(
        int mode,
        int count,
        int type,
        java.nio.Buffer indices,
        int instanceCount
    ) {JOGLGLES.gl2().glDrawElementsInstanced(mode, count, type, indices, instanceCount);}
    // C function void glDrawElementsInstanced ( GLenum mode, GLsizei count, GLenum type, const GLvoid *indices, GLsizei instanceCount )
    public static void glDrawElementsInstanced(
        int mode,
        int count,
        int type,
        int indicesOffset,
        int instanceCount
    ) {JOGLGLES.gl2().glDrawElementsInstanced(mode, count, type, indicesOffset, instanceCount);}
    // C function GLsync glFenceSync ( GLenum condition, GLbitfield flags )
    public static long glFenceSync(
        int condition,
        int flags
    ) {return JOGLGLES.gl3().glFenceSync(condition, flags);}
    // C function GLboolean glIsSync ( GLsync sync )
    public static boolean glIsSync(
        long sync
    ) {return JOGLGLES.gl3().glIsSync(sync);}
    // C function void glDeleteSync ( GLsync sync )
    public static void glDeleteSync(
        long sync
    ) {JOGLGLES.gl3().glDeleteSync(sync);}
    // C function GLenum glClientWaitSync ( GLsync sync, GLbitfield flags, GLuint64 timeout )
    public static int glClientWaitSync(
        long sync,
        int flags,
        long timeout
    ) {return JOGLGLES.gl3().glClientWaitSync(sync, flags, timeout);}
    // C function void glWaitSync ( GLsync sync, GLbitfield flags, GLuint64 timeout )
    public static void glWaitSync(
        long sync,
        int flags,
        long timeout
    ) {JOGLGLES.gl3().glWaitSync(sync, flags, timeout);}
    // C function void glGetInteger64v ( GLenum pname, GLint64 *params )
    public static void glGetInteger64v(
        int pname,
        long[] params,
        int offset
    ) {JOGLGLES.gl3().glGetInteger64v(pname, params, offset);}
    // C function void glGetInteger64v ( GLenum pname, GLint64 *params )
    public static void glGetInteger64v(
        int pname,
        java.nio.LongBuffer params
    ) {JOGLGLES.gl3().glGetInteger64v(pname, params);}
    // C function void glGetSynciv ( GLsync sync, GLenum pname, GLsizei bufSize, GLsizei *length, GLint *values )
    public static void glGetSynciv(
        long sync,
        int pname,
        int bufSize,
        int[] length,
        int lengthOffset,
        int[] values,
        int valuesOffset
    ) {JOGLGLES.gl3().glGetSynciv(sync, pname, bufSize, length, lengthOffset, values, valuesOffset);}
    // C function void glGetSynciv ( GLsync sync, GLenum pname, GLsizei bufSize, GLsizei *length, GLint *values )
    public static void glGetSynciv(
        long sync,
        int pname,
        int bufSize,
        java.nio.IntBuffer length,
        java.nio.IntBuffer values
    ) {JOGLGLES.gl3().glGetSynciv(sync, pname, bufSize, length, values);}
    // C function void glGetInteger64i_v ( GLenum target, GLuint index, GLint64 *data )
    public static void glGetInteger64i_v(
        int target,
        int index,
        long[] data,
        int offset
    ) {JOGLGLES.gl3().glGetInteger64i_v(target, index, data, offset);}
    // C function void glGetInteger64i_v ( GLenum target, GLuint index, GLint64 *data )
    public static void glGetInteger64i_v(
        int target,
        int index,
        java.nio.LongBuffer data
    ) {JOGLGLES.gl3().glGetInteger64i_v(target, index, data);}
    // C function void glGetBufferParameteri64v ( GLenum target, GLenum pname, GLint64 *params )
    public static void glGetBufferParameteri64v(
        int target,
        int pname,
        long[] params,
        int offset
    ) {JOGLGLES.gl3().glGetBufferParameteri64v(target, pname, params, offset);}
    // C function void glGetBufferParameteri64v ( GLenum target, GLenum pname, GLint64 *params )
    public static void glGetBufferParameteri64v(
        int target,
        int pname,
        java.nio.LongBuffer params
    ) {JOGLGLES.gl3().glGetBufferParameteri64v(target, pname, params);}
    // C function void glGenSamplers ( GLsizei count, GLuint *samplers )
    public static void glGenSamplers(
        int count,
        int[] samplers,
        int offset
    ) {JOGLGLES.gl3().glGenSamplers(count, samplers, offset);}
    // C function void glGenSamplers ( GLsizei count, GLuint *samplers )
    public static void glGenSamplers(
        int count,
        java.nio.IntBuffer samplers
    ) {JOGLGLES.gl3().glGenSamplers(count, samplers);}
    // C function void glDeleteSamplers ( GLsizei count, const GLuint *samplers )
    public static void glDeleteSamplers(
        int count,
        int[] samplers,
        int offset
    ) {JOGLGLES.gl3().glDeleteSamplers(count, samplers, offset);}
    // C function void glDeleteSamplers ( GLsizei count, const GLuint *samplers )
    public static void glDeleteSamplers(
        int count,
        java.nio.IntBuffer samplers
    ) {JOGLGLES.gl3().glDeleteSamplers(count, samplers);}
    // C function GLboolean glIsSampler ( GLuint sampler )
    public static boolean glIsSampler(
        int sampler
    ) {return JOGLGLES.gl3().glIsSampler(sampler);}
    // C function void glBindSampler ( GLuint unit, GLuint sampler )
    public static void glBindSampler(
        int unit,
        int sampler
    ) {JOGLGLES.gl3().glBindSampler(unit, sampler);}
    // C function void glSamplerParameteri ( GLuint sampler, GLenum pname, GLint param )
    public static void glSamplerParameteri(
        int sampler,
        int pname,
        int param
    ) {JOGLGLES.gl3().glSamplerParameteri(sampler, pname, param);}
    // C function void glSamplerParameteriv ( GLuint sampler, GLenum pname, const GLint *param )
    public static void glSamplerParameteriv(
        int sampler,
        int pname,
        int[] param,
        int offset
    ) {JOGLGLES.gl3().glSamplerParameteriv(sampler, pname, param, offset);}
    // C function void glSamplerParameteriv ( GLuint sampler, GLenum pname, const GLint *param )
    public static void glSamplerParameteriv(
        int sampler,
        int pname,
        java.nio.IntBuffer param
    ) {JOGLGLES.gl3().glSamplerParameteriv(sampler, pname, param);}
    // C function void glSamplerParameterf ( GLuint sampler, GLenum pname, GLfloat param )
    public static void glSamplerParameterf(
        int sampler,
        int pname,
        float param
    ) {JOGLGLES.gl3().glSamplerParameterf(sampler, pname, param);}
    // C function void glSamplerParameterfv ( GLuint sampler, GLenum pname, const GLfloat *param )
    public static void glSamplerParameterfv(
        int sampler,
        int pname,
        float[] param,
        int offset
    ) {JOGLGLES.gl3().glSamplerParameterfv(sampler, pname, param, offset);}
    // C function void glSamplerParameterfv ( GLuint sampler, GLenum pname, const GLfloat *param )
    public static void glSamplerParameterfv(
        int sampler,
        int pname,
        java.nio.FloatBuffer param
    ) {JOGLGLES.gl3().glSamplerParameterfv(sampler, pname, param);}
    // C function void glGetSamplerParameteriv ( GLuint sampler, GLenum pname, GLint *params )
    public static void glGetSamplerParameteriv(
        int sampler,
        int pname,
        int[] params,
        int offset
    ) {JOGLGLES.gl3().glGetSamplerParameteriv(sampler, pname, params, offset);}
    // C function void glGetSamplerParameteriv ( GLuint sampler, GLenum pname, GLint *params )
    public static void glGetSamplerParameteriv(
        int sampler,
        int pname,
        java.nio.IntBuffer params
    ) {JOGLGLES.gl3().glGetSamplerParameteriv(sampler, pname, params);}
    // C function void glGetSamplerParameterfv ( GLuint sampler, GLenum pname, GLfloat *params )
    public static void glGetSamplerParameterfv(
        int sampler,
        int pname,
        float[] params,
        int offset
    ) {JOGLGLES.gl3().glGetSamplerParameterfv(sampler, pname, params, offset);}
    // C function void glGetSamplerParameterfv ( GLuint sampler, GLenum pname, GLfloat *params )
    public static void glGetSamplerParameterfv(
        int sampler,
        int pname,
        java.nio.FloatBuffer params
    ) {JOGLGLES.gl3().glGetSamplerParameterfv(sampler, pname, params);}
    // C function void glVertexAttribDivisor ( GLuint index, GLuint divisor )
    public static void glVertexAttribDivisor(
        int index,
        int divisor
    ) {JOGLGLES.gl3().glVertexAttribDivisor(index, divisor);}
    // C function void glBindTransformFeedback ( GLenum target, GLuint id )
    public static void glBindTransformFeedback(
        int target,
        int id
    ) {JOGLGLES.gl2().glBindTransformFeedbackNV(target, id);}
    // C function void glDeleteTransformFeedbacks ( GLsizei n, const GLuint *ids )
    public static void glDeleteTransformFeedbacks(
        int n,
        int[] ids,
        int offset
    ) {JOGLGLES.gl2().glDeleteTransformFeedbacksNV(n, ids, offset);}
    // C function void glDeleteTransformFeedbacks ( GLsizei n, const GLuint *ids )
    public static void glDeleteTransformFeedbacks(
        int n,
        java.nio.IntBuffer ids
    ) {JOGLGLES.gl2().glDeleteTransformFeedbacksNV(n, ids);}
    // C function void glGenTransformFeedbacks ( GLsizei n, GLuint *ids )
    public static void glGenTransformFeedbacks(
        int n,
        int[] ids,
        int offset
    ) {JOGLGLES.gl2().glGenTransformFeedbacksNV(n, ids, offset);}
    // C function void glGenTransformFeedbacks ( GLsizei n, GLuint *ids )
    public static void glGenTransformFeedbacks(
        int n,
        java.nio.IntBuffer ids
    ) {JOGLGLES.gl2().glGenTransformFeedbacksNV(n, ids);}
    // C function GLboolean glIsTransformFeedback ( GLuint id )
    public static boolean glIsTransformFeedback(
        int id
    ) {return JOGLGLES.gl2().glIsTransformFeedbackNV(id);}
    // C function void glPauseTransformFeedback ( void )
    public static void glPauseTransformFeedback(
    ) {JOGLGLES.gl2().glPauseTransformFeedbackNV();}
    // C function void glResumeTransformFeedback ( void )
    public static void glResumeTransformFeedback(
    ) { JOGLGLES.gl2().glResumeTransformFeedbackNV();}
    // C function void glGetProgramBinary ( GLuint program, GLsizei bufSize, GLsizei *length, GLenum *binaryFormat, GLvoid *binary )
    public static void glGetProgramBinary(
        int program,
        int bufSize,
        int[] length,
        int lengthOffset,
        int[] binaryFormat,
        int binaryFormatOffset,
        java.nio.Buffer binary
    ) {JOGLGLES.gl2().glGetProgramBinary(program, bufSize, length, lengthOffset, binaryFormat, binaryFormatOffset, binary);}
    // C function void glGetProgramBinary ( GLuint program, GLsizei bufSize, GLsizei *length, GLenum *binaryFormat, GLvoid *binary )
    public static void glGetProgramBinary(
        int program,
        int bufSize,
        java.nio.IntBuffer length,
        java.nio.IntBuffer binaryFormat,
        java.nio.Buffer binary
    ) {JOGLGLES.gl2().glGetProgramBinary(program, bufSize, length, binaryFormat, binary);}
    // C function void glProgramBinary ( GLuint program, GLenum binaryFormat, const GLvoid *binary, GLsizei length )
    public static void glProgramBinary(
        int program,
        int binaryFormat,
        java.nio.Buffer binary,
        int length
    ) {JOGLGLES.gl2().glProgramBinary(program, binaryFormat, binary, length);}
    // C function void glProgramParameteri ( GLuint program, GLenum pname, GLint value )
    public static void glProgramParameteri(
        int program,
        int pname,
        int value
    ) {JOGLGLES.gl2().glProgramParameteriARB(program, pname, value);}
    // C function void glInvalidateFramebuffer ( GLenum target, GLsizei numAttachments, const GLenum *attachments )
    public static void glInvalidateFramebuffer(
        int target,
        int numAttachments,
        int[] attachments,
        int offset
    ) {JOGLGLES.gl4().glInvalidateFramebuffer(target, numAttachments, attachments, offset);}
    // C function void glInvalidateFramebuffer ( GLenum target, GLsizei numAttachments, const GLenum *attachments )
    public static void glInvalidateFramebuffer(
        int target,
        int numAttachments,
        java.nio.IntBuffer attachments
    ) {JOGLGLES.gl4().glInvalidateFramebuffer(target, numAttachments, attachments);}
    // C function void glInvalidateSubFramebuffer ( GLenum target, GLsizei numAttachments, const GLenum *attachments, GLint x, GLint y, GLsizei width, GLsizei height )
    public static void glInvalidateSubFramebuffer(
        int target,
        int numAttachments,
        int[] attachments,
        int offset,
        int x,
        int y,
        int width,
        int height
    ) {JOGLGLES.gl4().glInvalidateSubFramebuffer(target, numAttachments, attachments, offset, x, y, width, height);}
    // C function void glInvalidateSubFramebuffer ( GLenum target, GLsizei numAttachments, const GLenum *attachments, GLint x, GLint y, GLsizei width, GLsizei height )
    public static void glInvalidateSubFramebuffer(
        int target,
        int numAttachments,
        java.nio.IntBuffer attachments,
        int x,
        int y,
        int width,
        int height
    ) {JOGLGLES.gl4().glInvalidateSubFramebuffer(target, numAttachments, attachments, x, y, width, height);}
    // C function void glTexStorage2D ( GLenum target, GLsizei levels, GLenum internalformat, GLsizei width, GLsizei height )
    public static void glTexStorage2D(
        int target,
        int levels,
        int internalformat,
        int width,
        int height
    ) {JOGLGLES.gl2().glTexStorage2D(target, levels, internalformat, width, height);}
    // C function void glTexStorage3D ( GLenum target, GLsizei levels, GLenum internalformat, GLsizei width, GLsizei height, GLsizei depth )
    public static void glTexStorage3D(
        int target,
        int levels,
        int internalformat,
        int width,
        int height,
        int depth
    ) {JOGLGLES.gl2().glTexStorage3D(target, levels, internalformat, width, height, depth);}
    // C function void glGetInternalformativ ( GLenum target, GLenum internalformat, GLenum pname, GLsizei bufSize, GLint *params )
    public static void glGetInternalformativ(
        int target,
        int internalformat,
        int pname,
        int bufSize,
        int[] params,
        int offset
    ) {JOGLGLES.gl2().glGetInternalformativ(target, internalformat, pname, bufSize, params, offset);}
    // C function void glGetInternalformativ ( GLenum target, GLenum internalformat, GLenum pname, GLsizei bufSize, GLint *params )
    public static void glGetInternalformativ(
        int target,
        int internalformat,
        int pname,
        int bufSize,
        java.nio.IntBuffer params
    ) {JOGLGLES.gl2().glGetInternalformativ(target, internalformat, pname, bufSize, params);}

}

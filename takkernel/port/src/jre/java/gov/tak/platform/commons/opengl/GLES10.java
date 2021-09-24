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

import java.nio.Buffer;

public class GLES10 implements GLES10Constants {
    native private static void _nativeClassInit();

    static {
        _nativeClassInit();
    }

    private static Buffer _colorPointer;
    private static Buffer _normalPointer;
    private static Buffer _texCoordPointer;
    private static Buffer _vertexPointer;

    // C function void glActiveTexture ( GLenum texture )

    public static native void glActiveTexture(int texture);

    // C function void glAlphaFunc ( GLenum func, GLclampf ref )

    public static native void glAlphaFunc(int func, float ref);

    // C function void glAlphaFuncx ( GLenum func, GLclampx ref )

    public static native void glAlphaFuncx(int func, int ref);

    // C function void glBindTexture ( GLenum target, GLuint texture )

    public static native void glBindTexture(int target, int texture);

    // C function void glBlendFunc ( GLenum sfactor, GLenum dfactor )

    public static native void glBlendFunc(int sfactor, int dfactor);

    // C function void glClear ( GLbitfield mask )

    public static native void glClear(int mask);

    // C function void glClearColor ( GLclampf red, GLclampf green, GLclampf blue, GLclampf alpha )

    public static native void glClearColor(float red, float green, float blue, float alpha);

    // C function void glClearColorx ( GLclampx red, GLclampx green, GLclampx blue, GLclampx alpha )

    public static native void glClearColorx(int red, int green, int blue, int alpha);

    // C function void glClearDepthf ( GLclampf depth )

    public static native void glClearDepthf(float depth);

    // C function void glClearDepthx ( GLclampx depth )

    public static native void glClearDepthx(int depth);

    // C function void glClearStencil ( GLint s )

    public static native void glClearStencil(int s);

    // C function void glClientActiveTexture ( GLenum texture )

    public static native void glClientActiveTexture(int texture);

    // C function void glColor4f ( GLfloat red, GLfloat green, GLfloat blue, GLfloat alpha )

    public static native void glColor4f(float red, float green, float blue, float alpha);

    // C function void glColor4x ( GLfixed red, GLfixed green, GLfixed blue, GLfixed alpha )

    public static native void glColor4x(int red, int green, int blue, int alpha);

    // C function void glColorMask ( GLboolean red, GLboolean green, GLboolean blue, GLboolean alpha )

    public static native void glColorMask(boolean red, boolean green, boolean blue, boolean alpha);

    // C function void glColorPointer ( GLint size, GLenum type, GLsizei stride, const GLvoid *pointer )

    private static native void glColorPointerBounds(int size, int type, int stride, java.nio.Buffer pointer,
                                                    int remaining);

    public static void glColorPointer(int size, int type, int stride, java.nio.Buffer pointer) {
        glColorPointerBounds(size, type, stride, pointer, pointer.remaining());
        if ((size == 4) && ((type == GL_FLOAT) || (type == GL_UNSIGNED_BYTE) || (type == GL_FIXED))
                && (stride >= 0)) {
            _colorPointer = pointer;
        }
    }

    // C function void glCompressedTexImage2D ( GLenum target, GLint level, GLenum internalformat, GLsizei width, GLsizei height, GLint border, GLsizei imageSize, const GLvoid *data )

    public static native void glCompressedTexImage2D(int target, int level, int internalformat, int width,
                                                     int height, int border, int imageSize, java.nio.Buffer data);

    // C function void glCompressedTexSubImage2D ( GLenum target, GLint level, GLint xoffset, GLint yoffset, GLsizei width, GLsizei height, GLenum format, GLsizei imageSize, const GLvoid *data )

    public static native void glCompressedTexSubImage2D(int target, int level, int xoffset, int yoffset, int width,
                                                        int height, int format, int imageSize, java.nio.Buffer data);

    // C function void glCopyTexImage2D ( GLenum target, GLint level, GLenum internalformat, GLint x, GLint y, GLsizei width, GLsizei height, GLint border )

    public static native void glCopyTexImage2D(int target, int level, int internalformat, int x, int y, int width,
                                               int height, int border);

    // C function void glCopyTexSubImage2D ( GLenum target, GLint level, GLint xoffset, GLint yoffset, GLint x, GLint y, GLsizei width, GLsizei height )

    public static native void glCopyTexSubImage2D(int target, int level, int xoffset, int yoffset, int x, int y,
                                                  int width, int height);

    // C function void glCullFace ( GLenum mode )

    public static native void glCullFace(int mode);

    // C function void glDeleteTextures ( GLsizei n, const GLuint *textures )

    public static native void glDeleteTextures(int n, int[] textures, int offset);

    // C function void glDeleteTextures ( GLsizei n, const GLuint *textures )

    public static native void glDeleteTextures(int n, java.nio.IntBuffer textures);

    // C function void glDepthFunc ( GLenum func )

    public static native void glDepthFunc(int func);

    // C function void glDepthMask ( GLboolean flag )

    public static native void glDepthMask(boolean flag);

    // C function void glDepthRangef ( GLclampf zNear, GLclampf zFar )

    public static native void glDepthRangef(float zNear, float zFar);

    // C function void glDepthRangex ( GLclampx zNear, GLclampx zFar )

    public static native void glDepthRangex(int zNear, int zFar);

    // C function void glDisable ( GLenum cap )

    public static native void glDisable(int cap);

    // C function void glDisableClientState ( GLenum array )

    public static native void glDisableClientState(int array);

    // C function void glDrawArrays ( GLenum mode, GLint first, GLsizei count )

    public static native void glDrawArrays(int mode, int first, int count);

    // C function void glDrawElements ( GLenum mode, GLsizei count, GLenum type, const GLvoid *indices )

    public static native void glDrawElements(int mode, int count, int type, java.nio.Buffer indices);

    // C function void glEnable ( GLenum cap )

    public static native void glEnable(int cap);

    // C function void glEnableClientState ( GLenum array )

    public static native void glEnableClientState(int array);

    // C function void glFinish ( void )

    public static native void glFinish();

    // C function void glFlush ( void )

    public static native void glFlush();

    // C function void glFogf ( GLenum pname, GLfloat param )

    public static native void glFogf(int pname, float param);

    // C function void glFogfv ( GLenum pname, const GLfloat *params )

    public static native void glFogfv(int pname, float[] params, int offset);

    // C function void glFogfv ( GLenum pname, const GLfloat *params )

    public static native void glFogfv(int pname, java.nio.FloatBuffer params);

    // C function void glFogx ( GLenum pname, GLfixed param )

    public static native void glFogx(int pname, int param);

    // C function void glFogxv ( GLenum pname, const GLfixed *params )

    public static native void glFogxv(int pname, int[] params, int offset);

    // C function void glFogxv ( GLenum pname, const GLfixed *params )

    public static native void glFogxv(int pname, java.nio.IntBuffer params);

    // C function void glFrontFace ( GLenum mode )

    public static native void glFrontFace(int mode);

    // C function void glFrustumf ( GLfloat left, GLfloat right, GLfloat bottom, GLfloat top, GLfloat zNear, GLfloat zFar )

    public static native void glFrustumf(float left, float right, float bottom, float top, float zNear, float zFar);

    // C function void glFrustumx ( GLfixed left, GLfixed right, GLfixed bottom, GLfixed top, GLfixed zNear, GLfixed zFar )

    public static native void glFrustumx(int left, int right, int bottom, int top, int zNear, int zFar);

    // C function void glGenTextures ( GLsizei n, GLuint *textures )

    public static native void glGenTextures(int n, int[] textures, int offset);

    // C function void glGenTextures ( GLsizei n, GLuint *textures )

    public static native void glGenTextures(int n, java.nio.IntBuffer textures);

    // C function GLenum glGetError ( void )

    public static native int glGetError();

    // C function void glGetIntegerv ( GLenum pname, GLint *params )

    public static native void glGetIntegerv(int pname, int[] params, int offset);

    // C function void glGetIntegerv ( GLenum pname, GLint *params )

    public static native void glGetIntegerv(int pname, java.nio.IntBuffer params);

    // C function const GLubyte * glGetString ( GLenum name )

    public static native String glGetString(int name);
    // C function void glHint ( GLenum target, GLenum mode )

    public static native void glHint(int target, int mode);

    // C function void glLightModelf ( GLenum pname, GLfloat param )

    public static native void glLightModelf(int pname, float param);

    // C function void glLightModelfv ( GLenum pname, const GLfloat *params )

    public static native void glLightModelfv(int pname, float[] params, int offset);

    // C function void glLightModelfv ( GLenum pname, const GLfloat *params )

    public static native void glLightModelfv(int pname, java.nio.FloatBuffer params);

    // C function void glLightModelx ( GLenum pname, GLfixed param )

    public static native void glLightModelx(int pname, int param);

    // C function void glLightModelxv ( GLenum pname, const GLfixed *params )

    public static native void glLightModelxv(int pname, int[] params, int offset);

    // C function void glLightModelxv ( GLenum pname, const GLfixed *params )

    public static native void glLightModelxv(int pname, java.nio.IntBuffer params);

    // C function void glLightf ( GLenum light, GLenum pname, GLfloat param )

    public static native void glLightf(int light, int pname, float param);

    // C function void glLightfv ( GLenum light, GLenum pname, const GLfloat *params )

    public static native void glLightfv(int light, int pname, float[] params, int offset);

    // C function void glLightfv ( GLenum light, GLenum pname, const GLfloat *params )

    public static native void glLightfv(int light, int pname, java.nio.FloatBuffer params);

    // C function void glLightx ( GLenum light, GLenum pname, GLfixed param )

    public static native void glLightx(int light, int pname, int param);

    // C function void glLightxv ( GLenum light, GLenum pname, const GLfixed *params )

    public static native void glLightxv(int light, int pname, int[] params, int offset);

    // C function void glLightxv ( GLenum light, GLenum pname, const GLfixed *params )

    public static native void glLightxv(int light, int pname, java.nio.IntBuffer params);

    // C function void glLineWidth ( GLfloat width )

    public static native void glLineWidth(float width);

    // C function void glLineWidthx ( GLfixed width )

    public static native void glLineWidthx(int width);

    // C function void glLoadIdentity ( void )

    public static native void glLoadIdentity();

    // C function void glLoadMatrixf ( const GLfloat *m )

    public static native void glLoadMatrixf(float[] m, int offset);

    // C function void glLoadMatrixf ( const GLfloat *m )

    public static native void glLoadMatrixf(java.nio.FloatBuffer m);

    // C function void glLoadMatrixx ( const GLfixed *m )

    public static native void glLoadMatrixx(int[] m, int offset);

    // C function void glLoadMatrixx ( const GLfixed *m )

    public static native void glLoadMatrixx(java.nio.IntBuffer m);

    // C function void glLogicOp ( GLenum opcode )

    public static native void glLogicOp(int opcode);

    // C function void glMaterialf ( GLenum face, GLenum pname, GLfloat param )

    public static native void glMaterialf(int face, int pname, float param);

    // C function void glMaterialfv ( GLenum face, GLenum pname, const GLfloat *params )

    public static native void glMaterialfv(int face, int pname, float[] params, int offset);

    // C function void glMaterialfv ( GLenum face, GLenum pname, const GLfloat *params )

    public static native void glMaterialfv(int face, int pname, java.nio.FloatBuffer params);

    // C function void glMaterialx ( GLenum face, GLenum pname, GLfixed param )

    public static native void glMaterialx(int face, int pname, int param);

    // C function void glMaterialxv ( GLenum face, GLenum pname, const GLfixed *params )

    public static native void glMaterialxv(int face, int pname, int[] params, int offset);

    // C function void glMaterialxv ( GLenum face, GLenum pname, const GLfixed *params )

    public static native void glMaterialxv(int face, int pname, java.nio.IntBuffer params);

    // C function void glMatrixMode ( GLenum mode )

    public static native void glMatrixMode(int mode);

    // C function void glMultMatrixf ( const GLfloat *m )

    public static native void glMultMatrixf(float[] m, int offset);

    // C function void glMultMatrixf ( const GLfloat *m )

    public static native void glMultMatrixf(java.nio.FloatBuffer m);

    // C function void glMultMatrixx ( const GLfixed *m )

    public static native void glMultMatrixx(int[] m, int offset);

    // C function void glMultMatrixx ( const GLfixed *m )

    public static native void glMultMatrixx(java.nio.IntBuffer m);

    // C function void glMultiTexCoord4f ( GLenum target, GLfloat s, GLfloat t, GLfloat r, GLfloat q )

    public static native void glMultiTexCoord4f(int target, float s, float t, float r, float q);

    // C function void glMultiTexCoord4x ( GLenum target, GLfixed s, GLfixed t, GLfixed r, GLfixed q )

    public static native void glMultiTexCoord4x(int target, int s, int t, int r, int q);

    // C function void glNormal3f ( GLfloat nx, GLfloat ny, GLfloat nz )

    public static native void glNormal3f(float nx, float ny, float nz);

    // C function void glNormal3x ( GLfixed nx, GLfixed ny, GLfixed nz )

    public static native void glNormal3x(int nx, int ny, int nz);

    // C function void glNormalPointer ( GLenum type, GLsizei stride, const GLvoid *pointer )

    private static native void glNormalPointerBounds(int type, int stride, java.nio.Buffer pointer, int remaining);

    public static void glNormalPointer(int type, int stride, java.nio.Buffer pointer) {
        glNormalPointerBounds(type, stride, pointer, pointer.remaining());
        if (((type == GL_FLOAT) || (type == GL_BYTE) || (type == GL_SHORT) || (type == GL_FIXED))
                && (stride >= 0)) {
            _normalPointer = pointer;
        }
    }

    // C function void glOrthof ( GLfloat left, GLfloat right, GLfloat bottom, GLfloat top, GLfloat zNear, GLfloat zFar )

    public static native void glOrthof(float left, float right, float bottom, float top, float zNear, float zFar);

    // C function void glOrthox ( GLfixed left, GLfixed right, GLfixed bottom, GLfixed top, GLfixed zNear, GLfixed zFar )

    public static native void glOrthox(int left, int right, int bottom, int top, int zNear, int zFar);

    // C function void glPixelStorei ( GLenum pname, GLint param )

    public static native void glPixelStorei(int pname, int param);

    // C function void glPointSize ( GLfloat size )

    public static native void glPointSize(float size);

    // C function void glPointSizex ( GLfixed size )

    public static native void glPointSizex(int size);

    // C function void glPolygonOffset ( GLfloat factor, GLfloat units )

    public static native void glPolygonOffset(float factor, float units);

    // C function void glPolygonOffsetx ( GLfixed factor, GLfixed units )

    public static native void glPolygonOffsetx(int factor, int units);

    // C function void glPopMatrix ( void )

    public static native void glPopMatrix();

    // C function void glPushMatrix ( void )

    public static native void glPushMatrix();

    // C function void glReadPixels ( GLint x, GLint y, GLsizei width, GLsizei height, GLenum format, GLenum type, GLvoid *pixels )

    public static native void glReadPixels(int x, int y, int width, int height, int format, int type,
                                           java.nio.Buffer pixels);

    // C function void glRotatef ( GLfloat angle, GLfloat x, GLfloat y, GLfloat z )

    public static native void glRotatef(float angle, float x, float y, float z);

    // C function void glRotatex ( GLfixed angle, GLfixed x, GLfixed y, GLfixed z )

    public static native void glRotatex(int angle, int x, int y, int z);

    // C function void glSampleCoverage ( GLclampf value, GLboolean invert )

    public static native void glSampleCoverage(float value, boolean invert);

    // C function void glSampleCoveragex ( GLclampx value, GLboolean invert )

    public static native void glSampleCoveragex(int value, boolean invert);

    // C function void glScalef ( GLfloat x, GLfloat y, GLfloat z )

    public static native void glScalef(float x, float y, float z);

    // C function void glScalex ( GLfixed x, GLfixed y, GLfixed z )

    public static native void glScalex(int x, int y, int z);

    // C function void glScissor ( GLint x, GLint y, GLsizei width, GLsizei height )

    public static native void glScissor(int x, int y, int width, int height);

    // C function void glShadeModel ( GLenum mode )

    public static native void glShadeModel(int mode);

    // C function void glStencilFunc ( GLenum func, GLint ref, GLuint mask )

    public static native void glStencilFunc(int func, int ref, int mask);

    // C function void glStencilMask ( GLuint mask )

    public static native void glStencilMask(int mask);

    // C function void glStencilOp ( GLenum fail, GLenum zfail, GLenum zpass )

    public static native void glStencilOp(int fail, int zfail, int zpass);

    // C function void glTexCoordPointer ( GLint size, GLenum type, GLsizei stride, const GLvoid *pointer )

    private static native void glTexCoordPointerBounds(int size, int type, int stride, java.nio.Buffer pointer,
                                                       int remaining);

    public static void glTexCoordPointer(int size, int type, int stride, java.nio.Buffer pointer) {
        glTexCoordPointerBounds(size, type, stride, pointer, pointer.remaining());
        if (((size == 2) || (size == 3) || (size == 4))
                && ((type == GL_FLOAT) || (type == GL_BYTE) || (type == GL_SHORT) || (type == GL_FIXED))
                && (stride >= 0)) {
            _texCoordPointer = pointer;
        }
    }

    // C function void glTexEnvf ( GLenum target, GLenum pname, GLfloat param )

    public static native void glTexEnvf(int target, int pname, float param);

    // C function void glTexEnvfv ( GLenum target, GLenum pname, const GLfloat *params )

    public static native void glTexEnvfv(int target, int pname, float[] params, int offset);

    // C function void glTexEnvfv ( GLenum target, GLenum pname, const GLfloat *params )

    public static native void glTexEnvfv(int target, int pname, java.nio.FloatBuffer params);

    // C function void glTexEnvx ( GLenum target, GLenum pname, GLfixed param )

    public static native void glTexEnvx(int target, int pname, int param);

    // C function void glTexEnvxv ( GLenum target, GLenum pname, const GLfixed *params )

    public static native void glTexEnvxv(int target, int pname, int[] params, int offset);

    // C function void glTexEnvxv ( GLenum target, GLenum pname, const GLfixed *params )

    public static native void glTexEnvxv(int target, int pname, java.nio.IntBuffer params);

    // C function void glTexImage2D ( GLenum target, GLint level, GLint internalformat, GLsizei width, GLsizei height, GLint border, GLenum format, GLenum type, const GLvoid *pixels )

    public static native void glTexImage2D(int target, int level, int internalformat, int width, int height,
                                           int border, int format, int type, java.nio.Buffer pixels);

    // C function void glTexParameterf ( GLenum target, GLenum pname, GLfloat param )

    public static native void glTexParameterf(int target, int pname, float param);

    // C function void glTexParameterx ( GLenum target, GLenum pname, GLfixed param )

    public static native void glTexParameterx(int target, int pname, int param);

    // C function void glTexSubImage2D ( GLenum target, GLint level, GLint xoffset, GLint yoffset, GLsizei width, GLsizei height, GLenum format, GLenum type, const GLvoid *pixels )

    public static native void glTexSubImage2D(int target, int level, int xoffset, int yoffset, int width,
                                              int height, int format, int type, java.nio.Buffer pixels);

    // C function void glTranslatef ( GLfloat x, GLfloat y, GLfloat z )

    public static native void glTranslatef(float x, float y, float z);

    // C function void glTranslatex ( GLfixed x, GLfixed y, GLfixed z )

    public static native void glTranslatex(int x, int y, int z);

    // C function void glVertexPointer ( GLint size, GLenum type, GLsizei stride, const GLvoid *pointer )

    private static native void glVertexPointerBounds(int size, int type, int stride, java.nio.Buffer pointer,
                                                     int remaining);

    public static void glVertexPointer(int size, int type, int stride, java.nio.Buffer pointer) {
        glVertexPointerBounds(size, type, stride, pointer, pointer.remaining());
        if (((size == 2) || (size == 3) || (size == 4))
                && ((type == GL_FLOAT) || (type == GL_BYTE) || (type == GL_SHORT) || (type == GL_FIXED))
                && (stride >= 0)) {
            _vertexPointer = pointer;
        }
    }

    // C function void glViewport ( GLint x, GLint y, GLsizei width, GLsizei height )

    public static native void glViewport(int x, int y, int width, int height);

}
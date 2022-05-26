
package com.atakmap.opengl;

import android.graphics.Bitmap;
import android.graphics.PointF;
import android.opengl.GLUtils;

import com.atakmap.math.Matrix;
import com.atakmap.math.PointD;

import java.nio.Buffer;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;

import gov.tak.platform.commons.opengl.GLES30;

/**
 * An OpenGL texture or sub-texture.
 *
 */
public final class GLTexture {

    /**
     * Get the OpenGL texture id
     * 
     * @return the texture id or zero if the texture has not been initialized
     */
    public int getTexId() {
        if (_needsApply) {
            _needsApply = false;
            _apply();
        }
        return _id;
    }

    /**
     * Get the width in pixels of the (base-most) texture
     * 
     * @return the texture width in pixels or -1 if the texture memory has not been initialized
     */
    public int getTexWidth() {
        return _width;
    }

    /**
     * Get the height in pixels of the (base-most) texture
     * 
     * @return the texture height in pixels or -1 if the texture memory has not been initialized
     */
    public int getTexHeight() {
        return _height;
    }

    /**
     * Get the T wrap
     * 
     * @return
     */
    public int getWrapT() {
        return _wrapT;
    }

    /**
     * Get the S wrap
     *
     * @return
     */
    public int getWrapS() {
        return _wrapS;
    }

    public int getMagFilter() {
        return _magFilter;
    }

    public int getMinFilter() {
        return _minFilter;
    }

    /**
     * Create base texture
     * 
     * @param width
     * @param height
     * @param config
     */
    public GLTexture(int width, int height, Bitmap.Config config) {
        this(width, height, config, false);
    }

    public GLTexture(int width, int height, Bitmap.Config config, boolean npot) {
        this(width, height, getInternalFormat(config), getType(config), npot);
    }

    public GLTexture(int width, int height, int format, int type) {
        this(width, height, format, type, false);
    }
    public GLTexture(int width, int height, int format, int type, boolean npot) {
        _width = npot ? width : _nextPowerOf2(width);
        _height = npot ? height : _nextPowerOf2(height);
        _format = format;
        _type = type;
    }

    public void init() {
        if (_id == 0) {
            idBuffer[0] = 0;
            GLES30.glGenTextures(1, idBuffer, 0);
            _id = idBuffer[0];
            GLES30.glBindTexture(GLES20FixedPipeline.GL_TEXTURE_2D, _id);
            GLES30.glTexParameterf(GLES20FixedPipeline.GL_TEXTURE_2D,
                    GLES30.GL_TEXTURE_MAG_FILTER, _magFilter);
            GLES30.glTexParameterf(GLES20FixedPipeline.GL_TEXTURE_2D,
                    GLES30.GL_TEXTURE_MIN_FILTER, _minFilter);
            GLES30.glTexParameterf(GLES20FixedPipeline.GL_TEXTURE_2D,
                    GLES30.GL_TEXTURE_WRAP_S, _wrapS);
            GLES30.glTexParameterf(GLES20FixedPipeline.GL_TEXTURE_2D,
                    GLES30.GL_TEXTURE_WRAP_T, _wrapT);
            GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, _format,
                    _width, _height, 0,
                    _format, _type, null);
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0);
        }
    }

    public void load(Bitmap bitmap, int x, int y) {
        if(_id == 0 && x == 0 && y == 0 && bitmap.getWidth() == _width && bitmap.getHeight() == _height) {
            idBuffer[0] = 0;
            GLES30.glGenTextures(1, idBuffer, 0);
            _id = idBuffer[0];
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, _id);
            GLES30.glTexParameterf(GLES30.GL_TEXTURE_2D,
                    GLES30.GL_TEXTURE_MAG_FILTER, _magFilter);
            GLES30.glTexParameterf(GLES30.GL_TEXTURE_2D,
                    GLES30.GL_TEXTURE_MIN_FILTER, _minFilter);
            GLES30.glTexParameterf(GLES30.GL_TEXTURE_2D,
                    GLES30.GL_TEXTURE_WRAP_S, _wrapS);
            GLES30.glTexParameterf(GLES30.GL_TEXTURE_2D,
                    GLES30.GL_TEXTURE_WRAP_T, _wrapT);
            GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, _format, bitmap, _type, 0);
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0);
        } else {
            init();
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, _id);
            GLUtils.texSubImage2D(GLES30.GL_TEXTURE_2D, 0, x, y, bitmap);
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0);
        }
    }

    public void load(Bitmap bitmap) {
        load(bitmap, 0, 0);
    }

    public void load(Buffer data, int x, int y, int w, int h) {
        if(_id == 0 && x == 0 && y == 0 && w == _width && h == _height) {
            idBuffer[0] = 0;
            GLES20FixedPipeline.glGenTextures(1, idBuffer, 0);
            _id = idBuffer[0];
            GLES20FixedPipeline.glBindTexture(GLES20FixedPipeline.GL_TEXTURE_2D, _id);
            GLES20FixedPipeline.glTexParameterf(GLES20FixedPipeline.GL_TEXTURE_2D,
                    GLES20FixedPipeline.GL_TEXTURE_MAG_FILTER, _magFilter);
            GLES20FixedPipeline.glTexParameterf(GLES20FixedPipeline.GL_TEXTURE_2D,
                    GLES20FixedPipeline.GL_TEXTURE_MIN_FILTER, _minFilter);
            GLES20FixedPipeline.glTexParameterf(GLES20FixedPipeline.GL_TEXTURE_2D,
                    GLES20FixedPipeline.GL_TEXTURE_WRAP_S, _wrapS);
            GLES20FixedPipeline.glTexParameterf(GLES20FixedPipeline.GL_TEXTURE_2D,
                    GLES20FixedPipeline.GL_TEXTURE_WRAP_T, _wrapT);
            GLES20FixedPipeline.glTexImage2D(GLES20FixedPipeline.GL_TEXTURE_2D, 0, _format,
                    _width, _height, 0,
                    _format, _type, data);
            GLES20FixedPipeline.glBindTexture(GLES20FixedPipeline.GL_TEXTURE_2D, 0);
        } else {
            this.init();
            GLES20FixedPipeline.glBindTexture(GLES20FixedPipeline.GL_TEXTURE_2D, _id);
            GLES20FixedPipeline.glTexSubImage2D(GLES20FixedPipeline.GL_TEXTURE_2D, 0, x, y, w, h,
                    _format, _type, data);
            GLES20FixedPipeline.glBindTexture(GLES20FixedPipeline.GL_TEXTURE_2D, 0);
        }
    }

    public void setMinFilter(int minFilter) {
        _minFilter = minFilter;
        _needsApply = true;
    }

    public void setMagFilter(int magFilter) {
        _magFilter = magFilter;
        _needsApply = true;
    }

    public void setWrapS(int wrapS) {
        _wrapS = wrapS;
        _needsApply = true;
    }

    public void setWrapT(int wrapT) {
        _wrapT = wrapT;
        _needsApply = true;
    }

    /**
     * Releases the texture data. Only the object responsible for creating this texture should call
     * this. Any other entity that uses the texture should call recycle().
     */
    public void release() {
        idBuffer[0] = _id;
        GLES20FixedPipeline.glDeleteTextures(1, idBuffer, 0);
        _id = 0;
    }

    public int getType() {
        return this._type;
    }

    public int getFormat() {
        return this._format;
    }

    /**
     * Draws this texture in the current context using the specified texture and vertex coordinates.
     * 
     * @param type The type of the texture and vertex coordinates (e.g. <code>GL_FLOAT</code>)
     * @param textureCoordinates The texture coordinates
     * @param vertexCoordinates The vertex coordinates
     */
    public void draw(int numCoords, int type, Buffer textureCoordinates, Buffer vertexCoordinates) {
        this.draw(numCoords, type, textureCoordinates, type, vertexCoordinates);
    }

    public void draw(int numCoords, int texType, Buffer textureCoordinates, int vertType,
            Buffer vertexCoordinates) {
        draw(this._id, GLES20FixedPipeline.GL_TRIANGLE_FAN, numCoords, texType, textureCoordinates,
                vertType, vertexCoordinates);
    }

    public static void draw(int texId, int mode, int numCoords, int texType,
            Buffer textureCoordinates, int vertType, Buffer vertexCoordinates) {
        draw(texId, mode, numCoords,
             2, texType, textureCoordinates,
             2, vertType, vertexCoordinates);
    }
    public static void draw(int texId, int mode, int numCoords, int texSize, int texType,
            Buffer textureCoordinates, int vertSize, int vertType, Buffer vertexCoordinates) {
        
        draw(texId,
             mode,
             numCoords,
             texSize, texType, textureCoordinates,
             vertSize, vertType, vertexCoordinates,
             1f, 1f, 1f, 1f);
    }

    public static void draw(int texId, int mode, int numCoords, int texSize, int texType,
                            Buffer textureCoordinates, int vertSize, int vertType, Buffer vertexCoordinates,
                            float red, float green, float blue, float alpha)
    {
        if(texId == 0)
        {
            return;
        }
        GLES20FixedPipeline.glEnableClientState(GLES20FixedPipeline.GL_VERTEX_ARRAY);
        GLES20FixedPipeline.glEnableClientState(GLES20FixedPipeline.GL_TEXTURE_COORD_ARRAY);
        GLES20FixedPipeline.glEnable(GLES20FixedPipeline.GL_BLEND);
        GLES20FixedPipeline.glBlendFunc(GLES20FixedPipeline.GL_SRC_ALPHA, GLES20FixedPipeline.GL_ONE_MINUS_SRC_ALPHA);
        GLES20FixedPipeline.glVertexPointer(vertSize, vertType, 0, vertexCoordinates);
        GLES20FixedPipeline.glTexCoordPointer(texSize, texType, 0, textureCoordinates);
        GLES20FixedPipeline.glBindTexture(GLES20FixedPipeline.GL_TEXTURE_2D, texId);
        GLES20FixedPipeline.glColor4f(red, green, blue, alpha);
        GLES20FixedPipeline.glDrawArrays(mode, 0, numCoords);
        GLES20FixedPipeline.glDisableClientState(GLES20FixedPipeline.GL_VERTEX_ARRAY);
        GLES20FixedPipeline.glDisableClientState(GLES20FixedPipeline.GL_TEXTURE_COORD_ARRAY);
        GLES20FixedPipeline.glDisable(GLES20FixedPipeline.GL_BLEND);
    }

    public static void draw(int texId, int mode, int numCoords, int texType,
            Buffer textureCoordinates, int vertType, Buffer vertexCoordinates, int idxType,
            Buffer indices) {
        
        draw(texId, mode, numCoords, 2, texType, textureCoordinates, 2, vertType, vertexCoordinates, idxType, indices);
    }

    public static void draw(int texId, int mode, int numCoords, int texSize, int texType,
            Buffer textureCoordinates, int vertSize, int vertType, Buffer vertexCoordinates, int idxType,
            Buffer indices) {
            
        draw(texId,
             mode,
             numCoords,
             texSize, texType, textureCoordinates,
             vertSize, vertType, vertexCoordinates,
             idxType, indices,
             1f, 1f, 1f, 1f);
    }

    public static void draw(int texId, int mode, int numCoords, int texSize, int texType,
                            Buffer textureCoordinates, int vertSize, int vertType, Buffer vertexCoordinates,
                            int idxType, Buffer indices,
                            float red, float green, float blue, float alpha)
    {
        if(texId == 0)
        {
            return;
        }
        GLES20FixedPipeline.glEnableClientState(GLES20FixedPipeline.GL_VERTEX_ARRAY);
        GLES20FixedPipeline.glEnableClientState(GLES20FixedPipeline.GL_TEXTURE_COORD_ARRAY);
        GLES20FixedPipeline.glEnable(GLES20FixedPipeline.GL_BLEND);
        GLES20FixedPipeline.glBlendFunc(GLES20FixedPipeline.GL_SRC_ALPHA, GLES20FixedPipeline.GL_ONE_MINUS_SRC_ALPHA);
        GLES20FixedPipeline.glVertexPointer(vertSize, vertType, 0, vertexCoordinates);
        GLES20FixedPipeline.glTexCoordPointer(texSize, texType, 0, textureCoordinates);
        GLES20FixedPipeline.glBindTexture(GLES20FixedPipeline.GL_TEXTURE_2D, texId);
        GLES20FixedPipeline.glColor4f(red, green, blue, alpha);
        GLES20FixedPipeline.glDrawElements(mode, numCoords, idxType, indices);
        GLES20FixedPipeline.glDisableClientState(GLES20FixedPipeline.GL_VERTEX_ARRAY);
        GLES20FixedPipeline.glDisableClientState(GLES20FixedPipeline.GL_TEXTURE_COORD_ARRAY);
        GLES20FixedPipeline.glDisable(GLES20FixedPipeline.GL_BLEND);
    }

    private int _id;
    private int _format, _type;
    private int _width, _height;
    private int _minFilter = GLES20FixedPipeline.GL_NEAREST;
    private int _magFilter = GLES20FixedPipeline.GL_LINEAR;
    private int _wrapS = GLES20FixedPipeline.GL_CLAMP_TO_EDGE;
    private int _wrapT = GLES20FixedPipeline.GL_CLAMP_TO_EDGE;
    private boolean _needsApply;

    private static int[] idBuffer = new int[1];

    private void _apply() {
        idBuffer[0] = 0;
        GLES20FixedPipeline.glGetIntegerv(GLES20FixedPipeline.GL_TEXTURE_BINDING_2D, idBuffer, 0);
        GLES20FixedPipeline.glBindTexture(GLES20FixedPipeline.GL_TEXTURE_2D, _id);
        GLES20FixedPipeline.glTexParameteri(GLES20FixedPipeline.GL_TEXTURE_2D,
                GLES20FixedPipeline.GL_TEXTURE_MAG_FILTER, _magFilter);
        GLES20FixedPipeline.glTexParameteri(GLES20FixedPipeline.GL_TEXTURE_2D,
                GLES20FixedPipeline.GL_TEXTURE_MIN_FILTER, _minFilter);
        GLES20FixedPipeline.glTexParameteri(GLES20FixedPipeline.GL_TEXTURE_2D,
                GLES20FixedPipeline.GL_TEXTURE_WRAP_S, _wrapS);
        GLES20FixedPipeline.glTexParameteri(GLES20FixedPipeline.GL_TEXTURE_2D,
                GLES20FixedPipeline.GL_TEXTURE_WRAP_T, _wrapT);
        GLES20FixedPipeline.glBindTexture(GLES20FixedPipeline.GL_TEXTURE_2D, idBuffer[0]);
    }

    public static int getInternalFormat(Bitmap.Config config) {
        int internalFormat = 0;
        if (config.equals(Bitmap.Config.RGB_565)) {
            internalFormat = GLES20FixedPipeline.GL_RGB;
        }
        else if (config.equals(Bitmap.Config.ARGB_8888)) {
            internalFormat = GLES20FixedPipeline.GL_RGBA;
        }
        else if (config.equals(Bitmap.Config.ARGB_4444)) {
            internalFormat = GLES20FixedPipeline.GL_RGBA;
        }
        return internalFormat;
    }

    public static int getType(Bitmap.Config config) {
        int type = 0;
        if (config.equals(Bitmap.Config.RGB_565)) {
            type = GLES20FixedPipeline.GL_UNSIGNED_SHORT_5_6_5;
        }
        else if (config.equals(Bitmap.Config.ARGB_8888)) {
            type = GLES20FixedPipeline.GL_UNSIGNED_BYTE;
        }
        else if (config.equals(Bitmap.Config.ARGB_4444)) {
            type = GLES20FixedPipeline.GL_UNSIGNED_SHORT_4_4_4_4;
        }
        return type;
    }

    private static int _nextPowerOf2(int value) {
        --value;
        value = (value >> 1) | value;
        value = (value >> 2) | value;
        value = (value >> 4) | value;
        value = (value >> 8) | value;
        value = (value >> 16) | value;
        ++value;
        return value;
    }

    public static int getNumQuadMeshVertices(int numCellsX, int numCellsY) {
        return (numCellsX + 1) * (numCellsY + 1);
    }

    /**
     * This is a helper array that is used to avoid a ton of put operations in the
     * createQuadMeshTexCoords methods
     */
    private static final float [] floatArray = new float[256];

    /**
     * This is does the same thing as
     * {@link #createQuadMeshTexCoords(PointF, PointF, PointF, PointF, int, int, FloatBuffer)}
     * but takes in the coordinates equal to calling createQuadMeshTexCoords(new PointF(minX, minY),
     * new PointF(maxX, minY), new PointF(maxX, maxY), new PointF(minX, maxY), numCellsX, numCellsY,
     * buffer)
     * @param minX The minimum x value
     * @param minY The minimum y value
     * @param maxX The maximum x value
     * @param maxY The maximum y value
     * @param numCellsX The number of cells across minX to maxX
     * @param numCellsY The number of cells across minY to maxY
     * @param buffer The buffer to store the texture coordinates inside
     */
    public static void createQuadMeshTexCoords(float minX, float minY, float maxX, float maxY,
                int numCellsX, int numCellsY, FloatBuffer buffer) {
        Matrix gridToTexCoord = Matrix.mapQuads(0, 0, // grid UL
                    numCellsX, 0, // grid UR
                    numCellsX, numCellsY, // gridLR
                    0, numCellsY, // gridLL
                    minX, minY,
                    maxX, minY,
                    maxX, maxY,
                    minX, maxY);

        PointD gridCoord = new PointD(0, 0);
        PointD texCoord = new PointD(0, 0);

        synchronized( floatArray )
        {
            int index = 0;
            for( int y = 0; y <= numCellsY; y++ )
            {
                gridCoord.y = y;
                for( int x = 0; x <= numCellsX; x++ )
                {
                    gridCoord.x = x;

                    gridToTexCoord.transform( gridCoord, texCoord );

                    floatArray[index] = (float) texCoord.x;
                    ++index;
                    floatArray[index] = (float) texCoord.y;
                    ++index;
                    if( index == floatArray.length )
                    {
                        buffer.put( floatArray );
                        index = 0;
                    }
                }
            }
            if( index > 0 )
            {
                buffer.put( floatArray, 0, index );
            }
        }
    }

    public static void createQuadMeshTexCoords(PointF upperLeft, PointF upperRight,
            PointF lowerRight, PointF lowerLeft, int numCellsX, int numCellsY, FloatBuffer buffer) {
        Matrix gridToTexCoord = Matrix.mapQuads(0, 0, // grid UL
                numCellsX, 0, // grid UR
                numCellsX, numCellsY, // gridLR
                0, numCellsY, // gridLL
                upperLeft.x, upperLeft.y,
                upperRight.x, upperRight.y,
                lowerRight.x, lowerRight.y,
                lowerLeft.x, lowerLeft.y);

        PointD gridCoord = new PointD(0, 0);
        PointD texCoord = new PointD(0, 0);

        synchronized( floatArray )
        {
            int index = 0;
            for( int y = 0; y <= numCellsY; y++ )
            {
                gridCoord.y = y;
                for( int x = 0; x <= numCellsX; x++ )
                {
                    gridCoord.x = x;

                    gridToTexCoord.transform( gridCoord, texCoord );

                    floatArray[index] = (float) texCoord.x;
                    ++index;
                    floatArray[index] = (float) texCoord.y;
                    ++index;
                    if( index == floatArray.length )
                    {
                        buffer.put( floatArray );
                        index = 0;
                    }
                }
            }
            if( index > 0 )
            {
                buffer.put( floatArray, 0, index );
            }
        }
    }

    public static void createQuadMeshTexCoords(float width, float height, int numCellsX,
            int numCellsY, FloatBuffer buffer) {
        synchronized( floatArray )
        {
            int index = 0;
            for( int y = 0; y <= numCellsY; y++ )
            {
                for( int x = 0; x <= numCellsX; x++ )
                {
                    floatArray[index] = ( width * ( (float) x / (float) ( numCellsX + 1 ) ) );
                    ++index;
                    floatArray[index] = ( height * ( (float) y / (float) ( numCellsY + 1 ) ) );
                    ++index;
                    if( index == floatArray.length )
                    {
                        buffer.put( floatArray );
                        index = 0;
                    }
                }
            }
            if( index > 0 )
            {
                buffer.put( floatArray, 0, index );
            }
        }
    }

    public static int getNumQuadMeshIndices(int numCellsX, int numCellsY) {
        return ((2 * (numCellsX + 1)) * numCellsY) + (2 * (numCellsY - 1));
    }

    /**
     * This is a helper array that is used to avoid a ton of put operations in the
     * {@link #createQuadMeshIndexBuffer(int, int, ShortBuffer)} method
     */
    private static final short [] shortArray = new short[256];

    public static void createQuadMeshIndexBuffer(int numCellsX, int numCellsY, ShortBuffer buffer) {
        short index = 0;
        final int numVertsX = numCellsX + 1;
        int i = 0;
        synchronized( shortArray )
        {
            for( int y = 0; y < numCellsY; y++ )
            {
                for( int x = 0; x < numVertsX; x++ )
                {
                    shortArray[i] = index;
                    ++i;
                    shortArray[i] = (short) ( index + numVertsX );
                    ++i;
                    if(i == shortArray.length)
                    {
                        buffer.put( shortArray );
                        i = 0;
                    }
                    ++index;
                }
                // the degenerate triangle
                if( y < ( numCellsY - 1 ) )
                {
                    shortArray[i] = (short) ( ( index + numVertsX ) - 1 );
                    ++i;
                    shortArray[i] = index;
                    ++i;
                    if(i == shortArray.length)
                    {
                        buffer.put( shortArray );
                        i = 0;
                    }
                }
            }
            if( i > 0 )
            {
                buffer.put( shortArray, 0, i );
            }
        }
    }
}

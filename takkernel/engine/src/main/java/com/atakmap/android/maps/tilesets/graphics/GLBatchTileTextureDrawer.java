package com.atakmap.android.maps.tilesets.graphics;

import android.graphics.Color;
import android.opengl.GLES30;
import com.atakmap.map.opengl.GLMapView;
import com.atakmap.opengl.GLES20FixedPipeline;
import com.atakmap.opengl.GLES20FixedPipeline.Program;

import java.nio.Buffer;


/**
 * This object will store all the tile texture draw commands in a batch so they can be done all
 * at once instead of drawing textures individually
 *
 */
public class GLBatchTileTextureDrawer
{
    /**
     * This array is used to store the batch and cache items so they can be reused and easily tracked
     */
    private BatchTextureDrawWithIndices[] workingBatchList = new BatchTextureDrawWithIndices[128];

    /**
     * This stores how many items in {@link #workingBatchList} are active
     */
    private int batchSize = 0;

    /**
     * This is the red color component that should be used when drawing the textures
     */
    private float colorR = 1f;

    /**
     * This is the green color component that should be used when drawing the textures
     */
    private float colorG = 1f;

    /**
     * This is the blue color component that should be used when drawing the textures
     */
    private float colorB = 1f;

    /**
     * This is the alpha color component that should be used when drawing the textures
     */
    private float colorA = 1f;

    /**
     * This flag tells if the forward matrix needs to be applied before the texture drawing
     */
    private boolean useForwardMatrix = false;

    /**
     * This stores the forward matrix that needs to be used
     */
    private float[] forwardMatrix = null;

    /**
     * This is used to help with the tile texture drawing process
     */
    private FastTileTexturePipeline fastPipeline = new FastTileTexturePipeline( );

    /**
     * This sets the color that will be used when drawing all the textures in a batch
     *
     * @param color
     *             The color to be used
     */
    public void setColor( int color )
    {
        this.colorR = Color.red( color ) / 255f;
        this.colorG = Color.green( color ) / 255f;
        this.colorB = Color.blue( color ) / 255f;
        this.colorA = Color.alpha( color ) / 255f;
    }

    /**
     * This needs to be called once each frame to initialize the forward matrix for use when it
     * needs to be used during the batch operations
     *
     * @param view
     *             The GLMapView that contains the scene model forward matrix to use
     */
    public void populateForwardMatrix( GLMapView view )
    {
        forwardMatrix = view.sceneModelForwardMatrix;
    }

    /**
     * This will return the current forward matrix state of the batch.
     *
     * @return The current forward matrix state of the batch
     */
    public boolean getUseForwardMatrix( )
    {
        return useForwardMatrix;
    }

    /**
     * This will set the forward matrix state of the batch. When set to true the GLMapView's
     * forward matrix will be applied to the model stack for the rendering process
     *
     * @param forwardMatrix
     *             The new forward matrix state of the batch
     */
    public void setUseForwardMatrix( boolean forwardMatrix )
    {
        if( useForwardMatrix != forwardMatrix )
        {
            flush( );
            useForwardMatrix = forwardMatrix;
        }
    }

    /**
     * This will batch the texture for drawing assuming 2 float texture coordinates, 3 float
     * vertices and an unsigned short for the indices
     *
     * @param texId
     *             The identifier for the texture
     * @param mode
     *             The triangle drawing mode
     * @param numCoords
     *             The number of indices
     * @param textureCoordinates
     *             The buffer to the texture coordinates
     * @param vertexCoordinates
     *             The buffer to the vertex coordinates
     * @param indices
     *             The indices that define how the vertices should be drawn
     */
    public void drawWithIndices( int texId, int mode, int numCoords, Buffer textureCoordinates,
                Buffer vertexCoordinates, Buffer indices )
    {
        if( texId != 0 )
        {
            BatchTextureDrawWithIndices batchObj;
            if( workingBatchList.length == batchSize )
            {
                int newSize = workingBatchList.length * 2;
                BatchTextureDrawWithIndices[] biggerList = new BatchTextureDrawWithIndices[newSize];
                System.arraycopy( workingBatchList, 0, biggerList, 0, batchSize );
                workingBatchList = biggerList;
            }
            batchObj = workingBatchList[batchSize];
            if( batchObj == null )
            {
                batchObj = new BatchTextureDrawWithIndices( );
                workingBatchList[batchSize] = batchObj;
            }
            batchObj.update( texId, mode, numCoords, textureCoordinates, vertexCoordinates,
                        indices );
            batchSize++;
        }
    }

    public void flush( )
    {
        if( batchSize > 0 )
        {
            boolean adjustMatrix = ( useForwardMatrix ) && ( forwardMatrix != null );
            if( adjustMatrix )
            {
                GLES20FixedPipeline.glPushMatrix( );
                GLES20FixedPipeline.glLoadMatrixf( forwardMatrix, 0 );
            }
            GLES20FixedPipeline.glEnableClientState( GLES20FixedPipeline.GL_VERTEX_ARRAY );
            GLES20FixedPipeline.glEnableClientState( GLES20FixedPipeline.GL_TEXTURE_COORD_ARRAY );
            GLES20FixedPipeline.glEnable( GLES20FixedPipeline.GL_BLEND );
            GLES20FixedPipeline.glBlendFunc( GLES20FixedPipeline.GL_SRC_ALPHA,
                        GLES20FixedPipeline.GL_ONE_MINUS_SRC_ALPHA );

            fastPipeline.begin( colorR, colorG, colorB, colorA );

            for( int i = 0; i < batchSize; i++ )
            {
                fastPipeline.drawBatchTexture( workingBatchList[i] );
            }
            batchSize = 0;

            fastPipeline.end( );

            GLES20FixedPipeline.glDisableClientState( GLES20FixedPipeline.GL_VERTEX_ARRAY );
            GLES20FixedPipeline.glDisableClientState( GLES20FixedPipeline.GL_TEXTURE_COORD_ARRAY );
            GLES20FixedPipeline.glDisable( GLES20FixedPipeline.GL_BLEND );
            if( adjustMatrix )
            {
                GLES20FixedPipeline.glPopMatrix( );
            }
        }
    }

    /**
     * This class holds information about drawing a texture where indices are provided
     */
    private static class BatchTextureDrawWithIndices
    {
        /**
         * The identifier for the texture
         */
        int texId;

        /**
         * The triangle drawing mode
         */
        int mode;

        /**
         * The number of indices
         */
        int numCoords;

        /**
         * The buffer to the texture coordinates
         */
        Buffer textureCoordinates;

        /**
         * The buffer to the vertex coordinates
         */
        Buffer vertexCoordinates;

        /**
         * The indices that define how the vertices should be drawn
         */
        Buffer indices;

        public void update( int texId, int mode, int numCoords, Buffer textureCoordinates,
                    Buffer vertexCoordinates, Buffer indices )
        {
            this.texId = texId;
            this.mode = mode;
            this.numCoords = numCoords;
            this.textureCoordinates = textureCoordinates;
            this.vertexCoordinates = vertexCoordinates;
            this.indices = indices;
        }
    }

    /**
     * This class provides an optimized pipeline for drawing the tile texture
     */
    private static class FastTileTexturePipeline
    {
        /**
         * This is the program used when there is no color needed
         */
        private Program whiteShaderProgram = null;

        /**
         * This is the program used when color is needed
         */
        private Program colorShaderProgram = null;

        /**
         * This is the handle for the active program
         */
        private int programHandle;

        /**
         * This is used to store the handle to the vertex coordinates in the active program
         */
        private int vertexCoordsHandle;

        /**
         * This is used to store the handle to the texture coordinates in the active program
         */
        private int textureCoordsHandle;

        /**
         * This is used to lookup the matrixes
         */
        private final float[] matrixLookup = new float[16];

        void begin( float colorR, float colorG, float colorB, float colorA )
        {
            boolean colorNeeded =
                        ( colorR != 1.0f || colorG != 1.0f || colorB != 1.0f || colorA != 1.0f );
            setupProgram( colorNeeded );
            if( colorNeeded )
            {
                final int uColorHandle = GLES30.glGetUniformLocation( programHandle, "uColor" );
                GLES30.glUniform4f( uColorHandle, colorR, colorG, colorB, colorA );
            }

            setupMatrix( );

            final int uTextureHandle = GLES30.glGetUniformLocation( programHandle, "uTexture" );
            GLES20FixedPipeline.glActiveTexture( GLES30.GL_TEXTURE0 );
            GLES30.glUniform1i( uTextureHandle, 0 );

            vertexCoordsHandle = GLES30.glGetAttribLocation( programHandle, "aVertexCoords" );
            textureCoordsHandle = GLES30.glGetAttribLocation( programHandle, "aTextureCoords" );

            GLES30.glEnableVertexAttribArray( vertexCoordsHandle );
            GLES30.glEnableVertexAttribArray( textureCoordsHandle );
        }

        private void setupProgram( boolean colorNeeded )
        {
            if( colorNeeded )
            {
                if( colorShaderProgram == null )
                {
                    colorShaderProgram = new Program( );
                    colorShaderProgram
                                .create( GLES20FixedPipeline.TEXTURE_3D_ONE_MATRIX_VERT_SHADER_SRC,
                                            GLES20FixedPipeline.MODULATED_TEXTURE_FRAG_SHADER_SRC );
                }
                programHandle = colorShaderProgram.program;
            }
            else
            {
                if( whiteShaderProgram == null )
                {
                    whiteShaderProgram = new Program( );
                    whiteShaderProgram
                                .create( GLES20FixedPipeline.TEXTURE_3D_ONE_MATRIX_VERT_SHADER_SRC,
                                            GLES20FixedPipeline.GENERIC_TEXTURE_FRAG_SHADER_SRC );
                }
                programHandle = whiteShaderProgram.program;
            }
            GLES30.glUseProgram( programHandle );
        }

        private void setupMatrix( )
        {
            GLES20FixedPipeline.glGetFloatv( GLES20FixedPipeline.GL_PROJECTION, matrixLookup, 0 );
            float a00 = matrixLookup[0];
            float a10 = matrixLookup[1];
            float a20 = matrixLookup[2];
            float a30 = matrixLookup[3];

            float a01 = matrixLookup[4];
            float a11 = matrixLookup[5];
            float a21 = matrixLookup[6];
            float a31 = matrixLookup[7];

            float a02 = matrixLookup[8];
            float a12 = matrixLookup[9];
            float a22 = matrixLookup[10];
            float a32 = matrixLookup[11];

            float a03 = matrixLookup[12];
            float a13 = matrixLookup[13];
            float a23 = matrixLookup[14];
            float a33 = matrixLookup[15];

            GLES20FixedPipeline.glGetFloatv( GLES20FixedPipeline.GL_MODELVIEW, matrixLookup, 0 );
            float b00 = matrixLookup[0];
            float b10 = matrixLookup[1];
            float b20 = matrixLookup[2];
            float b30 = matrixLookup[3];

            float b01 = matrixLookup[4];
            float b11 = matrixLookup[5];
            float b21 = matrixLookup[6];
            float b31 = matrixLookup[7];

            float b02 = matrixLookup[8];
            float b12 = matrixLookup[9];
            float b22 = matrixLookup[10];
            float b32 = matrixLookup[11];

            float b03 = matrixLookup[12];
            float b13 = matrixLookup[13];
            float b23 = matrixLookup[14];
            float b33 = matrixLookup[15];

            // Projection (A) * Model (B)
            matrixLookup[0] = ( a00 * b00 ) + ( a01 * b10 ) + ( a02 * b20 ) + ( a03 * b30 );
            matrixLookup[1] = ( a10 * b00 ) + ( a11 * b10 ) + ( a12 * b20 ) + ( a13 * b30 );
            matrixLookup[2] = ( a20 * b00 ) + ( a21 * b10 ) + ( a22 * b20 ) + ( a23 * b30 );
            matrixLookup[3] = ( a30 * b00 ) + ( a31 * b10 ) + ( a32 * b20 ) + ( a33 * b30 );

            matrixLookup[4] = ( a00 * b01 ) + ( a01 * b11 ) + ( a02 * b21 ) + ( a03 * b31 );
            matrixLookup[5] = ( a10 * b01 ) + ( a11 * b11 ) + ( a12 * b21 ) + ( a13 * b31 );
            matrixLookup[6] = ( a20 * b01 ) + ( a21 * b11 ) + ( a22 * b21 ) + ( a23 * b31 );
            matrixLookup[7] = ( a30 * b01 ) + ( a31 * b11 ) + ( a32 * b21 ) + ( a33 * b31 );

            matrixLookup[8] = ( a00 * b02 ) + ( a01 * b12 ) + ( a02 * b22 ) + ( a03 * b32 );
            matrixLookup[9] = ( a10 * b02 ) + ( a11 * b12 ) + ( a12 * b22 ) + ( a13 * b32 );
            matrixLookup[10] = ( a20 * b02 ) + ( a21 * b12 ) + ( a22 * b22 ) + ( a23 * b32 );
            matrixLookup[11] = ( a30 * b02 ) + ( a31 * b12 ) + ( a32 * b22 ) + ( a33 * b32 );

            matrixLookup[12] = ( a00 * b03 ) + ( a01 * b13 ) + ( a02 * b23 ) + ( a03 * b33 );
            matrixLookup[13] = ( a10 * b03 ) + ( a11 * b13 ) + ( a12 * b23 ) + ( a13 * b33 );
            matrixLookup[14] = ( a20 * b03 ) + ( a21 * b13 ) + ( a22 * b23 ) + ( a23 * b33 );
            matrixLookup[15] = ( a30 * b03 ) + ( a31 * b13 ) + ( a32 * b23 ) + ( a33 * b33 );

            final int uMatrixHandle =
                        GLES30.glGetUniformLocation( programHandle, "uMatrix" );
            GLES30.glUniformMatrix4fv( uMatrixHandle, 1, false, matrixLookup, 0 );
        }

        void drawBatchTexture( BatchTextureDrawWithIndices info )
        {
            GLES20FixedPipeline.glBindTexture( GLES20FixedPipeline.GL_TEXTURE_2D, info.texId );

            GLES30.glVertexAttribPointer( vertexCoordsHandle, 3, GLES20FixedPipeline.GL_FLOAT,
                        false, 0, info.vertexCoordinates );
            GLES30.glVertexAttribPointer( textureCoordsHandle, 2, GLES20FixedPipeline.GL_FLOAT,
                        false, 0, info.textureCoordinates );

            if( info.indices != null )
            {
                GLES30.glDrawElements( info.mode, info.numCoords,
                            GLES20FixedPipeline.GL_UNSIGNED_SHORT,
                            info.indices );
                info.indices = null;
            }
            else
            {
                GLES30.glDrawArrays( info.mode, 0, info.numCoords );
            }

            info.textureCoordinates = null;
            info.vertexCoordinates = null;
        }

        void end( )
        {
            GLES30.glDisableVertexAttribArray( vertexCoordsHandle );
            GLES30.glDisableVertexAttribArray( textureCoordsHandle );
        }
    }
}

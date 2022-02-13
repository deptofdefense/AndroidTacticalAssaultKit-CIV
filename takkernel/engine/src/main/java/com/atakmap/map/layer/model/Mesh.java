package com.atakmap.map.layer.model;

import com.atakmap.map.layer.feature.geometry.Envelope;
import com.atakmap.math.PointD;
import com.atakmap.util.Disposable;

import java.nio.Buffer;

import gov.tak.api.annotation.DontObfuscate;

@DontObfuscate
public interface Mesh extends Disposable {

    public enum WindingOrder {
        Clockwise,
        CounterClockwise,
        Undefined,
    }
    public enum DrawMode {
        Points,
        Triangles,
        TriangleStrip,
        Lines,
        LineStrip,
    }

    public final static int VERTEX_ATTR_TEXCOORD_0 = 1;
    public final static int VERTEX_ATTR_NORMAL = 1 << 1;
    public final static int VERTEX_ATTR_COLOR = 1 << 2;
    public final static int VERTEX_ATTR_POSITION = 1 << 3;
    public final static int VERTEX_ATTR_TEXCOORD_1 = 1 << 4;
    public final static int VERTEX_ATTR_TEXCOORD_2 = 1 << 5;
    public final static int VERTEX_ATTR_TEXCOORD_3 = 1 << 6;
    public final static int VERTEX_ATTR_TEXCOORD_4 = 1 << 7;
    public final static int VERTEX_ATTR_TEXCOORD_5 = 1 << 8;
    public final static int VERTEX_ATTR_TEXCOORD_6 = 1 << 9;
    public final static int VERTEX_ATTR_TEXCOORD_7 = 1 << 10;

    /**
     * Returns the number of vertices in the model.
     *
     * @return  The number of vertices in the model.
     */
    public int getNumVertices();

    /**
     * Returns the number of faces (triangles) in the model.
     *
     * @return  The number of faces in the model.
     */
    public int getNumFaces();

    /**
     * Returns a flag indicating whether or not the model has indexed vertex
     * data.
     *
     * @return  <code>true</code> if the model has indexed vertex data,
     *          <code>false</code> otherwise.
     */
    public boolean isIndexed();

    /**
     * Returns the position attribute data for the specified vertex.
     *
     * @param i     The vertex number
     * @param xyz   Returns the vertex position
     */
    public void getPosition(int i, PointD xyz);

    /**
     * Returns the texture coordinate attribute data for the specified vertex.
     *
     * @param i     The vertex number
     * @param uv    Returns the vertex texture coordinate. The <I>u</I>
     *              component is returned via <code>uv.x</code> and the
     *              <I>v</I> component is returned via <code>uv.y</code>
     */
    public void getTextureCoordinate(int texCoordNum, int i, PointD uv);
    /**
     * Returns the normal attribute data for the specified vertex.
     *
     * @param i     The vertex number
     * @param xyz   Returns the vertex normal
     */
    public void getNormal(int i, PointD xyz);

    /**
     * Returns the color attribute data for the specified vertex as a 32-bit
     * packed ARGB value.
     *
     * @param i The vertex number
     *
     * @return  The color attribute data for the specified vertex as a 32-bit
     *          packed ARGB value.
     */
    public int getColor(int i);
    public Class<?> getVertexAttributeType(int attr);

    /**
     * Returns the index type. Must be one of:
     * <UL>
     *     <LI>{@link Short#TYPE}</LI>
     *     <LI>{@link Integer#TYPE}</LI>
     *     <LI><code>null</code></LI>
     * </UL>
     * @return The index type, or <code>null</code> if the model is not
     *         indexed.
     */
    public Class<?> getIndexType();

    /**
     * Returns the specified index element.
     *
     * @param i The index offset
     *
     * @return  The specified index element.
     */
    public int getIndex(int i);

    /**
     * Returns a {@link Buffer} containing the index data. The first element of
     * the indices is expected to start at the <I>base pointer of the buffer,
     * ignoring position, plus the index offset.</I> All components per vertex
     * for the attribute data are expected to be packed contiguously.
     *
     * <P>The returned {@link Buffer} MUST:
     * <UL>
     *     <LI>Be direct</LI>
     *     <LI>Be in native order</LI>
     *     <LI>Not be a slice OR be a slice created from a
     *         <code>Buffer</code> whose <I>position</I> was
     *         <code>0</code></LI>
     * </UL>
     * @return
     */
    public Buffer getIndices();

    /**
     * Returns the offset, in bytes, from the first byte of the {@link Buffer}
     * returned by {@link #getIndices()} of the first index element.
     * @return
     */
    public int getIndexOffset();

    /**
     * Returns a {@link Buffer} containing the specified vertex data. The first
     * byte of the data of the specified vertex attribute is expected to start
     * at the <I>base pointer of the buffer, ignoring position, plus the vertex
     * offset for the attribute type.</I> All components per vertex for the
     * attribute data are expected to be packed contiguously; attribute data
     * for consecutive components will be separated by the respective stride
     * bytes as defined by the {@link VertexDataLayout}.
     *
     * <P>The returned {@link Buffer} MUST:
     * <UL>
     *     <LI>Be direct</LI>
     *     <LI>Be in native order</LI>
     *     <LI>Not be a slice OR be a slice created from a
     *         <code>Buffer</code> whose <I>position</I> was
     *         <code>0</code></LI>
     * </UL>
     * @param attr  The attribute type. Must be only one of
     *              <UL>
     *                  <LI>{@link #VERTEX_ATTR_COLOR}</LI>
     *                  <LI>{@link #VERTEX_ATTR_NORMAL}</LI>
     *                  <LI>{@link #VERTEX_ATTR_POSITION}</LI>
     *                  <LI>{@link #VERTEX_ATTR_TEXCOORD_0}</LI>
     *              </UL>
     * @return  The {@link Buffer} that contains the specified vertex attribute.
     */
    public Buffer getVertices(int attr);
    public WindingOrder getFaceWindingOrder();
    public DrawMode getDrawMode();
    public Envelope getAABB();
    public VertexDataLayout getVertexDataLayout();

    public int getNumMaterials();
    public Material getMaterial(int index);
    public Material getMaterial(Material.PropertyType propertyType);
}

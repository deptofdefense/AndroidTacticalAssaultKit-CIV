
package com.atakmap.android.model.opengl;

import android.graphics.Color;

import com.atakmap.annotations.DeprecatedApi;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;

import com.atakmap.lang.Unsafe;
import com.atakmap.map.layer.feature.geometry.Envelope;
import com.atakmap.map.layer.model.Material;
import com.atakmap.map.layer.model.Mesh;
import com.atakmap.map.layer.model.MeshBuilder;
import com.atakmap.map.layer.model.Model;
import com.atakmap.map.layer.model.ModelBuilder;
import com.atakmap.map.layer.model.ModelInfo;
import com.atakmap.map.layer.model.ModelSpi;
import com.atakmap.map.layer.model.Models;
import com.atakmap.map.layer.model.VertexDataLayout;
import com.atakmap.math.MathUtils;
import com.atakmap.math.Matrix;
import com.atakmap.math.PointD;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/** @deprecated NAME SUBJECT TO CHANGE !!!! */
@Deprecated
@DeprecatedApi(since = "4.1")
public class MemoryMappedModel {

    private static final String TAG = "MemoryMappedModel";

    public final static ModelSpi SPI = new ModelSpi() {

        @Override
        public Model create(ModelInfo object) {
            return this.create(object, null);
        }

        @Override
        public Model create(ModelInfo object, ModelSpi.Callback callback) {
            File f = new File(object.uri);
            if (!IOProviderFactory.exists(f))
                return null;
            try (FileChannel fis = IOProviderFactory.getChannel(f, "r")) {
                return read(fis, Integer.MAX_VALUE);
            } catch (Throwable t) {
                Log.e(TAG, "error", t);
                return null;
            }
        }

        @Override
        public String getType() {
            return "TAKOTM";
        }

        @Override
        public int getPriority() {
            return 1;
        }
    };

    /*************************************************************************/

    private static void writeMesh(Mesh m, WritableByteChannel chan,
            int writeLimit)
            throws IOException {
        ByteBuffer buf = ByteBuffer.allocate(158);
        buf.order(ByteOrder.BIG_ENDIAN);

        // AABB -- 48 bytes (48)
        Envelope aabb = m.getAABB();
        buf.putDouble(aabb.minX);
        buf.putDouble(aabb.minY);
        buf.putDouble(aabb.minZ);
        buf.putDouble(aabb.maxX);
        buf.putDouble(aabb.maxY);
        buf.putDouble(aabb.maxZ);

        // winding order -- 1 byte (49)
        switch (m.getFaceWindingOrder()) {
            case Clockwise:
                buf.put((byte) 0x00);
                break;
            case CounterClockwise:
                buf.put((byte) 0x01);
                break;
            case Undefined:
                buf.put((byte) 0xFF);
                break;
            default:
                throw new IllegalStateException();
        }
        // draw mode -- 1 byte (50)
        switch (m.getDrawMode()) {
            case Triangles:
                buf.put((byte) 0x00);
                break;
            case TriangleStrip:
                buf.put((byte) 0x01);
                break;
            case Points:
                buf.put((byte) 0x02);
                break;
            default:
                throw new IllegalStateException();
        }

        // num faces -- 4 bytes (54)
        buf.putInt(m.getNumFaces());
        // num vertices -- 4 bytes (58)
        buf.putInt(m.getNumVertices());

        // layout -- 92 bytes (150)
        final VertexDataLayout srcLayout = m.getVertexDataLayout();
        // vertex data attributes
        buf.putInt(srcLayout.attributes);

        // vertex data layout
        final boolean rebuildVertexData = !srcLayout.interleaved ||
                !(m.getVertices(
                        Mesh.VERTEX_ATTR_POSITION) instanceof ByteBuffer);
        VertexDataLayout dstLayout;
        if (rebuildVertexData) {
            dstLayout = VertexDataLayout
                    .createDefaultInterleaved(srcLayout.attributes);
        } else {
            dstLayout = srcLayout;
        }

        buf.putInt(dstLayout.position.offset);
        buf.putInt(dstLayout.position.stride);
        buf.putInt(dstLayout.texCoord0.offset);
        buf.putInt(dstLayout.texCoord0.stride);
        buf.putInt(dstLayout.normal.offset);
        buf.putInt(dstLayout.normal.stride);
        buf.putInt(dstLayout.color.offset);
        buf.putInt(dstLayout.color.stride);
        buf.putInt(dstLayout.texCoord1.offset);
        buf.putInt(dstLayout.texCoord1.stride);
        buf.putInt(dstLayout.texCoord2.offset);
        buf.putInt(dstLayout.texCoord2.stride);
        buf.putInt(dstLayout.texCoord3.offset);
        buf.putInt(dstLayout.texCoord3.stride);
        buf.putInt(dstLayout.texCoord4.offset);
        buf.putInt(dstLayout.texCoord4.stride);
        buf.putInt(dstLayout.texCoord5.offset);
        buf.putInt(dstLayout.texCoord5.stride);
        buf.putInt(dstLayout.texCoord6.offset);
        buf.putInt(dstLayout.texCoord6.stride);
        buf.putInt(dstLayout.texCoord7.offset);
        buf.putInt(dstLayout.texCoord7.stride);

        // vertex data size -- 4 bytes (158)
        buf.putInt(VertexDataLayout.requiredInterleavedDataSize(dstLayout,
                m.getNumVertices()));

        // flush header data to stream
        buf.flip();
        writeFully(chan, buf, writeLimit);
        buf.clear();

        // XXX - handle attribute typing

        if (!rebuildVertexData &&
                m.getVertices(
                        Mesh.VERTEX_ATTR_POSITION) instanceof ByteBuffer) {

            ByteBuffer dupe = ((ByteBuffer) m
                    .getVertices(Mesh.VERTEX_ATTR_POSITION)).duplicate();

            // XXX - fix up position and limit
            dupe.limit(
                    VertexDataLayout.requiredInterleavedDataSize(dstLayout,
                            m.getNumVertices()));

            writeFully(chan, dupe, writeLimit);
        } else {
            PointD scratch = new PointD(0d, 0d, 0d);

            // reallocate if necessary
            if (buf.capacity() < dstLayout.position.stride)
                buf = ByteBuffer.allocate(dstLayout.position.stride);
            else
                buf.clear();

            // vertex data is always native order
            buf.order(ByteOrder.nativeOrder());

            for (int i = 0; i < m.getNumVertices(); i++) {
                if (MathUtils.hasBits(srcLayout.attributes,
                        Mesh.VERTEX_ATTR_POSITION)) {
                    m.getPosition(i, scratch);
                    buf.putFloat(dstLayout.position.offset,
                            (float) scratch.x);
                    buf.putFloat(dstLayout.position.offset + 4,
                            (float) scratch.y);
                    buf.putFloat(dstLayout.position.offset + 8,
                            (float) scratch.z);
                }
                if (MathUtils.hasBits(srcLayout.attributes,
                        Mesh.VERTEX_ATTR_TEXCOORD_0)) {
                    m.getTextureCoordinate(0, i, scratch);
                    buf.putFloat(dstLayout.texCoord0.offset, (float) scratch.x);
                    buf.putFloat(dstLayout.texCoord0.offset + 4,
                            (float) scratch.y);
                }
                int[] extTexOffsets = {
                        dstLayout.texCoord1.offset,
                        dstLayout.texCoord2.offset,
                        dstLayout.texCoord3.offset,
                        dstLayout.texCoord4.offset,
                        dstLayout.texCoord5.offset,
                        dstLayout.texCoord6.offset,
                        dstLayout.texCoord7.offset,
                };
                for (int j = 0; j < 7; ++j) {
                    if (MathUtils.hasBits(srcLayout.attributes,
                            Mesh.VERTEX_ATTR_TEXCOORD_1 << j)) {
                        m.getTextureCoordinate(j, i, scratch);
                        buf.putFloat(extTexOffsets[j], (float) scratch.x);
                        buf.putFloat(extTexOffsets[j] + 4, (float) scratch.y);
                    }
                }
                if (MathUtils.hasBits(srcLayout.attributes,
                        Mesh.VERTEX_ATTR_NORMAL)) {
                    m.getNormal(i, scratch);
                    buf.putFloat(dstLayout.normal.offset, (float) scratch.x);
                    buf.putFloat(dstLayout.normal.offset + 4,
                            (float) scratch.y);
                    buf.putFloat(dstLayout.normal.offset + 8,
                            (float) scratch.z);
                }
                if (MathUtils.hasBits(srcLayout.attributes,
                        Mesh.VERTEX_ATTR_COLOR)) {
                    int color = m.getColor(i);
                    buf.put(dstLayout.color.offset,
                            (byte) ((color >> 24) & 0xFF));
                    buf.put(dstLayout.color.offset + 1,
                            (byte) ((color >> 16) & 0xFF));
                    buf.put(dstLayout.color.offset + 2,
                            (byte) ((color >> 8) & 0xFF));
                    buf.put(dstLayout.color.offset + 3,
                            (byte) (color & 0xFF));
                }
                buf.flip();
                if (buf.remaining() > 0)
                    writeFully(chan, buf, writeLimit);
                buf.clear();
            }

            // reset for more writing
            buf.order(ByteOrder.BIG_ENDIAN);
            buf.clear();
        }

        // index section

        // XXX - handle int indices

        // num indices
        final int numIndices = Models.getNumIndices(m);
        buf.putInt(numIndices);
        buf.flip();
        chan.write(buf);
        buf.clear();

        // index values
        for (int i = 0; i < numIndices; i++) {
            buf.putShort((short) m.getIndex(i));
            buf.flip();
            chan.write(buf);
            buf.clear();
        }

        // materials section
        buf.putInt(m.getNumMaterials());
        buf.flip();
        chan.write(buf);
        buf.clear();

        {
            DataOutputStream dos = new DataOutputStream(
                    Channels.newOutputStream(chan));
            for (int i = 0; i < m.getNumMaterials(); ++i) {
                Material material = m.getMaterial(i);
                dos.writeInt(material.getPropertyType().ordinal());
                dos.writeInt(material.getColor());
                dos.writeUTF(
                        material.getTextureUri() != null
                                ? material.getTextureUri()
                                : "");
            }
        }
    }

    public static void write(Model m, OutputStream stream) throws IOException {
        if (stream.getClass().equals(FileOutputStream.class))
            write(m, ((FileOutputStream) stream).getChannel(),
                    Integer.MAX_VALUE);
        else
            write(m, Channels.newChannel(stream), 1024 * 1024);
    }

    public static void write(Model m, FileChannel channel) throws IOException {
        write(m, channel,
                Integer.MAX_VALUE);
    }

    private static void write(Model m, WritableByteChannel stream, int limit)
            throws IOException {
        // write magic number
        ByteBuffer buf = ByteBuffer.wrap(new byte[] {
                (byte) 'T',
                (byte) 'A',
                (byte) 'K',
                (byte) 'O',
                (byte) 'T',
                (byte) 'M',
        });

        stream.write(buf);
        writeVersion3(m, stream, limit);
    }

    private static void writeVersion3(Model m, WritableByteChannel stream,
            int limit)
            throws IOException {

        ByteBuffer buf = ByteBuffer.allocate(132);
        buf.order(ByteOrder.BIG_ENDIAN);

        // write header
        buf.put((byte) 0x03); // version

        // AABB
        Envelope aabb = m.getAABB();
        buf.putDouble(aabb.minX);
        buf.putDouble(aabb.minY);
        buf.putDouble(aabb.minZ);
        buf.putDouble(aabb.maxX);
        buf.putDouble(aabb.maxY);
        buf.putDouble(aabb.maxZ);

        buf.flip();
        stream.write(buf);

        int numMeshes = 0;
        int numInstancedMeshes = 0;
        Map<Integer, List<Integer>> instanceMeshIds = new HashMap<>();
        for (int i = 0; i < m.getNumMeshes(); ++i) {
            final int instanceId = m.getInstanceId(i);
            if (instanceId == Model.INSTANCE_ID_NONE) {
                numMeshes++;
            } else {
                numInstancedMeshes++;
                List<Integer> meshIds = instanceMeshIds.get(instanceId);
                if (meshIds == null)
                    instanceMeshIds.put(instanceId,
                            meshIds = new LinkedList<>());
                meshIds.add(i);
            }
        }

        // non-instanced mesh section
        buf.clear();
        buf.putInt(numMeshes);
        buf.flip();
        stream.write(buf);

        for (int i = 0; i < m.getNumMeshes(); ++i) {
            if (m.getInstanceId(i) == Model.INSTANCE_ID_NONE)
                writeMesh(m.getMesh(i), stream, limit);
        }

        // instance records section
        buf.clear();
        buf.putInt(numInstancedMeshes);
        buf.flip();
        stream.write(buf);

        for (int i = 0; i < m.getNumMeshes(); ++i) {
            final int instanceId = m.getInstanceId(i);
            if (instanceId == Model.INSTANCE_ID_NONE)
                continue;

            buf.clear();
            buf.putInt(instanceId);

            Matrix mx = m.getTransform(i);
            if (mx == null)
                mx = Matrix.getIdentity();
            for (int j = 0; j < 16; j++)
                buf.putDouble(mx.get(j / 4, j % 4));

            buf.flip();
            stream.write(buf);
        }

        // instanced mesh data section
        buf.clear();
        buf.putInt(instanceMeshIds.size());
        buf.flip();
        stream.write(buf);

        for (Map.Entry<Integer, List<Integer>> instanceEntry : instanceMeshIds
                .entrySet()) {
            buf.clear();
            buf.putInt(instanceEntry.getKey());
            buf.flip();
            stream.write(buf);

            writeMesh(m.getMesh(instanceEntry.getValue().get(0), false), stream,
                    limit);
        }
    }

    private static void readFully(ReadableByteChannel chan, ByteBuffer buf)
            throws IOException {
        readFully(chan, buf, buf.remaining());
    }

    private static void readFully(ReadableByteChannel chan, ByteBuffer buf,
            int maxReadSize)
            throws IOException {

        final int limit = buf.limit();
        while (buf.position() < limit) {
            final int pos = buf.position();
            buf.limit(pos + Math.min(limit - pos, maxReadSize));
            int numRead = chan.read(buf);
            if (numRead < 1)
                throw new EOFException();
        }
    }

    private static void writeFully(WritableByteChannel chan, ByteBuffer buf,
            int maxWriteSize)
            throws IOException {

        final int limit = buf.limit();
        while (buf.position() < limit) {
            final int pos = buf.position();
            buf.limit(pos + Math.min(limit - pos, maxWriteSize));
            int numWritten = chan.write(buf);
            if (numWritten < 1)
                throw new IOException();
        }
    }

    private static boolean memcmp(byte[] a, int aoff, byte[] b, int boff,
            int len) {
        for (int i = 0; i < len; i++) {
            if (a[aoff + i] != b[boff + i])
                return false;
        }
        return true;
    }

    public static Model read(InputStream stream) throws IOException {
        if (stream.getClass().equals(FileInputStream.class))
            return read(((FileInputStream) stream).getChannel(),
                    Integer.MAX_VALUE);
        else
            return read(Channels.newChannel(stream), 1024 * 1024);
    }

    public static Model read(FileChannel channel) throws IOException {
        return read(channel, Integer.MAX_VALUE);
    }

    private static Model read(ReadableByteChannel channel, int readLimit)
            throws IOException {
        byte[] magic = new byte[6];
        ByteBuffer buf = ByteBuffer.wrap(magic);
        if (channel.read(buf) < 6)
            return null;

        if (!memcmp(magic, 0, new byte[] {
                (byte) 'T', (byte) 'A', (byte) 'K', (byte) 'O', (byte) 'T',
                (byte) 'M'
        }, 0, 6))
            return null;

        buf.clear();
        buf.limit(1);
        readFully(channel, buf);
        buf.flip();

        int version = buf.get() & 0xFF;
        switch (version) {
            case 1:
                return readVersion1(channel, readLimit);
            case 2:
                return readVersion2(channel, readLimit);
            case 3:
                return readVersion3(channel, readLimit);
        }

        return null;
    }

    private static Model readVersion2(ReadableByteChannel chan, int readLimit)
            throws IOException {
        ByteBuffer buf = ByteBuffer.allocateDirect(52); // 6*8 + 4
        buf.order(ByteOrder.BIG_ENDIAN);

        Envelope aabb = new Envelope();
        buf.limit(8 * 6 + 4);
        readFully(chan, buf);
        buf.flip();

        aabb.minX = buf.getDouble();
        aabb.minY = buf.getDouble();
        aabb.minZ = buf.getDouble();
        aabb.maxZ = buf.getDouble();
        aabb.maxY = buf.getDouble();
        aabb.maxZ = buf.getDouble();

        int numMeshes = buf.getInt();
        Mesh[] meshes = new Mesh[numMeshes];
        for (int i = 0; i < numMeshes; ++i) {
            meshes[i] = readMesh(chan, readLimit);
        }

        return ModelBuilder.build(meshes);
    }

    private static Model readVersion3(ReadableByteChannel chan, int readLimit)
            throws IOException {

        ModelBuilder retval = new ModelBuilder();

        ByteBuffer buf = ByteBuffer.allocateDirect(132); // 6*8 + 4
        buf.order(ByteOrder.BIG_ENDIAN);

        Envelope aabb = new Envelope();
        buf.limit(8 * 6 + 4);
        readFully(chan, buf);
        buf.flip();

        aabb.minX = buf.getDouble();
        aabb.minY = buf.getDouble();
        aabb.minZ = buf.getDouble();
        aabb.maxZ = buf.getDouble();
        aabb.maxY = buf.getDouble();
        aabb.maxZ = buf.getDouble();

        int numMeshes = buf.getInt();
        for (int i = 0; i < numMeshes; ++i) {
            retval.addMesh(readMesh(chan, readLimit));
        }

        buf.clear();
        buf.limit(4);
        readFully(chan, buf);
        buf.flip();

        final int numInstanceRecords = buf.getInt();
        if (numInstanceRecords > 0) {
            Map<Integer, LinkedList<Matrix>> instances = new HashMap<>();

            for (int i = 0; i < numInstanceRecords; i++) {
                buf.clear();
                buf.limit(132);
                readFully(chan, buf);
                buf.flip();

                // instance ID
                final int instanceId = buf.getInt();
                // transform
                Matrix transform = new Matrix(buf.getDouble(),
                        buf.getDouble(),
                        buf.getDouble(),
                        buf.getDouble(),
                        buf.getDouble(),
                        buf.getDouble(),
                        buf.getDouble(),
                        buf.getDouble(),
                        buf.getDouble(),
                        buf.getDouble(),
                        buf.getDouble(),
                        buf.getDouble(),
                        buf.getDouble(),
                        buf.getDouble(),
                        buf.getDouble(),
                        buf.getDouble());

                LinkedList<Matrix> instanceXforms = instances.get(instanceId);
                if (instanceXforms == null)
                    instances.put(instanceId,
                            instanceXforms = new LinkedList<>());
                instanceXforms.add(transform);
            }

            buf.clear();
            buf.limit(4);
            readFully(chan, buf);
            buf.flip();

            final int numInstanceData = buf.getInt();
            for (int i = 0; i < numInstanceData; i++) {
                buf.clear();
                buf.limit(4);
                readFully(chan, buf);
                buf.flip();

                // instance ID
                final int instanceId = buf.getInt();

                // mesh data
                final Mesh data = readMesh(chan, readLimit);

                LinkedList<Matrix> instanceXforms = instances.get(instanceId);
                if (instanceXforms == null)
                    throw new IllegalStateException();

                retval.addMesh(data, instanceId, instanceXforms.removeFirst());
            }

            for (Map.Entry<Integer, LinkedList<Matrix>> entry : instances
                    .entrySet()) {
                final int instanceId = entry.getKey();
                for (Matrix xform : entry.getValue()) {
                    retval.addMesh(instanceId, xform);
                }
            }
        }

        return retval.build();
    }

    private static Mesh readMesh(ReadableByteChannel chan, int readLimit)
            throws IOException {
        ByteBuffer buf = Unsafe.allocateDirect(154);
        buf.order(ByteOrder.BIG_ENDIAN);

        readFully(chan, buf, readLimit);
        buf.flip();

        // AABB -- 48 bytes (48)
        Envelope aabb = new Envelope();
        aabb.minX = buf.getDouble();
        aabb.minY = buf.getDouble();
        aabb.minZ = buf.getDouble();
        aabb.maxX = buf.getDouble();
        aabb.maxY = buf.getDouble();
        aabb.maxZ = buf.getDouble();

        // winding order -- 1 byte (49)
        Mesh.WindingOrder windingOrder = Mesh.WindingOrder.Undefined;
        switch (buf.get()) {
            case 0x00:
                windingOrder = Mesh.WindingOrder.Clockwise;
                break;
            case 0x01:
                windingOrder = Mesh.WindingOrder.CounterClockwise;
                break;
            case (byte) 0xFF:
                break;
            default:
                throw new IllegalStateException();
        }

        // draw mode -- 1 byte (50)
        Mesh.DrawMode drawMode = Mesh.DrawMode.Triangles;
        switch (buf.get()) {
            case 0x00:
                break;
            case 0x01:
                drawMode = Mesh.DrawMode.TriangleStrip;
                break;
            case 0x02:
                drawMode = Mesh.DrawMode.Points;
                break;
            default:
                throw new IllegalStateException();
        }

        // num faces -- 4 bytes (54)
        int numFaces = buf.getInt();
        // num vertices -- 4 bytes (58)
        int numVertices = buf.getInt();

        // layout -- 92 bytes (150)
        VertexDataLayout layout = new VertexDataLayout();
        layout.attributes = buf.getInt();

        // vertex data layout
        /*final boolean rebuildVertexData = !srcLayout.interleaved ||
                !(m.getVertices(
                        Mesh.VERTEX_ATTR_POSITION) instanceof ByteBuffer);
        VertexDataLayout dstLayout;
        if (rebuildVertexData) {
            dstLayout = VertexDataLayout
                    .createDefaultInterleaved(srcLayout.attributes);
        } else {
            dstLayout = srcLayout;
        }*/

        layout.position.offset = buf.getInt();
        layout.position.stride = buf.getInt();
        layout.texCoord0.offset = buf.getInt();
        layout.texCoord0.stride = buf.getInt();
        layout.normal.offset = buf.getInt();
        layout.normal.stride = buf.getInt();
        layout.color.offset = buf.getInt();
        layout.color.stride = buf.getInt();
        layout.interleaved = true;
        layout.texCoord1.offset = buf.getInt();
        layout.texCoord1.stride = buf.getInt();
        layout.texCoord2.offset = buf.getInt();
        layout.texCoord2.stride = buf.getInt();
        layout.texCoord3.offset = buf.getInt();
        layout.texCoord3.stride = buf.getInt();
        layout.texCoord4.offset = buf.getInt();
        layout.texCoord4.stride = buf.getInt();
        layout.texCoord5.offset = buf.getInt();
        layout.texCoord5.stride = buf.getInt();
        layout.texCoord6.offset = buf.getInt();
        layout.texCoord6.stride = buf.getInt();
        layout.texCoord7.offset = buf.getInt();
        layout.texCoord7.stride = buf.getInt();

        // data size -- 4 bytes (150)
        int vertexDataSize = buf.getInt();

        // XXX - handle attribute typing

        ByteBuffer vertexData = Unsafe.allocateDirect(vertexDataSize);
        vertexData.order(ByteOrder.nativeOrder());

        readFully(chan, vertexData, readLimit);
        vertexData.flip();

        // index section

        buf.clear();
        buf.limit(4);
        readFully(chan, buf);
        buf.flip();

        // XXX - handle int indices
        int numIndices = buf.getInt();

        // index values
        ShortBuffer indices = null;
        if (numIndices > 0) {
            ByteBuffer sbuf = Unsafe.allocateDirect(2 * numIndices);
            sbuf.order(ByteOrder.nativeOrder());
            readFully(chan, sbuf);
            sbuf.flip();
            indices = sbuf.asShortBuffer();
        }

        // materials section
        buf.clear();
        buf.limit(4);
        readFully(chan, buf);
        buf.flip();

        int numMaterials = buf.getInt();
        Material[] materials = new Material[numMaterials];
        {
            // XXX -  need to switch to DataInputStream here to remain
            //        consistent with legacy implementation without having to
            //        reimplement DataInputStream.readUTF via NIO
            DataInputStream dis = new DataInputStream(
                    Channels.newInputStream(chan));
            for (int i = 0; i < numMaterials; ++i) {
                Material.PropertyType propType = Material.PropertyType
                        .values()[dis
                                .readInt()];
                int color = dis.readInt();
                String textureUri = dis.readUTF();
                if (textureUri.isEmpty())
                    textureUri = null;
                materials[i] = new Material(textureUri, propType, color, 0);
            }
        }

        if (indices == null)
            return MeshBuilder.build(drawMode, windingOrder, layout, materials,
                    aabb, numVertices, vertexData);
        else
            return MeshBuilder.build(drawMode, windingOrder, layout, materials,
                    aabb, numVertices, vertexData, Short.TYPE, numIndices,
                    indices);
    }

    private static Model readVersion1(ReadableByteChannel chan, int readLimit)
            throws IOException {
        ByteBuffer buf = Unsafe.allocateDirect(8192);
        buf.order(ByteOrder.nativeOrder());

        /*readFully(chan, buf);
        if (buf.get(0) != (byte) 0x01)
            return null;
        buf.clear();*/

        MemoryMappedModel retval = new MemoryMappedModel();
        //ModelBuilder builder = new ModelBuilder();

        String textureUri = "";
        // texture URI
        //if(m.getTextureUri() == null) {
        //    stream.write(0x00);
        //    stream.write(0x00);
        //} else {
        //    byte[] texUriBytes = m.getTextureUri().getBytes(Charset.forName(FileSystemUtils.UTF8_CHARSET));
        //    buf.putShort(0, (short)texUriBytes.length);
        //    stream.write(buf.array(), 0, 2);
        //   stream.write(texUriBytes);
        //}
        buf.limit(2);
        readFully(chan, buf);
        final int texUriLen = (buf.getShort(0) & 0xFFFF);
        if (texUriLen != 0) {
            if (buf.capacity() < texUriLen) {
                buf = ByteBuffer.allocate(texUriLen);
                buf.order(ByteOrder.nativeOrder());
            }
            buf.clear();
            buf.limit(texUriLen);
            int r = chan.read(buf);
            if (r != texUriLen)
                Log.d(TAG, "short read, expecting: " + texUriLen);

            buf.flip();
            textureUri = new String(buf.array(), buf.position(),
                    buf.limit(), FileSystemUtils.UTF8_CHARSET);
        } else {
            textureUri = null;
        }
        buf.clear();

        // AABB
        //Envelope aabb = m.getAABB();
        //buf.putDouble(0, aabb.minX);
        //buf.putDouble(8, aabb.minY);
        //buf.putDouble(16, aabb.minZ);
        //buf.putDouble(24, aabb.maxX);
        //buf.putDouble(32, aabb.maxY);
        //buf.putDouble(40, aabb.maxZ);
        //stream.write(buf.array(), 0, 48);
        buf.limit(48);
        readFully(chan, buf);
        buf.flip();
        Envelope aabb = new Envelope(buf.getDouble(0), buf.getDouble(8),
                buf.getDouble(16), buf.getDouble(24), buf.getDouble(32),
                buf.getDouble(40));
        buf.clear();

        // winding order
        //switch(m.getFaceWindingOrder()) {
        //    case Clockwise:
        //        stream.write(0x00);
        //        break;
        //    case CounterClockwise:
        //        stream.write(0x01);
        //        break;
        //    case Undefined:
        //        stream.write((byte)0xFF);
        //        break;
        //    default :
        //        throw new IllegalStateException();
        //}
        buf.limit(1);
        readFully(chan, buf);
        Mesh.WindingOrder windingOrder = Mesh.WindingOrder.Undefined;
        switch (buf.get(0) & 0xFF) {
            case 0x00:
                windingOrder = Mesh.WindingOrder.Clockwise;
                break;
            case 0x01:
                windingOrder = Mesh.WindingOrder.CounterClockwise;
                break;
            case 0x02:
                windingOrder = Mesh.WindingOrder.Undefined;
                break;
            default:
                throw new IllegalArgumentException();
        }
        buf.clear();
        // draw mode
        //switch(m.getDrawMode()) {
        //    case Triangles:
        //        stream.write(0x00);
        //        break;
        //    case TriangleStrip:
        //        stream.write(0x01);
        //        break;
        //    default :
        //        throw new IllegalStateException();
        //}
        buf.limit(1);
        readFully(chan, buf);
        Mesh.DrawMode drawMode;
        switch (buf.get(0) & 0xFF) {
            case 0x00:
                drawMode = Mesh.DrawMode.Triangles;
                break;
            case 0x01:
                drawMode = Mesh.DrawMode.TriangleStrip;
                break;
            default:
                throw new IllegalArgumentException();
        }
        buf.clear();
        // num faces
        //buf.putInt(0, m.getNumFaces());
        //stream.write(buf.array(), 0, 4);
        buf.limit(4);
        readFully(chan, buf);
        int numFaces = buf.getInt(0);
        buf.clear();

        // vertex section
        buf.clear();

        VertexDataLayout layout = new VertexDataLayout();

        // num vertices
        //buf.putInt(m.getNumVertices());
        buf.limit(4);
        readFully(chan, buf);
        int numVertices = buf.getInt(0);
        buf.clear();
        // vertex data attributes
        //buf.putInt(m.getVertexAttributes());
        buf.limit(4);
        readFully(chan, buf);
        layout.attributes = buf.getInt(0);
        buf.clear();

        // vertex data layout

        //buf.putInt(layout.positionOffset);
        //buf.putInt(layout.positionStride);
        //buf.putInt(layout.texCoordOffset);
        //buf.putInt(layout.texCoordStride);
        //buf.putInt(layout.normalOffset);
        //buf.putInt(layout.normalStride);
        //buf.putInt(layout.colorOffset);
        //buf.putInt(layout.colorStride);

        //buf.flip();
        //stream.write(buf.array(), buf.position(), buf.remaining());
        buf.limit(32);
        readFully(chan, buf);
        buf.flip();

        layout.position.offset = buf.getInt();
        layout.position.stride = buf.getInt();
        layout.texCoord0.offset = buf.getInt();
        layout.texCoord0.stride = buf.getInt();
        layout.normal.offset = buf.getInt();
        layout.normal.stride = buf.getInt();
        layout.color.offset = buf.getInt();
        layout.color.stride = buf.getInt();
        layout.interleaved = true;

        buf.clear();

        // vertex data size
        //buf.putInt(0, VertexDataLayout.requiredSize(layout, m.getNumVertices()));
        //stream.write(buf.array(), 0, 4);
        buf.limit(4);
        readFully(chan, buf);

        ByteBuffer vertexData = Unsafe.allocateDirect(buf.getInt(0));
        vertexData.order(ByteOrder.nativeOrder());

        readFully(chan, vertexData,
                Math.min(vertexData.remaining(), readLimit));
        vertexData.flip();
        buf.clear();

        // index section

        // XXX - handle int indices

        // num indices
        //final int numIndices = Models.getNumIndices(m);
        //buf.putInt(0, numIndices);
        //stream.write(buf.array(), 0, 4);

        buf.limit(4);
        readFully(chan, buf);
        int numIndices = buf.getInt(0);
        buf.clear();

        // index values
        ShortBuffer indices = null;
        if (numIndices > 0) {
            ByteBuffer sbuf = Unsafe.allocateDirect(2 * numIndices);
            sbuf.order(ByteOrder.nativeOrder());
            readFully(chan, sbuf, Math.min(readLimit, sbuf.remaining()));
            sbuf.flip();
            indices = sbuf.asShortBuffer();
        }

        Material[] materials = {
                new Material(textureUri, Material.PropertyType.Diffuse,
                        Color.WHITE, 0)
        };

        ModelBuilder model = new ModelBuilder();
        if (indices == null)
            model.addMesh(MeshBuilder.build(drawMode, windingOrder, layout,
                    materials, aabb, numVertices, vertexData));
        else
            model.addMesh(MeshBuilder.build(drawMode, windingOrder, layout,
                    materials, aabb, numVertices, vertexData, Short.TYPE,
                    numIndices, indices));

        return model.build();
    }
}

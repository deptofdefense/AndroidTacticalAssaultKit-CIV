
package com.atakmap.opengl;

import java.nio.Buffer;
import java.util.AbstractMap;
import java.util.LinkedHashMap;
import java.util.Map;

import com.atakmap.coremap.log.Log;

public class GLTextureCache {

    public static final String TAG = "GLTextureCache";
    protected final int maxSize;
    protected LinkedHashMap<String, BidirectionalNode> hash;
    protected BidirectionalNode head;
    protected BidirectionalNode tail;
    protected int size;
    protected int count;

    /**
     * Creates a new cache with the specified maximum size (in bytes).
     * 
     * @param maxSize The maximum size of the cache, in bytes
     */
    public GLTextureCache(int maxSize) {
        this.maxSize = maxSize;
        this.hash = new LinkedHashMap<String, BidirectionalNode>();
        this.head = null;
        this.tail = null;
        this.size = 0;
        this.count = 0;
    }

    public Entry get(String key) {
        final BidirectionalNode node = this.hash.get(key);
        if (node == null)
            return null;
        return node.value.getValue();
    }

    public Entry remove(String key) {
        final BidirectionalNode node = this.hash.remove(key);
        if (node == null)
            return null;

        final Entry retval = node.value.getValue();

        if (node.previous != null) {
            node.previous.next = node.next;
        } else if (node == this.head) {
            this.head = node.next;
            if (this.head != null)
                this.head.previous = null;
        } else {
            throw new IllegalStateException();
        }
        if (node.next != null) {
            node.next.previous = node.previous;
        } else if (node == this.tail) {
            this.tail = node.previous;
            if (this.tail != null)
                this.tail.next = null;
        } else {
            throw new IllegalStateException();
        }

        this.count--;

        this.size -= sizeOf(retval.texture);
        return retval;
    }

    public void put(String key, GLTexture texture) {
        this.put(key, texture, 0, null, null, 0, null);
    }

    public void put(String key, GLTexture texture, int hints, Object opaque) {
        this.put(key, texture, 0, null, null, hints, opaque);
    }

    public void put(String key, GLTexture texture, int numVertices, Buffer textureCoordinates,
            Buffer vertexCoordinates) {
        this.put(key, texture, numVertices, textureCoordinates, vertexCoordinates, 0, null);
    }

    public void put(String key, GLTexture texture, int numVertices, Buffer textureCoordinates,
            Buffer vertexCoordinates, int hints, Object opaque) {
        this.putImpl(key, new Entry(texture, numVertices, textureCoordinates, vertexCoordinates,
                hints, opaque));
    }

    public void put(String key, GLTexture texture, int numVertices, Buffer textureCoordinates,
            Buffer vertexCoordinates, int numIndices, Buffer indices, int hints, Object opaque) {
        this.putImpl(key, new Entry(texture, numVertices, textureCoordinates, vertexCoordinates,
                numIndices, indices, hints, opaque));
    }

    private void putImpl(String key, Entry entry) {
        Entry old = this.remove(key);
        if (old != null)
            old.texture.release();
        BidirectionalNode node = new BidirectionalNode(this.tail, key, entry);
        if (this.head == null)
            this.head = node;
        this.tail = node;
        Object o = this.hash.put(key, node);
        if (o != null)
            throw new IllegalStateException("hash already contained key: " + key);
        this.count++;
        this.size += sizeOf(entry.texture);

        this.trimToSize();
    }

    private void trimToSize() {
        if (this.size > this.maxSize)
            Log.d(TAG, "trimToSize maxSize=" + this.maxSize + " size=" + this.size);
        GLTexture toRelease;
        int releasedSize;
        while (this.size > this.maxSize && this.hash.size() > 1) {
            toRelease = this.head.value.getValue().texture;
            releasedSize = sizeOf(toRelease);
            this.hash.remove(this.head.value.getKey());
            this.head = this.head.next;
            this.head.previous = null;
            this.count--;
            toRelease.release();
            this.size -= releasedSize;
        }
    }

    public void clear() {
        this.hash.clear();
        while (this.head != null) {
            this.head.value.getValue().texture.release();
            this.head = this.head.next;
            this.count--;
        }
        this.tail = null;
        this.size = 0;
    }

    public void delete(String key) {
        final Entry toDelete = this.remove(key);
        if (toDelete != null)
            toDelete.texture.release();
    }

    /**************************************************************************/

    public static int sizeOf(GLTexture texture) {
        int bytesPerPixel;
        switch (texture.getType()) {
            case GLES20FixedPipeline.GL_UNSIGNED_BYTE:
                switch (texture.getFormat()) {
                    case GLES20FixedPipeline.GL_LUMINANCE:
                        bytesPerPixel = 1;
                        break;
                    case GLES20FixedPipeline.GL_LUMINANCE_ALPHA:
                        bytesPerPixel = 2;
                        break;
                    case GLES20FixedPipeline.GL_RGB:
                        bytesPerPixel = 3;
                        break;
                    case GLES20FixedPipeline.GL_RGBA:
                        bytesPerPixel = 4;
                        break;
                    default:
                        throw new IllegalArgumentException();
                }
                break;
            case GLES20FixedPipeline.GL_UNSIGNED_SHORT_5_5_5_1:
            case GLES20FixedPipeline.GL_UNSIGNED_SHORT_5_6_5:
                bytesPerPixel = 2;
                break;
            default:
                throw new IllegalArgumentException();
        }
        return bytesPerPixel * texture.getTexWidth() * texture.getTexHeight();
    }

    /**************************************************************************/

    private static class BidirectionalNode {
        public BidirectionalNode previous;
        public BidirectionalNode next;
        public final Map.Entry<String, Entry> value;

        public BidirectionalNode(BidirectionalNode previous, String key, Entry value) {
            this.previous = previous;
            this.next = null;
            this.value = new AbstractMap.SimpleEntry<String, Entry>(key, value);

            if (this.previous != null)
                this.previous.next = this;
        }

        public String toString() {
            return "BidirectionalNode@" + Integer.toString(this.hashCode(), 16) + " {key="
                    + this.value.getKey() + "}";
        }
    }

    public final static class Entry {
        public final GLTexture texture;
        public final Buffer textureCoordinates;
        public final Buffer vertexCoordinates;
        public final Buffer indices;
        public final int numVertices;
        public final int numIndices;
        public final int vertexCoordBufferPtr;
        public final int texCoordBufferPtr;
        public final int indexBufferPtr;
        public final int hints;
        public final Object opaque;

        private Entry(GLTexture texture, int numVertices, Buffer textureCoordinates,
                Buffer vertexCoordinates, int hints, Object opaque) {
            this(texture, numVertices, textureCoordinates, 0, vertexCoordinates, 0, 0, null, 0,
                    hints, opaque);
        }

        private Entry(GLTexture texture, int numVertices, int texCoordBufferPtr,
                int vertexCoordBufferPtr, int hints, Object opaque) {
            this(texture, numVertices, null, texCoordBufferPtr, null, vertexCoordBufferPtr, 0,
                    null, 0, hints, opaque);
        }

        private Entry(GLTexture texture, int numVertices, Buffer textureCoordinates,
                Buffer vertexCoordinates, int numIndices, Buffer indices, int hints, Object opaque) {
            this(texture, numVertices, textureCoordinates, 0, vertexCoordinates, 0, numIndices,
                    indices, 0, hints, opaque);
        }

        private Entry(GLTexture texture, int numVertices, int texCoordBufferPtr,
                int vertexCoordBufferPtr, int numIndices, int indexBufferPtr, int hints,
                Object opaque) {
            this(texture, numVertices, null, texCoordBufferPtr, null, vertexCoordBufferPtr,
                    numIndices, null, indexBufferPtr, hints, opaque);
        }

        private Entry(GLTexture texture, int numVertices, Buffer textureCoordinates,
                int texCoordBufferPtr, Buffer vertexCoordinates, int vertexCoordBufferPtr,
                int numIndices, Buffer indices, int indexBufferPtr, int hints, Object opaque) {
            this.texture = texture;
            this.numVertices = numVertices;
            this.textureCoordinates = textureCoordinates;
            this.texCoordBufferPtr = texCoordBufferPtr;
            this.vertexCoordinates = vertexCoordinates;
            this.vertexCoordBufferPtr = vertexCoordBufferPtr;
            this.numIndices = numIndices;
            this.indices = indices;
            this.indexBufferPtr = indexBufferPtr;
            this.hints = hints;
            this.opaque = opaque;
        }

        public final boolean hasHint(int flags) {
            return ((this.hints & flags) == flags);
        }
    }
}

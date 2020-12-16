package com.atakmap.map.layer.model.obj;

import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;
import com.atakmap.io.ZipVirtualFile;
import com.atakmap.lang.Unsafe;
import com.atakmap.map.layer.model.Material;
import com.atakmap.map.layer.model.MeshBuilder;
import com.atakmap.map.layer.model.Model;
import com.atakmap.map.layer.model.ModelBuilder;
import com.atakmap.map.layer.model.ModelInfo;
import com.atakmap.map.layer.model.ModelSpi;

import java.io.BufferedReader;
import java.io.File;
import java.io.FilterReader;
import java.io.IOException;
import java.io.Reader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// http://paulbourke.net/dataformats/obj/
// https://en.wikipedia.org/wiki/Wavefront_.obj_file

public final class ObjModelSpi implements ModelSpi {

    public final static String TAG = "ObjModelSpi";
    public final static ModelSpi INSTANCE = new ObjModelSpi();

    private static ByteBuffer allocate(int length) {
        ByteBuffer buf = Unsafe.allocateDirect(length);
        buf.order(ByteOrder.nativeOrder());
        return buf;
    }

    private static ByteBuffer reallocate(ByteBuffer src, int length) {
        ByteBuffer dst = Unsafe.allocateDirect(length);
        dst.order(ByteOrder.nativeOrder());
        src.flip();
        // XXX - is it quicker to do a memcpy?
        dst.put(src);
        Unsafe.free(src);
        return dst;
    }

    @Override
    public Model create(ModelInfo info) {
        return this.create(info, null);
    }
    @Override
    public Model create(ModelInfo info, Callback callback) {
        if(callback != null && callback.isProbeOnly()) {
            callback.setProbeMatch(ObjModelInfoSpi.INSTANCE.isSupported(info.uri));
            return null;
        }

        File f = new File(info.uri);;
        if(info.uri.endsWith(".zip") || info.uri.contains(".zip")) {
            try { 
                File obj = ObjUtils.findObj(new ZipVirtualFile(info.uri));
                if (obj != null)
                    f = obj;
            } catch (Exception e) { 
                Log.e(TAG, "file has issues: " + info.uri);
                return null;
            }
        }

        if(!IOProviderFactory.exists(f))
            return null;
        final long fileLength = IOProviderFactory.length(f);
        if(fileLength > 0x7FFFFFFFL)
            return null;

        ByteBuffer vertices = allocate((int)fileLength/4);
        ByteBuffer texCoords = allocate((int)fileLength/6);
        ByteBuffer vertTexCoordIndices = allocate((int)fileLength/6);

        Reader reader = null;
        try {
            long s = System.currentTimeMillis();
            reader = ObjUtils.open(f.getAbsolutePath());
            if(callback != null)
                reader = new CallbackReader(reader, (int)fileLength, callback, (int)fileLength/100);
            reader = new BufferedReader(reader, 32768);

            StringBuilder line = new StringBuilder(128);
            String[] parts;
            int code;

            ArrayList<String> faceIndices = new ArrayList<String>(3);

            parseloop: do {
                int c = reader.read();
                char ch = (char) c;
                switch (c) {
                    case -1:
                        // EOF
                        break parseloop;
                    case ' ':
                    case '\n':
                    case '\t':
                    case '\r':
                        // discard white space
                        break;
                    case 'v':
                        // next character must be space, 't' or 'n'
                        c = reader.read();
                        if ((c == -1) || !((c == 't') || (c == ' ') || (c == 'n'))) {
                            return null;
                        }
                        // read the definition
                        parts = ObjUtils.readTokens(reader, line, 1024);
                        switch (c) {
                            case ' ': // geometry vertex
                                // xyz[w] [rgba]
                                if (parts.length == 3 || parts.length == 4 || parts.length == 6 || parts.length == 7 || parts.length == 8) {
                                    if (vertices.remaining() < 12)
                                        vertices = reallocate(vertices, vertices.capacity() + ((int) fileLength / 8));

                                    vertices.putFloat(Float.parseFloat(parts[0]));
                                    vertices.putFloat(Float.parseFloat(parts[1]));
                                    vertices.putFloat(Float.parseFloat(parts[2]));
                                } else {
                                    return null;
                                }
                                break;
                            case 't': // texture coordinate
                                // uv[w]
                                if (parts.length == 2 || parts.length == 3) {
                                    if (texCoords.remaining() < 8)
                                        texCoords = reallocate(texCoords, texCoords.capacity() + ((int) fileLength / 12));
                                    texCoords.putFloat(Float.parseFloat(parts[0]));
                                    texCoords.putFloat(Float.parseFloat(parts[1]));
                                } else {
                                    return null;
                                }
                                break;
                            case 'n': // vertex normal
                                // xyz
                                if (parts.length != 3)
                                    return null;
                                break;
                            default:
                                throw new IllegalStateException();
                        }
                        break;
                    case 'f':
                        // read the face definition and populate the vertex and
                        // texcoord index buffers
                        String[] faces = ObjUtils.readTokens(reader, line, 1024);
                        for (String face : faces) {
                            splitForwardSlash(face, faceIndices);
                            if (faceIndices.size() == 2 || faceIndices.size() == 3) {
                                if (vertTexCoordIndices.remaining() < 8)
                                    vertTexCoordIndices = reallocate(vertTexCoordIndices, vertTexCoordIndices.capacity() + ((int) fileLength / 24));
                                vertTexCoordIndices.putInt(Integer.parseInt(faceIndices.get(0)));
                                vertTexCoordIndices.putInt(Integer.parseInt(faceIndices.get(1)));
                            } else {
                                return null;
                            }
                        }
                        break;
                    case 'l':
                        // check valid line definition
                        // read the definition
                        line.delete(0, line.length());
                        code = ObjUtils.advanceToNewline(reader, line, 1024);
                        if(code == ObjUtils.LIMIT)
                            return null;
                        if (line.toString().matches("(\\s+\\d+)+\\s*"))
                            return null;
                        if (code == ObjUtils.EOF)
                            break parseloop;

                        break;
                    case '#':
                        code = ObjUtils.advanceToNewline(reader, null, 1024);
                        if(code == ObjUtils.EOF)
                            break parseloop;
                        else if(code == ObjUtils.LIMIT)
                            return null;
                        break;
                    case 'm':
                        if (!ObjUtils.advanceThroughWord(reader, "tllib"))
                            return null;
                        // XXX - handle material file
                        code = ObjUtils.advanceToNewline(reader, null, 1024);
                        if(code == ObjUtils.EOF)
                            break parseloop;
                        else if(code == ObjUtils.LIMIT)
                            return null;
                        break;
                    case 'g' :   // could still be valid, just skip over this and advance
                    case 'o' :   // could still be valid, just skip over this and advance
                        code = ObjUtils.advanceToNewline(reader, null, 1024);
                        if(code == ObjUtils.LIMIT)
                            return null;
                        else if(code == ObjUtils.EOF)
                            break parseloop;
                        break;
                    case 'u':
                        if (!ObjUtils.advanceThroughWord(reader, "semtl"))
                            return null;
                        // XXX - handle material file
                        code = ObjUtils.advanceToNewline(reader, null, 1024);
                        if(code == ObjUtils.EOF)
                            break parseloop;
                        else if(code == ObjUtils.LIMIT)
                            return null;
                        break;
                    // XXX - others
                    default:
                        return null;
                }
            } while (true);

            long e = System.currentTimeMillis();

            Log.d(TAG, "Parse OBJ file in " + (e-s) + "ms");

            vertTexCoordIndices.flip();
            if(vertTexCoordIndices.remaining() == 0)
                return null;

            if(texCoords.position() == 0 || vertices.position() == 0)
                return null;

            // derive the base filename
            String baseFileName = f.getName();
            if(baseFileName.endsWith("_simplified_3d_mesh.obj")) {
                baseFileName = baseFileName.replace("_simplified_3d_mesh.obj", "");
            } else {
                baseFileName = baseFileName.substring(0, baseFileName.lastIndexOf('.'));
            }

            s = System.currentTimeMillis();
            MeshBuilder builder = new MeshBuilder(Model.VERTEX_ATTR_POSITION|Model.VERTEX_ATTR_TEXCOORD_0, false, Model.DrawMode.Triangles);
            builder.setWindingOrder(Model.WindingOrder.CounterClockwise);

            final int numIndices = (vertTexCoordIndices.remaining()/8);
            builder.reserveVertices(numIndices);

            final int updateInterval = numIndices/100;
            for(int i = 0; i < numIndices; i++) {
                int vertexCoordOff = (vertTexCoordIndices.getInt()-1)*12;
                int texCoordOff = (vertTexCoordIndices.getInt()-1)*8;

                float x = vertices.getFloat(vertexCoordOff);
                float y = vertices.getFloat(vertexCoordOff+4);
                float z = vertices.getFloat(vertexCoordOff+8);
                float u = texCoords.getFloat(texCoordOff);
                float v = texCoords.getFloat(texCoordOff+4);
                builder.addVertex(x, y, z,
                                  u, 1f-v,
                                  0, 0, 0,
                                  1, 1, 1, 1);

                if(callback != null) {
                    if((i%updateInterval) == 0) {
                        callback.progress(50 + (int)(((double)i/(double)numIndices) * 50d));
                    }
                }
            }
            e = System.currentTimeMillis();
            Log.d(TAG, "Build model in " + (e-s) + "ms");

            // XXX - check for material file
            File textureFile = null;
            final File materialFile = ObjUtils.getSibling(f, f.getName().replace(".obj", ".mtl"));
            if(IOProviderFactory.exists(materialFile)) {
                try {
                    Map<String, String> materials = ObjUtils.extractMaterialTextures(materialFile);
                    for(String filename : materials.values()) {
                        textureFile = new File(f.getParentFile(), filename);
                        if(IOProviderFactory.exists(textureFile))
                            break;
                        textureFile = null;
                    }
                } catch(Throwable ignored) {}
            }
            if(textureFile == null || !IOProviderFactory.exists(textureFile)) {
                String[] exts = new String[]
                        {
                                "_texture.jpg",
                                "_texture.jpeg",
                                "_texture.png",
                                ".jpg",
                                ".jpeg",
                                ".png",
                        };
                textureFile = ObjUtils.findFile(f.getParentFile(), baseFileName, exts);
            }
            if(textureFile != null)
                builder.addMaterial(new Material(textureFile.getAbsolutePath(), Material.PropertyType.Diffuse, -1));
            return ModelBuilder.build(builder.build());
        } catch(Throwable t) {
            if(callback != null)
                callback.errorOccurred(null, t);
            Log.e(TAG, "error", t);
            return null;
        } finally {
            if(reader != null)
                try {
                    reader.close();
                } catch(Throwable ignored) {}
        }
    }

    @Override
    public String getType() {
        return "OBJ";
    }

    @Override
    public int getPriority() {
        return 1;
    }

    private static void splitWhitespace(String src, List<String> dst) {
        dst.clear();
        final int len = src.length();
        if(len < 1)
            return;
        int startIdx = 0;
        boolean inToken = !Character.isWhitespace(src.charAt(0));
        for(int i = 1; i < len; i++) {
            final char c = src.charAt(i);
            if(Character.isWhitespace(c)) {
                if(inToken) {
                    dst.add(src.substring(startIdx, i));
                    inToken = false;
                }
            } else if(!inToken) {
                inToken = true;
                startIdx = i;
            }
        }

        if(inToken)
            dst.add(src.substring(startIdx, len));
    }

    private static void splitForwardSlash(String src, List<String> dst) {
        dst.clear();
        final int len = src.length();
        if(len < 1)
            return;
        int startIdx = 0;
        boolean inToken = (src.charAt(0) != '/');
        for(int i = 1; i < len; i++) {
            final char c = src.charAt(i);
            if(c == '/') {
                if(inToken) {
                    dst.add(src.substring(startIdx, i));
                    inToken = false;
                }
            } else if(!inToken) {
                inToken = true;
                startIdx = i;
            }
        }

        if(inToken)
            dst.add(src.substring(startIdx, len));
    }

    private static class CallbackReader extends FilterReader {

        private ModelSpi.Callback callback;
        private int maxCount;
        private int count;
        private int lastUpdate;
        private int updateInterval;

        CallbackReader(Reader in, int maxCount, ModelSpi.Callback callback, int updateInterval) {
            super(in);
            lastUpdate = 0;
            this.count = 0;
            this.maxCount = maxCount;
            this.updateInterval = updateInterval;
            this.callback = callback;
        }

        @Override
        public int read() throws IOException {
            int retval = super.read();
            if(retval != -1) {
                count++;
                if(count-lastUpdate >= updateInterval) {
                    callback.progress((int) (((double) count / (double) maxCount) * 50d));
                    lastUpdate = count;
                }
            }
            return retval;
        }

        @Override
        public int read(char[] arr, int off, int len) throws IOException {
            int retval = super.read(arr, off, len);
            if(retval != -1) {
                count += retval;
                if(count-lastUpdate >= updateInterval) {
                    callback.progress((int) (((double) count / (double) maxCount) * 50d));
                    lastUpdate = count;
                }
            }
            return retval;
        }
    }
}

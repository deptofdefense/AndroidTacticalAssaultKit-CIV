package com.atakmap.map.layer.model.obj;

import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;
import com.atakmap.io.ZipVirtualFile;
import com.atakmap.coremap.locale.LocaleUtil;
import com.atakmap.util.zip.IoUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;

/**
 * Various utilities used for the parsing of Obj files or Zipped Obj Files.
 */
public final class ObjUtils {
    public final static int SUCCESS = 0;
    public final static int EOF = 1;
    public final static int LIMIT = 2;
    private final static String TAG = "ObjUtils";

    private ObjUtils() {}

    static int advanceToNewline(Reader reader, StringBuilder data, int limit) throws IOException {
        int count = 0;
        while(count < limit) {
            int c = reader.read();
            if(c == -1)
                return EOF;
            if(c == '\n')
                return SUCCESS;
            if(data != null)
                data.append((char)c);
            count++;
        }

        return LIMIT;
    }
    static boolean advanceThroughWord(Reader reader, String word) throws IOException {
        final int length = word.length();
        for(int i = 0; i < length; i++) {
            int c = reader.read();
            if(c == -1)
                return false;
            if((char)c != word.charAt(i))
                return false;
        }
        return true;
    }
    static String[] readTokens(Reader reader, StringBuilder sb, int limit) throws IOException {
        if(sb != null)
            sb.delete(0, sb.length());
        else
            sb = new StringBuilder();

        int count = 0;
        LinkedList<String> tokens = new LinkedList<String>();
        boolean inToken = false;
        while(count < limit) {
            int c = reader.read();
            if(c == -1)
                break;
            if(c == '\n')
                break;
            final boolean wsp = Character.isWhitespace(c);
            if(wsp && inToken) {
                tokens.add(sb.toString());
                sb.delete(0, sb.length());
            } else if(!wsp) {
                sb.append((char)c);
            }
            inToken = !wsp;
            count++;
        }

        if(count == limit)
            throw new IOException();

        if(inToken)
            tokens.add(sb.toString());

        return tokens.toArray(new String[0]);
    }

    static Map<String, String> extractMaterialTextures(File material) throws IOException {
        Reader reader = null;
        try {
            if(material instanceof ZipVirtualFile)
                reader = new BufferedReader(new InputStreamReader(((ZipVirtualFile)material).openStream()));
            else
                reader = new BufferedReader(IOProviderFactory.getFileReader(material));
            StringBuilder line = new StringBuilder();
            String newmtl = null;
            Map<String, String> retval = new HashMap<String, String>();
            while(true) {
                line.delete(0, line.length());
                int code = advanceToNewline(reader, line, 1024);
                if(code == LIMIT)
                    break;
                String s = line.toString().trim();
                if(s.startsWith("newmtl")) {
                    newmtl = s.substring(6).trim();
                } else if(s.startsWith("map_")) {
                    String textureName = null;
                    for(int i = s.length()-1; i >= 4; i--) {
                        if(Character.isWhitespace(s.charAt(i))) {
                            textureName = s.substring(i+1).trim();
                            break;
                        }
                    }
                    if(textureName == null)
                        newmtl = null;
                    else
                        retval.put(newmtl, textureName);
                }
                if(code == EOF)
                    break;
            }
            return retval;
        } finally {
            IoUtils.close(reader, TAG);
        }
    }

    /**
     * Open a uri providing a reader directly.   Checks for a file URI or defaults back through
     * failure to a ZipVirtualFile.
     * @param uri the uri to open as a reader.
     * @return the valid reader or null if no uri exists.
     * @throws IOException if their is an issue opening the file
     */
    static Reader open(final String uri) throws IOException {
        File f = new File(uri);
        if(IOProviderFactory.exists(f))
            return IOProviderFactory.getFileReader(f);
        ZipVirtualFile zf = new ZipVirtualFile(uri);
        if(IOProviderFactory.exists(zf))
            return new InputStreamReader(zf.openStream());
        return null;
    }


    /**
     * Used as a last ditch effort to find a file that might be appropriate for a model.
     * In this example a model was generated, but the xyz and prj files do not have the correct
     * name.  This is a case insensitive search.
     * @param f  the directory or zip file to
     * @param ext the specific extension to look for when searching
     * @return the first occurance of a match for the extension based on the directory or zip file.
     */
    public static File findFirst(final File f, final String ext) {
        if (ext == null)
           return null;

        try {
            final File[] children = IOProviderFactory.listFiles(f);
            if (children != null) {
                for (File c : children) {
                    if (c.getName().toLowerCase(LocaleUtil.US).
                                 endsWith(ext.toLowerCase(LocaleUtil.US)))
                        return c;
                }
            }
            return null;
        } catch (Exception e) {
            return null;
        }

            
    }

    public static File findObj(ZipVirtualFile f) {
        if(IOProviderFactory.isDirectory(f)) {
            File[] children = IOProviderFactory.listFiles(f);
            if (children != null) { 
                for(File c : children) {
                    File r = findObj((ZipVirtualFile)c);
                    if(r != null)
                        return r;
                }
            }
            return null;
        } else if(f.getName().endsWith(".obj")) {
            return f;
        } else {
            return null;
        }
    }

    /**
     * Returns the file with the given name that shares the same parent.
     * @param f             A file
     * @param siblingName   The name of a file that is sibling to <code>f</code>
     * @return
     */
    static File getSibling(File f, String siblingName) {
        if(f instanceof ZipVirtualFile)
            return new ZipVirtualFile(f.getParentFile(), siblingName);
        else
            return new File(f.getParentFile(), siblingName);
    }

    /**
     * Given a directory, basename and a set of extensions, return the first file
     * that exists in a directory and matches.
     * @param directory the directory to look in.
     * @param base the name of the file without the extension.
     * @param exts a list of extensions.
     */
    public static File findFile(File directory, String base, String[] exts) {
        if(!(directory instanceof ZipVirtualFile))
            return FileSystemUtils.findFile(directory, base, exts);
        if (exts != null && base != null) {
            for (String ext : exts) {
                File f = new ZipVirtualFile(directory, base + ext);
                if (IOProviderFactory.exists(f))
                    return f;
            }
        }
        return null;
    }

    public static String copyStreamToString(File f) throws IOException {
        if(!(f instanceof ZipVirtualFile))
            return FileSystemUtils.copyStreamToString(f);
        return FileSystemUtils.copyStreamToString(((ZipVirtualFile)f).openStream(), true, FileSystemUtils.UTF8_CHARSET);
    }

    /**
     * Search an OBJ file for the "mtllib" definition
     *
     * @return .mtl file or null if not found
     */
    public static File findMaterialLibrary(File obj) {
        StringBuilder lib = new StringBuilder();
        try(InputStream fis = IOProviderFactory.getInputStream(obj)) {
            byte[] mtlBytes = "mtllib ".getBytes(FileSystemUtils.UTF8_CHARSET);
            int mtlLength = mtlBytes.length;
            int numRead;
            boolean stopAtNL = false;
            byte[] buf = new byte[FileSystemUtils.BUF_SIZE * 8];
            outer: while ((numRead = fis.read(buf)) > -1) {
                for (int i = 0; i < numRead - mtlLength;) {
                    if (stopAtNL) {
                        if (buf[i] == '\n' || buf[i] == '\r')
                            break outer;
                        lib.append((char) buf[i++]);
                        continue;
                    }
                    boolean equals = true;
                    int j = 0;
                    for (; j < mtlLength; j++) {
                        if (buf[i + j] != mtlBytes[j]) {
                            equals = false;
                            j++;
                            break;
                        }
                    }
                    i += j;
                    if (equals) {
                        mtlLength = 0;
                        stopAtNL = true;
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to find mtllib in " + obj, e);
        }
        String name = FileSystemUtils.sanitizeFilename(lib.toString());
        if (FileSystemUtils.isEmpty(name))
            return null;
        return new File(obj.getParent(), name);
    }

    /**
     * Find all linked materials within an MTL file
     *
     * @param mtl .mtl file
     * @return List of materials
     */
    public static Set<File> findMaterials(File mtl) {
        Set<File> ret = new HashSet<>();
        try (Reader r = IOProviderFactory.getFileReader(mtl);
             BufferedReader br = new BufferedReader(r)) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.startsWith("map_")
                        || line.startsWith("disp")
                        || line.startsWith("decal")
                        || line.startsWith("bump")) {
                    int lastSpace = line.lastIndexOf(' ');
                    if (lastSpace == -1)
                        continue;
                    String name = FileSystemUtils.sanitizeFilename(
                            line.substring(lastSpace + 1));
                    if (FileSystemUtils.isEmpty(name))
                        continue;
                    File f = new File(mtl.getParent(), name);
                    if (IOProviderFactory.exists(f))
                        ret.add(f);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to find materials in " + mtl, e);
        }
        return ret;
    }
}

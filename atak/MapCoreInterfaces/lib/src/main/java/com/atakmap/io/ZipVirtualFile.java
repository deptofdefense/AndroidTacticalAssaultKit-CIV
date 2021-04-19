
package com.atakmap.io;

/*
 * ZipVirtualFile.java
 *
 * Created on June 5, 2013, 7:55 AM
 */

import java.io.*;
import java.util.*;

import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;
import com.atakmap.util.ReferenceCount;
import com.atakmap.util.zip.ZipEntry;
import com.atakmap.util.zip.ZipFile;

/**
 * @author Developer
 */
public class ZipVirtualFile extends File {

    private static final long serialVersionUID = 1L;
    public static final String TAG = "ZipVirtualFile";

    private static final Map<File, OpenArchiveSpec> openZipFiles = new HashMap<File, OpenArchiveSpec>();
    private static final Map<File, ReferenceCount<HierarchicalZipFile>> mounts = new HashMap<>();

    private File zipFile;
    private final HierarchicalZipFile shadow;
    private String entryPath;

    public ZipVirtualFile(String parent, String child) {
        this(new File(parent), child);
    }

    public ZipVirtualFile(File parent, String child) {
        this((parent instanceof ZipVirtualFile)
                ? ((ZipVirtualFile) parent).zipFile
                : getZipFile(parent),
                (parent instanceof ZipVirtualFile)
                        ? ((ZipVirtualFile) parent).shadow.root
                        : null,
                null,
                getZipEntryPath(
                        parent.getAbsolutePath() + File.separatorChar + child));
    }

    public ZipVirtualFile(File f) {
        this((f instanceof ZipVirtualFile) ? ((ZipVirtualFile) f).zipFile
                : getZipFile(f),
                (f instanceof ZipVirtualFile) ? ((ZipVirtualFile) f).shadow.root
                        : null,
                null,
                getZipEntryPath(f));
    }

    public ZipVirtualFile(String path) {
        this(getZipFile(path), null, null, getZipEntryPath(path));
    }

    private ZipVirtualFile(File zip, HierarchicalZipFile shadow) {
        this(zip, shadow, null);
    }

    private ZipVirtualFile(File zip, HierarchicalZipFile shadow,
            String entryPath) {
        this(zip,
                (shadow != null) ? shadow.root : null,
                shadow,
                entryPath);
    }

    private ZipVirtualFile(File zip, HierarchicalZipFile shadowRoot,
            HierarchicalZipFile shadow, String entryPath) {
        super("");
        if (zip == null)
            throw new IllegalArgumentException("not a zip file.");

        this.zipFile = zip;
        if (shadow == null) {
            if (shadowRoot == null) {
                synchronized (mounts) {
                    ReferenceCount<HierarchicalZipFile> ref = mounts.get(zip);
                    if (ref != null)
                        shadowRoot = ref.value;
                }
                if (shadowRoot == null) {
                    try {
                        shadowRoot = buildHierarchy(this.zipFile);
                    } catch (IOException e) {
                        throw new IllegalStateException(e);
                    }
                }
            }
            shadow = findInHierarchy(shadowRoot, entryPath);
        }

        this.shadow = shadow;
        if (this.shadow == null)
            this.entryPath = entryPath;
    }

    public String toString() {
        StringBuilder retval = new StringBuilder(
                this.zipFile.getAbsolutePath());
        if (this.shadow != null) {
            retval.append(File.separatorChar);
            retval.append(this.shadow.entry.getName());
        } else if (this.entryPath != null) {
            retval.append(File.separatorChar);
            retval.append(this.entryPath);
        }
        return retval.toString();
    }

    /**
     * Returns the underlying zip file.
     * 
     * @return The underlying zip file.
     */
    public File getZipFile() {
        return this.zipFile;
    }

    /**
     * Returns the path of the associated zip file entry.
     * 
     * @return The path of the associated zip file entry
     */
    public String getEntryPath() {
        if (this.entryPath != null)
            return this.entryPath;

        StringBuilder retval = new StringBuilder();

        HierarchicalZipFile f = this.shadow;
        do {
            retval.insert(0, f.name);
            f = f.parent;
        } while (f != null);

        if (retval.length() > 0) {
            char last = retval.charAt(retval.length() - 1);
            if (last == '/' || last == '\\')
                retval.delete(retval.length() - 1, retval.length());
        }
        return retval.toString();
    }

    public ZipEntry getEntry() {
        return this.shadow.entry;
    }

    /**
     * Opens a new {@link java.io.InputStream} to read from the zip entry associated with this
     * <code>ZipVirtualFile</code> instance.
     * <P>
     * It is important to invoke {@link java.io.InputStream#close()} when the returned stream is no
     * longer needed.
     * <P>
     * Please note, there are conditions that this will throw IllegalArgumentException as well as the 
     * specified IOException.
     */
    public InputStream openStream() throws IOException {
        return new VSIZipFileInputStream();
    }

    /**
     * Batch mode should be activated when repeated, uninterrupted access is required to one or more
     * of the entries (via {@link #openStream} in the source zip file. This keeps the underlying
     * file pointer open and will reduce the time required to open streams.
     * <P>
     * It is important to deactivate batch mode when no longer required to ensure that this instance
     * becomes eligible for garbage collection.
     */
    public void setBatchMode(boolean b) throws IOException {
        synchronized (ZipVirtualFile.class) {
            OpenArchiveSpec spec = openZipFiles.get(this.zipFile);
            if (b) {
                if (spec == null)
                    openZipFiles.put(this.zipFile,
                            spec = new OpenArchiveSpec(this.zipFile));
                spec.batchmode.add(this);
            } else if (spec != null) {
                spec.batchmode.remove(this);
                if (spec.refs.size() < 1 && spec.batchmode.size() < 1) {
                    spec.source.close();
                    openZipFiles.remove(this.zipFile);
                }
            }
        }
    }

    /**************************************************************************/

    @Override
    public String getName() {
        if (this.shadow != null) {
            if (this.shadow.parent == null)
                return this.zipFile.getName();

            String path = this.getEntryPath();
            if (path.lastIndexOf('\\') >= 0)
                path = path.substring(path.lastIndexOf('\\') + 1);
            else if (path.lastIndexOf('/') >= 0)
                path = path.substring(path.lastIndexOf('/') + 1);
            return path;
        }
        return "null";
    }

    @Override
    public String getParent() {
        ZipVirtualFile parentFile = (ZipVirtualFile) this.getParentFile();
        if (parentFile == null)
            return null;
        return parentFile.getPath();
    }

    @Override
    public File getParentFile() {
        if (this.shadow.parent == null)
            return this.zipFile.getParentFile();
        return new ZipVirtualFile(this.zipFile, this.shadow.parent);
    }

    @Override
    public String getPath() {
        return this.zipFile.getPath() + File.separator + this.getEntryPath();
    }

    /* -- Path operations -- */

    @Override
    public String getAbsolutePath() {
        return this.zipFile.getAbsolutePath() + File.separator
                + this.getEntryPath();
    }

    @Override
    public File getAbsoluteFile() {
        return new ZipVirtualFile(this.zipFile.getAbsoluteFile(), this.shadow,
                this.entryPath);
    }

    @Override
    public String getCanonicalPath() throws IOException {
        return this.zipFile.getCanonicalPath() + File.separator
                + this.getEntryPath();
    }

    @Override
    public File getCanonicalFile() throws IOException {
        return new ZipVirtualFile(this.zipFile.getCanonicalFile(), this.shadow,
                this.entryPath);
    }

    /* -- Attribute accessors -- */

    @Override
    public boolean canRead() {
        return this.zipFile.canRead();
    }

    @Override
    public boolean canWrite() {
        return false;
    }

    @Override
    public boolean exists() {
        return (this.shadow != null);
    }

    @Override
    public boolean isDirectory() {
        if (!IOProviderFactory.exists(this))
            return false;
        return this.shadow.isDirectory();
    }

    @Override
    public boolean isFile() {
        // XXX - is the root both a file and a directory ???
        return !this.isDirectory();
    }

    @Override
    public boolean isHidden() {
        return this.zipFile.isHidden();
    }

    @Override
    public long lastModified() {
        if (!IOProviderFactory.exists(this))
            return 0L;
        return this.shadow.entry.getTime();
    }

    @Override
    public long length() {
        if (!IOProviderFactory.exists(this))
            return 0L;
        return this.shadow.entry.getSize();
    }

    /* -- File operations -- */

    @Override
    public boolean createNewFile() throws IOException {
        return false;
    }

    @Override
    public boolean delete() {
        return false;
    }

    @Override
    public void deleteOnExit() {
        // not supported
    }

    @Override
    public String[] list() {
        return this.list(null);
    }

    @Override
    public String[] list(FilenameFilter filter) {
        if (!IOProviderFactory.exists(this) || !this.shadow.isDirectory())
            return null;

        File[] list = this.listFiles(filter);
        if (list == null)
            return null;

        String[] retval = new String[list.length];
        for (int i = 0; i < list.length; i++)
            retval[i] = list[i].getName();
        return retval;
    }

    @Override
    public File[] listFiles() {
        return listFiles(AcceptAllFileFilter.INSTANCE);
    }

    @Override
    public File[] listFiles(FilenameFilter filter) {
        if (filter != null)
            return this.listFiles(new FilenameFileFilter(filter));
        else
            return this.listFiles();
    }

    @Override
    public File[] listFiles(FileFilter filter) {
        if (!IOProviderFactory.exists(this) || !this.shadow.isDirectory())
            return null;

        HierarchicalZipFile[] children = this.shadow.getChildren();
        if (children == null)
            return null;

        ArrayList<File> retval = new ArrayList<File>(children.length);
        ZipVirtualFile f;
        for (int i = 0; i < children.length; i++) {
            f = new ZipVirtualFile(this.zipFile, children[i]);
            if (filter == null || filter.accept(f))
                retval.add(f);
        }

        return retval.toArray(new File[0]);
    }

    @Override
    public boolean mkdir() {
        return false;
    }

    @Override
    public boolean mkdirs() {
        return false;
    }

    @Override
    public boolean renameTo(File dest) {
        return false;
    }

    @Override
    public boolean setLastModified(long time) {
        return false;
    }

    @Override
    public boolean setReadOnly() {
        return false;
    }

    @Override
    public boolean setWritable(boolean writable, boolean ownerOnly) {
        return false;
    }

    @Override
    public boolean setWritable(boolean writable) {
        return false;
    }

    @Override
    public boolean setReadable(boolean readable, boolean ownerOnly) {
        return false;
    }

    @Override
    public boolean setReadable(boolean readable) {
        return false;
    }

    @Override
    public boolean setExecutable(boolean executable, boolean ownerOnly) {
        return false;
    }

    @Override
    public boolean setExecutable(boolean executable) {
        return false;
    }

    @Override
    public boolean canExecute() {
        return this.zipFile.canExecute();
    }

    /* -- Disk usage -- */

    @Override
    public long getTotalSpace() {
        return IOProviderFactory.length(zipFile);
    }

    @Override
    public long getFreeSpace() {
        return 0L;
    }

    @Override
    public long getUsableSpace() {
        return 0L;
    }

    /* -- Basic infrastructure -- */

    @Override
    public int compareTo(File pathname) {
        throw new UnsupportedOperationException();
    }

    public boolean equals(Object obj) {
        if (obj instanceof ZipVirtualFile)
            return super.equals(obj);
        return false;
    }

    public int hashCode() {
        return this.getAbsolutePath().hashCode();
    }

    /**************************************************************************/

    private static String sanitize(String path) {
        // Remove file:// or zip:// from the path in case a URI is passed in
        int scheme = path.indexOf("://");
        if (scheme != -1)
            path = path.substring(scheme + 3);
        return path;
    }

    private static File getZipFile(String f) {
        return getZipFile(new File(sanitize(f)));
    }

    private static File getZipFile(File f) {
        if (f instanceof ZipVirtualFile)
            return ((ZipVirtualFile) f).getZipFile();
        if (!IOProviderFactory.exists(f) && f.getParentFile() != null)
            return getZipFile(f.getParentFile());
        ZipFile z = null;
        try {
            z = new ZipFile(f);
            z.entries();

            return f;
        } catch (IOException e) {
            Log.w(TAG, e.getMessage() + " " + f.getAbsolutePath());

            return null;
        } finally {
            if (z != null)
                try {
                    z.close();
                } catch (IOException ignored) {
                }
        }
    }

    private static String getZipEntryPath(String f) {
        return getZipEntryPath(new File(sanitize(f)));
    }

    private static String getZipEntryPath(File f) {
        File zipFile = getZipFile(f);
        if (zipFile == null)
            return null;
        String retval = f.getPath().substring(zipFile.getPath().length());
        if (retval.length() >= 1
                && (retval.charAt(0) == '/' || retval.charAt(0) == '\\'))
            retval = retval.substring(1);
        if (retval.length() < 1)
            return null;
        return retval;
    }

    private static HierarchicalZipFile buildHierarchy(File file)
            throws IOException {
        ZipFile zipFile = null;
        try {
            zipFile = new ZipFile(file);

            HierarchicalZipFile root = new HierarchicalZipFile();
            root.entry.setTime(IOProviderFactory.lastModified(file));
            root.entry.setSize(0L);

            buildHierarchy(Collections.singletonMap("", root),
                    (EnumerationIterator<ZipEntry>) new EnumerationIterator(
                            zipFile.entries()),
                    0);
            root.resolved();

            return root;
        } finally {
            if (zipFile != null)
                zipFile.close();
        }
    }

    private static void buildHierarchy(
            Map<String, HierarchicalZipFile> previousDirs,
            Iterator<ZipEntry> entriesIter, int depth) {
        Map<String, HierarchicalZipFile> currentDirs = new LinkedHashMap<String, HierarchicalZipFile>();
        LinkedList<ZipEntry> delayed = new LinkedList<ZipEntry>();

        ZipEntry entry;
        HierarchicalZipFile p;
        HierarchicalZipFile f;
        while (entriesIter.hasNext()) {
            entry = entriesIter.next();

            if (getDepth(entry.getName()) == depth) {
                p = previousDirs.get(getParentPath(entry.getName(), depth));
                if (p == null)
                    throw new IllegalStateException();
                if (!entry.isDirectory()) {
                    f = new HierarchicalZipFile(p,
                            getChildPath(entry.getName(), depth), entry);
                } else if (!currentDirs.containsKey(entry.getName())) {
                    f = new HierarchicalZipFile(p,
                            getChildPath(entry.getName(), depth), entry);
                    currentDirs.put(entry.getName(), f);
                }
            } else {
                String depthAncestor = getParentPath(entry.getName(),
                        depth + 1);
                if (depthAncestor != null
                        && !currentDirs.containsKey(depthAncestor)) {
                    p = previousDirs.get(getParentPath(depthAncestor, depth));
                    if (p == null)
                        throw new IllegalStateException();
                    f = new HierarchicalZipFile(p,
                            getChildPath(depthAncestor, depth),
                            new ZipEntry(depthAncestor));
                    currentDirs.put(depthAncestor, f);
                }

                delayed.add(entry);
            }
        }

        if (delayed.size() > 0)
            buildHierarchy(currentDirs, delayed.iterator(), depth + 1);

        Iterator<HierarchicalZipFile> iter = currentDirs.values().iterator();
        while (iter.hasNext())
            iter.next().resolved();
    }

    private static int getDepth(String path) {
        // length-1 to ignore trailing separator
        int depth = 0;
        for (int i = 0; i < path.length() - 1; i++)
            if (path.charAt(i) == '/' || path.charAt(i) == '\\')
                depth++;
        return depth;
    }

    private static String getParentPath(String path, int depth) {
        int d = 0;
        int idx = 0;
        for (int i = 0; i < path.length() - 1; i++) {
            if (path.charAt(i) == '/' || path.charAt(i) == '\\') {
                if ((++d) == depth) {
                    idx = i;
                    break;
                }
            }
        }
        if (d != depth)
            return null;
        if (depth > 0)
            idx++;
        return path.substring(0, idx);
    }

    private static String getChildPath(String path, int depth) {
        int d = 0;
        int idx = 0;
        for (int i = 0; i < path.length() - 1; i++) {
            if (path.charAt(i) == '/' || path.charAt(i) == '\\') {
                if ((++d) == depth) {
                    idx = i;
                    break;
                }
            }
        }
        if (d != depth)
            return null;
        if (depth > 0)
            idx++;
        return path.substring(idx);
    }

    private static HierarchicalZipFile findInHierarchy(HierarchicalZipFile root,
            String entryPath) {
        if (entryPath == null)
            return root;
        String[] pathComponents = entryPath.split("[\\/\\\\]");
        HierarchicalZipFile match = root;
        for (int i = 0; i < pathComponents.length; i++) {
            if (pathComponents[i].equals(".") || pathComponents[i].equals("")) {
                continue;
            } else if (pathComponents[i].equals("..")) {
                match = match.parent;
            } else {
                HierarchicalZipFile[] children = match.getChildren();
                match = null;
                if (children != null) {
                    for (int j = 0; j < children.length; j++) {
                        String name = children[j].name;
                        name = name.replaceAll("[\\/\\\\]", "");
                        if (name.equals(pathComponents[i])) {
                            match = children[j];
                            break;
                        }
                    }
                }
            }
            if (match == null)
                break;
        }
        return match;
    }

    private static synchronized InputStream openStream(ZipVirtualFile f)
            throws IOException {
        if (f.shadow == null || f.shadow.entry == null || f.isDirectory())
            throw new IllegalArgumentException();

        OpenArchiveSpec spec = openZipFiles.get(f.zipFile);
        if (spec == null)
            openZipFiles.put(f.zipFile, spec = new OpenArchiveSpec(f.zipFile));
        InputStream retval = spec.source.getInputStream(f.shadow.entry);
        spec.refs.add(retval);
        return retval;
    }

    /**************************************************************************/

    private static class EnumerationIterator<T> implements Iterator<T> {
        private final Enumeration<T> enumeration;

        public EnumerationIterator(Enumeration<T> enumeration) {
            this.enumeration = enumeration;
        }

        @Override
        public boolean hasNext() {
            return this.enumeration.hasMoreElements();
        }

        @Override
        public T next() {
            return this.enumeration.nextElement();
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }
    }

    private static class AcceptAllFileFilter implements FileFilter {
        public final static FileFilter INSTANCE = new AcceptAllFileFilter();

        private AcceptAllFileFilter() {
        }

        @Override
        public boolean accept(File f) {
            return true;
        }
    }

    private static class FilenameFileFilter implements FileFilter {
        private final FilenameFilter filter;

        FilenameFileFilter(FilenameFilter filter) {
            this.filter = filter;
        }

        @Override
        public boolean accept(File f) {
            return this.filter.accept(f.getParentFile(), f.getName());
        }
    }

    private static class HierarchicalZipFile {
        public final ZipEntry entry;
        public final String name;
        public final HierarchicalZipFile parent;
        public final HierarchicalZipFile root;

        private Collection<HierarchicalZipFile> children;
        private HierarchicalZipFile[] childrenArray;

        public HierarchicalZipFile() {
            this(null, "", new ZipEntry("/"));
        }

        public HierarchicalZipFile(HierarchicalZipFile parent, String name,
                ZipEntry entry) {
            this.entry = entry;
            this.name = name;
            this.parent = parent;
            this.root = (this.parent != null) ? this.parent.root : this;

            if (parent != null)
                parent.addChild(this);

            this.children = new LinkedList<HierarchicalZipFile>();
            this.childrenArray = null;
        }

        public HierarchicalZipFile[] getChildren() {
            if (!this.isDirectory())
                return null;
            if (this.childrenArray == null)
                throw new IllegalStateException();
            return this.childrenArray;
        }

        public boolean isDirectory() {
            return (this.entry == null || this.entry.isDirectory());
        }

        protected void addChild(HierarchicalZipFile child) {
            if (this.childrenArray != null || !this.isDirectory())
                throw new IllegalStateException();
            this.children.add(child);
        }

        void resolved() {
            this.childrenArray = new HierarchicalZipFile[this.children.size()];
            this.children.toArray(this.childrenArray);
        }

        public String toString() {
            return "HierarchicalZipFile {" + entry.getName() + "}";
        }
    }

    private final class VSIZipFileInputStream extends FilterInputStream {
        /** Creates a new instance of VSIZipFileInputStream */
        public VSIZipFileInputStream() throws IOException {
            super(openStream(ZipVirtualFile.this));
        }

        @Override
        public void close() throws IOException {
            try {
                synchronized (ZipVirtualFile.class) {
                    OpenArchiveSpec spec = openZipFiles
                            .get(ZipVirtualFile.this.zipFile);
                    if (spec != null) {
                        spec.refs.remove(this.in);
                        if (spec.refs.size() < 1 && spec.batchmode.size() < 1) {
                            spec.source.close();
                            openZipFiles.remove(ZipVirtualFile.this.zipFile);
                        }
                    }
                }
            } catch (Throwable ignored) {
            }
            super.close();
        }
    }

    private static class OpenArchiveSpec {

        /**
         * The archive source.
         */
        public final ZipFile source;

        /**
         * A {@link java.util.Set} of the {@link java.io.InputStream} instances that are currently
         * open to read from the zip file.
         */
        public final Set<InputStream> refs;

        /**
         * A {@link java.util.Set} of the <code>ZipVirtualFile</code> instances that currently have
         * batch mode enabled.
         */
        public final Set<ZipVirtualFile> batchmode;

        public OpenArchiveSpec(File zipFile) throws IOException {
            this.source = new ZipFile(zipFile);
            this.refs = Collections
                    .newSetFromMap(new IdentityHashMap<InputStream, Boolean>());
            this.batchmode = Collections
                    .newSetFromMap(
                            new IdentityHashMap<ZipVirtualFile, Boolean>());
        }
    }

    public static void mountArchive(File file) throws IOException {
        synchronized (mounts) {
            ReferenceCount<HierarchicalZipFile> root = mounts.get(file);
            if (root == null)
                mounts.put(file,
                        new ReferenceCount<>(buildHierarchy(file), true));
            else
                root.reference();
        }
    }

    public static void unmountArchive(File file) {
        synchronized (mounts) {
            ReferenceCount<HierarchicalZipFile> root = mounts.get(file);
            if (root == null)
                return;
            root.dereference();
            if (!root.isReferenced())
                mounts.remove(file);
        }
    }

    /**
     * Scans the specified zip file. Each scanned entry will be passed to
     * <code>filter.accept(...)</code>. The method will return if the entry
     * name is accepted.
     *
     * @param zipFile   A zip file
     * @param filter    The filter
     */
    public static void scan(File zipFile, FilenameFilter filter)
            throws IOException {
        ZipFile zip = null;
        try {
            zip = new ZipFile(zipFile);
            Enumeration entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = (ZipEntry) entries.nextElement();
                if (filter.accept(zipFile, entry.getName()))
                    break;
            }
        } finally {
            if (zip != null)
                zip.close();
        }
    }
}

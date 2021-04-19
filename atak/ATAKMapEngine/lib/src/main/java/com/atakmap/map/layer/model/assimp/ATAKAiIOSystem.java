package com.atakmap.map.layer.model.assimp;

import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;
import com.atakmap.io.ZipVirtualFile;
import com.atakmap.map.layer.model.ModelSpi;
import com.atakmap.util.Disposable;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

import jassimp.AiIOStream;
import jassimp.AiIOSystem;

public class ATAKAiIOSystem implements AiIOSystem<AiIOStream>, Disposable {

    private static String TAG = "ATAKAiIOSystem";

    public static ATAKAiIOSystem INSTANCE = new ATAKAiIOSystem();

    private ModelSpi.Callback callback;
    private int maxProgress;

    private Set<InputStreamAiIOStream> streams = Collections.newSetFromMap(new IdentityHashMap<InputStreamAiIOStream, Boolean>());

    private static class InputStreamAiIOStream implements AiIOStream, Closeable {

        private ModelSpi.Callback callback;
        private int maxProgress;
        protected InputStream inputStream;
        protected final long fileSize;
        protected long filePos;
        private long readCount;

        InputStreamAiIOStream(InputStream inputStream, long fileSize, ModelSpi.Callback callback, int maxProgress) {
            this.fileSize = fileSize;
            this.inputStream = inputStream;
            this.callback = callback;
            this.maxProgress = maxProgress;
        }

        @Override
        public boolean read(ByteBuffer byteBuffer) {
            byte[] bytes = new byte[1024];
            int left = byteBuffer.limit();
            int count = 0;
            try {
                while ((count = inputStream.read(bytes, 0, Math.min(bytes.length, left))) > 0) {
                    if(callback != null && callback.isCanceled()) {
                        this.callback = null;
                        return false;
                    }
                    byteBuffer.put(bytes, 0, count);
                    left -= count;
                }
            } catch (IOException e) {
                Log.e(TAG, "failed to read", e);
            }

            long thisRead = byteBuffer.limit() - left;
            filePos += thisRead;
            readCount += thisRead;
            updateProgress();
            return thisRead > 0;
        }

        @Override
        public int getFileSize() {
            //XXX-- change to long
            return (int)fileSize;
        }

        @Override
        public long seek(long l, int from) {
            switch (from) {
                case 0: /*ORIGIN*/
                    if (l != filePos) {
                        return -1;
                    }
                    break;
                case 1: /*CUR*/
                    try {
                        final long retval = this.inputStream.skip(l);
                        // XXX is the intent to return the actual skip or the requested skip
                        if (retval != l) { 
                            Log.e(TAG, "failed to skip, requested " + l + " actual " + retval);
                        }
                    } catch (IOException e) {
                        Log.e(TAG, "error", e);
                        return -1;
                    }
                    break;
                case 2: /* END */
                    return -1;
            }
            return filePos;
        }

        @Override
        public long tell() {
            return filePos;
        }

        public void close() throws IOException {
            if(inputStream != null) {
                inputStream.close();
                inputStream = null;
            }
            callback = null;
        }

        protected void updateProgress() {
            if (callback != null) {
                double progress = (double)readCount / (double)fileSize;
                callback.progress((int)Math.min(progress * (double)maxProgress, maxProgress));
            }
        }
    }

    private static class ZipVirualFileAiIOStream extends InputStreamAiIOStream {

        private final ZipVirtualFile zipFile;

        ZipVirualFileAiIOStream(ZipVirtualFile zf, ModelSpi.Callback callback, int maxProgress) throws IOException {
            super(zf.openStream(), zf.getEntry().getSize(), callback, maxProgress);
            zipFile = zf;
        }

        private boolean reopenAndSkip(long skip) {
            try {
                InputStream newInputStream = zipFile.openStream();
                long actualSkip = newInputStream.skip(skip);
                this.inputStream.close();
                this.inputStream = newInputStream;
                this.filePos += actualSkip;
            } catch (IOException e) {
                Log.e(TAG, "failed to reopen and skip", e);
                return false;
            }
            return true;
        }

        @Override
        public long seek(long l, int from) {
            switch (from) {
                case 0: /*ORIGIN*/
                    if (l != filePos && !reopenAndSkip(l)) {
                        return -1;
                    }
                    break;
                case 2: /* END */
                    if (filePos != fileSize - l && !reopenAndSkip(fileSize - l)) {
                        return -1;
                    }
                default:
                    return super.seek(l, from);
            }

            return filePos;
        }

        @Override
        public long tell() {
            return filePos;
        }
    }

    static File findObj(File f) {
        if(IOProviderFactory.isDirectory(f)) {
            File[] children = IOProviderFactory.listFiles(f);
            if (children != null) {
                for(File c : children) {
                    File r = findObj(c);
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

    InputStreamAiIOStream openFile(String path) throws IOException {
        File f = new File(path);
        if(IOProviderFactory.exists(f))
            return new InputStreamAiIOStream(IOProviderFactory.getInputStream(f), IOProviderFactory.length(f), callback, maxProgress);
        try {
            ZipVirtualFile zf = new ZipVirtualFile(f.getPath());
            if (IOProviderFactory.exists(zf))
                return new ZipVirualFileAiIOStream(zf, callback, maxProgress);
        } catch(Throwable ignored) {}
        return null;
    }

    static boolean isZipFile(File file) {
        return FileSystemUtils.isZipPath(file);
    }

    @Override
    public AiIOStream open(String path, String ioMode) {

        // must be read only
        if (ioMode.startsWith("w")) {
            return null;
        }

        File file = new File(path);
        if(isZipFile(file)) {
            try {
                File entry = findObj(new ZipVirtualFile(file));
                if (entry != null)
                    file = entry;
            } catch(IllegalArgumentException ignored) {}
        }
        try {
            final InputStreamAiIOStream retval = openFile(file.getPath());
            synchronized(streams) {
                streams.add(retval);
            }
            return retval;
        } catch (IOException e) {
            Log.e(TAG, "Failed to open io stream", e);
            return null;
        }
    }

    @Override
    public boolean exists(String path) {
        File f = new File(path);
        if(IOProviderFactory.exists(f))
            return true;
        try {
            ZipVirtualFile zf = new ZipVirtualFile(f.getPath());
            return IOProviderFactory.exists(zf);
        } catch(Throwable t) {
            return false;
        }
    }

    @Override
    public char getOsSeparator() {
        return File.pathSeparatorChar;
    }

    @Override
    public void close(AiIOStream aiIOStream) {
        synchronized(streams) {
            streams.remove(aiIOStream);
        }
        try {
            ((InputStreamAiIOStream)aiIOStream).close();
        } catch (IOException e) {
            Log.e(TAG, "Failed to close io stream", e);
        }
    }

    @Override
    public void dispose() {
        synchronized(streams) {
            for(InputStreamAiIOStream stream : streams) {
                try {
                    stream.close();
                } catch(Throwable ignored) {}
            }
            streams.clear();
        }
    }
    static ATAKAiIOSystem forModelSpiCallback(ModelSpi.Callback callback,
                                              int maxProgress) {
        ATAKAiIOSystem result = new ATAKAiIOSystem();
        result.callback = callback;
        result.maxProgress = maxProgress;
        return result;
    }
}

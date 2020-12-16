package com.atakmap.commoncommo;

import java.io.File;
import java.util.Vector;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.net.URI;
import java.util.Map;
import java.util.List;
import java.nio.channels.FileChannel;
import java.nio.ByteBuffer;


public final class IOProviderTester {
    private final static Map<FileIOProvider, Long> ioProviders = new IdentityHashMap<FileIOProvider, Long>();
    private final static Map<FileChannel, Long> channels = new IdentityHashMap<FileChannel, Long>();

    private long nativePtr;

    public IOProviderTester() throws CommoException
    {
        nativePtr = ioProviderTesterCreateNative();
        if (nativePtr == 0)
            throw new CommoException();
    }

    public FileChannel open(String path, String mode){
        return openNative(nativePtr, path, mode);
    }

    public void close(FileChannel channel){
        closeNative(nativePtr, channels.get(channel));
        channels.remove(channel);
    }

    public void setMap(FileChannel channel, long ptr){
        synchronized(channels) {
            channels.put(channel, Long.valueOf(ptr));
        }
    }

    public long read(ByteBuffer buf, long size, long nmemb, FileChannel channel){
        return readNative(nativePtr, buf, size, nmemb, channel);
    }

    public long write(ByteBuffer buf, long size, long nmemb, FileChannel channel){
        return writeNative(nativePtr, buf, size, nmemb, channel);
    }

    public int eof(FileChannel channel){
        return eofNative(nativePtr, channel);
    }

    public int seek(long offset, int origin, FileChannel channel){
        return seekNative(nativePtr, offset, origin, channel);
    }

    public int error(FileChannel channel){
        return errorNative(nativePtr, channel);
    }

    public long tell(FileChannel channel){
        return tellNative(nativePtr, channel);
    }

    public long getSize(String path){
        return getSizeNative(nativePtr, path);
    }

    public void registerFileIOProvider(FileIOProvider provider){
        synchronized(ioProviders) {
            if(ioProviders.containsKey(provider))
                return; // already registered
            final long providerPtr = registerFileIOProviderNative(nativePtr, provider);
            ioProviders.put(provider, Long.valueOf(providerPtr));
        }
    }

    public void deregisterFileIOProvider(FileIOProvider provider){
        synchronized(ioProviders) {
            final Long providerPtr = ioProviders.remove(provider);
            if(providerPtr == null)
                return; // not registered
            deregisterFileIOProviderNative(nativePtr, providerPtr.longValue());
        }
    }

    static native long ioProviderTesterCreateNative();

    static native long registerFileIOProviderNative(long nativePtr, FileIOProvider jprovider);

    static native void deregisterFileIOProviderNative(long nativePtr, long jprovider);

    native FileChannel openNative(long nativePtr, String jpath, String jmode);

    static native void closeNative(long nativePtr, long jchannel);

    static native long readNative(long nativePtr, ByteBuffer jbuf, long jsize, long jnmemb, FileChannel jchannel);

    static native long writeNative(long nativePtr, ByteBuffer jbuf, long jsize, long jnmemb, FileChannel jchannel);

    static native int eofNative(long nativePtr, FileChannel jchannel);

    static native int seekNative(long nativePtr, long joffset, int jorigin, FileChannel jchannel);

    static native int errorNative(long nativePtr, FileChannel jchannel);

    static native long tellNative(long nativePtr, FileChannel jchannel);

    static native long getSizeNative(long nativePtr, String jpath);
}


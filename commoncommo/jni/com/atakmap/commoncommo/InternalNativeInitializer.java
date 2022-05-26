package com.atakmap.commoncommo;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Internal use only class that handles automatic native initialization
 * during any native-using public class loading.
 * This is one of two possible current implementations, with only one built
 * in to the library.  Which is chosen depends on a build-time property.
 * See also ExternalNativeInitializer.java
 */
final class NativeInitializer {
    private final static int ARCH_x86 = 0x01;
    private final static int ARCH_x64 = 0x02;
    
    private final static int OS_WIN = 0x10;
    private final static int OS_LINUX = 0x20;
    private final static int OS_MACOS = 0x40;
    
    private final static Map<Integer, String> libPaths = new HashMap<Integer, String>();
    static {
        libPaths.put(OS_WIN|ARCH_x64, "win64");
        libPaths.put(OS_LINUX|ARCH_x64, "linux64");
        libPaths.put(OS_MACOS|ARCH_x64, "macos64");
    }

    
    final static AtomicBoolean initialized = new AtomicBoolean(false);

    private NativeInitializer() {}

    static void initialize() {
        if (initialized.getAndSet(true))
            return;

        if(!deployNativeLibraries()) {
            try {
                System.loadLibrary("commoncommojni");
            } catch (Throwable t) {
                System.err.println("Failed to load libcommoncommojni");
            }
        }

        try {
            Commo.initThirdpartyNativeLibraries();
        } catch (CommoException e) {
            System.err.println("Failed to initialize native libraries");
        }
    }
    
    private static boolean deployNativeLibraries() {
        // determine platform
        final String arch = System.getProperty("os.arch");
        final String os = System.getProperty("os.name");
        
        int key = 0;
        // OS detection
        if(os.toLowerCase().indexOf("win") >= 0) {
            key |= OS_WIN;
        }
        if(os.toLowerCase().indexOf("linux") >= 0) {
            key |= OS_LINUX;
        }
        if(os.toLowerCase().indexOf("mac") >= 0 || os.toLowerCase().indexOf("darwin") >= 0) {
            key |= OS_MACOS;
        }
        // architecture detection
        if(arch.toLowerCase().indexOf("64") >= 0) {
            key |= ARCH_x64;
        } else if(arch.toLowerCase().indexOf("32") >= 0) {
            key |= ARCH_x86;
        } else if(arch.toLowerCase().indexOf("86") >= 0) {
            key |= ARCH_x86;
        }
        final String libPath = libPaths.get(key);
        if(libPath == null) {
            System.err.println("Internal native init not supported for " + os + " " + arch);
            return false;
        }
        // read library listing
        InputStream is = null;
        try {
            is = NativeInitializer.class.getResourceAsStream("/jcommoncommo/libs/" + libPath + "/jcommoncommo.libs");
            if(is == null) {
                System.err.println("No library listing found");
                return false;
            }
            BufferedReader reader = null;
            try {
                reader = new BufferedReader(new InputStreamReader(is));
                // obtain version hash
                final String version = reader.readLine();
                if(version == null) {
                    System.err.println("Unexpected EOF");
                    return false;
                }
                // create deploy directory
                File nativeLibDir = new File(System.getProperty("java.io.tmpdir"), "jcommoncommo/" + version + "/" + libPath);
                if(!nativeLibDir.exists())
                    nativeLibDir.mkdirs();
                boolean allLoaded = true;
                // iterate libraries; deploy and load
                do {
                    final String libname = reader.readLine();
                    if(libname == null)
                        break;
                    InputStream strm = null;
                    try {
                        strm = NativeInitializer.class.getResourceAsStream("/jcommoncommo/libs/" + libPath + "/" + libname);
                        if (strm == null) {
                            allLoaded = false;
                            continue;
                        }
                        final File file = new File(nativeLibDir, libname);
                        FileOutputStream out = null;
                        try {
                            out = new FileOutputStream(file);
                            copyStream(strm, out);
                        } finally {
                            if(out != null)
                                out.close();
                        }
                        System.load(file.getAbsolutePath());
                    } finally {
                        if(strm != null)
                            strm.close();
                    }
                } while(true);
                return allLoaded;
            } finally {
                if(reader != null)
                    reader.close();
            }
        } catch(Throwable t) {
            System.err.println("Failed to deploy or load native libraries");
            return false;
        } finally {
            if(is != null)
                try {
                    is.close();
                } catch(IOException e) {}
        }
    }
    
    private static void copyStream(InputStream in, OutputStream out) throws IOException {
        final byte[] buf = new byte[4096];
        do {
            final int numRead = in.read(buf);
            if(numRead < 0)
                break;
            out.write(buf, 0, numRead);
        } while(true);
    }
}

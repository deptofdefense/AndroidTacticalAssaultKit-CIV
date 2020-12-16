
package com.atakmap.map;

import android.content.Context;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.loader.NativeLoader;
import com.atakmap.util.ConfigOptions;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public final class EngineLibrary {
    private final static AtomicBoolean initialized = new AtomicBoolean(false);

    private final static int ARCH_x86 = 0x01;
    private final static int ARCH_x64 = 0x02;
    
    private final static int OS_WIN = 0x10;
    
    private final static Map<Integer, String[]> libs = new HashMap<>();
    static {
        // NOTE: order is important
        /** WIN64 **/
        libs.put(
            OS_WIN|ARCH_x64,
            new String[]
            {
                "charset-1",
                "iconv-2",
                "zlibwapi",
                "libxml2",
                "libcrypto-1_1",
                "sqlite3",
                "proj",
                "geos_c",
                "spatialite",
                "libexpat",
                "ogdi",
                "tbb",
                "lti_dsdk_9.5",
                "libssl-1_1",
                "libcurl",
                "kdu_v64R",
                "gdal204",
                "assimp",
                "pri",
                "libGLESv2",
                "takengine",
                "takenginejni",
            }
        );
    }

    private final static Map<Integer, String> libPaths = new HashMap<>();
    static {
        libPaths.put(OS_WIN|ARCH_x64, "win64");
    }

    private final static Map<Integer, NameResolver> libNameResolvers = new HashMap<>();
    static {
        final NameResolver win = new NameResolver() {
            @Override
            public String resolve(String name) {
                return name + ".dll";
            }
        };
        libNameResolvers.put(OS_WIN|ARCH_x86, win);
        libNameResolvers.put(OS_WIN|ARCH_x64, win);
    }
    
    private EngineLibrary() {
    }

    public static void initialize() {
        if (initialized.getAndSet(true))
            return;

        final String arch = System.getProperty("os.arch");
        final String os = System.getProperty("os.name");
        
        int key = 0;
        // OS detection
        if(os.toLowerCase().indexOf("win") >= 0) {
            key |= OS_WIN;
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
        final NameResolver nameResolver = libNameResolvers.get(key);
        try {
            Context ctx = new Context();
            File nativeLibDir = new File(ctx.getPackageManager().getApplicationInfo(ctx.getPackageName(), 0).nativeLibraryDir);
            nativeLibDir.delete();
            nativeLibDir.mkdirs();

            String[] libs = EngineLibrary.libs.get(key);
            if(libs != null) {
                for(String lib : libs) {
                    String libname = nameResolver.resolve(lib);
                    InputStream strm = EngineLibrary.class.getResourceAsStream("/libs/" + libPath + "/" + libname);
                    if(strm != null)
                        FileSystemUtils.copyStream(strm, new FileOutputStream(new File(nativeLibDir, libname)));
                    NativeLoader.loadLibrary(lib);
                }
            }

            // config options for default platform behavior
            ConfigOptions.setOption("glmapview.tilt-skew-offset", 1.0);
            ConfigOptions.setOption("glmapview.tilt-skew-mult", 2.5);
            ConfigOptions.setOption("terrain.constrain-query-res", 1);
            ConfigOptions.setOption("terrain.fill-with-hi-res", 0);
            ConfigOptions.setOption("terrain.legacy-elevation-api", 0);
            ConfigOptions.setOption("terrain.resadj", 1.0);
            ConfigOptions.setOption("gltext.use-font-bins", 0);

        } catch (Throwable t) {
            System.err.println("EngineLibrary: Failed to load native libraries\n" + t);
        }
    }

    interface NameResolver {
        String resolve(String name);
    }
}

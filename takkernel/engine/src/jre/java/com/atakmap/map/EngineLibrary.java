package com.atakmap.map;

import android.content.Context;
import android.content.pm.PackageManager;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.loader.NativeLoader;
import com.atakmap.filesystem.HashingUtils;
import com.atakmap.map.opengl.GLRenderGlobals;
import com.atakmap.math.MathUtils;
import com.atakmap.opengl.GLText;
import com.atakmap.util.ConfigOptions;
import gov.tak.api.engine.map.IRenderContextSpi;
import gov.tak.api.engine.map.RenderContext;
import gov.tak.api.engine.map.RenderContextFactory;
import gov.tak.platform.commons.opengl.JOGLGLES;
import gov.tak.platform.engine.JOGLRenderContext;

import javax.media.opengl.GL;
import javax.media.opengl.GLAutoDrawable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public final class EngineLibrary {
    private final static AtomicBoolean initialized = new AtomicBoolean(false);

    private final static int ARCH_x86 = 0x01;
    private final static int ARCH_x64 = 0x02;
    
    private final static int OS_WIN = 0x10;
    private final static int OS_LINUX = 0x20;
    private final static int OS_MACOS = 0x40;

    private final static Map<Integer, String[]> libs = new HashMap<>();
    private static Context runtimeContext;

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
                "liblas",
                "liblas_c",
                "takengine",
                "takenginejni",
            }
        );
        /** LINUX64 **/
        libs.put(
            OS_LINUX|ARCH_x64,
            new String[]
            {
                "charset",
                "iconv",
                "crypto",
                "proj",
                "spatialite",
                "tbb",
                "ltidsdk",
                "ssl",
                "ogdi",
                "gdal",
                "gdalalljni",
                "assimp",
                "pri",
                "las",
                "las_c",
                "GLESv2",
                "takengine",
                "takenginejni",
            }
        );
        /** MACOS64 **/
        libs.put(
            OS_MACOS|ARCH_x64,
            new String[]
            {
                //"charset",
                //"iconv",
                //"crypto",
                //"proj",
                "spatialite",
                "tbb",
                "ltidsdk",
                //"ssl",
                "ogdi",
                "gdal",
                //"gdalalljni",
                //"assimp",
                //"pri",
                "GLESv2",
                "las",
                "las_c",
                "takengine",
                "takenginejni",
            }
        );
    }

    private final static Map<Integer, String> libPaths = new HashMap<>();
    static {
        libPaths.put(OS_WIN|ARCH_x64, "win64");
        libPaths.put(OS_LINUX|ARCH_x64, "linux64");
        libPaths.put(OS_MACOS|ARCH_x64, "macos64");
    }

    private final static Map<Integer, NameResolver> libNameResolvers = new HashMap<>();
    static {
        final NameResolver dll = new NameResolver() {
            @Override
            public String[] resolve(String name) {
                return new String[] {name + ".dll"};
            }
        };
        final NameResolver so = new NameResolver() {
            @Override
            public String[] resolve(String name) {
                return new String[] {"lib" + name + ".so"};
            }
        };
        final NameResolver dylib = new NameResolver() {
            @Override
            public String[] resolve(String name) {
                return new String[] {
                        "lib" + name + ".dylib",
                        "lib" + name + ".jnilib",
                        "lib" + name + ".so"

                };
            }
        };
        libNameResolvers.put(OS_WIN|ARCH_x86, dll);
        libNameResolvers.put(OS_WIN|ARCH_x64, dll);
        libNameResolvers.put(OS_LINUX|ARCH_x86, so);
        libNameResolvers.put(OS_LINUX|ARCH_x64, so);
        libNameResolvers.put(OS_MACOS|ARCH_x86, dylib);
        libNameResolvers.put(OS_MACOS|ARCH_x64, dylib);

        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
            public void run() {
                EngineLibrary.shutdown();
            }
        }));
    }

    /*************************************************************************/
    // Providers Registration

    static {
        RenderContextFactory.registerSpi(new IRenderContextSpi() {
            @Override
            public RenderContext create(Object opaque) {
                if (opaque instanceof GLAutoDrawable) {
                    return new JOGLRenderContext((GLAutoDrawable) opaque);
                }
                return null;
            }
        });
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

        // NOTE: Observed on *some* Linux hosts that `libjawt` needs to be explicitly loaded
        if(MathUtils.hasBits(key, OS_LINUX)) {
            try {
                System.loadLibrary("jawt");
            } catch(Throwable ignored) {}
        }

        final String libPath = libPaths.get(key);
        final NameResolver nameResolver = libNameResolvers.get(key);
        try {
            runtimeContext = new Context();
            NativeLoader.init(runtimeContext);
            File nativeLibDir = getRuntimeDirectory();
            if(nativeLibDir != null)
                nativeLibDir = new File(nativeLibDir, "lib/" + libPath);
            else
                nativeLibDir = new File(runtimeContext.getPackageManager().getApplicationInfo(runtimeContext.getPackageName(), 0).nativeLibraryDir);
            nativeLibDir.mkdirs();

            String[] libs = EngineLibrary.libs.get(key);
            if(libs != null) {
                for(String lib : libs) {
                    for(String libname : nameResolver.resolve(lib)) {
                        InputStream strm = EngineLibrary.class.getResourceAsStream("/libs/" + libPath + "/" + libname);
                        if (strm == null)
                            continue;
                        final File file = new File(nativeLibDir, System.mapLibraryName(lib));
                        try(FileOutputStream out = new FileOutputStream(file)) {
                            FileSystemUtils.copyStream(strm, out);
                        }
                        System.load(file.getAbsolutePath());
                        break;
                    }
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

            GLRenderGlobals.appContext = runtimeContext;
            // force static initialization to install default `TextFormatFactory`
            GLText.localize(null);
        } catch (Throwable t) {
            System.err.println("EngineLibrary: Failed to load native libraries\n" + t);
        }

        JOGLGLES.addInitializationHook(new JOGLGLES.InitializationHook() {
            @Override
            public void onInit(GL gl) {
                initJOGL();
            }
        });
    }

    public static void shutdown() {
        shutdownImpl();

        if(runtimeContext != null) {
            try {
                FileSystemUtils.deleteDirectory(runtimeContext.getCacheDir(), false);
            } catch(Throwable ignored) {}
        }
    }

    static native void shutdownImpl();

    static native void initJOGL();

    /**
     * Returns the directory to store the runtime libraries. May be <code>null</code> if the
     * preferred directory is not available.
     * @return
     */
    static File getRuntimeDirectory() {
        final String classFile = '/' + EngineLibrary.class.getName().replace('.', '/')  + ".class";
        final URL u = EngineLibrary.class.getResource(classFile);
        String base = u.toString().replace(classFile, "");
        if(base.endsWith("!"))
            base = base.substring(0, base.length()-1);
        if(base.startsWith("jar:file:"))
            base = base.substring(4);
        String id;
        try {
            final File d = new File(new URL(base).toURI());
            if (d.isFile())
                id = HashingUtils.md5sum(d);
            else
                id = HashingUtils.md5sum(d.getAbsolutePath());

            return FileSystemUtils.combine(System.getProperty("java.io.tmpdir"), "takkernel", "engine", id);
        } catch(Throwable e) {
            return null;
        }
    }

    interface NameResolver {
        String[] resolve(String name);
    }
}

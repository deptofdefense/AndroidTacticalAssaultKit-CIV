package com.atakmap.opengl;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.util.ConfigOptions;

import android.content.Context;
import android.content.res.AssetManager;
import android.content.res.Resources;
import com.atakmap.util.zip.IoUtils;

public final class GLSLUtil {
    private static final String TAG = "GLSLUtil";
    private static Context context = null;

    private GLSLUtil() {}
    
    /**
     * Loads the shader source code from the specified path. Shader source files
     * will be searched for in the following order
     * <UL>
     *  <LI>Absolute path on the filesystem
     *  <LI>Path relative to paths defined by <I>Config Option</I>
     *      <code>glsltutil.additional-shader-source-paths</code>, colon
     *      delimited
     *  <LI>The application assets in a <code>shaders</code> subdirectory, using
     *      the {@link AssetManager} provided previously to
     *      {@link #setAssetManager(AssetManager)}.
     *      
     * @param path  The path to the shader source file
     * 
     * @return  The shader source code
     * 
     * @throws IOException  If the shader source file cannot be found or there
     *                      is an IO error reading the shader source
     */
    public static String loadShaderSource(String path) throws IOException {
        InputStream stream = null;
        
        try {
            do {
                File shaderFile;
                
                shaderFile = new File(path);
                // look up absolute path
                if(IOProviderFactory.exists(shaderFile)) {
                    stream = IOProviderFactory.getInputStream(shaderFile);
                    break;
                }
                
                // XXX - look up shader source paths
                String additionalShaderSourcePaths = ConfigOptions.getOption("glslutil.additional-shader-source-paths", null);
                if(additionalShaderSourcePaths != null) {
                    String[] paths = additionalShaderSourcePaths.split("\\:");
                    for(int i = 0; i < paths.length; i++) {
                        shaderFile = new File(paths[i], path);
                        if(IOProviderFactory.exists(shaderFile)) {
                            stream = IOProviderFactory.getInputStream(shaderFile);
                            break;
                        }
                    }
                    if(stream != null)
                        break;
                }
    
                // load from assets
                if(context != null) {
                    Resources r = context.getResources();
                    final int id = r.getIdentifier(path, "raw", context.getPackageName());
                    if(id != 0) { 
                        stream = r.openRawResource(id);
                        break;
                    } 
                }
            } while(false);
            
            if(stream == null)
                throw new FileNotFoundException();
            
            return new String(FileSystemUtils.read(stream), FileSystemUtils.UTF8_CHARSET);
        } finally {
            IoUtils.close(stream, TAG);
        }
    }
    
    public static void setContext(Context ctx) {
        context = ctx;
    }
    
    // XXX - convenience methods to set uniforms, varyings, etc. by name
}

package com.atakmap.map.layer.model.opengl;

import com.atakmap.map.MapRenderer;
import com.atakmap.map.layer.model.ModelInfo;
import com.atakmap.map.opengl.GLMapRenderable2;
import com.atakmap.spi.PriorityServiceProviderRegistry2;
import com.atakmap.spi.ServiceProvider;

import java.util.IdentityHashMap;
import java.util.Map;

public final class GLSceneFactory {
    private final static PriorityServiceProviderRegistry2<GLMapRenderable2, Arg, Spi> registry = new PriorityServiceProviderRegistry2<>();
    private final static Map<GLSceneSpi, Spi> spis = new IdentityHashMap<>();

    private GLSceneFactory() {}

    public static void registerSpi(GLSceneSpi spi) {
        Spi toRegister;
        synchronized(spis) {
            if(spis.containsKey(spi))
                return;
            toRegister = new Spi(spi);
            spis.put(spi, toRegister);
        }
        registry.register(toRegister, toRegister.impl.getPriority());
    }

    public static void unregisterSpi(GLSceneSpi spi) {
        Spi toUnregister;
        synchronized(spis) {
            toUnregister = spis.remove(spi);
            if(toUnregister == null)
                return;
        }
        registry.unregister(toUnregister);
    }

    public static GLMapRenderable2 create(MapRenderer ctx, ModelInfo info, String cacheDir) {
        Arg arg = new Arg();
        arg.ctx = ctx;
        arg.info = info;
        arg.cacheDir = cacheDir;
        return registry.create(arg);
    }

    final static class Arg {
        MapRenderer ctx;
        ModelInfo info;
        String cacheDir;
    }

    final static class Spi implements ServiceProvider<GLMapRenderable2, Arg> {
        final GLSceneSpi impl;

        Spi(GLSceneSpi impl) {
            this.impl = impl;
        }


        @Override
        public GLMapRenderable2 create(Arg object) {
            return this.impl.create(object.ctx, object.info, object.cacheDir);
        }
    }
}


package com.atakmap.map.opengl;

import android.util.Pair;

import com.atakmap.android.androidtest.ATAKInstrumentedTest;
import com.atakmap.map.Globe;
import com.atakmap.map.MapRenderer;
import com.atakmap.map.MockMapRenderer;
import com.atakmap.map.MockRenderContext;
import com.atakmap.map.MockSurface;
import com.atakmap.map.RenderContext;
import com.atakmap.map.layer.Layer;
import com.atakmap.map.layer.MockLayer;
import com.atakmap.map.layer.opengl.GLLayer2;
import com.atakmap.map.layer.opengl.GLLayerFactory;
import com.atakmap.map.layer.opengl.GLLayerSpi2;
import com.atakmap.map.layer.opengl.MockGLLayer3;

import org.junit.Test;

import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertNull;
import static junit.framework.Assert.assertSame;

public class GLLayerFactoryTest extends ATAKInstrumentedTest {

    @Test
    public void create_spi_accepts_compatible() {
        final Layer compatible = new MockLayer("test", true);
        final GLLayerSpi2 spi = new GLLayerSpi2() {
            @Override
            public int getPriority() {
                return 1;
            }

            @Override
            public GLLayer2 create(Pair<MapRenderer, Layer> object) {
                if (object.second == compatible)
                    return new MockGLLayer3(compatible, 0);
                else
                    return null;
            }
        };
        try {
            GLLayerFactory.register(spi);

            Globe globe = new Globe();
            RenderContext ctx = new MockRenderContext(
                    new MockSurface(1920, 1080, 240));
            GLMapView view = new GLMapView(ctx, globe, 0, 0,
                    ctx.getRenderSurface().getWidth(),
                    ctx.getRenderSurface().getHeight());

            final GLLayer2 result = GLLayerFactory.create3(view, compatible);
            assertNotNull(result);
            assertSame(compatible, result.getSubject());
        } finally {
            GLLayerFactory.unregister(spi);
        }
    }

    @Test
    public void create_spi_rejects_incompatible() {
        final Layer compatible = new MockLayer("compatible", true);
        final Layer incompatible = new MockLayer("incompatible", true);
        final GLLayerSpi2 spi = new GLLayerSpi2() {
            @Override
            public int getPriority() {
                return 1;
            }

            @Override
            public GLLayer2 create(Pair<MapRenderer, Layer> object) {
                if (object.second == compatible)
                    return new MockGLLayer3(compatible, 0);
                else
                    return null;
            }
        };
        try {
            GLLayerFactory.register(spi);
            RenderContext ctx = new MockRenderContext(
                    new MockSurface(1920, 1080, 240));
            final GLLayer2 result = GLLayerFactory
                    .create3(new MockMapRenderer(ctx), incompatible);
            assertNull(result);
        } finally {
            GLLayerFactory.unregister(spi);
        }
    }
}

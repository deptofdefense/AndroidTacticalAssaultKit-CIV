
package com.atakmap.map.opengl;

import com.atakmap.android.androidtest.ATAKInstrumentedTest;
import com.atakmap.map.Globe;
import com.atakmap.map.MockRenderContext;
import com.atakmap.map.MockSurface;
import com.atakmap.map.RenderContext;

import org.junit.Test;

public class GLMapViewTest extends ATAKInstrumentedTest {
    @Test(expected = IllegalArgumentException.class)
    public void GLMapView_init_null_globe_throws() {
        Globe globe = null;
        RenderContext ctx = new MockRenderContext(
                new MockSurface(1920, 1080, 240));
        GLMapView view = new GLMapView(ctx, globe, GLMapView.MATCH_SURFACE,
                GLMapView.MATCH_SURFACE, GLMapView.MATCH_SURFACE,
                GLMapView.MATCH_SURFACE);
    }

}

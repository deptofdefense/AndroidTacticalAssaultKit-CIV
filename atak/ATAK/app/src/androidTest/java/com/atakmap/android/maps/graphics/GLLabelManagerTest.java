
package com.atakmap.android.maps.graphics;

import android.util.Log;

import com.atakmap.android.androidtest.ATAKInstrumentedTest;
import com.atakmap.interop.Pointer;
import com.atakmap.map.Interop;
import com.atakmap.map.MockRenderContext;
import com.atakmap.map.MockSurface;
import com.atakmap.map.RenderContext;

import org.junit.BeforeClass;
import org.junit.Test;

public class GLLabelManagerTest extends ATAKInstrumentedTest {

    @BeforeClass
    public static void checkLibraryLoad() {
        RenderContext renderContext = new MockRenderContext(
                new MockSurface(1920, 1080, 240));
        Interop<RenderContext> RenderContext_interop = Interop
                .findInterop(RenderContext.class);
        Pointer contextPtr = RenderContext_interop.wrap(renderContext);
        //GLLabelManager.create(contextPtr.raw);
    }

    @Test
    public void test_set_color() {
        Log.d("TEST_DEBUG", "BEGIN: test_set_color()");
        //GLLabelManager.setColor(0, 255);
        Log.d("TEST_DEBUG", "END: test_set_color()");
    }
}


package com.atakmap.map.elevation;

import com.atakmap.android.androidtest.ATAKInstrumentedTest;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import androidx.test.ext.junit.runners.AndroidJUnit4;

@RunWith(AndroidJUnit4.class)
public class ElevationSourceManagerTests extends ATAKInstrumentedTest {
    @Test
    public void noSourcesOnInit() {
        Collection<ElevationSource> sources = new LinkedList<>();
        ElevationSourceManager.getSources(sources);
        assertTrue(sources.isEmpty());
    }

    @Test
    public void source_add_roundtrip() {
        final ElevationSource src = ElevationSourceBuilder.build("test",
                Collections.<ElevationChunk> emptySet());
        try {
            ElevationSourceManager.attach(src);

            Collection<ElevationSource> sources = new LinkedList<>();
            ElevationSourceManager.getSources(sources);
            assertEquals(1, sources.size());
            assertTrue(sources.contains(src));
        } finally {
            ElevationSourceManager.detach(src);
        }
    }

    @Test
    public void find_by_name_empty_no_results() {
        Collection<ElevationSource> sources = new LinkedList<>();
        ElevationSource src = ElevationSourceManager.findSource("asdasd");
        assertNull(src);
    }

    @Test
    public void source_find() {
        final ElevationSource src = ElevationSourceBuilder.build("test",
                Collections.<ElevationChunk> emptySet());
        try {
            ElevationSourceManager.attach(src);

            ElevationSource f0 = ElevationSourceManager.findSource("asdasd");
            assertNull(f0);

            ElevationSource f1 = ElevationSourceManager
                    .findSource(src.getName());
            assertNotNull(f1);
            assertSame(f1, src);
        } finally {
            ElevationSourceManager.detach(src);
        }
    }

    @Test
    public void source_add_remove_roundtrip() {
        final ElevationSource src = ElevationSourceBuilder.build("test",
                Collections.<ElevationChunk> emptySet());

        ElevationSourceManager.attach(src);
        ElevationSourceManager.detach(src);

        Collection<ElevationSource> sources = new LinkedList<>();
        ElevationSourceManager.getSources(sources);

        assertTrue(sources.isEmpty());
    }

    @Test
    public void source_add_remove_callbacks_received() {
        final ElevationSource src = ElevationSourceBuilder.build("test",
                Collections.<ElevationChunk> emptySet());

        final ElevationSource[] attached = new ElevationSource[1];
        final ElevationSource[] detatched = new ElevationSource[1];

        ElevationSourceManager.OnSourcesChangedListener cb = new ElevationSourceManager.OnSourcesChangedListener() {
            @Override
            public void onSourceAttached(ElevationSource a) {
                attached[0] = a;
            }

            @Override
            public void onSourceDetached(ElevationSource d) {
                detatched[0] = d;
            }
        };

        ElevationSourceManager.addOnSourcesChangedListener(cb);

        ElevationSourceManager.attach(src);
        ElevationSourceManager.detach(src);

        assertNotNull(attached[0]);
        assertSame(src, attached[0]);
        assertNotNull(detatched[0]);
        assertSame(src, detatched[0]);
    }

    @Test
    public void source_add_remove_callbacks_not_received_after_removelistener() {
        final ElevationSource src = ElevationSourceBuilder.build("test",
                Collections.<ElevationChunk> emptySet());

        final ElevationSource[] attached = new ElevationSource[1];
        final ElevationSource[] detatched = new ElevationSource[1];

        ElevationSourceManager.OnSourcesChangedListener cb = new ElevationSourceManager.OnSourcesChangedListener() {
            @Override
            public void onSourceAttached(ElevationSource a) {
                attached[0] = a;
            }

            @Override
            public void onSourceDetached(ElevationSource d) {
                detatched[0] = d;
            }
        };

        ElevationSourceManager.addOnSourcesChangedListener(cb);
        ElevationSourceManager.removeOnSourcesChangedListener(cb);

        ElevationSourceManager.attach(src);
        ElevationSourceManager.detach(src);

        assertNull(attached[0]);
        assertNull(detatched[0]);
    }
}

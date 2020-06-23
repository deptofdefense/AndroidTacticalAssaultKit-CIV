
package com.atakmap.map.elevation;

import com.atakmap.map.elevation.ElevationChunk;
import com.atakmap.map.elevation.ElevationSource;
import com.atakmap.map.elevation.ElevationSourceBuilder;

import org.junit.Test;

import java.util.Collection;
import java.util.LinkedList;

import static org.junit.Assert.assertEquals;

public class ElevationSourceBuilderTest {
    @Test(expected = IllegalArgumentException.class)
    public void static_build_null_name_throws() {
        String name = null;
        Collection<ElevationChunk> chunks = new LinkedList<>();

        ElevationSourceBuilder.build(name, chunks);
    }

    @Test(expected = IllegalArgumentException.class)
    public void static_build_null_chunks_throws() {
        String name = "name";
        Collection<ElevationChunk> chunks = null;

        ElevationSourceBuilder.build(name, chunks);
    }

    @Test
    public void static_build_name_roundtrip() {
        String name = Long.toString(System.currentTimeMillis(), 16);
        Collection<ElevationChunk> chunks = new LinkedList<>();

        ElevationSource source = ElevationSourceBuilder.build(name, chunks);
        assertEquals(name, source.getName());
    }
}

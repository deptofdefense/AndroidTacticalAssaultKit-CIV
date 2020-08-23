
package com.atakmap.coremap.concurrent;

import android.opengl.GLES30;

import com.atakmap.opengl.Skirt;

import org.junit.Test;
import static org.junit.Assert.*;

import java.nio.FloatBuffer;
import java.nio.ShortBuffer;
import java.util.Comparator;
import java.util.Set;
import java.util.TreeSet;

public class NamedThreadFactoryTest {

    @Test
    public void test_factory_name() {

        NamedThreadFactory ntf = new NamedThreadFactory("test_name");
        Thread t = ntf.newThread(new Runnable() {
            public void run() {

            }
        });
        assertTrue(t.getName().startsWith("test_name"));

    }
}


package com.atakmap.coremap.concurrent;

import org.junit.Test;
import static org.junit.Assert.*;

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

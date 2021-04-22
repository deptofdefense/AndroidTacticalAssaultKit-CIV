
package com.atakmap.util;

import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class DisposerTests {
    @Test
    public void direct_invocation() {
        final boolean[] disposed = new boolean[] {
                false
        };
        Disposer d = new Disposer(new Disposable() {
            @Override
            public void dispose() {
                disposed[0] = true;
            }
        });
        d.close();

        assertTrue(disposed[0]);
    }

    @Test
    public void try_with_resources() {
        final boolean[] disposed = new boolean[] {
                false
        };
        try (Disposer d = new Disposer(new Disposable() {
            @Override
            public void dispose() {
                disposed[0] = true;
            }
        })) {
        }

        assertTrue(disposed[0]);
    }
}

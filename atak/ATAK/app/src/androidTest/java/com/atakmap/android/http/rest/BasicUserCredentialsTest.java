
package com.atakmap.android.http.rest;

import com.atakmap.android.androidtest.ATAKInstrumentedTest;

import static org.junit.Assert.*;

import org.junit.Test;

public class BasicUserCredentialsTest extends ATAKInstrumentedTest {

    @Test
    public void isValid() {
        BasicUserCredentials c = new BasicUserCredentials("");
        assertFalse(c.isValid());

        c = new BasicUserCredentials("CREDS");
        assertTrue("Valid credentials", c.isValid());
    }
}

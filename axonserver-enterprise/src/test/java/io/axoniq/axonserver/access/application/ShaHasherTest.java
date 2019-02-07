package io.axoniq.axonserver.access.application;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author Marc Gathier
 */
public class ShaHasherTest {
    @Test
    public void checkpw() throws Exception {
        ShaHasher hasher = new ShaHasher();
        String hashed = hasher.hash("password");
        System.out.println(hashed);
        assertTrue(hasher.checkpw("password", hashed));
    }
    @Test
    public void checkInvalid() throws Exception {
        ShaHasher hasher = new ShaHasher();
        assertFalse(hasher.checkpw("password", "aaaaaaaaaaaaaaaa"));
    }

}
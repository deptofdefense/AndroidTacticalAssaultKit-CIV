package gov.tak.platform.contact;

import gov.tak.api.contact.IContactStore;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.Assert.*;

/**
 * Unit tests for the {@link Contacts} class.
 *
 * @since 0.17.0
 */
public class ContactsTest {
    @Test
    public void getContactStore_ReturnsSameInstance() {
        IContactStore contactStoreOne = Contacts.getContactStore();
        IContactStore contactStoreTwo = Contacts.getContactStore();

        assertSame(
                "Expected same instance to be returned by multiple invocations of Contacts.getContactStore().",
                contactStoreOne, contactStoreTwo);
    }

    @Test
    public void getContactStore_IsThreadSafe() throws InterruptedException {
        final int numThreads = 3;
        final IContactStore[] contactStores = new IContactStore[numThreads];
        final CountDownLatch startCountDownLatch = new CountDownLatch(1);
        final CountDownLatch stopCountDownLatch = new CountDownLatch(numThreads);
        final ExecutorService executorService = Executors.newFixedThreadPool(numThreads);

        // Setup threads to access Contacts.getContactStore at the same time.
        for (int i = 0; i < numThreads; i++) {
            final int index = i;
            executorService.submit(() -> {
                try {
                    startCountDownLatch.await();
                } catch (InterruptedException e) {
                    fail("Exception occurred while awaiting starting CountDownLatch: " + e);
                }
                contactStores[index] = Contacts.getContactStore();
                stopCountDownLatch.countDown();
            });
        }

        // Start all threads at the same time and wait for them all to finish.
        startCountDownLatch.countDown();
        stopCountDownLatch.await();

        // Get the first store and use as "truth" store.
        final IContactStore expectedContactStore = contactStores[0];

        // Assert that all threads received the same IContactStore instance.
        for (IContactStore currentStore : contactStores) {
            assertSame(
                    "Expected multiple threads accessing Contacts.getContactStore() to receive the same store instance.",
                    expectedContactStore,
                    currentStore);
        }
    }
}


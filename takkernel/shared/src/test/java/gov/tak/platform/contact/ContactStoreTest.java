package gov.tak.platform.contact;

import gov.tak.api.contact.IContactListener;
import gov.tak.api.contact.IIndividualContact;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the {@link ContactStore} class.
 *
 * @since 0.17.0
 */
@RunWith(MockitoJUnitRunner.class)
public class ContactStoreTest {
    private static final int DEFAULT_VERIFY_TIMEOUT_MS = 250;
    private static final String DEFAULT_CONTACT_ID = "Goose123";
    private static final String DEFAULT_CONTACT_DISPLAY_NAME = "GooseTheGreat";
    private static final String ALTERNATE_CONTACT_ID = "Falcon456";

    private ContactStore contactStoreUnderTest;

    @Mock
    private IContactListener contactListener;

    @Mock
    private IIndividualContact contact;

    @Before
    public void setupContactStore() {
        contactStoreUnderTest = new ContactStore();

        when(contact.getUniqueId()).thenReturn(DEFAULT_CONTACT_ID);
        when(contact.getDisplayName()).thenReturn(DEFAULT_CONTACT_DISPLAY_NAME);
    }

    @Test(expected = NullPointerException.class)
    public void addContact_ThrowsNullPointerException_WhenGivenNullContact() {
        contactStoreUnderTest.addContact(null);
    }

    @Test
    public void addContact_IgnoresContact_WhenContactHasAlreadyBeenAdded() {
        contactStoreUnderTest.registerContactListener(contactListener);
        contactStoreUnderTest.addContact(contact);
        contactStoreUnderTest.addContact(contact);

        verify(contactListener, timeout(DEFAULT_VERIFY_TIMEOUT_MS)).contactAdded(contact);
    }

    @Test
    public void updateContact_CorrectlyUpdatesExistingContact() {
        final IIndividualContact updatedContact = mock(IIndividualContact.class);
        final String updatedContactDisplayName = contact.getDisplayName() + "-2";
        final ArgumentCaptor<IIndividualContact> contactCaptor = ArgumentCaptor.forClass(IIndividualContact.class);

        when(updatedContact.getUniqueId()).thenReturn(DEFAULT_CONTACT_ID);
        when(updatedContact.getDisplayName()).thenReturn(updatedContactDisplayName);

        contactStoreUnderTest.registerContactListener(contactListener);
        contactStoreUnderTest.addContact(contact);
        contactStoreUnderTest.updateContact(updatedContact);

        verify(contactListener, timeout(DEFAULT_VERIFY_TIMEOUT_MS)).contactUpdated(contactCaptor.capture());
        assertThat("Expected Contact listener to be notified of updated Contact.",
                updatedContactDisplayName, is(contactCaptor.getValue().getDisplayName()));
        assertThat("Expected containsContact() to return true after Contact has been updated.",
                contactStoreUnderTest.containsContact(updatedContact), is(true));
    }

    @Test(expected = IllegalArgumentException.class)
    public void updateContact_ThrowsIllegalArgumentException_WhenContactHasNotPreviouslyBeenAdded() {
        contactStoreUnderTest.updateContact(contact);
    }

    @Test(expected = NullPointerException.class)
    public void updateContact_ThrowsNullPointerException_WhenGivenNullContact() {
        contactStoreUnderTest.updateContact(null);
    }

    @Test
    public void removeContact_CorrectlyRemovesContact() {
        contactStoreUnderTest.addContact(contact);
        contactStoreUnderTest.removeContact(contact.getUniqueId());

        assertThat("Expected Contact to be removed from store after calling removeContact().",
                contactStoreUnderTest.containsContact(contact), is(false));
    }

    @Test
    public void removeContact_NotifiesContactListener() {
        contactStoreUnderTest.registerContactListener(contactListener);
        contactStoreUnderTest.addContact(contact);
        contactStoreUnderTest.removeContact(contact.getUniqueId());

        verify(contactListener, timeout(DEFAULT_VERIFY_TIMEOUT_MS)).contactRemoved(contact);
    }

    @Test(expected = NullPointerException.class)
    public void removeContact_ThrowsNullPointerException_WhenGivenNullContactId() {
        contactStoreUnderTest.removeContact(null);
    }

    @Test
    public void removeContact_DoesNotNotifyListeners_WhenRemovingContactThatHasNotBeenAdded() {
        contactStoreUnderTest.registerContactListener(contactListener);
        contactStoreUnderTest.removeContact(contact.getUniqueId());

        verifyNoInteractions(contactListener);
    }

    @Test
    public void containsContact_ReturnsFalse_WhenNoContactsHaveBeenAdded() {
        assertThat("Expected containsContact() to return false when no Contacts have been added.",
                contactStoreUnderTest.containsContact(contact), is(false));
    }

    @Test
    public void containsContact_ReturnsTrue_WhenContactHasBeenAdded() {
        contactStoreUnderTest.addContact(contact);
        assertThat("Expected containsContact() to return true for a Contact that has been added to the store.",
                contactStoreUnderTest.containsContact(contact), is(true));
    }

    @Test
    public void containsContact_ReturnsFalse_WhenContactHasNotBeenAdded_WhenOtherContactsExistInStore() {
        final IIndividualContact contactTwo = mock(IIndividualContact.class);

        when(contactTwo.getUniqueId()).thenReturn(ALTERNATE_CONTACT_ID);

        contactStoreUnderTest.addContact(contact);
        assertThat(
                "Expected containsContact() to return false for a Contact that has not been added, while other Contacts exist in store.",
                contactStoreUnderTest.containsContact(contactTwo), is(false));
    }

    @Test
    public void containsContact_ReturnsTrue_WhenCheckingDifferentContactInstanceWithSameUniqueId() {
        final IIndividualContact duplicateContact = mock(IIndividualContact.class);

        when(duplicateContact.getUniqueId()).thenReturn(DEFAULT_CONTACT_ID);

        contactStoreUnderTest.addContact(contact);
        assertThat("Expected containsContact() to return true when Contact with same unique ID already in store.",
                contactStoreUnderTest.containsContact(duplicateContact), is(true));
    }

    @Test(expected = NullPointerException.class)
    public void containsContact_ThrowsNullPointerException_WhenGivenNullContact() {
        contactStoreUnderTest.containsContact(null);
    }

    @Test
    public void registerContactListener_CorrectlyRegistersListener_WhenNoContactsHaveBeenAdded() {
        contactStoreUnderTest.registerContactListener(contactListener);
        contactStoreUnderTest.addContact(contact);

        verify(contactListener, timeout(DEFAULT_VERIFY_TIMEOUT_MS)).contactAdded(contact);
    }

    @Test
    public void registerContactListener_CorrectlyRegistersListener_WhenContactsWerePreviouslyAdded() {
        final IIndividualContact contactTwo = mock(IIndividualContact.class);

        when(contactTwo.getUniqueId()).thenReturn(ALTERNATE_CONTACT_ID);

        contactStoreUnderTest.addContact(contact);
        contactStoreUnderTest.registerContactListener(contactListener);
        contactStoreUnderTest.addContact(contactTwo);

        verify(contactListener, timeout(DEFAULT_VERIFY_TIMEOUT_MS)).contactAdded(contact);
        verify(contactListener, timeout(DEFAULT_VERIFY_TIMEOUT_MS)).contactAdded(contactTwo);
    }

    @Test
    public void registerContactListener_BackfillsExistingContacts() {
        final IIndividualContact contactTwo = mock(IIndividualContact.class);
        final IIndividualContact contactThree = mock(IIndividualContact.class);

        when(contactTwo.getUniqueId()).thenReturn(DEFAULT_CONTACT_ID + "-2");
        when(contactThree.getUniqueId()).thenReturn(DEFAULT_CONTACT_ID + "-3");

        contactStoreUnderTest.addContact(contact);
        contactStoreUnderTest.addContact(contactTwo);
        contactStoreUnderTest.addContact(contactThree);

        contactStoreUnderTest.registerContactListener(contactListener);

        verify(contactListener, timeout(DEFAULT_VERIFY_TIMEOUT_MS)).contactAdded(contact);
        verify(contactListener, timeout(DEFAULT_VERIFY_TIMEOUT_MS)).contactAdded(contactTwo);
        verify(contactListener, timeout(DEFAULT_VERIFY_TIMEOUT_MS)).contactAdded(contactThree);
    }

    @Test(expected = NullPointerException.class)
    public void registerContactListener_ThrowsNullPointerException_WhenGivenNullListener() {
        contactStoreUnderTest.registerContactListener(null);
    }

    @Test
    public void registerContactListener_HasNoEffect_WhenListenerHasAlreadyBeenRegistered() {
        contactStoreUnderTest.registerContactListener(contactListener);
        contactStoreUnderTest.registerContactListener(contactListener);

        contactStoreUnderTest.addContact(contact);

        verify(contactListener, timeout(DEFAULT_VERIFY_TIMEOUT_MS)).contactAdded(contact);
    }

    @Test
    public void unregisterContactListener_CorrectlyRemovesListener_WhenNoContactsHaveBeenAdded() {
        final IIndividualContact contactTwo = mock(IIndividualContact.class);

        when(contactTwo.getUniqueId()).thenReturn(ALTERNATE_CONTACT_ID);

        contactStoreUnderTest.registerContactListener(contactListener);
        contactStoreUnderTest.unregisterContactListener(contactListener);
        contactStoreUnderTest.addContact(contactTwo);

        verifyNoInteractions(contactListener);
    }

    @Test
    public void unregisterContactListener_CorrectlyRemovesListener_WhenContactsWerePreviouslyAdded() {
        final IIndividualContact contactTwo = mock(IIndividualContact.class);

        when(contactTwo.getUniqueId()).thenReturn(ALTERNATE_CONTACT_ID);

        contactStoreUnderTest.addContact(contact);
        contactStoreUnderTest.registerContactListener(contactListener);
        contactStoreUnderTest.unregisterContactListener(contactListener);
        contactStoreUnderTest.addContact(contactTwo);

        verify(contactListener, timeout(DEFAULT_VERIFY_TIMEOUT_MS)).contactAdded(contact);
        verifyNoMoreInteractions(contactListener);
    }

    @Test(expected = NullPointerException.class)
    public void unregisterContactListener_ThrowsNullPointerException_WhenGivenNullListener() {
        contactStoreUnderTest.registerContactListener(contactListener);
        contactStoreUnderTest.unregisterContactListener(null);
    }

    @Test
    public void unregisterContactListener_HasNoEffect_WhenListenerHasNotBeenRegistered() {
        contactStoreUnderTest.unregisterContactListener(contactListener);

        contactStoreUnderTest.addContact(contact);

        verifyNoInteractions(contactListener);
    }

    @Test
    public void containsListener_ReturnsFalse_WhenListenerHasNotBeenAdded() {
        assertThat("Expected containsListener() to return false when no listeners have been added.",
                contactStoreUnderTest.containsListener(contactListener), is(false));
    }

    @Test
    public void containsListener_ReturnsTrue_WhenListenerHasBeenAdded() {
        contactStoreUnderTest.registerContactListener(contactListener);
        assertThat("Expected containsListener() to return true when listeners have been added.",
                contactStoreUnderTest.containsListener(contactListener), is(true));
    }

    @Test(expected = NullPointerException.class)
    public void containsListener_ThrowsNullPointerException_WhenGivenNullListener() {
        contactStoreUnderTest.containsListener(null);
    }

}


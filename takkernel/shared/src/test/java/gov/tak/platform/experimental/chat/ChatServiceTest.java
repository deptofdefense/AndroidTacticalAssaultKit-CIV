package gov.tak.platform.experimental.chat;

import com.google.common.collect.Sets;
import gov.tak.api.contact.IContact;
import gov.tak.api.contact.IGroupContact;
import gov.tak.api.contact.IIndividualContact;
import gov.tak.api.experimental.chat.IChatMessage;
import gov.tak.api.experimental.chat.IChatService;
import gov.tak.api.experimental.chat.IChatServiceClient;
import gov.tak.api.experimental.chat.IChatServiceProvider;
import gov.tak.api.experimental.chat.IChatServiceProviderDelegate;
import gov.tak.api.experimental.chat.ISendCallback;
import gov.tak.api.util.AttributeSet;
import gov.tak.platform.contact.Contacts;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the {@link ChatService} class.
 *
 * @since 0.21.0
 */
@RunWith(MockitoJUnitRunner.class)
public class ChatServiceTest {
    private static final String NULL_DELEGATE_TEST_FAILURE_MESSAGE = "Expected ChatService to return non-null delegate upon registering provider.";
    private static final String DEFAULT_SERVICE_PROVIDER_PROTOCOL = "Carrier Pigeon Provider";
    private static final String DEFAULT_SERVICE_CLIENT_UID = "Tango-Alpha-Kilo-21";
    private static final String DEFAULT_SERVICE_CLIENT_DISPLAY_NAME = "TAK-21";

    private ChatService chatServiceUnderTest;

    @Mock
    private IChatServiceProvider chatServiceProvider;

    @Mock
    private IChatServiceClient chatServiceClient;

    @Mock
    private ISendCallback sendCallback;

    @Before
    public void setupChatService() {
        when(chatServiceProvider.getProviderProtocol()).thenReturn(DEFAULT_SERVICE_PROVIDER_PROTOCOL);
        chatServiceUnderTest = new ChatService();
    }

    @Test
    public void getChatService_ReturnsSameInstance() {
        IChatService chatServiceOne = ChatService.getChatService();
        IChatService chatServiceTwo = ChatService.getChatService();

        assertSame("Expected same instance to be returned by multiple invocations of ChatService.getChatService().",
                chatServiceOne, chatServiceTwo);
    }

    @Test
    public void getChatService_IsThreadSafe() throws InterruptedException {
        final int numThreads = 3;
        final IChatService[] chatServices = new IChatService[numThreads];
        final CountDownLatch startCountDownLatch = new CountDownLatch(1);
        final CountDownLatch stopCountDownLatch = new CountDownLatch(numThreads);
        final ExecutorService executorService = Executors.newFixedThreadPool(numThreads);

        // Setup threads to access ChatService.getChatService at the same time.
        for (int i = 0; i < numThreads; i++) {
            final int index = i;
            executorService.submit(() -> {
                try {
                    startCountDownLatch.await();
                } catch (InterruptedException e) {
                    fail("Exception occurred while awaiting starting CountDownLatch: " + e);
                }
                chatServices[index] = ChatService.getChatService();
                stopCountDownLatch.countDown();
            });
        }

        // Start all threads at the same time and wait for them all to finish.
        startCountDownLatch.countDown();
        stopCountDownLatch.await();

        // Get the first chat service and use as "truth".
        final IChatService expectedChatService = chatServices[0];

        // Assert that all threads received the same IChatService instance.
        for (IChatService currentService : chatServices) {
            assertSame(
                    "Expected multiple threads accessing ChatService.getChatService() to receive the same service instance.",
                    expectedChatService,
                    currentService);
        }
    }

    @Test
    public void sendMessage_RoutesMessagesToCorrectProviders_WhenMultipleProvidersRegistered() {
        final IChatServiceProvider chatServiceProviderTwo = mock(IChatServiceProvider.class);
        final String chatServiceProviderTwoCapability = chatServiceProvider.getProviderProtocol() + "-2";
        final IContact contactOne = mock(IContact.class);
        final IContact contactTwo = mock(IContact.class);
        final AttributeSet contactOneAttributes = new AttributeSet();
        final AttributeSet contactTwoAttributes = new AttributeSet();
        final String[] contactOneCapabilities = { chatServiceProvider.getProviderProtocol() };
        final String[] contactTwoCapabilities = { chatServiceProviderTwoCapability };
        final IChatMessage chatMessageOne = mock(IChatMessage.class);
        final IChatMessage chatMessageTwo = mock(IChatMessage.class);

        contactOneAttributes.setAttribute(Contacts.PROTOCOL_ATTRIBUTE_KEY, contactOneCapabilities);
        contactTwoAttributes.setAttribute(Contacts.PROTOCOL_ATTRIBUTE_KEY, contactTwoCapabilities);

        when(contactOne.getAttributes()).thenReturn(contactOneAttributes);
        when(contactTwo.getAttributes()).thenReturn(contactTwoAttributes);
        when(chatServiceProviderTwo.getProviderProtocol()).thenReturn(chatServiceProviderTwoCapability);

        chatServiceUnderTest.registerChatServiceProvider(chatServiceProvider);
        chatServiceUnderTest.registerChatServiceProvider(chatServiceProviderTwo);
        chatServiceUnderTest.sendMessage(contactOne, chatMessageOne, sendCallback);
        chatServiceUnderTest.sendMessage(contactTwo, chatMessageTwo, sendCallback);

        verify(chatServiceProvider).sendMessage(contactOne, chatMessageOne, sendCallback);
        verify(chatServiceProviderTwo).sendMessage(contactTwo, chatMessageTwo, sendCallback);
    }

    @Test(expected = NullPointerException.class)
    public void sendMessage_ThrowsNullPointerException_WhenGivenNullContact() {
        final IChatMessage chatMessage = mock(IChatMessage.class);

        chatServiceUnderTest.sendMessage(null, chatMessage, sendCallback);
    }

    @Test(expected = NullPointerException.class)
    public void sendMessage_ThrowsNullPointerException_WhenGivenNullChatMessage() {
        final IContact contact = mock(IContact.class);

        chatServiceUnderTest.sendMessage(contact, null, sendCallback);
    }

    @Test(expected = IllegalArgumentException.class)
    public void sendMessage_ThrowsIllegalArgumentException_WhenContactDoesNotHaveCapabilities() {
        final IContact contact = mock(IContact.class);
        final IChatMessage chatMessage = mock(IChatMessage.class);

        when(contact.getAttributes()).thenReturn(new AttributeSet());

        chatServiceUnderTest.sendMessage(contact, chatMessage, sendCallback);
    }

    @Test(expected = IllegalArgumentException.class)
    public void sendMessage_ThrowsIllegalArgumentException_WhenContactCapabilitiesAreEmpty() {
        final IContact contact = mock(IContact.class);
        final IChatMessage chatMessage = mock(IChatMessage.class);
        final AttributeSet contactAttributes = new AttributeSet();
        final String[] emptyCapabilities = {};

        contactAttributes.setAttribute(Contacts.PROTOCOL_ATTRIBUTE_KEY, emptyCapabilities);
        when(contact.getAttributes()).thenReturn(contactAttributes);

        chatServiceUnderTest.sendMessage(contact, chatMessage, sendCallback);
    }

    @Test
    public void createGroupContact_RequestsServiceProviderCreateGroupContact_WhenOneProviderRegistered() {
        final String testGroupName = "testGroup";
        final IIndividualContact individualContact = mock(IIndividualContact.class);
        final Set<IContact> testGroupMembers = Sets.newHashSet(individualContact);
        final AttributeSet individualContactAttributes = new AttributeSet();
        final String[] individualContactCapabilities = { chatServiceProvider.getProviderProtocol() };

        individualContactAttributes
                .setAttribute(Contacts.PROTOCOL_ATTRIBUTE_KEY, individualContactCapabilities);
        testGroupMembers.add(individualContact);

        when(individualContact.getAttributes()).thenReturn(individualContactAttributes);

        chatServiceUnderTest.registerChatServiceProvider(chatServiceProvider);
        chatServiceUnderTest
                .createGroupContact(testGroupName, testGroupMembers, chatServiceProvider.getProviderProtocol());

        verify(chatServiceProvider).createGroupContact(testGroupName, testGroupMembers);
    }

    @Test
    public void createGroupContact_RequestsServiceProviderCreateGroupContact_WhenMultipleProvidersRegistered() {
        final String testGroupName = "testGroup";
        final IChatServiceProvider chatServiceProviderTwo = mock(IChatServiceProvider.class);
        final String chatServiceProviderTwoCapability = chatServiceProvider.getProviderProtocol() + "-2";
        final IIndividualContact individualContact = mock(IIndividualContact.class);
        final Set<IContact> testGroupMembers = Sets.newHashSet(individualContact);
        final AttributeSet individualContactAttributes = new AttributeSet();
        final String[] individualContactCapabilities = { chatServiceProvider.getProviderProtocol() };

        individualContactAttributes
                .setAttribute(Contacts.PROTOCOL_ATTRIBUTE_KEY, individualContactCapabilities);
        testGroupMembers.add(individualContact);

        when(individualContact.getAttributes()).thenReturn(individualContactAttributes);
        when(chatServiceProviderTwo.getProviderProtocol()).thenReturn(chatServiceProviderTwoCapability);

        chatServiceUnderTest.registerChatServiceProvider(chatServiceProvider);
        chatServiceUnderTest.registerChatServiceProvider(chatServiceProviderTwo);
        chatServiceUnderTest
                .createGroupContact(testGroupName, testGroupMembers, chatServiceProvider.getProviderProtocol());

        verify(chatServiceProvider).createGroupContact(testGroupName, testGroupMembers);
        verify(chatServiceProviderTwo, times(0)).createGroupContact(any(), any());
    }

    @Test(expected = IllegalArgumentException.class)
    public void createGroupContact_ThrowsIllegalArgumentException_WhenGroupMemberDoesNotHaveRequestedCapability() {
        final String testGroupName = "testGroup";
        final String chatServiceProviderTwoCapability = chatServiceProvider.getProviderProtocol() + "-2";
        final IIndividualContact individualContactOne = mock(IIndividualContact.class);
        final IIndividualContact individualContactTwo = mock(IIndividualContact.class);
        final AttributeSet individualContactOneAttributes = new AttributeSet();
        final AttributeSet individualContactTwoAttributes = new AttributeSet();
        final String[] contactOneCapabilities = { chatServiceProvider.getProviderProtocol() };
        final String[] contactTwoCapabilities = { chatServiceProviderTwoCapability };

        individualContactOneAttributes.setAttribute(Contacts.PROTOCOL_ATTRIBUTE_KEY, contactOneCapabilities);
        individualContactTwoAttributes.setAttribute(Contacts.PROTOCOL_ATTRIBUTE_KEY, contactTwoCapabilities);

        // Use lenient stubbing here to prevent non-deterministic throwing of an UnnecessaryStubbingException
        lenient().when(individualContactOne.getAttributes()).thenReturn(individualContactOneAttributes);
        lenient().when(individualContactTwo.getAttributes()).thenReturn(individualContactTwoAttributes);

        chatServiceUnderTest.registerChatServiceProvider(chatServiceProvider);
        chatServiceUnderTest.createGroupContact(
                testGroupName,
                Sets.newHashSet(individualContactOne, individualContactTwo),
                chatServiceProvider.getProviderProtocol());
    }

    @Test(expected = NullPointerException.class)
    public void createGroupContact_ThrowsNullPointerException_WhenGroupNameNull() {
        final IIndividualContact individualContact = mock(IIndividualContact.class);

        chatServiceUnderTest.createGroupContact(
                null,
                Sets.newHashSet(individualContact),
                chatServiceProvider.getProviderProtocol());
    }

    @Test(expected = IllegalArgumentException.class)
    public void createGroupContact_ThrowsIllegalArgumentException_WhenGroupMembersIsEmpty() {
        final String testGroupName = "testGroup";

        chatServiceUnderTest.createGroupContact(
                testGroupName,
                Collections.emptySet(),
                chatServiceProvider.getProviderProtocol());
    }

    @Test(expected = NullPointerException.class)
    public void createGroupContact_ThrowsNullPointerException_WhenGroupMembersNull() {
        final String testGroupName = "testGroup";

        chatServiceUnderTest.createGroupContact(
                testGroupName,
                null,
                chatServiceProvider.getProviderProtocol());
    }

    @Test(expected = NullPointerException.class)
    public void createGroupContact_ThrowsNullPointerException_WhenChatServiceCapabilityNull() {
        final String testGroupName = "testGroup";
        final IIndividualContact individualContact = mock(IIndividualContact.class);

        chatServiceUnderTest.createGroupContact(
                testGroupName,
                Sets.newHashSet(individualContact),
                null);
    }

    @Test
    public void deleteGroupContact_RequestsServiceProviderDeleteGroupContact_WhenOneProviderRegistered() {
        final IGroupContact groupContact = mock(IGroupContact.class);
        final AttributeSet groupContactAttributes = new AttributeSet();
        final String[] groupContactCapabilities = { chatServiceProvider.getProviderProtocol() };

        groupContactAttributes.setAttribute(Contacts.PROTOCOL_ATTRIBUTE_KEY, groupContactCapabilities);
        when(groupContact.getAttributes()).thenReturn(groupContactAttributes);

        chatServiceUnderTest.registerChatServiceProvider(chatServiceProvider);
        chatServiceUnderTest.deleteGroupContact(groupContact);

        verify(chatServiceProvider).deleteGroupContact(groupContact);
    }

    @Test
    public void deleteGroupContact_RequestsServiceProviderDeleteGroupContact_WhenMultipleProvidersRegistered() {
        final IGroupContact groupContact = mock(IGroupContact.class);
        final AttributeSet groupContactAttributes = new AttributeSet();
        final String[] groupContactCapabilities = { chatServiceProvider.getProviderProtocol() };
        final IChatServiceProvider chatServiceProviderTwo = mock(IChatServiceProvider.class);
        final String chatServiceProviderTwoCapability = chatServiceProvider.getProviderProtocol() + "-2";

        groupContactAttributes.setAttribute(Contacts.PROTOCOL_ATTRIBUTE_KEY, groupContactCapabilities);
        when(groupContact.getAttributes()).thenReturn(groupContactAttributes);
        when(chatServiceProviderTwo.getProviderProtocol()).thenReturn(chatServiceProviderTwoCapability);

        chatServiceUnderTest.registerChatServiceProvider(chatServiceProvider);
        chatServiceUnderTest.registerChatServiceProvider(chatServiceProviderTwo);
        chatServiceUnderTest.deleteGroupContact(groupContact);

        verify(chatServiceProvider).deleteGroupContact(groupContact);
        verify(chatServiceProviderTwo, times(0)).deleteGroupContact(any());
    }

    @Test(expected = NullPointerException.class)
    public void deleteGroupContact_ThrowsNullPointerException_WhenGivenNullGroupContact() {
        chatServiceUnderTest.deleteGroupContact(null);
    }

    @Test
    public void updateGroupContact_RequestsServiceProviderUpdateGroupContact_WhenOneProviderRegistered() {
        final IGroupContact groupContact = mock(IGroupContact.class);
        final AttributeSet groupContactAttributes = new AttributeSet();
        final String[] groupContactCapabilities = { chatServiceProvider.getProviderProtocol() };

        groupContactAttributes.setAttribute(Contacts.PROTOCOL_ATTRIBUTE_KEY, groupContactCapabilities);
        when(groupContact.getAttributes()).thenReturn(groupContactAttributes);

        chatServiceUnderTest.registerChatServiceProvider(chatServiceProvider);
        chatServiceUnderTest.updateGroupContact(groupContact);

        verify(chatServiceProvider).updateGroupContact(groupContact);
    }

    @Test
    public void updateGroupContact_RequestsServiceProviderUpdateGroupContact_WhenMultipleProvidersRegistered() {
        final IGroupContact groupContact = mock(IGroupContact.class);
        final AttributeSet groupContactAttributes = new AttributeSet();
        final String[] groupContactCapabilities = { chatServiceProvider.getProviderProtocol() };
        final IChatServiceProvider chatServiceProviderTwo = mock(IChatServiceProvider.class);
        final String chatServiceProviderTwoCapability = chatServiceProvider.getProviderProtocol() + "-2";

        groupContactAttributes.setAttribute(Contacts.PROTOCOL_ATTRIBUTE_KEY, groupContactCapabilities);
        when(groupContact.getAttributes()).thenReturn(groupContactAttributes);
        when(chatServiceProviderTwo.getProviderProtocol()).thenReturn(chatServiceProviderTwoCapability);

        chatServiceUnderTest.registerChatServiceProvider(chatServiceProvider);
        chatServiceUnderTest.registerChatServiceProvider(chatServiceProviderTwo);
        chatServiceUnderTest.updateGroupContact(groupContact);

        verify(chatServiceProvider).updateGroupContact(groupContact);
        verify(chatServiceProviderTwo, times(0)).updateGroupContact(any());
    }

    @Test(expected = NullPointerException.class)
    public void updateGroupContact_ThrowsNullPointerException_WhenGivenNullGroupContact() {
        chatServiceUnderTest.updateGroupContact(null);
    }

    @Test(expected = NullPointerException.class)
    public void receiveMessage_ThrowsNullPointerException_WhenGivenNullContact() {
        final IChatMessage chatMessage = mock(IChatMessage.class);

        chatServiceUnderTest.receiveMessage(null, chatMessage);
    }

    @Test(expected = NullPointerException.class)
    public void receiveMessage_ThrowsNullPointerException_WhenGivenNullChatMessage() {
        final IContact contact = mock(IContact.class);

        chatServiceUnderTest.receiveMessage(contact, null);
    }

    @Test
    public void registerChatServiceProvider_SuccessfullyRegistersProvider() {
        final IContact contact = mock(IContact.class);
        final IChatMessage chatMessage = mock(IChatMessage.class);
        final AttributeSet contactAttributes = new AttributeSet();

        contactAttributes.setAttribute(Contacts.PROTOCOL_ATTRIBUTE_KEY,
                new String[] { chatServiceProvider.getProviderProtocol() });
        when(contact.getAttributes()).thenReturn(contactAttributes);

        chatServiceUnderTest.registerChatServiceProvider(chatServiceProvider);
        chatServiceUnderTest.sendMessage(contact, chatMessage, sendCallback);

        verify(chatServiceProvider).sendMessage(contact, chatMessage, sendCallback);
    }

    @Test
    public void registerChatServiceProvider_ReturnsNonNullDelegate_WhenGivenNonNullProvider() {
        IChatServiceProviderDelegate chatServiceProviderDelegate =
                chatServiceUnderTest.registerChatServiceProvider(chatServiceProvider);

        assertNotNull(NULL_DELEGATE_TEST_FAILURE_MESSAGE, chatServiceProviderDelegate);
    }

    @Test
    public void registerChatServiceProvider_ReturnsNonNullDelegate_WhenInvokedWithDifferentProviders() {
        final String chatServiceProviderOneCapability = chatServiceProvider.getProviderProtocol();
        final IChatServiceProvider chatServiceProviderTwo = mock(IChatServiceProvider.class);
        final IChatServiceProvider chatServiceProviderThree = mock(IChatServiceProvider.class);

        when(chatServiceProviderTwo.getProviderProtocol()).thenReturn(chatServiceProviderOneCapability + "-2");
        when(chatServiceProviderThree.getProviderProtocol()).thenReturn(chatServiceProviderOneCapability + "-3");

        final IChatServiceProviderDelegate chatServiceProviderDelegateOne =
                chatServiceUnderTest.registerChatServiceProvider(chatServiceProvider);

        final IChatServiceProviderDelegate chatServiceProviderDelegateTwo =
                chatServiceUnderTest.registerChatServiceProvider(chatServiceProviderTwo);

        final IChatServiceProviderDelegate chatServiceProviderDelegateThree =
                chatServiceUnderTest.registerChatServiceProvider(chatServiceProviderThree);

        assertNotNull(NULL_DELEGATE_TEST_FAILURE_MESSAGE, chatServiceProviderDelegateOne);
        assertNotNull(NULL_DELEGATE_TEST_FAILURE_MESSAGE, chatServiceProviderDelegateTwo);
        assertNotNull(NULL_DELEGATE_TEST_FAILURE_MESSAGE, chatServiceProviderDelegateThree);
    }

    @Test
    public void registerChatServiceProvider_ReturnsNonNullDelegate_WhenProviderHasAlreadyBeenRegistered() {
        chatServiceUnderTest.registerChatServiceProvider(chatServiceProvider);

        IChatServiceProviderDelegate chatServiceProviderDelegate =
                chatServiceUnderTest.registerChatServiceProvider(chatServiceProvider);

        assertNotNull("Expected non-null delegate to be returned upon duplicate provider registration.",
                chatServiceProviderDelegate);
    }

    @Test
    public void registerChatServiceProvider_DoesNotRegisterProviderInstance_WhenDifferentProviderWithSameCapabilityHasAlreadyBeenRegistered() {
        final String chatServiceProviderOneCapability = chatServiceProvider.getProviderProtocol();
        final IChatServiceProvider chatServiceProviderTwo = mock(IChatServiceProvider.class);
        final IContact contact = mock(IContact.class);
        final AttributeSet contactAttributes = new AttributeSet();
        final String[] contactCapabilities = { chatServiceProviderOneCapability };
        final IChatMessage chatMessage = mock(IChatMessage.class);

        contactAttributes.setAttribute(Contacts.PROTOCOL_ATTRIBUTE_KEY, contactCapabilities);
        when(contact.getAttributes()).thenReturn(contactAttributes);
        when(chatServiceProviderTwo.getProviderProtocol()).thenReturn(chatServiceProviderOneCapability);

        chatServiceUnderTest.registerChatServiceProvider(chatServiceProvider);
        chatServiceUnderTest.registerChatServiceProvider(chatServiceProviderTwo);
        chatServiceUnderTest.sendMessage(contact, chatMessage, sendCallback);

        verify(chatServiceProvider).sendMessage(contact, chatMessage, sendCallback);
        verify(chatServiceProviderTwo, times(0)).sendMessage(any(), any(), any());
    }

    @Test(expected = NullPointerException.class)
    public void registerChatServiceProvider_ThrowsNullPointerException_WhenGivenNullProvider() {
        chatServiceUnderTest.registerChatServiceProvider(null);
    }

    @Test
    public void unregisterChatServiceProvider_SuccessfullyUnregistersProvider_WhenProviderHasAlreadyBeenRegistered() {
        final IContact contact = mock(IContact.class);
        final IChatMessage chatMessage = mock(IChatMessage.class);
        final AttributeSet contactAttributes = new AttributeSet();
        final String[] contactCapabilities = { chatServiceProvider.getProviderProtocol() };

        contactAttributes.setAttribute(Contacts.PROTOCOL_ATTRIBUTE_KEY, contactCapabilities);
        when(contact.getAttributes()).thenReturn(contactAttributes);

        chatServiceUnderTest.registerChatServiceProvider(chatServiceProvider);
        chatServiceUnderTest.unregisterChatServiceProvider(chatServiceProvider);

        chatServiceUnderTest.sendMessage(contact, chatMessage, sendCallback);

        verify(chatServiceProvider, times(0)).sendMessage(any(), any(), any());
    }

    @Test(expected = NullPointerException.class)
    public void unregisterChatServiceProvider_ThrowsNullPointerException_WhenGivenNullProvider() {
        chatServiceUnderTest.registerChatServiceProvider(chatServiceProvider);
        chatServiceUnderTest.unregisterChatServiceProvider(null);
    }

    @Test
    public void unregisterChatServiceProvider_HasNoEffect_WhenProviderHasNotBeenRegistered() {
        final IContact contact = mock(IContact.class);
        final IChatMessage chatMessage = mock(IChatMessage.class);
        final AttributeSet contactAttributes = new AttributeSet();
        final String[] contactCapabilities = { chatServiceProvider.getProviderProtocol() };

        contactAttributes.setAttribute(Contacts.PROTOCOL_ATTRIBUTE_KEY, contactCapabilities);
        when(contact.getAttributes()).thenReturn(contactAttributes);

        chatServiceUnderTest.unregisterChatServiceProvider(chatServiceProvider);
        chatServiceUnderTest.unregisterChatServiceProvider(chatServiceProvider);
        chatServiceUnderTest.sendMessage(contact, chatMessage, sendCallback);

        verify(chatServiceProvider, times(0)).sendMessage(any(), any(), any());
    }

    @Test
    public void registerChatServiceClient_SuccessfullyAddsChatServiceClient() {
        final IContact contact = mock(IContact.class);
        final IChatMessage chatMessage = mock(IChatMessage.class);

        chatServiceUnderTest.registerChatServiceClient(chatServiceClient);
        chatServiceUnderTest.receiveMessage(contact, chatMessage);

        verify(chatServiceClient).receiveMessage(contact, chatMessage);
        verifyNoMoreInteractions(chatServiceClient);
    }

    @Test(expected = NullPointerException.class)
    public void registerChatServiceClient_ThrowsNullPointerException_WhenGivenNullClient() {
        chatServiceUnderTest.registerChatServiceClient(null);
    }

    @Test
    public void registerChatServiceClient_ReturnsNonNullDelegate_WhenSameClientHasAlreadyBeenRegistered() {
        chatServiceUnderTest.registerChatServiceClient(chatServiceClient);

        assertNotNull("Expected duplicate chat service client registration to return non-null delegate.",
                chatServiceUnderTest.registerChatServiceClient(chatServiceClient));
    }

    @Test(expected = IllegalArgumentException.class)
    public void registerChatServiceClient_ThrowsIllegalArgumentException_WhenSeparateClientHasAlreadyBeenRegistered() {
        chatServiceUnderTest.registerChatServiceClient(chatServiceClient);
        chatServiceUnderTest.registerChatServiceClient(mock(IChatServiceClient.class));
    }

    @Test
    public void unregisterChatServiceClient_SuccessfullyUnregistersChatServiceClient() {
        final IContact contact = mock(IContact.class);
        final IChatMessage chatMessage = mock(IChatMessage.class);

        chatServiceUnderTest.registerChatServiceClient(chatServiceClient);
        chatServiceUnderTest.unregisterChatServiceClient(chatServiceClient);

        chatServiceUnderTest.receiveMessage(contact, chatMessage);

        verifyNoMoreInteractions(chatServiceClient);
    }

    @Test(expected = NullPointerException.class)
    public void unregisterChatServiceClient_ThrowsNullPointerException_WhenGivenNullClient() {
        chatServiceUnderTest.registerChatServiceClient(chatServiceClient);
        chatServiceUnderTest.unregisterChatServiceClient(null);
    }

    @Test
    public void unregisterChatServiceClient_HasNoEffect_WhenClientHasNotBeenRegistered() {
        final IContact contact = mock(IContact.class);
        final IChatMessage chatMessage = mock(IChatMessage.class);

        chatServiceUnderTest.unregisterChatServiceClient(chatServiceClient);
        chatServiceUnderTest.receiveMessage(contact, chatMessage);

        verifyNoInteractions(chatServiceClient);
    }

    @Test
    public void unregisterChatServiceClient_HasNoEffect_WhenClientHasNotBeenRegistered_WhenDifferentClientHasPreviouslyBeenRegistered() {
        final IContact contact = mock(IContact.class);
        final IChatMessage chatMessage = mock(IChatMessage.class);
        final IChatServiceClient chatServiceClientTwo = mock(IChatServiceClient.class);

        chatServiceUnderTest.registerChatServiceClient(chatServiceClient);
        chatServiceUnderTest.unregisterChatServiceClient(chatServiceClientTwo);
        chatServiceUnderTest.receiveMessage(contact, chatMessage);

        verify(chatServiceClient).receiveMessage(contact, chatMessage);
        verifyNoInteractions(chatServiceClientTwo);
    }
}


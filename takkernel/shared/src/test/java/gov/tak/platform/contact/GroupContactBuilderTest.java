package gov.tak.platform.contact;

import com.google.common.collect.Sets;
import gov.tak.api.contact.IContact;
import gov.tak.api.contact.IGroupContact;
import gov.tak.api.contact.IIndividualContact;
import gov.tak.api.util.AttributeSet;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Collections;
import java.util.Set;

import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the {@link GroupContactBuilder} class.
 *
 * @since 0.17.0
 */
@RunWith(MockitoJUnitRunner.class)
public class GroupContactBuilderTest {
    private static final String DEFAULT_UNIQUE_ID = "ID#135246";

    private GroupContactBuilder groupContactBuilderUnderTest;
    private Set<IContact> defaultGroupMembers;

    @Before
    public void setupGroupContactBuilder() {
        defaultGroupMembers = Sets.newHashSet(mock(IContact.class));
        groupContactBuilderUnderTest = new GroupContactBuilder();
    }

    @Test
    public void build_GeneratesUniqueId_WhenUniqueIdNotExplicitlySet() {
        final IGroupContact contact =
                groupContactBuilderUnderTest.withGroupMembers(defaultGroupMembers).build();

        assertThat("Expected group contact to have unique contact ID automatically generated if not explicitly set.",
                contact.getUniqueId(), notNullValue());
    }

    @Test
    public void build_GeneratesNewUniqueIdBetweenCalls_WhenUniqueIdNotExplicitlySet() {
        final IGroupContact contactOne =
                groupContactBuilderUnderTest.withGroupMembers(defaultGroupMembers).build();
        final IGroupContact contactTwo =
                groupContactBuilderUnderTest.withGroupMembers(defaultGroupMembers).build();

        assertThat("Expected group contacts to have new unique IDs generated each time they are built.",
                contactOne.getUniqueId(), is(not(contactTwo.getUniqueId())));
    }

    @Test
    public void build_RevertsToGeneratingUniqueId_AfterBuildingGroupContactWithExplicitUniqueId() {
        final IGroupContact contactOne = groupContactBuilderUnderTest
                .withUniqueContactId(DEFAULT_UNIQUE_ID)
                .withGroupMembers(defaultGroupMembers)
                .build();
        final IGroupContact contactTwo =
                groupContactBuilderUnderTest.withGroupMembers(defaultGroupMembers).build();

        assertThat("Expected group contact to contain generated unique ID after previously-built contact contained "
                        + "explicitly set unique ID.",
                contactOne.getUniqueId(), is(not(contactTwo.getUniqueId())));
    }

    @Test
    public void build_UsesUniqueIdAsDisplayName_WhenDisplayNameNotExplicitlySet() {
        final IGroupContact contact =
                groupContactBuilderUnderTest.withGroupMembers(defaultGroupMembers).build();

        assertThat("Expected unique ID to equal display name when display name not explicitly set.",
                contact.getUniqueId(), is(contact.getDisplayName()));
    }

    @Test
    public void build_RevertsToUsingUniqueIdAsDisplayName_AfterBuildingAGroupContact() {
        groupContactBuilderUnderTest.withGroupMembers(defaultGroupMembers).withDisplayName("Test").build();

        final IGroupContact contact =
                groupContactBuilderUnderTest.withGroupMembers(defaultGroupMembers).build();

        assertThat("Expected GroupContactBuilder to revert to using the unique ID as the display name after "
                        + "building a contact.",
                contact.getUniqueId(), is(contact.getDisplayName()));
    }

    @Test
    public void withUniqueContactId_CorrectlySetsTheUniqueContactId() {
        final IGroupContact contact = groupContactBuilderUnderTest
                .withGroupMembers(defaultGroupMembers)
                .withUniqueContactId(DEFAULT_UNIQUE_ID)
                .build();

        assertThat("Expected the unique contact ID that was set on GroupContactBuilder to be equal to the unique "
                        + "contact ID returned by the built IGroupContact.",
                DEFAULT_UNIQUE_ID, is(contact.getUniqueId()));
    }

    @Test
    public void withDisplayName_CorrectlySetsTheDisplayName() {
        final String testDisplayName = "Admiral Ackbar";
        final IGroupContact contact = groupContactBuilderUnderTest
                .withGroupMembers(defaultGroupMembers)
                .withDisplayName(testDisplayName)
                .build();

        assertThat("Expected the display name that was set on GroupContactBuilder to be equal to the display name "
                        + "returned by the built IGroupContact.",
                testDisplayName, is(contact.getDisplayName()));
    }

    @Test(expected = NullPointerException.class)
    public void withDisplayName_ThrowsNullPointerException_WhenGivenNullDisplayName() {
        groupContactBuilderUnderTest.withDisplayName(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void withDisplayName_ThrowsIllegalArgumentException_WhenGivenBlankDisplayName() {
        groupContactBuilderUnderTest.withDisplayName(" ");
    }

    @Test
    public void withAttributes_CorrectlySetsAttributes() {
        final AttributeSet attributes = new AttributeSet();
        final String testAttributeKey = "testKey";
        final int testAttributeValue = 2021;

        attributes.setAttribute(testAttributeKey, testAttributeValue);

        final IGroupContact contact = groupContactBuilderUnderTest
                .withGroupMembers(defaultGroupMembers)
                .withAttributes(attributes)
                .build();

        assertThat(
                "Expected attributes set on GroupContactBuilder to be equal to attributes returned by the built "
                        + "IGroupContact.",
                attributes, is(contact.getAttributes()));
    }

    @Test(expected = NullPointerException.class)
    public void withAttributes_ThrowsNullPointerException_WhenGivenNullAttributes() {
        groupContactBuilderUnderTest.withAttributes(null);
    }

    @Test
    public void withGroupMembers_CorrectlySetsGroupMembers() {
        final IIndividualContact contactOne = mock(IIndividualContact.class);
        final IGroupContact contactTwo = mock(IGroupContact.class);
        final IIndividualContact contactThree = mock(IIndividualContact.class);
        final Set<IContact> groupMembers = Sets.newHashSet(contactOne, contactTwo, contactThree);

        final IGroupContact groupContact =
                groupContactBuilderUnderTest.withGroupMembers(groupMembers).build();

        assertThat("Expected the group members set on the GroupContactBuilder to be the same as the group members "
                        + "returned by the built IGroupContact.",
                groupContact.getGroupMembers(), is(groupMembers));
    }

    @Test(expected = NullPointerException.class)
    public void withGroupMembers_ThrowsNullPointerException_WhenGivenNullSetOfGroupMembers() {
        groupContactBuilderUnderTest.withGroupMembers(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void withGroupMembers_ThrowsIllegalArgumentException_WhenGivenEmptySetOfGroupMembers() {
        groupContactBuilderUnderTest.withGroupMembers(Collections.emptySet());
    }
}


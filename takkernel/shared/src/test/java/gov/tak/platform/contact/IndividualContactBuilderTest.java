package gov.tak.platform.contact;

import gov.tak.api.contact.IIndividualContact;
import gov.tak.api.util.AttributeSet;
import org.junit.Before;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.*;

/**
 * Unit tests covering the behavior of both the {@link IndividualContactBuilder} class as well as the
 * {@link ContactBuilder} abstract class.
 *
 * @since 0.17.0
 */
public class IndividualContactBuilderTest {
    private static final String DEFAULT_UNIQUE_ID = "ID#135246";

    private IndividualContactBuilder individualContactBuilderUnderTest;

    @Before
    public void setupIndividualContactBuilder() {
        individualContactBuilderUnderTest = new IndividualContactBuilder();
    }

    @Test
    public void build_GeneratesUniqueId_WhenUniqueIdNotExplicitlySet() {
        final IIndividualContact contact = individualContactBuilderUnderTest.build();

        assertThat("Expected individual contact to have unique contact ID automatically generated if not "
                        + "explicitly set.",
                contact.getUniqueId(), notNullValue());
    }

    @Test
    public void build_GeneratesNewUniqueIdBetweenCalls_WhenUniqueIdNotExplicitlySet() {
        final IIndividualContact contactOne = individualContactBuilderUnderTest.build();
        final IIndividualContact contactTwo = individualContactBuilderUnderTest.build();

        assertThat("Expected individual contacts to have new unique IDs generated each time they are built.",
                contactOne.getUniqueId(), is(not(contactTwo.getUniqueId())));
    }

    @Test
    public void build_RevertsToGeneratingUniqueId_AfterBuildingIndividualContactWithExplicitUniqueId() {
        final IIndividualContact contactOne =
                individualContactBuilderUnderTest.withUniqueContactId(DEFAULT_UNIQUE_ID).build();
        final IIndividualContact contactTwo =
                individualContactBuilderUnderTest.build();

        assertThat("Expected individual contact to contain generated unique ID after previously-built contact "
                        + "contained explicitly set unique ID.",
                contactOne.getUniqueId(), is(not(contactTwo.getUniqueId())));
    }

    @Test
    public void build_UsesUniqueIdAsDisplayName_WhenDisplayNameNotExplicitlySet() {
        final IIndividualContact contact = individualContactBuilderUnderTest.build();

        assertThat("Expected unique ID to equal display name when display name not explicitly set.",
                contact.getUniqueId(), is(contact.getDisplayName()));
    }

    @Test
    public void build_RevertsToUsingUniqueIdAsDisplayName_AfterBuildingAnIndividualContact() {
        individualContactBuilderUnderTest.withDisplayName("Test").build();

        final IIndividualContact contact = individualContactBuilderUnderTest.build();

        assertThat("Expected IndividualContactBuilder to revert to using the unique ID as the display name after "
                        + "building a contact.",
                contact.getUniqueId(), is(contact.getDisplayName()));
    }

    @Test
    public void withUniqueContactId_CorrectlySetsTheUniqueContactId() {
        final IIndividualContact contact = individualContactBuilderUnderTest
                .withUniqueContactId(DEFAULT_UNIQUE_ID)
                .build();

        assertThat("Expected the unique contact ID that was set on GroupContactBuilder to be equal to the unique "
                        + "contact ID returned by the built IGroupContact.",
                DEFAULT_UNIQUE_ID, is(contact.getUniqueId()));
    }

    @Test
    public void withDisplayName_CorrectlySetsTheDisplayName() {
        final String testDisplayName = "Admiral Ackbar";
        final IIndividualContact contact = individualContactBuilderUnderTest.withDisplayName(testDisplayName).build();

        assertThat("Expected the display name that was set on IndividualContactBuilder to be equal to the display name "
                        + "returned by the built IIndividualContact.",
                testDisplayName, is(contact.getDisplayName()));
    }

    @Test(expected = NullPointerException.class)
    public void withDisplayName_ThrowsNullPointerException_WhenGivenNullDisplayName() {
        individualContactBuilderUnderTest.withDisplayName(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void withDisplayName_ThrowsIllegalArgumentException_WhenGivenBlankDisplayName() {
        individualContactBuilderUnderTest.withDisplayName(" ");
    }

    @Test
    public void withAttributes_CorrectlySetsAttributes() {
        final AttributeSet attributes = new AttributeSet();
        final String testAttributeKey = "testKey";
        final int testAttributeValue = 2021;

        attributes.setAttribute(testAttributeKey, testAttributeValue);

        final IIndividualContact contact = individualContactBuilderUnderTest.withAttributes(attributes).build();

        assertThat(
                "Expected attributes set on IndividualContactBuilder to be equal to attributes returned by the built "
                        + "IIndividualContact.",
                attributes, is(contact.getAttributes()));
    }

    @Test(expected = NullPointerException.class)
    public void withAttributes_ThrowsNullPointerException_WhenGivenNullAttributes() {
        individualContactBuilderUnderTest.withAttributes(null);
    }
}


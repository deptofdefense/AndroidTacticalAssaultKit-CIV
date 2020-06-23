package com.atakmap.commoncommo;

/**
 * Identifies a remote Contact accessible by this Commo library.
 * Each Contact is identified uniquely by its UID.
 */
public class Contact implements Comparable<Contact> {
/* SHAWN BISGROVE DESIGN NOTES BELOW:
 * note: groups and teams will still be handled at a higher level at the moment.
 * all of these values are a copy of the native contact.   Since the higher level app will
 * be handling stale out time for most of the functions, the only thing we need to worry 
 * about is the case that a contact is both locally reachable and reachable over TAK.
 *
 * generation of the contact from ATAK/WinTAK will still be handled at a higher level for rev 1
 * this is only in charge of receipt of the contact from the network.
 */

    /**
     * The unique ID for this Contact.  This fixed String will 
     * never change throughout the life of this Contact and
     * uniquely identifies it.
     */
    public final String contactUID;


    Contact(String contactUID)
    {
        this.contactUID = contactUID;
    }


    /*
     * Returns the common name, or "callsign", of this Contact.
     * This is a snapshot of the callsign of this Contact.  
     * Callsigns can change over the life of the Contact.
     * 
     * @return the name of this Contact
     * @throws CommoContactGoneException if this Contact is no longer valid
     */
    // Not currently implemented; slated for version-next
    //public String getName() throws CommoContactGoneException
    //{
    //    return null; 
    //}

    /**
     * Returns the most recently received CoT message 
     * identifying and announcing this Contact. The message is 
     * returned in its full, unadulterated form.
     * 
     * @return CoT message
     * @throws CommoContactGoneException if this Contact is no longer valid
     */
    // Not currently implemented
    //public String getContactEvent() throws CommoContactGoneException
    //{
    //    return null;
    //}

    /**
     * Compares this Contact to another; implemented
     * to ease use in common java.util data structures.
     */
    public int compareTo(Contact other)
    {
        return contactUID.compareTo(other.contactUID);
    }
    
    /**
     * Compares this Contact to another; implemented
     * to ease use in common java.util data structures.
     */
    public boolean equals(Object other)
    {
        if (other == null)
            return false;
        if (!(other instanceof Contact))
            return false;
        Contact c = (Contact)other;
        return contactUID.equals(c.contactUID);
    }
    
    /**
     * Computes hashcode of this Contact; implemented
     * to ease use in common java.util data structures and
     * provide consistency with equals()
     */
    public int hashCode()
    {
        return contactUID.hashCode();
    }
}

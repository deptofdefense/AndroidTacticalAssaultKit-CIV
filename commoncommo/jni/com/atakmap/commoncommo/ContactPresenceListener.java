package com.atakmap.commoncommo;

/**
 * Interface that can be registered with a Commo instance to 
 * be notified in changes to the available Contacts
 */
public interface ContactPresenceListener {

    /**
     * Invoked when a Contact is seen on the network. The contact
     * remains valid until a contactRemoved notification is received.
     * This could be invoked for an entirely new Contact, or for one
     * that was Removed due to a network failure or other event, and
     * then subsequently reappeared.
     * 
     * @param c the Contact newly seen on the network.
     */
    public void contactAdded(Contact c);

    /**
     * Invoked when a Contact is no longer relevant. This could be
     * because the network or server connection by which the Contact
     * was known is no longer available or some other reason.
     * Contacts may once again become relevant and passed to contactAdded
     * if they reappear on the network.
     *  
     * @param c the Contact that is no longer accessible
     */
    public void contactRemoved(Contact c);
    
}

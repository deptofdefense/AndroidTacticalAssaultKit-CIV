#ifndef CONTACTUID_H_
#define CONTACTUID_H_


#include "commoutils.h"
#include <stddef.h>
#include <stdint.h>

namespace atakmap {
namespace commoncommo {

struct COMMONCOMMO_API ContactUID
{
    const size_t contactUIDLen;
    const uint8_t * const contactUID;
    ContactUID(const uint8_t *contactUIDdata, size_t len) :
                contactUIDLen(len), contactUID(contactUIDdata) {
    }
private:
    COMMO_DISALLOW_COPY(ContactUID);
};

struct COMMONCOMMO_API ContactList
{
    size_t nContacts;
    const ContactUID **contacts;
    ContactList() : nContacts(0), contacts(NULL)
    {
    }
    ContactList(const size_t nContacts, const ContactUID **contacts) :
        nContacts(nContacts), contacts(contacts)
    {
    }
private:
    COMMO_DISALLOW_COPY(ContactList);
};


class COMMONCOMMO_API ContactPresenceListener
{
public:
    ContactPresenceListener() {};
    virtual void contactAdded(const ContactUID *c) = 0;
    virtual void contactRemoved(const ContactUID *c) = 0;

protected:
    virtual ~ContactPresenceListener() {};

private:
    COMMO_DISALLOW_COPY(ContactPresenceListener);
};

}
}


#endif /* CONTACT_H_ */

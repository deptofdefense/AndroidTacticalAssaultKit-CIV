
#ifndef IMPL_TAKMESSAGE_H_
#define IMPL_TAKMESSAGE_H_


#include "cotmessage.h"
#include "commologger.h"
#include "internalutils.h"
#include <stdexcept>
#include <string>

namespace atakmap {
namespace commoncommo {
namespace impl
{



// Captures min/max tak protocol versions supported
// for DECODE by a client.
// Versions are always > 0
class TakProtoInfo {
  private:
    unsigned int minVersion;
    unsigned int maxVersion;

  public:
    // Convenience to create for local device support
    TakProtoInfo();    
    TakProtoInfo(unsigned int minV, unsigned int maxV) COMMO_THROW (std::invalid_argument);
    TakProtoInfo(unsigned int maxV) COMMO_THROW (std::invalid_argument);
    
    unsigned int getMin() const;
    unsigned int getMax() const;
    
    bool operator==(const TakProtoInfo &pi) const;
    bool operator!=(const TakProtoInfo &pi) const;

    
    static const unsigned int SELF_MIN = 1;
    static const unsigned int SELF_MAX = 1;
};

class TakMessage
{
public:
    typedef enum {
        HEADER_TAKPROTO,
        HEADER_LENGTH,
        HEADER_NONE
    } HeaderType;

    static const int PROTOINF_BCAST_SEC = 40;
    static const int PROTOINF_TIMEOUT_SEC = 3 * PROTOINF_BCAST_SEC;


    // May pass NULL for msg.
    // May pass NULL for uid if not including proto info
    // Caller retains ownership on msg and uid, which must remain
    // valid for the lifetime of this object
    TakMessage(CommoLogger *logger, CoTMessage *msg, ContactUID *uid,
               bool includeProtoInf = false);
    TakMessage(CommoLogger *logger, const uint8_t *data, size_t len,
               bool tryXml = true, 
               bool tryProto = false) COMMO_THROW (std::invalid_argument);
    ~TakMessage();

    // Serialize the TakMessage to a new byte array using protocol buffers,
    // optionally prepending a header.
    // useCot and useProtoInfconf specify what elements of this TakMessage 
    // are to be serialized
    // out. If the specified element(s) are not present (NULL) in the message
    // data, they are skipped. A size of 0 is returned if neither element
    // ends up in the output; buf is set to NULL and not allocated in this
    // case.
    // The returned array must be delete[]'d by the caller when finished. 
    // Returns the size of the serialized data, including the header if
    // present.
    // Throws invalid_argument for any errors.
    size_t serializeAsProtobuf(int protoVersion, uint8_t **buf, 
                               HeaderType headerType,
                               bool useCot,
                               bool useProtoInf) 
                               const COMMO_THROW (std::invalid_argument);
    
    // NULL if not present in message
    const TakProtoInfo *getProtoInfo() const;

    // NULL if not present in message
    const CoTMessage *getCoTMessage() const;

    // Releases internal cot message (if any) to caller.
    // NULL if not present in message
    CoTMessage *releaseCoTMessage();
    
    // If we enclose a cot message, get it's contact uid
    // else, if we enclose protoinfo, get it's contact uid
    // else, return NULL
    // Returned pointer is only valid as long as this tak message
    // and the contained cot message is 
    const ContactUID *getContactUID() const;
    
    // 0 if not created via deserialization
    unsigned int getProtoVersion() const;

    // Check supplied version for support in serializeAsProtobuf().
    // If not supported, return 0. Else return the supplied number
    static int checkProtoVersion(int protoVersion);

private:
    COMMO_DISALLOW_COPY(TakMessage);
    void initFromXml(const uint8_t *data, size_t len) 
                                                COMMO_THROW (std::invalid_argument);
    void initFromProtobuf(const uint8_t *data, size_t len)
                                                COMMO_THROW (std::invalid_argument);

    CommoLogger *logger;
    
    // Each of these may be NULL
    TakProtoInfo *protoInfo;
    CoTMessage *cot;
    CoTMessage *ownedCot;
    ContactUID *contactUid;
    InternalContactUID *ownedUid;
    unsigned int protoVersion;
};

}
}
}


#endif

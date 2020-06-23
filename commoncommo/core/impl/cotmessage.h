
#ifndef IMPL_COTMESSAGE_H_
#define IMPL_COTMESSAGE_H_


#include "internalutils.h"
#include "cotmessageio.h"
#include "contactuid.h"
#include "commologger.h"
#include <stdexcept>
#include <vector>
#include <set>
#include <string>

namespace atakmap {
namespace commoncommo {

// Forward declare
namespace protobuf {
namespace v1 {
    class CotEvent;
}
}

namespace impl
{

struct CoTMessageImpl;


typedef enum {
    ENDPOINT_UDP_USESRC,
    ENDPOINT_UDP,
    ENDPOINT_TCP_USESRC,
    ENDPOINT_TCP,
    ENDPOINT_STREAMING,
    ENDPOINT_NONE
} EndpointType;

typedef enum {
    // indices used by impl - do not change without reviewing impl
    TYPE_SUPPORT = 0,
    TYPE_REQUEST,
    TYPE_RESPONSE,
    TYPE_NONE
} TakControlType;

class CoTFileTransferRequest {
public:
    // All args are copied during construction
    CoTFileTransferRequest(const std::string &sha256hash,
                           const std::string &name,
                           const std::string &senderFilename,
                           const std::string &senderUrl,
                           const uint64_t sizeInBytes,
                           const std::string &senderCallsign,
                           const ContactUID *senderuid,
                           const std::string &ackuid);
    CoTFileTransferRequest(const CoTFileTransferRequest &src);
    ~CoTFileTransferRequest();

    // hash of the file to be transferred
    const std::string sha256hash;

    // name of the transfer
    const std::string name;

    // the sender's indication of the file name on its end
    const std::string senderFilename;

    // the url at which the file may be obtained
    const std::string senderUrl;

    // the size in bytes of the file
    const uint64_t sizeInBytes;

    // Callsign of the sending contact
    const std::string senderCallsign;

    // UID of the sending contact
    const ContactUID *senderuid;

    // uid to be used for any ack message
    // This may be the empty string, in which case no ack is requested
    const std::string ackuid;
};


class CoTMessage
{
private:
    static const std::string STREAMING_ENDPOINT;
    static const std::string TCP_USESRC_ENDPOINT;
    static const std::string UDP_USESRC_ENDPOINT;

public:
    // Init a new ping request CoTMessage
    CoTMessage(CommoLogger *logger, const std::string &uid);
    
    // Init a new TakControl/TakResponse message indicating
    // desire to use the specified version of TAK protocol
    CoTMessage(CommoLogger *logger, const std::string &uid,
               int version);

    // Init a new file transfer request CoTMessage
    CoTMessage(CommoLogger *logger, 
               const std::string &uid, const CoTPointData &point,
               const CoTFileTransferRequest &fileTransferRequest);

    // Init a new file transfer ack CoTMessage for the specified transfer.
    // The message should have an indication of the failure if the
    // transfer failed, else it can contain some description
    CoTMessage(CommoLogger *logger, 
               const std::string &uid, const CoTPointData &point,
               const CoTFileTransferRequest &fileTransferRequest,
               const ContactUID *receiveruid,
               const bool failed,
               const std::string &message);

    // Init a new CoTMessage by deserializing xml from the supplied buffer
    // Throws if there is a formatting error in the supplied serialized form
    CoTMessage(CommoLogger *logger, const uint8_t *data, size_t len)
               COMMO_THROW (std::invalid_argument);
    // Init a new CoTMessage by deserializing protobuf data from the given
    // CotEvent. Throws if there is an error in one or more of the values
    CoTMessage(CommoLogger *logger, const protobuf::v1::CotEvent &event)
               COMMO_THROW (std::invalid_argument);
    CoTMessage(const CoTMessage &src) COMMO_THROW (std::invalid_argument);
    ~CoTMessage();

    // Re-init this CoTMessage by deserializing from the supplied buffer;
    // basically the same as the constructor of similar args except without
    // creation of a new CoTMessage object.
    // On success all old state is lost and rebuilt from the deserialized
    // data. On any failure the state is kept precisely the same as before
    // the call was made.
    // Throws if there is a formatting error in the supplied serialized form.
    void reinitFrom(const uint8_t *data, const size_t len) COMMO_THROW (std::invalid_argument);

    // Same as reinitFrom() but for protobuf data
    void reinitFromProtobuf(const protobuf::v1::CotEvent &event) COMMO_THROW (std::invalid_argument);

    // Serialize the CoTMessage to a new byte array.  The returned array must
    // be delete[]'d by the caller when finished. A null terminator is added for
    // convenience.  Returns the size of the serialized data without the
    // null terminator.
    // Throws invalid_argument for any errors.
    size_t serialize(uint8_t **buf, bool prettyFormat = false) const COMMO_THROW (std::invalid_argument);

    // Serialize the CoTMessage to the given protocol buffer object, which
    // is assumed to be in a default state at invocation.
    // Throws invalid_argument for any errors.
    // If an error is thrown, the event is likely to be in an indeterminate
    // state and should be discarded.
    void serializeAsProtobuf(protobuf::v1::CotEvent *event) const COMMO_THROW (std::invalid_argument);

    // Gets the uid string from the event
    std::string getEventUid() const;

    // "endpoints" are simply stringified network addresses of the sender.
    // Endpoint host includes only the host - it does not include anything else
    // (port #'s, protocol info), just IP or host.
    // May return an empty string if there is no endpoint given in the
    // message
    std::string getEndpointHost() const;

    // Gets the port number encoded in the endpoint. If there was no
    // valid endpoint port, -1 is returned. -1 also returned for stream
    // type endpoints
    int getEndpointPort() const;

    // Return the type of endpoint in the message.  Gives NONE if
    // no endpoint given or the endpoint is invalid
    EndpointType getEndpointType() const;

    // This will write the unicast source endpoint using the specified
    // type of endpoint. For the endpoint string, specify only the IP
    // information (no port or transport).
    // The ipAddr is ignored for ENDPOINT_UDP_USESRC, ENDPOINT_TCP_USESRC,
    // ENDPOINT_STREAMING, and ENDPOINT_NONE types.
    // Use ENDPOINT_NONE to clear the endpoint of the message.
    // Use ENDPOINT_STREAMING to set for a TAK server endpoint.
    // Use ENDPOINT_TCP_USESRC to set for tcp replies via packet source address.
    // Use ENDPOINT_UDP_USESRC to set for tcp replies via packet source address.
    // For the types which need ipAddr, if ipAddr is empty string
    // this behaves as though ENDPOINT_NONE were passed instead.
    // Has no effect at all if this CoTMessage has no contact node and no
    // file transfer ack request node.
    void setEndpoint(EndpointType type, const std::string &ipAddr);

    // Gets the callsign of the sender of this message. May be the empty string.
    std::string getCallsign() const;

    // Gets the type of the message
    CoTMessageType getType() const;

    // Gets the type of control message represented by this message    
    TakControlType getTakControlType() const;
    
    // If this is a TYPE_SUPPORT message, returns a set of TAK protocol
    // versions advertised as being supported in the message.  If not
    // a TYPE_SUPPORT message, returns empty set.
    std::set<int> getTakControlSupportedVersions() const;
    
    // If this is a TYPE_RESPONSE message, returns the status of the response
    bool getTakControlResponseStatus() const;
    
    // True if this message is a "pong" message
    bool isPong() const;

    // Gets file transfer request details if this message is a request
    // for file transfer, else returns NULL.
    // The returned pointer is valid  until this CoTMessage is reinitialized
    // or destroyed
    const CoTFileTransferRequest *getFileTransferRequest() const;

    // Gets the uid of the sender of the file transfer that this CoTMessage
    // is acknowledging if this message is a file transfer (n)ack.
    // If this is not a file transfer acknowledgement, the returned
    // value will be the empty string.
    std::string getFileTransferAckSenderUid() const;

    // Gets the uid of the file transfer that this CoTMessage is acknowledging
    // if this message is a file transfer (n)ack. Use
    // getFileTransferSucceeded() to determine if this message is an
    // ack or a nack.
    // If this is not a file transfer acknowledgement, the returned
    // value will be the empty string.
    std::string getFileTransferAckUid() const;

    // Returns the file size stated in the acknowledgement of
    // a file transfer.  Returns 0 if this message is not an ack
    // of a file transfer (see getFileTransferAckSenderUid() or 
    // if the value in the ack is not valid
    uint64_t getFileTransferAckSize() const;

    // Returns true if this CoTMessage represents a file transfer
    // acknowledgement (getFileTransferAckUid() returns non-empty result)
    // and this acknowledgement indicates the transfer was successful.
    // Otherwise, false is returned to indicate the transfer failed (or
    // that this CoTMessage does not represent a file transfer acknowledgement)
    bool getFileTransferSucceeded() const;

    // Obtains the reason a file transfer has failed or an ancillary message
    // if a file transfer has succeeded, according to this
    // response from the receiver.  Only meaningful if this CoTMessage
    // represents a file transfer acknowledgement (getFileTransferAckUid()
    // returns a non-empty result).
    std::string getFileTransferReason() const;

    // Gets a ContactUID for the sender of this message.  The returned object
    // is only valid until this CoTMessage is reinitialized (reinitFrom) or
    // destroyed.
    // Returns NULL if this message is *not* a contact-info-containing
    // SA message.
    const ContactUID *getContactUID() const;

    // Add or replace TAK server destination information.
    // Any existing TAK server destination information is always removed.
    // empty vector will result in the message going to "all tak server"
    // recipients.
    // NULL will result in removing any TAK server destination information.
    void setTAKServerRecipients(const std::vector<std::string> *recipients);

    // Add or replace TAK server destination information with
    // a single mission recipient.
    void setTAKServerMissionRecipient(const std::string &mission);
    
    CommoLogger *getLogger() const;

private:
    CoTMessageImpl *internalState;
    CommoLogger *logger;


    std::string endpointAsString() const;
};

}
}
}


#endif

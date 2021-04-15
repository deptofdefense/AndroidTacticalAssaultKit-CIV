*** Traditional Protocol - "Protocol Version 0"

Clients send and receive XML CoT <event> messages.
"Mesh" network participants announce via "SA" messages via UDP datagrams
over multicast to a well known address and port.  Each UDP datagram contains
one (and only one) CoT XML message as its payload.

Messages directed only to specific network participants are send by making
TCP connection to the remote recipient, sending the CoT XML, then closing
the connection.


Streaming connections (to TAK servers) send the same XML-based CoT payloads
over TCP sockets.  The TCP stream is comprised of one CoT <event> after
another.  Messages are delimited and broken apart by searching for the token
"</event>" and breaking apart immediately after that token.
When sending, messages must be prefaced by XML header (<?xml ... ?>),
followed by a newline, followed by the complete XML <event>.  TAK servers
require that no arbitrary newlines follow the </event> end of message and
that the next character immediate commences the next <?xml ... ?> header.



*** TAK Protocol - Design Goals

The goal of the new TAK Protocol design is to allow interoperation with
other legacy clients and TAK server, as well as to strongly identify 
what rendition of communication will be used in a session.  This is to allow
for future expansion or complete revision of the protocol while allowing
an opportunity to support mixed client versions (and varying versions of TAK
servers).


*** TAK Protocol - Ground Rules

All clients obey the following basic rules regardless of the version(s)
of TAK protocol that they support.  These rules are important base rules
upon which the protocol version negotiations detailed in subsequent sections
rely:

1. A client sending TAK protocol version "V" is also capable of receiving
   and decoding version "V"
2. All clients must support decoding TAK protocol version "0" (legacy XML)


*** TAK Protocol - Generic Framework - Mesh Networks

Mesh networks broadcasts (SA announces, etc) will reuse the existing UDP
datagram-based networking already in place.   Directed (unicasted) TCP
messages will reuse the existing connect, send 1 message, disconnect
networking.

For both TCP and UDP, instead of sending CoT as XML, clients will send data
packets whose payloads contain one message complying with the new "TAK
Protocol".
Both types of messages will utilize a data payload that begins with the "TAK
Protocol Header" followed by the "TAK Protocol Payload".  The header
serves to self-identify as a TAK Protocol message, as well as indicate a
particular version number to which the subsequent Payload comforms.

TAK Protocol Message: <TAK Protocol Header> <TAK Protocol Payload>


The "TAK Protocol Header" is nothing more than a set of "magic numbers" to
identify the message header as such, and a version identifier to indicate
what TAK Protocol version the remainder of the payload is comprised of.

TAK Protocol Header: <magic byte> <tak protocol version> <magic byte>
Where....
<magic byte> is the single byte 0xbf
<tak protocol version> is the version number of the TAK Protocol the payload
in the remainder of the message conforms to.  This is encoded as a "varint".
See "TAK Protocol Varint Encoding".



*** TAK Protocol - Generic Framework - Streaming Connections

Steaming connections (TAK server connections) use a different message style
as the repeating protocol version information in every message that is done
in mesh TAK Protocol Messages would be a waste of resources in the streaming
environment (since all messages will use the same Version).
The TAK Protocol Stream Message is instead defined to provide the length of
the streaming message (necessary to break apart the message from its
neighbors to avoid need to scan for special tokens).

In a streaming connection, "TAK Protocol Streaming Messages" are sent one
after another (with no intervening data) over the streaming connection.

TAK Protocol Stream Message: 
<TAK Protocol Streaming Header> <TAK Protocol Payload>

Important to note here is that the "TAK Protocol Payload" is precisely the
same in form and content to that which is used for mesh network messages for
a given protocol version.



The "TAK Protocol Streaming Header" is as follows:

TAK Protocol Streaming Header:
<magic byte> <message length>
Where...
<magic byte> is the single byte 0xbf
<message length> is the number of bytes in the "TAK Protocol Payload" which
                 follows the header. This is encoded as a "varint".

As mentioned prior, the version identification for the message's payload
format is omitted from the streaming header.  Protocol version negotiation
is expected to occur outside of core TAK Protocol message exchange.
See "Streaming Connection Protocol Negotiation".




*** TAK Protocol Payload - Version 1

Version 1 of the TAK Protocol Payload is a Google Protocol Buffer based
payload.  Each Payload consists of one (and only one)
atakmap::commoncommo::v1::TakMessage message which is serialized using
Google protocol buffers version 3.

See the .proto files for more information on the specific messages and their
fields, as well as the mapping to/from CoT XML.

Revising the messages used by Version 1 may be done in accordance with the
following rules:

1. Additional message fields MAY be added to the end of existing messages
   following normal google protobuf rules if and only if
   ignorance of the new fields on decoding is 100% irrelevant to correct
   semantic operation at the TAK application level of ALL TAK applications.
2. Otherwise, any and all changes must be tied to a protocol version change.


This version of TAK Protocol does not define any additional attributes to be
used during Streaming Connection Protocol Negotiation.



*** Streaming Connection Protocol Negotiation

TAK clients often connect to a variety of TAK servers, each of which may be
a different version of software capable of different versions of the TAK
Protocol (or indeed not capable of the TAK Protocol and simply only
supporting traditional streaming CoT as XML).

Because of the desire to allow operation of various client and server
versions, and the desire to keep the traditional XML encoding available, the
following negotiation is performed when connecting to a TAK server with a
client that supports the TAK Protocol.

1. Once the connection is established, client and server should expect to
   exchange traditional CoT XML messages per "Traditional Protocol" section.
   Note, however, if the server requires authentication, the auth XML message
   MUST be the first message sent to the TAK server by the client.
   Even if awaiting auth, the server MAY send CoT XML.  Upon supplying an
   auth message (when required), one of two things happens:
  1a. If the server accepts the auth, proceed to 2.
  1b. If the server denies the auth, the connection is closed.
2. Client and server continue to expect to exchange traditional 
   CoT XML messages per "Traditional Protocol" section.
3. A server which supports the TAK Protocol MAY send the following CoT XML
   message to indicate this support (whitespace added, xml header omitted):
<event version='2.0' uid='protouid' type='t-x-takp-v' time='TIME' start='TIME' stale='TIME' how='m-g'>
  <point lat='0.0' lon='0.0' hae='0.0' ce='999999' le='999999'/>
  <detail>
    <TakControl>
      <TakProtocolSupport version="1"/>
    </TakControl>
  </detail>
</event>

   ... where the version attribute is an integer number specifying
   a version of the TAK Protocol the server supports.  This message
   may contain one or more TakProtocolSupport elements inside the single
   <TakControl> detail, each specifying a supported version.
   The TAK server MUST send this message no more than once per connection.

   To allow for ancillary information in the negotiation, the
   TakProtocolSupport element MAY contain additional attributes compliant
   with the Protocol version indicated.
4. Client and server continue to expect to exchange traditional CoT XML
   messages per "Traditional Protocol" section.
5. If the client wishes to initiate a transfer to TAK Protocol encoding, it
   selects one of the supported versions advertised in the server's message
   from step 3.  It then sends the following CoT XML:
<event version='2.0' uid='protouid' type='t-x-takp-q' time='TIME' start='TIME' stale='TIME' how='m-g'>
  <point lat='0.0' lon='0.0' hae='0.0' ce='999999' le='999999'/>
  <detail>
    <TakControl>
      <TakRequest version="1"/>
    </TakControl>
  </detail>
</event>

   ... where the version attribute is the integer version chosen above.
   Only ONE TakRequest element is allowed.

   To allow for ancillary information in the negotiation, the
   TakRequest element MAY contain additional attributes compliant
   with the Protocol version indicated.

   Clients SHALL NOT send this message unless they have observed the
   message from step #3, above, first.
   Server MUST examine all receive CoT events for this message from the point
   in time when the message in #3 is sent until at least one minute following
   that point in time.  If a "false" status is subsequently issued per step #6
   below step, this time limit SHALL be extended to at least one minute
   from the point in time the failure response message specified in 
   #6 is issued to allow the client additional time to retry.

6. Once the client sends the message in #5, it MUST NOT send additional
   CoT XML to the server.  Client also MUST still process incoming CoT XML
   from the server.  The client MUST wait in this state for a response per
   the following for at least one minute.  
   The server MAY still send CoT XML messages up until it notices the
   control request from the client (from step #5) and is ready to respond.
   The server MUST then respond as soon as possible to the client with the
   following message to indicate either acceptance or denial of the request:
<event version='2.0' uid='protouid' type='t-x-takp-r' time='TIME' start='TIME' stale='TIME' how='m-g'>
  <point lat='0.0' lon='0.0' hae='0.0' ce='999999' le='999999'/>
  <detail>
    <TakControl>
      <TakResponse status="true"/>
    </TakControl>
  </detail>
</event>
   
   ... where the status attribute is either true (to indicate the server
   accepts the requested version) or false (to indicate that the server
   denies the request).
   Only ONE TakResponse element is allowed.

   To allow for ancillary information in the negotiation, the
   TakResponse element MAY contain additional attributes compliant
   with the Protocol version selected in the request that this response
   applies to.

   If no response is received by the client before its timeout elapses, 
   the client SHALL disconnect as the entire negotiation is in an
   indeterminate state. The client SHOULD reconnect and begin again at step
   1, possibly with a longer timeout or alternate protocol version choice.

7. Operation at this point depends on the response send in #6:
7a. If status was true: The server MUST NOT send additiona CoT XML after the
    "true" response in #6.  Instead, the server SHALL send all future data
    in accordance with the TAK Protocol Streaming Connection framework
    and containing TAK Payloads of the negotiated version.
    The client MAY resume sending messages at this time but MUST immediately
    send said messages in accordance with the TAK Protocol Streaming
    Connection framework and containing TAK Payloads of the negotiated
    version.  NOTE: the negotiated version SHALL be the same for both
    directions of the streaming connection!
7b. If status was false: Both client and server resume operation as though
    they were back at step #4.  The client may attempt a new negotiation
    if it wishes, or may simply continue to exchange traditional XML-based
    CoT messages.

In the messages in 3, 5, and 6 above, the following common rules apply:
a. "protouid" is any valid UID representing the negotiation transaction.
   The server generates this when offering protocol versions.  The client
   re-uses it when placing request(s) and the server re-uses it when
   issuing the response to a request.
   The UID SHALL be unique from UIDs used for other messages and purposes.
b. "TIME" is filled with a valid time representation per the CoT schemas.
   The TIME values may be different from each other as needed.


*** "Mesh Network" Protocol Negotiation

Mesh networking in TAK products relies on repeated broadcasting of
device presence and "SA" data that gives basic information on how to reach
local network participants.  To allow for clients with mixed TAK protocol
versions (as well as legacy XML only capabilities), the following protocol
selection and support advertisement shall be performed on each device:

1. All devices supporting TAK Protocol versions > 0 (legacy xml) MUST
   broadcast to all configured and active non-TAK server broadcast destinations
   the TakControl message in a TakMessage at least once every 60 seconds.
   This information MAY be sent alongside CotEvent data or standalone.
   This message indicates the minimum and maximum versions of TAK protocol
   that the device can **decode**.
   Note that devices not supporting TAK protocol > 0 will not be sending these
   messages.
   It is RECOMMENDED that devices do *not* frequently change the
   version information in these messages as receivers may optimize around
   the information being mostly static/fixed.
   This information SHALL be sent using the protocol level
   determined under the rule in 4 except when rule 4 results in 
   protocol level 0, in which case TakControl information
   SHALL be sent using the lowest protocol version > 0 supported by the
   sender.
2. Each device MUST examine and decode the TakControl message in any message
   it receives and knows how to decode.  If for a version that the device
   does not support, it MAY discard the message.
3. Each device MUST maintain the minimum and maximum supported TAK protocol
   versions known from every client known to exist on the network based on
   the following ruleset:
   3a. Newly detected clients are assigned a min/max supported version
       equal to the version used to relay the message that resulted
       in discovery of the client. Note that this could be version 0
       (legacy XML)
   3b. Upon receipt of a TakControl message, the min/max version info
       is updated to match the information in the message.  Optimizing
       for infrequent changes of this info is recommended. 
       Note that TakControl messages do NOT allow versions of 0 in them.
       Support for version 0 is implied (see base rules) and need not be
       tracked except for those clients which support *only* version 0.
   3c. Known clients that have not sent any TakControl messages in the previous
       2 minutes shall revert to a minimum and maximum version equal
       to the version used in the most recently received message that
       keeps the client from becoming entirely stale. Note that this could
       be version 0 (legacy XML).
   3d. Received messages that are not decodable by the receiver should
       continue to be treated as not having received TakControl messages
       under 3c.
4. Devices MUST send out broadcast messages using the highest protocol version
   supported by *all* known contacts (including consideration of the
   sending device itself) tracked based on the rules in (3) at the time
   of sending.
   This includes SA announcements/broadcasts.
   If there is no version overlap suitable for all versions, then protocol
   "version 0" must be used.
   If this is "version 0" (legacy xml), then XML shall be used.
5. Whenever the version computed via rule 5 changes, clients SHALL immediately
   send out a TakControl message using the new version per rule 1.
   This must be done even if not otherwise broadcasting a message.



*** TAK Protocol Varint Encoding

The varints used in the headers of the TAK Protocol are encoded in
accordance with the UNSIGNED varint rules for Google protocol buffers. 
This encoding is summarized here:

1. The value must be UNSIGNED.  Only values equal to or greater than zero 
   are allowed.
2. The value to be encoded is taken 7 bits at a time, starting with the
   least significant 7 bits (bits 7 -> 0), then the next least significant bits
   (14 -> 8), etc.  This repeats over all 7 bit values that are significant
   (that is, up to and including the most significant '1' bit).
3. For each 7 bit group:
  3a. Let S = 0 if this is the the last 7 bit group, else let S = 1
  3b. Output a byte that is (S << 7) | (the 7 bits)

The TAK Protocol use of Varints limits use to 64-bit values.  This
effectively limits the range as [ 0, (2^63 - 1) ] and the varint coded value
to be limited to 10 bytes.



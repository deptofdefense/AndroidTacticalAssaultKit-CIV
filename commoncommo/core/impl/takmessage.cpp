#include "takmessage.h"
#include "internalutils.h"

#include "protobuf.h"


using namespace atakmap::commoncommo;
using namespace atakmap::commoncommo::impl;

namespace {
    // The singular version we currently support
    const unsigned TAKPROTO_VERSION = 1;

    const uint8_t TAKPROTO_MAGIC = 0xbf;
}


TakProtoInfo::TakProtoInfo(unsigned int minV, unsigned int maxV)
                           COMMO_THROW (std::invalid_argument) : 
                                                 minVersion(minV),
                                                 maxVersion(maxV)
{
    if (minV > maxV || minV < 1)
        throw std::invalid_argument("Invalid version specified");
}

TakProtoInfo::TakProtoInfo(unsigned int maxV) 
                           COMMO_THROW (std::invalid_argument) : 
                                                minVersion(SELF_MIN),
                                                maxVersion(maxV)
{
    if (maxV < 1)
        throw std::invalid_argument("Invalid version specified");
}

TakProtoInfo::TakProtoInfo() : minVersion(SELF_MIN), 
                               maxVersion(SELF_MAX)
{
}

unsigned int TakProtoInfo::getMin() const
{
    return minVersion;
}

unsigned int TakProtoInfo::getMax() const
{
    return maxVersion;
}

bool TakProtoInfo::operator ==(const TakProtoInfo &pi) const
{
    return minVersion == pi.minVersion &&
           maxVersion == pi.maxVersion;
}

bool TakProtoInfo::operator !=(const TakProtoInfo &pi) const
{
    return !(*this == pi);
}

    
// 0 if not TAK proto header or not a version we expect.
// >0 means it is and we went through that many bytes to parse it
// XXX - change to return back version once we support more than 1 version!
size_t isTAKProtoHeader(const uint8_t *d, size_t len)
{
    if (len < 1 || d[0] != TAKPROTO_MAGIC)
        return 0;
    
    d++;
    len--;
    
    uint64_t v;
    size_t i = len;

    try {
        v = InternalUtils::varintDecode(d, &i);
        len -= i;
    } catch (std::invalid_argument &) {
        return 0;
    }

    if (len < 1 || v < TakProtoInfo::SELF_MIN || 
                   v > TakProtoInfo::SELF_MAX || d[i] != TAKPROTO_MAGIC)
        return 0;
    
    return i + 2;
}


TakMessage::TakMessage(CommoLogger *logger, CoTMessage *cotMsg,
                       ContactUID *sendingUid,
                       bool includeVersion) : logger(logger), 
                                  protoInfo(NULL), cot(cotMsg),
                                  ownedCot(NULL), contactUid(sendingUid),
                                  ownedUid(NULL), protoVersion(0)
{
    if (includeVersion)
        protoInfo = new TakProtoInfo();
}

TakMessage::TakMessage(CommoLogger *logger, const uint8_t *data, size_t len,
           bool tryXml, bool tryProto) COMMO_THROW (std::invalid_argument) :
                                  logger(logger), protoInfo(NULL),
                                  cot(NULL), ownedCot(NULL),
                                  contactUid(NULL), ownedUid(NULL),
                                  protoVersion(0)
{
    if (tryXml && tryProto) {
        size_t hdrSkip = isTAKProtoHeader(data, len);
        if (hdrSkip != 0) {
            data += hdrSkip;
            len -= hdrSkip;
            tryXml = false;
            protoVersion = TAKPROTO_VERSION;
        } else {
            tryProto = false;
            protoVersion = 0;
        }
    }
    if (tryXml)
        initFromXml(data, len);
    else if (tryProto)
        initFromProtobuf(data, len);
    else
        throw std::invalid_argument("No decode mode given");
}

TakMessage::~TakMessage()
{
    delete protoInfo;
    delete ownedCot;
    delete ownedUid;
}

size_t TakMessage::serializeAsProtobuf(int protoVersion,
                                       uint8_t **buf, 
                                       HeaderType headerType,
                                       bool useCot,
                                       bool useProtoInf) const 
                                 COMMO_THROW (std::invalid_argument)
{
    if (protoVersion != TAKPROTO_VERSION)
        throw std::invalid_argument("Protocol version not supported");

    protobuf::v1::TakMessage takm;
    if (!protoInfo)
        useProtoInf = false;
    if (!cot)
        useCot = false;
    
    if (!useCot && !useProtoInf) {
        *buf = NULL;
        return 0;
    }
    
    if (useProtoInf) {
        protobuf::v1::TakControl *c = takm.mutable_takcontrol();
        // Don't send 1's 
        // XXXXXXXXX - verify that both 1's still sends a message!
        if (protoInfo->getMin() != 1)
            c->set_minprotoversion(protoInfo->getMin());
        if (protoInfo->getMax() != 1)
            c->set_minprotoversion(protoInfo->getMax());
        if (!useCot || !cot->getContactUID()) {
            if (!contactUid)
                throw std::invalid_argument("Uid not provided");
            
            c->set_contactuid(std::string((const char *)contactUid->contactUID,
                                          contactUid->contactUIDLen));
        }
    }
    
    if (useCot) {
        protobuf::v1::CotEvent *ev = takm.mutable_cotevent();
        cot->serializeAsProtobuf(ev);
    }

    size_t len = takm.ByteSizeLong();
    size_t headerlen = InternalUtils::VARINT_MAXBUF + 2;
    uint8_t headerBuf[InternalUtils::VARINT_MAXBUF + 2];
    
    switch (headerType) {
      case HEADER_TAKPROTO:
      {
        headerBuf[0] = TAKPROTO_MAGIC;
        size_t n = InternalUtils::varintEncode(headerBuf + 1, headerlen - 1,
                                               TAKPROTO_VERSION);
        headerBuf[1 + n] = TAKPROTO_MAGIC;
        headerlen = n + 2;
        break;
      }
      case HEADER_LENGTH:
      {
        headerBuf[0] = TAKPROTO_MAGIC;
        size_t n = InternalUtils::varintEncode(headerBuf + 1, headerlen - 1,
                                               len);
        headerlen = n + 1;
        break;
      }
      case HEADER_NONE:
        headerlen = 0;
        break;
    }
    
    uint8_t *ret = new uint8_t[len + headerlen];
    memcpy(ret, headerBuf, headerlen);
    if (len > INT_MAX || !takm.SerializeToArray(ret + headerlen, (int)len)) {
        delete[] ret;
        InternalUtils::logprintf(logger, CommoLogger::LEVEL_ERROR, 
                "Severe error serializing protobuf to output bytes");
        throw std::invalid_argument("Error serializing cot-based protobuf to output array");
    }

    *buf = ret;
    return len + headerlen;
}

const TakProtoInfo *TakMessage::getProtoInfo() const
{
    return protoInfo;
}

const CoTMessage *TakMessage::getCoTMessage() const
{
    return cot;
}

const ContactUID *TakMessage::getContactUID() const
{
    if (cot && cot->getContactUID())
        return cot->getContactUID();
    else
        return contactUid;
}

unsigned int TakMessage::getProtoVersion() const
{
    return protoVersion;
}


CoTMessage *TakMessage::releaseCoTMessage()
{
    CoTMessage *ret = cot;
    cot = NULL;
    ownedCot = NULL;
    return ret;
}

void TakMessage::initFromXml(const uint8_t *data, size_t len)
                        COMMO_THROW (std::invalid_argument)
{
    ownedCot = new CoTMessage(logger, data, len);
    cot = ownedCot;
}

void TakMessage::initFromProtobuf(const uint8_t *data, size_t len)
                        COMMO_THROW (std::invalid_argument)
{
    protobuf::v1::TakMessage takm;
    
    if (len > INT_MAX || !takm.ParseFromArray(data, (int)len))
        throw std::invalid_argument("Failed to parse protobuf from supplied data");

    if (takm.has_cotevent()) {
        const protobuf::v1::CotEvent &ev = takm.cotevent();
        ownedCot = new CoTMessage(logger, ev);
        cot = ownedCot;
    }
    
    if (takm.has_takcontrol()) {
        const protobuf::v1::TakControl &tc = takm.takcontrol();
        int min = tc.minprotoversion();
        int max = tc.maxprotoversion();
        if (min == 0) min = 1;
        if (max == 0) max = 1;
        try {
            protoInfo = new TakProtoInfo(min, max);
        } catch (std::invalid_argument &e) {
            delete cot;
            cot = ownedCot = NULL;
            throw e;
        }
        std::string cuid = tc.contactuid();
        if (cuid.length()) {
            ownedUid = new InternalContactUID((const uint8_t *)cuid.c_str(), 
                                              cuid.length());
            contactUid = ownedUid;
        }
    }
}

int TakMessage::checkProtoVersion(int protoVersion)
{
    if (protoVersion < 0 ||
            (unsigned)protoVersion < TakProtoInfo::SELF_MIN || 
            (unsigned)protoVersion > TakProtoInfo::SELF_MAX)
        return 0;
    return protoVersion;
}







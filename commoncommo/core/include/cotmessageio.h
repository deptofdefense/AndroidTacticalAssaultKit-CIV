#ifndef COTMESSAGEIO_H_
#define COTMESSAGEIO_H_

#include "commoutils.h"
#include <stddef.h>
#include <stdint.h>

namespace atakmap {
namespace commoncommo {


enum CoTSendMethod {
    SEND_TAK_SERVER = 0x1,
    SEND_POINT_TO_POINT = 0x2,
    SEND_ANY = 0x3
};

struct COMMONCOMMO_API CoTPointData {
    // Can be used for hae, ce, or le
#define COMMO_COT_POINT_NO_VALUE 9999999.0

    CoTPointData(double lat, double lon, double hae,
                 double ce, double le) : lat(lat), lon(lon), hae(hae), ce(ce), le(le)
    {
    };

    double lat;
    double lon;
    double hae;
    double ce;
    double le;
};


enum CoTMessageType {
    SITUATIONAL_AWARENESS,
    CHAT,
};


class COMMONCOMMO_API CoTMessageListener
{
public:
    CoTMessageListener() {};
    virtual void cotMessageReceived(const char *cotMessage, const char *rxIfaceEndpointId) = 0;

protected:
    virtual ~CoTMessageListener() {};

private:
    COMMO_DISALLOW_COPY(CoTMessageListener);
};


class COMMONCOMMO_API GenericDataListener
{
public:
    GenericDataListener() {};
    virtual void genericDataReceived(const uint8_t *data,
                                     size_t length,
                                     const char *rxIfaceEndpointId) = 0;

protected:
    virtual ~GenericDataListener() {};

private:
    COMMO_DISALLOW_COPY(GenericDataListener);
};


class COMMONCOMMO_API CoTSendFailureListener
{
public:
    virtual void sendCoTFailure(const char *host, int port, const char *errorReason) = 0;

protected:
    CoTSendFailureListener() {};
    virtual ~CoTSendFailureListener() {};
    
private:
    COMMO_DISALLOW_COPY(CoTSendFailureListener);
};


}
}


#endif /* COTMESSAGE_H_ */

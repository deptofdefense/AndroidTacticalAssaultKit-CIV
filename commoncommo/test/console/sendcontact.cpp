#include <unistd.h>
#include <string.h>
#include <sys/types.h>
#include <sys/socket.h>
#include <arpa/inet.h>
#include <fcntl.h>
#include <errno.h>

#include <sys/ioctl.h>
#include <netinet/in.h>
#include <net/if.h>
#include <net/if_arp.h>
#include <stdio.h>
#include <string>        
        

int main(int argc, char *argv[])
{

    int fd = socket(AF_INET, SOCK_DGRAM, IPPROTO_UDP);
    
    struct in_addr addr;
    struct sockaddr_in sockaddr;
    struct sockaddr_in mcastaddr_port2;
    
    if (inet_pton(AF_INET, "192.168.167.162", &addr) != 1) {
        printf("A1 fial\n");
        return 1;
    }

    memset(&sockaddr, 0, sizeof(struct sockaddr_in));
    sockaddr.sin_addr = addr;
    sockaddr.sin_family = AF_INET;
    sockaddr.sin_port = htons(9988);
    
#if 0
    memset(&mcastaddr_port2, 0, sizeof(struct sockaddr_in));
    mcastaddr_port2.sin_addr.s_addr = INADDR_ANY;
    mcastaddr_port2.sin_family = AF_INET;
    mcastaddr_port2.sin_port = htons(7000);
    
    if ( bind(fd, (struct sockaddr *)&mcastaddr_port2, sizeof(struct sockaddr_in)) != 0) {
        printf("FAIL\n");
        return 1;
    }
#endif


    std::string myCallsign = "fakecontactcs";
    std::string myUID = "fakecontactuid";



    time_t now = time(NULL);
    struct tm t;
#ifdef WIN32
    gmtime_s(&t, &now);
#else
    gmtime_r(&now, &t);
#endif
    const size_t timebufsize = 256;
    char timebuf[timebufsize];
    char timebuf2[timebufsize];
    strftime(timebuf, timebufsize, "%Y-%m-%dT%H:%M:%S.000Z", &t);
    now += 120;
#ifdef WIN32
    gmtime_s(&t, &now);
#else
    gmtime_r(&now, &t);
#endif
    strftime(timebuf2, timebufsize, "%Y-%m-%dT%H:%M:%S.000Z", &t);

    std::string saString = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><event version=\"2.0\" uid=\"";
    saString += myUID;
    saString += "\" type=\"a-f-G-U-C\" time=\"";
    saString += timebuf;
    saString += "\" start=\"";
    saString += timebuf;
    saString += "\" stale=\"";
    saString += timebuf2;
    saString += "\" how=\"h-e\">";
    saString += "<point lat=\"36.5261810013514\" lon=\"-77.3862509255614\" hae=\"9999999.0\" "
           "ce=\"9999999\" le=\"9999999\"/>"
           "<detail>        <contact phone=\"3152545187\" endpoint=\"10.233.154.103:4242:tcp\""
           " callsign=\"";
    saString += myCallsign;
    saString += "\"/>"
           "<uid Droid=\"JDOG\"/>"
           "<__group name=\"Cyan\" role=\"Team Member\"/>"
           "<status battery=\"100\"/>"
           "<track speed=\"0";
    saString += "\" course=\"56.23885995781046\"/>"
           "<precisionlocation geopointsrc=\"User\" altsrc=\"???\"/>"
           "</detail>"
           "</event>";

    sendto(fd, saString.c_str(), saString.size(), 0, (struct sockaddr *)&sockaddr, sizeof(sockaddr));

  
    close(fd);
    return 0;
}

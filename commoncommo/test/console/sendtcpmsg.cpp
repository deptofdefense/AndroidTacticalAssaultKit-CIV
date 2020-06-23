#include <string.h>
#include <sys/types.h>
#include <fcntl.h>
#include <errno.h>

#include <stdio.h>
#include <string>        
        

#ifdef WIN32
#define SockType SOCKET
#define BadSocket INVALID_SOCKET
#define SocketErr SOCKET_ERROR
#define KillSocket closesocket
#include <winsock2.h>
#include <ws2tcpip.h>
#include <time.h>

static int getSockError() {
return WSAGetLastError();
}
#else
#include <unistd.h>
#include <sys/socket.h>
#include <arpa/inet.h>
#include <sys/ioctl.h>
#include <netinet/in.h>
#include <net/if.h>
#include <net/if_arp.h>
#define SockType int
#define BadSocket -1
#define KillSocket close
#define SocketErr -1
static int getSockError() {
return errno;
}
#endif


std::string myUID("testcontactuid");
std::string myCallsign("testcontactcallsign");



std::string getMessage()
{
    static char buf[20];
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
    saString += "\" type=\"a-f-G-U-C\" how = \"h-e\" time=\"";
    saString += timebuf;
    saString += "\" start=\"";
    saString += timebuf;
    saString += "\" stale=\"";
    saString += timebuf2;
    saString += "\">";

    saString += "<point lat=\"36.5261810013514\" lon=\"-77.3862509255614\" hae=\"9999999.0\" "
           "ce=\"9999999\" le=\"9999999\"/>"
           "<detail>        <contact phone=\"3152545187\" endpoint=\"10.233.154.103:4242:tcp\""
           " callsign=\"";
    saString += myCallsign;
    saString += "\"/>"
           "<uid Droid=\"JDOG\"/>"
           "<__group name=\"Cyan\" role=\"Team Member\"/>"
           "<status battery=\"100\"/>"
           "<track speed=\"";
    sprintf(buf, "%d", 50);
    saString += buf;
    saString += "\" course=\"56.23885995781046\"/>"
           "<precisionlocation geopointsrc=\"User\" altsrc=\"???\"/>"
           "</detail>"
           "</event>";

    return saString;
}





int main(int argc, char *argv[])
{

    if (argc < 2) {
        fprintf(stderr, "Usage: %s <ip address to send to>\n", argv[0]);
        return -1;
    }
    
#ifdef WIN32
    WSADATA wsaData = { 0 };
    int iResult = WSAStartup(MAKEWORD(2, 2), &wsaData);
    if (iResult != 0) {
        fprintf(stderr, "WSAStartup failed: %d\n", iResult);
        return -1;
    }
#endif


    printf("Creating socket\n");
    SockType fd = socket(AF_INET, SOCK_STREAM, IPPROTO_TCP);
    if (fd == BadSocket) {
        fprintf(stderr, "Socket creation failed\n");
        return -1;
    }
    
    struct in_addr addr;
    struct sockaddr_in sockaddr;
    
    if (inet_pton(AF_INET, argv[1], &addr) != 1) {
        fprintf(stderr, "Address format error\n");
        return 1;
    }
    
    memset(&sockaddr, 0, sizeof(struct sockaddr_in));
    sockaddr.sin_addr = addr;
    sockaddr.sin_family = AF_INET;
    sockaddr.sin_port = htons(4242);
    
    printf("Connecting to %s port 4242\n", argv[1]);
    if (connect(fd, (struct sockaddr *)&sockaddr, sizeof(sockaddr)) != 0) {
        fprintf(stderr, "Error connecting %d\n", getSockError());
        return -1;
    }
    printf("Connected! Sending cot message....\n");
    

    std::string msg = getMessage();
    const char *msgBytes = msg.c_str();
    int remains = msg.length();
    while (remains > 0) {
        int n = send(fd, msgBytes, remains, 0);
        if (n == SocketErr) {
            fprintf(stderr, "Send returned an error %d\n", getSockError());
            return -1;
        }
        remains -= n;
        msgBytes += n;
    }
    



    printf("Complete - disconnecting\n");
    KillSocket(fd);
    
    
    return 0;
}

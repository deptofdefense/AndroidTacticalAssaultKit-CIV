#include "util/HttpClient.h"

using namespace atakmap::util;

HttpClient::HttpClient() :
    opaque(NULL)
{
#warning TODO implement HttpClient
}

HttpClient::~HttpClient()
{
    if (opaque) {
#warning TODO implement HttpClient
        opaque = NULL;
    }
}

void HttpClient::setReadTimeout(const size_t readTimeout)
{
#warning TODO implement HttpClient
}

void HttpClient::setConnectTimeout(const size_t timeout)
{
#warning TODO implement HttpClient
}

void HttpClient::setUserAgent(const char *agent)
{
#warning TODO implement HttpClient
}

bool HttpClient::openConnection(const char *url)
{
#warning TODO implement HttpClient
    return false;
}

void HttpClient::closeConnection()
{
#warning TODO implement HttpClient
}

int HttpClient::read(uint8_t *buf, const size_t len)
{
#warning TODO implement HttpClient
    return -1;
}

int HttpClient::getResponseCode() {
#warning TODO implement HttpClient
    return -1;
}

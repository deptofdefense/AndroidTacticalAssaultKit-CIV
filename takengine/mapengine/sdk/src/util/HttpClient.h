#ifndef ATAKMAP_UTIL_HTTP_CLIENT_H_INCLUDED
#define ATAKMAP_UTIL_HTTP_CLIENT_H_INCLUDED

#include <cstdint>

namespace atakmap {
    namespace util {
        class HttpClient
        {
        public:
            HttpClient();
            ~HttpClient();
        public:
            void setReadTimeout(const size_t readTimeout);
            void setConnectTimeout(const size_t timeout);
            void setUserAgent(const char *agent);
            bool openConnection(const char *url);
            void closeConnection();
            int read(uint8_t *buf, const size_t len);
            int getResponseCode();
        private:
            class HttpClientImpl;
            HttpClientImpl *opaque;
        };
    }
}

#endif // ATAKMAP_UTIL_HTTP_CLIENT_H_INCLUDED


#include <curl/curl.h>
#include <deque>
#include <algorithm>
#include <regex>
#include <memory>
#include "openssl/ssl.h"
#include "util/HttpProtocolHandler.h"
#include "util/DataInput2.h"
#include "util/Memory.h"
#include "util/URI.h"

using namespace TAK::Engine::Util;

namespace {
    static const char * const DEFAULT_USER_AGENT = "TAK";

    enum class HTTPSupport {
        NO_SUPPORT,
        HTTP,
        HTTPS
    };

    HTTPSupport getHTTPSupport(const char *URI) NOTHROWS;

    class OpenSSLX509TrustStore : public X509TrustStore {
    public:
        virtual ~OpenSSLX509TrustStore() NOTHROWS;
        virtual TAKErr addEncodedCert(const uint8_t* data, size_t dataLen) NOTHROWS;

        void build(X509_STORE *store) NOTHROWS;

        std::vector<X509 *> certs_;
    };

    class CURLDataInput : public DataInput2 {
    public:
        CURLDataInput(const std::shared_ptr<HttpProtocolHandlerClientInterface> &client_iface_, const std::shared_ptr<OpenSSLX509TrustStore> &trustStore);
        TAKErr open(const char *URI, HTTPSupport support) NOTHROWS;
        virtual ~CURLDataInput();
        virtual TAKErr close() NOTHROWS;
        virtual TAKErr read(uint8_t* buf, std::size_t* numRead, const std::size_t len) NOTHROWS;
        virtual TAKErr readByte(uint8_t* value) NOTHROWS;
        virtual TAKErr skip(const std::size_t n) NOTHROWS;
        virtual int64_t length() const NOTHROWS;
    private:
        TAKErr performRead(size_t len) NOTHROWS;
        static size_t writeCallback(void* data, size_t size, size_t nmemb, void* userData);
        static CURLcode sslContextFunction(CURL* curl, void* sslctx, void* userData);
    private:
        mutable TAK::Engine::Thread::Mutex mutex_;
        std::shared_ptr<HttpProtocolHandlerClientInterface> client_iface_;
        std::shared_ptr<OpenSSLX509TrustStore> root_trust_store_;
        CURL *curl_easy_;
        CURLM *curl_multi_;
        std::deque<uint8_t> buf_;
        TAK::Engine::Port::String host_;
        int64_t length_;
        int running_;
    };

    class DefaultHttpProtocolHandlerClientInterface : public HttpProtocolHandlerClientInterface {
    public:
        virtual ~DefaultHttpProtocolHandlerClientInterface() NOTHROWS;
        virtual TAKErr getUserAgent(TAK::Engine::Port::String* result, const char* host) NOTHROWS;
        virtual bool shouldVerifySSLHost(const char* host) NOTHROWS;
        virtual bool shouldVerifySSLPeer(const char* host) NOTHROWS;
        virtual bool allowRedirect(const char* host) NOTHROWS;
        virtual TAKErr populateX509TrustStore(X509TrustStore* trustStore, const char* host) NOTHROWS;
        virtual bool shouldAuthenticateHost(const char* host, int priorAttempts) NOTHROWS;
        virtual TAKErr getBasicAuth(TAK::Engine::Port::String* username, TAK::Engine::Port::String* password, const char *host) NOTHROWS;
    };
}

//
// X509TrustStore
//

X509TrustStore::~X509TrustStore() NOTHROWS
{ }

//
// HttpProtocolHandlerClientInterface
//

HttpProtocolHandlerClientInterface::~HttpProtocolHandlerClientInterface() NOTHROWS
{ }

//
// HttpProtocolHandler
//

HttpProtocolHandler::HttpProtocolHandler()
    : HttpProtocolHandler(std::make_shared<DefaultHttpProtocolHandlerClientInterface>())
{ }


HttpProtocolHandler::HttpProtocolHandler(const std::shared_ptr<HttpProtocolHandlerClientInterface>& client_iface_) NOTHROWS
    : client_iface_(client_iface_)
{ }

HttpProtocolHandler::~HttpProtocolHandler() NOTHROWS 
{ }

TAKErr HttpProtocolHandler::triggerRootTrustStorePopulate() NOTHROWS {
    Thread::Lock lock(this->mutex_);
    if (lock.status != TE_Ok)
        return lock.status;
    this->root_trust_store_ = nullptr;
    return TE_Ok;
}

TAKErr HttpProtocolHandler::handleURI(DataInput2Ptr& ctx, const char* URI) NOTHROWS {

    curl_global_init(CURL_GLOBAL_DEFAULT);

    HTTPSupport support = getHTTPSupport(URI);
    if (support == HTTPSupport::NO_SUPPORT)
        return TE_Unsupported;
    
    std::shared_ptr<OpenSSLX509TrustStore> rootTrustStore;
    if (support == HTTPSupport::HTTPS) {
        
        Thread::Lock lock(this->mutex_);

        rootTrustStore = std::static_pointer_cast<OpenSSLX509TrustStore>(this->root_trust_store_);
        if (!rootTrustStore) {

            // (re)populate the root trust store
            rootTrustStore = std::make_shared<OpenSSLX509TrustStore>();
            TAKErr code = this->client_iface_->populateX509TrustStore(rootTrustStore.get(), nullptr);
            if (code != TE_Ok)
                return code;
            this->root_trust_store_ = rootTrustStore;
        } else {
        }
    }

    std::unique_ptr<CURLDataInput> result(new CURLDataInput(this->client_iface_, rootTrustStore));
    TAKErr code = result->open(URI, support);
    if (code == TE_Ok) {
        ctx = DataInput2Ptr(result.release(), Memory_deleter_const<DataInput2, CURLDataInput>);
    }

    return code;
}

namespace {
    HTTPSupport getHTTPSupport(const char* URI) NOTHROWS {
        TAK::Engine::Port::String scheme;
        if (URI_parse(&scheme, nullptr, nullptr, nullptr, nullptr, nullptr, URI) != TE_Ok || scheme.get() == nullptr)
            return HTTPSupport::NO_SUPPORT;
        
        int cmp = -1;
        TAK::Engine::Port::String_compareIgnoreCase(&cmp, scheme, "http");
        if (cmp == 0)
            return HTTPSupport::HTTP;
        TAK::Engine::Port::String_compareIgnoreCase(&cmp, scheme, "https");
        if (cmp == 0)
            return HTTPSupport::HTTPS;

        return HTTPSupport::NO_SUPPORT;
    }

    //
    // OpenSSLX509TrustStore
    //

    OpenSSLX509TrustStore::~OpenSSLX509TrustStore() NOTHROWS {
        for (size_t i = 0; i < certs_.size(); ++i)
            X509_free(certs_[i]);
    }

    TAKErr OpenSSLX509TrustStore::addEncodedCert(const uint8_t* data, size_t dataLen) NOTHROWS {
        const uint8_t* dataIn = data;
        X509* cert = d2i_X509(nullptr, &dataIn, static_cast<int>(dataLen));
        if (cert) {
            certs_.push_back(cert);
            return TE_Ok;
        }
        return TE_Err;
    }

    void OpenSSLX509TrustStore::build(X509_STORE* store) NOTHROWS {
        for (size_t i = 0; i < certs_.size(); ++i)
            X509_STORE_add_cert(store, certs_[i]);
    }

    //
    // DefaultHttpProtocolHandlerClientInterface
    //

    DefaultHttpProtocolHandlerClientInterface::~DefaultHttpProtocolHandlerClientInterface() NOTHROWS
    {}

    TAKErr DefaultHttpProtocolHandlerClientInterface::getUserAgent(TAK::Engine::Port::String* result, const char* URI) NOTHROWS {
        return TE_Ok;
    }

    bool DefaultHttpProtocolHandlerClientInterface::shouldVerifySSLHost(const char* URI) NOTHROWS {
        return true;
    }

    bool DefaultHttpProtocolHandlerClientInterface::shouldVerifySSLPeer(const char* URI) NOTHROWS {
        return true;
    }

    bool DefaultHttpProtocolHandlerClientInterface::allowRedirect(const char* URI) NOTHROWS {
        return false;
    }

    TAKErr DefaultHttpProtocolHandlerClientInterface::populateX509TrustStore(X509TrustStore* trustStore, const char* URI) NOTHROWS {
        return TE_Ok;
    }

    bool DefaultHttpProtocolHandlerClientInterface::shouldAuthenticateHost(const char* host, int priorAttempts) NOTHROWS {
        return false;
    }
    
    TAKErr DefaultHttpProtocolHandlerClientInterface::getBasicAuth(TAK::Engine::Port::String* username, TAK::Engine::Port::String* password, const char *host) NOTHROWS {
        return TE_Unsupported;
    }

    //
    // CURLDataInput
    //

    CURLDataInput::CURLDataInput(const std::shared_ptr<HttpProtocolHandlerClientInterface>& client_iface_, const std::shared_ptr<OpenSSLX509TrustStore>& trustStore)
    : curl_easy_(nullptr),
    curl_multi_(nullptr),
    client_iface_(client_iface_),
    root_trust_store_(trustStore),
    running_(0),
    length_(0)
    { }

    CURLDataInput::~CURLDataInput() {
        this->close();
    }

    TAKErr CURLDataInput::open(const char* URI, HTTPSupport support) NOTHROWS {

        // must have a valid host
        if (URI_parse(nullptr, nullptr, &this->host_, nullptr, nullptr, nullptr, URI) != TE_Ok || this->host_.get() == nullptr)
            return TE_InvalidArg;

        TAK::Engine::Port::String username;
        TAK::Engine::Port::String password;
        int authAttempts = 0;
        bool connecting = true;
        TAKErr code = TE_Ok;

        while (connecting) {

            curl_easy_ = curl_easy_init();
            if (!curl_easy_)
                return TE_Err;

            CURLcode res = curl_easy_setopt(curl_easy_, CURLOPT_URL, URI);
            if (res != CURLE_OK)
                return TE_Err;

            curl_easy_setopt(curl_easy_, CURLOPT_WRITEFUNCTION, writeCallback);
            curl_easy_setopt(curl_easy_, CURLOPT_WRITEDATA, (void*)this);

            TAK::Engine::Port::String userAgentStr;
            const char* userAgent = ::DEFAULT_USER_AGENT;
            if (client_iface_) {
                client_iface_->getUserAgent(&userAgentStr, this->host_);
                if (userAgentStr)
                    userAgent = userAgentStr.get();

                if (client_iface_->allowRedirect(this->host_))
                    curl_easy_setopt(curl_easy_, CURLOPT_FOLLOWLOCATION, 1L);

                if (client_iface_->shouldAuthenticateHost(this->host_, authAttempts)) {
                    code = client_iface_->getBasicAuth(&username, &password, this->host_);
                    TE_CHECKBREAK_CODE(code);
                } else if (authAttempts > 0) {
                    // too many failed attempts
                    code = TE_IO;
                    break;
                }
            }

            curl_easy_setopt(curl_easy_, CURLOPT_USERAGENT, userAgent);

            if (username)
                curl_easy_setopt(curl_easy_, CURLOPT_USERNAME, username.get());
            if (password)
                curl_easy_setopt(curl_easy_, CURLOPT_PASSWORD, password.get());

            if (support == HTTPSupport::HTTPS) {
                curl_easy_setopt(curl_easy_, CURLOPT_SSL_CTX_FUNCTION, sslContextFunction);
                curl_easy_setopt(curl_easy_, CURLOPT_SSL_CTX_DATA, (void*)this);

                if (client_iface_) {
                    if (!client_iface_->shouldVerifySSLHost(this->host_))
                        curl_easy_setopt(curl_easy_, CURLOPT_SSL_VERIFYHOST, 0L);
                    if (!client_iface_->shouldVerifySSLPeer(this->host_))
                        curl_easy_setopt(curl_easy_, CURLOPT_SSL_VERIFYPEER, 0L);
                }
            }

            curl_multi_ = curl_multi_init();
            if (!curl_multi_)
                return TE_Err;

            CURLMcode mres = curl_multi_add_handle(curl_multi_, curl_easy_);
            if (mres != CURLM_OK) {
                curl_multi_cleanup(curl_multi_);
                curl_multi_ = nullptr;
                return TE_Err;
            }

            long responseCode = 0;
            do {
                int numfds;
                CURLMcode mc = curl_multi_wait(curl_multi_, NULL, 0, INT_MAX, &numfds);
                if (mc != CURLM_OK) {
                    return TE_IO;
                }
                curl_multi_perform(curl_multi_, &running_);
                curl_easy_getinfo(curl_easy_, CURLINFO_RESPONSE_CODE, &responseCode);
            } while (this->running_ && responseCode == 0);

            if (responseCode == 401) {
                // authentication error
                this->close();
                authAttempts++;
                username = nullptr;
                password = nullptr;
            } else if (responseCode == 200) {
                connecting = false;
                code = TE_Ok;
            } else {
                code = TE_IO;
            }
        }

        if (code == TE_Ok) {
            curl_off_t contentLen = 0;
            curl_easy_getinfo(curl_easy_, CURLINFO_CONTENT_LENGTH_DOWNLOAD_T, &contentLen);
            this->length_ = contentLen;
        } else {
            this->close();
        }

        return code;
    }

    TAKErr CURLDataInput::close() NOTHROWS {

        TAK::Engine::Thread::Lock lock(this->mutex_);
        if (lock.status != TE_Ok)
            return lock.status;

        if (curl_multi_) {
            curl_multi_remove_handle(curl_multi_, curl_easy_);
            curl_multi_cleanup(curl_multi_);
            this->curl_multi_ = nullptr;
        }
        if (curl_easy_) {
            curl_easy_cleanup(curl_easy_);
            this->curl_easy_ = nullptr;
        }
        this->buf_.erase(buf_.begin(), buf_.end());
        this->running_ = false;

        return TE_Ok;
    }
    
    TAKErr CURLDataInput::read(uint8_t *dst, std::size_t* numRead, const std::size_t len) NOTHROWS {
        
        if (!dst && len)
            return TE_InvalidArg;
        
        TAK::Engine::Thread::Lock lock(this->mutex_);
        if (lock.status != TE_Ok)
            return lock.status;

        TAKErr code = this->performRead(len);
        if (code != TE_Ok)
            return code;
        size_t copyNum = std::min(len, this->buf_.size());

        if (copyNum) {
            memcpy(dst, &buf_[0], copyNum);
            buf_.erase(buf_.begin(), buf_.begin() + copyNum);
            length_ = std::max<int64_t>(0, length_ - copyNum);
        }

        if (numRead)
            *numRead = copyNum;

        return TE_Ok;
    }
    
    TAKErr CURLDataInput::readByte(uint8_t* value) NOTHROWS {
        size_t numRead = 0;
        TAKErr code = read(value, &numRead, 1);
        if (!code)
            return code;
        return numRead == 1 ? TE_Ok : TE_EOF;
    }
    
    TAKErr CURLDataInput::skip(const std::size_t n) NOTHROWS {

        TAK::Engine::Thread::Lock lock(this->mutex_);
        if (lock.status != TE_Ok)
            return lock.status;

        TAKErr code = TE_Ok;
        size_t unreadCount = buf_.size() - n;
        if (unreadCount > 0) {
            code = performRead(unreadCount);
            if (code != TE_Ok)
                return code;
        }

        if (buf_.size() < n)
            return TE_InvalidArg;

        buf_.erase(buf_.begin(), buf_.begin() + n);
        length_ = std::max<int64_t>(0, length_ - n);
        return code;
    }
    
    int64_t CURLDataInput::length() const NOTHROWS {

        TAK::Engine::Thread::Lock lock(this->mutex_);
        if (lock.status != TE_Ok)
            return lock.status;

        return this->length_;
    }

    size_t CURLDataInput::writeCallback(void* data, size_t size, size_t nmemb, void *userData) {
        CURLDataInput *thiz = static_cast<CURLDataInput *>(userData);
        size_t byteSize = size * nmemb;
        const uint8_t *start = static_cast<uint8_t *>(data);
        thiz->buf_.insert(thiz->buf_.end(), start, start + byteSize);
        return byteSize;
    }

    CURLcode CURLDataInput::sslContextFunction(CURL* curl, void *rawSSLCtx, void *userData) {

        SSL_CTX *sslCtx = static_cast<SSL_CTX *>(rawSSLCtx);
        CURLDataInput *thiz = static_cast<CURLDataInput *>(userData);

        X509_STORE* store = X509_STORE_new();
        if (!store)
            return CURLE_SSL_CERTPROBLEM;

        // build host specific trust store
        OpenSSLX509TrustStore trustStore;
        TAKErr code = thiz->client_iface_->populateX509TrustStore(&trustStore, thiz->host_);
        if (code != TE_Ok)
            return CURLE_SSL_CERTPROBLEM;

        trustStore.build(store);

        // add root certs
        if (thiz->root_trust_store_)
            thiz->root_trust_store_->build(store);

        SSL_CTX_set_cert_store(sslCtx, store);
        return CURLE_OK;
    }

    TAKErr CURLDataInput::performRead(size_t len) NOTHROWS {
        while (running_ && buf_.size() < len) {
            int numfds;
            CURLMcode mc = curl_multi_wait(curl_multi_, NULL, 0, INT_MAX, &numfds);
            if (mc != CURLM_OK) {
                return TE_IO;
            }
            curl_multi_perform(curl_multi_, &running_);
        }
        return TE_Ok;
    }
}
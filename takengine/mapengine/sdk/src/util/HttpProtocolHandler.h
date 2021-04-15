#ifndef TAK_ENGINE_UTIL_HTTPPROTOCOLHANDLER_H_INCLUDED
#define TAK_ENGINE_UTIL_HTTPPROTOCOLHANDLER_H_INCLUDED

#include "util/ProtocolHandler.h"
#include "port/String.h"

namespace TAK
{
    namespace Engine
    {
        namespace Util
        {
            /**
             * CA Trust Store
             */
            class ENGINE_API X509TrustStore {
            public:
                virtual ~X509TrustStore() NOTHROWS;

                /**
                 * Call to add a binary encoded X509 certificate to the trust store
                 */
                virtual TAKErr addEncodedCert(const uint8_t *data, size_t dataLen) NOTHROWS = 0;
            };

            /**
             * Registration callbacks allowing HttpProtocolHandler to interface with the client application
             */
            class ENGINE_API HttpProtocolHandlerClientInterface {
            public:
                virtual ~HttpProtocolHandlerClientInterface() NOTHROWS;
                
                /**
                 * Optinal client supplied Aser-Agent string for a given host. If nullptr is returned, the default
                 * User-Agent "TAK" is used.
                 *
                 * @param host the host
                 */
                virtual Util::TAKErr getUserAgent(Port::String *result, const char *host) NOTHROWS = 0;

                /**
                 * Client indication whether to verify SSL host or not (default is true). Generally
                 * this should always be the case (best left to debug only situations).
                 *
                 * @param host the host
                 */
                virtual bool shouldVerifySSLHost(const char *host) NOTHROWS = 0;

                /**
                 * Client indication whether to verify SSL peer or not (default is true). Generally
                 * this should always be the case (best left to debug only situations).
                 *
                 * @param host the host
                 */
                virtual bool shouldVerifySSLPeer(const char *host) NOTHROWS = 0;

                /**
                 * Client indication whether server side redirects should be followed for a given
                 * host.
                 *
                 * @param host the host
                 */
                virtual bool allowRedirect(const char *host) NOTHROWS = 0;

                /**
                 * Called to populate a trust store for a given host OR root trust store. If this is to populate the
                 * root trust store, host is nullptr. The root trust store is populated only as needed. This is upon
                 * the very first HTTPS request or the very next request after triggerRootTrustStorePopulate is called
                 * on HttpProtocolHandler.
                 *
                 * @param trustStore the trust store to populate
                 * @param host the host string OR nullptr if root trust store
                 */
                virtual TAKErr populateX509TrustStore(X509TrustStore *trustStore, const char *host) NOTHROWS = 0;

                /**
                 * Called to determine if a connection should add authentication.
                 *
                 * @param domain the host domain in question
                 * @param priorAttempts the number of previous failed attempts
                 */
                virtual bool shouldAuthenticateHost(const char *domain, int priorAttempts) NOTHROWS = 0;

                /**
                 * Called to get the Basic-Auth network credentials.
                 *
                 * @param username [out] the application provided username
                 * @param password [out] the application provided password
                 * @param domain the domain in question
                 * @returns anything but TE_Ok to indicate error
                 */
                virtual TAKErr getBasicAuth(Port::String *username, Port::String *password, const char *domain) NOTHROWS = 0;

                /**
                 * Called to provide a chance for the client application to trust a server certificate when standard
                 * verification fails.
                 *
                 * @param x509Data the certificate data
                 * @param size the size of the certificate data
                 * @param host the host
                 * @returns TE_Ok to accept; TE_Unsupported to deny; anything else as general failure
                 */
                virtual TAKErr shouldTrustCertificate(const uint8_t *x509Data, size_t size, const char *host) NOTHROWS = 0;
            };

            /**
             * A protocol handler for HTTP and HTTPS requests.
             */
            class ENGINE_API HttpProtocolHandler : public ProtocolHandler
            {
            public:
                virtual ~HttpProtocolHandler() NOTHROWS;               
                
                /**
                 * Construct a handler with default client interface
                 */
                HttpProtocolHandler();

                /**
                 * Construct a handler with a supplied client interface
                 */
                explicit HttpProtocolHandler(const std::shared_ptr<HttpProtocolHandlerClientInterface> &client_iface_) NOTHROWS;

                /**
                 * Mark the root trust store as needing a refresh. This will trigger a call to the client interface
                 * to repopulate the root trust store during the very next HTTPS request.
                 */
                Util::TAKErr triggerRootTrustStorePopulate() NOTHROWS;

                //
                // ProtocolHandler interface
                //

                /**
                 * Handle the request
                 */
                Util::TAKErr handleURI(DataInput2Ptr &ctx, const char *URI) NOTHROWS override;
            private:
                Thread::Mutex mutex_;
                std::shared_ptr<HttpProtocolHandlerClientInterface> client_iface_;
                std::shared_ptr<X509TrustStore> root_trust_store_;
            };

        }
    }
}

#endif
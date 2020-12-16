#ifndef IMPL_INTERNALUTILS_H_
#define IMPL_INTERNALUTILS_H_

#include "contactuid.h"
#include "commologger.h"
#include "netinterface.h"
#include "commoresult.h"
#include "fileioprovider.h"
#include "fileioprovidertracker.h"
#include <string>
#include <stdexcept>
#include <curl/curl.h>
#include "openssl/ssl.h"
#include "openssl/pkcs12.h"
#include <memory>


#define COMMO_THROW(...)


namespace atakmap {
namespace commoncommo {
namespace impl {


class SSLArgException : public std::invalid_argument
{
public:
    explicit SSLArgException(CommoResult errCode, const std::string &what) :
                     std::invalid_argument(what), errCode(errCode)
    {
    }

    const CommoResult errCode;
};

class SSLCertChecker {
public:
    // Create a cert checker that checks certs against the given certificate
    // chain of the specified length.  Throws ILLEGAL_ARGUMENT based exceptions
    // only.
    SSLCertChecker(STACK_OF(X509) *caCerts,
                   int nCaCerts) COMMO_THROW (SSLArgException);
    ~SSLCertChecker();

    // True if cert verifies against the caCerts provided at construction.
    bool checkCert(X509 *cert);
    // Gets openssl error code from the last failed checkCert() call.
    // See ERROR CODES return from X509_STORE_CTX_get_error()
    // XXX - probably make this return our own error codes if ever used for
    // this other than logging!
    int getLastErrorCode();


private:
    COMMO_DISALLOW_COPY(SSLCertChecker);
    X509_STORE *store;
    X509_STORE_CTX *storeCtx;
    int lastErr;

};


class InternalUtils
{
public:
    static void logprintf(CommoLogger *logger, CommoLogger::Level level, const char *format, ...);
    static std::string hwAddrAsString(const HwAddress *addr, netinterfaceenums::NetInterfaceAddressMode addrMode);
    static std::string sizeToString(size_t s);
    static std::string uint64ToString(uint64_t v);
    static std::string intToString(int v);
    static std::string doubleToString(double d);

    // throws if ALL of s is not a string version of a single uint64,
    // if the string value is too big for uint64, or if s is empty string
    static uint64_t uint64FromString(const char *s);

    // throws if ALL of s is not a string version of a single int,
    // if the string value is too big for int, or if s is empty string
    static int intFromString(const char *s) COMMO_THROW (std::invalid_argument);

    // throws if ALL of s is not a string version of a single int,
    // if the string value is too big for int,
    // the converted int is less than min or greater than max,
    // or if s is empty string
    static int intFromString(const char *s, int min, int max) COMMO_THROW (std::invalid_argument);

    // throws if ALL of s is not a string version of a double, or
    // if s is empty string
    static double doubleFromString(const char *s) COMMO_THROW (std::invalid_argument);
    
    // Parse *unsigned* varint from buf with length len.
    // Return the decoded value if it is completely decoded before reaching
    // end of input.  Throws if input is exhausted prematurely or
    // encoded value would not fit in a uint64_t.
    // On successful return, len is set equal to the amount of input
    // consumed during decode.
    static uint64_t varintDecode(const uint8_t *buf, size_t *len) COMMO_THROW (std::invalid_argument);

    // Buffer size that can support encoding of any value supplied to
    // varintEncode.
    static const size_t VARINT_MAXBUF = 10;

    // Encode "value" as a varint into the buffer "buf" of size
    // buflen.  Returns the number of bytes in the buffer used.
    // Throws if the varint does not fit in buflen bytes (note that
    // buf may still have been modified!)
    // A buffer of size VARINT_MAXBUF bytes will fit any uint64 value
    static size_t varintEncode(uint8_t *buf, size_t buflen, uint64_t value) COMMO_THROW (std::invalid_argument);

    // val gets URL escaped internally using CURL functions on curlCtx.
    static void urlAppendParam(CURL *curlCtx, std::string *url,
                               const char *name, const char *val);
    // true if replacement succeeds, else false
    static bool urlReplaceHost(std::string *url, const char *newHost);
    // true if replacement succeeds, else false
    static bool urlReplacePort(std::string *url, int port);

    static bool computeSha256Hash(std::string *hashOut, const char *filename, std::shared_ptr<FileIOProvider>& provider);

    // Reads certData and populates *cert, *privKey, and ca with 
    // main cert, private key, and supporting certs respectively.
    // On successful return, caller must free cert, privKey, and caCerts using
    // X509_free(), EVP_PKEY_free(), and sk_X509_pop_free(caCerts, X509_free)
    // respectively, when no longer needed.
    // Reading ca is optional; pass NULL if not needed.
    // Can throw ILLEGAL_ARGUMENT, INVALID_CERT_PASSWORD, and INVALID_CERT
    static void readCert(const uint8_t *certData, size_t certLen,
                         const char *certPassword,
                         X509 **cert,
                         EVP_PKEY **privKey,
                         STACK_OF(X509) **caCerts = NULL,
                         int *nCaCerts = NULL) COMMO_THROW (SSLArgException);

    // Reads caCert and populates *caCerts and *nCaCerts with the chain
    // of ca certs and quantity of ca certs read in.
    // On successful return, caller must free caCerts with
    // sk_X509_pop_free(caCerts, X509_free) or similar.
    // Can throw ILLEGAL_ARGUMENT, INVALID_CACERT_PASSWORD, and INVALID_CACERT
    static void readCACerts(const uint8_t *caCertData, 
                         size_t caCertLen,
                         const char *caCertPassword,
                         STACK_OF(X509) **caCerts,
                         int *nCaCerts) COMMO_THROW (SSLArgException);

private:
    InternalUtils() {};
};

struct InternalContactUID : public ContactUID
{
    InternalContactUID(const uint8_t *uid, size_t len);
    InternalContactUID(const ContactUID *srcAddr);
    ~InternalContactUID();
private:
    COMMO_DISALLOW_COPY(InternalContactUID);

};


}
}
}

#endif

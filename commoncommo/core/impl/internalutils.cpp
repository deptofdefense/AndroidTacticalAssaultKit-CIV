#include "platform.h"
#include "internalutils.h"
#include "openssl/evp.h"
#include "openssl/err.h"

#include <stdarg.h>
#include <stdio.h>
#include <sstream>
#include <string.h>
#include <limits.h>
#include <math.h>

#include <sys/types.h>
#include <sys/stat.h>
#include <stddef.h>

using namespace atakmap::commoncommo;
using namespace atakmap::commoncommo::netinterfaceenums;
using namespace atakmap::commoncommo::impl;

void InternalUtils::logprintf(CommoLogger* logger,
        CommoLogger::Level level, const char* format, ...)
{
    const size_t BUFSIZE = 1024;
    char buf[BUFSIZE];

    va_list args;
    va_start(args, format);

    vsnprintf(buf, BUFSIZE, format, args);
    // Be certain of null termination if string is too long
    buf[BUFSIZE - 1] = '\0';

    va_end(args);

    logger->log(level, buf);
}


std::string InternalUtils::hwAddrAsString(const HwAddress *addr, NetInterfaceAddressMode addrMode)
{
    std::string s;
    if (addrMode == MODE_NAME) {
        s = std::string((const char *)addr->hwAddr, addr->addrLen);
    } else {
        char buf[3];
        for (size_t i = 0; i < addr->addrLen; ++i) {
            snprintf(buf, 3, "%02hhx", addr->hwAddr[i]);
            s.append(buf);
        }
    }
    return s;
}


std::string InternalUtils::sizeToString(const size_t s)
{
    std::stringstream ss;
    ss << s;
    return ss.str();
}

std::string InternalUtils::uint64ToString(const uint64_t s)
{
    std::stringstream ss;
    ss << s;
    return ss.str();
}

std::string InternalUtils::intToString(const int s)
{
    std::stringstream ss;
    ss << s;
    return ss.str();
}

std::string InternalUtils::doubleToString(const double d)
{
    std::stringstream ss;
    // Use 8 digits post decimal point and no scientific notation
    // to ensure we don't round out too much (think decimal degrees lat/lon)
    ss << std::fixed;
    ss.precision(8);
    ss << d;
    return ss.str();
}

uint64_t InternalUtils::uint64FromString(const char *s)
{
    char *end;
    unsigned long long l = strtoull(s, &end, 10);
    if (l > UINT64_MAX || *end != '\0' || *s == '\0')
        throw std::invalid_argument("invalid numeric unsigned integer string");
    return (uint64_t)l;
}

int InternalUtils::intFromString(const char *s) COMMO_THROW (std::invalid_argument)
{
    return intFromString(s, INT_MIN, INT_MAX);
}

int InternalUtils::intFromString(const char *s, int min, int max) COMMO_THROW (std::invalid_argument)
{
    char *end;
    long l = strtol(s, &end, 10);
    if (l < min || l > max || *end != '\0' || *s == '\0')
        throw std::invalid_argument("invalid numeric integer string");
    return (int)l;
}

double InternalUtils::doubleFromString(const char *s) COMMO_THROW (std::invalid_argument)
{
    char *end;
    double d = strtod(s, &end);
    if (d == HUGE_VAL || d == -HUGE_VAL || *end != '\0' || *s == '\0')
        throw std::invalid_argument("invalid numeric string");
    return d;
}

uint64_t InternalUtils::varintDecode(const uint8_t *buf, size_t *len) COMMO_THROW (std::invalid_argument)
{
    size_t i = 0;
    uint64_t v = 0;
    int shiftBits = 0;
    bool complete = false;
    
    while (!complete && i < *len && shiftBits < (64 - 7)) {
        int x = buf[i] & 0x7F;
        v |= (x << shiftBits);
        shiftBits += 7;
        
        if ((buf[i++] & 0x80) == 0)
            complete = true;
    }

    if (!complete)
        throw std::invalid_argument("Could not decode varint");

    *len = i;
    return v;
}

size_t InternalUtils::varintEncode(uint8_t *buf, size_t buflen, uint64_t v) COMMO_THROW (std::invalid_argument)
{
    static const uint64_t mask = 0xFFFFFFFFFFFFFF80;
    size_t i = 0;
    bool complete = false;

    while (!complete && i < buflen) {
        if ((v & mask) == 0) {
            // final bits
            buf[i++] = v & 0x7F;
            complete = true;
        } else {
            buf[i++] = 0x80 | (v & 0x7F);
            v = v >> 7;
        }
    }
    
    if (!complete)
        throw std::invalid_argument("Buffer too small for encoding of varint");
    
    return i;
}


void InternalUtils::urlAppendParam(CURL *curlCtx, std::string *url,
                                   const char *name, const char *val)
{
    bool hasQ = url->find("?") != std::string::npos;
    if (!hasQ) {
        url->push_back('?');
    } else {
        url->push_back('&');
    }
    url->append(name);
    url->push_back('=');

    char *escaped = curlCtx ? curl_easy_escape(curlCtx, val, 0) : NULL;
    if (escaped) {
        url->append(escaped);
        curl_free(escaped);
    } else {
        url->append(val);
    }
}

bool InternalUtils::urlReplaceHost(std::string *url, const char *newHost)
{
    // Find ://
    size_t colonSlashSlash = url->find("://");
    if (colonSlashSlash == std::string::npos)
        return false;
    colonSlashSlash += 3;
    
    // Find /root/of/path
    size_t rootSlash = url->find("/", colonSlashSlash);
    if (rootSlash == std::string::npos)
        rootSlash = url->size();
    
    // Look for any user:pass@ in the remainder - if there,
    // move start position forward to it
    size_t authAt = url->find("@", colonSlashSlash);
    if (authAt != std::string::npos && authAt < rootSlash)
        colonSlashSlash = authAt + 1;
    
    // Look for ':port'
    size_t portColon = url->find(":", colonSlashSlash);
    if (portColon != std::string::npos && portColon < rootSlash)
        rootSlash = portColon;
    
    rootSlash -= colonSlashSlash;
    url->replace(colonSlashSlash, rootSlash, newHost);
    return true;
}

bool InternalUtils::urlReplacePort(std::string *url, int port)
{
    // Find ://
    size_t colonSlashSlash = url->find("://");
    if (colonSlashSlash == std::string::npos)
        return false;
    colonSlashSlash += 3;
    
    // Find /root/of/path
    size_t rootSlash = url->find("/", colonSlashSlash);
    if (rootSlash == std::string::npos)
        rootSlash = url->size();
    
    // Look for any user:pass@ in the remainder - if there,
    // move start position forward to it
    size_t authAt = url->find("@", colonSlashSlash);
    if (authAt != std::string::npos && authAt < rootSlash)
        colonSlashSlash = authAt + 1;
    
    // Look for ':port'
    size_t portColon = url->find(":", colonSlashSlash);
    if (portColon != std::string::npos && portColon < rootSlash) {
        // Port exists, replace it
        portColon++;
        url->replace(portColon, rootSlash - portColon, intToString(port));
    } else {
        // Port does not exist, add it
        url->insert(rootSlash, intToString(port));
    }
    return true;
}

bool InternalUtils::computeSha256Hash(std::string *hashOut, const char *filename, std::shared_ptr<FileIOProvider>& provider)
{
    static const size_t bufSize = 512;
    uint8_t buf[bufSize];
    unsigned int hashBinSize = 0;
    uint8_t hashBin[EVP_MAX_MD_SIZE];
    bool ret = true;

    hashOut->clear();

    EVP_MD_CTX *mdCtx = EVP_MD_CTX_create();
    const EVP_MD *sha256type = EVP_sha256();

    if (!EVP_DigestInit(mdCtx, sha256type))
        ret = false;

    FileHandle *f = provider->open(filename, "rb");
    if (!f)
        ret = false;

    while (ret) {
        size_t n = provider->read(buf, 1, bufSize, f);
        if (n > 0 && !EVP_DigestUpdate(mdCtx, buf, n))
            ret = false;

        if (n < bufSize && provider->error(f))
            ret = false;

        if (!n)
            // EOF
            break;
    }
    if (f)
        provider->close(f);

    if (ret) {
        if (!EVP_DigestFinal(mdCtx, hashBin, &hashBinSize))
            ret = false;
    }
    if (ret) {
        char cbuf[4];
        for (unsigned int i = 0; i < hashBinSize; ++i) {
            sprintf(cbuf, "%02x", hashBin[i]);
            hashOut->append(cbuf);
        }
    }

    EVP_MD_CTX_destroy(mdCtx);
    return ret;
}

namespace {
    // Throws ILLEGAL_ARGUMENT based exception only for invalid/nonsense args.
    // Returns NULL if pkcs12 unable to be read/parsed.
    // Caller must free using PKCS12_free()
    PKCS12 *pkcsRead(const uint8_t *certData, size_t certLen) COMMO_THROW (SSLArgException)
    {
        if (!certData || !certLen)
            throw SSLArgException(COMMO_ILLEGAL_ARGUMENT, "Missing argument - certificate or length is null!");
        if (certLen > INT_MAX)
            throw SSLArgException(COMMO_ILLEGAL_ARGUMENT, "Certificate is too large!");
        
        BIO *bio = BIO_new_mem_buf(const_cast<uint8_t *>(certData),
                                   (int)certLen);
        PKCS12 *pkcs12 = d2i_PKCS12_bio(bio, NULL);
        BIO_free(bio);
        // NULL on failure to read/parse
        return pkcs12;
    }
}

void InternalUtils::
readCert(const uint8_t *certData, size_t certLen,
         const char *certPassword,
         X509 **pcert, EVP_PKEY **pprivKey, 
         STACK_OF(X509) **pcaCerts, int *pnCaCerts) COMMO_THROW (SSLArgException)
{
    const char *errMsg = NULL;
    CommoResult errCode = COMMO_SUCCESS;
    PKCS12 *pkcs12 = pkcsRead(certData, certLen);
    if (pcaCerts && !pnCaCerts)
        throw SSLArgException(COMMO_ILLEGAL_ARGUMENT, "Improper request for CA certificates");
    if (!pkcs12)
        throw SSLArgException(COMMO_INVALID_CERT, "Unable to read client cert");

    EVP_PKEY *privKey = NULL;
    X509 *cert = NULL;
    STACK_OF(X509) *caCerts = NULL;
    int ret = PKCS12_parse(pkcs12, certPassword, &privKey, &cert,
                           pcaCerts ? &caCerts : NULL);
    PKCS12_free(pkcs12);
    
    if (!ret) {
        unsigned long sslErr = ERR_get_error();
        if (ERR_GET_LIB(sslErr) == ERR_LIB_PKCS12 && 
                     ERR_GET_REASON(sslErr) == PKCS12_R_MAC_VERIFY_FAILURE) {
            errMsg = "Incorrect password for client cert";
            errCode = COMMO_INVALID_CERT_PASSWORD;
        } else {
            errMsg = "Invalid format for client cert";
            errCode = COMMO_INVALID_CERT;
        }
        throw SSLArgException(errCode, errMsg);
    }

    if (!cert) {

        errCode = COMMO_INVALID_CERT;
        errMsg = "No certificate in the pkcs#12 file";
        goto error;
    }
    if (!privKey) {
        X509_free(cert);
        errCode = COMMO_INVALID_CERT;
        errMsg = "no private key in the pkcs#12 file";
        goto error;
    }
    
    *pcert = cert;
    *pprivKey = privKey;
    if (pcaCerts) {
        *pcaCerts = caCerts;
        *pnCaCerts = sk_X509_num(caCerts);
    }
    return;

error:
    if (cert)
        X509_free(cert);
    if (privKey)
        EVP_PKEY_free(privKey);
    if (pcaCerts && caCerts)
        sk_X509_pop_free(caCerts, X509_free);
    throw SSLArgException(errCode, errMsg);
}

void InternalUtils::
readCACerts(const uint8_t *caCertData, size_t caCertLen,
            const char *caCertPassword,
            STACK_OF(X509) **pcaCerts,
            int *pnCaCerts) COMMO_THROW (SSLArgException)
{
    STACK_OF(X509) *caCerts = NULL;
    EVP_PKEY *caPrivKey = NULL;
    X509 *dummyCert = NULL;
    int nCaCerts = 0;
    const char *errMsg = NULL;
    CommoResult errCode = COMMO_SUCCESS;

    PKCS12 *pkcs12 = pkcsRead(caCertData, caCertLen);
    if (!pkcs12)
        throw SSLArgException(COMMO_INVALID_CACERT, "Unable to read ca cert");


    int ret = PKCS12_parse(pkcs12, caCertPassword,
                           &caPrivKey, &dummyCert, &caCerts);
    PKCS12_free(pkcs12);

    if (!ret) {
        unsigned long sslErr = ERR_get_error();
        if (ERR_GET_LIB(sslErr) == ERR_LIB_PKCS12 && 
                     ERR_GET_REASON(sslErr) == PKCS12_R_MAC_VERIFY_FAILURE) {
            errMsg = "Incorrect password for ca cert";
            errCode = COMMO_INVALID_CACERT_PASSWORD;
        } else {
            errMsg = "Invalid format for ca cert";
            errCode = COMMO_INVALID_CACERT;
        }

        goto error;
    }
    if (caPrivKey)
        EVP_PKEY_free(caPrivKey);
    if (dummyCert)
        X509_free(dummyCert);
    if (!caCerts || (nCaCerts = sk_X509_num(caCerts)) == 0) {
        errMsg = "No ca certificate chain in the pkcs#12 buffer";
        errCode = COMMO_INVALID_CACERT;
        goto error;
    }
    
    *pnCaCerts = nCaCerts;
    *pcaCerts = caCerts;
    
    return;
    
error:
    if (caCerts)
        sk_X509_pop_free(caCerts, X509_free);
    EVP_PKEY_free(caPrivKey);
    throw SSLArgException(errCode, errMsg);
}




SSLCertChecker::SSLCertChecker(STACK_OF(X509) *caCerts,
                               int nCaCerts)
                COMMO_THROW (SSLArgException) :
                    store(NULL),
                    storeCtx(NULL),
                    lastErr(0)
{
    store = X509_STORE_new();
    if (!store)
        throw SSLArgException(COMMO_ILLEGAL_ARGUMENT,
                              "Unable to create certificate store");
    
    for (int i = 0; i < nCaCerts; ++i)
        X509_STORE_add_cert(store, sk_X509_value(caCerts, i));

    storeCtx = X509_STORE_CTX_new();
    if (!storeCtx) {
        X509_STORE_free(store);
        throw SSLArgException(COMMO_ILLEGAL_ARGUMENT,
                          "unable to create certificate verification context");
    }
}

SSLCertChecker::~SSLCertChecker()
{
    X509_STORE_CTX_free(storeCtx);
    X509_STORE_free(store);
}

bool SSLCertChecker::checkCert(X509 *cert)
{
    X509_STORE_CTX_init(storeCtx, store, cert, NULL);

    int vResult = X509_verify_cert(storeCtx);
    
    bool ret = (vResult > 0);
    if (!ret)
        lastErr = X509_STORE_CTX_get_error(storeCtx);

    X509_STORE_CTX_cleanup(storeCtx);

    return ret;
}

int SSLCertChecker::getLastErrorCode()
{
    return lastErr;
}




InternalContactUID::InternalContactUID(const uint8_t* uid, size_t len) :
        ContactUID(new uint8_t[len], len)
{
    memcpy(const_cast<uint8_t * const>(contactUID), uid, len);
}

InternalContactUID::InternalContactUID(const ContactUID* srcAddr) :
        ContactUID(new uint8_t[srcAddr->contactUIDLen], srcAddr->contactUIDLen)
{
    memcpy(const_cast<uint8_t * const>(contactUID),
           srcAddr->contactUID, contactUIDLen);
}

InternalContactUID::~InternalContactUID()
{
    delete[] contactUID;
}


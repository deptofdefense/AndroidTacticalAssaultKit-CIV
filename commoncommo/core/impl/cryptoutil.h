#ifndef IMPL_CRYPTOUTIL_H
#define IMPL_CRYPTOUTIL_H

#include "commologger.h"
#include "internalutils.h"
#include <string>
#include <stdexcept>
#include "openssl/ssl.h"
//#include "openssl/evp.h"

namespace atakmap {
namespace commoncommo {
namespace impl
{


// Access by multiple threads simultaneously is not allowed
class MeshNetCrypto
{
public:
    static const size_t KEY_BYTE_LEN = 32;

    // Keys MUST be non-NULL and 256-bit
    // Keys are copied
    MeshNetCrypto(CommoLogger *logger, const uint8_t *cipherKey,
                                       const uint8_t *authKey);
    ~MeshNetCrypto();
    
    // Key MUST be non-NULL and 256-bit
    // Key is copied
    void setCipherKey(const uint8_t *cipherKey);
    // Key MUST be non-NULL and 256-bit
    // Key is copied
    void setAuthKey(const uint8_t *authKey);

    // Encrypt *data, which is *len bytes long.
    // Updates *data to point to a new buffer; *len updated with encrypted size
    // *data must be deallocated by caller using delete[]
    void encrypt(uint8_t **data, size_t *len) COMMO_THROW (std::invalid_argument);
    // Decrypt *data, which is *len bytes long.
    // Returns true for success, false if fails; note that success just means
    // that data matched the auth key and that it could be passed through
    // decryption!  Treat decrypted data with due caution still!
    // On true return only: 
    //   Updates *data to point to a new buffer; *len updated with
    //   decrypted size
    //   *data must be deallocated by caller using delete[]
    // If data and len are untouched on false return.
    bool decrypt(uint8_t **data, size_t *len);



private:
#ifdef DEBUG_COMMO_CRYPTO
    CommoLogger *logger;
#endif
    
    // Non-null, 256-bit
    uint8_t cipherKey[KEY_BYTE_LEN];
    uint8_t authKey[KEY_BYTE_LEN];
    
    EVP_CIPHER_CTX *cipherCtx;

    static const size_t BLOCK_BYTE_LEN = 16;
    static const size_t IV_BYTE_LEN = BLOCK_BYTE_LEN;
    static const size_t HMAC_BYTE_LEN = 32;
};




class CryptoUtil
{
public:
    CryptoUtil(CommoLogger *logger);

    char *generateKeyCryptoString(const char *password, const int keyLen);
    char *generateCSRCryptoString(const char **entryKeys,
                                  const char **entryValues,
                                  size_t nEntries,
                                  const char* pem,
                                  const char* password);
    
    char *generateKeystoreCryptoString(const char *certPem, const char **caPem, 
                           size_t nCa, const char *pkeyPem,
                           const char *password, 
                           const char *friendlyName);
    static void freeCryptoString(char *cryptoString);

    size_t generateSelfSignedCert(uint8_t **cert, const char *password);
    void freeSelfSignedCert(uint8_t *cert);

    static bool fromBase64(const char *b64, 
                           char **poutbuf, size_t *poutlen);
    static char *toBase64(char *str, int length);

private:
    CommoLogger *logger;
    void logerr(const char *msg);

    bool pkeyToString(std::string &str, EVP_PKEY *pkey, const char *password);
    X509 *stringToCert(const char *pem);
    EVP_PKEY *stringToPkey(const char *pem, const char *password);
    bool csrToString(std::string &str, X509_REQ *csr);
};

}
}
}


#endif


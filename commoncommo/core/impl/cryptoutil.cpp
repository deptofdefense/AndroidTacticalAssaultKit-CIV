#include "cryptoutil.h"
#include "internalutils.h"
#include <string.h>

/*
 * This ported more or less verbatim from JBlomberg "cryptoutils";
 * reorganized slightly with several bug fixes of memory leaks and use of global
 * vars that preclude multithreaded use
 */

#ifdef WIN32
// Windows demands use of _strdup name
#define strdup _strdup
// Windows system headers define this.  Undef prior to openssl x509 include
// to avoid conflicting definitions
#undef X509_NAME
#endif


#include "openssl/x509.h"
#include "openssl/x509v3.h"
#include "openssl/err.h"
#include "openssl/rand.h"
#include <openssl/opensslconf.h>
#if !defined(OPENSSL_THREADS)
    #error "openssl lacks threading support"
#endif

using namespace atakmap::commoncommo::impl;




/*******************************************************************/
// MeshNetCrypto


MeshNetCrypto::MeshNetCrypto(atakmap::commoncommo::CommoLogger *logger, const uint8_t *cipherKey,
                             const uint8_t *authKey) : 
#ifdef DEBUG_COMMO_CRYPTO
                             logger(logger),
#endif
                             cipherKey(),
                             authKey(),
                             cipherCtx(NULL)
{
    memcpy(this->cipherKey, cipherKey, KEY_BYTE_LEN);
    memcpy(this->authKey, authKey, KEY_BYTE_LEN);
    cipherCtx = EVP_CIPHER_CTX_new(); //XXX ret
    EVP_CIPHER_CTX_reset(cipherCtx);
}


MeshNetCrypto::~MeshNetCrypto()
{
    memset(cipherKey, 0, KEY_BYTE_LEN);
    memset(authKey, 0, KEY_BYTE_LEN);
    if (cipherCtx)
        EVP_CIPHER_CTX_free(cipherCtx);
}

void MeshNetCrypto::setCipherKey(const uint8_t *cipherKey)
{
    memcpy(this->cipherKey, cipherKey, KEY_BYTE_LEN);
}


void MeshNetCrypto::setAuthKey(const uint8_t *authKey)
{
    memcpy(this->authKey, authKey, KEY_BYTE_LEN);
}


void MeshNetCrypto::encrypt(uint8_t **data, size_t *len) COMMO_THROW (std::invalid_argument)
{
    uint8_t iv[IV_BYTE_LEN];

    size_t srcLen = *len;
    if (srcLen > INT_MAX)
        throw std::invalid_argument("Source string too long");
    int srcLenInt = (int)srcLen;
    uint8_t *srcData = *data;
    
    // Include 2 blocks extra bytes; enough for final padding plus temp 
    // padding during Update() call
    size_t outMsgSpace = srcLen + 2 * BLOCK_BYTE_LEN;
    size_t totalBufLen = outMsgSpace + IV_BYTE_LEN + HMAC_BYTE_LEN;
    uint8_t *outData = new uint8_t[totalBufLen];

    try {    
        if (outMsgSpace > INT_MAX)
            throw std::invalid_argument("Data too long");
        int outMsgLen = (int) outMsgSpace;
        
        if (RAND_bytes(iv, IV_BYTE_LEN) != 1)
            throw std::invalid_argument("Randomized init failure");
        memcpy(outData, iv, IV_BYTE_LEN);
        if (EVP_EncryptInit_ex(cipherCtx, EVP_aes_256_cbc(), 
                               NULL, cipherKey, iv) != 1)
            throw std::invalid_argument("EncInit failure");
        
        int outMsgLen0 = outMsgLen;
        if (EVP_EncryptUpdate(cipherCtx, outData + IV_BYTE_LEN, 
                          &outMsgLen, srcData, srcLenInt) != 1)
            throw std::invalid_argument("EncUp failure");

        int remains = outMsgLen0 - outMsgLen;
        if (remains < (int)BLOCK_BYTE_LEN)
            throw std::invalid_argument("buffer too small");
        if (EVP_EncryptFinal_ex(cipherCtx, outData + IV_BYTE_LEN + outMsgLen,
                                &remains) != 1)
            throw std::invalid_argument("EncFinal failure");

        outMsgLen += remains;
        outMsgLen += IV_BYTE_LEN;

        uint8_t hmac_buf[EVP_MAX_MD_SIZE];
        unsigned int hmac_len = EVP_MAX_MD_SIZE;
        if (!HMAC(EVP_sha256(), authKey, KEY_BYTE_LEN, outData, 
                  outMsgLen,
                  hmac_buf, &hmac_len))
            throw std::invalid_argument("Unexpected hmac error");
        if (hmac_len != HMAC_BYTE_LEN)
            throw std::invalid_argument("Unexpected hmac output length");
        memcpy(outData + outMsgLen, hmac_buf, HMAC_BYTE_LEN);
        outMsgLen += HMAC_BYTE_LEN;

        *data = outData;
        *len = (size_t)outMsgLen;
        
#ifdef DEBUG_COMMO_CRYPTO
        char cbuf[4];
        std::string s;
        for (size_t i = 0; i < *len; ++i) {
            sprintf(cbuf, "%02x", outData[i]);
            s.append(cbuf);
        }
        InternalUtils::logprintf(logger, CommoLogger::LEVEL_ERROR, "dbgout: %s", s.c_str());
#endif
        
    } catch (std::invalid_argument &e) {
        EVP_CIPHER_CTX_reset(cipherCtx);
        delete[] outData;
        throw e;
    }
    EVP_CIPHER_CTX_reset(cipherCtx);
}


bool MeshNetCrypto::decrypt(uint8_t **data, size_t *len)
{
    size_t srcLen = *len;
    uint8_t *srcData = *data;

    if (srcLen < IV_BYTE_LEN + HMAC_BYTE_LEN) {
#ifdef DEBUG_COMMO_CRYPTO
        InternalUtils::logprintf(logger, CommoLogger::LEVEL_DEBUG, 
                                 "Decrypt fail - length wrong");
#endif
        return false;
    }
    
    if (srcLen > INT_MAX) {
#ifdef DEBUG_COMMO_CRYPTO
        InternalUtils::logprintf(logger, CommoLogger::LEVEL_DEBUG, 
                                 "Decrypt fail - length too long");
#endif
        return false;
    }
    int srcMsgLen = (int)srcLen - HMAC_BYTE_LEN;

    
    // Check hmac
    uint8_t hmac_buf[EVP_MAX_MD_SIZE];
    unsigned int hmac_len = EVP_MAX_MD_SIZE;
    if (!HMAC(EVP_sha256(), authKey, KEY_BYTE_LEN, srcData, 
              srcMsgLen, hmac_buf, &hmac_len)) {
#ifdef DEBUG_COMMO_CRYPTO
        InternalUtils::logprintf(logger, CommoLogger::LEVEL_DEBUG, 
                                 "Decrypt fail - HMAC computation failed");
#endif
        return false;
    }

    if (hmac_len != HMAC_BYTE_LEN) {
#ifdef DEBUG_COMMO_CRYPTO
        InternalUtils::logprintf(logger, CommoLogger::LEVEL_DEBUG, 
                                 "Decrypt fail - HMAC length wrong");
#endif
        return false;
    }
    if (memcmp(hmac_buf, srcData + srcMsgLen, HMAC_BYTE_LEN) != 0) {
#ifdef DEBUG_COMMO_CRYPTO
        InternalUtils::logprintf(logger, CommoLogger::LEVEL_DEBUG, 
                                 "Decrypt fail - HMAC mismatch");
#endif
        return false;
    }

    // Now decrypt
    if (EVP_DecryptInit_ex(cipherCtx, EVP_aes_256_cbc(), NULL,
                       cipherKey, srcData) != 1) {
#ifdef DEBUG_COMMO_CRYPTO
        InternalUtils::logprintf(logger, CommoLogger::LEVEL_DEBUG, 
                                 "Decrypt fail - init fail");
#endif
        return false;
    }
    srcMsgLen -= IV_BYTE_LEN;
    srcData += IV_BYTE_LEN;

    // Include 2 blocks extra bytes; enough for final call plus temp 
    // padding during Update() call
    int outBufLen = srcMsgLen + 2 * BLOCK_BYTE_LEN;
    uint8_t *outData = new uint8_t[outBufLen];
    int outLen = outBufLen;

    try {
        if (EVP_DecryptUpdate(cipherCtx, outData, &outLen, 
                              srcData, srcMsgLen) != 1)
            throw std::invalid_argument("Decrypt fail - decrypt update failure");
        int remains = outBufLen - outLen;
        if (remains < (int)BLOCK_BYTE_LEN)
            throw std::invalid_argument("Decrypt fail - not enough trailing buf space");
        int rc = EVP_DecryptFinal_ex(cipherCtx, outData + outLen, &remains);
        if (rc != 1)
            throw std::invalid_argument("Decrypt fail - finalize error");
        outLen += remains;
    } catch (std::invalid_argument &e) {
        // Suppress unused var warning when debug is off
        (void)e;
        EVP_CIPHER_CTX_reset(cipherCtx);
#ifdef DEBUG_COMMO_CRYPTO
        InternalUtils::logprintf(logger, CommoLogger::LEVEL_DEBUG, 
                                 "Decrypt fail - decrypt update failure %s", e.what());
#endif
        return false;
    }
    EVP_CIPHER_CTX_reset(cipherCtx);
    
    *data = outData;
    *len = (size_t)outLen;
    return true;
}



/*******************************************************************/
// CryptoUtil


CryptoUtil::CryptoUtil(atakmap::commoncommo::CommoLogger *logger) : logger(logger)
{

}

char *CryptoUtil::generateKeyCryptoString(const char *password, const int keyLen)
{
    EVP_PKEY *pkey = EVP_PKEY_new();
    if (!pkey) {
        logerr("EVP_PKEY_new failed!");
        return NULL;
    }

    BN_ULONG e = RSA_F4;
    BIGNUM *bne = BN_new();
    BN_set_word(bne,e);

    RSA *rsa = RSA_new();
    if (!RSA_generate_key_ex(rsa, keyLen, bne, NULL)) {
        logerr("RSA_generate_key_ex failed!");
        EVP_PKEY_free(pkey);
        BN_free(bne);
        return NULL;
     }
    
    if (!EVP_PKEY_assign_RSA(pkey, rsa)) {
        logerr("EVP_PKEY_assign failed!");
        RSA_free(rsa);
        EVP_PKEY_free(pkey);
        BN_free(bne);
        return NULL;
    }
    
    std::string key;
    bool ok = pkeyToString(key, pkey, password);

    EVP_PKEY_free(pkey);
    // ^ frees RSA
    BN_free(bne);
    
    return ok ? strdup(key.c_str()) : NULL;
}


char *CryptoUtil::generateCSRCryptoString(const char **entryKeys,
                                  const char **entryValues,
                                  size_t nEntries,
                                  const char* pem,
                                  const char* password)
{
    X509_REQ *req;
    X509_NAME *subj = NULL;
    EVP_MD *digest;
    
    EVP_PKEY *pkey = stringToPkey(pem, password);
    if (!pkey)
        return NULL;
    
    req = X509_REQ_new();
    if (!req) {
        logerr("X509_REQ_new failed!");
        EVP_PKEY_free(pkey);
        return NULL;
    }
        
    X509_REQ_set_pubkey(req, pkey);

    subj = X509_NAME_new();
    if (!subj) {
        logerr("X509_NAME_new failed!");
        EVP_PKEY_free(pkey);
        X509_REQ_free(req);
        return NULL;
    }

    for (size_t i = 0; i < nEntries; ++i) {
        const char *key = entryKeys[i];
        const char *value = entryValues[i];
        
        int nid = OBJ_txt2nid(key);
        if (nid == NID_undef) { 
            logerr("OBJ_txt2nid failed!");
            EVP_PKEY_free(pkey);
            X509_REQ_free(req);
            X509_NAME_free(subj);
            return NULL;
        } 
        
        X509_NAME_ENTRY *ent = X509_NAME_ENTRY_create_by_NID(NULL, 
                         nid, MBSTRING_ASC, 
                         (unsigned char*)value, -1);
        if (!ent) {
            logerr("X509_NAME_ENTRY_create_by_NID failed!");
            EVP_PKEY_free(pkey);
            X509_REQ_free(req);
            X509_NAME_free(subj);
            return NULL;
        }

        if (X509_NAME_add_entry(subj, ent, -1, 0) != 1) {
            logerr("X509_NAME_add_entry failed!");

            X509_NAME_ENTRY_free(ent);
            EVP_PKEY_free(pkey);
            X509_REQ_free(req);
            X509_NAME_free(subj);
            return NULL;
        }
        
        X509_NAME_ENTRY_free(ent);
    } 

    if (X509_REQ_set_subject_name(req, subj) != 1) {
        logerr("X509_REQ_set_subject_name failed!");

        EVP_PKEY_free(pkey);
        X509_REQ_free(req);
        X509_NAME_free(subj);
        return NULL;
    }

    digest = (EVP_MD *)EVP_sha1(); 
    if (!(X509_REQ_sign(req, pkey, digest))) {
        logerr("X509_REQ_sign failed!");

        EVP_PKEY_free(pkey);
        X509_REQ_free(req);
        X509_NAME_free(subj);
        return NULL;
    }

    std::string csr;
    bool ret = csrToString(csr, req);
    EVP_PKEY_free(pkey);
    X509_REQ_free(req);
    X509_NAME_free(subj);
    
    return ret ? strdup(csr.c_str()) : NULL;
}



    
char *CryptoUtil::generateKeystoreCryptoString(const char *certPem,
                           const char **caPem, size_t nCa,
                           const char *pkeyPem, const char *password, 
                           const char *friendlyName)
{
    X509 *cert = stringToCert(certPem);
    if (!cert)
        return NULL;

    EVP_PKEY *pkey = stringToPkey(pkeyPem, password);
    if (!pkey) {
        X509_free(cert);
        return NULL;
    }
    
    STACK_OF(X509) *caStack = sk_X509_new_null();
    if (!caStack) {
        logerr("sk_X509_new_null failed!");
        EVP_PKEY_free(pkey);
        X509_free(cert);
        return NULL;
    }
    for (size_t i = 0; i < nCa; ++i) { 
        X509 *ca = stringToCert(caPem[i]);
        if (!ca) {
            sk_X509_pop_free(caStack, X509_free);
            EVP_PKEY_free(pkey);
            X509_free(cert);
            return NULL;
        }
        
        sk_X509_push(caStack, ca);
    }

    PKCS12 *pkcs12 = PKCS12_create((char *)password, (char *)friendlyName,
                                   pkey, cert, caStack, 0, 0, 0, 0, 0);
    if (!pkcs12) {
        logerr("PKCS12_create failed!");
        sk_X509_pop_free(caStack, X509_free);
        EVP_PKEY_free(pkey);
        X509_free(cert);
        return NULL;
    }
   
    BIO *bio = BIO_new(BIO_s_mem());
    i2d_PKCS12_bio(bio, pkcs12);
    BUF_MEM *buffer;
    BIO_get_mem_ptr(bio, &buffer);
        
    char *p12 = NULL;
    if (buffer->length >= 0 && buffer->length <= INT_MAX) {
        int intLen = (int)buffer->length;
        p12 = toBase64(buffer->data, intLen);
    }
    
    BIO_free(bio);
    PKCS12_free(pkcs12);
    sk_X509_pop_free(caStack, X509_free);
    EVP_PKEY_free(pkey);
    X509_free(cert);

    return p12;
}


void CryptoUtil::freeCryptoString(char *cryptoString)
{
    free(cryptoString);
}


void CryptoUtil::logerr(const char *msg)
{
    char errbuf[2048];
    ERR_error_string_n(ERR_get_error(), errbuf, 2048);
    InternalUtils::logprintf(logger, CommoLogger::LEVEL_ERROR, 
                             "%s (%s)", msg, errbuf);
}




bool CryptoUtil::pkeyToString(std::string &str, EVP_PKEY *pkey,
                              const char *password)
{
    int rc = 0;
    
    BIO *bio = BIO_new(BIO_s_mem());
    if (bio == NULL) {
        logerr("BIO_new failed!");
        return false;
    }    
    
    size_t passLen = strlen(password);
    if (passLen > INT_MAX) {
        logerr("password too long!");
        return false;
    }
    int passLenInt = (int)passLen;
    rc = PEM_write_bio_PrivateKey(bio, pkey, EVP_aes_192_cbc(), 
                                  (unsigned char*)password, 
                                  passLenInt, NULL, NULL);
    if (rc != 1) {
        logerr("PEM_write_bio_PrivateKey failed!");
        BIO_free(bio);
        return false;
    }

    BUF_MEM* mem = NULL;
    BIO_get_mem_ptr(bio, &mem);
    if (!mem || !mem->data || !mem->length) {
        logerr("BIO_get_mem_ptr failed!");
        BIO_free(bio);
        return false;
    }

    std::string pem(mem->data, mem->length);
    
    BIO_free(bio);
    str = pem;
    return true;
}


X509 *CryptoUtil::stringToCert(const char *pem)
{
    BIO *bio = BIO_new_mem_buf((char *)pem, -1);
    X509 *cert = PEM_read_bio_X509(bio, NULL, 0, NULL);
    if (!cert) {
        logerr("PEM_read_bio_X509 failed!");
    }

    BIO_free(bio);
    return cert;
}

EVP_PKEY *CryptoUtil::stringToPkey(const char *pem, const char *password)
{
    BIO *bio = BIO_new_mem_buf((char *)pem, -1);
    EVP_PKEY *pkey = PEM_read_bio_PrivateKey(bio, NULL, 0, (char*)password);
    if (pkey == NULL) {
        logerr("PEM_read_bio_PrivateKey failed!");
    }

    BIO_free(bio);
    return pkey;
}

char *CryptoUtil::toBase64(char *str, int length)
{
    BIO *b64 = BIO_new(BIO_f_base64());
    BIO *bio = BIO_new(BIO_s_mem());
    bio = BIO_push(b64, bio);

    BIO_set_flags(bio, BIO_FLAGS_BASE64_NO_NL); 
    (void)BIO_set_close(bio, BIO_CLOSE);
    BIO_write(bio, str, length);
    (void)BIO_flush(bio);
    
    BUF_MEM *buffer;
    BIO_get_mem_ptr(bio, &buffer);
    
    char *output = (char *)malloc(buffer->length + 1);
    memcpy(output, buffer->data, buffer->length);
    output[buffer->length] = '\0';
    
    BIO_free_all(bio);
    return output;
}

bool CryptoUtil::fromBase64(const char *b64, char **poutbuf, size_t *poutlen)
{
    size_t b64LenSizeT = strlen(b64);
    if (b64LenSizeT > INT_MAX)
        return false;
    int b64Len = (int)b64LenSizeT;
    
    size_t outbuflen = b64LenSizeT;
    char *outbuf = (char *)malloc(outbuflen);
    
    // cast needed for ancient openssl
    // new_mem_buf uses this read only so it's "ok"
    BIO *bio = BIO_new_mem_buf((void *)b64, b64Len);
    BIO *bio64 = BIO_new(BIO_f_base64());
    bio = BIO_push(bio64, bio);
    
    BIO_set_flags(bio, BIO_FLAGS_BASE64_NO_NL);
    (void)BIO_set_close(bio, BIO_CLOSE);
    outbuflen = BIO_read(bio, outbuf, b64Len);

    BIO_free_all(bio);
    
    *poutbuf = outbuf;
    *poutlen = outbuflen;
    return true;
}

bool CryptoUtil::csrToString(std::string &str, X509_REQ *csr)
{
    int rc = 0;

    BIO *bio = BIO_new(BIO_s_mem());

    rc = PEM_write_bio_X509_REQ(bio, csr);
    if (rc != 1) {
        logerr("PEM_write_bio_X509_REQ failed!");
        BIO_free(bio);
        return false;
    }

    BUF_MEM *mem = NULL;
    BIO_get_mem_ptr(bio, &mem);
    if (!mem || !mem->data || !mem->length) {
        logerr("BIO_get_mem_ptr failed!");
        BIO_free(bio);
        return false;
    }

    std::string pem(mem->data, mem->length);
    BIO_free(bio);
    str = pem;
    return true;
}


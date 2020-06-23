
typedef void (*pfnLogger)(const char* msg);
void setLogger(pfnLogger);

int openOrCreateDatabase(const char* filename, const char* password);
int closedb();
int getCertificate(const char* type, void** certificate, int* size, char** hash);
int saveCertificate(const char* type, const void* certificate, const int size, const char* hash);
int getCertificateForServer(const char* type, const char* server, void** certificate, int* size, char** hash);
int saveCertificateForServer(const char* type, const char* server, const void* certificate, const int size, const char* hash);
int deleteCertificate(const char* type);
int deleteCertificateForServer(const char* type, const char* server);


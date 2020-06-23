
typedef void (*pfnLogger)(const char* msg);
void setLogger(pfnLogger);

int openOrCreateDatabase(const char* filename, const char* password);
int closedb();
int getDistinctSitesAndTypes(char*** sites, char*** types, int* size);
int getCredentials(const char* type, const char* site, char** username, char** password);
int saveCredentials(const char* type, const char* site, const char* username, const char* password, const long long expires);
int invalidate(const char* type, const char* site);
int deleteExpiredCredentials(long long time);


#include "db/DatabaseInformation.h"

using namespace TAK::Engine::DB;
using namespace TAK::Engine::Util;

DatabaseInformation::DatabaseInformation(const char* uri) : uri_(uri), passphrase_(nullptr), options_(DATABASE_OPTIONS_NONE) {}

DatabaseInformation::DatabaseInformation(const char* uri, const char* passphrase, int options)
    : uri_(uri), passphrase_(passphrase), options_(options) {}

TAKErr DatabaseInformation::getUri(const char** value) const NOTHROWS {
    if (value)
        *value = this->uri_;
    return TE_Ok;
}

TAKErr DatabaseInformation::getPassphrase(const char** value) const NOTHROWS {
    if (value)
        *value = this->passphrase_;
    return TE_Ok;
}

TAKErr DatabaseInformation::getOptions(int* value) const NOTHROWS {
    if (value)
        *value = this->options_;
    return TE_Ok;
}
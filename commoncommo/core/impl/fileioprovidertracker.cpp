#include "fileioprovidertracker.h"

#include <algorithm>

using namespace atakmap::commoncommo;
using namespace atakmap::commoncommo::impl;

FileIOProviderTracker::FileIOProviderTracker():
    plaintextProvider(std::make_shared<PlainTextFileIOProvider>()){
    currentProvider = plaintextProvider;
}

void FileIOProviderTracker::deregisterProvider(const FileIOProvider& provider) {
    auto providerIter = getProviderIter(provider);

    if(providerIter != providerDeque.end()){
        providerDeque.erase(providerIter);
    }


    if(providerDeque.size() == 0)
        currentProvider = plaintextProvider;
    else
        currentProvider = providerDeque.front();
}

void FileIOProviderTracker::registerProvider(std::shared_ptr<FileIOProvider>& provider) {
    if(isProviderInTracker(*provider))
        return;

    providerDeque.push_front(provider);
    currentProvider = provider;
}

std::shared_ptr<FileIOProvider> FileIOProviderTracker::getCurrentProvider() const {
    return currentProvider;
}

bool FileIOProviderTracker::isProviderInTracker(const FileIOProvider& provider) const{
    return getProviderIter(provider) != providerDeque.end();
}

std::deque<std::shared_ptr<FileIOProvider>>::const_iterator FileIOProviderTracker::getProviderIter(const FileIOProvider& provider) const{
    for(auto it = providerDeque.cbegin(); it != providerDeque.cend(); it++) {
        if(&provider == (*it).get())
            return it;
    }
    return providerDeque.cend();
}

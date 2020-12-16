#ifndef IMPL_FILEIOPROVIDERTRACKER_H_
#define IMPL_FILEIOPROVIDERTRACKER_H_

#include "commoutils.h"
#include "fileioprovider.h"
#include "plaintextfileioprovider.h"
#include <stdio.h>
#include <deque>
#include <memory>

namespace atakmap {
namespace commoncommo {
namespace impl
{
/**
 * Tracker for File IO providers. The tracker's current provider is the most
 * recently registered provider. When no externally supplied provider is
 * registered, a default implementation built on simple OS-provided file IO
 * is used.
 */
class COMMONCOMMO_API FileIOProviderTracker {
public:
    FileIOProviderTracker();

    /**
     * Deregisters a provider with the tracker
     *
     * @param provider the provder to deregister
     *
     * @return True of the cyclic redundancy check passes, false if otherwise.
     */
    void deregisterProvider(const FileIOProvider& provider);

    /**
     * Deregisters a provider with the tracker
     *
     * @param provider the provder to register
     */
    void registerProvider(std::shared_ptr<FileIOProvider>& provider);

    /**
     * Gets the Current Provider from the tracker
     *
     * @returns the provider
     */
    std::shared_ptr<FileIOProvider> getCurrentProvider() const;

private:
    /**
     * method which checks whether or not we have a provider
     *
     * @param provider the provider to check for
     *
     * @returns true iff the provider is found in the deque
     */
    bool isProviderInTracker(const FileIOProvider& provider) const;

    /**
     * method which gets a deque iterator to a provider with the
     * given provider
     *
     * @param provider the provider to check for
     *
     * @returns the deque iterator to the provider,
     *   end if it doesn't exist
     */
    std::deque<std::shared_ptr<FileIOProvider>>::const_iterator getProviderIter(const FileIOProvider& provider) const;

private:
    std::shared_ptr<PlainTextFileIOProvider> plaintextProvider;
    std::shared_ptr<FileIOProvider> currentProvider;
    std::deque<std::shared_ptr<FileIOProvider>> providerDeque;
};

}
}
}


#endif /* IMPL_FILEIOPROVIDERTRACKER_H_ */

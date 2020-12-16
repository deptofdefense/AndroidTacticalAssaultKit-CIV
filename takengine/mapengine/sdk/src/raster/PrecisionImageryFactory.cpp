
#include <map>
#include <functional>
#include <string>
#include <string>
#include "raster/PrecisionImageryFactory.h"
#include "util/CopyOnWrite.h"

using namespace TAK::Engine::Util;
using namespace TAK::Engine::Raster;

namespace {
	class PrecisionImagerySpiRegistry {
	public:
		TAKErr registerSpi(const std::shared_ptr<PrecisionImagerySpi> &spiPtr) NOTHROWS;
		TAKErr unregisterSpi(const std::shared_ptr<PrecisionImagerySpi> &spiPtr) NOTHROWS;
		TAKErr create(PrecisionImageryPtr &result, const char *URI, const char *hint) const NOTHROWS;
		TAKErr isSupported(const char *URI, const char *hint) const NOTHROWS;

	private:
		std::multimap<std::string, std::shared_ptr<PrecisionImagerySpi>> hint_sorted;
		std::multimap<int, std::shared_ptr<PrecisionImagerySpi>, std::greater<int>> priority_sorted;
	};

	static CopyOnWrite<PrecisionImagerySpiRegistry> &sharedPrecisionImageryRegistry();
}

TAKErr TAK::Engine::Raster::PrecisionImageryFactory_register(const std::shared_ptr<PrecisionImagerySpi> &spi) NOTHROWS {
	return sharedPrecisionImageryRegistry().invokeWrite(&PrecisionImagerySpiRegistry::registerSpi, spi);
}

TAKErr TAK::Engine::Raster::PrecisionImageryFactory_unregister(const std::shared_ptr<PrecisionImagerySpi> &spi) NOTHROWS {
	return sharedPrecisionImageryRegistry().invokeWrite(&PrecisionImagerySpiRegistry::unregisterSpi, spi);
}

TAKErr TAK::Engine::Raster::PrecisionImageryFactory_create(PrecisionImageryPtr &result, const char *URI, const char *hint) NOTHROWS {
	return sharedPrecisionImageryRegistry().read()->create(result, URI, hint);
}

TAKErr TAK::Engine::Raster::PrecisionImageryFactory_isSupported(const char *URI, const char *hint) NOTHROWS {
	return sharedPrecisionImageryRegistry().read()->isSupported(URI, hint);
}

namespace {
	CopyOnWrite<PrecisionImagerySpiRegistry> &sharedPrecisionImageryRegistry() {
		static CopyOnWrite<PrecisionImagerySpiRegistry> registry;
		return registry;
	}

	TAKErr PrecisionImagerySpiRegistry::registerSpi(const std::shared_ptr<PrecisionImagerySpi> &spiPtr) NOTHROWS {

		if (!spiPtr)
			return TE_InvalidArg;
		const char *type = spiPtr->getType();
		if (!type)
			return TE_InvalidArg;

		TAKErr code = TE_Ok;
		TE_BEGIN_TRAP() {
			hint_sorted.insert(std::make_pair(type, spiPtr));
			priority_sorted.insert(std::make_pair(spiPtr->getPriority(), spiPtr));
		}
		TE_END_TRAP(code);
		return code;
	}

	template <typename Container, typename K>
	void eraseIf(Container &m, const K &key, const std::shared_ptr<PrecisionImagerySpi> &spi) {
		auto range = m.equal_range(key);
		for (auto it = range.first; it != range.second; ++it) {
			if (it->second == spi) {
				m.erase(it);
				break;
			}
		}
	}

	TAKErr PrecisionImagerySpiRegistry::unregisterSpi(const std::shared_ptr<PrecisionImagerySpi> &spiPtr) NOTHROWS {

		if (!spiPtr)
			return TE_InvalidArg;
		const char *type = spiPtr->getType();
		if (!type)
			return TE_InvalidArg;

		eraseIf(this->priority_sorted, spiPtr->getPriority(), spiPtr);
		eraseIf(this->hint_sorted, type, spiPtr);

		return TE_Ok;
	}

	template <typename Iter>
	TAKErr createImpl(Iter begin, Iter end, PrecisionImageryPtr &result, const char *URI) NOTHROWS {
		while (begin != end) {
			TAKErr code;
			if ((code = begin->second->create(result, URI)) != TE_Unsupported) {
				return code;
			}
			++begin;
		}
		return TE_Unsupported;
	}

	template <typename Iter>
	TAKErr isSupportedImpl(Iter begin, Iter end, const char *URI) NOTHROWS {
		while (begin != end) {
			TAKErr code;
			if ((code = begin->second->isSupported(URI)) != TE_Unsupported) {
				return code;
			}
			++begin;
		}
		return TE_Unsupported;
	}

	TAKErr PrecisionImagerySpiRegistry::create(PrecisionImageryPtr &result, const char *URI, const char *hint) const NOTHROWS {
		if (hint) {
			auto range = hint_sorted.equal_range(hint);
			return ::createImpl(range.first, range.second, result, URI);
		}
		return ::createImpl(priority_sorted.begin(), priority_sorted.end(), result, URI);
	}

	TAKErr PrecisionImagerySpiRegistry::isSupported(const char *URI, const char *hint) const NOTHROWS {
		if (hint) {
			auto range = hint_sorted.equal_range(hint);
			return isSupportedImpl(range.first, range.second, URI);
		}
		return isSupportedImpl(priority_sorted.begin(), priority_sorted.end(), URI);
	}

}
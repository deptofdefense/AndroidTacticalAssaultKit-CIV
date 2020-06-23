#pragma once

namespace TAK {
    namespace Commo {

        public interface class IContactPresenceListener
        {
        public:
            virtual void contactAdded(Platform::String ^c);
            virtual void contactRemoved(Platform::String ^c);
        };

    }
}
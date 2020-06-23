#pragma once

namespace TAK {
    namespace Commo {

        public enum class Level {
            LevelVerbose,
            LevelDebug,
            LevelWarning,
            LevelInfo,
            LevelError,
        };

        /**
        * Interface used to log messages from the Commo library.
        */
        public interface class ICommoLogger {
        public:

            /**
            * log a message of the specified severity level.
            * This callback will be invoked from multiple different threads;
            * an implementation must be able to handle this properly.
            */
            virtual void log(Level level, Platform::String ^message);
        };
    }
}
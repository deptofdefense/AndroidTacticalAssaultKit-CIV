#ifndef COMMOLOGGER_CLI_H_
#define COMMOLOGGER_CLI_H_

namespace TAK {
    namespace Commo {

        /**
        * Interface used to log messages from the Commo library.
        */
        public interface class ICommoLogger {
        public:
            enum class Level {
                LevelVerbose,
                LevelDebug,
                LevelWarning,
                LevelInfo,
                LevelError,
            };

            /**
                * log a message of the specified severity level.
                * This callback will be invoked from multiple different threads;
                * an implementation must be able to handle this properly.
                */
            virtual void Log(Level level, System::String ^message);
        };


    }
}


#endif /* COMMOLOGGER_H_ */

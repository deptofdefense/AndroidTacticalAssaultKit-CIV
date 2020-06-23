#ifndef CONTACTUID_CLI_H_
#define CONTACTUID_CLI_H_


namespace TAK {
    namespace Commo {

        public interface class IContactPresenceListener
        {
        public:
            virtual void ContactAdded(const System::String ^c);
            virtual void ContactRemoved(const System::String ^c);
        };

    }
}


#endif /* CONTACT_H_ */

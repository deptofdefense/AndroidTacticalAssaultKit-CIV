#ifndef INPUTSTREAM_READER_H
#define INPUTSTREAM_READER_H

#include <jni.h>

#include "DataReader.h"

using namespace std;

class InputStreamReader : public DataReader{
private:
    bool eofReached;
    long long currentCount;
    long long lastAmountRead;
    jobject* backingStream;
    JNIEnv* env;

    jclass inputStreamClass;
    jmethodID inputStreamReadID;
    jmethodID inputStreamSkipID;

    bool initializationSuccessful;
public:
    InputStreamReader(JNIEnv*, jobject*);
    virtual ~InputStreamReader();

    void read(unsigned char*, long long);
    void seek(long long, STARTING_POINT);
    long long tell();
    bool eof();
    long long count();

    bool isInitialized();
};


#endif // INPUTSTREAM_READER_H

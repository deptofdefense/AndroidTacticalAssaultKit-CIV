#include "InputStreamReader.h"

#include <iostream>

InputStreamReader::InputStreamReader(JNIEnv *env, jobject *stream) :
                    eofReached(false), currentCount(0),
                    lastAmountRead(0), initializationSuccessful(0)
{
    this->env = env;
    this->backingStream = stream;

    inputStreamClass = (jclass) env->NewWeakGlobalRef(
                env->FindClass("java/io/InputStream"));

    if(!inputStreamClass){
        initializationSuccessful = false;
        return;
    }

    inputStreamReadID = env->GetMethodID(inputStreamClass,
                                                "read", "([BII)I");

    if(!inputStreamReadID){
        initializationSuccessful = false;
        return;
    }

    inputStreamSkipID = env->GetMethodID(inputStreamClass,
                                                "skip", "(J)J");
    if(!inputStreamSkipID){
        initializationSuccessful = false;
        return;
    }

    initializationSuccessful = true;
}

InputStreamReader::~InputStreamReader(void){
    env->DeleteWeakGlobalRef(inputStreamClass);

    this->env = NULL;
    this->backingStream = NULL;
}

void InputStreamReader::read(unsigned char* dest, long long numBytesToRead){
    long increment;
    if(numBytesToRead > 2147483647){
        std::cerr << "WARNING: InputStreamReader can only read up to 2147483647 bytes per call" << std::endl;
        increment = 2147483647;
    }else{
        increment = (long) numBytesToRead;
    }

    jbyteArray buffer = env->NewByteArray(increment);
    long totalRead = 0;
    long amountToReadTmp = 0;
    while(totalRead < increment){
        amountToReadTmp = increment - totalRead;
        jint amountRead = env->CallIntMethod(*backingStream, inputStreamReadID, buffer, totalRead, amountToReadTmp);

        totalRead = totalRead + amountRead;
        currentCount += amountRead;

        if(amountRead < 0){
            // EOF reached
            eofReached = true;
            std::cerr << "WARNING: InputStreamReader reached end of file." << std::endl;
            lastAmountRead = totalRead;
            return;
        }
    }
    env->GetByteArrayRegion(buffer, 0, increment, (jbyte *) dest);
    env->DeleteLocalRef(buffer);

    lastAmountRead = totalRead;
}


void InputStreamReader::seek(long long amountToSeek, STARTING_POINT startingPoint){
    if(startingPoint != CURRENT){
        std::cerr << "WARNING: InputStreamReader can only handle CURRENT as starting point." << std::endl;
        return;
    }

    long long totalSkipped = 0;
    long long amountToSeekTmp = 0;
    while(totalSkipped < amountToSeek){
        amountToSeekTmp = amountToSeek - totalSkipped;
        jlong amountSkipped = env->CallLongMethod(
                                            *backingStream, inputStreamSkipID,
                                            (jlong) amountToSeekTmp);

        totalSkipped = totalSkipped + amountSkipped;
        currentCount += amountSkipped;

        if(amountSkipped < 0){
            // EOF reached
            eofReached = true;
            std::cerr << "WARNING: InputStreamReader reached end of file." << std::endl;
            return;
        }
    }
}

long long InputStreamReader::tell(){
    return currentCount;
}

bool InputStreamReader::eof(){
    return eofReached;
}

long long InputStreamReader::count() {
    return lastAmountRead;
}

bool InputStreamReader::isInitialized(){
    return initializationSuccessful;
}

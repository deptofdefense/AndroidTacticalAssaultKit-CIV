#pragma once
#include "util/DataInput2.h"
#include "util/DataOutput2.h"

namespace TAK {
namespace Engine {
namespace Formats {
namespace QuantizedMesh {

    struct Indices {

        int length;
        bool is32bit;

        std::unique_ptr<Util::FileInput2> buffer;
        // std::unique_ptr<Util::DynamicOutput> indexBuffer;

        unsigned int *indexArray32;
        unsigned short *indexArray16;

        Indices()
            : length(0),
              is32bit(false) {

        }

        Indices(int length, bool is32bit, Util::FileInput2 *buffer) : Indices(length, is32bit, false, buffer){
        }
        
        Indices(int length, bool is32bit, bool compressed, Util::FileInput2 *buffer) {
            this->length = length;
            this->is32bit = is32bit;
            this->indexArray32 = new unsigned int[length];
            this->indexArray16 = new unsigned short[length];

            if(compressed) {
                int highest = 0;
                for( int i=0; i < length; ++i) {
                    if(is32bit) {
                        int code;
                        buffer->readInt(&code);
                        int index = highest - code;
                        
                        if(code == 0)
                            ++highest;
                        
                        // indexBuffer->writeInt(index);
                        indexArray32[i] = static_cast<unsigned>(index);
                    }
                    else {
                        short code;
                        buffer->readShort(&code);
                        unsigned short codes = static_cast<unsigned>(code);
                        short index = highest - codes;
                        if(codes == 0) 
                            ++highest;

                        // indexBuffer->writeShort(index);
                        unsigned short t = static_cast<unsigned>(index);
                        indexArray16[i] = t;
                    }
                }
            }
            //not compressed
            else {
                for(int i=0; i<length; ++i) {
                    if(is32bit) {
                        int value;
                        buffer->readInt(&value);
                        // indexBuffer->writeInt(value);
                        indexArray32[i] = static_cast<unsigned>(value);
                    }
                    else {
                        short value;
                        buffer->readShort(&value);
                        // indexBuffer->writeShort(value);
                        indexArray16[i] = static_cast<unsigned>(value);
                    }
                }
            }
        }

        int get(int i) {
            
            // indexBuffer->reset();
            // indexBuffer->skip(i);
            // const uint8_t* data(nullptr);
            // size_t len;
            // indexBuffer->get(&data, &len );
            //
            // return static_cast<int>(*data);
            if (this->is32bit)
                return this->indexArray32[i];
            else
                return this->indexArray16[i];
            return 0;
        }
        
    };

}
}
}
}

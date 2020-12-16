#pragma once
#include "Indices.h"
#include "VertexData.h"

namespace TAK {
namespace Engine {
namespace Formats {
namespace QuantizedMesh {

    struct EdgeIndicies {

        static const int NORTH = 0;
        static const int EAST = 1;
        static const int SOUTH = 2;
        static const int WEST = 3;

        long totalSize;

        Indices edges[4];

        EdgeIndicies(VertexData *vertexData, bool is32bit, Util::FileInput2 *buffer) {

            int south;
            int east;
            int north;
            int west;
            
            buffer->readInt(&west);
            edges[WEST] = Indices(west, is32bit, buffer);
            
            buffer->readInt(&south);
            edges[SOUTH] = Indices(south, is32bit, buffer);
            
            buffer->readInt(&east);
            edges[EAST] = Indices(east, is32bit, buffer);
            
            buffer->readInt(&north);
            edges[NORTH] = Indices(north, is32bit, buffer);
            
        }

        Indices* get(int edge) {
            return &edges[edge];
        }
    };

}
}
}
}

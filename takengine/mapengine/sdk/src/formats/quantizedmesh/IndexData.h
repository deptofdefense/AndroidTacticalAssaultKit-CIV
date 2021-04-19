#pragma once

#include "TriangleIndices.h"
#include "math/Utils.h"
#include "util/Quadtree.h"

#include "math/Triangle.h"

namespace TAK {
namespace Engine {
namespace Formats {
namespace QuantizedMesh {

struct IndexData {
    long totalSize;
    int triangleCount;
    bool is32bit;

    int get(int i) {
        return indices->get(i);
    }
    
private:
    std::unique_ptr<Indices> indices;
    
    static const int MAX_RANGE = 32767;
    static const int MAX_LEVEL = 15;

    std::vector<int> sizes;
    
    // std::unique_ptr<atakmap::util::Quadtree<std::list<int>>> quadtree;
    // std::vector<std::vector<std::vector<std::vector<int>>>> quadtree;

    std::vector<std::vector<std::vector<std::vector<int>>>> quadtree;

public:
    
    IndexData(VertexData* vData, Util::FileInput2* buffer) {

        sizes = std::vector<int>(TriangleIndices::LEVEL_COUNT);
        for(int i=0; i < TriangleIndices::LEVEL_COUNT; i++) {
            sizes[i] = 1 << (MAX_LEVEL - i);
        }

        buffer->readInt(&triangleCount);
        triangleCount = static_cast<unsigned>(triangleCount);
        
        is32bit = vData->vertexCount > 65536;
        int length = triangleCount * 3;

        indices = std::make_unique<Indices>(length, is32bit, true, buffer);

        quadtree.resize(TriangleIndices::LEVEL_COUNT);
        for(int l = 0; l < TriangleIndices::LEVEL_COUNT; l++) {
            int m = 1 << l;
            
            auto yvector = std::vector<std::vector<int>>(m);
            auto xvector = std::vector<std::vector<std::vector<int>>>(m);
            xvector.resize(m);
            yvector.resize(m);
            
            for(int i = 0 ;i < m ; i++){
                yvector[i] = std::vector<int>();
            }
            
            for(int i=0; i < m; i++ ) {
                xvector[i] = yvector;
            }
            
            for(int i=0; i<m; i++) {
                quadtree[l].push_back(xvector[i]);
            }

        }

        int indPerTri = 0;
        int triIndex = 0;
        int minX = MAX_RANGE;
        int minY = MAX_RANGE;

        int maxX = 0;
        int maxY = 0;
        int quadtreeSize = 0;

        using namespace atakmap::math;
        
        for (int i = 0; i < length; ++i) {
            int index = indices->get(i);

            int x = vData->u[index];
            int y = vData->v[index];


            minX = min(x, minX);
            minY = min(y, minY);
            maxX = max(x, maxX);
            maxY = max(y, maxY);

            if(++indPerTri == 3) {
                int size = max(maxX - minX, maxY - minY);
                int level = static_cast<int>(ceil(log2(size)));

                int l = TriangleIndices::MAX_LEVEL - level;
                if(l >= TriangleIndices::LEVEL_COUNT)
                    l = TriangleIndices::LEVEL_COUNT -1;
                else if(l < 0)
                    l = 0;

                int d = sizes[l];
                int minTX = minX / d;
                int minTY = minY / d;
                int maxTX = maxX / d;
                int maxTY = maxY / d;

                for (int ty = minTY; ty <= maxTY; ty++) {
                    for (int tx = minTX; tx <= maxTX; tx++ ) {
                        // if(quadtree[l].size() >= tx) continue;
                        // if(quadtree[l][tx].size() >= ty) continue;
                        
                        auto ind = &quadtree[l][tx][ty];
                        ind->push_back(triIndex);
                        quadtreeSize += 4;
                    }
                }
                
                // Reset for next triangle
                minX = minY = MAX_RANGE;
                maxX = maxY = 0;
                indPerTri = 0;
                triIndex++;
            }
        }
    }

    std::vector<int> getTriangleIndices(int level, int x, int y)
    {
        if(level < TriangleIndices::MIN_LEVEL) { 
            level = TriangleIndices::MIN_LEVEL;
        }

        int l = MAX_LEVEL - level;
        int tx = x / sizes[l];
        int ty = y / sizes[l];

        if (tx < 0) tx = 0;
        else if (tx >= static_cast<int>(quadtree[l].size())) {
            int size = static_cast<int>(quadtree[l].size());
            tx = size-1;
        }

        int ysize = static_cast<int>(quadtree[l][tx].size());
        if (ty < 0) ty = 0;
        else if( ty >= ysize) {
            ty = ysize - 1;
        }

        
        return quadtree[l][tx][ty];
    }

    int getLength() {
        return indices->length;
    }
};

}
}
}
}

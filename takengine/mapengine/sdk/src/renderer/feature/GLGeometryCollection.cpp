#include "renderer/feature/GLGeometryCollection.h"

using namespace atakmap::feature;
using namespace atakmap::renderer::feature;

GLGeometryCollection::GLGeometryCollection(GeometryCollection *collection)
: GLGeometry(collection)
{
    std::pair<GeometryCollection::GeometryVector::const_iterator, GeometryCollection::GeometryVector::const_iterator> contents = collection->contents();
    size_t count = std::distance(contents.first, contents.second);
    this->geometries.reserve(count);
    while (contents.first != contents.second) {
        Geometry *geom = *contents.first;
        this->geometries.push_back(GLGeometry::createRenderer(geom));
        ++contents.first;
    }
}

GLGeometryCollection::~GLGeometryCollection() { }

GLGeometryCollection::GLGeometryIterator::GLGeometryIterator(GeometryList::iterator first,
                                                             GeometryList::iterator last)
: pos(first), end(last) { }

GLGeometryCollection::GLGeometryIterator::~GLGeometryIterator() { }

bool GLGeometryCollection::GLGeometryIterator::hasNext() {
    return this->pos != this->end;
}

GLGeometry *GLGeometryCollection::GLGeometryIterator::next() {
    ++pos;
    return *pos;
}

GLGeometry *GLGeometryCollection::GLGeometryIterator::get() {
    return *pos;
}

atakmap::port::Iterator<GLGeometry *> *GLGeometryCollection::getIterator() {
    return new GLGeometryIterator(this->geometries.begin(), this->geometries.end());
}

#ifndef ATAKMAP_UTIL_QUADTREE_H_INCLUDED
#define ATAKMAP_UTIL_QUADTREE_H_INCLUDED

#include <map>
#include <vector>

#include "math/Point.h"
#include "math/Rectangle.h"

#define MAX_NODE_DEPTH 19

namespace atakmap {
    namespace util {

        template <typename Container>
        struct QuadtreePushBackVisitor {
            static void invoke(typename Container::value_type elem, void *opaque) {
                static_cast<Container *>(opaque)->push_back(elem);
            }
        };
        
        template<typename T>
        class Quadtree {
        public:
            typedef void (* GetBoundsFunction)(const T &object, atakmap::math::Point<double> &min, atakmap::math::Point<double> &max);
        public:
            Quadtree(GetBoundsFunction function, double minX, double minY, double maxX, double maxY);
            Quadtree(GetBoundsFunction function, int nodeLimit, double minX, double minY, double maxX, double maxY);
        private:
            Quadtree(Quadtree<T> *parent, GetBoundsFunction function, int nodeLimit, double minX, double minY, double maxX, double maxY);
        private:
            void init();
        public:
            void get(double minX, double minY, double maxX, double maxY, void (* visitor)(T *, void *), void *opaque);
            size_t size(double minX, double minY, double maxX, double maxY);
            void add(T *object);
            bool remove(T *object);
            bool refresh(T *object);
            void clear();
        private:
            
            void getImpl(double minX, double minY, double maxX, double maxY,
                         void (* visitor)(T *, void *), void *opaque,
                         atakmap::math::Point<double> *objMin, atakmap::math::Point<double> *objMax);
            
            int sizeImpl(double minX, double minY, double maxX, double maxY,
                         atakmap::math::Point<double> *objMin, atakmap::math::Point<double> *objMax);
            
            void add(T *object, double minX, double minY, double maxX, double maxY);
        private :
            Quadtree<T> *parent_;
            Quadtree<T> *root_;

            /** ordered UL, UR, LR, LL */
            Quadtree<T> *children_[4];

            const double min_x_;
            const double min_y_;
            const double max_x_;
            const double max_y_;
            const double center_x_;
            const double center_y_;

            GetBoundsFunction function_;

            std::vector<T *> objects_;

            const int limit_;

            //System::Collections::template::Dictionary<T, Quadtree<T>^> ^objectToNode;
            std::map<T *, Quadtree<T> *> object_to_node_;

            int descendants_;
            int depth_;
        };
        
        //
        // Impl
        //

        template<class T>
        Quadtree<T>::Quadtree(typename Quadtree<T>::GetBoundsFunction function, double min_x, double min_y, double max_x, double max_y) :
        parent_(nullptr),
        function_(function),
        limit_(0),
        min_x_(min_x),
        min_y_(min_y),
        max_x_(max_x),
        max_y_(max_y),
        center_x_((min_x+max_x)/2),
        center_y_((min_y + max_y)/2),
        depth_(0)
        {
            init();
        }
        
        template<class T>
        Quadtree<T>::Quadtree(typename Quadtree<T>::GetBoundsFunction function, int node_limit, double min_x, double min_y, double max_x, double max_y) :
        parent_(nullptr),
        function_(function),
        limit_(node_limit),
        min_x_(min_x),
        min_y_(min_y),
        max_x_(max_x),
        max_y_(max_y),
        center_x_((min_x + max_x) / 2),
        center_y_((min_y + max_y) / 2),
        depth_(0)
        {
            init();
        }
        
        template<class T>
        Quadtree<T>::Quadtree(Quadtree<T> *parent, typename Quadtree<T>::GetBoundsFunction function, int node_limit, double min_x, double min_y, double max_x, double max_y) :
        parent_(parent),
        function_(function),
        limit_(node_limit),
        min_x_(min_x),
        min_y_(min_y),
        max_x_(max_x),
        max_y_(max_y),
        center_x_((min_x + max_x) / 2),
        center_y_((min_y + max_y) / 2),
        depth_(parent->depth_+1)
        {
            init();
        }
        
        template<class T>
        void Quadtree<T>::init() {
            if (this->parent_ == nullptr) {
            //    this->objectToNode = gcnew System::Collections::template::Dictionary<T, Quadtree<T> ^>();
                this->root_ = this;
            }
            else {
                //this->objectToNode = nullptr;
                this->root_ = this->parent_->root_;
            }
            this->descendants_ = 0;
            this->children_[0] = nullptr;
            this->children_[1] = nullptr;
            this->children_[2] = nullptr;
            this->children_[3] = nullptr;
        }
        
        template<class T>
        void Quadtree<T>::getImpl(double minX, double minY, double maxX, double maxY,
                                  void (* visitor)(T *, void *), void *opaque,
                                  atakmap::math::Point<double> *objMin, atakmap::math::Point<double> *objMax)
        {
            for (typename std::vector<T *>::iterator it = this->objects_.begin(); it != this->objects_.end(); ++it) {
                function_(*(*it), *objMin, *objMax);
                if (math::Rectangle<double>::intersects(minX, minY, maxX, maxY,
                                                  objMin->x, objMin->y, objMax->x, objMax->y)) {
                    
                    visitor(*it, opaque);
                }
            }
            for (int i = 0; i < 4; i++) {
                if (this->children_[i] == nullptr)
                    continue;
                if (math::Rectangle<double>::intersects(minX, minY,
                                                  maxX, maxY,
                                                  children_[i]->min_x_, children_[i]->min_y_,
                                                  children_[i]->max_x_, children_[i]->max_y_)) {
                    
                    children_[i]->getImpl(minX, minY, maxX, maxY, visitor, opaque, objMin, objMax);
                }
            }
        }
        
        template<class T>
        void Quadtree<T>::get(double minX, double minY, double maxX, double maxY,
                              void (* visitor)(T *, void *), void *opaque) {
            math::Point<double> min;
            math::Point<double> max;
            getImpl(minX, minY, maxX, maxY, visitor, opaque, &min, &max);
        }
        
        template<class T>
        int Quadtree<T>::sizeImpl(double minX, double minY, double maxX, double maxY,
                                  math::Point<double> *objMin, math::Point<double> *objMax) {
            int retval = 0;
            for (typename std::vector<T *>::iterator it = this->objects_.begin(); it != this->objects_.end(); ++it) {
                this->function_(*(*it), *objMin, *objMax);
                if (math::Rectangle<double>::intersects(minX, minY, maxX, maxY,
                                                  objMin->x, objMin->y, objMax->x, objMax->y)) {
                    
                    retval++;
                }
            }
            for (int i = 0; i < 4; i++) {
                if (this->children_[i] == NULL)
                    continue;
                if (children_[i]->min_x_ >= minX &&
                    children_[i]->min_y_ >= minY &&
                    children_[i]->max_x_ <= maxX &&
                    children_[i]->max_y_ <= maxY) {
                    
                    // if the ROI contains the child, add its contents plus all of
                    // its descendants
                    retval += static_cast<int>(children_[i]->objects_.size()) + children_[i]->descendants_;
                }
                else if (math::Rectangle<double>::intersects(minX, minY,
                                                       maxX, maxY,
                                                       children_[i]->min_x_, children_[i]->min_y_,
                                                       children_[i]->max_x_, children_[i]->max_y_)) {
                    
                    // if the ROI intersects the child, process the child
                    retval += children_[i]->sizeImpl(minX, minY, maxX, maxY, objMin, objMax);
                }
            }
            return retval;
        }
        
        template<class T>
        size_t Quadtree<T>::size(double minX, double minY, double maxX, double maxY)
        {
            if (minX <= this->min_x_ && minY <= this->min_y_ && maxX >= this->max_x_ && maxY >= this->max_y_) {
                return this->objects_.size() + this->descendants_;
            }
            else {
                math::Point<double> min;
                math::Point<double> max;
                return sizeImpl(minX, minY, maxX, maxY, &min, &max);
            }
        }
        
        template<class T>
        void Quadtree<T>::add(T *object) {
            math::Point<double> min(0, 0);
            math::Point<double> max(0, 0);
            
            function_(*object, min, max);
            add(object, min.x, min.y, max.x, max.y);
        }
        
        template<class T>
        void Quadtree<T>::add(T *object, double minX, double minY, double maxX, double maxY)
        {
            const double halfWidth = (this->max_x_ - this->min_x_) / 2.0;
            const double halfHeight = (this->max_y_ - this->min_y_) / 2.0;
            
            const bool preferChild = (static_cast<std::size_t>(depth_) < MAX_NODE_DEPTH && this->objects_.size() >= static_cast<std::size_t>(this->limit_));
            
            double cnx;
            double cny;
            double cxx;
            double cxy;
            for (int i = 0; i < 4; i++) {
                cnx = this->min_x_ + ((i % 2)*halfWidth);
                cxx = this->center_x_ + ((i % 2)*halfWidth);
                cny = this->min_y_ + ((i / 2)*halfHeight);
                cxy = this->center_y_ + ((i / 2)*halfHeight);
                
                // if a child contains the object, add it to the child
                if (cnx <= minX &&
                    cny <= minY &&
                    cxx >= maxX &&
                    cxy >= maxY) {
                    
                    if (preferChild || this->children_[i] != nullptr) {
                        if (this->children_[i] == nullptr)
                            this->children_[i] = new Quadtree<T>(this, this->function_, this->limit_, cnx, cny, cxx, cxy);
                        children_[i]->add(object, minX, minY, maxX, maxY);
                        return;
                    }
                }
            }
            
            this->objects_.push_back(object);
            this->root_->object_to_node_.insert(std::make_pair(object, this));
            Quadtree<T> *ancestor = this->parent_;
            while (ancestor != nullptr) {
                ancestor->descendants_++;
                ancestor = ancestor->parent_;
            }
        }
        
        template<class T>
        bool Quadtree<T>::remove(T *object)
        {
            Quadtree<T> *node = nullptr;
            auto it = root_->object_to_node_.find(object);
            if (it == root_->object_to_node_.end()) {
                return false;
            }
            node = it->second;
            root_->object_to_node_.erase(it);
            
            bool retval = false;
            auto oIt = std::find(node->objects_.begin(), node->objects_.end(), object);
            if (oIt != node->objects_.end()) {
                node->objects_.erase(oIt);
                retval = true;
            }
            
            Quadtree<T> *ancestor = node->parent_;
            while (ancestor != nullptr) {
                ancestor->descendants_--;
                ancestor = ancestor->parent_;
            }
            
            // XXX - remove node from parent on empty?
            return retval;
        }
        
        template<class T>
        bool Quadtree<T>::refresh(T *object)
        {
            Quadtree<T> *node = nullptr;
            
            auto it = root_->object_to_node_.find(object);
            if (it == root_->object_to_node_.end()) {
                return false;
            }
            node = it->second;
            //root->objectToNode.erase(it);
            node->remove(object);
            math::Point<double> objMin(0, 0);
            math::Point<double> objMax(0, 0);
            function(*object, objMin, objMax);
            if (node->min_x_ <= objMin.x &&
                node->min_y_ <= objMin.y &&
                node->max_x_ >= objMax.x &&
                node->max_y_ >= objMax.y) {
                
                node->add(object, objMin.x, objMin.y, objMax.x, objMax.y);
            }
            else {
                root_->add(object, objMin.x, objMin.y, objMax.x, objMax.y);
            }
            return true;
        }
        
        template<class T>
        void Quadtree<T>::clear() {
            objects_.clear();
            for (int i = 0; i < 4; i++)
                children_[i] = nullptr;
        }
    }
}

#endif // ATAKMAP_CPP_CLI_PRIV_QUADTREE_H_INCLUDED

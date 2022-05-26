////============================================================================
////
////    FILE:           AttributeSet.h
////
////    DESCRIPTION:    A class that provides a mapping of names to one of a set
////                    of value types.
////
////    AUTHOR(S):      scott           scott_barrett@partech.com
////
////
////    HISTORY:
////
////      DATE          AUTHOR          COMMENTS
////      ------------  --------        --------
////      Jan 15, 2015  scott           Created.
////      Feb 9, 2018   joe b.          See improvements.
////
////========================================================================////
////                                                                        ////
////    (c) Copyright 2015 PAR Government Systems Corporation.              ////
////                                                                        ////
////========================================================================////

/*
 Improvments:
    - AttributeSet has one member: unordered_map<std::string, std::shared_ptr<AttrItem>> for all items
    - Created AttrItem base and concrete types
    - Since attr items are all immutable, copying an attribute set is trivial (relies on std::shared_ptr<>).
    - Rely on built-in copy and move constructors
 */


#ifndef ATAKMAP_UTIL_ATTRIBUTE_SET_H_INCLUDED
#define ATAKMAP_UTIL_ATTRIBUTE_SET_H_INCLUDED


////========================================================================////
////                                                                        ////
////    INCLUDES AND MACROS                                                 ////
////                                                                        ////
////========================================================================////


#include <algorithm>
#include <cstdint>
#include <cstring>
#include <functional>
#include <iterator>
#include <map>
#include <memory>
#include <sstream>
#include <utility>
#include <vector>
#include <unordered_map>

#include "port/Platform.h"


////========================================================================////
////                                                                        ////
////    FORWARD DECLARATIONS                                                ////
////                                                                        ////
////========================================================================////

////========================================================================////
////                                                                        ////
////    TYPE DEFINITIONS                                                    ////
////                                                                        ////
////========================================================================////


namespace atakmap                       // Open atakmap namespace.
{
namespace util                          // Open util namespace.
{

class ENGINE_API AttributeSet
  {


                                        //====================================//
  public:                               //                      PUBLIC        //
                                        //====================================//


    //==================================
    //  PUBLIC NESTED TYPES
    //==================================


    enum Type
      {
        INT,
        LONG,
        DOUBLE,
        STRING,
        BLOB,
        ATTRIBUTE_SET,
        INT_ARRAY,
        LONG_ARRAY,
        DOUBLE_ARRAY,
        STRING_ARRAY,
        BLOB_ARRAY
      };

    typedef std::pair<const unsigned char*, const unsigned char*>       Blob;
    typedef std::pair<const int*, const int*>                   IntArray;
    typedef std::pair<const int64_t*, const int64_t*>                 LongArray;
    typedef std::pair<const double*, const double*>             DoubleArray;
    typedef std::pair<const char* const*, const char* const*>   StringArray;
    typedef std::pair<const Blob*, const Blob*>                 BlobArray;


    //==================================
    //  PUBLIC INTERFACE
    //==================================


    ~AttributeSet()
        NOTHROWS;

    //
    // Clears all attributes.
    //
    void
    clear ();

    bool
    containsAttribute (const char* key)
        const
        NOTHROWS
      { return attrItems.find(key) != attrItems.end(); }

    std::vector<const char*>
    getAttributeNames ()
        const
        NOTHROWS;

    //
    // Returns the AttributeSet attribute with the supplied attributeName.
    //
    // Throw std::invalid_argument if the supplied attributeName is NULL or if
    // the AttributeSet does not contain the supplied attributeName or if the
    // attribute is not an AttributeSet.
    //
    const AttributeSet&
    getAttributeSet (const char* attributeName)
        const;

    //
    // Returns the AttributeSet attribute with the supplied attributeName via
    // the specified shared_ptr reference.
    //
    // Throw std::invalid_argument if the supplied attributeName is NULL or if
    // the AttributeSet does not contain the supplied attributeName or if the
    // attribute is not an AttributeSet.
    //
    void
    getAttributeSet(std::shared_ptr<AttributeSet> &value,
                    const char *attributeName);

    //
    // Returns the type of the attribute with the supplied attributeName.
    //
    // Throw std::invalid_argument if the supplied attributeName is NULL or if
    // the AttributeSet does not contain the supplied attributeName.
    //
    Type
    getAttributeType (const char* attributeName)
        const;

    //
    // Returns the Blob attribute with the supplied attributeName.
    //
    // Throw std::invalid_argument if the supplied attributeName is NULL or if
    // the AttributeSet does not contain the supplied attributeName or if the
    // attribute is not a Blob.
    //
    Blob
    getBlob (const char* attributeName)
        const;

    //
    // Returns the BlobArray attribute with the supplied attributeName.
    //
    // Throw std::invalid_argument if the supplied attributeName is NULL or if
    // the AttributeSet does not contain the supplied attributeName or if the
    // attribute is not a BlobArray.
    //
    BlobArray
    getBlobArray (const char* attributeName)
        const;

    //
    // Returns the double attribute with the supplied attributeName.
    //
    // Throw std::invalid_argument if the supplied attributeName is NULL or if
    // the AttributeSet does not contain the supplied attributeName or if the
    // attribute is not a double.
    //
    double
    getDouble (const char* attributeName)
        const;

    //
    // Returns the double array attribute with the supplied attributeName.
    //
    // Throw std::invalid_argument if the supplied attributeName is NULL or if
    // the AttributeSet does not contain the supplied attributeName or if the
    // attribute is not a double array.
    //
    DoubleArray
    getDoubleArray (const char* attributeName)
        const;

    //
    // Returns the int attribute with the supplied attributeName.
    //
    // Throw std::invalid_argument if the supplied attributeName is NULL or if
    // the AttributeSet does not contain the supplied attributeName or if the
    // attribute is not a int.
    //
    int
    getInt (const char* attributeName)
        const;

    //
    // Returns the int array attribute with the supplied attributeName.
    //
    // Throw std::invalid_argument if the supplied attributeName is NULL or if
    // the AttributeSet does not contain the supplied attributeName or if the
    // attribute is not a int array.
    //
    IntArray
    getIntArray (const char* attributeName)
        const;

    //
    // Returns the int64_t attribute with the supplied attributeName.
    //
    // Throw std::invalid_argument if the supplied attributeName is NULL or if
    // the AttributeSet does not contain the supplied attributeName or if the
    // attribute is not a int64_t.
    //
    int64_t
    getLong (const char* attributeName)
        const;

    //
    // Returns the int64_t array attribute with the supplied attributeName.
    //
    // Throw std::invalid_argument if the supplied attributeName is NULL or if
    // the AttributeSet does not contain the supplied attributeName or if the
    // attribute is not a int64_t array.
    //
    LongArray
    getLongArray (const char* attributeName)
        const;

    //
    // Returns the string attribute with the supplied attributeName.
    //
    // Throw std::invalid_argument if the supplied attributeName is NULL or if
    // the AttributeSet does not contain the supplied attributeName or if the
    // attribute is not a string.
    //
    const char*
    getString (const char* attributeName)
        const;

    //
    // Returns the string array attribute with the supplied attributeName.
    //
    // Throw std::invalid_argument if the supplied attributeName is NULL or if
    // the AttributeSet does not contain the supplied attributeName or if the
    // attribute is not a string array.
    //
    StringArray
    getStringArray (const char* attributeName)
        const;

    //
    // Removes the attribute with the supplied attributeName.
    //
    // Throw std::invalid_argument if the supplied attributeName is NULL.
    //
    void
    removeAttribute (const char* attributeName);

    //
    // Sets the value of the supplied attributeName to the supplied AttributeSet.
    // Replaces any existing value for attributeName.
    //
    // Throws std::invalid_argument if the supplied attributeName is NULL.
    //
    void
    setAttributeSet (const char* attributeName,
                     const AttributeSet& value);

    //
    // Sets the value of the supplied attributeName to the supplied Blob.
    // Replaces any existing value for attributeName.
    //
    // Throws std::invalid_argument if the supplied attributeName is NULL or if
    // either of the Blob's pointers are NULL.
    //
    void
    setBlob (const char* attributeName,
             const Blob& value);

    //
    // Sets the value of the supplied attributeName to the supplied Blob array.
    // Replaces any existing value for attributeName.
    //
    // Throws std::invalid_argument if the supplied attributeName is NULL or if
    // any of the value's pointers are NULL.
    //
    void
    setBlobArray (const char* attributeName,
                  const BlobArray& value);

    //
    // Sets the value of the supplied attributeName to the supplied double.
    // Replaces any existing value for attributeName.
    //
    // Throws std::invalid_argument if the supplied attributeName is NULL.
    //
    void
    setDouble (const char* attributeName,
               double value);

    //
    // Sets the value of the supplied attributeName to the supplied double array.
    // Replaces any existing value for attributeName.
    //
    // Throws std::invalid_argument if the supplied attributeName is NULL or if
    // either of the value's pointers are NULL.
    //
    void
    setDoubleArray (const char* attributeName,
                    const DoubleArray& value);

    //
    // Sets the value of the supplied attributeName to the supplied integer.
    // Replaces any existing value for attributeName.
    //
    // Throws std::invalid_argument if the supplied attributeName is NULL.
    //
    void
    setInt (const char* attributeName,
            int value);

    //
    // Sets the value of the supplied attributeName to the supplied integer
    // array.  Replaces any existing value for attributeName.
    //
    // Throws std::invalid_argument if the supplied attributeName is NULL or if
    // either of the value's pointers are NULL.
    //
    void
    setIntArray (const char* attributeName,
                 const IntArray& value);

    //
    // Sets the value of the supplied attributeName to the supplied int64_t.
    // Replaces any existing value for attributeName.
    //
    // Throws std::invalid_argument if the supplied attributeName is NULL.
    //
    void
    setLong (const char* attributeName,
             int64_t value);

    //
    // Sets the value of the supplied attributeName to the supplied int64_t array.
    // Replaces any existing value for attributeName.
    //
    // Throws std::invalid_argument if the supplied attributeName is NULL or if
    // either of the value's pointers are NULL.
    //
    void
    setLongArray (const char* attributeName,
                  const LongArray& value);

    //
    // Sets the value of the supplied attributeName to the supplied string.
    // Replaces any existing value for attributeName.
    //
    // Throws std::invalid_argument if the supplied attributeName or value is
    // NULL.
    //
    void
    setString (const char* attributeName,
               const char* value);

    //
    // Sets the value of the supplied attributeName to the supplied string array.
    // Replaces any existing value for attributeName.
    //
    // Throws std::invalid_argument if the supplied attributeName is NULL or if
    // any of the value's pointers are NULL.
    //
    void
    setStringArray (const char* attributeName,
                    const StringArray& value);


                                        //====================================//
  protected:                            //                      PROTECTED     //
                                        //====================================//

                                        //====================================//
  private:                              //                      PRIVATE       //
                                        //====================================//


    //==================================
    //  PRIVATE NESTED TYPES
    //==================================

      class AttrItem {
      public:
          virtual ~AttrItem() = default;
          virtual Type attrType() const = 0;
          virtual bool isNull() const = 0;
      };

      template <int type>
      class NullAttrItem : public AttrItem {
      public:
          NullAttrItem() { }
          virtual ~NullAttrItem() { }
          virtual Type attrType() const override { return (Type)type; }
          bool isNull() const { return true; }
      };

      template <typename T, int type>
      class BasicAttrItem : public AttrItem {
      public:
          BasicAttrItem(const T &value_)
          : value(value_) { }
          virtual ~BasicAttrItem() { }
          virtual Type attrType() const override { return (Type)type; }

          const T &get() const { return value; }
          bool isNull() const override { return false; }
      private:
          T value;
      };

      template <typename T, Type type>
      class BasicArrayAttrItem : public AttrItem {
      public:
          BasicArrayAttrItem(const T *begin, const T *end) :
            nullValue(!begin)
          {
              if(!nullValue) {
                  items.reserve(end - begin);
                  items.insert(items.begin(), begin, end);
              }
          }

          inline std::pair<const T *, const T *> get() const {
              return std::make_pair(items.data(),
                                    items.data() + items.size());
          }

          virtual Type attrType() const override { return type; }
          virtual bool isNull() const override { return nullValue; }
          virtual ~BasicArrayAttrItem() { }

      private:
          std::vector<T> items;
          bool nullValue;
      };

      class StringArrayAttrItem : public AttrItem {
      public:
          virtual ~StringArrayAttrItem();

          StringArrayAttrItem(const char * const *begin,
                              const char * const *end);

          virtual Type attrType() const override;

          std::pair<const char * const*, const char * const*> get() const;
          virtual bool isNull() const override { return nullValue; }
      private:
          std::vector<const char *> ptrs;
          std::vector<std::string> strs;
          bool nullValue;
      };

      class BlobArrayAttrItem : public AttrItem {
      public:
          virtual ~BlobArrayAttrItem();

          BlobArrayAttrItem(const Blob *begin,
                            const Blob *end);

          virtual Type attrType() const override;
          virtual bool isNull() const override { return nullValue; }

          std::pair<const Blob *, const Blob *> get() const;
      private:
          std::vector<Blob> ptrs;
          std::vector<std::vector<uint8_t>> blobs;
          bool nullValue;
      };

    //==================================
    //  PRIVATE IMPLEMENTATION
    //==================================


    static
    bool
    invalidBlob (const Blob& blob);

    static
    bool
    isNULL (const void* ptr);

    void
    throwNotFound (const char* attributeName,
                   const char* attributeType,
                   const char* errHdr)
        const;


    //==================================
    //  PRIVATE REPRESENTATION
    //==================================

      std::unordered_map<std::string, std::shared_ptr<AttrItem>> attrItems;
  };


}                                       // Close util namespace.
}                                       // Close atakmap namespace.


////========================================================================////
////                                                                        ////
////    EXTERN DECLARATIONS                                                 ////
////                                                                        ////
////========================================================================////


////========================================================================////
////                                                                        ////
////    PUBLIC INLINE DEFINITIONS                                           ////
////                                                                        ////
////========================================================================////


namespace atakmap                       // Open atakmap namespace.
{
namespace util                          // Open util namespace.
{





}                                       // Close util namespace.
}                                       // Close atakmap namespace.


////========================================================================////
////                                                                        ////
////    PROTECTED INLINE DEFINITIONS                                        ////
////                                                                        ////
////========================================================================////


#endif  // #ifndef ATAKMAP_UTIL_ATTRIBUTE_SET_H_INCLUDED

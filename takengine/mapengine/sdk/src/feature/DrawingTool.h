////============================================================================
////
////    FILE:           DrawingTool.h
////
////    DESCRIPTION:    Declaration of abstract DrawingTool class and concrete
////                    derived classes.
////
////    AUTHOR(S):      scott           scott_barrett@partech.com
////
////
////    HISTORY:
////
////      DATE          AUTHOR          COMMENTS
////      ------------  --------        --------
////      Feb 6, 2015   scott           Created.
////
////========================================================================////
////                                                                        ////
////    (c) Copyright 2015 PAR Government Systems Corporation.              ////
////                                                                        ////
////========================================================================////


#ifndef ATAKMAP_FEATURE_DRAWING_TOOL_H_INCLUDED
#define ATAKMAP_FEATURE_DRAWING_TOOL_H_INCLUDED


////========================================================================////
////                                                                        ////
////    INCLUDES AND MACROS                                                 ////
////                                                                        ////
////========================================================================////

#include <vector>

#include "port/String.h"


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
namespace feature                       // Open feature namespace.
{


///=============================================================================
///
///  class atakmap::feature::DrawingTool
///
///     Abstract base class for OGR drawing tools.
///
///=============================================================================


class DrawingTool
  {
                                        //====================================//
  public:                               //                      PUBLIC        //
                                        //====================================//

    enum Type
    {
        BRUSH,
        PEN,
        SYMBOL,
        LABEL,
    };
  public :
    virtual
    ~DrawingTool ()
        NOTHROWS
      { }

    Type getType() const
      { return type; }

    //
    // The compiler-generated constructor, copy constructor, and assignment
    // operator are acceptable.
    //

    //
    // Parses hexadecimal string of the form: #RRGGBB[AA] where the default for
    // the optional alpha [AA] is FF (opaque).  The returned value will be of
    // the form: 0xAARRGGBB.  An unset color in a DrawingTool will have the
    // value 0x00000000 (i.e., 0).
    //
    // Examples:
    //
    //          "#000000" or "#000000FF" is opaque black =>     0xFF000000
    //          "#FFFFFF" or "#FFFFFFFF" is opaque white =>     0xFFFFFFFF
    //
    // Throws std::invalid_argument if supplied string is NULL.
    //
    static
    unsigned int
    parseOGR_Color (const char*);

    //
    // Parses and returns a single DrawingTool from the supplied OGR style
    // string.  Sets remainder to the unparsed portion of the supplied style
    // string.
    //
    // Example use:
    //
    //          const char* styleString (...);
    //          const char* styleEnd (styleString + std::strlen (styleString));
    //
    //          while (styleString != styleEnd)
    //            {
    //              const char* remainder (NULL);
    //              std::auto_ptr<DrawingTool> tool
    //                  (DrawingTool::parseOGR_Tool (styleString, remainder));
    //              ... // do something with tool
    //              styleString = remainder;
    //            }
    //
    // Throws std::invalid_argument if supplied style string is NULL or
    // syntactically malformed.
    //
    static
    DrawingTool*
    parseOGR_Tool (const char* styleString,
                   const char*& remainder);


    //
    // Throws std::invalid_argument if supplied key or value is NULL.
    //
    virtual
    void
    setParam (const char* key,
              const char* value)
        = 0;
  protected :
    DrawingTool(Type type_) : type(type_)
      {}
  private :
    DrawingTool();
  private:
    Type type;
  };


///=============================================================================
///
///  class atakmap::feature::Brush
///
///     OGR-style Brush class.
///
///=============================================================================


class Brush
  : public DrawingTool
  {
                                        //====================================//
  public:                               //                      PUBLIC        //
                                        //====================================//


    //==================================
    //  PUBLIC CONSTANTS
    //==================================


    static const char* const BRUSH_FORE_COLOR;
    static const char* const BRUSH_BACK_COLOR;
    static const char* const BRUSH_NAMES;
    static const char* const BRUSH_ANGLE;
    static const char* const BRUSH_SIZE;
    static const char* const BRUSH_HORIZONTAL_SPACING;
    static const char* const BRUSH_VERTICAL_SPACING;
    static const char* const BRUSH_PRIORITY_LEVEL;


    //==================================
    //  PUBLIC INTERFACE
    //==================================


    Brush ();

    ~Brush ()
        NOTHROWS
      { }

    void
    setParam (const char* key,
              const char* value) override;


    //==================================
    //  PUBLIC REPRESENTATION
    //==================================


    TAK::Engine::Port::String names;
    unsigned int foreColor;             // Defaults to 0xFF808080.
    unsigned int backColor;             // Defaults to 0xFF808080.
    float angle;
    float size;
    float dx;
    float dy;
    unsigned int priority;
  };


///=============================================================================
///
///  class atakmap::feature::Label
///
///     OGR-style Label class.
///
///=============================================================================


class Label
  : public DrawingTool
  {
                                        //====================================//
  public:                               //                      PUBLIC        //
                                        //====================================//


    //==================================
    //  PUBLIC NESTED TYPES
    //==================================


    enum Placement
      {
        FIRST_VERTEX                    = 'p',
        LAST_VERTEX                     = 'l',
        STRETCHED                       = 's',
        MIDDLE                          = 'm',
        WORD_PER_SEGMENT                = 'w',
        WORD_PER_SEGMENT_MIDDLE         = 'h',
        WORD_PER_SEGMENT_STRETCHED      = 'a',
        DEFAULT                         = FIRST_VERTEX
      };


    enum Position
      {
        BASELINE_LEFT = 1,
        BASELINE_CENTER,
        BASELINE_RIGHT,
        CENTER_LEFT,
        CENTER_CENTER,
        CENTER_RIGHT,
        TOP_LEFT,
        TOP_CENTER,
        TOP_RIGHT,
        BOTTOM_LEFT,
        BOTTOM_CENTER,
        BOTTOM_RIGHT
      };


    //==================================
    //  PUBLIC CONSTANTS
    //==================================


    static const char* const LABEL_FORE_COLOR;
    static const char* const LABEL_BACK_COLOR;
    static const char* const LABEL_OUTLINE_COLOR;
    static const char* const LABEL_SHADOW_COLOR;
    static const char* const LABEL_FONT_NAMES;
    static const char* const LABEL_FONT_SIZE;
    static const char* const LABEL_TEXT;
    static const char* const LABEL_BOLD;
    static const char* const LABEL_ITALIC;
    static const char* const LABEL_UNDERLINE;
    static const char* const LABEL_STRIKEOUT;
    static const char* const LABEL_ANGLE;
    static const char* const LABEL_STRETCH;
    static const char* const LABEL_PLACEMENT;
    static const char* const LABEL_POSITION;
    static const char* const LABEL_HORIZONTAL_OFFSET;
    static const char* const LABEL_VERTICAL_OFFSET;
    static const char* const LABEL_PERPENDICULAR_OFFSET;
    static const char* const LABEL_PRIORITY_LEVEL;
    // libtakengine additions
    static const char* const LABEL_RELATIVE_ANGLE;


    //==================================
    //  PUBLIC INTERFACE
    //==================================


    Label ();

    ~Label ()
        NOTHROWS
      { }

    void
    setParam (const char* key,
              const char* value) override;


    //==================================
    //  PUBLIC REPRESENTATION
    //==================================


    TAK::Engine::Port::String text;
    TAK::Engine::Port::String fontNames;
    unsigned int foreColor;             // Defaults to 0xFF000000.
    unsigned int backColor;
    unsigned int outlineColor;
    unsigned int shadowColor;
    float fontSize;                     // In pixels.
    float angle;
    float relativeAngle;
    float stretch;                      // Defaults to 100.
    Placement placement;                // Defaults to FIRST_VERTEX.
    Position position;                  // Defaults to CENTER_LEFT.
    float dx;
    float dy;
    float dp;
    unsigned int priority;
    bool bold;
    bool italic;
    bool underline;
    bool strikeout;
  };


///=============================================================================
///
///  class atakmap::feature::Pen
///
///     OGR-style Pen class.
///
///=============================================================================


class Pen
  : public DrawingTool
  {
                                        //====================================//
  public:                               //                      PUBLIC        //
                                        //====================================//


    //==================================
    //  PUBLIC NESTED TYPES
    //==================================


    enum Cap
      {
        BUTT,
        ROUND,
        PROJECTING
      };


    enum Join
      {
        MITER,
        ROUNDED,
        BEVEL
      };


    //==================================
    //  PUBLIC CONSTANTS
    //==================================


    static const char* const PEN_COLOR;
    static const char* const PEN_WIDTH;
    static const char* const PEN_PATTERN;
    static const char* const PEN_NAMES;
    static const char* const PEN_CAP;
    static const char* const PEN_JOIN;
    static const char* const PEN_PERPENDICULAR_OFFSET;
    static const char* const PEN_PRIORITY_LEVEL;


    //==================================
    //  PUBLIC INTERFACE
    //==================================


    Pen ();

    ~Pen ()
        NOTHROWS
      { }

    void
    setParam (const char* key,
              const char* value) override;


    //==================================
    //  PUBLIC REPRESENTATION
    //==================================


    TAK::Engine::Port::String names;
    std::vector<unsigned> pattern;
    unsigned int color;                 // Defaults to 0xFF000000.
    float width;
    float dp;
    Cap cap;
    Join join;
    unsigned int priority;
  };


///=============================================================================
///
///  class atakmap::feature::Symbol
///
///     OGR-style Symbol class.
///
///=============================================================================


class Symbol
  : public DrawingTool
  {
                                        //====================================//
  public:                               //                      PUBLIC        //
                                        //====================================//


    enum Position
      {
        BASELINE_LEFT = 1,
        BASELINE_CENTER,
        BASELINE_RIGHT,
        CENTER_LEFT,
        CENTER_CENTER,
        CENTER_RIGHT,
        TOP_LEFT,
        TOP_CENTER,
        TOP_RIGHT,
        BOTTOM_LEFT,
        BOTTOM_CENTER,
        BOTTOM_RIGHT
      };

    //==================================
    //  PUBLIC CONSTANTS
    //==================================


    static const char* const SYMBOL_COLOR;
    static const char* const SYMBOL_OUTLINE_COLOR;
    static const char* const SYMBOL_NAMES;
    static const char* const SYMBOL_ANGLE;
    static const char* const SYMBOL_SIZE;
    static const char* const SYMBOL_HORIZONTAL_OFFSET;
    static const char* const SYMBOL_VERTICAL_OFFSET;
    static const char* const SYMBOL_PERPENDICULAR_OFFSET;
    static const char* const SYMBOL_SPACING_STEP;
    static const char* const SYMBOL_SPACING_INITIAL;
    static const char* const SYMBOL_PRIORITY_LEVEL;
    // libtakengine additions
    static const char* const SYMBOL_POSITION;
    static const char* const SYMBOL_RELATIVE_ANGLE;
    static const char* const SYMBOL_SYMBOL_WIDTH;
    static const char* const SYMBOL_SYMBOL_HEIGHT;


    //==================================
    //  PUBLIC INTERFACE
    //==================================


    Symbol ();

    ~Symbol ()
        NOTHROWS
      { }

    void
    setParam (const char* key,
              const char* value) override;


    //==================================
    //  PUBLIC REPRESENTATION
    //==================================


    TAK::Engine::Port::String names;
    unsigned int color;                 // Defaults to 0xFF000000.
    unsigned int outlineColor;
    float angle;                        // Absolute degrees CCW, per OGR; ignored if `relativeAngle` defined
    float relativeAngle;                // Relative degrees CCW
    float scaling;                      // Defaults to 1.0.  If 0.0, use size.
    float size;                         // Defaults to 0.0.  Defers to scaling.
    float dx;
    float dy;
    float dp;
    float ds;
    float di;
    unsigned int priority;
    Position position;
    float symbolWidth;
    float symbolHeight;
  };


}                                       // Close feature namespace.
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

////========================================================================////
////                                                                        ////
////    PROTECTED INLINE DEFINITIONS                                        ////
////                                                                        ////
////========================================================================////

#endif  // #ifndef ATAKMAP_FEATURE_DRAWING_TOOL_H_INCLUDED

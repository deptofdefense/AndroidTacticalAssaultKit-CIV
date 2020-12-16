////============================================================================
////
////    FILE:           DrawingTool.cpp
////
////    DESCRIPTION:    Implementation of DrawingTool classes.
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

////========================================================================////
////                                                                        ////
////    INCLUDES AND MACROS                                                 ////
////                                                                        ////
////========================================================================////


#include "feature/DrawingTool.h"

#include <algorithm>
#include <cstring>
#include <functional>
#include <memory>
#include <sstream>
#include <stdexcept>


#define MEM_FN( fn )    "atakmap::feature::DrawingTool::" fn ": "


////========================================================================////
////                                                                        ////
////    USING DIRECTIVES AND DECLARATIONS                                   ////
////                                                                        ////
////========================================================================////


using namespace atakmap;


////========================================================================////
////                                                                        ////
////    EXTERN DECLARATIONS                                                 ////
////                                                                        ////
////========================================================================////

////========================================================================////
////                                                                        ////
////    FILE-SCOPED TYPE DEFINITIONS                                        ////
////                                                                        ////
////========================================================================////

////========================================================================////
////                                                                        ////
////    EXTERN VARIABLE DEFINITIONS                                         ////
////                                                                        ////
////========================================================================////

////========================================================================////
////                                                                        ////
////    FILE-SCOPED VARIABLE DEFINITIONS                                    ////
////                                                                        ////
////========================================================================////

////========================================================================////
////                                                                        ////
////    FILE-SCOPED FUNCTION DEFINITIONS                                    ////
////                                                                        ////
////========================================================================////


namespace                               // Open unnamed namespace.
{


void
convertUnits (const char* unitString,
              float& value)
  {
    //
    // Pixels (i.e., "px") were the assumed units when value was originally
    // parsed.  Adjust the value if a different unit was specified.
    //
    const double PPI (240);             // Need to get this from somewhere!!!

    if (unitString && *unitString)
      {
        if (!std::strcmp (unitString, "pt"))    // 1/72 "
          {
            value *= static_cast<float>(PPI / 72);
          }
        else if (!std::strcmp (unitString, "in"))
          {
            value *= static_cast<float>(PPI);
          }
        else if (!std::strcmp (unitString, "cm"))
          {
            value *= static_cast<float>(PPI / 2.54);
          }
        else if (!std::strcmp (unitString, "mm"))
          {
            value *= static_cast<float>(PPI / 25.4);
          }
//        else if (!std::strcmp (unitString, "g"))
//          {
//            // Adjust to "map ground units".  Meters?
//          }
      }
  }


template <typename T>
const char*
extractValue (const char* value,
              T& result)
  {
    std::istringstream strm (value);

    if (!(strm >> result))
      {
        std::ostringstream msg;
        msg << MEM_FN ("extractValue") << "Value extraction failed for: " <<
              value;
        throw std::invalid_argument
                  (msg.str());
      }

    std::streampos bytesExtracted (strm.tellg ());

    return bytesExtracted >= 0
        ? value + static_cast<std::size_t>(bytesExtracted)
        : nullptr;
  }


const char*
extractValue (const char* value,
              feature::Label::Placement& result)
  {
    if (std::strlen (value) != 1 || !std::strchr ("plsmwha", *value))
      {
          std::ostringstream msg;
           msg << MEM_FN ("extractValue") <<
                 "Invalid Label::Placement value: " <<
                 value;
        throw std::invalid_argument(msg.str());
      }

    result = static_cast<feature::Label::Placement> (*value);
    return ++value;
  }


const char*
extractValue (const char* value,
              feature::Label::Position& result)
  {
    unsigned int tmp (0);
    const char* remainder (extractValue (value, tmp));

    if (tmp < feature::Label::BASELINE_LEFT
        || tmp > feature::Label::BOTTOM_RIGHT)
      {
          std::ostringstream msg;
               msg <<
                    MEM_FN ("extractValue") <<
                     "Invalid Label::Position value: " <<
                     value;
        throw std::invalid_argument
                  (msg.str());
      }

    result = static_cast<feature::Label::Position> (tmp);
    return remainder;
  }

const char*
extractValue (const char* value,
              feature::Symbol::Position& result)
  {
    unsigned int tmp (0);
    const char* remainder (extractValue (value, tmp));

    if (tmp < feature::Label::BASELINE_LEFT
        || tmp > feature::Label::BOTTOM_RIGHT)
      {
          std::ostringstream msg;
               msg <<
                    MEM_FN ("extractValue") <<
                     "Invalid Label::Position value: " <<
                     value;
        throw std::invalid_argument
                  (msg.str());
      }

    result = static_cast<feature::Symbol::Position> (tmp);
    return remainder;
  }

const char*
extractValue (const char* value,
              feature::Pen::Cap& result)
  {
    unsigned int tmp (0);
    const char* remainder (extractValue (value, tmp));

    if (tmp > feature::Pen::PROJECTING)
      {
          std::ostringstream msg;
               msg << MEM_FN ("extractValue") <<
                     "Invalid Pen::Cap value: " <<
                     value;
        throw std::invalid_argument
                  (msg.str());
      }

    result = static_cast<feature::Pen::Cap> (tmp);
    return remainder;
  }


const char*
extractValue (const char* value,
              feature::Pen::Join& result)
  {
    unsigned int tmp (0);
    const char* remainder (extractValue (value, tmp));

    if (tmp > feature::Pen::BEVEL)
      {
          std::ostringstream msg;
               msg << MEM_FN ("extractValue") <<
                     "Invalid Pen::Join value: " <<
                     value;
        throw std::invalid_argument
                  (msg.str());
      }

    result = static_cast<feature::Pen::Join> (tmp);
    return remainder;
  }

float extractFontSize(const char *value) {
    char *end = nullptr;
    auto result = static_cast<float>(strtod(value, &end));
    convertUnits(end, result);
    return result;
}


const char*
trim (TAK::Engine::Port::String& str)
  {
    char* contents (str.get ());

    if (contents)
      {
        //
        // Trim spaces from the tail.
        //
        char* end (contents + std::strlen (contents));

        while (end > contents && *(end - 1) == ' ')
          {
            --end;
          }
        *end = '\0';

        //
        // Trim spaces from the head.
        //
        char* from (contents);

        while (*from == ' ')
          {
            ++from;
          }

        if (from > contents)
          {
            char* to (contents);

            while (from < end)
              {
                *to++ = *from++;
              }
            *to = '\0';
          }
      }

    return contents;
  }


inline
void
validate (const char* key,
          const char* value)
  {
    if (!key)
      {
        throw std::invalid_argument (MEM_FN ("setParam") "Received NULL key");
      }
    if (!value)
      {
        throw std::invalid_argument (MEM_FN ("setParam") "Received NULL value");
      }
  }


}                                       // Close unnamed namespace.


////========================================================================////
////                                                                        ////
////    EXTERN FUNCTION DEFINITIONS                                         ////
////                                                                        ////
////========================================================================////

////========================================================================////
////                                                                        ////
////    PRIVATE INLINE MEMBER FUNCTION DEFINITIONS                          ////
////                                                                        ////
////========================================================================////

////========================================================================////
////                                                                        ////
////    PUBLIC MEMBER FUNCTION DEFINITIONS                                  ////
////                                                                        ////
////========================================================================////


namespace atakmap                       // Open atakmap namespace.
{
namespace feature                       // Open feature namespace.
{

Brush::Brush()
    : DrawingTool(DrawingTool::BRUSH),
    foreColor(0xFF808080),
    backColor(0xFF808080),
    angle(0.0),
    size(1.0),
    dx(0.0),
    dy(0.0),
    priority(1)
{ }


Label::Label()
    : DrawingTool(DrawingTool::LABEL),
    foreColor(0xFF000000),
    backColor(0),
    outlineColor(0),
    shadowColor(0),
    fontSize(0),
    angle(NAN),
    relativeAngle(NAN),
    stretch(100.0),
    placement(FIRST_VERTEX),
    position(CENTER_LEFT),
    dx(0.0),
    dy(0.0),
    dp(0.0),
    priority(1),
    bold(false),
    italic(false),
    underline(false),
    strikeout(false)
{ }


Pen::Pen()
    : DrawingTool(DrawingTool::PEN),
    color(0xFF000000),
    width(1.0),
    dp(0.0),
    cap(BUTT),
    join(MITER),
    priority(1)
{ }


Symbol::Symbol()
    : DrawingTool(DrawingTool::SYMBOL),
    color(0xFFFFFFFF),
    outlineColor(0),
    angle(NAN),
    relativeAngle(NAN),
    scaling(1.0),
    size(0.0),
    dx(0.0),
    dy(0.0),
    dp(0.0),
    ds(0.0),
    di(0.0),
    priority(1),
    position(CENTER_CENTER),
    symbolWidth(0.0),
    symbolHeight(0.0)
{ }


unsigned int
DrawingTool::parseOGR_Color (const char* color)
  {
    if (!color)
      {
        throw std::invalid_argument (MEM_FN ("parseOGR_Color")
                                     "Received NULL color specification");
      }
    std::size_t len (std::strlen (color));

    if ((len != 7 && len != 9)
        || *color != '#'
#if (defined(MSVC) || defined(__ANDROID__))
        || std::find_if (color + 1, color + len,
                         std::not1 (std::bind1st (std::ptr_fun<const char *, int> (strchr),
                                                  "0123456789ABCDEFabcdef")))
#else
        || std::find_if(color + 1, color + len,
                        std::not1 (std::bind1st (std::ptr_fun<const char *, int, const char *>(std::strchr),
                                                  "0123456789ABCDEFabcdef")))
#endif
           != color + len)
      {
          std::ostringstream msg;
               msg <<
                    MEM_FN ("parseOGR_Color") <<
                     "Received invalid color specification: " <<
                     color;
        throw std::invalid_argument
                  (msg.str());
      }

    std::istringstream strm (color + 1);
    unsigned int result (0);

    strm >> std::hex >> result;
    return len == 7
        ? (result | 0xFF000000)
        : (result & 0xFF) << 24 | (0xFFFFFF & result >> 8);     // RGBA => ARGB.
  }


DrawingTool*
DrawingTool::parseOGR_Tool (const char* styleString,
                            const char*& remainder)
  {
    enum State
      {
        TOOL_NAME,
        PARAM_KEY,
        PARAM_VALUE,
        QUOTED_VALUE,
        TOOL_END
      };

    if (!styleString)
      {
        throw std::invalid_argument (MEM_FN ("parseOGR_Tool")
                                     "Received NULL style string");
      }

    std::unique_ptr<DrawingTool> result;
    const char* end (styleString + std::strlen (styleString));
    const char* cursor (styleString);
    State state (TOOL_NAME);
    TAK::Engine::Port::String paramKey;
    TAK::Engine::Port::String paramValue;
    std::ostringstream token;

    while (cursor < end)
      {
        switch (state)
          {
          case TOOL_NAME:

            if (*cursor == '(')
              {
                //
                // Tool name is completed.  The call to trim modifies the
                // contents of the PGSC::String, so it only needs to be called
                // on the first use of toolName.
                //
                TAK::Engine::Port::String toolName (token.str ().c_str ());

                if (!std::strcmp (trim (toolName), "PEN"))
                  {
                    result.reset (new Pen);
                  }
                else if (!std::strcmp (toolName, "BRUSH"))
                  {
                    result.reset (new Brush);
                  }
                else if (!std::strcmp (toolName, "LABEL"))
                  {
                    result.reset (new Label);
                  }
                else if (!std::strcmp (toolName, "SYMBOL"))
                  {
                      auto *sym = new Symbol();
                      sym->scaling = 0.0; // this defaults to 1 so size never works
                    result.reset (sym);
                  }
                else
                  {
                      std::ostringstream msg;
                           msg << MEM_FN ("parseOGR_Tool") <<
                                              "Unknown tool name: " <<
                                              toolName;
                    throw std::invalid_argument
                              (msg.str());
                  }
                token.str (std::string ());
                state = PARAM_KEY;
                cursor++;
              }
            else
              {
                token.put (*cursor++);
              }
            break;

          case PARAM_KEY:

            if (*cursor == ':')
              {
                //
                // Parameter name is completed.
                //
                paramKey = token.str ().c_str ();
                token.str (std::string ());
                state = PARAM_VALUE;
                cursor++;
              }
            else
              {
                token.put (*cursor++);
              }
            break;

          case PARAM_VALUE:

            if (*cursor == ',' || *cursor == ')')
              {
                //
                // Parameter value is completed.
                //
                paramValue = token.str ().c_str ();
                result->setParam (trim (paramKey), trim (paramValue));
                token.str (std::string ());
                state = *cursor == ',' ? PARAM_KEY : TOOL_END;
                  cursor++;
              }
            else if (*cursor == '"')
              {
                if (!token.str ().empty ())
                  {
                      std::ostringstream msg;
                           msg <<
                                MEM_FN ("parseOGR_Tool") <<
                                 "Quote not at start of value; " <<
                                 "found after: " <<
                                 token.str ();
                    throw std::invalid_argument
                              (msg.str());
                  }
                state = QUOTED_VALUE;
                  cursor++;
              }
            else
              {
                token.put (*cursor++);
              }
            break;

          case QUOTED_VALUE:

            if (*cursor == '"' && *(cursor - 1) != '\\')
              {
                state = PARAM_VALUE;
                cursor++;
              }
            else if (*cursor != '\\' || *(cursor + 1) != '"')
              {
                token.put (*cursor++);
              }
            break;

          case TOOL_END:

            if (*cursor == ';')         // Another tool to follow.
              {
                end = ++cursor;         // Terminate the parse.
              }
            else if (*cursor == ' ')    // Consume trailing spaces.
              {
                ++cursor;
              }
            else
              {
                  std::ostringstream msg;
                       msg <<
                            MEM_FN ("parseOGR_Tool") <<
                             "Unexpected text at tool end: " <<
                             cursor;
                throw std::invalid_argument
                          (msg.str());
              }
            break;
          }
      }
    if (state != TOOL_END)
      {
        const char* whilst ("");

        switch (state)
          {
          case PARAM_KEY:       whilst = " parameter name";     break;
          case PARAM_VALUE:     whilst = " parameter value";    break;
          case QUOTED_VALUE:    whilst = " quoted string";      break;
          default:              whilst = " tool name";          break;
          }
        std::ostringstream msg;
        msg << MEM_FN ("parseOGR_Tool") <<
                           "Unexpected end of style string " <<
                           "while parsing" <<
                           whilst;
        throw std::invalid_argument
                  (msg.str());
      }
    remainder = cursor;

    return result.release ();
  }


const char* const Brush::BRUSH_FORE_COLOR ("fc");
const char* const Brush::BRUSH_BACK_COLOR ("bc");
const char* const Brush::BRUSH_NAMES ("id");
const char* const Brush::BRUSH_ANGLE ("a");
const char* const Brush::BRUSH_SIZE ("s");
const char* const Brush::BRUSH_HORIZONTAL_SPACING ("dx");
const char* const Brush::BRUSH_VERTICAL_SPACING ("dy");
const char* const Brush::BRUSH_PRIORITY_LEVEL ("l");


void
Brush::setParam (const char* key,
                 const char* value)
  {
    validate (key, value);

    if (!std::strcmp (key, BRUSH_FORE_COLOR))
      {
        foreColor = parseOGR_Color (value);
      }
    else if (!std::strcmp (key, BRUSH_BACK_COLOR))
      {
        backColor = parseOGR_Color (value);
      }
    else if (!std::strcmp (key, BRUSH_SIZE))
      {
        convertUnits (extractValue (value, size), size);
      }
    else if (!std::strcmp (key, BRUSH_ANGLE))
      {
        extractValue (value, angle);
      }
    else if (!std::strcmp (key, BRUSH_NAMES))
      {
        names = value;
      }
    else if (!std::strcmp (key, BRUSH_HORIZONTAL_SPACING))
      {
        convertUnits (extractValue (value, dx), dx);
      }
    else if (!std::strcmp (key, BRUSH_VERTICAL_SPACING))
      {
        convertUnits (extractValue (value, dy), dy);
      }
    else if (!std::strcmp (key, BRUSH_PRIORITY_LEVEL))
      {
        extractValue (value, priority);
      }
  }


const char* const Label::LABEL_FORE_COLOR ("c");
const char* const Label::LABEL_BACK_COLOR ("b");
const char* const Label::LABEL_OUTLINE_COLOR ("o");
const char* const Label::LABEL_SHADOW_COLOR ("h");
const char* const Label::LABEL_FONT_NAMES ("f");
const char* const Label::LABEL_FONT_SIZE ("s");
const char* const Label::LABEL_TEXT ("t");
const char* const Label::LABEL_BOLD ("bo");
const char* const Label::LABEL_ITALIC ("it");
const char* const Label::LABEL_UNDERLINE ("un");
const char* const Label::LABEL_STRIKEOUT ("st");
const char* const Label::LABEL_ANGLE ("a");
const char* const Label::LABEL_STRETCH ("w");
const char* const Label::LABEL_PLACEMENT ("m");
const char* const Label::LABEL_POSITION ("p");
const char* const Label::LABEL_HORIZONTAL_OFFSET ("dx");
const char* const Label::LABEL_VERTICAL_OFFSET ("dy");
const char* const Label::LABEL_PERPENDICULAR_OFFSET ("dp");
const char* const Label::LABEL_PRIORITY_LEVEL ("l");
// libtakengine additions
const char* const Label::LABEL_RELATIVE_ANGLE ("ra");


void
Label::setParam (const char* key,
                 const char* value)
  {
    validate (key, value);

    if (!std::strcmp (key, LABEL_TEXT))
      {
        text = value;
      }
    else if (!std::strcmp (key, LABEL_FONT_NAMES))
      {
        fontNames = value;
      }
    else if (!std::strcmp (key, LABEL_FONT_SIZE))
      {
        //XXX-- (iOS) extractValue fails because unit indicator
        //convertUnits (extractValue (value, fontSize), fontSize);
          fontSize = extractFontSize(value);
      }
    else if (!std::strcmp (key, LABEL_FORE_COLOR))
      {
        foreColor = parseOGR_Color (value);
      }
    else if (!std::strcmp (key, LABEL_BACK_COLOR))
      {
        backColor = parseOGR_Color (value);
      }
    else if (!std::strcmp (key, LABEL_OUTLINE_COLOR))
      {
        outlineColor = parseOGR_Color (value);
      }
    else if (!std::strcmp (key, LABEL_SHADOW_COLOR))
      {
        shadowColor = parseOGR_Color (value);
      }
    else if (!std::strcmp (key, LABEL_ANGLE))
      {
        extractValue (value, angle);
      }
    else if (!std::strcmp (key, LABEL_STRETCH))
      {
        extractValue (value, stretch);
      }
    else if (!std::strcmp (key, LABEL_PLACEMENT))
      {
        extractValue (value, placement);
      }
    else if (!std::strcmp (key, LABEL_POSITION))
      {
        extractValue (value, position);
      }
    else if (!std::strcmp (key, LABEL_HORIZONTAL_OFFSET))
      {
        convertUnits (extractValue (value, dx), dx);
      }
    else if (!std::strcmp (key, LABEL_VERTICAL_OFFSET))
      {
        convertUnits (extractValue (value, dy), dy);
      }
    else if (!std::strcmp (key, LABEL_PERPENDICULAR_OFFSET))
      {
        convertUnits (extractValue (value, dp), dp);
      }
    else if (!std::strcmp (key, LABEL_PRIORITY_LEVEL))
      {
        extractValue (value, priority);
      }
    else if (!std::strcmp (key, LABEL_BOLD))
      {
        extractValue (value, bold);
      }
    else if (!std::strcmp (key, LABEL_ITALIC))
      {
        extractValue (value, italic);
      }
    else if (!std::strcmp (key, LABEL_UNDERLINE))
      {
        extractValue (value, underline);
      }
    else if (!std::strcmp (key, LABEL_STRIKEOUT))
      {
        extractValue (value, strikeout);
      }
    // libtakengine additions
    else if (!std::strcmp (key, LABEL_RELATIVE_ANGLE))
      {
        extractValue (value, relativeAngle);
      }
  }


const char* const Pen::PEN_COLOR ("c");
const char* const Pen::PEN_WIDTH ("w");
const char* const Pen::PEN_PATTERN ("p");
const char* const Pen::PEN_NAMES ("id");
const char* const Pen::PEN_CAP ("cap");
const char* const Pen::PEN_JOIN ("j");
const char* const Pen::PEN_PERPENDICULAR_OFFSET ("dp");
const char* const Pen::PEN_PRIORITY_LEVEL ("l");


void
Pen::setParam (const char* key,
               const char* value)
  {
    validate (key, value);

    if (!std::strcmp (key, PEN_COLOR))
      {
        color = parseOGR_Color (value);
      }
    else if (!std::strcmp (key, PEN_WIDTH))
      {
          std::string valueString(value);
          if(valueString.length() > 2 && valueString.compare(valueString.size() - 2, 2, "px") == 0)
          {
              valueString = valueString.substr(0, valueString.length()-2);
              convertUnits (extractValue (valueString.c_str(), width), width);
          }
      }
    else if (!std::strcmp (key, PEN_PATTERN))
      {
        const char* ch = value;
        std::ostringstream sub;
        while (ch && *ch) {
            if (*ch >= '0' && *ch <= '9') {
                // append
                sub << *ch;
            } else if (sub.str().length()) {
                // emit
                unsigned px;
                extractValue(sub.str().c_str(), px);
                pattern.push_back(px);
                sub.str("");
            }
            ch++;
        }
        if(sub.str().length()) {
            // emit
            unsigned px;
            extractValue(sub.str().c_str(), px);
            pattern.push_back(px);
        }
      }
    else if (!std::strcmp (key, PEN_CAP))
      {
        extractValue (value, cap);
      }
    else if (!std::strcmp (key, PEN_NAMES))
      {
        names = value;
      }
    else if (!std::strcmp (key, PEN_JOIN))
      {
        extractValue (value, join);
      }
    else if (!std::strcmp (key, PEN_PERPENDICULAR_OFFSET))
      {
        convertUnits (extractValue (value, dp), dp);
      }
    else if (!std::strcmp (key, PEN_PRIORITY_LEVEL))
      {
        extractValue (value, priority);
      }
  }


const char* const Symbol::SYMBOL_COLOR ("c");
const char* const Symbol::SYMBOL_OUTLINE_COLOR ("o");
const char* const Symbol::SYMBOL_NAMES ("id");
const char* const Symbol::SYMBOL_ANGLE ("a");
const char* const Symbol::SYMBOL_SIZE ("s");
const char* const Symbol::SYMBOL_HORIZONTAL_OFFSET ("dx");
const char* const Symbol::SYMBOL_VERTICAL_OFFSET ("dy");
const char* const Symbol::SYMBOL_PERPENDICULAR_OFFSET ("dp");
const char* const Symbol::SYMBOL_SPACING_STEP ("ds");
const char* const Symbol::SYMBOL_SPACING_INITIAL ("di");
const char* const Symbol::SYMBOL_PRIORITY_LEVEL ("l");
// libtakengine additions
const char* const Symbol::SYMBOL_POSITION ("p");
const char* const Symbol::SYMBOL_RELATIVE_ANGLE ("ra");
const char* const Symbol::SYMBOL_SYMBOL_WIDTH ("sw");
const char* const Symbol::SYMBOL_SYMBOL_HEIGHT ("sh");


void
Symbol::setParam (const char* key,
                  const char* value)
  {
    validate (key, value);

    if (!std::strcmp (key, SYMBOL_NAMES))
      {
        names = value;
      }
    else if (!std::strcmp (key, SYMBOL_COLOR))
      {
        color = parseOGR_Color (value);
      }
    else if (!std::strcmp (key, SYMBOL_OUTLINE_COLOR))
      {
        outlineColor = parseOGR_Color (value);
      }
    else if (!std::strcmp (key, SYMBOL_ANGLE))
      {
        extractValue (value, angle);
      }
    else if (!std::strcmp (key, SYMBOL_SIZE))
      {
          std::string valueString(value);
          const char *units = nullptr;
          if(valueString.length() > 2 && valueString.compare(valueString.size() - 2, 2, "px") == 0)
          {
              valueString = valueString.substr(0, valueString.length()-2);
              units = "px";
          }
          const char *temp(extractValue (valueString.c_str(), size));
          if(!units)
          {
              units = temp;
          }

        if (units && *units)
          {
            convertUnits (units, size); // Size is absolute.
          }
        else
          {
            scaling = size;             // Size is relative.
            size = 0;
          }
      }
    else if (!std::strcmp (key, SYMBOL_HORIZONTAL_OFFSET))
      {
        convertUnits (extractValue (value, dx), dx);
      }
    else if (!std::strcmp (key, SYMBOL_VERTICAL_OFFSET))
      {
        convertUnits (extractValue (value, dy), dy);
      }
    else if (!std::strcmp (key, SYMBOL_PERPENDICULAR_OFFSET))
      {
        convertUnits (extractValue (value, dp), dp);
      }
    else if (!std::strcmp (key, SYMBOL_SPACING_STEP))
      {
        convertUnits (extractValue (value, ds), ds);
      }
    else if (!std::strcmp (key, SYMBOL_SPACING_INITIAL))
      {
        convertUnits (extractValue (value, di), di);
      }
    else if (!std::strcmp (key, SYMBOL_PRIORITY_LEVEL))
      {
        extractValue (value, priority);
      }
    // libtakengine additions
    else if (!std::strcmp (key, SYMBOL_RELATIVE_ANGLE))
      {
        extractValue (value, relativeAngle);
      }
    else if (!std::strcmp (key, SYMBOL_POSITION))
      {
        extractValue (value, position);
      }
    else if (!std::strcmp (key, SYMBOL_SYMBOL_WIDTH))
      {
          std::string valueString(value);
          const char *units = nullptr;
          if(valueString.length() > 2 && valueString.compare(valueString.size() - 2, 2, "px") == 0)
          {
              valueString = valueString.substr(0, valueString.length()-2);
              units = "px";
          }
          const char *temp(extractValue (valueString.c_str(), symbolWidth));
          if(!units)
          {
              units = temp;
          }

        if (units && *units)
          {
            convertUnits (units, symbolWidth); // Size is absolute.
          }
        else
          {
            // Size is pixels.
          }
      }
    else if (!std::strcmp (key, SYMBOL_SYMBOL_HEIGHT))
      {
          std::string valueString(value);
          const char *units = nullptr;
          if(valueString.length() > 2 && valueString.compare(valueString.size() - 2, 2, "px") == 0)
          {
              valueString = valueString.substr(0, valueString.length()-2);
              units = "px";
          }
          const char *temp(extractValue (valueString.c_str(), symbolHeight));
          if(!units)
          {
              units = temp;
          }

        if (units && *units)
          {
            convertUnits (units, symbolHeight); // Size is absolute.
          }
        else
          {
            // Size is pixels.
          }
      }
  }


}                                       // Close feature namespace.
}                                       // Close atakmap namespace.


////========================================================================////
////                                                                        ////
////    PROTECTED MEMBER FUNCTION DEFINITIONS                               ////
////                                                                        ////
////========================================================================////

////========================================================================////
////                                                                        ////
////    PRIVATE MEMBER FUNCTION DEFINITIONS                                 ////
////                                                                        ////
////========================================================================////

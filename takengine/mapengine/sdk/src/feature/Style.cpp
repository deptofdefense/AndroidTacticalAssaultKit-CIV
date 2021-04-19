#include "feature/Style.h"

#include <algorithm>
#include <cstdio>
#include <cstring>
#include <functional>
#include <iomanip>
#include <iostream>
#include <sstream>

#include "feature/DrawingTool.h"
#include "math/Utils.h"
#include "util/Memory.h"
#include "util/MathUtils.h"

using namespace atakmap;

using namespace TAK::Engine;
using namespace TAK::Engine::Util;

namespace
{

    class OGR_Color
    {
    public:
        OGR_Color (unsigned int color) :
            color (color)
        { }
    private:
        template <class _CharT, class _Traits>
        friend
        std::basic_ostream<_CharT, _Traits>& operator<< (std::basic_ostream<_CharT, _Traits>& strm, const OGR_Color& ogr)
        {
            std::ios::fmtflags oldFlags (strm.flags ());
            std::streamsize oldWidth (strm.width ());
            char oldFill (strm.fill ('0'));
            char fmt[10];
#ifdef MSVC
            _snprintf(fmt, 10, "#%06X%02X", (ogr.color & 0xFFFFFF), (ogr.color >> 24 & 0xFF));
#else
            snprintf(fmt, 10, "#%06X%02X", (ogr.color & 0xFFFFFF), (ogr.color >> 24 & 0xFF));
#endif
            strm << fmt;

            // Return stream with restored settings.
            strm.flags (oldFlags);
            return strm << std::setw (oldWidth) << std::setfill (oldFill);
        }

        unsigned int color;
    };

    template <typename T>
    struct VecDeleter
    {
        VecDeleter (const std::vector<T*>& vector) :
            vector (vector)
        { }

        static void deleteElement (T* elem)
        { delete elem; }

        void operator() () const
        {
            std::for_each (vector.begin (), vector.end (),
                       std::ptr_fun (deleteElement));
        }

        const std::vector<T*>& vector;
    };
}

namespace
{

    bool isNULL (const void* ptr)
    { return !ptr; }

    inline
    float pixelsToPoints (float pixels)
    {
        // This value of PPI is copied from convertUnits in DrawingTool.cpp.
        // Both functions should get the value from elsewhere.
        const double PPI (240);             // Need to get this from somewhere!!!

        return static_cast<float>(pixels * 72 / PPI);           // 72 points per inch.
    }

    // derived from https://codereview.stackexchange.com/questions/66711/greatest-common-divisor/66735
    template<class T>
    T gcd(const T a, const T b) NOTHROWS
    {
        return (a<b) ? gcd(b, a) : (!b ? a : gcd(b, a % b));
    }

    template<class T>
    T gcd(const std::vector<T>& values) NOTHROWS
    {
        T result = values[0];
        for (std::size_t i = 1u; i < values.size(); i++)
        {
            result = gcd(result, values[i]);
            if (result == 1)
                break;
        }
        return result;
    }
}

using namespace atakmap::feature;

Style::Style(const StyleClass styleClass_) NOTHROWS :
    styleClass(styleClass_)
{}
Style::~Style () NOTHROWS
  { }
StyleClass Style::getClass() const NOTHROWS
{
    return styleClass;
}
void Style::destructStyle(const Style *style)
{
    delete style;
}

Style* Style::parseStyle (const char* styleOGR)
{
    if (!styleOGR) {
        throw std::invalid_argument
                  ("atakmap::feature::Style::parseStyle: "
                   "Received NULL OGR style string");
    }

    const char* styleEnd (styleOGR+ std::strlen (styleOGR));
    std::vector<std::unique_ptr<Style>> styles;

    while (styleOGR!= styleEnd) {
        std::unique_ptr<DrawingTool> tool (DrawingTool::parseOGR_Tool (styleOGR, styleOGR));
        Brush* tmpBrush = (tool.get() && tool->getType() == DrawingTool::BRUSH) ? static_cast<Brush *>(tool.get()) : nullptr;
        Label* tmpLabel = (tool.get() && tool->getType() == DrawingTool::LABEL) ? static_cast<Label *>(tool.get()) : nullptr;
        Pen* tmpPen = (tool.get() && tool->getType() == DrawingTool::PEN) ? static_cast<Pen *>(tool.get()) : nullptr;
        Symbol* tmpSymbol = (tool.get() && tool->getType() == DrawingTool::SYMBOL) ? static_cast<Symbol *>(tool.get()) : nullptr;

        if (tmpBrush) {
            styles.emplace_back (std::unique_ptr<Style>(new BasicFillStyle (tmpBrush->foreColor)));
        } else if (tmpPen) {
            if (tmpPen->pattern.size()) {
                std::size_t len = tmpPen->pattern[0u];
                for (std::size_t i = 1u; i < tmpPen->pattern.size(); i++)
                    len += tmpPen->pattern[i];
                std::size_t factor;
                if (len >= 16) {
                    // the factor will be the total pattern length, divided by 16
                    factor = len / 16u;
                } else {
                    factor = 1u;
                }

                std::size_t bit = 0u;
                int16_t pattern = 0LL;
                for (std::size_t i = 0; i < tmpPen->pattern.size(); i++) {
                    std::size_t run = (tmpPen->pattern[i] / factor);
                    for (std::size_t j = 0; j < run; j++) {
                        if (i % 2 == 0)
                            pattern |= static_cast<int16_t>(0x1u) << bit;
                        bit++;
                    }
                }
                if (bit > 16)
                    Logger_log(TELL_Warning, "Stroke pattern overflow");
                else if(bit < 16)
                    Logger_log(TELL_Warning, "Stroke pattern underflow");
                styles.emplace_back(std::unique_ptr<Style>(new PatternStrokeStyle(
                    factor,
                    pattern,
                    tmpPen->color,
                    tmpPen->width)));
            } else {
                styles.emplace_back(std::unique_ptr<Style>(new BasicStrokeStyle(tmpPen->color,
                    tmpPen->width)));
            }
        } else if (tmpSymbol) {
            float scaling (tmpSymbol->scaling);
            float width (tmpSymbol->size);
            float height (tmpSymbol->size);
            if (tmpSymbol->symbolWidth && tmpSymbol->symbolHeight) {
                width = tmpSymbol->symbolWidth;
                height = tmpSymbol->symbolHeight;
            }
            IconPointStyle::HorizontalAlignment hAlign (IconPointStyle::H_CENTER);

            switch (tmpSymbol->position)
            {
            case Label::BASELINE_LEFT:
            case Label::CENTER_LEFT:
            case Label::TOP_LEFT:
            case Label::BOTTOM_LEFT:
                hAlign = IconPointStyle::RIGHT;
                break;
            case Label::BASELINE_RIGHT:
            case Label::CENTER_RIGHT:
            case Label::TOP_RIGHT:
            case Label::BOTTOM_RIGHT:
                hAlign = IconPointStyle::LEFT;
                break;
            default:
                break;
            }

            IconPointStyle::VerticalAlignment vAlign (IconPointStyle::V_CENTER);

            switch (tmpSymbol->position)
            {
            case Label::BASELINE_LEFT:
            case Label::BASELINE_CENTER:
            case Label::BASELINE_RIGHT:
            case Label::BOTTOM_LEFT:
            case Label::BOTTOM_CENTER:
            case Label::BOTTOM_RIGHT:
                vAlign = IconPointStyle::ABOVE;
                break;
            case Label::TOP_LEFT:
            case Label::TOP_CENTER:
            case Label::TOP_RIGHT:
                vAlign = IconPointStyle::BELOW;
                break;
            default:
                break;
            }

            const bool relativeRotation = (isnan(tmpSymbol->relativeAngle) && isnan(tmpSymbol->angle)) || !isnan(tmpSymbol->relativeAngle);
            if (isnan(tmpSymbol->angle))            tmpSymbol->angle = 0.0;
            if (isnan(tmpSymbol->relativeAngle))    tmpSymbol->relativeAngle = 0.0;

            if(!tmpSymbol->names.get()) {
                styles.emplace_back(std::unique_ptr<Style>(new BasicPointStyle(tmpSymbol->color, tmpSymbol->size)));
            } else {
                styles.emplace_back(std::unique_ptr<Style>(
                    scaling
                      ? new IconPointStyle(tmpSymbol->color,
                      tmpSymbol->names,
                      scaling,
                      hAlign,
                      vAlign,
                      !tmpSymbol->relativeAngle ? -tmpSymbol->angle : -tmpSymbol->relativeAngle, 
                      !tmpSymbol->relativeAngle)
                      : new IconPointStyle(tmpSymbol->color,
                      tmpSymbol->names,
                      width, height,
                      tmpSymbol->dx,
                      tmpSymbol->dy,
                      hAlign,
                      vAlign,
                      !relativeRotation ? -tmpSymbol->angle : -tmpSymbol->relativeAngle, 
                      !relativeRotation)));
            }
        } else if (tmpLabel) {
            // Label represents font size in pixels.  Convert to points.
            float fontSize (pixelsToPoints (tmpLabel->fontSize));
            LabelPointStyle::HorizontalAlignment hAlign (LabelPointStyle::H_CENTER);

            switch (tmpLabel->position)
            {
            case Label::BASELINE_LEFT:
            case Label::CENTER_LEFT:
            case Label::TOP_LEFT:
            case Label::BOTTOM_LEFT:
                hAlign = LabelPointStyle::RIGHT;
                break;
            case Label::BASELINE_RIGHT:
            case Label::CENTER_RIGHT:
            case Label::TOP_RIGHT:
            case Label::BOTTOM_RIGHT:
                hAlign = LabelPointStyle::LEFT;
                break;
            default:
                break;
            }

            LabelPointStyle::VerticalAlignment vAlign (LabelPointStyle::V_CENTER);

            switch (tmpLabel->position)
            {
            case Label::BASELINE_LEFT:
            case Label::BASELINE_CENTER:
            case Label::BASELINE_RIGHT:
            case Label::BOTTOM_LEFT:
            case Label::BOTTOM_CENTER:
            case Label::BOTTOM_RIGHT:
                vAlign = LabelPointStyle::ABOVE;
                break;
            case Label::TOP_LEFT:
            case Label::TOP_CENTER:
            case Label::TOP_RIGHT:
                vAlign = LabelPointStyle::BELOW;
                break;
            default:
                break;
            }

            // Determine ScrollMode from OGR Label placement mode.  See
            // LabelPointStyle::toOGR below.
            LabelPointStyle::ScrollMode scrollMode (LabelPointStyle::DEFAULT);

            switch (tmpLabel->placement)
              {
              case Label::STRETCHED:

                scrollMode = LabelPointStyle::ON;
                break;

              case Label::MIDDLE:

                scrollMode = LabelPointStyle::OFF;
                break;

              default:
                break;
            }

            unsigned style = 0u;
            if (tmpLabel->bold)
                style |= LabelPointStyle::BOLD;
            if (tmpLabel->italic)
                style |= LabelPointStyle::ITALIC;
            if (tmpLabel->strikeout)
                style |= LabelPointStyle::STRIKETHROUGH;
            if (tmpLabel->underline)
                style |= LabelPointStyle::UNDERLINE;

            const char *face = nullptr;
            if (tmpLabel->fontNames) {
                // truncate to the first font name
                const char* sep = strstr(tmpLabel->fontNames, ",");
                if (sep)
                    tmpLabel->fontNames.get()[sep - tmpLabel->fontNames.get()] = '\0';
                face = tmpLabel->fontNames;
            }
            if(!tmpLabel->text)
                tmpLabel->text = "";

            const bool relativeRotation = (isnan(tmpLabel->relativeAngle) && isnan(tmpLabel->angle)) || !isnan(tmpLabel->relativeAngle);
            if (isnan(tmpLabel->angle))         tmpLabel->angle = 0.0;
            if (isnan(tmpLabel->relativeAngle)) tmpLabel->relativeAngle = 0.0;

            styles.emplace_back (std::unique_ptr<Style>(new LabelPointStyle (tmpLabel->text,
                                                   tmpLabel->foreColor,
                                                   tmpLabel->backColor,
                                                   tmpLabel->outlineColor,
                                                   scrollMode,
                                                   face,
                                                   fontSize,
                                                   (LabelPointStyle::Style)style,
                                                   tmpLabel->dx,
                                                   tmpLabel->dy,
                                                   hAlign,
                                                   vAlign,
                                                   !relativeRotation ? -tmpLabel->angle : -tmpLabel->relativeAngle, 
                                                   !relativeRotation,
                                                   tmpLabel->stretch / 100.0f)));
        }
    }

    Style* result (nullptr);

    if (styles.size () == 1) {
        result = styles[0].release();
    } else if (styles.size () > 1) {
        std::vector<Style *> compositeStyles;
        compositeStyles.reserve(styles.size());
        for (std::size_t i = 0u; i < styles.size(); i++)
            compositeStyles.push_back(styles[i].release());
        result = new CompositeStyle (compositeStyles);
    }

    return result;
}

BasicFillStyle::BasicFillStyle (unsigned int color) NOTHROWS :
    Style(TESC_BasicFillStyle),
    color (color)
{ }
TAKErr BasicFillStyle::toOGR(Port::String &value) const NOTHROWS
{
    std::ostringstream strm;
    strm << "BRUSH(fc:" << OGR_Color(color) << ")";
    value = strm.str().c_str();
    return TE_Ok;
}
Style *BasicFillStyle::clone() const
{
    return new BasicFillStyle(*this);
}

BasicPointStyle::BasicPointStyle (unsigned int color, float size) :
    Style(TESC_BasicPointStyle),
    color (color),
    size (size)
{
    if (size < 0) {
        throw std::invalid_argument
                  ("atakmap::feature::BasicPointStyle::BasicPoinStyle: "
                   "Received negative size");
    }
}
TAKErr BasicPointStyle::toOGR(Port::String &value) const NOTHROWS
{
    std::ostringstream strm;
    strm << "SYMBOL(c:" << OGR_Color(color) << ",s:" << size << "px)";
    value = strm.str().c_str();
    return TE_Ok;
}
Style * BasicPointStyle::clone() const
{
  return new BasicPointStyle(*this);
}

BasicStrokeStyle::BasicStrokeStyle (unsigned int color, float width) :
    Style(TESC_BasicStrokeStyle),
    color (color),
    width (width)
{
    if (width < 0) {
        throw std::invalid_argument
                  ("atakmap::feature::BasicStrokeStyle::BasicStrokeStyle: "
                   "Received negative width");
    }
}
TAKErr BasicStrokeStyle::toOGR(Port::String &value) const NOTHROWS
{
    std::ostringstream strm;
	strm << "PEN(c:" << OGR_Color(color) << ",w:" << width << "px)";
    value = strm.str().c_str();
    return TE_Ok;
}
Style *BasicStrokeStyle::clone() const
{
    return new BasicStrokeStyle(*this);
}


CompositeStyle::CompositeStyle (const std::vector<Style*>& styles) :
    Style(TESC_CompositeStyle)
{
    if (styles.empty ()) {
        throw std::invalid_argument
                  ("atakmap::feature::CompositeStyle::CompositeStyle: "
                   "Received empty Style vector");
    }

    this->styles_.reserve(styles.size());
    for(std::size_t i = 0u; i < styles.size(); i++) {
        if(styles[i])
            this->styles_.push_back(std::shared_ptr<Style>(styles[i]));
        else
            throw std::invalid_argument
                  ("atakmap::feature::CompositeStyle::CompositeStyle: "
                   "Received NULL Style");
    }
}
const Style& CompositeStyle::getStyle (std::size_t index) const throw (std::range_error)
{
    if (index >= styles_.size ()) {
        throw std::range_error
                  ("atakmap::feature::CompositeStyle::getStyle: "
                   "Received out-of-range index");
    }
    return *styles_[index];
}
TAKErr CompositeStyle::toOGR(Port::String &value) const NOTHROWS
{
    TAKErr code(TE_Ok);
    std::ostringstream strm;
    auto iter (styles_.begin ());
    auto end (styles_.end ());

    TAK::Engine::Port::String substyle;

    code = (*iter)->toOGR(substyle);
    TE_CHECKRETURN_CODE(code);
    strm << substyle;
    while (++iter != end) {
        substyle = nullptr;
        code = (*iter)->toOGR(substyle);
        TE_CHECKRETURN_CODE(code);
        strm << ";" << substyle;
    }

    value = strm.str().c_str();
    return TE_Ok;
}
Style *CompositeStyle::clone() const
{
    std::vector<Style *> styles;
    styles.reserve(this->getStyleCount());
    for (size_t i = 0; i < this->getStyleCount(); ++i) {
        styles.push_back(this->getStyle(i).clone());
    }
    return new CompositeStyle(styles);
}

IconPointStyle::IconPointStyle (unsigned int color,
                                const char* iconURI,
                                float scaling,
                                HorizontalAlignment hAlign,
                                VerticalAlignment vAlign,
                                float rotation,
                                bool absoluteRotation) :
    Style(TESC_IconPointStyle),
    color (color),
    iconURI (iconURI),
    hAlign (hAlign),
    vAlign (vAlign),
    scaling (scaling == 0 ? 1.0f : scaling),
    width (0),
    height (0),
    rotation (rotation),
    absoluteRotation (absoluteRotation),
    offsetX(0.0),
    offsetY(0.0)
{

    if (!iconURI) {
        throw std::invalid_argument
                  ("atakmap::feature::IconPointStyle::IconPointStyle: "
                   "Received NULL icon URI");
    }
    if (scaling < 0) {
        throw std::invalid_argument
                  ("atakmap::feature::IconPointStyle::IconPointStyle: "
                   "Received negative scaling");
    }
}
IconPointStyle::IconPointStyle (unsigned int color,
                                const char* iconURI,
                                float width,
                                float height,
                                HorizontalAlignment hAlign,
                                VerticalAlignment vAlign,
                                float rotation,
                                bool absoluteRotation) :
    Style(TESC_IconPointStyle),
    color (color),
    iconURI (iconURI),
    hAlign (hAlign),
    vAlign (vAlign),
    scaling (!width && !height ? 1.0f : 0.0f),
    width (width),
    height (height),
    rotation (rotation),
    absoluteRotation (absoluteRotation),
    offsetX(0.0),
    offsetY(0.0)
{
    if (!iconURI) {
        throw std::invalid_argument
                  ("atakmap::feature::IconPointStyle::IconPointStyle: "
                   "Received NULL icon URI");
    }
    if (width < 0) {
        throw std::invalid_argument
                  ("atakmap::feature::IconPointStyle::IconPointStyle: "
                   "Received negative width");
    }
    if (height < 0) {
        throw std::invalid_argument
                  ("atakmap::feature::IconPointStyle::IconPointStyle: "
                   "Received negative height");
    }
}
IconPointStyle::IconPointStyle (unsigned int color,
                                const char* iconURI,
                                float width,
                                float height,
                                float offsetX,
                                float offsetY,
                                HorizontalAlignment hAlign,
                                VerticalAlignment vAlign,
                                float rotation,
                                bool absoluteRotation) :
    Style(TESC_IconPointStyle),
    color (color),
    iconURI (iconURI),
    hAlign (hAlign),
    vAlign (vAlign),
    scaling (!width && !height ? 1.0f : 0.0f),
    width (width),
    height (height),
    rotation (rotation),
    absoluteRotation (absoluteRotation),
    offsetX(offsetX),
    offsetY(offsetY)
{
    if (!iconURI) {
        throw std::invalid_argument
                  ("atakmap::feature::IconPointStyle::IconPointStyle: "
                   "Received NULL icon URI");
    }
    if (width < 0) {
        throw std::invalid_argument
                  ("atakmap::feature::IconPointStyle::IconPointStyle: "
                   "Received negative width");
    }
    if (height < 0) {
        throw std::invalid_argument
                  ("atakmap::feature::IconPointStyle::IconPointStyle: "
                   "Received negative height");
    }
}
TAKErr IconPointStyle::toOGR(Port::String &value) const NOTHROWS
{
    std::ostringstream strm;
    float size (std::max (width, height));

    strm << "SYMBOL(id:\"" << iconURI << "\"";
    if (rotation) {
        if(absoluteRotation)
            strm << ",a:" << -rotation;     // OGR uses CCW degrees.
        else
            strm << ",ra:" << -rotation;     // OGR uses CCW degrees.
    }
	strm << ",c:" << OGR_Color(color);
    if (!scaling) {
        strm << ",sw:" << width << "px";
        strm << ",sh:" << height << "px"; // Units means size, not scaling.
    } else if(scaling && scaling != 1.0) {
        strm << ",s:" << scaling;       // No units means scaling, not size.
    }

    unsigned anchorPosition = 5;
    switch (hAlign)
    {
    case LEFT:  anchorPosition += 1;    break;
    case RIGHT: anchorPosition -= 1;    break;
    default:                            break;
    }
    switch (vAlign)
    {
    case ABOVE: anchorPosition += 6;    break;
    case BELOW: anchorPosition += 3;    break;
    default:                            break;
    }
    if (anchorPosition != 5) {
        strm << ",p:" << anchorPosition;
    }
    if(offsetX)
        strm << ",dx:" << offsetX << "px";
    if(offsetY)
        strm << ",dy:" << offsetY << "px";
    strm << ")";

    value = strm.str().c_str();
    return TE_Ok;
}
Style *IconPointStyle::clone() const
{
  return new IconPointStyle(*this);
}

LabelPointStyle::LabelPointStyle (const char* text,
                                  unsigned int textColor,
                                  unsigned int backColor,
                                  ScrollMode mode,
                                  const char *face,
                                  float textSize,
                                  LabelPointStyle::Style style,
                                  float offsetX,
                                  float offsetY,
                                  HorizontalAlignment hAlign,
                                  VerticalAlignment vAlign,
                                  float rotation,
                                  bool absoluteRotation,
                                  float paddingX,
                                  float paddingY,
                                  double labelMinRenderResolution,
                                  float labelScale) :
    atakmap::feature::Style(TESC_LabelPointStyle),
    text (text),
    foreColor (textColor),
    backColor (backColor),
    scrollMode (mode),
    hAlign (hAlign),
    vAlign (vAlign),
    textSize (textSize ? textSize : 16),        // ATAK hack
    rotation (rotation),
    paddingX(paddingX),
    paddingY(paddingY),
    absoluteRotation (absoluteRotation),
    labelMinRenderResolution (labelMinRenderResolution),
    labelScale (labelScale),
    offsetX(offsetX),
    offsetY(offsetY),
    face(face),
    style(style),
    outlineColor(0u)
  {
    if (!text) {
        throw std::invalid_argument
                  ("atakmap::feature::LabelPointStyle::LabelPointStyle: "
                   "Received NULL label text");
    }
    if (textSize < 0) {
        throw std::invalid_argument
                  ("atakmap::feature::LabelPointStyle::LabelPointStyle: "
                   "Received negative textSize");
    }
}
LabelPointStyle::LabelPointStyle (const char* text,
                                  unsigned int textColor,
                                  unsigned int backColor,
                                  unsigned int outlineColor,
                                  ScrollMode mode,
                                  const char *face,
                                  float textSize,
                                  LabelPointStyle::Style style,
                                  float offsetX,
                                  float offsetY,
                                  HorizontalAlignment hAlign,
                                  VerticalAlignment vAlign,
                                  float rotation,
                                  bool absoluteRotation,
                                  float paddingX,
                                  float paddingY,
                                  double labelMinRenderResolution,
                                  float labelScale) :
    atakmap::feature::Style(TESC_LabelPointStyle),
    text (text),
    foreColor (textColor),
    backColor (backColor),
    scrollMode (mode),
    hAlign (hAlign),
    vAlign (vAlign),
    textSize (textSize ? textSize : 16),        // ATAK hack
    rotation (rotation),
    paddingX(paddingX),
    paddingY(paddingY),
    absoluteRotation (absoluteRotation),
    labelMinRenderResolution (labelMinRenderResolution),
    labelScale (labelScale),
    offsetX(offsetX),
    offsetY(offsetY),
    face(face),
    style(style),
    outlineColor(outlineColor)
  {
    if (!text) {
        throw std::invalid_argument
                  ("atakmap::feature::LabelPointStyle::LabelPointStyle: "
                   "Received NULL label text");
    }
    if (textSize < 0) {
        throw std::invalid_argument
                  ("atakmap::feature::LabelPointStyle::LabelPointStyle: "
                   "Received negative textSize");
    }
}
TAKErr LabelPointStyle::toOGR(Port::String &value) const NOTHROWS
{
    std::ostringstream strm;

    strm << "LABEL(t:\"";
    if (text) {
        for (int i = 0u; i < static_cast<int>(strlen(text)); i++) {
            const char c = text[i];
            if (c == '"')
                strm << '\\';
            strm << c;
        }
    }
    strm << "\""
         << ",s:" << textSize << "pt";
    if (style & LabelPointStyle::BOLD)
        strm << ",bo:1";
    if (style & LabelPointStyle::ITALIC)
        strm << ",it:1";
    if (style & LabelPointStyle::UNDERLINE)
        strm << ",un:1";
    if (style & LabelPointStyle::STRIKETHROUGH)
        strm << ",st:1";
    if (face)
        strm << ",f:\"" << face << "\"";
    if (rotation) {
        if (absoluteRotation)
            strm << ",a:" << -rotation;     // OGR uses CCW degrees.
        else
            strm << ",ra:" << -rotation;
    }
    strm << ",c:" << OGR_Color (foreColor)
         << ",b:" << OGR_Color (backColor);
    if (outlineColor & 0xFF000000)
        strm << ",o:" << OGR_Color(outlineColor);

    // Co-opt the OGR Label placement mode to specify the scroll mode.  The
    // co-opted placements normally apply to polylines.
    //
    //  ScrollMode::DEFAULT     ==>     Label::DEFAULT
    //  ScrollMode::ON          ==>     Label::STRETCHED
    //  ScrollMode::OFF         ==>     Label::MIDDLE
    Label::Placement placement (Label::DEFAULT);

    switch (scrollMode)
    {
    case ON:  placement = Label::STRETCHED; break;
    case OFF: placement = Label::MIDDLE;    break;
    default:                                break;
    }
    (strm << ",m:").put (placement);

    // Translate the horizontal and vertical alignments into an OGR Label anchor
    // position number.  We use baseline rather than bottom vertical alignments
    // for labels that are to be above the point.
    unsigned short position (0);

    switch (hAlign)
    {
    case LEFT:  position = 6;   break;
    case RIGHT: position = 4;   break;
    default:    position = 5;   break;
    }
    switch (vAlign)
    {
    case ABOVE: position += 6;  break;  // Adjust to baseline values.
    case BELOW: position += 3;  break;  // Adjust to top values.
    default:                    break;
    }
    if (offsetX)
        strm << ",dx:" << offsetX << "px";
    if (offsetY)
        strm << ",dy:" << offsetY << "px";
    strm << ",p:" << position << ")";

    value = strm.str().c_str();
    return TE_Ok;
}
Style *LabelPointStyle::clone() const
{
    return new LabelPointStyle(*this);
}

PatternStrokeStyle::PatternStrokeStyle (const std::size_t factor_,
                    const uint16_t pattern_,
                    const unsigned int color_,
                    const float strokeWidth_) :
    Style(TESC_PatternStrokeStyle),
    pattern(pattern_),
    factor(factor_),
    color(color_),
    width(strokeWidth_)
{
    if(!factor)
        throw std::invalid_argument("bad pattern length");
    if(width < 0.0)
        throw std::invalid_argument("invalid stroke width");
}
uint16_t PatternStrokeStyle::getPattern() const NOTHROWS
{
    return pattern;
}
std::size_t PatternStrokeStyle::getFactor() const NOTHROWS
{
    return factor;
}
unsigned int PatternStrokeStyle::getColor () const NOTHROWS
{
    return color;
}
float PatternStrokeStyle::getStrokeWidth() const NOTHROWS
{
    return width;
}
TAKErr PatternStrokeStyle::toOGR(TAK::Engine::Port::String &value) const NOTHROWS
{
    std::ostringstream ogr;
    //PEN(c:#FF0000,w:2px,p:”4px 5px”)
    ogr << "PEN(c:" << OGR_Color (color) << ",w:" << width << "px,p:\"";
    uint8_t state = 0x1;
    std::size_t stateCount = 0;
    uint64_t pat = pattern;
    std::size_t emit = 0;
    for(std::size_t i = 0u; i < 16u; i++) {
        const uint8_t px = pat&0x1u;
        pat >>= 0x1u;
        if(px != state) {
            // the state has flipped, emit the pixel count
            if(!emit)
                ogr << ' ';
            ogr << (stateCount*factor) << "px";
            emit++;
            state = px;
            stateCount = 0u;
        }

        // update pixel count for current state
        stateCount++;
    }
    if(stateCount) {
        if(!emit)
            ogr << ' ';
        ogr << (stateCount*factor) << "px";
    }
    ogr << "\")";
    value = ogr.str().c_str();
    return TE_Ok;
}
Style *PatternStrokeStyle::clone() const
{
    return new PatternStrokeStyle(*this);
}

TAKErr atakmap::feature::Style_parseStyle(StylePtr &value, const char *ogr) NOTHROWS
{
    try {
        value = StylePtr(Style::parseStyle(ogr), Style::destructStyle);
        return !value ? TE_InvalidArg : TE_Ok;
    } catch(const std::invalid_argument &) {
        return TE_InvalidArg;
    } catch(const std::bad_alloc &) {
        return TE_OutOfMemory;
    } catch(...) {
        return TE_Err;
    }
}
TAKErr atakmap::feature::BasicFillStyle_create(StylePtr &value, const unsigned int color) NOTHROWS
{
    try {
        value = StylePtr(new BasicFillStyle(color), Memory_deleter_const<Style, BasicFillStyle>);
        return TE_Ok;
    } catch(const std::invalid_argument &) {
        return TE_InvalidArg;
    } catch(const std::bad_alloc &) {
        return TE_OutOfMemory;
    } catch(...) {
        return TE_Err;
    }
}
TAKErr atakmap::feature::BasicPointStyle_create(StylePtr &value, const unsigned int color, const float size) NOTHROWS
{
    try {
        value = StylePtr(new BasicPointStyle(color, size), Memory_deleter_const<Style, BasicPointStyle>);
        return TE_Ok;
    } catch(const std::invalid_argument &) {
        return TE_InvalidArg;
    } catch(const std::bad_alloc &) {
        return TE_OutOfMemory;
    } catch(...) {
        return TE_Err;
    }
}
TAKErr atakmap::feature::BasicStrokeStyle_create(StylePtr &value, const unsigned int color, const float width) NOTHROWS
{
    try {
        value = StylePtr(new BasicStrokeStyle(color, width), Memory_deleter_const<Style, BasicStrokeStyle>);
        return TE_Ok;
    } catch(const std::invalid_argument &) {
        return TE_InvalidArg;
    } catch(const std::bad_alloc &) {
        return TE_OutOfMemory;
    } catch(...) {
        return TE_Err;
    }
}
TAKErr atakmap::feature::CompositeStyle_create(StylePtr &value, const Style **styles, const std::size_t count) NOTHROWS
{
    try {
        std::vector<Style *> styles_v;
        styles_v.reserve(count);
        for(std::size_t i = 0u; i < count; i++) {
            if(!styles[i])
                return TE_InvalidArg;
            styles_v.push_back(styles[i]->clone());
        }
        value = StylePtr(new CompositeStyle(styles_v), Memory_deleter_const<Style, CompositeStyle>);
        return TE_Ok;
    } catch(const std::invalid_argument &) {
        return TE_InvalidArg;
    } catch(const std::bad_alloc &) {
        return TE_OutOfMemory;
    } catch(...) {
        return TE_Err;
    }
}
TAKErr atakmap::feature::IconPointStyle_create(StylePtr &value, unsigned int color,
                                                                const char* iconURI,
                                                                float scaleFactor,
                                                                IconPointStyle::HorizontalAlignment hAlign,
                                                                IconPointStyle::VerticalAlignment vAlign,
                                                                float rotation,
                                                                bool absoluteRotation) NOTHROWS
{
    try {
        value = StylePtr(new IconPointStyle(color, iconURI, scaleFactor, hAlign, vAlign, rotation, absoluteRotation), Memory_deleter_const<Style, IconPointStyle>);
        return TE_Ok;
    } catch(const std::invalid_argument &) {
        return TE_InvalidArg;
    } catch(const std::bad_alloc &) {
        return TE_OutOfMemory;
    } catch(...) {
        return TE_Err;
    }
}
TAKErr atakmap::feature::IconPointStyle_create(StylePtr &value, unsigned int color,
                                                                            const char* iconURI,
                                                                            float width,
                                                                            float height,
                                                                            IconPointStyle::HorizontalAlignment hAlign,
                                                                            IconPointStyle::VerticalAlignment vAlign,
                                                                            float rotation,
                                                                            bool absoluteRotation) NOTHROWS
{
    try {
        value = StylePtr(new IconPointStyle(color, iconURI, width, height, hAlign, vAlign, rotation, absoluteRotation), Memory_deleter_const<Style, IconPointStyle>);
        return TE_Ok;
    } catch(const std::invalid_argument &) {
        return TE_InvalidArg;
    } catch(const std::bad_alloc &) {
        return TE_OutOfMemory;
    } catch(...) {
        return TE_Err;
    }
}
TAKErr atakmap::feature::LabelPointStyle_create(StylePtr &value, const char* text,
                                                                 unsigned int textColor,    // 0xAARRGGBB
                                                                 unsigned int backColor,    // 0xAARRGGBB
                                                                 LabelPointStyle::ScrollMode mode,
                                                                 float textSize,      // Use system default size.
                                                                 LabelPointStyle::HorizontalAlignment hAlign,
                                                                 LabelPointStyle::VerticalAlignment vAlign,
                                                                 float rotation,      // 0 degrees of rotation.
                                                                 bool absoluteRotation, // Relative to screen.
                                                                 float paddingX, // offset from alignment position
                                                                 float paddingY, 
                                                                 double labelMinRenderResolution,
                                                                 float labelScale) NOTHROWS
{
    try {

        value = StylePtr(new LabelPointStyle(text, textColor, backColor, mode, nullptr, textSize, (LabelPointStyle::Style)0, 0.0, 0.0, hAlign, vAlign, rotation, absoluteRotation, paddingX, paddingY, labelMinRenderResolution, labelScale), Memory_deleter_const<Style, LabelPointStyle>);
        return TE_Ok;
    } catch(const std::invalid_argument &) {
        return TE_InvalidArg;
    } catch(const std::bad_alloc &) {
        return TE_OutOfMemory;
    } catch(...) {
        return TE_Err;
    }
}
TAKErr atakmap::feature::PatternStrokeStyle_create(StylePtr &value, const std::size_t factor,
                                                                    const uint16_t pattern,
                                                                    const unsigned int color,
                                                                    const float strokeWidth) NOTHROWS
{
    try {
        value = StylePtr(new PatternStrokeStyle(factor, pattern, color, strokeWidth), Memory_deleter_const<Style, PatternStrokeStyle>);
        return TE_Ok;
    } catch(const std::invalid_argument &) {
        return TE_InvalidArg;
    } catch(const std::bad_alloc &) {
        return TE_OutOfMemory;
    } catch(...) {
        return TE_Err;
    }
}

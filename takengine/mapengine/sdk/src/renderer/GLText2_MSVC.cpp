#include "renderer/GLText2.h"

#ifdef _MSC_VER

#include <codecvt>
#include <Windows.h>
#include <ObjIdl.h>

#pragma warning(push)
#pragma warning(disable : 4458)
#ifdef NOMINMAX
#define min(a, b) ((a)<(b) ? (a) : (b))
#define max(a, b) ((a)<(b) ? (a) : (b))
#include <gdiplus.h>
#pragma comment (lib, "Gdiplus.lib")
#include <gdiplusheaders.h>
#include <gdiplusfont.h>
#include <gdipluspixelformats.h>
#undef min
#undef max
#else
#include <gdiplus.h>
#pragma comment (lib, "Gdiplus.lib")
#include <gdiplusheaders.h>
#include <gdipluspixelformats.h>
#endif
#pragma warning(pop)

#include <algorithm>

#include "renderer/impl/BitmapAdapter_MSVC.h"
#include "thread/Lock.h"
#include "thread/Mutex.h"

using namespace TAK::Engine::Renderer;

using namespace TAK::Engine::Renderer::Impl;
using namespace TAK::Engine::Thread;
using namespace TAK::Engine::Util;


namespace
{
    Mutex systemDefaultTextFmtsMutex;
    std::set<std::string> defaultedFonts;
    std::map<std::string, std::map<float, std::map<Gdiplus::FontStyle, TAK::Engine::Renderer::TextFormat2 *>>> systemDefaultTextFmts2;

    class CLITextFormat2 : public TextFormat2
    {
    private:
        typedef struct char_metrics_t {
            float offx;
            float offy;
            float width;
            float height;
        } char_metrics_t;
    private:
        std::unique_ptr<Gdiplus::Font> font_;

        static const unsigned char COMMON_CHAR_START = 32; // ASCII
        static const unsigned char COMMON_CHAR_END = 254;

        char_metrics_t common_char_metrics_[COMMON_CHAR_END - COMMON_CHAR_START + 1];
        std::map<unsigned int, char_metrics_t> char_metrics_;

        static char_metrics_t MeasureDisplayStringBounds(const wchar_t *text, std::size_t index, std::size_t length, Gdiplus::Font &font);

        float char_height_;
        int font_size_;
        float descent_;
        Mutex mutex_;
    public:
        CLITextFormat2(std::unique_ptr<Gdiplus::Font> &&font);
    public:
        float getStringWidth(const char *text) NOTHROWS override;
        float getCharPositionWidth(const char *text, int position) NOTHROWS override;
        float getCharWidth(const unsigned int chr) NOTHROWS override;
        float getCharHeight() NOTHROWS override;
        float getDescent() NOTHROWS override;
        float getStringHeight(const char *) NOTHROWS override;
        float getBaselineSpacing() NOTHROWS override;
        int getFontSize() NOTHROWS override;

        TAKErr loadGlyph(BitmapPtr &value, const unsigned int c) NOTHROWS override;
    private:
        void getCharMetrics(char_metrics_t *metrics, const unsigned int c);
    private:
        int designUnitsToPixels(float value);
    };

    std::wstring getWideString(unsigned int c);

    float computeDescent(Gdiplus::Font &font, Gdiplus::FontStyle style);

    void configureStringFormat(Gdiplus::StringFormat &format);

    float GetSizeInPoints(const Gdiplus::Font &font)
    {
        // https://referencesource.microsoft.com/#System.Drawing/commonui/System/Drawing/Advanced/Font.cs
        if (font.GetUnit() == Gdiplus::UnitPoint) {
            return font.GetSize();
        } else {
            float emHeightInPoints;

            class DCHandle
            {
            public :
                DCHandle() :
                    handle(GetDC(nullptr))
                {}
                ~DCHandle()
                {
                    ReleaseDC(nullptr, handle);
                }
            public :
                HDC handle;
            };

            DCHandle dc;
            std::unique_ptr<Gdiplus::Graphics> graphics(Gdiplus::Graphics::FromHDC(dc.handle));
            auto pixelsPerPoint = (float)(graphics->GetDpiY() / 72.0);
            float lineSpacingInPixels = font.GetHeight(graphics.get());
            Gdiplus::FontFamily fontFamily;
            font.GetFamily(&fontFamily);
            float emHeightInPixels = lineSpacingInPixels * fontFamily.GetEmHeight(font.GetStyle()) / fontFamily.GetLineSpacing(font.GetStyle());

            emHeightInPoints = emHeightInPixels / pixelsPerPoint;

            return emHeightInPoints;
        }
    }
}


TAKErr TAK::Engine::Renderer::TextFormat2_createDefaultSystemTextFormat(TAK::Engine::Renderer::TextFormat2Ptr& value, float size) NOTHROWS
{
    return TextFormat2_createTextFormat(value, TextFormatParams(size));
}
TAKErr TAK::Engine::Renderer::TextFormat2_createTextFormat(TAK::Engine::Renderer::TextFormat2Ptr &value, const TextFormatParams &params) NOTHROWS
{
    if (params.size <= 0)
        return TE_InvalidArg;

    Lock lock(systemDefaultTextFmtsMutex);
    std::string fontName;
    if (params.fontName)
        fontName = params.fontName;
    else
        fontName = "Arial";
    if (defaultedFonts.find(fontName) != defaultedFonts.end())
        fontName = "Arial";

    unsigned int style = 0u;
    if (params.bold)
        style |= Gdiplus::FontStyleBold;
    if (params.italic)
        style |= Gdiplus::FontStyleItalic;
    if (params.underline)
        style |= Gdiplus::FontStyleUnderline;
    if (params.strikethrough)
        style |= Gdiplus::FontStyleStrikeout;
    if (!style)
        style = Gdiplus::FontStyleRegular;

    do {
        auto entry = systemDefaultTextFmts2.find(fontName);
        if (entry == systemDefaultTextFmts2.end())
            break;
        auto e0 = entry->second.find(params.size);
        if (e0 == entry->second.end())
            break;
        auto e1 = e0->second.find(static_cast<Gdiplus::FontStyle>(style));
        if (e1 == e0->second.end())
            break;
        value = TAK::Engine::Renderer::TextFormat2Ptr(e1->second, TAK::Engine::Util::Memory_leaker_const<TAK::Engine::Renderer::TextFormat2>);
        return TE_Ok;
    } while (false);

    array_ptr<wchar_t> fontName_w(new wchar_t[fontName.length() + 1]);
    std::size_t convertedChars;
    mbstowcs_s(&convertedChars, fontName_w.get(), fontName.length() + 1u, fontName.c_str(), _TRUNCATE);
    std::unique_ptr<Gdiplus::Font> font(new Gdiplus::Font(fontName_w.get(), params.size, static_cast<Gdiplus::FontStyle>(style)));
    if (!font->IsAvailable()) {
        defaultedFonts.insert(fontName);
        font.reset(new Gdiplus::Font(L"Arial", params.size, static_cast<Gdiplus::FontStyle>(style)));
    }

    TAK::Engine::Renderer::TextFormat2 *retval = new CLITextFormat2(std::move(font));
    systemDefaultTextFmts2[fontName][params.size][static_cast<Gdiplus::FontStyle>(style)] = retval;
    value = TAK::Engine::Renderer::TextFormat2Ptr(retval, TAK::Engine::Util::Memory_leaker_const<TAK::Engine::Renderer::TextFormat2>);
    return TE_Ok;
}

namespace
{
    CLITextFormat2::char_metrics_t CLITextFormat2::MeasureDisplayStringBounds(const wchar_t *text, std::size_t index, std::size_t length, Gdiplus::Font &font)
    {
        Gdiplus::Bitmap bitmap(1, 1, PixelFormat32bppARGB);
        std::unique_ptr<Gdiplus::Graphics> graphics(Gdiplus::Graphics::FromImage(&bitmap));

        graphics->SetTextRenderingHint(Gdiplus::TextRenderingHintAntiAlias);
        Gdiplus::StringFormat format;
        configureStringFormat(format);

        Gdiplus::RectF rect(0, 0, 1000, 1000);
        Gdiplus::CharacterRange ranges(static_cast<INT>(index), static_cast<INT>(length));

        format.SetMeasurableCharacterRanges(1u, &ranges);

        Gdiplus::Region region;
        graphics->MeasureCharacterRanges(text, 1, &font, rect, &format, 1, &region);
        region.GetBounds(&rect, graphics.get());

        char_metrics_t retval;
        retval.offx = rect.X;
        retval.offy = rect.Y;
        retval.width = rect.Width;
        retval.height = rect.Height;

        return retval;
    }

    CLITextFormat2::CLITextFormat2(std::unique_ptr<Gdiplus::Font> &&font) :
        TextFormat2(),
        font_(std::move(font)),
        char_height_(0),
        font_size_(static_cast<int>(font_->GetSize())),
        descent_(computeDescent(*font_, Gdiplus::FontStyleRegular))
    {
        for (unsigned int c = COMMON_CHAR_START; c <= COMMON_CHAR_END; ++c) {
            wchar_t str[2];
            str[0] = (char)c;
            str[1] = '\0';
            common_char_metrics_[c - COMMON_CHAR_START] = MeasureDisplayStringBounds(str, 0, 1, *font_);
        }
    }

    float CLITextFormat2::getStringWidth(const char *text) NOTHROWS
    {
        float maxWidth = 0;
        float curWidth = 0;

        std::wstring_convert<std::codecvt_utf8_utf16<wchar_t>> convert;
        std::wstring wstr = convert.from_bytes(text);
        const wchar_t *wtext = wstr.c_str();

        while (*wtext) {
            if (*wtext == '\r') {
                wtext++;
                continue;
            }
            if (*wtext == '\n') {
                if (curWidth > maxWidth)
                    maxWidth = curWidth;
                curWidth = 0;
            } else {
                curWidth += getCharWidth((unsigned)wtext[0]);
            }
            wtext++;
        }
        if (curWidth > maxWidth)
            maxWidth = curWidth;
        return maxWidth;
    }

    float CLITextFormat2::getCharHeight() NOTHROWS
    {
        if (!char_height_) {
            Lock lock(mutex_);
            char_metrics_t retval = MeasureDisplayStringBounds(L" \tabcdefghijklmnopqrstuvwxyz0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ~`()-_=+[]{}:;\"\'<>,./?", 0, 1, *font_);
            char_height_ = retval.height;
        }
        return char_height_;
    }

    float CLITextFormat2::getCharWidth(const unsigned int c) NOTHROWS
    {
        char_metrics_t metrics;
        getCharMetrics(&metrics, c);
        return metrics.width;
    }

    void CLITextFormat2::getCharMetrics(char_metrics_t *metrics, const unsigned int c)
    {
        if (c >= COMMON_CHAR_START && c <= COMMON_CHAR_END) {
            *metrics = common_char_metrics_[c - COMMON_CHAR_START];
        }
        else {
            auto entry = char_metrics_.find(c);
            if (entry != char_metrics_.end()) {
                *metrics = entry->second;
            } else {
                Lock lock(mutex_);
                std::wstring wstr = getWideString(c);
                char_metrics_t retval = MeasureDisplayStringBounds(wstr.c_str(), 0, 1, *font_);
                char_metrics_[c] = retval;
                *metrics = retval;
            }
        }
    }

    float CLITextFormat2::getCharPositionWidth(const char *text, int position) NOTHROWS
    {
        return getCharWidth((unsigned)(text[position]&0xFF));
    }

    float CLITextFormat2::getDescent() NOTHROWS
    {
        return descent_;
    }

    float CLITextFormat2::getStringHeight(const char *text) NOTHROWS
    {
        std::size_t numLines = 0;
        for (const char *c = text; *c; c++)
            if (*c == '\n')
                numLines++;
        return getCharHeight() * (numLines + 1);
    }

    float CLITextFormat2::getBaselineSpacing() NOTHROWS
    {
        return getCharHeight();
    }

    int CLITextFormat2::getFontSize() NOTHROWS
    {
        return font_size_;
    }

    TAKErr CLITextFormat2::loadGlyph(BitmapPtr &value, const unsigned int c) NOTHROWS
    {
        std::unique_ptr<Gdiplus::Bitmap> sbmap;
        try {
            char_metrics_t metrics;
            getCharMetrics(&metrics, c);

            int charWidth = std::max(static_cast<int>(metrics.width), 1);
            int charHeight = std::max(static_cast<int>(getCharHeight()), 1);
            sbmap.reset(new Gdiplus::Bitmap(
                charWidth,
                charHeight,
                PixelFormat32bppARGB));

            std::unique_ptr<Gdiplus::Graphics> g(Gdiplus::Graphics::FromImage(sbmap.get()));
            g->SetTextRenderingHint(Gdiplus::TextRenderingHintAntiAlias);
            g->SetSmoothingMode(Gdiplus::SmoothingModeAntiAlias);
            g->Clear(Gdiplus::Color(0x00FFFFFF));
            Lock lock(mutex_);

            Gdiplus::StringFormat format;
            configureStringFormat(format);

            Gdiplus::PointF loc(-metrics.offx, -metrics.offy);

            float size = g->GetDpiY() * (float)GetSizeInPoints(*font_) / 72.0f;
            //size = font->FontFamily->GetEmHeight(font->Style);

            Gdiplus::GraphicsPath p;
            // since our string is only a single character and source is not multi-byte, shouldn't need to run mbtowcs
            std::wstring wstr = getWideString(c);
            Gdiplus::FontFamily family;
            font_->GetFamily(&family);
            p.AddString(
                wstr.c_str(),       // text to draw
                1,
                &family,            // or any other font family
                font_->GetStyle(),  // font style (bold, italic, etc.)
                size,               // em size
                loc,                // location where to draw text
                &format);           // set options here (e.g. center alignment)

            Gdiplus::Pen outlinePen(Gdiplus::Color::Black);
            outlinePen.SetWidth(3);
            outlinePen.SetLineJoin(Gdiplus::LineJoinRound);
            g->DrawPath(&outlinePen, &p);

            Gdiplus::SolidBrush fillBrush(Gdiplus::Color::White);
            g->FillPath(&fillBrush, &p);

            g->Flush();

            g.reset();
        } catch (...) {
            return TE_Err;
        }

        // ensure that the bitmap is valid
        if (sbmap->GetLastStatus() != Gdiplus::Status::Ok)
            return TE_Err;

        return BitmapAdapter_adapt(value, *sbmap);
    }

    std::wstring getWideString(unsigned int c) {
        std::wstring_convert<std::codecvt_utf8_utf16<wchar_t>> convert;
        char buf[5u];
        std::wstring wstring;
        if (c < 0xFF) {
            // 7 bits, emit
            wstring += (unsigned char)c;
            wstring += L'\0';
        } else if (c < 0x800) {
            // 11 bits
            buf[0] = 0xC0 | ((c >> 6) & 0x1F);
            buf[1] = 0x80 | (c & 0x3F);
            buf[2] = '\0';
            wstring = convert.from_bytes(buf);
        } else if (c < 0x10000) {
            // 16 bits
            buf[0] = 0xE0 | ((c >> 12) & 0x0F);
            buf[1] = 0x80 | ((c >> 6) & 0x3F);
            buf[2] = 0x80 | (c & 0x3F);
            buf[3] = '\0';
            wstring = convert.from_bytes(buf);
        } else {
            // truncated to 21 bits
            buf[0] = 0xF0 | ((c >> 18) & 0x07);
            buf[1] = 0x80 | ((c >> 12) & 0x3F);
            buf[2] = 0x80 | ((c >> 6) & 0x3F);
            buf[3] = 0x80 | (c & 0x3F);
            buf[4] = '\0';
            wstring = convert.from_bytes(buf);
        }

        return wstring;
    }

    float computeDescent(Gdiplus::Font &font, Gdiplus::FontStyle style)
    {
        Gdiplus::FontFamily fontFamily;
        font.GetFamily(&fontFamily);
        float value = fontFamily.GetCellDescent(style);
        return (float)std::ceil(font.GetSize() * value / fontFamily.GetEmHeight(style));
    }

    void configureStringFormat(Gdiplus::StringFormat &format)
    {
        format.SetFormatFlags(0);
        format.SetAlignment(Gdiplus::StringAlignmentNear);
        format.SetLineAlignment(Gdiplus::StringAlignmentNear);
        format.SetTrimming(Gdiplus::StringTrimmingNone);
        format.SetHotkeyPrefix(Gdiplus::HotkeyPrefixNone);

        format.SetFormatFlags(format.GetFormatFlags()|Gdiplus::StringFormatFlagsMeasureTrailingSpaces);
    }
}

#endif

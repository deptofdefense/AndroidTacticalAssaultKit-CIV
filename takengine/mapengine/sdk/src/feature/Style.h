#ifndef ATAKMAP_FEATURE_STYLE_H_INCLUDED
#define ATAKMAP_FEATURE_STYLE_H_INCLUDED

#include <memory>
#include <stdexcept>
#include <vector>

#include "port/Platform.h"
#include "port/String.h"
#include "util/Error.h"

namespace atakmap                       // Open atakmap namespace.
{
    namespace feature                       // Open feature namespace.
    {

        class ENGINE_API Style;

        enum StyleClass
        {
            TESC_BasicPointStyle,
            TESC_LabelPointStyle,
            TESC_IconPointStyle,
            TESC_BasicStrokeStyle,
            TESC_BasicFillStyle,
            TESC_CompositeStyle,
            TESC_PatternStrokeStyle,
        };
        typedef std::unique_ptr<Style, void(*)(const Style *)> StylePtr;
        typedef std::unique_ptr<const Style, void(*)(const Style *)> StylePtr_Const;

        class ENGINE_API Style
        {
        private :
            Style(const StyleClass clazz) NOTHROWS;
        public:
            virtual ~Style () throw () = 0;
        public :
            static void destructStyle(const Style *);

            /**
             * Parses and returns a (possibly NULL) Style from the supplied OGR
             * style string.
             *
             * Throws std::invalid_argument if the supplied OGR style string is
             * NULL or if parsing fails.
             */
            static Style* parseStyle (const char* styleOGR);

            StyleClass getClass() const NOTHROWS;

            /**
             * Returns an OGR feature style string.
             */
            virtual TAK::Engine::Util::TAKErr toOGR(TAK::Engine::Port::String &value) const NOTHROWS = 0;
            virtual Style *clone() const = 0;
        private :
            StyleClass styleClass;

            friend class IconPointStyle;
            friend class BasicPointStyle;
            friend class LabelPointStyle;
            friend class BasicFillStyle;
            friend class BasicStrokeStyle;
            friend class CompositeStyle;
            friend class PatternStrokeStyle;
        };

        class ENGINE_API BasicFillStyle : public Style
        {
        public:
            /**
             * @param color 0xAARRGGBB
             */
            BasicFillStyle (unsigned int color)   throw ();
            ~BasicFillStyle () throw ()
            { }
        public :
            unsigned int getColor () const throw ()
            { return color; }
        public : // Style INTERFACE
            TAK::Engine::Util::TAKErr toOGR(TAK::Engine::Port::String &value) const NOTHROWS override;
            virtual Style *clone() const override;
        private:
            unsigned int color;
        };

        class ENGINE_API BasicPointStyle : public Style
        {
        public:
            /**
             * Throws std::invalid_argument if the supplied size is negative.
             * @param color 0xAARRGGBB
             * @param size  Diameter in pixels.
             */
            BasicPointStyle (unsigned int color, float size);

            ~BasicPointStyle () throw ()
              { }

        public :
            unsigned int getColor () const throw ()
              { return color; }

            /**
             * Returns diameter in pixels.
             */
            float getSize () const throw ()
              { return size; }
        public ://  Style INTERFACE
            Style * clone() const override;
            TAK::Engine::Util::TAKErr toOGR(TAK::Engine::Port::String &value) const NOTHROWS override;
        private:
            unsigned int color;
            float size;
        };

        class ENGINE_API BasicStrokeStyle : public Style
        {
        public:
            /**
             * Throws std::invalid_argument if the supplied width is negative.
             * @param color 0xAARRGGBB
             * @param width Stroke width in pixels.
             */
            BasicStrokeStyle (unsigned int color, float width);
            ~BasicStrokeStyle () throw ()
            { }
        public :
            unsigned int getColor () const throw ()
            { return color; }

            float getStrokeWidth () const throw ()
            { return width; }
        public : //  Style INTERFACE
            TAK::Engine::Util::TAKErr toOGR(TAK::Engine::Port::String &value) const NOTHROWS override;
            virtual Style *clone() const override;
        private:
            unsigned int color;
            float width;
        };


        /**
         * A concrete Style that is the composition of one or more Style
         * instances.
         * Composition allows for complex styling to achieved by sequentially
         * rendering the geometry using multiple Styles.  Styles are always
         * applied in FIFO order.
         *
         * For example, the following:
         *     std::vector<Style*> styles (2);
         *     styles.push_back (new BasicStrokeStyle (0xFFFFFFFF, 3));
         *     styles.push_back (new BasicStrokeStyle (0xFF000000, 1));
         *     CompositeStyle outlined (styles);
         * Would create an outline stroking effect.  The geometry would first
         * get stroked using a white line of 3 pixels, and then an additional
         * black stroke of 1 pixel would be applied.
         *
         * It should be assumed that all component styles are applicable for the geometry to which the composite is applied.
         */
        class ENGINE_API CompositeStyle : public Style
        {
        public:
            typedef std::shared_ptr<Style> StylePtr;
            typedef std::vector<StylePtr> StyleVector;
        public :
            /**
             * Throws std::invalid_argument if the supplied vector of Styles is empty or if one of the Styles in the vector is NULL.
             * @param styles    Ownership of Style instances is transferred
             */
            CompositeStyle (const std::vector<Style*>& styles);
            ~CompositeStyle () throw ()
            { }
        public :
            const StyleVector& components () const throw ()
            { return styles_; }

            template <class StyleType>
            StylePtr findStyle () const throw ();

            TAK::Engine::Util::TAKErr findStyle(const Style **s, const StyleClass styleClass) NOTHROWS;

            const Style& getStyle (std::size_t index) const throw (std::range_error);

            std::size_t getStyleCount () const throw ()
            { return styles_.size (); }

        public : //  Style INTERFACE
            TAK::Engine::Util::TAKErr toOGR(TAK::Engine::Port::String &value) const NOTHROWS override;
            virtual Style *clone() const override;
        private:
            StyleVector styles_;
        };

        /**
         * Icon style for Point geometry. Renders the Point using the specified
         * icon.
         *
         * Icon rendering size is determined by scaling OR width & height. If
         * scaling is 1.0, icon is rendered at original size.  If scaling is
         * 0.0, width and height are absolute size in pixels.  If width or
         * height is 0.0, the icon's original width or height is used.
         */
        class ENGINE_API IconPointStyle : public Style
        {
        public :
            enum HorizontalAlignment
            {
                /** Aligned to the left of the Point. */
                LEFT    = -1,
                /** Horizontally centered on the Point. */
                H_CENTER,
                /** Aligned to the right of the Point. */
                RIGHT
            };

            enum VerticalAlignment
            {
                /** Aligned above the Point. */
                ABOVE   = -1,
                /** Vertically centered on the Point. */
                V_CENTER,
                /** Aligned below the Point. */
                BELOW
            };
        public:
            /**
             * Creates a new icon style with the supplied properties.
             *
             * The supplied scaling value specifies scale at which the icon
             * should be rendered.  The icon's original size is used if the
             * supplied value of scaling is the default value of 1.0.
             *
             * The supplied rotation should be specified in degrees.  If the
             * supplied value of absoluteRotation is true, the rotation is
             * relative to north (and the icon will rotate as the displayed
             * direction of north changes).  If the supplied value of
             * absoluteRotation is false, the rotation is relative to the
             * screen (and the icon will not rotate as the displayed direction
             * of north changes).
             *
             * The default arguments create a new icon style, centered on the
             * Point at original size, displayed right side up (relative to the
             * screen).
             *
             * Throws std::invalid_argument if the supplied icon URI is NULL or
             * if the supplied scaling is negative.
             *
             * @param color             0xAARRGGBB
             * @param scaling           defaults to 1.0, use original icon size
             * @param hAlign            defaults to H_CENTER
             * @param vAlign            defaults to V_CENTER
             * @param rotation          default to 0.0
             * @param absoluteRotation  defaults to false
             */
            IconPointStyle (unsigned int color,
                            const char* iconURI,
                            float scaling = 1.0,
                            HorizontalAlignment hAlign = H_CENTER,
                            VerticalAlignment vAlign = V_CENTER,
                            float rotation = 0.0,
                            bool absoluteRotation = false);

            /**
             * Creates a new icon style with the supplied properties.
             *
             * The supplied width and height values specify the rendered width
             * and height (in pixels) of the icon.  The icon's original width
             * or height is used if the width or height is specified as 0.0,
             * respectively.
             *
             * The supplied rotation should be specified in degrees.  If the
             * supplied value of absoluteRotation is true, the rotation is
             * relative to north (and the icon will rotate as the displayed
             * direction of north changes).  If the supplied value of
             * absoluteRotation is false, the rotation is relative to the
             * screen (and the icon will not rotate as the displayed direction
             * of north changes).
             *
             * The default arguments create a new icon style, centered on the
             * Point at the specified size, displayed right side up (relative
             * to the screen).
             *
             * Throws std::invalid_argument if the supplied icon URI is NULL or
             * if the supplied width or height is negative.
             *
             * @param color             0xAARRGGBB
             * @param width             Rendered width in pixels.
             * @param height            Rendered height in pixels.
             * @param hAlign            defaults to H_CENTER,
             * @param vAlign            defaults to V_CENTER,
             * @param rotation          defaults to 0.0
             * @param absoluteRotation  defaults to false
             */
            IconPointStyle (unsigned int color,
                            const char* iconURI,
                            float width,
                            float height,
                            HorizontalAlignment hAlign = H_CENTER,
                            VerticalAlignment vAlign = V_CENTER,
                            float rotation = 0.0,
                            bool absoluteRotation = false);

            ~IconPointStyle () throw ()
            { }
        public :
            /** 0xAARRGGBB */
            unsigned int getColor () const throw ()
            { return color; }

            /** Height in pixels if scaling is 0. */
            float getHeight () const throw ()
            { return height; }

            HorizontalAlignment getHorizontalAlignment () const throw ()
              { return hAlign; }

            const char* getIconURI () const throw ()
              { return iconURI; }

            /** Returns rotation in degrees. */
            float getRotation () const throw ()
            { return rotation; }

            VerticalAlignment getVerticalAlignment () const throw ()
              { return vAlign; }

            /** Returns 0.0 if width & height are set. */
            float getScaling () const throw ()
              { return scaling; }

            /** Width in pixels if scaling is 0. */
            float getWidth () const throw ()
              { return width; }

            /** Absolute == relative to north. */
            bool isRotationAbsolute ()  const throw ()
              { return absoluteRotation; }
        public ://  Style INTERFACE
            TAK::Engine::Util::TAKErr toOGR(TAK::Engine::Port::String &value) const NOTHROWS override;
            virtual Style *clone() const override;
        private:
            unsigned int color;
            TAK::Engine::Port::String iconURI;
            HorizontalAlignment hAlign;
            VerticalAlignment vAlign;
            float scaling;
            float width;
            float height;
            float rotation;
            bool absoluteRotation;
        };

        class ENGINE_API LabelPointStyle : public Style
        {
        public :
            enum HorizontalAlignment
            {
                /** Aligned to the left of the Point. */
                LEFT    = -1,
                /** Horizontally centered on the Point. */
                H_CENTER,
                /** Aligned to the right of the Point. */
                RIGHT
            };

            enum VerticalAlignment
            {
                /** Aligned above the Point. */
                ABOVE   = -1,
                /** Vertically centered on the Point. */
                V_CENTER,
                /** Aligned below the Point. */
                BELOW
            };
        public:
            enum ScrollMode
            {
                /** Use system-wide setting. */
                DEFAULT,
                /**  Label scrolls if longer than a width specified by the system. */
                ON,
                /**  Label is always fully displayed. */
                OFF
            };
        public :

        /**
         * Creates a new label style with the supplied properties.
         *
         * The supplied textSize is in points.  The system's default font size is used when textSize is specified as 0.
         *
         * The supplied rotation should be specified in degrees.  If the supplied value of absoluteRotation is true, the rotation is relative to north (and the icon will rotate as the displayed direction of north changes).  If the supplied value of absoluteRotation is false, the rotation is relative to the screen (and the icon will not rotate as the displayed direction of north changes).
         *
         * The default arguments create a new label style, centered on the Point at default system font size, displayed right side up (relative to the screen).
         *
         * Throws std::invalid_argument if the supplied label text is NULL or if the supplied textSize is negative.
        */
        LabelPointStyle (const char* text,
                         unsigned int textColor,    // 0xAARRGGBB
                         unsigned int backColor,    // 0xAARRGGBB
                         ScrollMode mode,
                         float textSize = 0.0,      // Use system default size.
                         HorizontalAlignment hAlign = H_CENTER,
                         VerticalAlignment vAlign = V_CENTER,
                         float rotation = 0.0,      // 0 degrees of rotation.
                         bool absoluteRotation = false, // Relative to screen.
                         float paddingX = 0.0, // offset from alignment position
                         float paddingY = 0.0,
                         double labelMinRenderResolution = 13.0);

        ~LabelPointStyle () throw ()
          { }
        public :
            /** Returns 0xAARRGGB. */
            unsigned int getBackgroundColor () const throw ()
              { return backColor; }

            HorizontalAlignment getHorizontalAlignment () const throw ()
              { return hAlign; }

            /** Returns rotation in degrees. */
            float getRotation () const throw ()
              { return rotation; }

            ScrollMode getScrollMode () const throw ()
              { return scrollMode; }

            const char* getText () const throw ()
              { return text; }

            /** Returns 0xAARRGGBB. */
            unsigned int getTextColor ()  const throw ()
              { return foreColor; }

            /** Returns size in points (or 0). */
            float getTextSize () const throw ()
              { return textSize; }

            VerticalAlignment getVerticalAlignment () const throw ()
              { return vAlign; }

            float getPaddingX() const throw ()
              { return paddingX; }

            float getPaddingY() const throw ()
              { return paddingY; }

            /**  Absolute == relative to north. */
            bool isRotationAbsolute () const throw ()
              { return absoluteRotation; }

            double getLabelMinRenderResolution() const throw () 
              { return labelMinRenderResolution; }

        public : //  Style INTERFACE
            TAK::Engine::Util::TAKErr toOGR(TAK::Engine::Port::String &value) const NOTHROWS override;
            virtual Style *clone() const override;
        private:
            TAK::Engine::Port::String text;
            unsigned int foreColor;
            unsigned int backColor;
            ScrollMode scrollMode;
            HorizontalAlignment hAlign;
            VerticalAlignment vAlign;
            float textSize;
            float rotation;
            float paddingX;
            float paddingY;
            bool absoluteRotation;
            double labelMinRenderResolution; 
        };

        class ENGINE_API PatternStrokeStyle : public Style
        {
        public :
        /**
         * Creates a new patterned storke style with the supplied properties.
         *
         * Throws std::invalid_argument if 'patternLen' is less than 2 or not a power-of-two.
        */
        PatternStrokeStyle (const uint64_t pattern,
                            const std::size_t patternLen,
                            const unsigned int color,
                            const float strokeWidth);

        ~PatternStrokeStyle () throw ()
          { }
        public :
            uint64_t getPattern() const throw ();
            std::size_t getPatternLength() const throw ();
            /** Returns 0xAARRGGB. */
            unsigned int getColor () const throw ();
            float getStrokeWidth() const throw ();
        public : //  Style INTERFACE
            TAK::Engine::Util::TAKErr toOGR(TAK::Engine::Port::String &value) const NOTHROWS override;
            virtual Style *clone() const override;
        private:
            uint64_t pattern;
            std::size_t patternLen;
            unsigned int color;
            float width;
        };

        ENGINE_API TAK::Engine::Util::TAKErr BasicFillStyle_create(StylePtr &value, const unsigned int color) NOTHROWS;
        ENGINE_API TAK::Engine::Util::TAKErr BasicPointStyle_create(StylePtr &value, const unsigned int color, const float size) NOTHROWS;
        ENGINE_API TAK::Engine::Util::TAKErr BasicStrokeStyle_create(StylePtr &value, const unsigned int color, const float width) NOTHROWS;
        ENGINE_API TAK::Engine::Util::TAKErr CompositeStyle_create(StylePtr &value, const Style **styles, const std::size_t count) NOTHROWS;
        ENGINE_API TAK::Engine::Util::TAKErr IconPointStyle_create(StylePtr &value, unsigned int color,
                                                                                    const char* iconURI,
                                                                                    float scaleFactor = 1.0,
                                                                                    IconPointStyle::HorizontalAlignment hAlign = IconPointStyle::H_CENTER,
                                                                                    IconPointStyle::VerticalAlignment vAlign = IconPointStyle::V_CENTER,
                                                                                    float rotation = 0.0,
                                                                                    bool absoluteRotation = false) NOTHROWS;
        ENGINE_API TAK::Engine::Util::TAKErr IconPointStyle_create(StylePtr &value, unsigned int color,
                                                                                    const char* iconURI,
                                                                                    float width,
                                                                                    float height,
                                                                                    IconPointStyle::HorizontalAlignment hAlign = IconPointStyle::H_CENTER,
                                                                                    IconPointStyle::VerticalAlignment vAlign = IconPointStyle::V_CENTER,
                                                                                    float rotation = 0.0,
                                                                                    bool absoluteRotation = false) NOTHROWS;
        ENGINE_API TAK::Engine::Util::TAKErr LabelPointStyle_create(StylePtr &value, const char* text,
                                                                                     unsigned int textColor,    // 0xAARRGGBB
                                                                                     unsigned int backColor,    // 0xAARRGGBB
                                                                                     LabelPointStyle::ScrollMode mode,
                                                                                     float textSize = 0.0,      // Use system default size.
                                                                                     LabelPointStyle::HorizontalAlignment hAlign = LabelPointStyle::H_CENTER,
                                                                                     LabelPointStyle::VerticalAlignment vAlign = LabelPointStyle::V_CENTER,
                                                                                     float rotation = 0.0,      // 0 degrees of rotation.
                                                                                     bool absoluteRotation = false, // Relative to screen.
                                                                                     float paddingX = 0.0, // offset from alignment position
                                                                                     float paddingY = 0.0,
                                                                                     double labelMinRenderResolution = 13.0) NOTHROWS;
        ENGINE_API TAK::Engine::Util::TAKErr PatternStrokeStyle_create(StylePtr &value, const uint64_t pattern,
                                                                                        const std::size_t patternLen,
                                                                                        const unsigned int color,
                                                                                        const float strokeWidth) NOTHROWS;
    }
}

namespace atakmap {
    namespace feature {

        template <class StyleType>
        inline CompositeStyle::StylePtr CompositeStyle::findStyle () const throw ()
        {
            StylePtr result;
            const StyleVector::const_iterator end (styles_.end ());

            for (StyleVector::const_iterator iter (styles_.begin ());
                 !result && iter != end;
                 ++iter) {

                const CompositeStyle *composite(nullptr);

                if (dynamic_cast<const StyleType*> (iter->get ())) {
                    result = *iter;
                } else if ((composite = dynamic_cast<const CompositeStyle*> (iter->get ())), composite != nullptr) {
                    result = composite->findStyle<StyleType> ();
                }
            }

            return result;
        }
    }
}

#endif  // #ifndef ATAKMAP_FEATURE_STYLE_H_INCLUDED

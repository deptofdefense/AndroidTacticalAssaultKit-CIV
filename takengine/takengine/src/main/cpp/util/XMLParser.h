
#ifndef ATAKMAP_UTIL_XML_PARSER_H_INCLUDED
#define ATAKMAP_UTIL_XML_PARSER_H_INCLUDED



namespace atakmap {
    namespace util {
        
        class DataInput;
        
        class XMLAttributes {
        public:
            XMLAttributes(void *impl)
            : _impl(impl) { }
            int getCount() const;
            const char *getNameAt(int index) const;
            const char *getValueAt(int index) const;
            const char *getValueOf(const char *name) const;
        private:
            void *_impl;
        };
        
        /**
         * Interface for implementing different XML outputs. Provides SAX-like callbacks for XML parsing.
         */
        class XMLParserOutput {
        public:
            virtual ~XMLParserOutput();
            
            /**
             * Called when an opening element tag is parsed.
             *
             * @param baseURI the element uri (if exists)
             * @param localName the local element name
             * @param qualifiedName the fully qualified element name
             * @param attrs accessor to the attributes of the element
             */
            virtual void xmlBeginElement(const char *baseURI, const char *localName, const char *qualifiedName,
                                         const XMLAttributes &attrs) = 0;
            
            /**
             * Called when a closing element tag is parsed.
             *
             * @param baseURI the element uri (if exists)
             * @param localName the local element name
             * @param qualifiedName the fully qualified element name
             */
            virtual void xmlEndElement(const char *baseURI, const char *localName, const char *qualifiedName) = 0;
            
            /**
             * Called when a text value is parsed
             *
             * @param value the text value
             */
            virtual void xmlTextValue(const char *value) = 0;
            
            /**
             * Called when during XML parsing error or warning
             *
             * @param message the error message
             * @param fatal if the error prevents any further parsing
             * @param line the line number
             * @param col the column number
             */
            virtual void xmlError(const char *message, bool fatal, int line, int col) = 0;
            
            /**
             * Called when the document is successfully, completely parsed.
             */
            virtual void xmlEndDocument() = 0;
        };
        
        /**
         * Options for XMLParser (for future needs)
         */
        struct XMLParserOptions {
            // Add as needed
        };
        
        /**
         * An XML parser
         */
        class XMLParser {
        public:
            /**
             * Initialize default parser
             */
            XMLParser();
            
            /**
             * Initialize parser with options
             */
            explicit XMLParser(const XMLParserOptions &opts);
            
            /**
             * Parse an input stream.
             *
             * @param input the input XML stream
             * @param output the resulting output
             *
             * @return true if parsing succeeded without error
             */
            bool parse(atakmap::util::DataInput &input, XMLParserOutput &output);
        };

        
    }
}

#endif

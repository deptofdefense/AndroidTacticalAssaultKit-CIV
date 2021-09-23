
// libxml implementation of XMLParser

#include <string.h>

#include <libxml/xmlreader.h>

#include "util/IO.h"
#include "util/XMLParser.h"

using namespace atakmap::util;

XMLParserOutput::~XMLParserOutput() { }

namespace {
    int xmlDataInputRead(void * context, char * buffer, int len) {
        atakmap::util::DataInput *input = static_cast<atakmap::util::DataInput *>(context);
        size_t value = input->read(reinterpret_cast<uint8_t *>(buffer), len);
        if (value == atakmap::util::DataInput::EndOfStream) {
            return 0;
        }
        return static_cast<int>(value);
    }
    
    int xmlDataInputClose (void * context) {
        return 0;
    }
    
    void xmlErrorHandler(void *arg,
                         const char *msg,
                         xmlParserSeverities severity,
                         xmlTextReaderLocatorPtr locator) {
        
        XMLParserOutput *output = static_cast<XMLParserOutput *>(arg);
        output->xmlError(msg,
                         severity == XML_PARSER_SEVERITY_ERROR || severity == XML_PARSER_SEVERITY_VALIDITY_ERROR,
                         -1, -1);
    }
    
    bool parseDocument(xmlTextReaderPtr xmlReader, XMLParserOutput &output) {
        
        int readResult;
        XMLAttributes attrs(xmlReader);
        
        while ((readResult = xmlTextReaderRead(xmlReader)) == 1) {
            int nodeType = xmlTextReaderNodeType(xmlReader);
            const xmlChar *name = xmlTextReaderConstName(xmlReader);
            const xmlChar *localName = xmlTextReaderConstLocalName(xmlReader);
            const xmlChar *baseURI = xmlTextReaderConstBaseUri(xmlReader);
            const xmlChar *value;
            
            switch (nodeType) {
                case XML_READER_TYPE_ELEMENT:
                    output.xmlBeginElement(reinterpret_cast<const char *>(baseURI),
                                           reinterpret_cast<const char *>(localName),
                                           reinterpret_cast<const char *>(name),
                                           attrs);
                    
                    // move back from element (if needed)
                    xmlTextReaderMoveToElement(xmlReader);
                    
                    // self closing element invoke end right away
                    if (xmlTextReaderIsEmptyElement(xmlReader)) {
                        output.xmlEndElement(reinterpret_cast<const char *>(baseURI),
                                             reinterpret_cast<const char *>(localName),
                                             reinterpret_cast<const char *>(name));
                    }
                    
                    break;
                    
                case XML_READER_TYPE_END_ELEMENT:
                    output.xmlEndElement(reinterpret_cast<const char *>(baseURI),
                                         reinterpret_cast<const char *>(localName),
                                         reinterpret_cast<const char *>(name));
                    break;
                    
                case XML_READER_TYPE_TEXT:
                    value = xmlTextReaderConstValue(xmlReader);
                    output.xmlTextValue(reinterpret_cast<const char *>(value));
                    break;
            }
        }
        
        bool result = false;
        if (readResult == 0) {
            output.xmlEndDocument();
            result = true;
        }
        
        return result;
    }
}

int XMLAttributes::getCount() const {
    xmlTextReaderPtr xmlReader = static_cast<xmlTextReaderPtr>(_impl);
    xmlTextReaderMoveToElement(xmlReader);
    int count = xmlTextReaderAttributeCount(xmlReader);
    return count;
}

const char *XMLAttributes::getNameAt(int index) const {
    xmlTextReaderPtr xmlReader = static_cast<xmlTextReaderPtr>(_impl);
    xmlTextReaderMoveToAttributeNo(xmlReader, index);
    const xmlChar *value = xmlTextReaderConstName(xmlReader);
    return reinterpret_cast<const char *>(value);
}

const char *XMLAttributes::getValueAt(int index) const {
    xmlTextReaderPtr xmlReader = static_cast<xmlTextReaderPtr>(_impl);
    xmlTextReaderMoveToAttributeNo(xmlReader, index);
    const xmlChar *value = xmlTextReaderConstValue(xmlReader);
    return reinterpret_cast<const char *>(value);
}

const char *XMLAttributes::getValueOf(const char *name) const {
    xmlTextReaderPtr xmlReader = static_cast<xmlTextReaderPtr>(_impl);
    if (xmlTextReaderMoveToAttribute(xmlReader, BAD_CAST name) != 1)
        return nullptr;
    const xmlChar *value = xmlTextReaderConstValue(xmlReader);
    return reinterpret_cast<const char *>(value);
}

XMLParser::XMLParser() { }

XMLParser::XMLParser(const XMLParserOptions &opts) { }

bool XMLParser::parse(atakmap::util::DataInput &input, XMLParserOutput &output) {
    xmlTextReaderPtr xmlReader = xmlReaderForIO(xmlDataInputRead, xmlDataInputClose, &input, "", NULL, 0);
    bool result = false;
    if (xmlReader) {
        xmlTextReaderSetErrorHandler(xmlReader, xmlErrorHandler, &output);
        result = parseDocument(xmlReader, output);
        xmlFreeTextReader(xmlReader);
    } else {
        output.xmlError("failed to create reader", true, -1, -1);
    }
    return result;
}

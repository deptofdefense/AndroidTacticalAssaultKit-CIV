package android.util;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;

final class AndroidXmlPullParser implements XmlPullParser, AutoCloseable {
    final XmlPullParser impl;

    AndroidXmlPullParser(XmlPullParser impl) {
        this.impl = impl;
    }

    @Override
    public void setFeature(String s, boolean b) throws XmlPullParserException {
        if(s.equals(Xml.FEATURE_RELAXED))
            return;
        impl.setFeature(s, b);
    }

    @Override
    public boolean getFeature(String s) {
        return impl.getFeature(s);
    }

    @Override
    public void setProperty(String s, Object o) throws XmlPullParserException {
        impl.setProperty(s, o);
    }

    @Override
    public Object getProperty(String s) {
        return impl.getProperty(s);
    }

    @Override
    public void setInput(Reader reader) throws XmlPullParserException {
        impl.setInput(reader);
    }

    @Override
    public void setInput(InputStream inputStream, String s) throws XmlPullParserException {
        impl.setInput(inputStream, s);
    }

    @Override
    public String getInputEncoding() {
        return impl.getInputEncoding();
    }

    @Override
    public void defineEntityReplacementText(String s, String s1) throws XmlPullParserException {
        impl.defineEntityReplacementText(s, s1);
    }

    @Override
    public int getNamespaceCount(int i) throws XmlPullParserException {
        return impl.getNamespaceCount(i);
    }

    @Override
    public String getNamespacePrefix(int i) throws XmlPullParserException {
        return impl.getNamespacePrefix(i);
    }

    @Override
    public String getNamespaceUri(int i) throws XmlPullParserException {
        return impl.getNamespaceUri(i);
    }

    @Override
    public String getNamespace(String s) {
        return impl.getNamespace();
    }

    @Override
    public int getDepth() {
        return impl.getDepth();
    }

    @Override
    public String getPositionDescription() {
        return impl.getPositionDescription();
    }

    @Override
    public int getLineNumber() {
        return impl.getLineNumber();
    }

    @Override
    public int getColumnNumber() {
        return impl.getColumnNumber();
    }

    @Override
    public boolean isWhitespace() throws XmlPullParserException {
        return impl.isWhitespace();
    }

    @Override
    public String getText() {
        return impl.getText();
    }

    @Override
    public char[] getTextCharacters(int[] ints) {
        return impl.getTextCharacters(ints);
    }

    @Override
    public String getNamespace() {
        return impl.getNamespace();
    }

    @Override
    public String getName() {
        return impl.getName();
    }

    @Override
    public String getPrefix() {
        return impl.getPrefix();
    }

    @Override
    public boolean isEmptyElementTag() throws XmlPullParserException {
        return impl.isEmptyElementTag();
    }

    @Override
    public int getAttributeCount() {
        return impl.getAttributeCount();
    }

    @Override
    public String getAttributeNamespace(int i) {
        return impl.getAttributeNamespace(i);
    }

    @Override
    public String getAttributeName(int i) {
        return impl.getAttributeName(i);
    }

    @Override
    public String getAttributePrefix(int i) {
        return impl.getAttributePrefix(i);
    }

    @Override
    public String getAttributeType(int i) {
        return impl.getAttributeType(i);
    }

    @Override
    public boolean isAttributeDefault(int i) {
        return impl.isAttributeDefault(i);
    }

    @Override
    public String getAttributeValue(int i) {
        return impl.getAttributeValue(i);
    }

    @Override
    public String getAttributeValue(String s, String s1) {
        return impl.getAttributeValue(s, s1);
    }

    @Override
    public int getEventType() throws XmlPullParserException {
        return impl.getEventType();
    }

    @Override
    public int next() throws XmlPullParserException, IOException {
        return impl.next();
    }

    @Override
    public int nextToken() throws XmlPullParserException, IOException {
        return impl.nextToken();
    }

    @Override
    public void require(int i, String s, String s1) throws XmlPullParserException, IOException {
        impl.require(i, s, s1);
    }

    @Override
    public String nextText() throws XmlPullParserException, IOException {
        return impl.nextText();
    }

    @Override
    public int nextTag() throws XmlPullParserException, IOException {
        return impl.nextTag();
    }

    @Override
    public void close() throws Exception {
        if(impl instanceof AutoCloseable)
            ((AutoCloseable) impl).close();
    }
}

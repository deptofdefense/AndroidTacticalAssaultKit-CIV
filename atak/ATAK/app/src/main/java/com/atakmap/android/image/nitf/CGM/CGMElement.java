
package com.atakmap.android.image.nitf.CGM;

/**
 *
 */
public enum CGMElement {
    DELIMITER_ELEMENTS(0),
    METAFILE_DESCRIPTOR_ELEMENTS(1),
    PICTURE_DESCRIPTOR_ELEMENTS(2),
    CONTROL_ELEMENTS(3),
    GRAPHICAL_PRIMITIVE_ELEMENTS(4),
    ATTRIBUTE_ELEMENTS(5),
    ESCAPE_ELEMENTS(6),
    EXTERNAL_ELEMENTS(7),
    SEGMENT_ELEMENTS(8),
    APPLICATION_STRUCTURE_ELEMENTS(9);

    private final int elementClass;

    CGMElement(int c) {
        elementClass = c;
    }

    /**
     * Returns the element class for the given class number
     * @param ec The class number to get
     * @return The corresponding element class
     */
    public static CGMElement getCGMElement(int ec) {
        if (ec < 0 || ec >= values().length)
            throw new ArrayIndexOutOfBoundsException(ec);

        return values()[ec];
    }

    /**
     * Returns the element for the given element class and element code
     *
     * @param elementClass
     *            The class number to get
     * @param elementCode
     *            The class code to get (depends on the class number)
     * @return The element as an object, will be one of the element code
     *         enumerations.
     */
    public static Object getElement(int elementClass, int elementCode) {
        CGMElement clazz = getCGMElement(elementClass);
        return clazz.getElementCode(elementCode);
    }

    /**
     * Returns the element code
     * @param elementCode
     * @return
     */
    private Object getElementCode(int elementCode) {
        switch (this) {
            case DELIMITER_ELEMENTS: // 0
                return DelimiterCGMElement.getElement(elementCode);
            case METAFILE_DESCRIPTOR_ELEMENTS: // 1
                return MetafileDescriptorCGMElement.getElement(elementCode);
            case PICTURE_DESCRIPTOR_ELEMENTS: // 2
                return PictureDescriptorCGMElement.getElement(elementCode);
            case CONTROL_ELEMENTS: // 3
                return ControlCGMElement.getElement(elementCode);
            case GRAPHICAL_PRIMITIVE_ELEMENTS: // 4
                return GraphicalPrimitiveCGMElement.getElement(elementCode);
            case ATTRIBUTE_ELEMENTS: // 5
                return AttributeCGMElement.getElement(elementCode);
            case EXTERNAL_ELEMENTS: // 7
                return ExternalCGMElement.getElement(elementCode);
        }
        return "null";
    }

    public String toString() {
        return name().concat("(").concat(String.valueOf(elementClass))
                .concat(")");
    }
}

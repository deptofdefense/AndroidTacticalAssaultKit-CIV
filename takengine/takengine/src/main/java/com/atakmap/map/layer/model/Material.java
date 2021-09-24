package com.atakmap.map.layer.model;

public final class Material {

    /**
     * Property type of the material (i.e. Diffuse map, Reflection map, etc.
     */
    public enum PropertyType {
        Diffuse,
    }

    public final static int NO_TEXCOORD_INDEX = -1;
    public final static int TWO_SIDED_BIT_FLAG = 1;

    private final String textureUri;
    private final PropertyType type;
    private final int color;
    private final int texCoordIndex;
    private final int bitFlags;

    public Material(String textureUri, PropertyType type, int color) {
        this.textureUri = textureUri;
        this.type = type;
        this.color = color;
        this.texCoordIndex = NO_TEXCOORD_INDEX;
        this.bitFlags = 0;
    }

    public Material(String textureUri, PropertyType type, int color, int texCoordIndex) {
        this.textureUri = textureUri;
        this.type = type;
        this.color = color;
        this.texCoordIndex = texCoordIndex;
        this.bitFlags = 0;
    }

    public Material(String textureUri, PropertyType type, int color, int texCoordIndex, int bitFlags) {
        this.textureUri = textureUri;
        this.type = type;
        this.color = color;
        this.texCoordIndex = texCoordIndex;
        this.bitFlags = bitFlags;
    }

    /**
     * The URI of the texture file (if any)
     *
     * @return
     */
    public String getTextureUri() {
        return this.textureUri;
    }

    /**
     * The property-type of the material
     *
     * @return
     */
    public PropertyType getPropertyType() {
        return this.type;
    }

    /**
     * The color of the material
     *
     * @return
     */
    public int getColor() {
        return this.color;
    }

    public int getTexCoordIndex() { return this.texCoordIndex; }

    public boolean getTwoSided() { return (this.bitFlags & TWO_SIDED_BIT_FLAG) != 0; }

    public static Material whiteDiffuse() {
        return new Material(null, PropertyType.Diffuse, -1);
    }
}

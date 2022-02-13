package gov.tak.api.util;

import com.atakmap.coremap.log.Log;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.ObjectCodec;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import gov.tak.api.annotation.NonNull;
import org.hibernate.CallbackException;
import org.hibernate.Session;
import org.hibernate.annotations.Type;
import org.hibernate.classic.Lifecycle;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Lob;
import java.io.IOException;
import java.io.Serializable;
import java.util.Iterator;
import java.util.Map;

/**
 * Typed collection of key/value tuples.  Nulls are not allowed as keys or as values.
 * <p>
 * Implementation uses a simple {@link java.util.Map} for storage, and JSON (Jackson data binding) for persistence.
 *
 * @since 0.17.0
 */
@Entity
public class AttributeSet extends AttributeSetBase implements Lifecycle {
    private static final ObjectMapper mapper = new ObjectMapper();

    static {
        SimpleModule module = new SimpleModule();
        // AttributeSet
        module.addSerializer(AttributeSet.class, new AttributeSetSerializer());
        module.addDeserializer(AttributeSet.class, new AttributeSetDeserializer());
        // AttributeValue
        module.addSerializer(AttributeValue.class, new AttributeValueSerializer());
        module.addDeserializer(AttributeValue.class, new AttributeValueDeserializer());
        mapper.registerModule(module);
    }

    @SuppressWarnings("unused")
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Lob
    @Type(type = "org.hibernate.type.TextType")
    private volatile String attributesAsJson = "";

    /**
     * Construct an empty attribute set.
     */
    public AttributeSet() {
    }

    /**
     * Copy constructor.
     */
    @SuppressWarnings("CopyConstructorMissesField") // Not copying id
    public AttributeSet(@NonNull AttributeSet source) {
        super(new AttributeSet(source.extern())); // Force update of source JSON
    }

    /**
     * Construct an attribute set from the given JSON representation.
     *
     * @param attributesAsJson JSON encoded form of AttributeSet
     */
    public AttributeSet(String attributesAsJson) {
        this.attributesAsJson = attributesAsJson;
        intern();
    }

    /**
     * @return The JSON encoded form of our attributes
     */
    public String toJson() {
        return extern();
    }

    @Override
    public boolean onSave(Session s) throws CallbackException {
        extern();
        return Lifecycle.NO_VETO;
    }

    @Override
    public boolean onUpdate(Session s) throws CallbackException {
        return onSave(s);
    }

    @Override
    public boolean onDelete(Session s) throws CallbackException {
        return Lifecycle.NO_VETO;
    }

    @Override
    public void onLoad(Session s, Serializable id) {
        intern();
    }

    /**
     * Internalize the {@code attributesAsJson}, creating the {@code attributeMap}.
     */
    private void intern() {
        try {
            attributeMap.putAll(mapper.readValue(attributesAsJson, new AttributeMapTypeReference()));
        } catch (JsonProcessingException e) {
            Log.e("AttributeSet", "Failed to reify attributes from '" + attributesAsJson + "'", e);
        }
    }

    /**
     * Externalize the {@code attributeMap}, creating the {@code attributesAsJson}.
     */
    private String extern() {
        try {
            attributesAsJson = mapper.writeValueAsString(attributeMap);
        } catch (JsonProcessingException e) {
            Log.e("AttributeSet", "Failed to create JSON form of '" + attributeMap + "'", e);
        }

        return attributesAsJson;
    }

    /**
     * Custom deserializer to properly handle the type mapping of each attribute value.  This is only needed because
     * we support arrays of primitive types, and by default Jackson will reify them as arrays of Object, and not
     * arrays of the primitive type.
     */
    private static final class AttributeSetSerializer extends StdSerializer<AttributeSet> {
        @SuppressWarnings("unused") // Required by Jackson
        protected AttributeSetSerializer() {
            this(null);
        }

        protected AttributeSetSerializer(Class<AttributeSet> vc) {
            super(vc);
        }

        @Override
        public void serialize(AttributeSet value, JsonGenerator gen, SerializerProvider provider) throws IOException {
            gen.writeObject(value.attributeMap);
        }
    }

    /**
     * Custom deserializer to properly handle the type mapping of each attribute value.  This is only needed because
     * we support arrays of primitive types, and by default Jackson will reify them as arrays of Object, and not
     * arrays of the primitive type.
     */
    private static final class AttributeSetDeserializer extends StdDeserializer<AttributeSet> {
        @SuppressWarnings("unused") // Required by Jackson
        protected AttributeSetDeserializer() {
            this(null);
        }

        protected AttributeSetDeserializer(Class<?> vc) {
            super(vc);
        }

        @NonNull
        @Override
        public AttributeSet deserialize(@NonNull JsonParser parser, DeserializationContext ctx) throws IOException {
            final ObjectCodec codec = parser.getCodec();

            final JsonNode root = codec.readTree(parser);
            final Iterator<Map.Entry<String, JsonNode>> fields = root.fields();
            final AttributeSet attributes = new AttributeSet();
            while (fields.hasNext()) {
                final Map.Entry<String, JsonNode> field = fields.next();
                final String key = field.getKey();
                final AttributeValue value = codec.treeToValue(field.getValue(), AttributeValue.class);
                attributes.setAttribute(key, value);

            }
            return attributes;
        }
    }

    /**
     * Custom deserializer to properly handle the type mapping of each attribute value.  This is only needed because
     * we support arrays of primitive types, and by default Jackson will reify them as arrays of Object, and not
     * arrays of the primitive type.
     */
    private static final class AttributeValueSerializer extends StdSerializer<AttributeValue> {
        @SuppressWarnings("unused") // Required by Jackson
        protected AttributeValueSerializer() {
            this(null);
        }

        protected AttributeValueSerializer(Class<AttributeValue> vc) {
            super(vc);
        }

        @Override
        public void serialize(AttributeValue value, JsonGenerator gen, SerializerProvider provider) throws IOException {
            gen.writeStartObject();
            gen.writeObjectField("type", value.type);
            gen.writeObjectField("value", value.value);
            gen.writeEndObject();
        }
    }

    /**
     * Custom deserializer to properly handle the type mapping of each attribute value.  This is only needed because
     * we support arrays of primitive types, and by default Jackson will reify them as arrays of Object, and not
     * arrays of the primitive type.
     */
    private static final class AttributeValueDeserializer extends StdDeserializer<AttributeValue> {
        @SuppressWarnings("unused") // Required by Jackson
        protected AttributeValueDeserializer() {
            this(null);
        }

        protected AttributeValueDeserializer(Class<?> vc) {
            super(vc);
        }

        @NonNull
        @Override
        public AttributeValue deserialize(@NonNull JsonParser parser, DeserializationContext ctx) throws IOException {
            final ObjectCodec codec = parser.getCodec();

            final JsonNode root = codec.readTree(parser);
            final JsonNode typeNode = root.get("type");
            final JsonNode valueNode = root.get("value");

            final AttributeType type = codec.treeToValue(typeNode, AttributeType.class);
            final Object value = codec.treeToValue(valueNode, type.valueType);

            return new AttributeValue(type, value);
        }
    }

    /**
     * Type reference for our internal attribute map.
     */
    private static class AttributeMapTypeReference extends TypeReference<Map<String, AttributeValue>> {
    }
}

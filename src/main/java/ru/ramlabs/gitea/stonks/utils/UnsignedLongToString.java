package ru.ramlabs.gitea.stonks.utils;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;

public final class UnsignedLongToString {

    public final static class Serializer extends JsonSerializer<Long> {

        @Override
        public void serialize(Long value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
            gen.writeString(Long.toUnsignedString(value));
        }
    }

    public final static class Deserializer extends JsonDeserializer<Long> {

        @Override
        public Long deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            return Long.parseUnsignedLong(p.getText());
        }
    }

}

package com.gabon.admin.config;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;
import java.time.Instant;

/**
 * 自定义Instant序列化器，将Instant转换为Unix时间戳(秒)
 * Custom Instant serializer to convert Instant to Unix timestamp (seconds)
 */
public class InstantToSecondsSerializer extends JsonSerializer<Instant> {

    @Override
    public void serialize(Instant value, JsonGenerator gen, SerializerProvider serializers)
            throws IOException {
        if (value != null) {
            gen.writeNumber(value.getEpochSecond());
        } else {
            gen.writeNull();
        }
    }
}

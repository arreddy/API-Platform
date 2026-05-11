package com.apiplatform.controlplane.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.UUID;

@Converter
public class UuidStringConverter implements AttributeConverter<String, UUID> {

    @Override
    public UUID convertToDatabaseColumn(String attribute) {
        if (attribute == null || attribute.isBlank()) return null;
        return UUID.fromString(attribute);
    }

    @Override
    public String convertToEntityAttribute(UUID dbData) {
        return dbData != null ? dbData.toString() : null;
    }
}

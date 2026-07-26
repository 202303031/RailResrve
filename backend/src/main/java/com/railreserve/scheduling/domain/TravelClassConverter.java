package com.railreserve.scheduling.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Persists {@link TravelClass} as its short external code (SL, 3A, ...) rather than the
 * Java constant name. {@code autoApply = true} applies it to every {@code TravelClass}
 * attribute automatically.
 */
@Converter(autoApply = true)
public class TravelClassConverter implements AttributeConverter<TravelClass, String> {

    @Override
    public String convertToDatabaseColumn(TravelClass attribute) {
        return attribute == null ? null : attribute.getCode();
    }

    @Override
    public TravelClass convertToEntityAttribute(String dbData) {
        return dbData == null ? null : TravelClass.fromCode(dbData);
    }
}

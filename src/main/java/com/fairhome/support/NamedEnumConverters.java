package com.fairhome.support;

import com.fairhome.application.ApplicationStatus;
import com.fairhome.application.Channel;
import com.fairhome.dedup.DuplicateResolution;
import com.fairhome.dedup.MatchType;
import com.fairhome.draw.DrawMode;
import com.fairhome.draw.Outcome;
import com.fairhome.rules.Gender;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Persist enums as their names. Hibernate 7 otherwise uses H2's numeric ENUM type, which breaks
 * {@code WHERE channel = 'ONLINE'} with a VARCHAR-to-DECFLOAT conversion error.
 */
public final class NamedEnumConverters {

    private NamedEnumConverters() {
    }

    @Converter(autoApply = false)
    public static class ChannelConverter implements AttributeConverter<Channel, String> {
        @Override
        public String convertToDatabaseColumn(Channel value) {
            return EnumNames.toName(value);
        }

        @Override
        public Channel convertToEntityAttribute(String value) {
            return EnumNames.fromName(Channel.class, value);
        }
    }

    @Converter(autoApply = false)
    public static class ApplicationStatusConverter implements AttributeConverter<ApplicationStatus, String> {
        @Override
        public String convertToDatabaseColumn(ApplicationStatus value) {
            return EnumNames.toName(value);
        }

        @Override
        public ApplicationStatus convertToEntityAttribute(String value) {
            return EnumNames.fromName(ApplicationStatus.class, value);
        }
    }

    @Converter(autoApply = false)
    public static class GenderConverter implements AttributeConverter<Gender, String> {
        @Override
        public String convertToDatabaseColumn(Gender value) {
            return EnumNames.toName(value);
        }

        @Override
        public Gender convertToEntityAttribute(String value) {
            return EnumNames.fromName(Gender.class, value);
        }
    }

    @Converter(autoApply = false)
    public static class MatchTypeConverter implements AttributeConverter<MatchType, String> {
        @Override
        public String convertToDatabaseColumn(MatchType value) {
            return EnumNames.toName(value);
        }

        @Override
        public MatchType convertToEntityAttribute(String value) {
            return EnumNames.fromName(MatchType.class, value);
        }
    }

    @Converter(autoApply = false)
    public static class DuplicateResolutionConverter implements AttributeConverter<DuplicateResolution, String> {
        @Override
        public String convertToDatabaseColumn(DuplicateResolution value) {
            return EnumNames.toName(value);
        }

        @Override
        public DuplicateResolution convertToEntityAttribute(String value) {
            return EnumNames.fromName(DuplicateResolution.class, value);
        }
    }

    @Converter(autoApply = false)
    public static class OutcomeConverter implements AttributeConverter<Outcome, String> {
        @Override
        public String convertToDatabaseColumn(Outcome value) {
            return EnumNames.toName(value);
        }

        @Override
        public Outcome convertToEntityAttribute(String value) {
            return EnumNames.fromName(Outcome.class, value);
        }
    }

    @Converter(autoApply = false)
    public static class DrawModeConverter implements AttributeConverter<DrawMode, String> {
        @Override
        public String convertToDatabaseColumn(DrawMode value) {
            return EnumNames.toName(value);
        }

        @Override
        public DrawMode convertToEntityAttribute(String value) {
            return EnumNames.fromName(DrawMode.class, value);
        }
    }
}

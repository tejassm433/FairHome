package com.fairhome.config;

import org.hibernate.dialect.H2Dialect;

/**
 * Hibernate 7's H2 dialect emits a native ENUM type. H2 stores those as numbers, so a query
 * parameter of {@code 'ONLINE'} becomes "CHARACTER VARYING to DECFLOAT". Force VARCHAR DDL instead.
 */
public class VarcharEnumH2Dialect extends H2Dialect {

    @Override
    public String getEnumTypeDeclaration(String name, String[] values) {
        int width = 16;
        if (values != null) {
            for (String value : values) {
                if (value != null && value.length() > width) {
                    width = value.length();
                }
            }
        }
        return "varchar(" + width + ")";
    }

    @Override
    public String getCheckCondition(String columnName, String[] values) {
        // H2 rejects some prepared-statement binds against enum IN-lists (23514 / DECFLOAT).
        return null;
    }

    @Override
    public String getCheckCondition(String columnName, Class<? extends Enum<?>> enumType) {
        return null;
    }
}

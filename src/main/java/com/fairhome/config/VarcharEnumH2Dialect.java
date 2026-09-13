package com.fairhome.config;

import org.hibernate.dialect.H2Dialect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Hibernate 7's H2 dialect emits a native ENUM type. H2 stores those as numbers, so a query
 * parameter of {@code 'ONLINE'} becomes "CHARACTER VARYING to DECFLOAT". Force VARCHAR DDL instead.
 */
public class VarcharEnumH2Dialect extends H2Dialect {

    private static final Logger log = LoggerFactory.getLogger(VarcharEnumH2Dialect.class);

    @Override
    public String getEnumTypeDeclaration(String name, String[] values) {
        log.debug("FairHome : VarcharEnumH2Dialect : in method getEnumTypeDeclaration : START");
        int width = 16;
        if (values != null) {
            for (String value : values) {
                if (value != null && value.length() > width) {
                    width = value.length();
                }
            }
        }
        String declaration = "varchar(" + width + ")";
        log.debug("FairHome : VarcharEnumH2Dialect : in method getEnumTypeDeclaration : END");
        return declaration;
    }

    @Override
    public String getCheckCondition(String columnName, String[] values) {
        log.debug("FairHome : VarcharEnumH2Dialect : in method getCheckCondition : START");
        // H2 rejects some prepared-statement binds against enum IN-lists (23514 / DECFLOAT).
        log.debug("FairHome : VarcharEnumH2Dialect : in method getCheckCondition : END");
        return null;
    }

    @Override
    public String getCheckCondition(String columnName, Class<? extends Enum<?>> enumType) {
        log.debug("FairHome : VarcharEnumH2Dialect : in method getCheckCondition : START");
        log.debug("FairHome : VarcharEnumH2Dialect : in method getCheckCondition : END");
        return null;
    }
}

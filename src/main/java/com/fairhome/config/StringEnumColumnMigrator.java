package com.fairhome.config;

import com.fairhome.application.ApplicationStatus;
import com.fairhome.application.Channel;
import com.fairhome.dedup.DuplicateResolution;
import com.fairhome.dedup.MatchType;
import com.fairhome.draw.DrawMode;
import com.fairhome.draw.Outcome;
import com.fairhome.rules.Gender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

/**
 * Rewrites leftover H2 ENUM / numeric columns to VARCHAR. {@code ddl-auto=update} will not change
 * an existing column type, which is what produced the channel count conversion error.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class StringEnumColumnMigrator implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(StringEnumColumnMigrator.class);

    private static final List<EnumColumn> COLUMNS = List.of(
            new EnumColumn("applications", "channel", 16, Channel.class),
            new EnumColumn("applications", "status", 32, ApplicationStatus.class),
            new EnumColumn("applications", "gender", 16, Gender.class),
            new EnumColumn("duplicate_flags", "match_type", 32, MatchType.class),
            new EnumColumn("duplicate_flags", "matchType", 32, MatchType.class),
            new EnumColumn("duplicate_flags", "resolution", 32, DuplicateResolution.class),
            new EnumColumn("allocations", "outcome", 24, Outcome.class),
            new EnumColumn("draw_runs", "mode", 16, DrawMode.class)
    );

    private final DataSource dataSource;

    public StringEnumColumnMigrator(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) {
        try (Connection connection = dataSource.getConnection()) {
            for (EnumColumn column : COLUMNS) {
                migrate(connection, column);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not normalise enum columns to VARCHAR", e);
        }
    }

    private void migrate(Connection connection, EnumColumn column) throws SQLException {
        ColumnRef ref = findColumn(connection, column.table(), column.column());
        if (ref == null) {
            return;
        }
        if (!isCharacterType(ref.typeName())) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("alter table " + ref.quotedTable()
                        + " alter column " + ref.quotedColumn()
                        + " varchar(" + column.width() + ")");
            }
            log.info("Changed {}.{} from {} to VARCHAR so enum filters bind as text.",
                    ref.tableName(), ref.columnName(), ref.typeName());
        }
        rewriteOrdinals(connection, ref, column.type());
    }

    private void rewriteOrdinals(Connection connection, ColumnRef ref, Class<? extends Enum<?>> type)
            throws SQLException {
        StringBuilder sql = new StringBuilder("update ")
                .append(ref.quotedTable())
                .append(" set ")
                .append(ref.quotedColumn())
                .append(" = case");
        for (Enum<?> constant : type.getEnumConstants()) {
            sql.append(" when ")
                    .append(ref.quotedColumn())
                    .append(" in ('")
                    .append(constant.ordinal())
                    .append("', '")
                    .append(constant.ordinal())
                    .append(".0') then '")
                    .append(constant.name())
                    .append('\'');
        }
        sql.append(" else ").append(ref.quotedColumn()).append(" end");
        try (Statement statement = connection.createStatement()) {
            int updated = statement.executeUpdate(sql.toString());
            if (updated > 0) {
                log.info("Rewrote {} ordinal value(s) in {}.{} to enum names.",
                        updated, ref.tableName(), ref.columnName());
            }
        }
    }

    private ColumnRef findColumn(Connection connection, String table, String column) throws SQLException {
        DatabaseMetaData meta = connection.getMetaData();
        try (ResultSet rs = meta.getColumns(null, null, "%", "%")) {
            while (rs.next()) {
                String tableName = rs.getString("TABLE_NAME");
                String columnName = rs.getString("COLUMN_NAME");
                if (table.equalsIgnoreCase(tableName) && column.equalsIgnoreCase(columnName)) {
                    return new ColumnRef(
                            rs.getString("TABLE_CAT"),
                            rs.getString("TABLE_SCHEM"),
                            tableName,
                            columnName,
                            rs.getString("TYPE_NAME"));
                }
            }
        }
        return null;
    }

    private static boolean isCharacterType(String typeName) {
        if (typeName == null) {
            return false;
        }
        String normalised = typeName.toUpperCase();
        return normalised.contains("CHAR") || normalised.contains("CLOB") || normalised.contains("TEXT");
    }

    private record EnumColumn(String table, String column, int width, Class<? extends Enum<?>> type) {
    }

    private record ColumnRef(String catalog, String schema, String tableName, String columnName,
                             String typeName) {
        String quotedTable() {
            StringBuilder sql = new StringBuilder();
            if (catalog != null && !catalog.isBlank()) {
                sql.append(quote(catalog)).append('.');
            }
            if (schema != null && !schema.isBlank()) {
                sql.append(quote(schema)).append('.');
            }
            sql.append(quote(tableName));
            return sql.toString();
        }

        String quotedColumn() {
            return quote(columnName);
        }

        private static String quote(String identifier) {
            return '"' + identifier.replace("\"", "\"\"") + '"';
        }
    }
}

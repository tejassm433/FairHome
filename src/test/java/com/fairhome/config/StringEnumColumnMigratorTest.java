package com.fairhome.config;

import com.fairhome.application.Channel;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StringEnumColumnMigratorTest {

    @Test
    void rewritesNumericChannelToEnumName() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:enum_repair;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        dataSource.setPassword("");

        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    create table applications (
                      id bigint primary key,
                      channel integer not null
                    )
                    """);
            statement.execute("insert into applications values (1, 0)");
            statement.execute("insert into applications values (2, 1)");
        }

        new StringEnumColumnMigrator(dataSource).run(new DefaultApplicationArguments());

        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(
                     "select channel from applications where id = 1")) {
            rs.next();
            assertEquals(Channel.ONLINE.name(), rs.getString(1));
        }
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(
                     "select count(*) from applications where channel = 'OFFLINE'")) {
            rs.next();
            assertEquals(1, rs.getInt(1));
        }
    }
}

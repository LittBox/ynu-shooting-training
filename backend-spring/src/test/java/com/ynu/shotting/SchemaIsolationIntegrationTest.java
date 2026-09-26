package com.ynu.shoting;

import com.ynu.shoting.entity.User;
import com.ynu.shoting.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** A same-named public table must never receive project reads or writes. */
@SpringBootTest(properties = {
        "spring.profiles.active=integration",
        "spring.datasource.url=${test.database.url:jdbc:h2:mem:schema-isolation;DB_CLOSE_DELAY=-1;MODE=PostgreSQL}",
        "spring.datasource.username=${test.database.user:sa}",
        "spring.datasource.password=${test.database.password:}",
        "booking.sweeper-enabled=false"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class SchemaIsolationIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;

    @Test
    void jdbcAndJpaUseProjectSchemaEvenWhenPublicHasTheSameTableName() {
        assertEquals("ynu-shooting", jdbc.queryForObject("SELECT current_schema()", String.class));
        assertEquals(13, jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.tables
                WHERE table_schema = 'ynu-shooting' AND table_type = 'BASE TABLE'
                """, Integer.class));

        jdbc.execute("CREATE SCHEMA IF NOT EXISTS \"public\"");
        jdbc.execute("CREATE TABLE \"public\".users (id BIGINT PRIMARY KEY, openid VARCHAR(255))");
        try {
            jdbc.update("INSERT INTO \"public\".users VALUES (1, 'public-sentinel')");
            User user = users.save(User.builder().openid("schema-isolation-user").nickname("Schema test").build());
            assertEquals(user.getId(), jdbc.queryForObject(
                    "SELECT id FROM users WHERE openid = 'schema-isolation-user'", Long.class));
            assertEquals(user.getId(), users.findByOpenid("schema-isolation-user").orElseThrow().getId());
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM \"public\".users", Integer.class));
            assertEquals("public-sentinel", jdbc.queryForObject("SELECT openid FROM \"public\".users", String.class));
        } finally {
            jdbc.execute("DROP TABLE \"public\".users");
        }
    }

    @Test
    void foreignKeyNamesAndTargetsAreExplicitAndConsistent() {
        var expected = java.util.Map.ofEntries(
                java.util.Map.entry("admin_schedules.admin_user_id", "users.id"),
                java.util.Map.entry("audit_log.admin_user_id", "users.id"),
                java.util.Map.entry("bookings.device_id", "devices.id"),
                java.util.Map.entry("bookings.user_id", "users.id"),
                java.util.Map.entry("cancellation_log.booking_id", "bookings.id"),
                java.util.Map.entry("cancellation_log.user_id", "users.id"),
                java.util.Map.entry("no_show_records.booking_id", "bookings.id"),
                java.util.Map.entry("no_show_records.user_id", "users.id"),
                java.util.Map.entry("profiles.user_id", "users.id"),
                java.util.Map.entry("score_attempts.training_session_id", "training_sessions.id"),
                java.util.Map.entry("scores.recorded_by_user_id", "users.id"),
                java.util.Map.entry("scores.training_session_id", "training_sessions.id"),
                java.util.Map.entry("training_sessions.booking_id", "bookings.id"),
                java.util.Map.entry("training_sessions.device_id", "devices.id"),
                java.util.Map.entry("training_sessions.user_id", "users.id"),
                java.util.Map.entry("user_availability.user_id", "users.id"));
        var actual = jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<java.util.Map<String, String>>) connection -> {
            var result = new java.util.HashMap<String, String>();
            var metadata = connection.getMetaData();
            try (var tables = metadata.getTables(null, "ynu-shooting", "%", null)) {
                while (tables.next()) {
                    String physicalTable = tables.getString("TABLE_NAME");
                    String table = physicalTable.toLowerCase(java.util.Locale.ROOT);
                    try (var keys = metadata.getImportedKeys(null, "ynu-shooting", physicalTable)) {
                        while (keys.next()) {
                            String column = keys.getString("FKCOLUMN_NAME").toLowerCase(java.util.Locale.ROOT);
                            String target = keys.getString("PKTABLE_NAME").toLowerCase(java.util.Locale.ROOT);
                            String pk = keys.getString("PKCOLUMN_NAME").toLowerCase(java.util.Locale.ROOT);
                            assertEquals("fk_" + table + "__" + column + "__" + target,
                                    keys.getString("FK_NAME").toLowerCase(java.util.Locale.ROOT));
                            result.put(table + "." + column, target + "." + pk);
                        }
                    }
                }
            }
            return result;
        });
        assertEquals(expected, actual);
        // Audit targets are deliberately historical references, not enforced foreign keys.
        org.junit.jupiter.api.Assertions.assertFalse(actual.containsKey("audit_log.target_user_id"));
    }
}

package com.petgrooming.pet_system.support;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 依實際查詢條件建立的索引（實體上的 @Index）都有被 ddl-auto=update 建出來，欄位順序正確。
 */
class DatabaseIndexTest extends IntegrationTestBase {

    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void queryIndexesExist() {
        Map<String, List<String>> expected = Map.ofEntries(
                Map.entry("appointments.idx_appointments_date", List.of("date")),
                Map.entry("appointments.idx_appointments_status_date", List.of("status", "date")),
                Map.entry("appointments.idx_appointments_user_pet", List.of("user_id", "pet_name")),
                Map.entry("transactions.idx_transactions_paid_time", List.of("paid", "payment_time")),
                Map.entry("walk_in_orders.idx_walk_in_orders_paid_time", List.of("paid", "payment_time")),
                Map.entry("walk_in_orders.idx_walk_in_orders_created_at", List.of("created_at")),
                Map.entry("walk_in_orders.idx_walk_in_orders_member_pet", List.of("member_id", "pet_name")),
                Map.entry("performance_records.idx_performance_records_date", List.of("service_date")),
                Map.entry("performance_records.idx_performance_records_staff_date", List.of("staff_id", "service_date")),
                Map.entry("performance_records.idx_performance_records_split_from", List.of("split_from_record_id")),
                Map.entry("performance_records.idx_performance_records_appointment", List.of("appointment_id")),
                Map.entry("performance_records.idx_performance_records_walk_in", List.of("walk_in_order_id")),
                Map.entry("users.idx_users_phone", List.of("phone")),
                Map.entry("users.idx_users_role", List.of("role")),
                Map.entry("topup_requests.idx_topup_requests_status_created", List.of("status", "created_at")),
                Map.entry("supply_usage_records.idx_supply_usage_records_used_at", List.of("used_at")),
                Map.entry("operation_logs.idx_operation_logs_created_at", List.of("created_at")),
                Map.entry("operation_logs.idx_operation_logs_category_created", List.of("category", "created_at")));

        expected.forEach((key, columns) -> {
            String[] parts = key.split("\\.");
            List<String> actual = jdbcTemplate.queryForList(
                    "SELECT column_name FROM information_schema.statistics "
                            + "WHERE table_schema = DATABASE() AND table_name = ? AND index_name = ? ORDER BY seq_in_index",
                    String.class, parts[0], parts[1]);
            assertThat(actual).as(key).containsExactlyElementsOf(columns);
        });
    }
}

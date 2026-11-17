package com.petgrooming.pet_system.ops;

import static com.petgrooming.pet_system.support.TestData.bookingRequest;
import static org.assertj.core.api.Assertions.assertThat;

import com.petgrooming.pet_system.dto.PetResponse;
import com.petgrooming.pet_system.model.User;
import com.petgrooming.pet_system.service.AppointmentService;
import com.petgrooming.pet_system.service.WalletService;
import com.petgrooming.pet_system.support.IntegrationTestBase;
import com.petgrooming.pet_system.support.TestData;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

/**
 * scripts/prelaunch-cleanup.sql 只會在正式站執行一次，執行前沒有機會試跑，所以在 CI 驗證。
 * 把測試資料庫連同外鍵完整複製一份，在複本上執行，不影響其他測試共用的資料。
 */
class PrelaunchCleanupScriptTest extends IntegrationTestBase {

    private static final String SOURCE_DB = "petdb";
    private static final String COPY_DB = "cleanup_check";

    @Autowired
    private TestData testData;

    @Autowired
    private AppointmentService appointmentService;

    @Autowired
    private WalletService walletService;

    @Value("${spring.datasource.url}")
    private String datasourceUrl;

    @Value("${spring.datasource.password}")
    private String password;

    @Test
    void removesCustomerDataAndKeepsStaffAndSettings() throws SQLException {
        User customer = testData.customer();
        User staff = testData.staff();
        PetResponse dog = testData.dog(customer);
        appointmentService.book(bookingRequest(dog.getId(), testData.openFutureDate(), LocalTime.NOON),
                customer.getUsername());
        walletService.getWallet(customer.getUsername());

        // Testcontainers 的 root 密碼與一般帳號相同；建立另一個資料庫需要 root 權限
        try (Connection root = DriverManager.getConnection(datasourceUrl, "root", password);
                Statement st = root.createStatement()) {
            assertThat(count(st, "SELECT COUNT(*) FROM " + SOURCE_DB + ".appointments")).isPositive();
            long groomingItems = count(st, "SELECT COUNT(*) FROM " + SOURCE_DB + ".grooming_items");

            copyDatabase(st);
            try {
                st.execute("CREATE TABLE users_line_backup_20260927 AS SELECT * FROM users");
                runScript(root, "scripts/prelaunch-cleanup-preview.sql");
                runScript(root, "scripts/prelaunch-cleanup.sql");

                assertThat(count(st, "SELECT COUNT(*) FROM users WHERE role = 'CUSTOMER'")).isZero();
                assertThat(count(st, "SELECT COUNT(*) FROM pets")).isZero();
                assertThat(count(st, "SELECT COUNT(*) FROM appointments")).isZero();
                assertThat(count(st, "SELECT COUNT(*) FROM transactions")).isZero();
                assertThat(count(st, "SELECT COUNT(*) FROM walk_in_orders")).isZero();
                assertThat(count(st, "SELECT COUNT(*) FROM wallets")).isZero();
                assertThat(count(st, "SELECT COUNT(*) FROM retail_products")).isZero();
                assertThat(count(st, "SELECT COUNT(*) FROM store_supplies")).isZero();
                assertThat(count(st, "SELECT COALESCE(SUM(booked), 0) FROM slot_capacity")).isZero();
                assertThat(count(st, "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = '"
                        + COPY_DB + "' AND table_name = 'users_line_backup_20260927'")).isZero();

                assertThat(count(st, "SELECT COUNT(*) FROM users WHERE id = " + staff.getId())).isOne();
                assertThat(count(st, "SELECT COUNT(*) FROM grooming_items")).isEqualTo(groomingItems);
            } finally {
                st.execute("DROP DATABASE IF EXISTS " + COPY_DB);
            }
        }
    }

    private static void copyDatabase(Statement st) throws SQLException {
        st.execute("DROP DATABASE IF EXISTS " + COPY_DB);
        st.execute("CREATE DATABASE " + COPY_DB + " CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
        List<String> tables = new ArrayList<>();
        try (ResultSet rs = st.executeQuery("SELECT table_name FROM information_schema.tables WHERE table_schema = '"
                + SOURCE_DB + "' AND table_type = 'BASE TABLE'")) {
            while (rs.next()) {
                tables.add(rs.getString(1));
            }
        }
        st.execute("USE " + COPY_DB);
        st.execute("SET FOREIGN_KEY_CHECKS = 0");
        for (String table : tables) {
            String ddl;
            try (ResultSet rs = st.executeQuery("SHOW CREATE TABLE " + SOURCE_DB + ".`" + table + "`")) {
                rs.next();
                ddl = rs.getString(2);
            }
            st.execute(ddl);
            st.execute("INSERT INTO `" + table + "` SELECT * FROM " + SOURCE_DB + ".`" + table + "`");
        }
        st.execute("SET FOREIGN_KEY_CHECKS = 1");
    }

    private static void runScript(Connection root, String path) {
        ScriptUtils.executeSqlScript(root, new EncodedResource(new FileSystemResource(path), StandardCharsets.UTF_8));
    }

    private static long count(Statement st, String sql) throws SQLException {
        try (ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getLong(1);
        }
    }
}

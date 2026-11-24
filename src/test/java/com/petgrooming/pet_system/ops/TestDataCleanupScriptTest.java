package com.petgrooming.pet_system.ops;

import static com.petgrooming.pet_system.support.TestData.bookingRequest;
import static org.assertj.core.api.Assertions.assertThat;

import com.petgrooming.pet_system.dto.DepositRequest;
import com.petgrooming.pet_system.dto.PetResponse;
import com.petgrooming.pet_system.model.User;
import com.petgrooming.pet_system.repository.UserRepository;
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
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

class TestDataCleanupScriptTest extends IntegrationTestBase {

    @Autowired private TestData testData;
    @Autowired private AppointmentService appointmentService;
    @Autowired private WalletService walletService;
    @Autowired private UserRepository userRepository;
    @Value("${spring.datasource.url}") private String datasourceUrl;
    @Value("${spring.datasource.password}") private String password;

    @Test
    void removesOnlyTestAccountsData() throws SQLException {
        User tester = testData.customer();
        tester.setName("洪大宝");
        userRepository.save(tester);
        User real = testData.customer();
        LocalDate day = testData.openFutureDate();
        PetResponse tdog = testData.dog(tester);
        PetResponse rdog = testData.dog(real);
        Long tAppt = appointmentService.book(bookingRequest(tdog.getId(), day, LocalTime.NOON), tester.getUsername()).getId();
        Long rAppt = appointmentService.book(bookingRequest(rdog.getId(), day, LocalTime.NOON), real.getUsername()).getId();
        DepositRequest d = new DepositRequest();
        d.setAmount(5000);
        walletService.deposit(tester.getUsername(), d);
        walletService.deposit(real.getUsername(), d);

        try (Connection root = DriverManager.getConnection(datasourceUrl, "root", password);
                Statement st = root.createStatement()) {
            st.execute("DROP DATABASE IF EXISTS tmp_check");
            st.execute("CREATE DATABASE tmp_check CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
            List<String> tables = new ArrayList<>();
            try (ResultSet rs = st.executeQuery("SELECT table_name FROM information_schema.tables WHERE table_schema = 'petdb' AND table_type = 'BASE TABLE'")) {
                while (rs.next()) {
                    tables.add(rs.getString(1));
                }
            }
            st.execute("USE tmp_check");
            st.execute("SET FOREIGN_KEY_CHECKS = 0");
            for (String t : tables) {
                try (ResultSet rs = st.executeQuery("SHOW CREATE TABLE petdb.`" + t + "`")) {
                    rs.next();
                    st.execute(rs.getString(2));
                }
                st.execute("INSERT INTO `" + t + "` SELECT * FROM petdb.`" + t + "`");
            }
            st.execute("SET FOREIGN_KEY_CHECKS = 1");
            long bookedBefore = count(st, "SELECT booked FROM slot_capacity WHERE slot_date = '" + day + "' AND slot_time = '12:00:00'");
            try {
                ScriptUtils.executeSqlScript(root, new EncodedResource(new FileSystemResource("scripts/test-data-cleanup-preview.sql"), StandardCharsets.UTF_8));
                ScriptUtils.executeSqlScript(root, new EncodedResource(new FileSystemResource("scripts/test-data-cleanup.sql"), StandardCharsets.UTF_8));
                assertThat(count(st, "SELECT COUNT(*) FROM appointments WHERE id = " + tAppt)).isZero();
                assertThat(count(st, "SELECT COUNT(*) FROM appointments WHERE id = " + rAppt)).isOne();
                assertThat(count(st, "SELECT COUNT(*) FROM wallets WHERE user_id = " + tester.getId())).isZero();
                assertThat(count(st, "SELECT COUNT(*) FROM wallets WHERE user_id = " + real.getId())).isOne();
                assertThat(count(st, "SELECT COUNT(*) FROM users WHERE id = " + tester.getId())).isOne();
                assertThat(count(st, "SELECT COUNT(*) FROM pets WHERE id = " + tdog.getId())).isOne();
                assertThat(count(st, "SELECT booked FROM slot_capacity WHERE slot_date = '" + day + "' AND slot_time = '12:00:00'")).isEqualTo(bookedBefore - 1);
            } finally {
                st.execute("DROP DATABASE IF EXISTS tmp_check");
            }
        }
    }

    private static long count(Statement st, String sql) throws SQLException {
        try (ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getLong(1);
        }
    }
}

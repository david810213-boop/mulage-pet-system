package com.petgrooming.pet_system.repository;

import com.petgrooming.pet_system.enums.DepositPaymentStatus;
import com.petgrooming.pet_system.model.DepositPayment;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface DepositPaymentRepository extends JpaRepository<DepositPayment, Long> {

    @Query("select d from DepositPayment d join fetch d.user where d.status = :status order by d.createdAt asc")
    List<DepositPayment> findWithUserByStatus(@Param("status") DepositPaymentStatus status);

    List<DepositPayment> findByUserIdAndAppointmentDateOrderByCreatedAtDesc(Long userId, LocalDate appointmentDate);

    List<DepositPayment> findByUserIdAndStatus(Long userId, DepositPaymentStatus status);

    long countByStatus(DepositPaymentStatus status);

    // 手動綁定既有匯入資料時，過戶這個會員名下的訂金匯款
    List<DepositPayment> findByUserId(Long userId);

    // 加鎖讀取：同一筆匯款的確認／駁回依序執行，避免連點或兩位員工同時操作時重複入帳（見 docs/adr/0008）
    @Query(value = "select status from deposit_payments where id = :id for update", nativeQuery = true)
    Optional<String> lockStatusById(@Param("id") Long id);
}

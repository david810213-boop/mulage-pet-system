package com.petgrooming.pet_system.repository;

import com.petgrooming.pet_system.model.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    // 查某使用者的所有交易（對應原本 getTransactionsByUser）
    List<Transaction> findByUserUsername(String username);

    // 查某筆預約是否已有交易紀錄（避免重複結帳）
    Optional<Transaction> findByAppointmentId(Long appointmentId);

    // 需求（追加，2026-09-13）：N+1 查詢優化，批次撈多筆預約對應的交易紀錄，
    // 取代 getAllForAdmin() 原本在迴圈裡逐筆呼叫 findByAppointmentId() 的寫法。
    List<Transaction> findByAppointmentIdIn(List<Long> appointmentIds);

    // 計算總營收（只算已付款，對應原本 calculateTotalRevenue）
    @Query("SELECT COALESCE(SUM(t.finalAmount), 0) FROM Transaction t WHERE t.paid = true")
    double calculateTotalRevenue();

    // 查所有已付款交易（財務報告用）
    List<Transaction> findByPaidTrue();
}
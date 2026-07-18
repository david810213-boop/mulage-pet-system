package com.petgrooming.pet_system.repository;

import com.petgrooming.pet_system.model.Wallet;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface WalletRepository extends JpaRepository<Wallet, Long> {
    Optional<Wallet> findByUserId(Long userId);

    // 扣款專用：對錢包列加悲觀寫鎖（SELECT ... FOR UPDATE），
    // 避免同一顧客並發扣款時各自讀到舊餘額造成超扣。
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from Wallet w where w.user.id = :userId")
    Optional<Wallet> lockByUserId(@Param("userId") Long userId);
}

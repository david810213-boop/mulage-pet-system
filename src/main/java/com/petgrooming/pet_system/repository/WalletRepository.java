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

    // 加鎖讀取（SELECT ... FOR UPDATE）。呼叫端須先移除快取中的同一實體，見 WalletService.lockWallet
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from Wallet w where w.user.id = :userId")
    Optional<Wallet> lockByUserId(@Param("userId") Long userId);
}

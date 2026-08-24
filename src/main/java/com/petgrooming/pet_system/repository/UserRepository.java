package com.petgrooming.pet_system.repository;

import com.petgrooming.pet_system.enums.UserRole;
import com.petgrooming.pet_system.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    boolean existsByUsername(String username);

    // 依 LINE userId 查詢（顧客 LINE 登入用）
    Optional<User> findByLineUserId(String lineUserId);

    // 依角色查詢
    List<User> findByRole(UserRole role);

    // 需求（追加）：既有會員資料匯入 + 顧客用電話認領帳號
    Optional<User> findByPhone(String phone);

    // 需求（追加）：認領帳號要找「還沒綁定 LINE 的匯入帳號」，跟一般查詢分開，
    // 避免不小心把已經有人在用的帳號（lineUserId 不是 null）也搜出來
    Optional<User> findByPhoneAndLineUserIdIsNull(String phone);
}
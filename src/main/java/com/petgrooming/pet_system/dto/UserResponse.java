package com.petgrooming.pet_system.dto;

import com.petgrooming.pet_system.enums.CustomerSource;
import com.petgrooming.pet_system.enums.UserRole;
import com.petgrooming.pet_system.model.User;
import lombok.Data;

// API 回傳格式，刻意不含 password
@Data
public class UserResponse {
    private Long id;
    private String username;
    private String name;
    private UserRole role;

    // ── 會員基本資料（供編輯資料頁與店家分析使用）─────────────────────
    private Integer age;
    private String occupation;
    private String residenceArea;
    private CustomerSource source;
    private String sourceLabel;       // 中文顯示用
    private boolean profileCompleted; // 是否已填寫過基本資料

    public static UserResponse from(User user) {
        UserResponse res = new UserResponse();
        res.setId(user.getId());
        res.setUsername(user.getUsername());
        res.setName(user.getName());
        res.setRole(user.getRole());
        res.setAge(user.getAge());
        res.setOccupation(user.getOccupation());
        res.setResidenceArea(user.getResidenceArea());
        res.setSource(user.getSource());
        res.setSourceLabel(user.getSource() != null ? user.getSource().getLabel() : null);
        res.setProfileCompleted(user.getProfileCompletedAt() != null);
        return res;
    }
}

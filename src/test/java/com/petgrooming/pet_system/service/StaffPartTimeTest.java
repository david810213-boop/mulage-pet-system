package com.petgrooming.pet_system.service;

import com.petgrooming.pet_system.dto.ResetPasswordRequest;
import com.petgrooming.pet_system.dto.UserResponse;
import com.petgrooming.pet_system.exception.MemberException;
import com.petgrooming.pet_system.model.User;
import com.petgrooming.pet_system.support.IntegrationTestBase;
import com.petgrooming.pet_system.support.TestData;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 代班只記積分不能登入、停用後不出現在經手人選單、代班轉正職。
 */
class StaffPartTimeTest extends IntegrationTestBase {

    @Autowired private UserService userService;
    @Autowired private TestData testData;

    @Test
    void partTimeStaffIsLabelledSelectableButCannotLogIn() {
        UserResponse partTime = userService.createPartTimeStaff("小美", null);

        assertThat(partTime.getName()).isEqualTo("小美（代班）");
        assertThat(partTime.isPartTime()).isTrue();
        assertThat(userService.operatorOptions()).anyMatch(o -> o.get("id").equals(partTime.getId()));
        assertThatThrownBy(() -> userService.resetPassword(partTime.getId(), passwordRequest("1234")))
                .isInstanceOf(MemberException.class);
    }

    @Test
    void deactivatedStaffDisappearsFromOperatorsAndReactivates() {
        User admin = testData.admin();
        UserResponse partTime = userService.createPartTimeStaff("阿明", "0912345678");

        userService.setStaffActive(partTime.getId(), false, admin);
        assertThat(userService.operatorOptions()).noneMatch(o -> o.get("id").equals(partTime.getId()));

        userService.setStaffActive(partTime.getId(), true, admin);
        assertThat(userService.operatorOptions()).anyMatch(o -> o.get("id").equals(partTime.getId()));
    }

    @Test
    void adminCannotDeactivateThemselves() {
        User admin = testData.admin();
        assertThatThrownBy(() -> userService.setStaffActive(admin.getId(), false, admin))
                .isInstanceOf(MemberException.class);
    }

    @Test
    void adminRoleCanBeGrantedAndRevokedButNotOnSelfOrPartTime() {
        User admin = testData.admin();
        User staff = testData.staff();

        userService.setStaffAdmin(staff.getId(), true, admin);
        assertThat(userService.getUserEntityById(staff.getId()).isAdmin()).isTrue();
        userService.setStaffAdmin(staff.getId(), false, admin);
        assertThat(userService.getUserEntityById(staff.getId()).isAdmin()).isFalse();

        assertThatThrownBy(() -> userService.setStaffAdmin(admin.getId(), false, admin))
                .isInstanceOf(MemberException.class);
        UserResponse partTime = userService.createPartTimeStaff("小安", null);
        assertThatThrownBy(() -> userService.setStaffAdmin(partTime.getId(), true, admin))
                .isInstanceOf(MemberException.class);
    }

    @Test
    void promotedPartTimeStaffCanLogInAndLosesLabel() {
        UserResponse partTime = userService.createPartTimeStaff("小華", null);
        String username = "promoted-" + UUID.randomUUID().toString().substring(0, 8) + "@pet.com";

        userService.promotePartTimeStaff(partTime.getId(), username, "secret1");

        User promoted = userService.getUserEntityById(partTime.getId());
        assertThat(promoted.getName()).isEqualTo("小華");
        assertThat(promoted.isPartTime()).isFalse();
        assertThat(userService.authenticate(username, "secret1")).isPresent();
    }

    private static ResetPasswordRequest passwordRequest(String password) {
        ResetPasswordRequest req = new ResetPasswordRequest();
        req.setNewPassword(password);
        return req;
    }
}

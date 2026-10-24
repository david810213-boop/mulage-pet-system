package com.petgrooming.pet_system.controller;

import com.petgrooming.pet_system.exception.ErrorCode;
import com.petgrooming.pet_system.exception.PaymentException;
import org.junit.jupiter.api.Test;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

/** 例外處理的訊息揭露規則：只有業務例外的訊息會回給使用者，其他一律通用訊息。 */
class ExceptionHandlerUnitTest {

    private final GlobalApiExceptionHandler global = new GlobalApiExceptionHandler();
    private final RestApiFallbackExceptionHandler fallback = new RestApiFallbackExceptionHandler();

    @Test
    void businessExceptionMessageIsShownWithItsCode() {
        ResponseEntity<ProblemDetail> response = global.handleBusiness(new PaymentException("此預約已完成結帳"));

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody().getProperties())
                .containsEntry("code", ErrorCode.BUSINESS_RULE.name())
                .containsEntry("message", "此預約已完成結帳");
    }

    @Test
    void nonBusinessIllegalArgumentDoesNotLeakItsMessage() {
        ResponseEntity<ProblemDetail> response = global.handleIllegalArgument(
                new IllegalArgumentException("No enum constant com.petgrooming.pet_system.enums.PetType.FISH"));

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody().getDetail()).doesNotContain("enum").doesNotContain("petgrooming");
    }

    @Test
    void unexpectedExceptionIs500WithoutInternalDetails() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/anything");

        ResponseEntity<ProblemDetail> response = fallback.handleUnexpected(
                new NullPointerException("Cannot invoke \"User.getName()\" because \"owner\" is null"), request);

        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertThat(response.getBody().getProperties()).containsEntry("code", ErrorCode.INTERNAL_ERROR.name());
        assertThat(response.getBody().getDetail()).doesNotContain("User").doesNotContain("null");
    }
}

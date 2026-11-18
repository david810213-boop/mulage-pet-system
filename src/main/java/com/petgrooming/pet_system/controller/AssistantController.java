package com.petgrooming.pet_system.controller;

import com.petgrooming.pet_system.annotation.CurrentUser;
import com.petgrooming.pet_system.dto.AssistantMessageRequest;
import com.petgrooming.pet_system.dto.AssistantQuotaResponse;
import com.petgrooming.pet_system.dto.AssistantReplyResponse;
import com.petgrooming.pet_system.exception.AuthException;
import com.petgrooming.pet_system.model.User;
import com.petgrooming.pet_system.service.assistant.AssistantService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 顧客端（LIFF）慕馬 AI 小助手。
 */
@Tag(name = "慕馬小助手", description = "顧客在 LIFF 頁面向慕馬提問")
@RestController
@RequiredArgsConstructor
public class AssistantController {

    private final AssistantService assistantService;

    /** 慕馬是否開放給這位會員；前端沒開放就不顯示慕馬。 */
    @GetMapping("/api/me/assistant/quota")
    public ResponseEntity<AssistantQuotaResponse> quota(@CurrentUser User user) {
        requireLogin(user);
        return ResponseEntity.ok(assistantService.quota(user));
    }

    @PostMapping("/api/me/assistant/messages")
    public ResponseEntity<AssistantReplyResponse> ask(@Valid @RequestBody AssistantMessageRequest req,
                                                      @CurrentUser User user) {
        requireLogin(user);
        return ResponseEntity.ok(assistantService.ask(user, req));
    }

    private static void requireLogin(User user) {
        if (user == null) {
            throw new AuthException("請先登入");
        }
    }
}

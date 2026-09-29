package com.petgrooming.pet_system.controller;

import com.petgrooming.pet_system.annotation.RequireRole;
import com.petgrooming.pet_system.enums.UserRole;
import com.petgrooming.pet_system.service.CloudinaryService;
import com.petgrooming.pet_system.service.GroomingNotePhotoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * 核對頁的美容狀況照片上傳（需求，2026-09-29）。網頁版與員工手機版共用，
 * 前端會先把照片壓縮再逐張上傳，拿到網址後放進核對表單的隱藏欄位。
 */
@RestController
@RequestMapping("/api/grooming-photos")
@RequireRole({ UserRole.ADMIN, UserRole.STAFF })
@RequiredArgsConstructor
public class GroomingPhotoController {

    private final GroomingNotePhotoService photoService;

    @PostMapping
    public ResponseEntity<Map<String, String>> upload(@RequestParam("file") MultipartFile file) {
        CloudinaryService.UploadResult r = photoService.uploadTemp(file);
        return ResponseEntity.ok(Map.of("url", r.url(), "publicId", r.publicId()));
    }

    @DeleteMapping
    public ResponseEntity<Map<String, String>> delete(@RequestParam String publicId) {
        photoService.deleteTemp(publicId);
        return ResponseEntity.ok(Map.of("message", "已移除"));
    }
}

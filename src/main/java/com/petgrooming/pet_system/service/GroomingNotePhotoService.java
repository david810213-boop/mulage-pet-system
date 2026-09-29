package com.petgrooming.pet_system.service;

import com.petgrooming.pet_system.exception.MediaException;
import com.petgrooming.pet_system.model.GroomingNotePhoto;
import com.petgrooming.pet_system.model.PetGroomingNote;
import com.petgrooming.pet_system.repository.GroomingNotePhotoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 美容狀況紀錄照片（需求，2026-09-29）。
 *
 * 流程：核對頁選照片 → 前端先壓縮再逐張呼叫 uploadTemp() 傳到 Cloudinary，
 * 拿回網址與 public_id 放進表單隱藏欄位 → 送出核對時才呼叫 attach() 寫進資料庫。
 * 這樣核對表單本身不用改成檔案上傳，網頁版、手機版、預約、現場單四種核對頁
 * 都用同一套做法。
 */
@Service
@RequiredArgsConstructor
public class GroomingNotePhotoService {

    public static final int MAX_PHOTOS = 5;
    private static final String FOLDER = "grooming-notes";

    private final CloudinaryService cloudinaryService;
    private final GroomingNotePhotoRepository photoRepository;

    /** 核對頁先上傳一張照片，回傳網址與 public_id（還沒跟任何紀錄綁在一起） */
    public CloudinaryService.UploadResult uploadTemp(MultipartFile file) {
        return cloudinaryService.upload(file, FOLDER);
    }

    /** 核對頁把剛上傳的照片移除（還沒送出核對前） */
    public void deleteTemp(String publicId) {
        if (!isOurPublicId(publicId)) {
            throw new MediaException("照片參數不正確");
        }
        cloudinaryService.deleteQuietly(publicId);
    }

    /** 表單送來的照片清單，驗證後整理成一組一組（網址＋public_id）。沒有照片回傳空清單 */
    public List<String[]> normalize(List<String> urls, List<String> publicIds) {
        List<String[]> result = new ArrayList<>();
        if (urls == null || urls.isEmpty()) {
            return result;
        }
        if (publicIds == null || publicIds.size() != urls.size()) {
            throw new MediaException("照片資料不完整，請重新整理頁面後再上傳一次");
        }
        for (int i = 0; i < urls.size(); i++) {
            String url = urls.get(i) == null ? "" : urls.get(i).trim();
            String pid = publicIds.get(i) == null ? "" : publicIds.get(i).trim();
            if (url.isEmpty()) {
                continue;
            }
            // 只接受我們自己傳到 Cloudinary 的照片，避免有人塞任意外部網址
            if (!url.startsWith("https://res.cloudinary.com/") || !isOurPublicId(pid)) {
                throw new MediaException("照片來源不正確，請重新上傳");
            }
            result.add(new String[] { url, pid });
        }
        if (result.size() > MAX_PHOTOS) {
            throw new MediaException("照片最多 " + MAX_PHOTOS + " 張");
        }
        return result;
    }

    /** 把照片綁到一筆美容狀況紀錄 */
    public void attach(PetGroomingNote note, List<String[]> photos) {
        int order = 0;
        for (String[] p : photos) {
            photoRepository.save(GroomingNotePhoto.builder()
                    .note(note)
                    .photoUrl(p[0])
                    .photoPublicId(p[1])
                    .sortOrder(order++)
                    .build());
        }
    }

    /** 一筆紀錄的所有照片網址：核對時拍的在前，事後補的舊單張照片接在後面 */
    public List<String> photoUrls(PetGroomingNote note) {
        List<String> urls = new ArrayList<>();
        if (note == null) {
            return urls;
        }
        photoRepository.findByNoteIdOrderBySortOrderAscIdAsc(note.getId())
                .forEach(p -> urls.add(p.getPhotoUrl()));
        if (note.getPhotoUrl() != null && !note.getPhotoUrl().isBlank()) {
            urls.add(note.getPhotoUrl());
        }
        return urls;
    }

    /** 多筆紀錄一次查齊照片（避免逐筆查詢），key 是紀錄 id，只含核對時拍的照片 */
    public Map<Long, List<String>> photoUrlsByNoteId(Collection<PetGroomingNote> notes) {
        Map<Long, List<String>> map = new LinkedHashMap<>();
        if (notes == null || notes.isEmpty()) {
            return map;
        }
        List<Long> ids = notes.stream().map(PetGroomingNote::getId).toList();
        for (GroomingNotePhoto p : photoRepository.findByNoteIdInOrderBySortOrderAscIdAsc(ids)) {
            map.computeIfAbsent(p.getNote().getId(), k -> new ArrayList<>()).add(p.getPhotoUrl());
        }
        return map;
    }

    /** 多筆紀錄的完整照片（核對照片＋舊單張照片），key 是紀錄 id */
    public Map<Long, List<String>> allPhotoUrlsByNoteId(Collection<PetGroomingNote> notes) {
        Map<Long, List<String>> map = photoUrlsByNoteId(notes);
        if (notes != null) {
            for (PetGroomingNote n : notes) {
                if (n.getPhotoUrl() != null && !n.getPhotoUrl().isBlank()) {
                    map.computeIfAbsent(n.getId(), k -> new ArrayList<>()).add(n.getPhotoUrl());
                }
            }
        }
        return map;
    }

    private boolean isOurPublicId(String publicId) {
        return publicId != null && publicId.startsWith(FOLDER + "/") && !publicId.contains("..");
    }
}

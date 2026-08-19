package com.petgrooming.pet_system;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.TimeZone;

@EnableScheduling
@SpringBootApplication
public class PetSystemApplication {

	public static void main(String[] args) {
		// 需求（追加）：Railway 容器預設時區是 UTC，系統全部用 LocalDateTime.now()
		// （沒有指定時區）記錄時間，包含操作紀錄、預約、交易、每天 19:00 的 LINE 提醒排程等，
		// 部署到 Railway 後全部會慢台灣時間 8 小時。這裡在 Spring 啟動前就把 JVM 預設時區
		// 鎖定成 Asia/Taipei，讓所有 LocalDateTime.now() 都直接是正確的台灣時間，
		// 不用一一去改每個呼叫的地方。
		TimeZone.setDefault(TimeZone.getTimeZone("Asia/Taipei"));
		SpringApplication.run(PetSystemApplication.class, args);
	}

}

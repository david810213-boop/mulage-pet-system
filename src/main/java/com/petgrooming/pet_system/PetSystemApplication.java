package com.petgrooming.pet_system;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class PetSystemApplication {

	public static void main(String[] args) {
		SpringApplication.run(PetSystemApplication.class, args);
	}

}

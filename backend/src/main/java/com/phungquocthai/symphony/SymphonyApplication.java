package com.phungquocthai.symphony;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

//@SpringBootApplication(exclude = {SecurityAutoConfiguration.class})
@SpringBootApplication
@EnableCaching
public class SymphonyApplication {

	public static void main(String[] args) {
		SpringApplication.run(SymphonyApplication.class, args);
	}

}

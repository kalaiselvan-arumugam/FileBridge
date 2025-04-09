package com.scb.filebridge;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import com.scb.filebridge.util.CustomBanner;

@SpringBootApplication
public class FileBridgeApplication {

	public static void main(String[] args) {
		SpringApplication app = new SpringApplication(FileBridgeApplication.class);
		app.setBanner(new CustomBanner());
		app.run(args);
	}
}
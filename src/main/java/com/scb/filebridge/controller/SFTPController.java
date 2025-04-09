package com.scb.filebridge.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.scb.filebridge.service.SftpService;

@RestController
@RequestMapping("/sftp")
public class SFTPController {

	@Autowired
	private SftpService sftpService;

	@GetMapping("/copy")
	public String sayHello(@RequestParam String fileIdentifier) {
		sftpService.copyFromSftp("/"+fileIdentifier);
		return "success";
	}
}

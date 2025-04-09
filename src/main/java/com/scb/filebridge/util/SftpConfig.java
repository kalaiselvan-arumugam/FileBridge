package com.scb.filebridge.util;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "sftp")
public class SftpConfig {
    private String host;
    private int port;
    private String username;
    private String password;
    private String knownHosts;
    private int sessionTimeout;
    private int channelTimeout;
    private String localBasePath;
    private String remoteBasePath;
    private String integrityCheck;
    private String deleteSource;

    public boolean shouldDeleteSource() {
        return "yes".equalsIgnoreCase(deleteSource);
    }
}
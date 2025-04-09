package com.scb.filebridge.service;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.Vector;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;
import org.springframework.stereotype.Service;

import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpException;
import com.scb.filebridge.util.SftpConfig;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class SftpService {

    private final SftpConfig config;

    public boolean copyFromSftp(String remoteDir) {
        ChannelSftp channel = null;
        try {
            channel = connect();
            if (channel == null) return false;
            //log.info("Deleting remote source directory: {}");
            String remotePath = combineRemotePaths(config.getRemoteBasePath(), remoteDir);
            String localPath = Paths.get(config.getLocalBasePath(), remoteDir).toString();

            boolean success = copyDirectoryFrom(channel, remotePath, localPath);

            if (success && config.shouldDeleteSource()) {
                System.out.println("Deleting remote source directory: " + remotePath);
                deleteRemoteDirectory(channel, remotePath);
            }
            return success;
        } catch (Exception e) {
            System.err.println("Error in copyFromSftp: " + e.getMessage());
            return false;
        } finally {
            disconnect(channel);
        }
    }

    public boolean copyToSftp(String localDir) {
        ChannelSftp channel = null;
        try {
            channel = connect();
            if (channel == null) return false;

            String localPath = Paths.get(config.getLocalBasePath(), localDir).toString();
            String remotePath = combineRemotePaths(config.getRemoteBasePath(), localDir);

            boolean success = copyDirectoryTo(channel, localPath, remotePath);

            if (success && config.shouldDeleteSource()) {
                System.out.println("Deleting local source directory: " + localPath);
                deleteLocalDirectory(localPath);
            }
            return success;
        } catch (Exception e) {
            System.err.println("Error in copyToSftp: " + e.getMessage());
            return false;
        } finally {
            disconnect(channel);
        }
    }

    private ChannelSftp connect() {
        try {
            JSch jsch = new JSch();
            jsch.setKnownHosts(config.getKnownHosts());

            Session session = jsch.getSession(config.getUsername(), config.getHost(), config.getPort());
            session.setPassword(config.getPassword());
            session.setConfig("StrictHostKeyChecking", "yes");
            session.connect(config.getSessionTimeout());

            Channel channel = session.openChannel("sftp");
            channel.connect(config.getChannelTimeout());
            return (ChannelSftp) channel;
        } catch (JSchException e) {
            System.err.println("SFTP connection error: " + e.getMessage());
            return null;
        }
    }

    private void disconnect(ChannelSftp channel) {
        if (channel != null) {
            try {
                channel.disconnect();
                if (channel.getSession() != null) {
                    channel.getSession().disconnect();
                }
            } catch (Exception e) {
                System.err.println("Disconnection error: " + e.getMessage());
            }
        }
    }

    private boolean copyDirectoryFrom(ChannelSftp channel, String remoteDir, String localDir) {
        try {
            FileUtils.forceMkdir(new File(localDir));
            Vector<ChannelSftp.LsEntry> entries = channel.ls(remoteDir);
            boolean allSuccess = true;

            for (ChannelSftp.LsEntry entry : entries) {
                String filename = entry.getFilename();
                if (filename.equals(".") || filename.equals("..")) continue;

                String remotePath = combineRemotePaths(remoteDir, filename);
                String localPath = Paths.get(localDir, filename).toString();

                if (entry.getAttrs().isDir()) {
                    allSuccess &= copyDirectoryFrom(channel, remotePath, localPath);
                } else {
                    allSuccess &= copyFileFrom(channel, remotePath, localPath);
                }
            }
            return allSuccess;
        } catch (Exception e) {
            System.err.println("Error copying directory from SFTP: " + e.getMessage());
            return false;
        }
    }

    private boolean copyFileFrom(ChannelSftp channel, String remotePath, String localPath) {
        try (InputStream is = channel.get(remotePath);
             OutputStream os = FileUtils.openOutputStream(new File(localPath))) {
            
            MessageDigest md = getDigest();
            byte[] buffer = new byte[8192];
            int bytesRead;
            
            while ((bytesRead = is.read(buffer)) != -1) {
                os.write(buffer, 0, bytesRead);
                md.update(buffer, 0, bytesRead);
            }
            
            String remoteHash = bytesToHex(md.digest());
            String localHash = calculateHash(new File(localPath));

            System.out.printf("Downloaded: %s | Checksum: %s | Integrity: %s%n",
                    remotePath, remoteHash, remoteHash.equals(localHash) ? "PASS" : "FAIL");

            return remoteHash.equals(localHash);
        } catch (Exception e) {
            System.err.println("Error downloading file: " + e.getMessage());
            return false;
        }
    }

    private boolean copyDirectoryTo(ChannelSftp channel, String localDir, String remoteDir) {
        try {
            mkdirs(channel, remoteDir);
            File localDirFile = new File(localDir);
            File[] files = localDirFile.listFiles();
            if (files == null) return false;

            boolean allSuccess = true;
            for (File file : files) {
                String remotePath = combineRemotePaths(remoteDir, file.getName());
                String localPath = file.getAbsolutePath();

                if (file.isDirectory()) {
                    allSuccess &= copyDirectoryTo(channel, localPath, remotePath);
                } else {
                    allSuccess &= copyFileTo(channel, localPath, remotePath);
                }
            }
            return allSuccess;
        } catch (Exception e) {
            System.err.println("Error copying directory to SFTP: " + e.getMessage());
            return false;
        }
    }

    private boolean copyFileTo(ChannelSftp channel, String localPath, String remotePath) {
        try (InputStream is = FileUtils.openInputStream(new File(localPath));
             OutputStream os = channel.put(remotePath)) {
            
            MessageDigest md = getDigest();
            byte[] buffer = new byte[8192];
            int bytesRead;
            
            while ((bytesRead = is.read(buffer)) != -1) {
                os.write(buffer, 0, bytesRead);
                md.update(buffer, 0, bytesRead);
            }
            
            String sentHash = bytesToHex(md.digest());
            String localHash = calculateHash(new File(localPath));

            System.out.printf("Uploaded: %s | Checksum: %s | Integrity: %s%n",
                    remotePath, sentHash, sentHash.equals(localHash) ? "PASS" : "FAIL");

            return sentHash.equals(localHash);
        } catch (Exception e) {
            System.err.println("Error uploading file: " + e.getMessage());
            return false;
        }
    }

    // Helper methods
    
    private String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : bytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
    }

    private MessageDigest getDigest() {
        switch (config.getIntegrityCheck().toUpperCase()) {
            case "SHA-256": return DigestUtils.getSha256Digest();
            case "MD5": return DigestUtils.getMd5Digest();
            default: throw new IllegalArgumentException("Unsupported hash algorithm");
        }
    }

    private String calculateHash(File file) throws IOException {
        try (InputStream is = FileUtils.openInputStream(file)) {
            return switch (config.getIntegrityCheck().toUpperCase()) {
                case "SHA-256" -> DigestUtils.sha256Hex(is);
                case "MD5" -> DigestUtils.md5Hex(is);
                default -> "N/A";
            };
        }
    }

    private String combineRemotePaths(String base, String addition) {
        String combined = base.replaceAll("/+$", "") + "/" + addition.replaceAll("^/+", "");
        return combined.replaceAll("/+", "/");
    }

    private void mkdirs(ChannelSftp channel, String path) throws SftpException {
        String[] folders = path.split("/");
        StringBuilder currentPath = new StringBuilder();
        
        for (String folder : folders) {
            if (folder.isEmpty()) continue;
            currentPath.append("/").append(folder);
            String dirPath = currentPath.toString();
            
            try {
                channel.mkdir(dirPath);
            } catch (SftpException e) {
                if (e.id != ChannelSftp.SSH_FX_FAILURE) throw e;
            }
        }
    }

    private boolean deleteRemoteDirectory(ChannelSftp channel, String path) {
        try {
            Vector<ChannelSftp.LsEntry> entries = channel.ls(path);
            for (ChannelSftp.LsEntry entry : entries) {
                String filename = entry.getFilename();
                if (filename.equals(".") || filename.equals("..")) continue;

                String filepath = combineRemotePaths(path, filename);
                if (entry.getAttrs().isDir()) {
                    deleteRemoteDirectory(channel, filepath);
                } else {
                    channel.rm(filepath);
                }
            }
            channel.rmdir(path);
            return true;
        } catch (SftpException e) {
            System.err.println("Error deleting remote directory: " + e.getMessage());
            return false;
        }
    }

    private boolean deleteLocalDirectory(String path) {
        try {
            File directory = new File(path);
            if (directory.exists()) {
                FileUtils.deleteDirectory(directory);
                return true;
            }
            return false;
        } catch (IOException e) {
            System.err.println("Error deleting local directory: " + e.getMessage());
            return false;
        }
    }
}
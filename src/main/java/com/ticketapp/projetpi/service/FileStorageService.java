package com.ticketapp.projetpi.service;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Service
public class FileStorageService {

    private final String uploadDir = "uploads/profile-pics";

    public FileStorageService() {
        try {
            Files.createDirectories(Paths.get(uploadDir));
        } catch (IOException e) {
            throw new RuntimeException("Could not initialize storage directory", e);
        }
    }

    /**
     * Store the file and return the relative path
     */
    public String storeFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return null;
        }

        try {
            String fileName = UUID.randomUUID() + "_" + file.getOriginalFilename();
            Path path = Paths.get(uploadDir, fileName);
            Files.copy(file.getInputStream(), path);
            // Return relative path that will be used by the WebConfig to serve the image
            return "/uploads/profile-pics/" + fileName;
        } catch (IOException e) {
            throw new RuntimeException("Could not store the file. Error: " + e.getMessage(), e);
        }
    }

    /**
     * Delete a file given its relative path
     */
    public void deleteFile(String filePath) {
        if (filePath == null || filePath.isEmpty()) {
            return;
        }

        try {
            // Remove the leading "/" and "/uploads/" to get the internal path
            String internalPath = filePath.replace("/uploads/", "uploads/");
            Path path = Paths.get(internalPath);
            Files.deleteIfExists(path);
        } catch (IOException e) {
            // Log error but don't fail the operation
            System.err.println("Could not delete file: " + filePath + ". Error: " + e.getMessage());
        }
    }
}

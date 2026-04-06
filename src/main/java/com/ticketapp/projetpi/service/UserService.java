package com.ticketapp.projetpi.service;

import com.ticketapp.projetpi.dto.UpdateUserRequest;
import com.ticketapp.projetpi.dto.UserResponse;
import com.ticketapp.projetpi.entity.User;
import com.ticketapp.projetpi.exception.UserNotFoundException;
import com.ticketapp.projetpi.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final FileStorageService fileStorageService;

    public UserService(UserRepository userRepository, FileStorageService fileStorageService) {
        this.userRepository = userRepository;
        this.fileStorageService = fileStorageService;
    }

    // ── Read ────────────────────────────────────────────────────────────────

    public UserResponse getUserById(UUID id) {
        return toResponse(userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException(id)) );
    }

    public UserResponse getUserByEmail(String email) {
        return toResponse(userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException(email)));
    }

    public List<UserResponse> getAllUsers() {
        return userRepository.findAll()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    // ── Update ──────────────────────────────────────────────────────────────

    public UserResponse updateUser(UUID id, UpdateUserRequest request, org.springframework.web.multipart.MultipartFile profilePicFile) {
        User user = userRepository.findById(id)
                .orElseThrow(()-> new UserNotFoundException(id))   ;

        if (request.getUsername() != null && !request.getUsername().isBlank()) {
            user.setUsername(request.getUsername());
        }
        if (request.getPhone() != null && !request.getPhone().isBlank()) {
            user.setPhone(request.getPhone());
        }
        
        // Handle physical file upload
        if (profilePicFile != null && !profilePicFile.isEmpty()) {
            // Delete old physical file if it was a local upload
            if (user.getProfilePic() != null && user.getProfilePic().startsWith("/uploads/")) {
                fileStorageService.deleteFile(user.getProfilePic());
            }
            String filePath = fileStorageService.storeFile(profilePicFile);
            user.setProfilePic(filePath);
        } else if (request.getProfilePic() != null) {
            // If the user wants to clear the profile pic
            if (request.getProfilePic().isBlank() || "null".equalsIgnoreCase(request.getProfilePic())) {
                if (user.getProfilePic() != null && user.getProfilePic().startsWith("/uploads/")) {
                    fileStorageService.deleteFile(user.getProfilePic());
                }
                user.setProfilePic(null);
            } else {
                // Manually setting a path (or keeping current)
                user.setProfilePic(request.getProfilePic());
            }
        }

        return toResponse(userRepository.save(user));
    }

    public UserResponse deleteProfilePic(UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException(id));
        
        if (user.getProfilePic() != null && user.getProfilePic().startsWith("/uploads/")) {
            fileStorageService.deleteFile(user.getProfilePic());
        }
        
        user.setProfilePic(null);
        User savedUser = userRepository.save(user);
        return toResponse(savedUser);
    }

    // ── Delete ──────────────────────────────────────────────────────────────

    public void deleteUser(UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException(id));

        if (user.getProfilePic() != null && user.getProfilePic().startsWith("/uploads/")) {
            fileStorageService.deleteFile(user.getProfilePic());
        }
        
        userRepository.deleteById(id);
    }

    // ── Mapper ──────────────────────────────────────────────────────────────

    private UserResponse toResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getPhone(),
                user.getRole().name(),
                user.getProfilePic(),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }
}
// UserResponse.java  (NEW)
package com.ticketapp.projetpi.dto;

import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserResponse {

    private UUID id;
    private String username;
    private String email;
    private String phone;
    private String role;
    private String profilePic;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
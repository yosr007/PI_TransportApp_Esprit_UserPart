// UpdateUserRequest.java  (NEW)
package com.ticketapp.projetpi.dto;

import jakarta.validation.constraints.*;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UpdateUserRequest {

    @NotBlank(message = "Username is required")
    private String username;

    private String phone;
}
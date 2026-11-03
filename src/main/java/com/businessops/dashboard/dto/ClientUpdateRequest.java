package com.businessops.dashboard.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request payload for updating client details.
 */
public record ClientUpdateRequest(
        @NotBlank(message = "Client name cannot be blank")
        @Size(max = 150, message = "Client name cannot exceed 150 characters")
        String name,

        @Size(max = 150, message = "Company name cannot exceed 150 characters")
        String company,

        @Email(message = "Invalid email format")
        @Size(max = 254, message = "Email cannot exceed 254 characters")
        String email,

        @Size(max = 30, message = "Phone number cannot exceed 30 characters")
        String phone,

        String status,

        @Size(max = 2000, message = "Notes cannot exceed 2000 characters")
        String notes
) {
}

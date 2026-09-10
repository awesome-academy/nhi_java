package demo.tripgo.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// Thông tin liên hệ trong body đặt tour.
public record ContactRequest(
    @NotBlank(message = "Contact name is required")
    @Size(max = 100, message = "Contact name must not exceed 100 characters")
    String fullName,

    @NotBlank(message = "Contact email is required")
    @Email(message = "Contact email is invalid")
    String email,

    @NotBlank(message = "Contact phone is required")
    @Size(max = 30, message = "Contact phone must not exceed 30 characters")
    String phone
) {
}

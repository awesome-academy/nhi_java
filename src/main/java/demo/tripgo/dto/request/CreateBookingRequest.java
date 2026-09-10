package demo.tripgo.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

// Body đặt tour. Không nhận userId/totalPrice từ client — server tự lấy user từ JWT và tự tính tiền.
public record CreateBookingRequest(
    @NotNull(message = "tourId is required")
    Long tourId,

    @NotNull(message = "date is required")
    @FutureOrPresent(message = "date must be today or in the future")
    LocalDate date,

    @NotNull(message = "adults is required")
    @Min(value = 1, message = "adults must be at least 1")
    @Max(value = 100, message = "adults must not exceed 100")
    Integer adults,

    @NotNull(message = "children is required")
    @Min(value = 0, message = "children must be at least 0")
    @Max(value = 100, message = "children must not exceed 100")
    Integer children,

    @NotNull(message = "contact is required")
    @Valid
    ContactRequest contact
) {
}

package demo.tripgo.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

// Body đặt tour. Không nhận userId/totalPrice từ client — server tự lấy user từ JWT và tự tính tiền.
public record CreateBookingRequest(
    @NotNull(message = "tourId không được để trống")
    Long tourId,

    @NotNull(message = "date không được để trống")
    @FutureOrPresent(message = "date phải là hôm nay hoặc một ngày trong tương lai")
    LocalDate date,

    @NotNull(message = "adults không được để trống")
    @Min(value = 1, message = "adults phải ít nhất là 1")
    @Max(value = 100, message = "adults không được vượt quá 100")
    Integer adults,

    @NotNull(message = "children không được để trống")
    @Min(value = 0, message = "children không được nhỏ hơn 0")
    @Max(value = 100, message = "children không được vượt quá 100")
    Integer children,

    @NotNull(message = "contact không được để trống")
    @Valid
    ContactRequest contact
) {
}

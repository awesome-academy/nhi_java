package demo.tripgo.dto.response;

public record ContactResponse(
    String fullName,
    String email,
    String phone
) {
}

package demo.tripgo.dto.response;

public record ItineraryDayResponse(
    int dayNumber,
    String title,
    String description
) {
}

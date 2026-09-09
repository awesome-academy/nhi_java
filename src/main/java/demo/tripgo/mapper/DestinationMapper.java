package demo.tripgo.mapper;

import demo.tripgo.dto.response.DestinationSummaryResponse;
import demo.tripgo.repository.DestinationTourCountView;
import org.springframework.stereotype.Component;

@Component
public class DestinationMapper {

    public DestinationSummaryResponse toSummary(DestinationTourCountView view) {
        return new DestinationSummaryResponse(
            view.getId(),
            view.getName(),
            view.getSlug(),
            view.getTourCount()
        );
    }
}

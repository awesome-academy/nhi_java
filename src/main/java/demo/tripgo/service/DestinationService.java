package demo.tripgo.service;

import demo.tripgo.dto.response.DestinationSummaryResponse;
import demo.tripgo.dto.response.ListResponse;
import demo.tripgo.mapper.DestinationMapper;
import demo.tripgo.repository.DestinationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class DestinationService {

    private final DestinationRepository destinationRepository;
    private final DestinationMapper destinationMapper;

    public DestinationService(
        DestinationRepository destinationRepository,
        DestinationMapper destinationMapper
    ) {
        this.destinationRepository = destinationRepository;
        this.destinationMapper = destinationMapper;
    }

    // Danh sách điểm đến kèm số tour, sắp theo tên; bọc object envelope { data: [...] }.
    public ListResponse<DestinationSummaryResponse> listDestinations() {
        return ListResponse.of(
            destinationRepository.findAllWithTourCount().stream()
                .map(destinationMapper::toSummary)
                .toList()
        );
    }
}

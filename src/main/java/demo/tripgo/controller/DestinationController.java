package demo.tripgo.controller;

import demo.tripgo.dto.response.DestinationSummaryResponse;
import demo.tripgo.dto.response.ListResponse;
import demo.tripgo.service.DestinationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/destinations")
public class DestinationController {

    private final DestinationService destinationService;

    public DestinationController(DestinationService destinationService) {
        this.destinationService = destinationService;
    }

    @GetMapping
    public ResponseEntity<ListResponse<DestinationSummaryResponse>> listDestinations() {
        return ResponseEntity.ok(destinationService.listDestinations());
    }
}

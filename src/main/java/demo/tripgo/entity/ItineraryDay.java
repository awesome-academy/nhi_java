package demo.tripgo.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

// Một ngày trong lịch trình tour (chi tiết theo ngày ở màn detail).
@Getter
@Setter
@Entity
@Table(name = "tour_itinerary_days", indexes = @Index(name = "idx_itinerary_tour", columnList = "tour_id"))
public class ItineraryDay {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tour_id", nullable = false)
    private Tour tour;

    @Column(name = "day_number", nullable = false)
    private int dayNumber;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "text")
    private String description;
}

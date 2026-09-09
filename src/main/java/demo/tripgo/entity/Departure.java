package demo.tripgo.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

// Một ngày khởi hành của tour cùng sức chứa và số chỗ đã đặt.
@Getter
@Setter
@Entity
@Table(
    name = "tour_departures",
    indexes = @Index(name = "idx_departure_tour_date", columnList = "tour_id, departure_date")
)
public class Departure {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tour_id", nullable = false)
    private Tour tour;

    @Column(name = "departure_date", nullable = false)
    private LocalDate departureDate;

    @Column(name = "total_seats", nullable = false)
    private int totalSeats;

    @Column(name = "booked_seats", nullable = false)
    private int bookedSeats;

    // Số chỗ còn lại; không lưu cột riêng để luôn nhất quán với total/booked.
    @Transient
    public int getRemainingSeats() {
        return totalSeats - bookedSeats;
    }
}

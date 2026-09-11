package demo.tripgo.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.Getter;
import lombok.Setter;

// Thông tin liên hệ của đơn (nhúng vào bảng bookings).
@Getter
@Setter
@Embeddable
public class ContactInfo {

    @Column(name = "contact_full_name", nullable = false, length = 100)
    private String fullName;

    @Column(name = "contact_email", nullable = false, length = 255)
    private String email;

    @Column(name = "contact_phone", nullable = false, length = 30)
    private String phone;

    @Column(name = "contact_note", length = 500)
    private String note;
}

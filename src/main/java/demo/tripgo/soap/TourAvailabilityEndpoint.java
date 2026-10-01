package demo.tripgo.soap;

import demo.tripgo.entity.Departure;
import demo.tripgo.entity.Tour;
import demo.tripgo.repository.DepartureRepository;
import demo.tripgo.repository.TourRepository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ws.server.endpoint.annotation.Endpoint;
import org.springframework.ws.server.endpoint.annotation.PayloadRoot;
import org.springframework.ws.server.endpoint.annotation.RequestPayload;
import org.springframework.ws.server.endpoint.annotation.ResponsePayload;
import org.springframework.ws.soap.server.endpoint.annotation.FaultCode;
import org.springframework.ws.soap.server.endpoint.annotation.SoapFault;

import java.time.LocalDate;
import java.util.List;

// Tra cứu chỗ trống cho hệ thống đối tác.
//
// Chỉ đọc và chỉ trả số liệu chỗ trống — không có thông tin khách hàng. Đó là lý do endpoint này
// mở công khai được: client SOAP không có phiên đăng nhập để mang theo.
@Endpoint
public class TourAvailabilityEndpoint {

    private static final String NAMESPACE = "http://tripgo.demo/soap";

    private final TourRepository tourRepository;
    private final DepartureRepository departureRepository;

    public TourAvailabilityEndpoint(
        TourRepository tourRepository,
        DepartureRepository departureRepository
    ) {
        this.tourRepository = tourRepository;
        this.departureRepository = departureRepository;
    }

    @PayloadRoot(namespace = NAMESPACE, localPart = "getTourAvailabilityRequest")
    @ResponsePayload
    @Transactional(readOnly = true)
    public GetTourAvailabilityResponse getAvailability(
        @RequestPayload GetTourAvailabilityRequest request
    ) {
        // findIdBySlug đã lọc tour xoá mềm, nên tour trong thùng rác không lộ ra ngoài.
        Long tourId = tourRepository.findIdBySlug(nullSafe(request.getTourSlug()))
            .orElseThrow(() -> new TourNotFoundException(
                "Không tìm thấy tour với slug '" + request.getTourSlug() + "'"));
        Tour tour = tourRepository.findActiveById(tourId).orElseThrow();

        LocalDate from = request.getFromDate() == null
            ? LocalDate.now()
            : LocalDate.of(request.getFromDate().getYear(),
                request.getFromDate().getMonth(), request.getFromDate().getDay());

        List<Departure> departures = departureRepository
            .findByTourIdAndDepartureDateGreaterThanEqualOrderByDepartureDateAsc(tourId, from);

        GetTourAvailabilityResponse response = new GetTourAvailabilityResponse();
        response.setTourSlug(tour.getSlug());
        response.setTourTitle(tour.getTitle());
        departures.stream().map(TourAvailabilityEndpoint::toPayload)
            .forEach(response.getDeparture()::add);
        return response;
    }

    private static demo.tripgo.soap.Departure toPayload(Departure departure) {
        demo.tripgo.soap.Departure payload = new demo.tripgo.soap.Departure();
        payload.setDate(toXml(departure.getDepartureDate()));
        payload.setTotalSeats(departure.getTotalSeats());
        payload.setRemainingSeats(departure.getRemainingSeats());
        return payload;
    }

    private static javax.xml.datatype.XMLGregorianCalendar toXml(LocalDate date) {
        try {
            return javax.xml.datatype.DatatypeFactory.newInstance()
                .newXMLGregorianCalendarDate(
                    date.getYear(), date.getMonthValue(), date.getDayOfMonth(),
                    javax.xml.datatype.DatatypeConstants.FIELD_UNDEFINED);
        } catch (javax.xml.datatype.DatatypeConfigurationException exception) {
            throw new IllegalStateException("Không dựng được kiểu ngày của XML", exception);
        }
    }

    private static String nullSafe(String value) {
        return value == null ? "" : value.trim();
    }

    // Slug không tồn tại là lỗi của bên gọi, nên trả SOAP Fault dạng CLIENT: đối tác biết là phải
    // sửa request chứ không phải thử lại.
    @SoapFault(faultCode = FaultCode.CLIENT)
    static class TourNotFoundException extends RuntimeException {
        TourNotFoundException(String message) {
            super(message);
        }
    }
}

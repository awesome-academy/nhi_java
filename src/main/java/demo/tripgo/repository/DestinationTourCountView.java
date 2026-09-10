package demo.tripgo.repository;

// Projection cho truy vấn đếm số tour theo điểm đến; Spring Data map theo tên alias.
public interface DestinationTourCountView {
    Long getId();

    String getName();

    String getSlug();

    long getTourCount();
}

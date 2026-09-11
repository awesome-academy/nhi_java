package demo.tripgo.repository;

// Projection cho truy vấn đếm số tour theo điểm đến; Spring Data map theo tên alias.
public interface DestinationTourCountView {
    String getSlug();

    String getName();

    String getImage();

    long getTourCount();
}

package lab.gabon.repository;

import java.util.List;
import java.util.Optional;
import lab.gabon.model.entity.Video;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;

public interface VideoRepository extends CrudRepository<Video, Long> {

  @Query("SELECT * FROM videos WHERE id = :id AND deleted_at IS NULL")
  Optional<Video> findActiveById(long id);

  @Query(
      """
      SELECT * FROM videos
      WHERE status = 4 AND deleted_at IS NULL
      ORDER BY created_at DESC
      LIMIT :limit OFFSET :offset
      """)
  List<Video> findApprovedVideos(int limit, int offset);

  @Query("SELECT COUNT(*) FROM videos WHERE status = 4 AND deleted_at IS NULL")
  long countApprovedVideos();

  @Query(
      """
      SELECT * FROM videos
      WHERE status = 4 AND deleted_at IS NULL
      ORDER BY like_count DESC, created_at DESC
      LIMIT :limit OFFSET :offset
      """)
  List<Video> findFeaturedVideos(int limit, int offset);

  @Query(
      """
      SELECT * FROM videos
      WHERE customer_id = :customerId AND deleted_at IS NULL
      ORDER BY created_at DESC
      LIMIT :limit OFFSET :offset
      """)
  List<Video> findMyVideos(long customerId, int limit, int offset);

  @Query("SELECT COUNT(*) FROM videos WHERE customer_id = :customerId AND deleted_at IS NULL")
  long countMyVideos(long customerId);

  @Query(
      """
      SELECT * FROM videos
      WHERE customer_id = :customerId AND status = 4 AND deleted_at IS NULL
      ORDER BY created_at DESC
      LIMIT :limit OFFSET :offset
      """)
  List<Video> findUserVideos(long customerId, int limit, int offset);

  @Query(
      """
      SELECT COUNT(*) FROM videos
      WHERE customer_id = :customerId AND status = 4 AND deleted_at IS NULL
      """)
  long countUserVideos(long customerId);

  @Modifying
  @Query(
      """
      UPDATE videos SET deleted_at = NOW(), updated_at = NOW()
      WHERE id = :id AND customer_id = :customerId AND deleted_at IS NULL
      """)
  int softDelete(long id, long customerId);

  @Modifying
  @Query("UPDATE videos SET total_clicks = total_clicks + 1 WHERE id = :id")
  void incrementTotalClicks(long id);

  @Modifying
  @Query("UPDATE videos SET valid_clicks = valid_clicks + 1 WHERE id = :id")
  void incrementValidClicks(long id);

  // -- Admin queries ----------------------------------------------------------

  @Query(
      """
      SELECT * FROM videos
      WHERE deleted_at IS NULL
          AND (:status IS NULL OR status = :status)
      ORDER BY created_at DESC
      LIMIT :limit OFFSET :offset
      """)
  List<Video> findVideosForReview(Short status, int limit, int offset);

  @Query(
      """
      SELECT COUNT(*) FROM videos
      WHERE deleted_at IS NULL
          AND (:status IS NULL OR status = :status)
      """)
  long countVideosForReview(Short status);

  @Modifying
  @Query(
      """
      UPDATE videos SET
          status = :status,
          reviewed_by = :adminId,
          reviewed_at = NOW(),
          review_notes = :notes,
          updated_at = NOW()
      WHERE id = :id AND deleted_at IS NULL
      """)
  int reviewVideo(long id, long adminId, short status, String notes);

  @Modifying
  @Query(
      "UPDATE videos SET deleted_at = NOW(), updated_at = NOW() WHERE id = :id AND deleted_at IS NULL")
  int adminDeleteVideo(long id);

  // -- Report queries ---------------------------------------------------------

  @Query(
      """
      SELECT DATE(created_at) AS report_date, COUNT(*) AS cnt
      FROM videos
      WHERE DATE(created_at) >= :startDate AND DATE(created_at) <= :endDate
      GROUP BY DATE(created_at)
      ORDER BY report_date
      """)
  List<Object[]> dailyVideoReport(String startDate, String endDate);

  @Query(
      """
      SELECT status AS sts, COUNT(*) AS cnt
      FROM videos
      WHERE deleted_at IS NULL
      GROUP BY status
      """)
  List<Object[]> videoSummaryReport();
}

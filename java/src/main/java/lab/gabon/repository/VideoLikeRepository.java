package lab.gabon.repository;

import lab.gabon.model.entity.VideoLike;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;

public interface VideoLikeRepository extends CrudRepository<VideoLike, Long> {

  @Modifying
  @Query(
      """
      WITH inserted AS (
          INSERT INTO video_likes (video_id, customer_id)
          VALUES (:videoId, :customerId)
          ON CONFLICT (video_id, customer_id) DO NOTHING
          RETURNING id
      )
      UPDATE videos SET like_count = like_count + 1, updated_at = NOW()
      WHERE id = :videoId AND EXISTS (SELECT 1 FROM inserted)
      """)
  int likeVideo(long videoId, long customerId);

  @Modifying
  @Query(
      """
      WITH deleted AS (
          DELETE FROM video_likes
          WHERE video_id = :videoId AND customer_id = :customerId
          RETURNING id
      )
      UPDATE videos SET like_count = GREATEST(like_count - 1, 0), updated_at = NOW()
      WHERE id = :videoId AND EXISTS (SELECT 1 FROM deleted)
      """)
  int unlikeVideo(long videoId, long customerId);

  @Query(
      """
      SELECT EXISTS(
          SELECT 1 FROM video_likes
          WHERE video_id = :videoId AND customer_id = :customerId
      )
      """)
  boolean existsByVideoIdAndCustomerId(long videoId, long customerId);
}

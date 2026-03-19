package lab.gabon.repository;

import java.util.List;
import lab.gabon.model.entity.UserFollow;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;

public interface UserFollowRepository extends CrudRepository<UserFollow, Long> {

  @Modifying
  @Query(
      """
      INSERT INTO user_follows (follower_id, followed_id)
      VALUES (:followerId, :followedId)
      ON CONFLICT (follower_id, followed_id) DO NOTHING
      """)
  int follow(long followerId, long followedId);

  @Modifying
  @Query("DELETE FROM user_follows WHERE follower_id = :followerId AND followed_id = :followedId")
  int unfollow(long followerId, long followedId);

  @Query(
      """
      SELECT EXISTS(
          SELECT 1 FROM user_follows
          WHERE follower_id = :followerId AND followed_id = :followedId
      )
      """)
  boolean existsByFollowerAndFollowed(long followerId, long followedId);

  @Query(
      """
      SELECT COUNT(*) FROM user_follows uf
      JOIN customers c ON c.id = uf.followed_id AND c.deleted_at IS NULL
      WHERE uf.follower_id = :userId
      """)
  long countFollowing(long userId);

  @Query(
      """
      SELECT COUNT(*) FROM user_follows uf
      JOIN customers c ON c.id = uf.follower_id AND c.deleted_at IS NULL
      WHERE uf.followed_id = :userId
      """)
  long countFollowers(long userId);

  @Query(
      """
      SELECT uf.* FROM user_follows uf
      JOIN customers c ON c.id = uf.followed_id AND c.deleted_at IS NULL
      WHERE uf.follower_id = :userId
      ORDER BY uf.created_at DESC
      LIMIT :limit OFFSET :offset
      """)
  List<UserFollow> findFollowing(long userId, int limit, int offset);

  @Query(
      """
      SELECT uf.* FROM user_follows uf
      JOIN customers c ON c.id = uf.follower_id AND c.deleted_at IS NULL
      WHERE uf.followed_id = :userId
      ORDER BY uf.created_at DESC
      LIMIT :limit OFFSET :offset
      """)
  List<UserFollow> findFollowers(long userId, int limit, int offset);
}

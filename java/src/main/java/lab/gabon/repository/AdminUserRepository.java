package lab.gabon.repository;

import java.util.List;
import java.util.Optional;
import lab.gabon.model.entity.AdminUser;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;

public interface AdminUserRepository extends CrudRepository<AdminUser, Long> {

  @Query("SELECT * FROM admin_users WHERE id = :id AND deleted_at IS NULL")
  Optional<AdminUser> findActiveById(long id);

  @Query(
      """
      SELECT * FROM admin_users
      WHERE LOWER(username) = LOWER(:username) AND deleted_at IS NULL
      """)
  Optional<AdminUser> findByUsername(String username);

  @Query(
      """
      SELECT * FROM admin_users
      WHERE deleted_at IS NULL
      ORDER BY id ASC
      LIMIT :limit OFFSET :offset
      """)
  List<AdminUser> findAllActive(int limit, int offset);

  @Query("SELECT COUNT(*) FROM admin_users WHERE deleted_at IS NULL")
  long countAllActive();

  @Modifying
  @Query(
      """
      UPDATE admin_users SET
          full_name = COALESCE(NULLIF(:fullName, ''), full_name),
          phone = COALESCE(NULLIF(:phone, ''), phone),
          avatar_url = COALESCE(NULLIF(:avatarUrl, ''), avatar_url),
          updated_at = NOW()
      WHERE id = :id AND deleted_at IS NULL
      """)
  int updateAdmin(long id, String fullName, String phone, String avatarUrl);

  @Modifying
  @Query(
      "UPDATE admin_users SET deleted_at = NOW(), updated_at = NOW()"
          + " WHERE id = :id AND deleted_at IS NULL")
  int softDelete(long id);

  @Modifying
  @Query(
      "UPDATE admin_users SET password_hash = :passwordHash, updated_at = NOW()"
          + " WHERE id = :id AND deleted_at IS NULL")
  void updatePassword(long id, String passwordHash);

  @Modifying
  @Query("UPDATE admin_users SET last_login_at = NOW(), updated_at = NOW() WHERE id = :id")
  void updateLastLogin(long id);
}

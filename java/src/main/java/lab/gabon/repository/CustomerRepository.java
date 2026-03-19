package lab.gabon.repository;

import java.util.List;
import java.util.Optional;
import lab.gabon.model.entity.Customer;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;

public interface CustomerRepository extends CrudRepository<Customer, Long> {

  @Query("SELECT * FROM customers WHERE id = :id AND deleted_at IS NULL")
  Optional<Customer> findActiveById(long id);

  @Query("SELECT * FROM customers WHERE LOWER(username) = LOWER(:username) AND deleted_at IS NULL")
  Optional<Customer> findByUsername(String username);

  @Modifying
  @Query("UPDATE customers SET last_login_at = NOW(), updated_at = NOW() WHERE id = :id")
  void updateLastLogin(long id);

  @Modifying
  @Query(
      "UPDATE customers SET password_hash = :passwordHash, updated_at = NOW()"
          + " WHERE id = :id AND deleted_at IS NULL")
  void updatePassword(long id, String passwordHash);

  @Modifying
  @Query(
      """
      UPDATE customers SET
          name = COALESCE(NULLIF(:name, ''), name),
          phone = COALESCE(NULLIF(:phone, ''), phone),
          email = COALESCE(NULLIF(:email, ''), email),
          signature = COALESCE(NULLIF(:signature, ''), signature),
          updated_at = NOW()
      WHERE id = :id AND deleted_at IS NULL
      """)
  void updateProfile(long id, String name, String phone, String email, String signature);

  @Modifying
  @Query(
      "UPDATE customers SET avatar_url = :avatarUrl, updated_at = NOW()"
          + " WHERE id = :id AND deleted_at IS NULL")
  void updateAvatarUrl(long id, String avatarUrl);

  @Modifying
  @Query(
      "UPDATE customers SET diamond_balance = diamond_balance + :amount, updated_at = NOW()"
          + " WHERE id = :id")
  void updateDiamondBalance(long id, long amount);

  // -- Admin queries ----------------------------------------------------------

  @Query(
      """
      SELECT * FROM customers
      WHERE deleted_at IS NULL
          AND (:search IS NULL OR (LOWER(username) LIKE LOWER(CONCAT('%', :search, '%'))
               OR LOWER(name) LIKE LOWER(CONCAT('%', :search, '%'))))
      ORDER BY id DESC
      LIMIT :limit OFFSET :offset
      """)
  List<Customer> searchCustomers(String search, int limit, int offset);

  @Query(
      """
      SELECT COUNT(*) FROM customers
      WHERE deleted_at IS NULL
          AND (:search IS NULL OR (LOWER(username) LIKE LOWER(CONCAT('%', :search, '%'))
               OR LOWER(name) LIKE LOWER(CONCAT('%', :search, '%'))))
      """)
  long countSearchCustomers(String search);
}

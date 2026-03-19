package lab.gabon.repository;

import java.util.List;
import java.util.Optional;
import lab.gabon.model.entity.TaskProgress;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;

public interface TaskProgressRepository extends CrudRepository<TaskProgress, Long> {

  @Query("SELECT * FROM task_progress WHERE id = :id AND customer_id = :customerId FOR UPDATE")
  Optional<TaskProgress> findByIdForUpdate(long id, long customerId);

  @Query(
      """
      SELECT * FROM task_progress
      WHERE customer_id = :customerId AND period_key = :periodKey
      ORDER BY task_id ASC
      """)
  List<TaskProgress> findByCustomerAndPeriod(long customerId, String periodKey);

  @Modifying
  @Query(
      """
      UPDATE task_progress SET
          current_count = current_count + 1,
          task_status = CASE
              WHEN current_count + 1 >= target_count THEN 2
              ELSE task_status
          END,
          completed_at = CASE
              WHEN current_count + 1 >= target_count AND completed_at IS NULL THEN NOW()
              ELSE completed_at
          END,
          updated_at = NOW()
      WHERE customer_id = :customerId
          AND task_id = :taskId
          AND period_key = :periodKey
          AND task_status = 1
      """)
  int incrementProgress(long customerId, long taskId, String periodKey);

  @Modifying
  @Query(
      "UPDATE task_progress SET task_status = 3, claimed_at = NOW(), updated_at = NOW()"
          + " WHERE id = :id")
  void claimReward(long id);

  @Modifying
  @Query(
      """
      INSERT INTO task_progress (customer_id, task_id, target_count, period_key, reward_diamonds,
          current_count, task_status, created_at, updated_at)
      VALUES (:customerId, :taskId, :targetCount, :periodKey, :rewardDiamonds,
          0, 1, NOW(), NOW())
      ON CONFLICT (customer_id, task_id, period_key) DO NOTHING
      """)
  void upsertProgress(
      long customerId, long taskId, int targetCount, String periodKey, int rewardDiamonds);

  // -- Report queries ---------------------------------------------------------

  @Query(
      """
      SELECT DATE(claimed_at) AS report_date, SUM(reward_diamonds) AS total
      FROM task_progress
      WHERE task_status = 3
          AND DATE(claimed_at) >= :startDate AND DATE(claimed_at) <= :endDate
      GROUP BY DATE(claimed_at)
      ORDER BY report_date
      """)
  List<Object[]> revenueReport(String startDate, String endDate);
}

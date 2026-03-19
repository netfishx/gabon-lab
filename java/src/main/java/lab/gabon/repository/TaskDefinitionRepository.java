package lab.gabon.repository;

import java.util.List;
import lab.gabon.model.entity.TaskDefinition;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;

public interface TaskDefinitionRepository extends CrudRepository<TaskDefinition, Long> {

  @Query(
      """
      SELECT * FROM task_definitions
      WHERE status = 1
          AND (start_time IS NULL OR start_time <= NOW())
          AND (end_time IS NULL OR end_time > NOW())
      ORDER BY display_order ASC, id ASC
      """)
  List<TaskDefinition> findActiveDefinitions();

  @Query(
      """
      SELECT id FROM task_definitions
      WHERE status = 1
          AND task_category = 1
          AND (start_time IS NULL OR start_time <= NOW())
          AND (end_time IS NULL OR end_time > NOW())
      """)
  List<Long> findWatchTaskIds();
}

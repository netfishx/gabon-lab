package lab.gabon.service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.IsoFields;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import lab.gabon.common.AppError;
import lab.gabon.common.AppException;
import lab.gabon.model.entity.TaskDefinition;
import lab.gabon.model.entity.TaskProgress;
import lab.gabon.model.response.TaskResponses.TaskItemResponse;
import lab.gabon.repository.CustomerRepository;
import lab.gabon.repository.TaskDefinitionRepository;
import lab.gabon.repository.TaskProgressRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaskService {

  private static final ZoneId SHANGHAI_ZONE = ZoneId.of("Asia/Shanghai");

  private static final short TASK_TYPE_DAILY = 1;
  private static final short TASK_TYPE_WEEKLY = 2;
  private static final short TASK_TYPE_MONTHLY = 3;

  private static final short STATUS_COMPLETED = 2;
  private static final short STATUS_CLAIMED = 3;

  private final TaskDefinitionRepository taskDefRepo;
  private final TaskProgressRepository taskProgressRepo;
  private final CustomerRepository customerRepo;

  public TaskService(
      TaskDefinitionRepository taskDefRepo,
      TaskProgressRepository taskProgressRepo,
      CustomerRepository customerRepo) {
    this.taskDefRepo = taskDefRepo;
    this.taskProgressRepo = taskProgressRepo;
    this.customerRepo = customerRepo;
  }

  public List<TaskItemResponse> listTasks(long customerId) {
    var now = Instant.now();
    var definitions = taskDefRepo.findActiveDefinitions();

    // Upsert progress for each definition with its matching period key
    for (var def : definitions) {
      var periodKey = generatePeriodKey(def.taskType(), now);
      taskProgressRepo.upsertProgress(
          customerId, def.id(), def.targetCount(), periodKey, def.rewardDiamonds());
    }

    // Collect progress rows grouped by period key
    var taskTypes = definitions.stream().map(TaskDefinition::taskType).distinct().toList();

    var progressByTaskId = new HashMap<Long, TaskProgress>();
    for (var tt : taskTypes) {
      var periodKey = generatePeriodKey(tt, now);
      var rows = taskProgressRepo.findByCustomerAndPeriod(customerId, periodKey);
      for (var row : rows) {
        progressByTaskId.put(row.taskId(), row);
      }
    }

    // Merge definitions with progress
    var items = new ArrayList<TaskItemResponse>(definitions.size());
    for (var def : definitions) {
      var periodKey = generatePeriodKey(def.taskType(), now);
      var progress = progressByTaskId.get(def.id());

      items.add(
          new TaskItemResponse(
              def.id(),
              def.taskCode(),
              def.taskName(),
              def.description(),
              def.targetCount(),
              progress != null ? progress.currentCount() : 0,
              def.rewardDiamonds(),
              progress != null ? progress.taskStatus() : 1,
              periodKey));
    }

    return items;
  }

  @Transactional
  public int claimReward(long customerId, long progressId) {
    var progress =
        taskProgressRepo
            .findByIdForUpdate(progressId, customerId)
            .orElseThrow(() -> new AppException(new AppError.TaskNotClaimable()));

    if (progress.taskStatus() != STATUS_COMPLETED) {
      throw new AppException(new AppError.TaskNotClaimable());
    }

    taskProgressRepo.claimReward(progressId);
    customerRepo.updateDiamondBalance(customerId, progress.rewardDiamonds());

    return progress.rewardDiamonds();
  }

  static String generatePeriodKey(short taskType, Instant instant) {
    ZonedDateTime zdt = instant.atZone(SHANGHAI_ZONE);
    return switch (taskType) {
      case TASK_TYPE_DAILY -> zdt.toLocalDate().toString();
      case TASK_TYPE_WEEKLY -> {
        int year = zdt.get(IsoFields.WEEK_BASED_YEAR);
        int week = zdt.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
        yield "%d-W%02d".formatted(year, week);
      }
      case TASK_TYPE_MONTHLY -> "%d-%02d".formatted(zdt.getYear(), zdt.getMonthValue());
      default -> throw new IllegalArgumentException("Unknown task type: " + taskType);
    };
  }
}

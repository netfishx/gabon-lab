package lab.gabon.controller.api;

import java.util.List;
import lab.gabon.common.ApiResponse;
import lab.gabon.model.response.TaskResponses.TaskItemResponse;
import lab.gabon.service.TaskService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tasks")
public class TaskController {

  private final TaskService taskService;

  public TaskController(TaskService taskService) {
    this.taskService = taskService;
  }

  @GetMapping
  public ApiResponse<List<TaskItemResponse>> listTasks(@RequestAttribute("userId") long userId) {
    var items = taskService.listTasks(userId);
    return ApiResponse.ok(items);
  }

  @PostMapping("/{progressId}/claim")
  public ApiResponse<Integer> claimReward(
      @RequestAttribute("userId") long userId, @PathVariable long progressId) {
    int diamonds = taskService.claimReward(userId, progressId);
    return ApiResponse.ok(diamonds);
  }
}

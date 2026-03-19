package lab.gabon.controller.api;

import lab.gabon.common.ApiResponse;
import lab.gabon.model.response.TaskResponses.SignInResponse;
import lab.gabon.service.ActivityService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/activity")
public class ActivityController {

  private final ActivityService activityService;

  public ActivityController(ActivityService activityService) {
    this.activityService = activityService;
  }

  @PostMapping("/sign-in")
  public ApiResponse<SignInResponse> signIn(@RequestAttribute("userId") long userId) {
    var result = activityService.signIn(userId);
    return ApiResponse.ok(result);
  }
}

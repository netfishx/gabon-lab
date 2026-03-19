package lab.gabon.model.response;

public final class TaskResponses {

  private TaskResponses() {}

  public record TaskItemResponse(
      long taskId,
      String taskCode,
      String taskName,
      String description,
      int targetCount,
      int currentCount,
      int rewardDiamonds,
      short status,
      String periodKey) {}

  public record SignInResponse(String periodKey, int rewardDiamonds) {}
}

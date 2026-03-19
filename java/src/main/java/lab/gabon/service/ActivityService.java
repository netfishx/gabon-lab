package lab.gabon.service;

import java.time.Instant;
import java.time.ZoneId;
import lab.gabon.common.AppError;
import lab.gabon.common.AppException;
import lab.gabon.model.entity.CustomerSignInRecord;
import lab.gabon.model.response.TaskResponses.SignInResponse;
import lab.gabon.repository.CustomerSignInRecordRepository;
import org.springframework.stereotype.Service;

@Service
public class ActivityService {

  private static final ZoneId SHANGHAI_ZONE = ZoneId.of("Asia/Shanghai");
  private static final short TASK_TYPE_DAILY = 1;
  private static final int SIGN_IN_REWARD = 10;

  private final CustomerSignInRecordRepository signInRepo;

  public ActivityService(CustomerSignInRecordRepository signInRepo) {
    this.signInRepo = signInRepo;
  }

  public SignInResponse signIn(long customerId) {
    var now = Instant.now();
    var periodKey = TaskService.generatePeriodKey(TASK_TYPE_DAILY, now);

    var existing = signInRepo.findByCustomerAndPeriodKey(customerId, periodKey);
    if (existing.isPresent()) {
      throw new AppException(new AppError.AlreadySignedIn());
    }

    var record = new CustomerSignInRecord(null, customerId, periodKey, SIGN_IN_REWARD, now);
    signInRepo.save(record);

    return new SignInResponse(periodKey, SIGN_IN_REWARD);
  }
}

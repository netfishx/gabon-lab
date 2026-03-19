package lab.gabon.service;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import lab.gabon.common.AppError;
import lab.gabon.common.AppException;
import lab.gabon.common.PageResponse;
import lab.gabon.model.entity.AdminUser;
import lab.gabon.model.entity.Customer;
import lab.gabon.model.entity.Video;
import lab.gabon.model.response.AdminResponses.AdminMeResponse;
import lab.gabon.model.response.AdminResponses.AdminTokenPairResponse;
import lab.gabon.model.response.AdminResponses.AdminUserResponse;
import lab.gabon.model.response.AdminResponses.AdminVideoResponse;
import lab.gabon.model.response.AdminResponses.CustomerListItemResponse;
import lab.gabon.model.response.AdminResponses.DailyVideoReportItem;
import lab.gabon.model.response.AdminResponses.RevenueReportItem;
import lab.gabon.model.response.AdminResponses.VideoSummaryItem;
import lab.gabon.repository.AdminUserRepository;
import lab.gabon.repository.CustomerRepository;
import lab.gabon.repository.TaskProgressRepository;
import lab.gabon.repository.VideoRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AdminService {

  private static final short ROLE_SUPERADMIN = 1;
  private static final short STATUS_ACTIVE = 1;

  private static final short STATUS_APPROVED = 4;
  private static final short STATUS_REJECTED = 5;

  private final AdminUserRepository adminUserRepo;
  private final CustomerRepository customerRepo;
  private final VideoRepository videoRepo;
  private final TaskProgressRepository taskProgressRepo;
  private final JwtService jwtService;
  private final TokenStore tokenStore;
  private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(10);

  public AdminService(
      AdminUserRepository adminUserRepo,
      CustomerRepository customerRepo,
      VideoRepository videoRepo,
      TaskProgressRepository taskProgressRepo,
      JwtService jwtService,
      TokenStore tokenStore) {
    this.adminUserRepo = adminUserRepo;
    this.customerRepo = customerRepo;
    this.videoRepo = videoRepo;
    this.taskProgressRepo = taskProgressRepo;
    this.jwtService = jwtService;
    this.tokenStore = tokenStore;
  }

  // -- Admin Auth ---------------------------------------------------------------

  public AdminTokenPairResponse adminLogin(String username, String password) {
    var admin =
        adminUserRepo
            .findByUsername(username)
            .orElseThrow(() -> new AppException(new AppError.InvalidCredentials()));

    if (admin.status() != STATUS_ACTIVE) {
      throw new AppException(new AppError.InvalidCredentials());
    }

    if (!encoder.matches(password, admin.passwordHash())) {
      throw new AppException(new AppError.InvalidCredentials());
    }

    adminUserRepo.updateLastLogin(admin.id());

    var roleName = admin.role() == ROLE_SUPERADMIN ? "superadmin" : "admin";
    var tokens = jwtService.generateAdminTokens(admin.id(), roleName, null);
    tokenStore.storeRefreshFamily(tokens.familyId(), admin.id(), tokens.refreshJti(), true);

    return new AdminTokenPairResponse(tokens.accessToken(), tokens.refreshToken());
  }

  public AdminTokenPairResponse adminRefresh(String refreshToken) {
    var claims = jwtService.verifyAdminRefresh(refreshToken);

    var newTokens =
        jwtService.generateAdminTokens(claims.userId(), claims.role(), claims.familyId());

    var casResult =
        tokenStore.rotateRefreshToken(claims.familyId(), claims.jti(), newTokens.refreshJti());

    return switch (casResult) {
      case TokenStore.CasResult.Success s ->
          new AdminTokenPairResponse(newTokens.accessToken(), newTokens.refreshToken());
      case TokenStore.CasResult.Missing m -> throw new AppException(new AppError.TokenInvalid());
      case TokenStore.CasResult.Conflict c -> throw new AppException(new AppError.TokenInvalid());
    };
  }

  public void adminLogout(String accessToken, String refreshToken) {
    var accessClaims = jwtService.verifyAdminAccess(accessToken);
    tokenStore.blacklistAccessToken(accessClaims.jti(), true);

    try {
      var refreshClaims = jwtService.verifyAdminRefresh(refreshToken);
      tokenStore.revokeFamily(refreshClaims.familyId());
    } catch (AppException e) {
      // Ignore errors if refresh token is expired/invalid
    }
  }

  public AdminMeResponse getAdminMe(long adminId) {
    var admin =
        adminUserRepo
            .findActiveById(adminId)
            .orElseThrow(() -> new AppException(new AppError.NotFound("admin not found")));

    return toMeResponse(admin);
  }

  // -- Admin User CRUD ----------------------------------------------------------

  public PageResponse<AdminUserResponse> listAdminUsers(int page, int pageSize) {
    var offset = (page - 1) * pageSize;
    var items =
        adminUserRepo.findAllActive(pageSize, offset).stream().map(this::toUserResponse).toList();
    var total = adminUserRepo.countAllActive();
    return new PageResponse<>(items, total, page, pageSize);
  }

  public AdminUserResponse getAdminUser(long id) {
    var admin =
        adminUserRepo
            .findActiveById(id)
            .orElseThrow(() -> new AppException(new AppError.NotFound("admin not found")));

    return toUserResponse(admin);
  }

  public AdminUserResponse createAdminUser(
      String username, String password, short role, String fullName, String phone) {
    adminUserRepo
        .findByUsername(username)
        .ifPresent(
            a -> {
              throw new AppException(new AppError.UsernameExists());
            });

    var hash = encoder.encode(password);
    var admin =
        new AdminUser(
            null,
            username,
            hash,
            role,
            fullName,
            phone,
            null,
            STATUS_ACTIVE,
            null,
            null,
            null,
            null);
    var saved = adminUserRepo.save(admin);

    return toUserResponse(saved);
  }

  public void updateAdminUser(long id, String fullName, String phone, Short role, Short status) {
    adminUserRepo
        .findActiveById(id)
        .orElseThrow(() -> new AppException(new AppError.NotFound("admin not found")));

    adminUserRepo.updateAdminFull(
        id,
        fullName,
        phone,
        role != null ? role : (short) -1,
        status != null ? status : (short) -1);
  }

  public void deleteAdminUser(long id) {
    var affected = adminUserRepo.softDelete(id);
    if (affected == 0) {
      throw new AppException(new AppError.NotFound("admin not found"));
    }
  }

  public void resetAdminPassword(long id, String newPassword) {
    adminUserRepo
        .findActiveById(id)
        .orElseThrow(() -> new AppException(new AppError.NotFound("admin not found")));

    adminUserRepo.updatePassword(id, encoder.encode(newPassword));
  }

  // -- Customer Management -------------------------------------------------------

  public PageResponse<CustomerListItemResponse> listCustomers(
      int page, int pageSize, String search) {
    int offset = (page - 1) * pageSize;
    var items =
        customerRepo.searchCustomers(search, pageSize, offset).stream()
            .map(AdminService::toCustomerItem)
            .toList();
    long total = customerRepo.countSearchCustomers(search);
    return new PageResponse<>(items, total, page, pageSize);
  }

  public void resetCustomerPassword(long customerId, String newPassword) {
    customerRepo
        .findActiveById(customerId)
        .orElseThrow(() -> new AppException(new AppError.NotFound("customer not found")));

    customerRepo.updatePassword(customerId, encoder.encode(newPassword));
  }

  // -- Video Review -------------------------------------------------------------

  public PageResponse<AdminVideoResponse> listVideosForReview(
      int page, int pageSize, Short status) {
    int offset = (page - 1) * pageSize;
    var items =
        videoRepo.findVideosForReview(status, pageSize, offset).stream()
            .map(AdminService::toAdminVideo)
            .toList();
    long total = videoRepo.countVideosForReview(status);
    return new PageResponse<>(items, total, page, pageSize);
  }

  public AdminVideoResponse getVideoForReview(long videoId) {
    var video =
        videoRepo
            .findActiveById(videoId)
            .orElseThrow(() -> new AppException(new AppError.VideoNotFound()));
    return toAdminVideo(video);
  }

  public void reviewVideo(long videoId, long adminId, short status, String notes) {
    if (status != STATUS_APPROVED && status != STATUS_REJECTED) {
      throw new AppException(
          new AppError.BadRequest("status must be 4 (approved) or 5 (rejected)"));
    }

    int rows = videoRepo.reviewVideo(videoId, adminId, status, notes);
    if (rows == 0) {
      throw new AppException(new AppError.VideoNotFound());
    }
  }

  public void adminDeleteVideo(long videoId) {
    int rows = videoRepo.adminDeleteVideo(videoId);
    if (rows == 0) {
      throw new AppException(new AppError.VideoNotFound());
    }
  }

  // -- Reports ------------------------------------------------------------------

  public List<RevenueReportItem> getRevenueReport(String startDate, String endDate) {
    validateDateRange(startDate, endDate);
    return taskProgressRepo.revenueReport(startDate, endDate).stream()
        .map(
            row -> {
              var date = row[0] != null ? row[0].toString() : "";
              var total = row[1] != null ? ((Number) row[1]).longValue() : 0L;
              return new RevenueReportItem(date, total);
            })
        .toList();
  }

  public List<DailyVideoReportItem> getDailyVideoReport(String startDate, String endDate) {
    validateDateRange(startDate, endDate);
    return videoRepo.dailyVideoReport(startDate, endDate).stream()
        .map(
            row -> {
              var date = row[0] != null ? row[0].toString() : "";
              var count = row[1] != null ? ((Number) row[1]).longValue() : 0L;
              return new DailyVideoReportItem(date, count);
            })
        .toList();
  }

  public List<VideoSummaryItem> getVideoSummaryReport() {
    return videoRepo.videoSummaryReport().stream()
        .map(
            row -> {
              var status = row[0] != null ? ((Number) row[0]).shortValue() : (short) 0;
              var count = row[1] != null ? ((Number) row[1]).longValue() : 0L;
              return new VideoSummaryItem(status, count);
            })
        .toList();
  }

  // -- Mappers ------------------------------------------------------------------

  private static CustomerListItemResponse toCustomerItem(Customer c) {
    return new CustomerListItemResponse(
        c.id(),
        c.username(),
        c.name(),
        c.phone(),
        c.email(),
        c.isVip(),
        c.diamondBalance(),
        c.createdAt());
  }

  private static AdminVideoResponse toAdminVideo(Video v) {
    return new AdminVideoResponse(
        v.id(),
        v.customerId(),
        v.title(),
        v.fileUrl(),
        v.status(),
        v.reviewNotes(),
        v.reviewedBy(),
        v.reviewedAt(),
        v.totalClicks(),
        v.likeCount(),
        v.createdAt());
  }

  private static AdminMeResponse toMeResponse(AdminUser a) {
    return new AdminMeResponse(
        a.id(), a.username(), a.role(), a.fullName(), a.phone(), a.avatarUrl(), a.status());
  }

  private AdminUserResponse toUserResponse(AdminUser a) {
    return new AdminUserResponse(
        a.id(),
        a.username(),
        a.role(),
        a.fullName(),
        a.phone(),
        a.avatarUrl(),
        a.status(),
        a.lastLoginAt(),
        a.createdAt());
  }

  // -- Helpers ------------------------------------------------------------------

  private static void validateDateRange(String startDate, String endDate) {
    var start = parseDate(startDate, "startDate");
    var end = parseDate(endDate, "endDate");
    if (start.isAfter(end)) {
      throw new AppException(new AppError.BadRequest("startDate must not be after endDate"));
    }
  }

  private static LocalDate parseDate(String value, String paramName) {
    try {
      return LocalDate.parse(value);
    } catch (DateTimeParseException e) {
      throw new AppException(
          new AppError.BadRequest("invalid " + paramName + " format, expected yyyy-MM-dd"));
    }
  }
}

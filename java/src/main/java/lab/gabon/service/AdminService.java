package lab.gabon.service;

import lab.gabon.common.AppError;
import lab.gabon.common.AppException;
import lab.gabon.common.PageResponse;
import lab.gabon.model.entity.AdminUser;
import lab.gabon.model.response.AdminResponses.AdminMeResponse;
import lab.gabon.model.response.AdminResponses.AdminTokenPairResponse;
import lab.gabon.model.response.AdminResponses.AdminUserResponse;
import lab.gabon.repository.AdminUserRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AdminService {

  private static final short ROLE_SUPERADMIN = 1;
  private static final short STATUS_ACTIVE = 1;

  private final AdminUserRepository adminUserRepo;
  private final JwtService jwtService;
  private final TokenStore tokenStore;
  private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(10);

  public AdminService(
      AdminUserRepository adminUserRepo, JwtService jwtService, TokenStore tokenStore) {
    this.adminUserRepo = adminUserRepo;
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

  // -- Mappers ------------------------------------------------------------------

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
}

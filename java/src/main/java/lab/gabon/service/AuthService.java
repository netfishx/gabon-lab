package lab.gabon.service;

import lab.gabon.common.AppError;
import lab.gabon.common.AppException;
import lab.gabon.model.entity.Customer;
import lab.gabon.model.response.AuthResponses.CustomerMeResponse;
import lab.gabon.model.response.AuthResponses.TokenPairResponse;
import lab.gabon.repository.CustomerRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

  private final CustomerRepository customerRepo;
  private final JwtService jwtService;
  private final TokenStore tokenStore;
  private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(10);

  public AuthService(
      CustomerRepository customerRepo, JwtService jwtService, TokenStore tokenStore) {
    this.customerRepo = customerRepo;
    this.jwtService = jwtService;
    this.tokenStore = tokenStore;
  }

  public TokenPairResponse register(String username, String password) {
    customerRepo
        .findByUsername(username)
        .ifPresent(
            c -> {
              throw new AppException(new AppError.UsernameExists());
            });

    var hash = encoder.encode(password);
    var customer =
        new Customer(
            null, username, hash, null, null, null, null, null, false, 0, null, null, null, null,
            null);
    var saved = customerRepo.save(customer);

    var tokens = jwtService.generateCustomerTokens(saved.id(), null);
    tokenStore.storeRefreshFamily(tokens.familyId(), saved.id(), tokens.refreshJti(), false);

    return new TokenPairResponse(tokens.accessToken(), tokens.refreshToken());
  }

  public TokenPairResponse login(String username, String password) {
    var customer =
        customerRepo
            .findByUsername(username)
            .orElseThrow(() -> new AppException(new AppError.InvalidCredentials()));

    if (!encoder.matches(password, customer.passwordHash())) {
      throw new AppException(new AppError.InvalidCredentials());
    }

    customerRepo.updateLastLogin(customer.id());

    var tokens = jwtService.generateCustomerTokens(customer.id(), null);
    tokenStore.storeRefreshFamily(tokens.familyId(), customer.id(), tokens.refreshJti(), false);

    return new TokenPairResponse(tokens.accessToken(), tokens.refreshToken());
  }

  public TokenPairResponse refresh(String refreshToken) {
    var claims = jwtService.verifyCustomerRefresh(refreshToken);

    var newTokens = jwtService.generateCustomerTokens(claims.userId(), claims.familyId());

    var casResult =
        tokenStore.rotateRefreshToken(claims.familyId(), claims.jti(), newTokens.refreshJti());

    return switch (casResult) {
      case TokenStore.CasResult.Success s ->
          new TokenPairResponse(newTokens.accessToken(), newTokens.refreshToken());
      case TokenStore.CasResult.Missing m -> throw new AppException(new AppError.TokenInvalid());
      case TokenStore.CasResult.Conflict c -> throw new AppException(new AppError.TokenInvalid());
    };
  }

  public void logout(String accessToken, String refreshToken) {
    var accessClaims = jwtService.verifyCustomerAccess(accessToken);
    tokenStore.blacklistAccessToken(accessClaims.jti(), false);

    try {
      var refreshClaims = jwtService.verifyCustomerRefresh(refreshToken);
      tokenStore.revokeFamily(refreshClaims.familyId());
    } catch (AppException e) {
      // Ignore errors if refresh token is expired/invalid
    }
  }

  public CustomerMeResponse getMe(long customerId) {
    var customer =
        customerRepo
            .findActiveById(customerId)
            .orElseThrow(() -> new AppException(new AppError.NotFound("customer not found")));

    return toMeResponse(customer);
  }

  public void updatePassword(long customerId, String currentPassword, String newPassword) {
    var customer =
        customerRepo
            .findActiveById(customerId)
            .orElseThrow(() -> new AppException(new AppError.NotFound("customer not found")));

    if (!encoder.matches(currentPassword, customer.passwordHash())) {
      throw new AppException(new AppError.PasswordMismatch());
    }

    customerRepo.updatePassword(customerId, encoder.encode(newPassword));
  }

  private static CustomerMeResponse toMeResponse(Customer c) {
    return new CustomerMeResponse(
        c.id(),
        c.username(),
        c.name(),
        c.phone(),
        c.email(),
        c.avatarUrl(),
        c.signature(),
        c.isVip(),
        c.diamondBalance());
  }
}

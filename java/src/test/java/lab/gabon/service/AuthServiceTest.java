package lab.gabon.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Optional;
import lab.gabon.common.AppError;
import lab.gabon.common.AppException;
import lab.gabon.model.entity.Customer;
import lab.gabon.model.response.AuthResponses.TokenPairResponse;
import lab.gabon.repository.CustomerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

  @Mock private CustomerRepository customerRepo;
  @Mock private JwtService jwtService;
  @Mock private TokenStore tokenStore;

  private AuthService authService;

  private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder(10);

  @BeforeEach
  void setUp() {
    authService = new AuthService(customerRepo, jwtService, tokenStore);
  }

  @Test
  void register_success() {
    when(customerRepo.findByUsername("testuser")).thenReturn(Optional.empty());
    when(customerRepo.save(any(Customer.class)))
        .thenAnswer(
            inv -> {
              Customer c = inv.getArgument(0);
              return new Customer(
                  1L,
                  c.username(),
                  c.passwordHash(),
                  null,
                  null,
                  null,
                  null,
                  null,
                  false,
                  0,
                  null,
                  null,
                  null,
                  null,
                  null);
            });
    when(jwtService.generateCustomerTokens(eq(1L), isNull()))
        .thenReturn(new JwtService.TokenPair("access-tok", "refresh-tok", "fam-1", "ajti", "rjti"));

    TokenPairResponse result = authService.register("testuser", "password123");

    assertEquals("access-tok", result.accessToken());
    assertEquals("refresh-tok", result.refreshToken());
    verify(tokenStore).storeRefreshFamily("fam-1", 1L, "rjti", false);
  }

  @Test
  void register_duplicateUsername_throwsUsernameExists() {
    var existing =
        new Customer(
            1L, "taken", "hash", null, null, null, null, null, false, 0, null, null, null, null,
            null);
    when(customerRepo.findByUsername("taken")).thenReturn(Optional.of(existing));

    var ex = assertThrows(AppException.class, () -> authService.register("taken", "password123"));

    assertInstanceOf(AppError.UsernameExists.class, ex.getError());
    verify(customerRepo, never()).save(any());
  }

  @Test
  void login_wrongPassword_throwsInvalidCredentials() {
    var hash = ENCODER.encode("correctpass");
    var customer =
        new Customer(
            1L, "user1", hash, null, null, null, null, null, false, 0, null, null, null, null,
            null);
    when(customerRepo.findByUsername("user1")).thenReturn(Optional.of(customer));

    var ex = assertThrows(AppException.class, () -> authService.login("user1", "wrongpass"));

    assertInstanceOf(AppError.InvalidCredentials.class, ex.getError());
  }

  @Test
  void login_userNotFound_throwsInvalidCredentials() {
    when(customerRepo.findByUsername("noone")).thenReturn(Optional.empty());

    var ex = assertThrows(AppException.class, () -> authService.login("noone", "password123"));

    assertInstanceOf(AppError.InvalidCredentials.class, ex.getError());
  }
}

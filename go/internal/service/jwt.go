package service

import (
	"fmt"
	"time"

	"github.com/golang-jwt/jwt/v5"
	"github.com/google/uuid"

	"gabon-go/internal/config"
)

type TokenPair struct {
	AccessToken  string
	RefreshToken string
	FamilyID     string
	AccessExp    time.Time
	RefreshExp   time.Time
}

type TokenClaims struct {
	UserID    int64
	JTI       string
	FamilyID  string
	TokenType string // "access" or "refresh"
	Issuer    string
	Audience  string
	Role      string
	ExpiresAt time.Time
}

type customClaims struct {
	jwt.RegisteredClaims
	TokenType string `json:"token_type"`
	FamilyID  string `json:"family_id"`
	Role      string `json:"role,omitempty"`
}

type JWTService struct {
	cfg *config.JWTConfig
}

func NewJWTService(cfg *config.JWTConfig) *JWTService {
	return &JWTService{cfg: cfg}
}

func (s *JWTService) GenerateCustomerTokens(customerID int64) (*TokenPair, error) {
	return s.generatePair(
		customerID, "", uuid.New().String(),
		"gabon-service", "customer",
		s.cfg.CustomerSecret,
		s.cfg.CustomerAccessTTL, s.cfg.CustomerRefreshTTL,
	)
}

func (s *JWTService) GenerateAdminTokens(adminID int64, role string) (*TokenPair, error) {
	return s.generatePair(
		adminID, role, uuid.New().String(),
		"gabon-admin", "admin",
		s.cfg.AdminSecret,
		s.cfg.AdminAccessTTL, s.cfg.AdminRefreshTTL,
	)
}

func (s *JWTService) RefreshCustomerTokens(customerID int64, familyID string) (*TokenPair, error) {
	return s.generatePair(
		customerID, "", familyID,
		"gabon-service", "customer",
		s.cfg.CustomerSecret,
		s.cfg.CustomerAccessTTL, s.cfg.CustomerRefreshTTL,
	)
}

func (s *JWTService) RefreshAdminTokens(adminID int64, role, familyID string) (*TokenPair, error) {
	return s.generatePair(
		adminID, role, familyID,
		"gabon-admin", "admin",
		s.cfg.AdminSecret,
		s.cfg.AdminAccessTTL, s.cfg.AdminRefreshTTL,
	)
}

func (s *JWTService) ParseCustomerToken(tokenStr string) (*TokenClaims, error) {
	return s.parseToken(tokenStr, s.cfg.CustomerSecret, "gabon-service", "customer")
}

func (s *JWTService) ParseAdminToken(tokenStr string) (*TokenClaims, error) {
	return s.parseToken(tokenStr, s.cfg.AdminSecret, "gabon-admin", "admin")
}

func (s *JWTService) generatePair(
	userID int64, role, familyID, iss, aud, secret string,
	accessTTL, refreshTTL time.Duration,
) (*TokenPair, error) {
	now := time.Now()
	accessExp := now.Add(accessTTL)
	refreshExp := now.Add(refreshTTL)

	accessToken, err := s.signToken(userID, role, familyID, iss, aud, secret, "access", now, accessExp)
	if err != nil {
		return nil, fmt.Errorf("sign access token: %w", err)
	}

	refreshToken, err := s.signToken(userID, role, familyID, iss, aud, secret, "refresh", now, refreshExp)
	if err != nil {
		return nil, fmt.Errorf("sign refresh token: %w", err)
	}

	return &TokenPair{
		AccessToken:  accessToken,
		RefreshToken: refreshToken,
		FamilyID:     familyID,
		AccessExp:    accessExp,
		RefreshExp:   refreshExp,
	}, nil
}

func (s *JWTService) signToken(
	userID int64, role, familyID, iss, aud, secret, tokenType string,
	now, exp time.Time,
) (string, error) {
	claims := customClaims{
		RegisteredClaims: jwt.RegisteredClaims{
			ID:        uuid.New().String(),
			Issuer:    iss,
			Audience:  jwt.ClaimStrings{aud},
			Subject:   fmt.Sprintf("%d", userID),
			ExpiresAt: jwt.NewNumericDate(exp),
			IssuedAt:  jwt.NewNumericDate(now),
		},
		TokenType: tokenType,
		FamilyID:  familyID,
		Role:      role,
	}

	token := jwt.NewWithClaims(jwt.SigningMethodHS256, claims)
	token.Header["kid"] = s.cfg.CurrentKID
	return token.SignedString([]byte(secret))
}

func (s *JWTService) parseToken(tokenStr, secret, expectedIss, expectedAud string) (*TokenClaims, error) {
	token, err := jwt.ParseWithClaims(tokenStr, &customClaims{}, func(t *jwt.Token) (any, error) {
		if _, ok := t.Method.(*jwt.SigningMethodHMAC); !ok {
			return nil, fmt.Errorf("unexpected signing method: %v", t.Header["alg"])
		}
		return []byte(secret), nil
	},
		jwt.WithIssuer(expectedIss),
		jwt.WithAudience(expectedAud),
		jwt.WithExpirationRequired(),
	)
	if err != nil {
		return nil, err
	}

	cc, ok := token.Claims.(*customClaims)
	if !ok || !token.Valid {
		return nil, fmt.Errorf("invalid token claims")
	}

	var userID int64
	_, _ = fmt.Sscanf(cc.Subject, "%d", &userID)

	return &TokenClaims{
		UserID:    userID,
		JTI:       cc.ID,
		FamilyID:  cc.FamilyID,
		TokenType: cc.TokenType,
		Issuer:    cc.Issuer,
		Audience:  expectedAud,
		Role:      cc.Role,
		ExpiresAt: cc.ExpiresAt.Time,
	}, nil
}

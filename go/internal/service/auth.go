package service

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"time"

	"github.com/jackc/pgx/v5/pgconn"
	"github.com/redis/go-redis/v9"
	"golang.org/x/crypto/bcrypt"

	"gabon-go/internal/model"
	"gabon-go/internal/repository"
)

type AuthRepo interface {
	GetCustomerByUsername(ctx context.Context, username string) (repository.Customer, error)
	GetCustomerByID(ctx context.Context, id int64) (repository.Customer, error)
	CreateCustomer(ctx context.Context, arg repository.CreateCustomerParams) (repository.Customer, error)
	UpdateCustomerLastLogin(ctx context.Context, id int64) error
	UpdateCustomerPassword(ctx context.Context, arg repository.UpdateCustomerPasswordParams) error
}

type TokenStore interface {
	SetBlacklist(ctx context.Context, jti string, ttl time.Duration) error
	IsBlacklisted(ctx context.Context, jti string) (bool, error)
	SetFamily(ctx context.Context, familyID string, customerID int64, currentJTI string, ttl time.Duration) error
	CASFamily(ctx context.Context, familyID, expectedJTI, newJTI string) (int64, error)
	DeleteFamily(ctx context.Context, familyID string) error
}

type AuthService struct {
	repo       AuthRepo
	tokens     TokenStore
	jwt        *JWTService
	refreshTTL time.Duration
}

func NewAuthService(repo AuthRepo, tokens TokenStore, jwt *JWTService, refreshTTL time.Duration) *AuthService {
	return &AuthService{
		repo:       repo,
		tokens:     tokens,
		jwt:        jwt,
		refreshTTL: refreshTTL,
	}
}

func (s *AuthService) Register(ctx context.Context, username, password string) (*AuthResponse, error) {
	hash, err := bcrypt.GenerateFromPassword([]byte(password), bcrypt.DefaultCost)
	if err != nil {
		return nil, model.WrapError(model.ErrInternal, "failed to hash password", err)
	}

	customer, err := s.repo.CreateCustomer(ctx, repository.CreateCustomerParams{
		Username:     username,
		PasswordHash: string(hash),
	})
	if err != nil {
		if isDuplicateKey(err) {
			return nil, model.NewAppError(model.ErrUsernameExists, "username already exists")
		}
		return nil, model.WrapError(model.ErrInternal, "failed to create customer", err)
	}

	pair, err := s.jwt.GenerateCustomerTokens(customer.ID)
	if err != nil {
		return nil, model.WrapError(model.ErrInternal, "failed to generate tokens", err)
	}

	refreshClaims, _ := s.jwt.ParseCustomerToken(pair.RefreshToken)
	if err := s.tokens.SetFamily(ctx, pair.FamilyID, customer.ID, refreshClaims.JTI, s.refreshTTL); err != nil {
		return nil, model.WrapError(model.ErrInternal, "failed to store token family", err)
	}

	return &AuthResponse{
		AccessToken:  pair.AccessToken,
		RefreshToken: pair.RefreshToken,
	}, nil
}

func (s *AuthService) Login(ctx context.Context, username, password string) (*AuthResponse, error) {
	customer, err := s.repo.GetCustomerByUsername(ctx, username)
	if err != nil {
		return nil, model.NewAppError(model.ErrInvalidCredentials, "invalid username or password")
	}

	if err := bcrypt.CompareHashAndPassword([]byte(customer.PasswordHash), []byte(password)); err != nil {
		return nil, model.NewAppError(model.ErrInvalidCredentials, "invalid username or password")
	}

	pair, err := s.jwt.GenerateCustomerTokens(customer.ID)
	if err != nil {
		return nil, model.WrapError(model.ErrInternal, "failed to generate tokens", err)
	}

	refreshClaims, _ := s.jwt.ParseCustomerToken(pair.RefreshToken)
	if err := s.tokens.SetFamily(ctx, pair.FamilyID, customer.ID, refreshClaims.JTI, s.refreshTTL); err != nil {
		return nil, model.WrapError(model.ErrInternal, "failed to store token family", err)
	}

	_ = s.repo.UpdateCustomerLastLogin(ctx, customer.ID)

	return &AuthResponse{
		AccessToken:  pair.AccessToken,
		RefreshToken: pair.RefreshToken,
	}, nil
}

func (s *AuthService) Logout(ctx context.Context, claims *TokenClaims) error {
	ttl := time.Until(claims.ExpiresAt)
	if ttl > 0 {
		if err := s.tokens.SetBlacklist(ctx, claims.JTI, ttl); err != nil {
			return model.WrapError(model.ErrInternal, "failed to blacklist token", err)
		}
	}

	if err := s.tokens.DeleteFamily(ctx, claims.FamilyID); err != nil {
		return model.WrapError(model.ErrInternal, "failed to delete token family", err)
	}

	return nil
}

func (s *AuthService) Refresh(ctx context.Context, refreshToken string) (*AuthResponse, error) {
	claims, err := s.jwt.ParseCustomerToken(refreshToken)
	if err != nil {
		return nil, model.NewAppError(model.ErrTokenInvalid, "invalid refresh token")
	}

	if claims.TokenType != "refresh" {
		return nil, model.NewAppError(model.ErrTokenInvalid, "not a refresh token")
	}

	newPair, err := s.jwt.RefreshCustomerTokens(claims.UserID, claims.FamilyID)
	if err != nil {
		return nil, model.WrapError(model.ErrInternal, "failed to generate tokens", err)
	}

	newRefreshClaims, _ := s.jwt.ParseCustomerToken(newPair.RefreshToken)

	result, err := s.tokens.CASFamily(ctx, claims.FamilyID, claims.JTI, newRefreshClaims.JTI)
	if err != nil {
		return nil, model.WrapError(model.ErrInternal, "failed to rotate token", err)
	}

	switch result {
	case 0: // CAS success
		return &AuthResponse{
			AccessToken:  newPair.AccessToken,
			RefreshToken: newPair.RefreshToken,
		}, nil
	case -1: // family not found
		return nil, model.NewAppError(model.ErrTokenInvalid, "token family expired or revoked")
	case -2: // replay detected
		return nil, model.NewAppError(model.ErrTokenInvalid, "token reuse detected, family revoked")
	default:
		return nil, model.NewAppError(model.ErrInternal, "unexpected token rotation result")
	}
}

func (s *AuthService) GetMe(ctx context.Context, customerID int64) (*CustomerInfo, error) {
	customer, err := s.repo.GetCustomerByID(ctx, customerID)
	if err != nil {
		return nil, model.NewAppError(model.ErrNotFound, "customer not found")
	}

	return &CustomerInfo{
		ID:        customer.ID,
		Username:  customer.Username,
		Name:      customer.Name.String,
		Phone:     customer.Phone.String,
		IsVip:     customer.IsVip,
		AvatarURL: customer.AvatarUrl.String,
	}, nil
}

func (s *AuthService) ChangePassword(ctx context.Context, customerID int64, oldPwd, newPwd string) error {
	customer, err := s.repo.GetCustomerByID(ctx, customerID)
	if err != nil {
		return model.NewAppError(model.ErrNotFound, "customer not found")
	}

	if err := bcrypt.CompareHashAndPassword([]byte(customer.PasswordHash), []byte(oldPwd)); err != nil {
		return model.NewAppError(model.ErrPasswordMismatch, "old password is incorrect")
	}

	hash, err := bcrypt.GenerateFromPassword([]byte(newPwd), bcrypt.DefaultCost)
	if err != nil {
		return model.WrapError(model.ErrInternal, "failed to hash password", err)
	}

	return s.repo.UpdateCustomerPassword(ctx, repository.UpdateCustomerPasswordParams{
		PasswordHash: string(hash),
		ID:           customerID,
	})
}

type AuthResponse struct {
	AccessToken  string `json:"access_token"`
	RefreshToken string `json:"refresh_token"`
}

type CustomerInfo struct {
	ID        int64  `json:"id"`
	Username  string `json:"username"`
	Name      string `json:"name"`
	Phone     string `json:"phone"`
	IsVip     bool   `json:"is_vip"`
	AvatarURL string `json:"avatar_url"`
}

func isDuplicateKey(err error) bool {
	var pgErr *pgconn.PgError
	if errors.As(err, &pgErr) {
		return pgErr.Code == "23505" // unique_violation
	}
	return false
}

// RedisTokenStore implements TokenStore using Redis.
type RedisTokenStore struct {
	rdb *redis.Client
}

func NewRedisTokenStore(rdb *redis.Client) *RedisTokenStore {
	return &RedisTokenStore{rdb: rdb}
}

type familyData struct {
	CurrentJTI string `json:"current_jti"`
	CustomerID int64  `json:"customer_id"`
}

func (s *RedisTokenStore) SetBlacklist(ctx context.Context, jti string, ttl time.Duration) error {
	return s.rdb.Set(ctx, "token:blacklist:"+jti, "1", ttl).Err()
}

func (s *RedisTokenStore) IsBlacklisted(ctx context.Context, jti string) (bool, error) {
	n, err := s.rdb.Exists(ctx, "token:blacklist:"+jti).Result()
	if err != nil {
		return false, err
	}
	return n > 0, nil
}

func (s *RedisTokenStore) SetFamily(ctx context.Context, familyID string, customerID int64, currentJTI string, ttl time.Duration) error {
	data, err := json.Marshal(familyData{CurrentJTI: currentJTI, CustomerID: customerID})
	if err != nil {
		return err
	}
	return s.rdb.Set(ctx, "token:family:"+familyID, data, ttl).Err()
}

// CASFamily atomically compares-and-swaps the current JTI in a token family.
// Returns: 0 = success, -1 = family not found, -2 = replay attack (family deleted).
func (s *RedisTokenStore) CASFamily(ctx context.Context, familyID, expectedJTI, newJTI string) (int64, error) {
	const luaScript = `
local current = redis.call("GET", KEYS[1])
if not current then return -1 end
local data = cjson.decode(current)
if data.current_jti ~= ARGV[1] then
    redis.call("DEL", KEYS[1])
    return -2
end
data.current_jti = ARGV[2]
redis.call("SET", KEYS[1], cjson.encode(data), "KEEPTTL")
return 0
`
	key := "token:family:" + familyID
	result, err := s.rdb.Eval(ctx, luaScript, []string{key}, expectedJTI, newJTI).Int64()
	if err != nil {
		return 0, fmt.Errorf("CAS lua script: %w", err)
	}
	return result, nil
}

func (s *RedisTokenStore) DeleteFamily(ctx context.Context, familyID string) error {
	return s.rdb.Del(ctx, "token:family:"+familyID).Err()
}

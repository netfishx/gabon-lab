package service

import (
	"context"
	"time"

	"github.com/jackc/pgx/v5/pgtype"
	"golang.org/x/crypto/bcrypt"

	"gabon-go/internal/model"
	"gabon-go/internal/repository"
)

type AdminRepo interface {
	GetAdminByUsername(ctx context.Context, username string) (repository.AdminUser, error)
	GetAdminByID(ctx context.Context, id int64) (repository.AdminUser, error)
	CreateAdmin(ctx context.Context, arg repository.CreateAdminParams) (repository.AdminUser, error)
	UpdateAdmin(ctx context.Context, arg repository.UpdateAdminParams) (repository.AdminUser, error)
	UpdateAdminPassword(ctx context.Context, arg repository.UpdateAdminPasswordParams) error
	UpdateAdminLastLogin(ctx context.Context, id int64) error
	SoftDeleteAdmin(ctx context.Context, id int64) error
	ListAdmins(ctx context.Context, arg repository.ListAdminsParams) ([]repository.ListAdminsRow, error)
	CountAdmins(ctx context.Context, arg repository.CountAdminsParams) (int64, error)
	ListCustomers(ctx context.Context, arg repository.ListCustomersParams) ([]repository.ListCustomersRow, error)
	CountCustomers(ctx context.Context, arg repository.CountCustomersParams) (int64, error)
	ResetCustomerPassword(ctx context.Context, arg repository.ResetCustomerPasswordParams) error
	AdminListVideos(ctx context.Context, arg repository.AdminListVideosParams) ([]repository.AdminListVideosRow, error)
	AdminCountVideos(ctx context.Context, arg repository.AdminCountVideosParams) (int64, error)
	AdminGetVideoDetail(ctx context.Context, id int64) (repository.AdminGetVideoDetailRow, error)
	ReviewVideo(ctx context.Context, arg repository.ReviewVideoParams) error
	AdminDeleteVideo(ctx context.Context, id int64) error
}

type AdminService struct {
	repo       AdminRepo
	jwt        *JWTService
	tokenStore TokenStore
	refreshTTL time.Duration
}

func NewAdminService(repo AdminRepo, tokenStore TokenStore, jwt *JWTService, refreshTTL time.Duration) *AdminService {
	return &AdminService{
		repo:       repo,
		jwt:        jwt,
		tokenStore: tokenStore,
		refreshTTL: refreshTTL,
	}
}

// --- Auth ---

type AdminLoginRequest struct {
	Username string `json:"username"`
	Password string `json:"password"`
}

func (s *AdminService) Login(ctx context.Context, req *AdminLoginRequest) (*AuthResponse, error) {
	admin, err := s.repo.GetAdminByUsername(ctx, req.Username)
	if err != nil {
		return nil, model.NewAppError(model.ErrInvalidCredentials, "invalid credentials")
	}

	if admin.Status != 1 {
		return nil, model.NewAppError(model.ErrForbidden, "account is disabled")
	}

	if err := bcrypt.CompareHashAndPassword([]byte(admin.PasswordHash), []byte(req.Password)); err != nil {
		return nil, model.NewAppError(model.ErrInvalidCredentials, "invalid credentials")
	}

	role := adminRoleStr(admin.Role)

	tokens, err := s.jwt.GenerateAdminTokens(admin.ID, role)
	if err != nil {
		return nil, model.WrapError(model.ErrInternal, "failed to generate tokens", err)
	}

	refreshClaims, _ := s.jwt.ParseAdminToken(tokens.RefreshToken)
	if refreshClaims != nil {
		if err := s.tokenStore.SetFamily(ctx, tokens.FamilyID, admin.ID, refreshClaims.JTI, s.refreshTTL); err != nil {
			return nil, model.WrapError(model.ErrInternal, "failed to store refresh token", err)
		}
	}

	_ = s.repo.UpdateAdminLastLogin(ctx, admin.ID)

	return &AuthResponse{
		AccessToken:  tokens.AccessToken,
		RefreshToken: tokens.RefreshToken,
	}, nil
}

func (s *AdminService) Refresh(ctx context.Context, refreshToken string) (*AuthResponse, error) {
	claims, err := s.jwt.ParseAdminToken(refreshToken)
	if err != nil {
		return nil, model.NewAppError(model.ErrTokenInvalid, "invalid refresh token")
	}

	if claims.TokenType != "refresh" {
		return nil, model.NewAppError(model.ErrTokenInvalid, "not a refresh token")
	}

	admin, err := s.repo.GetAdminByID(ctx, claims.UserID)
	if err != nil {
		return nil, model.NewAppError(model.ErrTokenInvalid, "admin not found")
	}

	role := adminRoleStr(admin.Role)

	newPair, err := s.jwt.RefreshAdminTokens(claims.UserID, role, claims.FamilyID)
	if err != nil {
		return nil, model.WrapError(model.ErrInternal, "failed to generate tokens", err)
	}

	newRefreshClaims, _ := s.jwt.ParseAdminToken(newPair.RefreshToken)

	result, err := s.tokenStore.CASFamily(ctx, claims.FamilyID, claims.JTI, newRefreshClaims.JTI)
	if err != nil {
		return nil, model.WrapError(model.ErrInternal, "failed to rotate token", err)
	}

	switch result {
	case 0:
		return &AuthResponse{
			AccessToken:  newPair.AccessToken,
			RefreshToken: newPair.RefreshToken,
		}, nil
	case -1:
		return nil, model.NewAppError(model.ErrTokenInvalid, "token family expired or revoked")
	case -2:
		return nil, model.NewAppError(model.ErrTokenInvalid, "token reuse detected, family revoked")
	default:
		return nil, model.NewAppError(model.ErrInternal, "unexpected token rotation result")
	}
}

func adminRoleStr(role int16) string {
	if role == 1 {
		return "superadmin"
	}
	return "admin"
}

func (s *AdminService) Logout(ctx context.Context, claims *TokenClaims) error {
	return s.tokenStore.SetBlacklist(ctx, claims.JTI, s.refreshTTL)
}

type AdminProfile struct {
	ID        int64  `json:"id"`
	Username  string `json:"username"`
	Role      int16  `json:"role"`
	FullName  string `json:"full_name,omitempty"`
	Phone     string `json:"phone,omitempty"`
	AvatarURL string `json:"avatar_url,omitempty"`
}

func (s *AdminService) GetMe(ctx context.Context, adminID int64) (*AdminProfile, error) {
	admin, err := s.repo.GetAdminByID(ctx, adminID)
	if err != nil {
		return nil, model.NewAppError(model.ErrNotFound, "admin not found")
	}
	return adminToProfile(&admin), nil
}

// --- Admin CRUD ---

type CreateAdminRequest struct {
	Username string `json:"username"`
	Password string `json:"password"`
	Role     int16  `json:"role"`
	FullName string `json:"full_name"`
	Phone    string `json:"phone"`
}

func (s *AdminService) CreateAdmin(ctx context.Context, req *CreateAdminRequest) (*AdminProfile, error) {
	if req.Role != 1 && req.Role != 2 {
		return nil, model.NewAppError(model.ErrBadRequest, "invalid role: must be 1 (superadmin) or 2 (admin)")
	}

	hash, err := bcrypt.GenerateFromPassword([]byte(req.Password), bcrypt.DefaultCost)
	if err != nil {
		return nil, model.WrapError(model.ErrInternal, "failed to hash password", err)
	}

	admin, err := s.repo.CreateAdmin(ctx, repository.CreateAdminParams{
		Username:     req.Username,
		PasswordHash: string(hash),
		Role:         req.Role,
		FullName:     pgtype.Text{String: req.FullName, Valid: req.FullName != ""},
		Phone:        pgtype.Text{String: req.Phone, Valid: req.Phone != ""},
		Status:       1,
	})
	if err != nil {
		return nil, model.WrapError(model.ErrUsernameExists, "username already exists", err)
	}

	return adminToProfile(&admin), nil
}

func (s *AdminService) GetAdmin(ctx context.Context, id int64) (*AdminProfile, error) {
	admin, err := s.repo.GetAdminByID(ctx, id)
	if err != nil {
		return nil, model.NewAppError(model.ErrNotFound, "admin not found")
	}
	return adminToProfile(&admin), nil
}

type AdminListItem struct {
	ID          int64   `json:"id"`
	Username    string  `json:"username"`
	Role        int16   `json:"role"`
	FullName    string  `json:"full_name,omitempty"`
	Phone       string  `json:"phone,omitempty"`
	AvatarURL   string  `json:"avatar_url,omitempty"`
	Status      int16   `json:"status"`
	LastLoginAt *string `json:"last_login_at,omitempty"`
	CreatedAt   string  `json:"created_at"`
}

type AdminListFilter struct {
	Username *string
	Role     *int16
	Status   *int16
}

func (s *AdminService) ListAdmins(ctx context.Context, page, pageSize int, filter AdminListFilter) ([]AdminListItem, int64, error) {
	offset := (page - 1) * pageSize

	filterParams := repository.CountAdminsParams{
		FilterUsername: toPgText(filter.Username),
		FilterRole:     toPgInt2(filter.Role),
		FilterStatus:   toPgInt2(filter.Status),
	}

	total, err := s.repo.CountAdmins(ctx, filterParams)
	if err != nil {
		return nil, 0, model.WrapError(model.ErrInternal, "failed to count admins", err)
	}

	rows, err := s.repo.ListAdmins(ctx, repository.ListAdminsParams{
		FilterUsername: filterParams.FilterUsername,
		FilterRole:     filterParams.FilterRole,
		FilterStatus:   filterParams.FilterStatus,
		OffsetVal:      int32(offset),
		LimitVal:       int32(pageSize),
	})
	if err != nil {
		return nil, 0, model.WrapError(model.ErrInternal, "failed to list admins", err)
	}

	items := make([]AdminListItem, len(rows))
	for i := range rows {
		r := &rows[i]
		items[i] = AdminListItem{
			ID:        r.ID,
			Username:  r.Username,
			Role:      r.Role,
			FullName:  r.FullName.String,
			Phone:     r.Phone.String,
			AvatarURL: r.AvatarUrl.String,
			Status:    r.Status,
			CreatedAt: r.CreatedAt.Format(time.RFC3339),
		}
		if r.LastLoginAt != nil {
			t := r.LastLoginAt.Format(time.RFC3339)
			items[i].LastLoginAt = &t
		}
	}

	return items, total, nil
}

type UpdateAdminRequest struct {
	FullName string `json:"full_name"`
	Phone    string `json:"phone"`
	Role     *int16 `json:"role,omitempty"`
	Status   *int16 `json:"status,omitempty"`
}

func (s *AdminService) UpdateAdmin(ctx context.Context, id int64, req *UpdateAdminRequest) (*AdminProfile, error) {
	params := repository.UpdateAdminParams{
		ID:       id,
		FullName: req.FullName,
		Phone:    req.Phone,
	}
	if req.Role != nil {
		params.SetRole = true
		params.Role = *req.Role
	}
	if req.Status != nil {
		params.SetStatus = true
		params.Status = *req.Status
	}

	admin, err := s.repo.UpdateAdmin(ctx, params)
	if err != nil {
		return nil, model.NewAppError(model.ErrNotFound, "admin not found")
	}

	return adminToProfile(&admin), nil
}

func (s *AdminService) ChangeAdminPassword(ctx context.Context, id int64, newPassword string) error {
	hash, err := bcrypt.GenerateFromPassword([]byte(newPassword), bcrypt.DefaultCost)
	if err != nil {
		return model.WrapError(model.ErrInternal, "failed to hash password", err)
	}
	return s.repo.UpdateAdminPassword(ctx, repository.UpdateAdminPasswordParams{
		ID:           id,
		PasswordHash: string(hash),
	})
}

func (s *AdminService) DeleteAdmin(ctx context.Context, id int64) error {
	return s.repo.SoftDeleteAdmin(ctx, id)
}

// --- Customer Management ---

type CustomerListItem struct {
	ID             int64  `json:"id"`
	Username       string `json:"username"`
	Name           string `json:"name,omitempty"`
	Phone          string `json:"phone,omitempty"`
	Email          string `json:"email,omitempty"`
	AvatarURL      string `json:"avatar_url,omitempty"`
	IsVip          bool   `json:"is_vip"`
	DiamondBalance int64  `json:"diamond_balance"`
	CreatedAt      string `json:"created_at"`
}

type CustomerListFilter struct {
	Name  *string
	IsVip *bool
}

func (s *AdminService) ListCustomers(ctx context.Context, page, pageSize int, filter CustomerListFilter) ([]CustomerListItem, int64, error) {
	offset := (page - 1) * pageSize

	filterParams := repository.CountCustomersParams{
		FilterName:  toPgText(filter.Name),
		FilterIsVip: toPgBool(filter.IsVip),
	}

	total, err := s.repo.CountCustomers(ctx, filterParams)
	if err != nil {
		return nil, 0, model.WrapError(model.ErrInternal, "failed to count customers", err)
	}

	rows, err := s.repo.ListCustomers(ctx, repository.ListCustomersParams{
		FilterName:  filterParams.FilterName,
		FilterIsVip: filterParams.FilterIsVip,
		OffsetVal:   int32(offset),
		LimitVal:    int32(pageSize),
	})
	if err != nil {
		return nil, 0, model.WrapError(model.ErrInternal, "failed to list customers", err)
	}

	items := make([]CustomerListItem, len(rows))
	for i := range rows {
		r := &rows[i]
		items[i] = CustomerListItem{
			ID:             r.ID,
			Username:       r.Username,
			Name:           r.Name.String,
			Phone:          r.Phone.String,
			Email:          r.Email.String,
			AvatarURL:      r.AvatarUrl.String,
			IsVip:          r.IsVip,
			DiamondBalance: r.DiamondBalance,
			CreatedAt:      r.CreatedAt.Format(time.RFC3339),
		}
	}

	return items, total, nil
}

func (s *AdminService) ResetCustomerPassword(ctx context.Context, customerID int64, newPassword string) error {
	hash, err := bcrypt.GenerateFromPassword([]byte(newPassword), bcrypt.DefaultCost)
	if err != nil {
		return model.WrapError(model.ErrInternal, "failed to hash password", err)
	}
	return s.repo.ResetCustomerPassword(ctx, repository.ResetCustomerPasswordParams{
		ID:           customerID,
		PasswordHash: string(hash),
	})
}

// --- Video Review ---

type AdminVideoItem struct {
	ID           int64  `json:"id"`
	CustomerID   int64  `json:"customer_id"`
	Title        string `json:"title,omitempty"`
	FileURL      string `json:"file_url"`
	ThumbnailURL string `json:"thumbnail_url,omitempty"`
	Status       int16  `json:"status"`
	TotalClicks  int64  `json:"total_clicks"`
	LikeCount    int64  `json:"like_count"`
	CreatedAt    string `json:"created_at"`
	AuthorName   string `json:"author_name"`
}

type AdminVideoFilter struct {
	AuthorName *string
	Status     *int16
	IsVip      *bool
	StartDate  *time.Time
	EndDate    *time.Time
}

func (s *AdminService) ListVideos(ctx context.Context, page, pageSize int, filter AdminVideoFilter) ([]AdminVideoItem, int64, error) {
	offset := (page - 1) * pageSize

	countParams := repository.AdminCountVideosParams{
		AuthorNameFilter: toPgText(filter.AuthorName),
		StatusFilter:     toPgInt2(filter.Status),
		IsVipFilter:      toPgBool(filter.IsVip),
		StartDate:        filter.StartDate,
		EndDate:          filter.EndDate,
	}

	total, err := s.repo.AdminCountVideos(ctx, countParams)
	if err != nil {
		return nil, 0, model.WrapError(model.ErrInternal, "failed to count videos", err)
	}

	rows, err := s.repo.AdminListVideos(ctx, repository.AdminListVideosParams{
		AuthorNameFilter: countParams.AuthorNameFilter,
		StatusFilter:     countParams.StatusFilter,
		IsVipFilter:      countParams.IsVipFilter,
		StartDate:        countParams.StartDate,
		EndDate:          countParams.EndDate,
		OffsetVal:        int32(offset),
		LimitVal:         int32(pageSize),
	})
	if err != nil {
		return nil, 0, model.WrapError(model.ErrInternal, "failed to list videos", err)
	}

	items := make([]AdminVideoItem, len(rows))
	for i := range rows {
		r := &rows[i]
		items[i] = AdminVideoItem{
			ID:           r.ID,
			CustomerID:   r.CustomerID,
			Title:        r.Title.String,
			FileURL:      r.FileUrl,
			ThumbnailURL: r.ThumbnailUrl.String,
			Status:       r.Status,
			TotalClicks:  r.TotalClicks,
			LikeCount:    r.LikeCount,
			CreatedAt:    r.CreatedAt.Format(time.RFC3339),
			AuthorName:   r.AuthorName,
		}
	}

	return items, total, nil
}

type ReviewVideoRequest struct {
	Status      int16  `json:"status"`
	ReviewNotes string `json:"review_notes"`
}

func (s *AdminService) ReviewVideo(ctx context.Context, videoID, adminID int64, req *ReviewVideoRequest) error {
	return s.repo.ReviewVideo(ctx, repository.ReviewVideoParams{
		ID:          videoID,
		Status:      req.Status,
		ReviewNotes: pgtype.Text{String: req.ReviewNotes, Valid: req.ReviewNotes != ""},
		ReviewedBy:  pgtype.Int8{Int64: adminID, Valid: true},
	})
}

type AdminVideoDetail struct {
	ID            int64   `json:"id"`
	CustomerID    int64   `json:"customer_id"`
	Title         string  `json:"title,omitempty"`
	Description   string  `json:"description,omitempty"`
	FileName      string  `json:"file_name"`
	FileSize      int64   `json:"file_size"`
	FileURL       string  `json:"file_url"`
	ThumbnailURL  string  `json:"thumbnail_url,omitempty"`
	PreviewGifURL string  `json:"preview_gif_url,omitempty"`
	MimeType      string  `json:"mime_type"`
	Duration      int32   `json:"duration,omitempty"`
	Width         int32   `json:"width,omitempty"`
	Height        int32   `json:"height,omitempty"`
	Status        int16   `json:"status"`
	ReviewNotes   string  `json:"review_notes,omitempty"`
	ReviewedBy    *int64  `json:"reviewed_by,omitempty"`
	ReviewedAt    *string `json:"reviewed_at,omitempty"`
	TotalClicks   int64   `json:"total_clicks"`
	ValidClicks   int64   `json:"valid_clicks"`
	LikeCount     int64   `json:"like_count"`
	CreatedAt     string  `json:"created_at"`
	UpdatedAt     string  `json:"updated_at"`
	AuthorName    string  `json:"author_name"`
	AuthorAvatar  string  `json:"author_avatar,omitempty"`
}

func (s *AdminService) GetVideoDetail(ctx context.Context, videoID int64) (*AdminVideoDetail, error) {
	r, err := s.repo.AdminGetVideoDetail(ctx, videoID)
	if err != nil {
		return nil, model.NewAppError(model.ErrNotFound, "video not found")
	}

	detail := &AdminVideoDetail{
		ID:            r.ID,
		CustomerID:    r.CustomerID,
		Title:         r.Title.String,
		Description:   r.Description.String,
		FileName:      r.FileName,
		FileSize:      r.FileSize,
		FileURL:       r.FileUrl,
		ThumbnailURL:  r.ThumbnailUrl.String,
		PreviewGifURL: r.PreviewGifUrl.String,
		MimeType:      r.MimeType,
		Duration:      r.Duration.Int32,
		Width:         r.Width.Int32,
		Height:        r.Height.Int32,
		Status:        r.Status,
		ReviewNotes:   r.ReviewNotes.String,
		TotalClicks:   r.TotalClicks,
		ValidClicks:   r.ValidClicks,
		LikeCount:     r.LikeCount,
		CreatedAt:     r.CreatedAt.Format(time.RFC3339),
		UpdatedAt:     r.UpdatedAt.Format(time.RFC3339),
		AuthorName:    r.AuthorName,
		AuthorAvatar:  r.AuthorAvatar.String,
	}

	if r.ReviewedBy.Valid {
		id := r.ReviewedBy.Int64
		detail.ReviewedBy = &id
	}
	if r.ReviewedAt != nil {
		t := r.ReviewedAt.Format(time.RFC3339)
		detail.ReviewedAt = &t
	}

	return detail, nil
}

func (s *AdminService) DeleteVideo(ctx context.Context, videoID int64) error {
	return s.repo.AdminDeleteVideo(ctx, videoID)
}

func adminToProfile(a *repository.AdminUser) *AdminProfile {
	return &AdminProfile{
		ID:        a.ID,
		Username:  a.Username,
		Role:      a.Role,
		FullName:  a.FullName.String,
		Phone:     a.Phone.String,
		AvatarURL: a.AvatarUrl.String,
	}
}

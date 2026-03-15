package service

import (
	"context"
	"time"

	"gabon-go/internal/model"
	"gabon-go/internal/repository"
)

type ReportRepo interface {
	ReportRevenue(ctx context.Context, arg repository.ReportRevenueParams) ([]repository.ReportRevenueRow, error)
	ReportRevenueTotal(ctx context.Context, arg repository.ReportRevenueTotalParams) (int64, error)
	ReportVideosDaily(ctx context.Context, arg repository.ReportVideosDailyParams) ([]repository.ReportVideosDailyRow, error)
	ReportVideosDailyTotal(ctx context.Context, arg repository.ReportVideosDailyTotalParams) (int64, error)
	ReportVideoSummary(ctx context.Context, arg repository.ReportVideoSummaryParams) (repository.ReportVideoSummaryRow, error)
}

type ReportService struct {
	repo ReportRepo
}

func NewReportService(repo ReportRepo) *ReportService {
	return &ReportService{repo: repo}
}

type RevenueItem struct {
	Date          string `json:"date"`
	ClaimCount    int64  `json:"claim_count"`
	TotalDiamonds int64  `json:"total_diamonds"`
}

func (s *ReportService) Revenue(ctx context.Context, startDate, endDate time.Time, page, pageSize int) ([]RevenueItem, int64, error) {
	offset := (page - 1) * pageSize

	total, err := s.repo.ReportRevenueTotal(ctx, repository.ReportRevenueTotalParams{
		StartDate: &startDate,
		EndDate:   &endDate,
	})
	if err != nil {
		return nil, 0, model.WrapError(model.ErrInternal, "failed to count revenue days", err)
	}

	rows, err := s.repo.ReportRevenue(ctx, repository.ReportRevenueParams{
		StartDate: &startDate,
		EndDate:   &endDate,
		OffsetVal: int32(offset),
		LimitVal:  int32(pageSize),
	})
	if err != nil {
		return nil, 0, model.WrapError(model.ErrInternal, "failed to query revenue", err)
	}

	items := make([]RevenueItem, len(rows))
	for i := range rows {
		r := &rows[i]
		items[i] = RevenueItem{
			Date:          r.ReportDate.Time.Format("2006-01-02"),
			ClaimCount:    r.ClaimCount,
			TotalDiamonds: r.TotalDiamonds,
		}
	}

	return items, total, nil
}

type VideoDailyItem struct {
	Date             string `json:"date"`
	UploadCount      int64  `json:"upload_count"`
	TotalClicks      int64  `json:"total_clicks"`
	TotalValidClicks int64  `json:"total_valid_clicks"`
	TotalLikes       int64  `json:"total_likes"`
}

func (s *ReportService) VideosDaily(ctx context.Context, startDate, endDate time.Time, page, pageSize int) ([]VideoDailyItem, int64, error) {
	offset := (page - 1) * pageSize

	total, err := s.repo.ReportVideosDailyTotal(ctx, repository.ReportVideosDailyTotalParams{
		StartDate: startDate,
		EndDate:   endDate,
	})
	if err != nil {
		return nil, 0, model.WrapError(model.ErrInternal, "failed to count video daily", err)
	}

	rows, err := s.repo.ReportVideosDaily(ctx, repository.ReportVideosDailyParams{
		StartDate: startDate,
		EndDate:   endDate,
		OffsetVal: int32(offset),
		LimitVal:  int32(pageSize),
	})
	if err != nil {
		return nil, 0, model.WrapError(model.ErrInternal, "failed to query video daily", err)
	}

	items := make([]VideoDailyItem, len(rows))
	for i := range rows {
		r := &rows[i]
		items[i] = VideoDailyItem{
			Date:             r.ReportDate.Time.Format("2006-01-02"),
			UploadCount:      r.UploadCount,
			TotalClicks:      r.TotalClicks,
			TotalValidClicks: r.TotalValidClicks,
			TotalLikes:       r.TotalLikes,
		}
	}

	return items, total, nil
}

type VideoSummary struct {
	TotalVideos      int64 `json:"total_videos"`
	ApprovedCount    int64 `json:"approved_count"`
	PendingCount     int64 `json:"pending_count"`
	RejectedCount    int64 `json:"rejected_count"`
	TotalClicks      int64 `json:"total_clicks"`
	TotalValidClicks int64 `json:"total_valid_clicks"`
	TotalLikes       int64 `json:"total_likes"`
}

func (s *ReportService) VideoSummary(ctx context.Context, startDate, endDate time.Time) (*VideoSummary, error) {
	row, err := s.repo.ReportVideoSummary(ctx, repository.ReportVideoSummaryParams{
		StartDate: startDate,
		EndDate:   endDate,
	})
	if err != nil {
		return nil, model.WrapError(model.ErrInternal, "failed to query video summary", err)
	}

	return &VideoSummary{
		TotalVideos:      row.TotalVideos,
		ApprovedCount:    row.ApprovedCount,
		PendingCount:     row.PendingCount,
		RejectedCount:    row.RejectedCount,
		TotalClicks:      row.TotalClicks,
		TotalValidClicks: row.TotalValidClicks,
		TotalLikes:       row.TotalLikes,
	}, nil
}

package lab.gabon.service;

import java.time.Instant;
import java.util.List;
import lab.gabon.common.ApiResponse;
import lab.gabon.common.AppError;
import lab.gabon.common.AppException;
import lab.gabon.common.PageResponse;
import lab.gabon.model.entity.Video;
import lab.gabon.model.entity.VideoPlayRecord;
import lab.gabon.model.request.VideoRequests.ConfirmUploadRequest;
import lab.gabon.model.request.VideoRequests.UploadUrlRequest;
import lab.gabon.model.response.VideoResponses.UploadUrlResponse;
import lab.gabon.model.response.VideoResponses.VideoDetailResponse;
import lab.gabon.model.response.VideoResponses.VideoListItemResponse;
import lab.gabon.repository.CustomerRepository;
import lab.gabon.repository.VideoLikeRepository;
import lab.gabon.repository.VideoPlayRecordRepository;
import lab.gabon.repository.VideoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class VideoService {

  private static final Logger log = LoggerFactory.getLogger(VideoService.class);

  private static final short STATUS_PENDING = 1;
  private static final short STATUS_APPROVED = 4;
  private static final short PLAY_TYPE_CLICK = 1;
  private static final short PLAY_TYPE_VALID = 2;

  private final VideoRepository videoRepo;
  private final VideoLikeRepository videoLikeRepo;
  private final VideoPlayRecordRepository playRecordRepo;
  private final CustomerRepository customerRepo;
  private final StorageService storageService;

  public VideoService(
      VideoRepository videoRepo,
      VideoLikeRepository videoLikeRepo,
      VideoPlayRecordRepository playRecordRepo,
      CustomerRepository customerRepo,
      StorageService storageService) {
    this.videoRepo = videoRepo;
    this.videoLikeRepo = videoLikeRepo;
    this.playRecordRepo = playRecordRepo;
    this.customerRepo = customerRepo;
    this.storageService = storageService;
  }

  // -- List ---------------------------------------------------------------

  public ApiResponse<PageResponse<VideoListItemResponse>> listVideos(int page, int pageSize) {
    int offset = (page - 1) * pageSize;
    var videos = videoRepo.findApprovedVideos(pageSize, offset);
    long total = videoRepo.countApprovedVideos();
    return ApiResponse.ok(new PageResponse<>(toListItems(videos), total, page, pageSize));
  }

  public ApiResponse<PageResponse<VideoListItemResponse>> listFeatured(int page, int pageSize) {
    int offset = (page - 1) * pageSize;
    var videos = videoRepo.findFeaturedVideos(pageSize, offset);
    long total = videoRepo.countApprovedVideos();
    return ApiResponse.ok(new PageResponse<>(toListItems(videos), total, page, pageSize));
  }

  public ApiResponse<PageResponse<VideoListItemResponse>> listMyVideos(
      long customerId, int page, int pageSize) {
    int offset = (page - 1) * pageSize;
    var videos = videoRepo.findMyVideos(customerId, pageSize, offset);
    long total = videoRepo.countMyVideos(customerId);
    return ApiResponse.ok(new PageResponse<>(toListItems(videos), total, page, pageSize));
  }

  public ApiResponse<PageResponse<VideoListItemResponse>> listUserVideos(
      long userId, int page, int pageSize) {
    int offset = (page - 1) * pageSize;
    var videos = videoRepo.findUserVideos(userId, pageSize, offset);
    long total = videoRepo.countUserVideos(userId);
    return ApiResponse.ok(new PageResponse<>(toListItems(videos), total, page, pageSize));
  }

  // -- Detail -------------------------------------------------------------

  public ApiResponse<VideoDetailResponse> getVideo(long videoId, Long customerId) {
    var video = videoRepo.findActiveById(videoId).orElseThrow(() -> videoNotFound());

    boolean isApproved = video.status() == STATUS_APPROVED;
    boolean isOwner = customerId != null && video.customerId() == customerId;
    if (!isApproved && !isOwner) {
      throw new AppException(new AppError.VideoNotApproved());
    }

    boolean liked =
        customerId != null && videoLikeRepo.existsByVideoIdAndCustomerId(videoId, customerId);

    var customer = customerRepo.findActiveById(video.customerId()).orElse(null);
    var detail = toDetail(video, customer, liked);
    return ApiResponse.ok(detail);
  }

  // -- Upload -------------------------------------------------------------

  public ApiResponse<UploadUrlResponse> generateUploadUrl(long customerId, UploadUrlRequest req) {
    var result = storageService.generateVideoUploadUrl(req.fileName());
    return ApiResponse.ok(result);
  }

  public ApiResponse<VideoDetailResponse> confirmUpload(long customerId, ConfirmUploadRequest req) {
    var uploadResult = storageService.generateVideoUploadUrl(req.fileName());
    var video =
        new Video(
            null,
            customerId,
            req.title(),
            req.description(),
            req.fileName(),
            req.fileSize(),
            uploadResult.fileUrl(),
            null,
            null,
            req.mimeType(),
            req.duration(),
            req.width(),
            req.height(),
            STATUS_PENDING,
            null,
            null,
            null,
            0,
            0,
            0,
            Instant.now(),
            Instant.now(),
            null);
    var saved = videoRepo.save(video);

    var customer = customerRepo.findActiveById(customerId).orElse(null);
    return ApiResponse.ok(toDetail(saved, customer, false));
  }

  // -- Delete -------------------------------------------------------------

  public ApiResponse<Void> deleteVideo(long videoId, long customerId) {
    int rows = videoRepo.softDelete(videoId, customerId);
    if (rows == 0) {
      throw videoNotFound();
    }
    return ApiResponse.ok();
  }

  // -- Like / Unlike ------------------------------------------------------

  public ApiResponse<Void> likeVideo(long videoId, long customerId) {
    int rows = videoLikeRepo.likeVideo(videoId, customerId);
    if (rows == 0) {
      throw new AppException(new AppError.AlreadyLiked());
    }
    return ApiResponse.ok();
  }

  public ApiResponse<Void> unlikeVideo(long videoId, long customerId) {
    int rows = videoLikeRepo.unlikeVideo(videoId, customerId);
    if (rows == 0) {
      log.debug("Unlike had no effect: videoId={}, customerId={}", videoId, customerId);
    }
    return ApiResponse.ok();
  }

  // -- Play Tracking ------------------------------------------------------

  public ApiResponse<Void> recordPlayClick(long videoId, Long customerId, String ipAddress) {
    videoRepo.incrementTotalClicks(videoId);
    playRecordRepo.save(
        new VideoPlayRecord(null, videoId, customerId, PLAY_TYPE_CLICK, ipAddress, Instant.now()));
    return ApiResponse.ok();
  }

  public ApiResponse<Void> recordPlayValid(long videoId, Long customerId, String ipAddress) {
    videoRepo.incrementValidClicks(videoId);
    playRecordRepo.save(
        new VideoPlayRecord(null, videoId, customerId, PLAY_TYPE_VALID, ipAddress, Instant.now()));
    return ApiResponse.ok();
  }

  // -- Internal -----------------------------------------------------------

  private List<VideoListItemResponse> toListItems(List<Video> videos) {
    return videos.stream().map(this::toListItem).toList();
  }

  private VideoListItemResponse toListItem(Video v) {
    var customer = customerRepo.findActiveById(v.customerId()).orElse(null);
    return new VideoListItemResponse(
        v.id(),
        v.customerId(),
        customer != null ? customer.name() : null,
        customer != null ? customer.avatarUrl() : null,
        v.title(),
        v.fileUrl(),
        v.thumbnailUrl(),
        v.likeCount(),
        v.totalClicks(),
        v.createdAt());
  }

  private VideoDetailResponse toDetail(
      Video v, lab.gabon.model.entity.Customer customer, boolean liked) {
    return new VideoDetailResponse(
        v.id(),
        v.customerId(),
        customer != null ? customer.name() : null,
        customer != null ? customer.avatarUrl() : null,
        v.title(),
        v.description(),
        v.fileUrl(),
        v.thumbnailUrl(),
        v.status(),
        v.totalClicks(),
        v.validClicks(),
        v.likeCount(),
        liked,
        v.createdAt());
  }

  private static AppException videoNotFound() {
    return new AppException(new AppError.VideoNotFound());
  }
}

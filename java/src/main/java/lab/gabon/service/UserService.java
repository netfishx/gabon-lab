package lab.gabon.service;

import java.util.List;
import lab.gabon.common.AppError;
import lab.gabon.common.AppException;
import lab.gabon.common.PageResponse;
import lab.gabon.model.entity.Customer;
import lab.gabon.model.entity.UserFollow;
import lab.gabon.model.request.UserRequests.UpdateProfileRequest;
import lab.gabon.model.response.UserResponses.FollowListItemResponse;
import lab.gabon.model.response.UserResponses.PublicUserResponse;
import lab.gabon.model.response.UserResponses.UserProfileResponse;
import lab.gabon.model.response.VideoResponses.UploadUrlResponse;
import lab.gabon.repository.CustomerRepository;
import lab.gabon.repository.UserFollowRepository;
import org.springframework.stereotype.Service;

@Service
public class UserService {

  private final CustomerRepository customerRepo;
  private final UserFollowRepository userFollowRepo;
  private final StorageService storageService;

  public UserService(
      CustomerRepository customerRepo,
      UserFollowRepository userFollowRepo,
      StorageService storageService) {
    this.customerRepo = customerRepo;
    this.userFollowRepo = userFollowRepo;
    this.storageService = storageService;
  }

  // -- Profile ----------------------------------------------------------------

  public UserProfileResponse getMyProfile(long customerId) {
    var customer = findActiveCustomer(customerId);
    long followingCount = userFollowRepo.countFollowing(customerId);
    long followerCount = userFollowRepo.countFollowers(customerId);
    return toProfileResponse(customer, followingCount, followerCount);
  }

  public UserProfileResponse updateMyProfile(long customerId, UpdateProfileRequest req) {
    customerRepo.updateProfile(customerId, req.name(), req.phone(), req.email(), req.signature());
    return getMyProfile(customerId);
  }

  // -- Avatar -----------------------------------------------------------------

  public UploadUrlResponse generateAvatarUploadUrl(long customerId) {
    return storageService.generateAvatarUploadUrl("avatar.jpg");
  }

  public void confirmAvatarUpload(long customerId, String avatarUrl) {
    customerRepo.updateAvatarUrl(customerId, avatarUrl);
  }

  // -- Public Profile ---------------------------------------------------------

  public PublicUserResponse getUserProfile(long userId, Long currentUserId) {
    var customer = findActiveCustomer(userId);
    long followingCount = userFollowRepo.countFollowing(userId);
    long followerCount = userFollowRepo.countFollowers(userId);
    boolean isFollowing =
        currentUserId != null && userFollowRepo.existsByFollowerAndFollowed(currentUserId, userId);
    return new PublicUserResponse(
        customer.id(),
        customer.username(),
        customer.name(),
        customer.avatarUrl(),
        customer.signature(),
        customer.isVip(),
        followingCount,
        followerCount,
        isFollowing);
  }

  // -- Follow / Unfollow ------------------------------------------------------

  public void follow(long followerId, long followedId) {
    if (followerId == followedId) {
      throw new AppException(new AppError.CannotFollowSelf());
    }
    findActiveCustomer(followedId);
    int rows = userFollowRepo.follow(followerId, followedId);
    if (rows == 0) {
      throw new AppException(new AppError.AlreadyFollowing());
    }
  }

  public void unfollow(long followerId, long followedId) {
    int rows = userFollowRepo.unfollow(followerId, followedId);
    if (rows == 0) {
      throw new AppException(new AppError.NotFollowing());
    }
  }

  // -- Following / Followers Lists --------------------------------------------

  public PageResponse<FollowListItemResponse> getFollowing(long userId, int page, int pageSize) {
    int offset = (page - 1) * pageSize;
    List<UserFollow> follows = userFollowRepo.findFollowing(userId, pageSize, offset);
    long total = userFollowRepo.countFollowing(userId);
    var items = follows.stream().map(f -> toFollowListItem(f.followedId())).toList();
    return new PageResponse<>(items, total, page, pageSize);
  }

  public PageResponse<FollowListItemResponse> getFollowers(long userId, int page, int pageSize) {
    int offset = (page - 1) * pageSize;
    List<UserFollow> follows = userFollowRepo.findFollowers(userId, pageSize, offset);
    long total = userFollowRepo.countFollowers(userId);
    var items = follows.stream().map(f -> toFollowListItem(f.followerId())).toList();
    return new PageResponse<>(items, total, page, pageSize);
  }

  // -- Internal ---------------------------------------------------------------

  private Customer findActiveCustomer(long id) {
    return customerRepo
        .findActiveById(id)
        .orElseThrow(() -> new AppException(new AppError.NotFound("user not found")));
  }

  private UserProfileResponse toProfileResponse(
      Customer c, long followingCount, long followerCount) {
    return new UserProfileResponse(
        c.id(),
        c.username(),
        c.name(),
        c.phone(),
        c.email(),
        c.avatarUrl(),
        c.signature(),
        c.isVip(),
        c.diamondBalance(),
        followingCount,
        followerCount);
  }

  private FollowListItemResponse toFollowListItem(long userId) {
    var customer = customerRepo.findActiveById(userId).orElse(null);
    if (customer == null) {
      return new FollowListItemResponse(userId, null, null, null);
    }
    return new FollowListItemResponse(
        customer.id(), customer.username(), customer.name(), customer.avatarUrl());
  }
}

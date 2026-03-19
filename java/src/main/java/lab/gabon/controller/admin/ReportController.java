package lab.gabon.controller.admin;

import java.util.List;
import lab.gabon.common.ApiResponse;
import lab.gabon.model.response.AdminResponses.DailyVideoReportItem;
import lab.gabon.model.response.AdminResponses.RevenueReportItem;
import lab.gabon.model.response.AdminResponses.VideoSummaryItem;
import lab.gabon.service.AdminService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/v1/reports")
public class ReportController {

  private final AdminService adminService;

  public ReportController(AdminService adminService) {
    this.adminService = adminService;
  }

  @GetMapping("/revenue")
  public ApiResponse<List<RevenueReportItem>> revenue(
      @RequestParam String startDate, @RequestParam String endDate) {
    var result = adminService.getRevenueReport(startDate, endDate);
    return ApiResponse.ok(result);
  }

  @GetMapping("/video/daily")
  public ApiResponse<List<DailyVideoReportItem>> dailyVideo(
      @RequestParam String startDate, @RequestParam String endDate) {
    var result = adminService.getDailyVideoReport(startDate, endDate);
    return ApiResponse.ok(result);
  }

  @GetMapping("/video/summary")
  public ApiResponse<List<VideoSummaryItem>> videoSummary() {
    var result = adminService.getVideoSummaryReport();
    return ApiResponse.ok(result);
  }
}

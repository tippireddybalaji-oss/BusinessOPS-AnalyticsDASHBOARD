package com.businessops.dashboard.controller;

import com.businessops.dashboard.dto.CategoryAnalyticsResponse;
import com.businessops.dashboard.dto.ClientPerformanceResponse;
import com.businessops.dashboard.dto.FinancialSummaryResponse;
import com.businessops.dashboard.dto.FinancialTrendsResponse;
import com.businessops.dashboard.security.SecurityUtils;
import com.businessops.dashboard.service.AnalyticsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller exposing financial analytics, KPI metrics, time-series trends,
 * category distributions, and client revenue performance adhering to Phase 5 Section 9.
 *
 * <p>Base path: {@code /api/v1/workspaces/{workspaceId}/analytics}
 */
@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/analytics")
public class AnalyticsController {

    private final AnalyticsService analyticsService;
    private final SecurityUtils securityUtils;

    public AnalyticsController(AnalyticsService analyticsService, SecurityUtils securityUtils) {
        this.analyticsService = analyticsService;
        this.securityUtils = securityUtils;
    }

    /**
     * Retrieves executive financial KPIs and realized/pipeline totals (Section 9.2).
     *
     * @param workspaceId target workspace ID
     * @param currency optional ISO-4217 currency code (defaults to workspace default currency)
     * @param startDate optional start calendar date (YYYY-MM-DD)
     * @param endDate optional end calendar date (YYYY-MM-DD)
     * @param startInstant optional start instant (ISO-8601)
     * @param endInstant optional end instant (ISO-8601)
     * @param timezone optional timezone identifier (defaults to UTC)
     * @param includePending whether to include pending pipeline items (defaults to false)
     * @return 200 OK with executive financial summary
     */
    @GetMapping("/summary")
    public ResponseEntity<FinancialSummaryResponse> getFinancialSummary(
            @PathVariable("workspaceId") String workspaceId,
            @RequestParam(name = "currency", required = false) String currency,
            @RequestParam(name = "startDate", required = false) String startDate,
            @RequestParam(name = "endDate", required = false) String endDate,
            @RequestParam(name = "startInstant", required = false) String startInstant,
            @RequestParam(name = "endInstant", required = false) String endInstant,
            @RequestParam(name = "timezone", required = false) String timezone,
            @RequestParam(name = "includePending", required = false, defaultValue = "false") boolean includePending
    ) {
        String uid = securityUtils.getAuthenticatedUid();
        FinancialSummaryResponse response = analyticsService.getFinancialSummary(
                uid, workspaceId, currency, startDate, endDate, startInstant, endInstant, timezone, includePending
        );
        return ResponseEntity.ok(response);
    }

    /**
     * Retrieves contiguous time-series trends grouped by month or year (Section 9.3).
     *
     * @param workspaceId target workspace ID
     * @param interval interval unit ("MONTHLY" or "YEARLY", defaults to "MONTHLY")
     * @param currency optional ISO-4217 currency code
     * @param startDate optional start calendar date (YYYY-MM-DD)
     * @param endDate optional end calendar date (YYYY-MM-DD)
     * @param startInstant optional start instant (ISO-8601)
     * @param endInstant optional end instant (ISO-8601)
     * @param timezone optional timezone identifier (defaults to UTC)
     * @param includePending whether to include pending pipeline items (defaults to false)
     * @return 200 OK with financial trends
     */
    @GetMapping("/trends")
    public ResponseEntity<FinancialTrendsResponse> getFinancialTrends(
            @PathVariable("workspaceId") String workspaceId,
            @RequestParam(name = "interval", required = false, defaultValue = "MONTHLY") String interval,
            @RequestParam(name = "currency", required = false) String currency,
            @RequestParam(name = "startDate", required = false) String startDate,
            @RequestParam(name = "endDate", required = false) String endDate,
            @RequestParam(name = "startInstant", required = false) String startInstant,
            @RequestParam(name = "endInstant", required = false) String endInstant,
            @RequestParam(name = "timezone", required = false) String timezone,
            @RequestParam(name = "includePending", required = false, defaultValue = "false") boolean includePending
    ) {
        String uid = securityUtils.getAuthenticatedUid();
        FinancialTrendsResponse response = analyticsService.getFinancialTrends(
                uid, workspaceId, interval, currency, startDate, endDate, startInstant, endInstant, timezone, includePending
        );
        return ResponseEntity.ok(response);
    }

    /**
     * Retrieves category breakdown analytics with combined period header totals (Section 9.4).
     *
     * @param workspaceId target workspace ID
     * @param type optional category type filter ("INCOME" or "EXPENSE")
     * @param currency optional ISO-4217 currency code
     * @param startDate optional start calendar date (YYYY-MM-DD)
     * @param endDate optional end calendar date (YYYY-MM-DD)
     * @param startInstant optional start instant (ISO-8601)
     * @param endInstant optional end instant (ISO-8601)
     * @param timezone optional timezone identifier (defaults to UTC)
     * @param includePending whether to include pending pipeline items (defaults to false)
     * @return 200 OK with category breakdown analytics
     */
    @GetMapping("/categories")
    public ResponseEntity<CategoryAnalyticsResponse> getCategoryAnalytics(
            @PathVariable("workspaceId") String workspaceId,
            @RequestParam(name = "type", required = false) String type,
            @RequestParam(name = "currency", required = false) String currency,
            @RequestParam(name = "startDate", required = false) String startDate,
            @RequestParam(name = "endDate", required = false) String endDate,
            @RequestParam(name = "startInstant", required = false) String startInstant,
            @RequestParam(name = "endInstant", required = false) String endInstant,
            @RequestParam(name = "timezone", required = false) String timezone,
            @RequestParam(name = "includePending", required = false, defaultValue = "false") boolean includePending
    ) {
        String uid = securityUtils.getAuthenticatedUid();
        CategoryAnalyticsResponse response = analyticsService.getCategoryAnalytics(
                uid, workspaceId, type, currency, startDate, endDate, startInstant, endInstant, timezone, includePending
        );
        return ResponseEntity.ok(response);
    }

    /**
     * Retrieves client revenue performance ranking and KPI averages (Section 9.5).
     *
     * @param workspaceId target workspace ID
     * @param currency optional ISO-4217 currency code
     * @param startDate optional start calendar date (YYYY-MM-DD)
     * @param endDate optional end calendar date (YYYY-MM-DD)
     * @param startInstant optional start instant (ISO-8601)
     * @param endInstant optional end instant (ISO-8601)
     * @param timezone optional timezone identifier (defaults to UTC)
     * @param includePending whether to include pending pipeline items (defaults to false)
     * @param limit maximum number of top client items to return (1..100, defaults to 100)
     * @return 200 OK with client performance ranking
     */
    @GetMapping("/clients")
    public ResponseEntity<ClientPerformanceResponse> getClientPerformance(
            @PathVariable("workspaceId") String workspaceId,
            @RequestParam(name = "currency", required = false) String currency,
            @RequestParam(name = "startDate", required = false) String startDate,
            @RequestParam(name = "endDate", required = false) String endDate,
            @RequestParam(name = "startInstant", required = false) String startInstant,
            @RequestParam(name = "endInstant", required = false) String endInstant,
            @RequestParam(name = "timezone", required = false) String timezone,
            @RequestParam(name = "includePending", required = false, defaultValue = "false") boolean includePending,
            @RequestParam(name = "limit", required = false, defaultValue = "100") Integer limit
    ) {
        String uid = securityUtils.getAuthenticatedUid();
        ClientPerformanceResponse response = analyticsService.getClientPerformance(
                uid, workspaceId, currency, startDate, endDate, startInstant, endInstant, timezone, includePending, limit
        );
        return ResponseEntity.ok(response);
    }
}


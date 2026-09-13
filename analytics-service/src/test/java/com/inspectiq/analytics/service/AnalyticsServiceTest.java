package com.inspectiq.analytics.service;

import com.inspectiq.analytics.dto.AnalyticsDtos.DashboardSummary;
import com.inspectiq.analytics.dto.AnalyticsDtos.DefectDistributionItem;
import com.inspectiq.analytics.dto.AnalyticsDtos.RecentInspection;
import com.inspectiq.analytics.dto.AnalyticsDtos.YieldPoint;
import com.inspectiq.analytics.entity.DefectType;
import com.inspectiq.analytics.entity.InspectionResult;
import com.inspectiq.analytics.entity.ProductionBatch;
import com.inspectiq.analytics.exception.NotFoundException;
import com.inspectiq.analytics.repository.InspectionResultRepository;
import com.inspectiq.analytics.repository.ProductionBatchRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AnalyticsServiceTest {

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final InspectionResultRepository inspectionRepository = mock(InspectionResultRepository.class);
    private final ProductionBatchRepository batchRepository = mock(ProductionBatchRepository.class);
    private final AnalyticsService service = new AnalyticsService(jdbcTemplate, inspectionRepository, batchRepository);

    @Test
    void summaryComputesKpisFromAggregateRow() {
        doReturn(new Object[]{3L, 2L})
                .when(jdbcTemplate).queryForObject(anyString(), any(RowMapper.class), any(Object[].class));
        doReturn(5L).when(batchRepository).count();

        DashboardSummary summary = service.summary();

        assertEquals(3L, summary.totalUnits());
        assertEquals(2L, summary.totalPasses());
        assertEquals(1L, summary.totalFails());
        assertEquals(2 * 100.0 / 3, summary.yieldPercent(), 0.0001);
        assertEquals(5L, summary.totalBatches());
    }

    @Test
    void summaryHasNullYieldWhenThereIsNoData() {
        doReturn(new Object[]{0L, 0L})
                .when(jdbcTemplate).queryForObject(anyString(), any(RowMapper.class), any(Object[].class));
        doReturn(0L).when(batchRepository).count();

        DashboardSummary summary = service.summary();

        assertEquals(0L, summary.totalUnits());
        assertNull(summary.yieldPercent());
    }

    @Test
    void yieldByDayReturnsOnePointPerDay() {
        doReturn(List.of(new YieldPoint("2026-09-13", 10L, 9L, 1L, 90.0)))
                .when(jdbcTemplate).query(anyString(), any(RowMapper.class), any(Object[].class));

        List<YieldPoint> points = service.yieldOverTime("day", null, null);

        assertEquals(1, points.size());
        YieldPoint point = points.get(0);
        assertEquals("2026-09-13", point.bucket());
        assertEquals(10L, point.totalUnits());
        assertEquals(9L, point.passCount());
        assertEquals(1L, point.failCount());
        assertEquals(90.0, point.yieldPercent(), 0.0001);
    }

    @Test
    void yieldAcceptsCaseInsensitiveGrouping() {
        doReturn(List.of(new YieldPoint("B-001", 10L, 9L, 1L, 90.0)))
                .when(jdbcTemplate).query(anyString(), any(RowMapper.class), any(Object[].class));

        List<YieldPoint> points = service.yieldOverTime("By_Batch", null, null);

        assertEquals("B-001", points.get(0).bucket());
    }

    @Test
    void yieldRejectsUnknownGrouping() {
        assertThrows(IllegalArgumentException.class, () -> service.yieldOverTime("weekly", null, null));
    }

    @Test
    void mapYieldRowComputesFailCountAndPercent() throws Exception {
        ResultSet rs = mock(ResultSet.class);
        doReturn("2026-09-13").when(rs).getString("bucket");
        doReturn(10L).when(rs).getLong("total_units");
        doReturn(9L).when(rs).getLong("pass_count");

        YieldPoint point = AnalyticsService.mapYieldRow(rs);

        assertEquals("2026-09-13", point.bucket());
        assertEquals(1L, point.failCount());
        assertEquals(90.0, point.yieldPercent(), 0.0001);
    }

    @Test
    void defectDistributionComputesPercentOfFails() {
        UUID batchId = UUID.randomUUID();
        doReturn(Optional.of(new ProductionBatch("B-001", "Board X", "COMPLETED")))
                .when(batchRepository).findById(batchId);
        doReturn(List.of(new AnalyticsService.DefectCount("SOLDER_BRIDGE", "Excess solder", 2L)))
                .when(jdbcTemplate).query(anyString(), any(RowMapper.class), any(Object[].class));
        doReturn(4L)
                .when(jdbcTemplate).queryForObject(anyString(), any(Class.class), any(Object[].class));

        List<DefectDistributionItem> items = service.defectDistribution(batchId, null, null);

        assertEquals(1, items.size());
        DefectDistributionItem item = items.get(0);
        assertEquals("SOLDER_BRIDGE", item.code());
        assertEquals("Excess solder", item.description());
        assertEquals(2L, item.count());
        assertEquals(50.0, item.percentOfFails(), 0.0001);
    }

    @Test
    void defectDistributionRejectsUnknownBatch() {
        UUID unknownBatchId = UUID.randomUUID();
        doReturn(Optional.empty()).when(batchRepository).findById(unknownBatchId);

        assertThrows(NotFoundException.class,
                () -> service.defectDistribution(unknownBatchId, null, null));
    }

    @Test
    void recentInspectionsMapsEntityAndCapsLimit() {
        ProductionBatch batch = new ProductionBatch("B-013", "Board Z", "COMPLETED");
        DefectType defect = new DefectType("TOMBSTONE", "Component lifted off the pad");
        InspectionResult inspection = new InspectionResult(batch, "FAIL", defect);
        Page<InspectionResult> page = new PageImpl<>(List.of(inspection), PageRequest.of(0, 200), 1);
        doReturn(page).when(inspectionRepository).findRecent(any(Pageable.class));

        List<RecentInspection> recent = service.recentInspections(9999);

        assertEquals(1, recent.size());
        RecentInspection row = recent.get(0);
        assertEquals("B-013", row.batchCode());
        assertEquals("FAIL", row.result());
        assertEquals("TOMBSTONE", row.defectTypeCode());
        verify(inspectionRepository).findRecent(PageRequest.of(0, 200));
    }

    @Test
    void recentInspectionsMapsPassWithoutDefect() {
        ProductionBatch batch = new ProductionBatch("B-014", "Board Z", "COMPLETED");
        InspectionResult inspection = new InspectionResult(batch, "PASS", null);
        Page<InspectionResult> page = new PageImpl<>(List.of(inspection), PageRequest.of(0, 20), 1);
        doReturn(page).when(inspectionRepository).findRecent(any(Pageable.class));

        RecentInspection row = service.recentInspections(20).get(0);

        assertEquals("PASS", row.result());
        assertNull(row.defectTypeCode());
    }
}
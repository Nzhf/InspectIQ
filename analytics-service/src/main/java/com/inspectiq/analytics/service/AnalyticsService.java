package com.inspectiq.analytics.service;

import com.inspectiq.analytics.dto.AnalyticsDtos.DashboardSummary;
import com.inspectiq.analytics.dto.AnalyticsDtos.DefectDistributionItem;
import com.inspectiq.analytics.dto.AnalyticsDtos.RecentInspection;
import com.inspectiq.analytics.dto.AnalyticsDtos.YieldPoint;
import com.inspectiq.analytics.entity.InspectionResult;
import com.inspectiq.analytics.exception.NotFoundException;
import com.inspectiq.analytics.repository.InspectionResultRepository;
import com.inspectiq.analytics.repository.ProductionBatchRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Read-side aggregations for the InspectIQ dashboard.
 *
 * Why JdbcTemplate for the aggregates? The yield/defect queries use Postgres-only
 * features (FILTER aggregates, date_trunc) that JPQL cannot express, and they build
 * their WHERE clause dynamically so absent filters simply drop out of the SQL rather
 * than being bound as NULL parameters. JPA is only used for the simple "latest rows"
 * listing. All access is read-only — this service never writes to the shared database.
 */
@Service
public class AnalyticsService {

    /** Upper bound so a misbehaving client cannot request an unbounded list. */
    private static final int MAX_RESULT_LIMIT = 200;

    private final JdbcTemplate jdbcTemplate;
    private final InspectionResultRepository inspectionRepository;
    private final ProductionBatchRepository batchRepository;

    public AnalyticsService(
            JdbcTemplate jdbcTemplate,
            InspectionResultRepository inspectionRepository,
            ProductionBatchRepository batchRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.inspectionRepository = inspectionRepository;
        this.batchRepository = batchRepository;
    }

    /**
     * Overall quality KPIs across all inspection data. A single aggregate query
     * keeps this cheap even as the table grows.
     */
    public DashboardSummary summary() {
        Object[] totals = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) AS total_units,\n" +
                        "       COUNT(*) FILTER (WHERE result = 'PASS') AS pass_count\n" +
                        "FROM inspection_results",
                SUMMARY_ROW_MAPPER,
                new Object[0]);

        long totalUnits = asLong(totals[0]);
        long passCount = asLong(totals[1]);
        return new DashboardSummary(
                totalUnits,
                passCount,
                totalUnits - passCount,
                yieldPercent(passCount, totalUnits),
                batchRepository.count());
    }

    /**
     * Yield trend line. groupBy accepts "day" (default; one point per calendar day)
     * or "batch" (one point per batch code); anything else is a 400.
     */
    @Transactional(readOnly = true)
    public List<YieldPoint> yieldOverTime(String groupBy, OffsetDateTime from, OffsetDateTime to) {
        String normalized = groupBy == null ? "day" : groupBy.trim().toLowerCase();
        return switch (normalized) {
            case "day", "daily" -> yieldByDay(from, to);
            case "batch", "by_batch" -> yieldByBatch(from, to);
            default -> throw new IllegalArgumentException("groupBy must be 'day' or 'batch'");
        };
    }

    /**
     * Defect-type distribution for failed inspections, optionally scoped to a batch
     * and/or time range. The unknown-batch check returns a clean 404 before any SQL runs.
     */
    @Transactional(readOnly = true)
    public List<DefectDistributionItem> defectDistribution(UUID batchId, OffsetDateTime from, OffsetDateTime to) {
        if (batchId != null && batchRepository.findById(batchId).isEmpty()) {
            throw new NotFoundException("No batch exists with id " + batchId);
        }

        String clause = whereClause(batchId, from, to);
        Object[] args = filterArgs(batchId, from, to);

        String distributionSql = "SELECT dt.code AS code, dt.description AS description, COUNT(*) AS defect_count\n" +
                "FROM inspection_results ir\n" +
                "JOIN defect_types dt ON dt.id = ir.defect_type_id\n" +
                "WHERE ir.result = 'FAIL'" + (clause.isEmpty() ? "" : " AND " + clause) + "\n" +
                "GROUP BY dt.code, dt.description\n" +
                "ORDER BY defect_count DESC, dt.code ASC";
        List<DefectCount> counts = jdbcTemplate.query(distributionSql, DEFECT_ROW_MAPPER, args);

        long totalFails = jdbcTemplate.queryForObject(
                "SELECT COUNT(*)\n" +
                        "FROM inspection_results ir\n" +
                        "WHERE ir.result = 'FAIL'" + (clause.isEmpty() ? "" : " AND " + clause),
                Long.class,
                args);

        return counts.stream()
                .map(count -> new DefectDistributionItem(
                        count.code(), count.description(), count.count(),
                        totalFails > 0 ? count.count() * 100.0 / totalFails : null))
                .toList();
    }

    /** Latest inspections (newest first) for the dashboard table. */
    @Transactional(readOnly = true)
    public List<RecentInspection> recentInspections(int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), MAX_RESULT_LIMIT);
        Page<InspectionResult> page = inspectionRepository.findRecent(PageRequest.of(0, safeLimit));
        return page.getContent().stream().map(this::toRecentInspection).toList();
    }

    private List<YieldPoint> yieldByDay(OffsetDateTime from, OffsetDateTime to) {
        String clause = whereClause(null, from, to);
        String sql = "SELECT to_char(date_trunc('day', ir.inspected_at), 'YYYY-MM-DD') AS bucket,\n" +
                "       COUNT(*) AS total_units,\n" +
                "       COUNT(*) FILTER (WHERE ir.result = 'PASS') AS pass_count\n" +
                "FROM inspection_results ir\n" +
                clause + "\n" +
                "GROUP BY date_trunc('day', ir.inspected_at)\n" +
                "ORDER BY bucket ASC";
        return jdbcTemplate.query(sql, YIELD_ROW_MAPPER, filterArgs(null, from, to));
    }

    private List<YieldPoint> yieldByBatch(OffsetDateTime from, OffsetDateTime to) {
        String clause = whereClause(null, from, to);
        String sql = "SELECT pb.batch_code AS bucket,\n" +
                "       COUNT(*) AS total_units,\n" +
                "       COUNT(*) FILTER (WHERE ir.result = 'PASS') AS pass_count\n" +
                "FROM inspection_results ir\n" +
                "JOIN production_batches pb ON pb.id = ir.batch_id\n" +
                clause + "\n" +
                "GROUP BY pb.batch_code\n" +
                "ORDER BY bucket ASC";
        return jdbcTemplate.query(sql, YIELD_ROW_MAPPER, filterArgs(null, from, to));
    }

    private RecentInspection toRecentInspection(InspectionResult inspection) {
        String defectTypeCode = inspection.getDefectType() != null ? inspection.getDefectType().getCode() : null;
        return new RecentInspection(
                inspection.getId(),
                inspection.getBatch().getId(),
                inspection.getBatch().getBatchCode(),
                inspection.getResult(),
                inspection.getInspectedAt(),
                defectTypeCode);
    }

    // --- SQL fragment builders -------------------------------------------------

    /**
     * Builds the WHERE fragment for the shared inspection filters. Predicates are
     * only emitted for present filters, and filterArgs() must receive the same
     * parameters so the placeholders stay aligned in this order:
     * (1) batch_id = ?, (2) inspected_at >= ?, (3) inspected_at <= ?.
     */
    static String whereClause(UUID batchId, OffsetDateTime from, OffsetDateTime to) {
        List<String> predicates = new ArrayList<>();
        if (batchId != null) {
            predicates.add("ir.batch_id = ?");
        }
        if (from != null) {
            predicates.add("ir.inspected_at >= ?");
        }
        if (to != null) {
            predicates.add("ir.inspected_at <= ?");
        }
        return predicates.isEmpty() ? "" : "WHERE " + String.join(" AND ", predicates);
    }

    /** Placeholder values for whereClause(), in the same order as its predicates. */
    static Object[] filterArgs(UUID batchId, OffsetDateTime from, OffsetDateTime to) {
        List<Object> args = new ArrayList<>();
        if (batchId != null) {
            args.add(batchId);
        }
        if (from != null) {
            args.add(from);
        }
        if (to != null) {
            args.add(to);
        }
        return args.toArray();
    }

    static Double yieldPercent(long passCount, long totalUnits) {
        return totalUnits > 0 ? (passCount * 100.0) / totalUnits : null;
    }

    private static long asLong(Object value) {
        return ((Number) value).longValue();
    }

    // --- Row mappings (SQL-result columns are read here so they are easy to audit) ---

    /** Intermediate projection for defect rows, decoupled from the DTO. */
    record DefectCount(String code, String description, long count) {
    }

    static Object[] mapSummaryRow(ResultSet rs) throws SQLException {
        return new Object[]{rs.getLong(1), rs.getLong(2)};
    }

    static YieldPoint mapYieldRow(ResultSet rs) throws SQLException {
        long totalUnits = rs.getLong("total_units");
        long passCount = rs.getLong("pass_count");
        return new YieldPoint(
                rs.getString("bucket"),
                totalUnits,
                passCount,
                totalUnits - passCount,
                yieldPercent(passCount, totalUnits));
    }

    static DefectCount mapDefectRow(ResultSet rs) throws SQLException {
        return new DefectCount(rs.getString("code"), rs.getString("description"), rs.getLong("defect_count"));
    }

    private static final RowMapper<Object[]> SUMMARY_ROW_MAPPER = (rs, rowNum) -> mapSummaryRow(rs);
    private static final RowMapper<YieldPoint> YIELD_ROW_MAPPER = (rs, rowNum) -> mapYieldRow(rs);
    private static final RowMapper<DefectCount> DEFECT_ROW_MAPPER = (rs, rowNum) -> mapDefectRow(rs);
}
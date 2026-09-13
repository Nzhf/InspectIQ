package com.inspectiq.alert.service;

import com.inspectiq.alert.dto.AlertDtos.WindowYield;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Read-only access to the shared inspection schema (owned by ingestion-service).
 *
 * The rolling window is computed in SQL so only the most recent N rows are ever
 * pulled across the wire: the sub-select grabs the latest result values, then a
 * single pass counts total rows and PASS rows with a Postgres FILTER aggregate.
 * A single-row result always comes back, so an empty table is reported as
 * {@code Optional.empty()} rather than a misleading 0% yield.
 */
@Repository
public class YieldMetricsQuery {

    private final JdbcTemplate jdbcTemplate;

    public YieldMetricsQuery(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Yield over the most recent {@code lookback} inspections.
     *
     * @return the sampled window, or {@link Optional#empty()} when there is no data at all
     */
    public Optional<WindowYield> recentWindowYield(int lookback) {
        Object[] row = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) AS inspected_units,\n" +
                        "       COUNT(*) FILTER (WHERE result = 'PASS') AS pass_count\n" +
                        "FROM (SELECT result FROM inspection_results\n" +
                        "      ORDER BY inspected_at DESC\n" +
                        "      LIMIT ?) recent",
                WINDOW_YIELD_MAPPER,
                lookback);

        long inspectedUnits = ((Number) row[0]).longValue();
        long passCount = ((Number) row[1]).longValue();
        if (inspectedUnits == 0) {
            return Optional.empty();
        }
        return Optional.of(new WindowYield(inspectedUnits, passCount));
    }

    private static final RowMapper<Object[]> WINDOW_YIELD_MAPPER =
            (rs, rowNum) -> new Object[]{rs.getLong("inspected_units"), rs.getLong("pass_count")};
}
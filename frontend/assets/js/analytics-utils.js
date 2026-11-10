/**
 * BusinessOPS Analytics Dashboard - Analytics Utilities
 *
 * Pure utility functions for parameter serialization, date preset calculations,
 * and currency/margin formatting without external network or browser dependencies.
 */

/**
 * Builds a URL query string from a parameters map, omitting empty/undefined values.
 * @param {Record<string, any>} [params={}]
 * @returns {string} e.g. "?currency=USD&includePending=true"
 */
export function buildAnalyticsQueryString(params = {}) {
  const query = new URLSearchParams();

  Object.entries(params).forEach(([key, val]) => {
    if (val !== undefined && val !== null && val !== "") {
      query.set(key, String(val));
    }
  });

  const qs = query.toString();
  return qs ? `?${qs}` : "";
}

/**
 * Format a Date object to YYYY-MM-DD in local timezone
 * @param {Date} date
 * @returns {string}
 */
export function formatDateYMD(date) {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
}

/**
 * Resolves start and end YYYY-MM-DD dates based on selected preset
 * @param {string} preset
 * @returns {{ startDate: string|null, endDate: string|null }}
 */
export function resolvePresetDates(preset) {
  const now = new Date();
  const today = formatDateYMD(now);

  switch (preset) {
    case "THIS_MONTH": {
      const firstDay = new Date(now.getFullYear(), now.getMonth(), 1);
      return { startDate: formatDateYMD(firstDay), endDate: today };
    }
    case "LAST_30_DAYS": {
      const past30 = new Date(now);
      past30.setDate(past30.getDate() - 30);
      return { startDate: formatDateYMD(past30), endDate: today };
    }
    case "QUARTER_TO_DATE": {
      const quarterStartMonth = Math.floor(now.getMonth() / 3) * 3;
      const firstQuarterDay = new Date(now.getFullYear(), quarterStartMonth, 1);
      return { startDate: formatDateYMD(firstQuarterDay), endDate: today };
    }
    case "YEAR_TO_DATE": {
      const firstDayOfYear = new Date(now.getFullYear(), 0, 1);
      return { startDate: formatDateYMD(firstDayOfYear), endDate: today };
    }
    case "ALL_TIME": {
      return { startDate: null, endDate: null };
    }
    default:
      return { startDate: null, endDate: null };
  }
}


/**
 * Suunto implementation of the cross-source training-stress contribution
 * seam introduced in PR-A. Plugs Suunto into {@code DailyTssComposer} so
 * that CTL/ATL/TSB for athletes with both Strava and Suunto data includes
 * both sources' TSS in the daily sum.
 */
package com.zensyra.collector.suunto.trainingload;

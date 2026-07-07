import { useEffect, useState } from 'react'
import { apiFetch } from '../api/client'
import {
  computeInsights,
  insightSentences,
  InsightsResult,
  RacePerformance,
} from './insights'
import RacePerformanceTable from './RacePerformanceTable'

// Races have annual cadence, so the ranges are in years, not days. 12 months is
// the same default window as the /race-performance endpoint (#44) and the
// /race-results endpoint.
const RANGE_OPTIONS = [
  { label: '1y', months: 12 },
  { label: '2y', months: 24 },
  { label: '5y', months: 60 },
] as const
type Months = (typeof RANGE_OPTIONS)[number]['months']

export default function InsightsView({
  athleteId,
  apiKey,
}: {
  athleteId: string
  apiKey: string
}) {
  const [months, setMonths] = useState<Months>(12)
  const [result, setResult] = useState<InsightsResult | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    setLoading(true)
    setError(null)

    const to = new Date()
    const from = new Date()
    from.setMonth(from.getMonth() - months)
    const toStr = to.toISOString().split('T')[0]
    const fromStr = from.toISOString().split('T')[0]

    // Single endpoint → plain .then/.catch (unlike TrendView, which needs
    // allSettled to combine two independent fetches). One failure = one error.
    apiFetch<RacePerformance[]>(
      `/api/v1/athletes/${athleteId}/race-performance?from=${fromStr}&to=${toStr}`,
      apiKey
    )
      .then((data) => setResult(computeInsights(data)))
      .catch((e: Error) => setError(e.message))
      .finally(() => setLoading(false))
  }, [athleteId, apiKey, months])

  const yearsLabel = months === 12 ? '12 months' : `${months / 12} years`

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <h2 className="text-lg font-semibold text-gray-800">Insights</h2>
        <div className="flex gap-2">
          {RANGE_OPTIONS.map((opt) => (
            <button
              key={opt.months}
              onClick={() => setMonths(opt.months)}
              className={`px-3 py-1 rounded text-sm font-medium transition-colors ${
                months === opt.months
                  ? 'bg-blue-600 text-white'
                  : 'bg-gray-100 text-gray-600 hover:bg-gray-200'
              }`}
            >
              {opt.label}
            </button>
          ))}
        </div>
      </div>

      {loading && (
        <div className="flex items-center justify-center h-40 text-gray-500">
          <svg className="animate-spin h-6 w-6 mr-2" viewBox="0 0 24 24" fill="none">
            <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
            <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8v8z" />
          </svg>
          Loading…
        </div>
      )}

      {error && (
        <div className="rounded-md bg-red-50 border border-red-200 p-4 text-red-700 text-sm">
          <strong>Error:</strong> {error}
        </div>
      )}

      {!loading && !error && result && result.totalRaces === 0 && (
        <div className="flex flex-col items-center justify-center h-48 text-center text-gray-500">
          <span className="text-3xl mb-2" role="img" aria-label="race flag">
            🏁
          </span>
          <p className="text-sm font-medium text-gray-600">
            No races in the last {yearsLabel}.
          </p>
          <p className="mt-1 text-xs text-gray-500">
            Log a race result, then come back to see how your training load lined up with it.
          </p>
        </div>
      )}

      {!loading && !error && result && result.totalRaces > 0 && (
        <div className="space-y-4">
          {result.insight ? (
            <div className="rounded-lg border border-violet-200 bg-violet-50 p-4">
              <h3 className="text-sm font-semibold text-violet-800 mb-1">
                Performance vs. training load
              </h3>
              {insightSentences(result.insight).map((sentence, i, all) => (
                <p
                  key={i}
                  className={
                    i === all.length - 1
                      ? 'text-xs text-violet-600 mt-2'
                      : 'text-sm text-violet-900'
                  }
                >
                  {sentence}
                </p>
              ))}
            </div>
          ) : (
            <div className="rounded-lg border border-gray-200 bg-gray-50 p-4">
              <p className="text-sm text-gray-700">
                Showing the {result.totalRaces === 1 ? 'race' : `${result.totalRaces} races`} in this
                window with whatever training-load data is available.
              </p>
              <p className="text-xs text-gray-500 mt-1">
                Add more races with training data to unlock performance insights — the correlation
                appears once at least 3 races have both a recorded goal + finish and race-day
                training load.
              </p>
            </div>
          )}

          <RacePerformanceTable rows={result.rows} />
        </div>
      )}
    </div>
  )
}

import { useEffect, useState } from 'react'
import { apiFetch } from '../api/client'
import DiaryEntryCard, { DiaryEntry } from './DiaryEntryCard'

const DAY_OPTIONS = [30, 60, 90] as const
type Days = (typeof DAY_OPTIONS)[number]

export default function DiaryView({
  athleteId,
  apiKey,
}: {
  athleteId: string
  apiKey: string
}) {
  const [days, setDays] = useState<Days>(90)
  const [data, setData] = useState<DiaryEntry[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    setLoading(true)
    setError(null)

    const to = new Date()
    const from = new Date()
    from.setDate(from.getDate() - days)
    const toStr = to.toISOString().split('T')[0]
    const fromStr = from.toISOString().split('T')[0]

    apiFetch<DiaryEntry[]>(
      `/api/v1/athletes/${athleteId}/training-days?from=${fromStr}&to=${toStr}`,
      apiKey
    )
      .then((items) => setData(items))
      .catch((e: Error) => setError(e.message))
      .finally(() => setLoading(false))
  }, [athleteId, apiKey, days])

  // Endpoint returns ascending by date; show most recent first.
  const entries = [...data].reverse()

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <h2 className="text-lg font-semibold text-gray-800">Training Diary</h2>
        <div className="flex gap-2">
          {DAY_OPTIONS.map((d) => (
            <button
              key={d}
              onClick={() => setDays(d)}
              className={`px-3 py-1 rounded text-sm font-medium transition-colors ${
                days === d
                  ? 'bg-blue-600 text-white'
                  : 'bg-gray-100 text-gray-600 hover:bg-gray-200'
              }`}
            >
              {d}d
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

      {!loading && !error && entries.length === 0 && (
        <div className="flex flex-col items-center justify-center h-48 text-center text-gray-500">
          <span className="text-3xl mb-2" role="img" aria-label="diary">
            📖
          </span>
          <p className="text-sm font-medium text-gray-600">
            No diary entries in the last {days} days.
          </p>
          <p className="mt-1 text-xs text-gray-500">
            Log a training-day via{' '}
            <code className="font-mono bg-gray-100 px-1 rounded">
              POST /api/v1/athletes/{athleteId}/training-days
            </code>
            , then refresh this page.
          </p>
        </div>
      )}

      {!loading && !error && entries.length > 0 && (
        <div className="space-y-3">
          {entries.map((entry) => (
            <DiaryEntryCard key={entry.date} entry={entry} />
          ))}
        </div>
      )}
    </div>
  )
}

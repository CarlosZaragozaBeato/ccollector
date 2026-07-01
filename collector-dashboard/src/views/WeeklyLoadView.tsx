import { useEffect, useState } from 'react'
import {
  BarChart,
  Bar,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ResponsiveContainer,
  Cell,
} from 'recharts'
import { apiFetch } from '../api/client'

interface PeriodSummaryItem {
  periodStart: string
  granularity: string
  totalTss: number | null
}
interface SummaryResponse {
  granularity: string
  items: PeriodSummaryItem[]
}

const fmtPeriod = (d: string) => {
  const dt = new Date(d)
  return `${dt.getMonth() + 1}/${dt.getDate()}`
}

export default function WeeklyLoadView({
  athleteId,
  apiKey,
}: {
  athleteId: string
  apiKey: string
}) {
  const [data, setData] = useState<PeriodSummaryItem[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    const to = new Date()
    const from = new Date()
    from.setDate(from.getDate() - 84) // 12 weeks back
    const toStr = to.toISOString().split('T')[0]
    const fromStr = from.toISOString().split('T')[0]

    apiFetch<SummaryResponse>(
      `/api/v1/athletes/${athleteId}/training-load/summary?granularity=weekly&from=${fromStr}&to=${toStr}`,
      apiKey
    )
      .then((r) => setData(r.items))
      .catch((e: Error) => setError(e.message))
      .finally(() => setLoading(false))
  }, [athleteId, apiKey])

  const maxTss = Math.max(...data.map((d) => d.totalTss ?? 0), 1)

  return (
    <div className="space-y-4">
      <div className="flex items-start justify-between">
        <h2 className="text-lg font-semibold text-gray-800">Weekly Training Load (last 12 weeks)</h2>
      </div>

      {/* TSS approximation note — visible, not in a tooltip */}
      <div className="rounded-md bg-amber-50 border border-amber-200 px-4 py-2 text-sm text-amber-800">
        <strong>Note:</strong> TSS values are approximate — computed as{' '}
        <code className="font-mono text-xs bg-amber-100 px-1 rounded">
          (moving_time_s / 3600) × IF² × 100
        </code>{' '}
        with a fixed intensity factor IF = 0.75. Real power-based TSS requires athlete FTP.
      </div>

      {loading && (
        <div className="flex items-center justify-center h-64 text-gray-500">
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

      {!loading && !error && data.length === 0 && (
        <p className="text-gray-500 text-sm">No training data for the last 12 weeks.</p>
      )}

      {!loading && !error && data.length > 0 && (
        <ResponsiveContainer width="100%" height={320}>
          <BarChart data={data} margin={{ top: 4, right: 16, left: 0, bottom: 0 }}>
            <CartesianGrid strokeDasharray="3 3" stroke="#f0f0f0" />
            <XAxis dataKey="periodStart" tickFormatter={fmtPeriod} tick={{ fontSize: 11 }} />
            <YAxis tick={{ fontSize: 11 }} label={{ value: 'TSS', angle: -90, position: 'insideLeft', fontSize: 11 }} />
            <Tooltip
              labelFormatter={(l: string) => `Week of ${new Date(l).toLocaleDateString()}`}
              formatter={(v: number) => [v != null ? v.toFixed(0) : '–', 'TSS']}
            />
            <Bar dataKey="totalTss" name="TSS" radius={[3, 3, 0, 0]}>
              {data.map((entry, idx) => {
                const ratio = (entry.totalTss ?? 0) / maxTss
                const color =
                  ratio > 0.8
                    ? '#ef4444'
                    : ratio > 0.5
                    ? '#f97316'
                    : '#3b82f6'
                return <Cell key={idx} fill={color} />
              })}
            </Bar>
          </BarChart>
        </ResponsiveContainer>
      )}
    </div>
  )
}

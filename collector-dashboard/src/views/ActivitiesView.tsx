import { useEffect, useState } from 'react'
import { apiFetch } from '../api/client'

interface ActivityItem {
  activityId: string
  name: string
  sportType: string
  distanceMeters: number | null
  movingTimeSecs: number | null
  startDate: string
  totalElevationGain: number | null
  averageHeartrate: number | null
  averageWatts: number | null
}
interface ActivitiesResponse {
  items: ActivityItem[]
  page: number
  size: number
}

const fmtDist = (m: number | null) =>
  m != null ? `${(m / 1000).toFixed(1)} km` : '—'

const fmtTime = (s: number | null) => {
  if (s == null) return '—'
  const h = Math.floor(s / 3600)
  const m = Math.floor((s % 3600) / 60)
  return h > 0 ? `${h}h ${m}m` : `${m}m`
}

const fmtDate = (iso: string) =>
  new Date(iso).toLocaleDateString(undefined, { month: 'short', day: 'numeric', year: 'numeric' })

const sportBadge: Record<string, string> = {
  Run: 'bg-green-100 text-green-800',
  Ride: 'bg-blue-100 text-blue-800',
  Swim: 'bg-cyan-100 text-cyan-800',
  VirtualRide: 'bg-purple-100 text-purple-800',
}

export default function ActivitiesView({
  athleteId,
  apiKey,
}: {
  athleteId: string
  apiKey: string
}) {
  const [data, setData] = useState<ActivityItem[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    apiFetch<ActivitiesResponse>(
      `/api/v1/athletes/${athleteId}/activities?size=20`,
      apiKey
    )
      .then((r) => setData(r.items))
      .catch((e: Error) => setError(e.message))
      .finally(() => setLoading(false))
  }, [athleteId, apiKey])

  return (
    <div className="space-y-4">
      <h2 className="text-lg font-semibold text-gray-800">Recent Activities</h2>

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

      {!loading && !error && data.length === 0 && (
        <p className="text-gray-500 text-sm">No activities found.</p>
      )}

      {!loading && !error && data.length > 0 && (
        <div className="overflow-x-auto rounded-lg border border-gray-200">
          <table className="min-w-full divide-y divide-gray-200 text-sm">
            <thead className="bg-gray-50">
              <tr>
                <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Date</th>
                <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Name</th>
                <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Sport</th>
                <th className="px-4 py-3 text-right text-xs font-medium text-gray-500 uppercase tracking-wider">Distance</th>
                <th className="px-4 py-3 text-right text-xs font-medium text-gray-500 uppercase tracking-wider">Time</th>
                <th className="px-4 py-3 text-right text-xs font-medium text-gray-500 uppercase tracking-wider">Avg Watts</th>
              </tr>
            </thead>
            <tbody className="bg-white divide-y divide-gray-100">
              {data.map((a) => (
                <tr key={a.activityId} className="hover:bg-gray-50 transition-colors">
                  <td className="px-4 py-3 text-gray-500 whitespace-nowrap">{fmtDate(a.startDate)}</td>
                  <td className="px-4 py-3 font-medium text-gray-900">{a.name}</td>
                  <td className="px-4 py-3">
                    <span className={`inline-flex px-2 py-0.5 rounded-full text-xs font-medium ${sportBadge[a.sportType] ?? 'bg-gray-100 text-gray-700'}`}>
                      {a.sportType}
                    </span>
                  </td>
                  <td className="px-4 py-3 text-right text-gray-700">{fmtDist(a.distanceMeters)}</td>
                  <td className="px-4 py-3 text-right text-gray-700">{fmtTime(a.movingTimeSecs)}</td>
                  <td className="px-4 py-3 text-right text-gray-700">
                    {a.averageWatts != null ? `${Math.round(a.averageWatts)} W` : '—'}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}

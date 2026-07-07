import { useEffect, useState } from 'react'
import { DashboardConfig, loadConfig } from './config'
import TrendView from './views/TrendView'
import WeeklyLoadView from './views/WeeklyLoadView'
import ActivitiesView from './views/ActivitiesView'
import DiaryView from './views/DiaryView'
import InsightsView from './views/InsightsView'

type Tab = 'trend' | 'weekly' | 'activities' | 'diary' | 'insights'

const TABS: { id: Tab; label: string }[] = [
  { id: 'trend', label: 'Fitness Trends' },
  { id: 'weekly', label: 'Weekly Load' },
  { id: 'activities', label: 'Recent Activities' },
  { id: 'diary', label: 'Diary' },
  { id: 'insights', label: 'Insights' },
]

export default function App() {
  const [config, setConfig] = useState<DashboardConfig | null>(null)
  const [configError, setConfigError] = useState<string | null>(null)
  const [activeTab, setActiveTab] = useState<Tab>('trend')

  useEffect(() => {
    loadConfig()
      .then(setConfig)
      .catch((e: Error) => setConfigError(e.message))
  }, [])

  return (
    <div className="min-h-screen bg-gray-50">
      <header className="bg-gray-900 text-white">
        <div className="max-w-6xl mx-auto px-4 py-4 flex items-center justify-between">
          <div>
            <h1 className="text-xl font-bold tracking-tight">CCollector Dashboard</h1>
            {config && (
              <p className="text-xs text-gray-400 mt-0.5 font-mono">
                athlete: {config.athleteId}
              </p>
            )}
          </div>
          <a
            href="/q/swagger-ui"
            target="_blank"
            rel="noreferrer"
            className="text-xs text-gray-400 hover:text-white transition-colors"
          >
            API docs ↗
          </a>
        </div>
      </header>

      {configError && (
        <div className="max-w-6xl mx-auto px-4 mt-8">
          <div className="rounded-lg bg-red-50 border border-red-200 p-6 text-red-800">
            <h2 className="font-semibold mb-2">Configuration required</h2>
            <p className="text-sm">{configError}</p>
            <pre className="mt-3 text-xs bg-red-100 rounded p-3 overflow-x-auto">
              {`# 1. Copy the example file
cp collector-dashboard/public/config.json.example \\
   collector-dashboard/public/config.json

# 2. Edit with your values
{
  "athleteId": "<your-athlete-uuid>",
  "apiKey":    "<COLLECTOR_API_KEY value>"
}

# 3. Rebuild
mvn package -DskipTests`}
            </pre>
          </div>
        </div>
      )}

      {!configError && !config && (
        <div className="flex items-center justify-center h-64 text-gray-500">
          <svg className="animate-spin h-6 w-6 mr-2" viewBox="0 0 24 24" fill="none">
            <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
            <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8v8z" />
          </svg>
          Loading configuration…
        </div>
      )}

      {config && (
        <>
          <nav className="bg-white border-b border-gray-200 sticky top-0 z-10">
            <div className="max-w-6xl mx-auto px-4">
              <div className="flex gap-0">
                {TABS.map((tab) => (
                  <button
                    key={tab.id}
                    onClick={() => setActiveTab(tab.id)}
                    className={`px-5 py-3 text-sm font-medium border-b-2 transition-colors ${
                      activeTab === tab.id
                        ? 'border-blue-600 text-blue-600'
                        : 'border-transparent text-gray-600 hover:text-gray-900 hover:border-gray-300'
                    }`}
                  >
                    {tab.label}
                  </button>
                ))}
              </div>
            </div>
          </nav>

          <main className="max-w-6xl mx-auto px-4 py-6">
            <div className="bg-white rounded-xl shadow-sm border border-gray-200 p-6">
              {activeTab === 'trend' && (
                <TrendView athleteId={config.athleteId} apiKey={config.apiKey} />
              )}
              {activeTab === 'weekly' && (
                <WeeklyLoadView athleteId={config.athleteId} apiKey={config.apiKey} />
              )}
              {activeTab === 'activities' && (
                <ActivitiesView athleteId={config.athleteId} apiKey={config.apiKey} />
              )}
              {activeTab === 'diary' && (
                <DiaryView athleteId={config.athleteId} apiKey={config.apiKey} />
              )}
              {activeTab === 'insights' && (
                <InsightsView athleteId={config.athleteId} apiKey={config.apiKey} />
              )}
            </div>
          </main>
        </>
      )}
    </div>
  )
}

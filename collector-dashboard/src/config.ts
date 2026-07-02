export interface DashboardConfig {
  athleteId: string
  apiKey: string
}

let cached: DashboardConfig | null = null

export async function loadConfig(): Promise<DashboardConfig> {
  if (cached) return cached
  const resp = await fetch('/dashboard/config.json')
  if (!resp.ok) throw new Error(`Failed to load config.json: ${resp.status}`)
  cached = (await resp.json()) as DashboardConfig
  if (!cached.athleteId || cached.athleteId === '00000000-0000-0000-0000-000000000000') {
    throw new Error(
      'athleteId is not configured. Copy public/config.json.example to public/config.json and set your values.'
    )
  }
  return cached
}

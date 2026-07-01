export async function apiFetch<T>(path: string, apiKey: string): Promise<T> {
  const resp = await fetch(path, {
    headers: { 'X-API-Key': apiKey },
  })
  if (!resp.ok) {
    let message = `HTTP ${resp.status}`
    try {
      const body = (await resp.json()) as { error?: string }
      if (body.error) message = body.error
    } catch {
      // ignore parse error
    }
    throw new Error(message)
  }
  return resp.json() as Promise<T>
}

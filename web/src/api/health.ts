// Mirrors com.caloriecompanion.shared.api.HealthResponse.
// To be replaced by a client generated from the OpenAPI spec.
export interface HealthResponse {
  status: string
  version: string
  apiVersion: number
}

export async function fetchHealth(): Promise<HealthResponse> {
  const response = await fetch('/api/health')
  if (!response.ok) throw new Error(`HTTP ${response.status}`)
  return (await response.json()) as HealthResponse
}

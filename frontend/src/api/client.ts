import type {
  ActivityFeedResponse,
  ApiError,
  IssueResponse,
  RisksResponse,
  TimelineResponse,
  WorkstreamProgressResponse,
  WorkstreamType,
} from './types'

async function getJson<T>(path: string): Promise<T> {
  const response = await fetch(path)
  if (!response.ok) {
    let message = `Ошибка запроса (${response.status})`
    try {
      const body = (await response.json()) as ApiError
      if (body.error) {
        message = body.error
      }
    } catch {
      // keep status message
    }
    throw new Error(message)
  }
  return response.json() as Promise<T>
}

export function fetchActivity(params?: {
  limit?: number
  workstreamType?: string
  orphans?: boolean
}): Promise<ActivityFeedResponse> {
  const query = new URLSearchParams()
  if (params?.limit != null) query.set('limit', String(params.limit))
  if (params?.workstreamType) query.set('workstreamType', params.workstreamType)
  if (params?.orphans != null) query.set('orphans', String(params.orphans))
  const suffix = query.toString() ? `?${query}` : ''
  return getJson(`/api/activity${suffix}`)
}

export function fetchRisks(params?: { limit?: number }): Promise<RisksResponse> {
  const query = new URLSearchParams()
  if (params?.limit != null) query.set('limit', String(params.limit))
  const suffix = query.toString() ? `?${query}` : ''
  return getJson(`/api/risks${suffix}`)
}

export function fetchWorkstreamTypes(): Promise<WorkstreamType[]> {
  return getJson('/api/workstream-types')
}

export function fetchWorkstreamProgress(): Promise<WorkstreamProgressResponse> {
  return getJson('/api/workstreams/progress')
}

export async function fetchIssue(key: string): Promise<IssueResponse | null> {
  const response = await fetch(`/api/issues/${encodeURIComponent(key)}`)
  if (response.status === 404) {
    return null
  }
  if (!response.ok) {
    let message = `Ошибка запроса (${response.status})`
    try {
      const body = (await response.json()) as ApiError
      if (body.error) {
        message = body.error
      }
    } catch {
      // keep status message
    }
    throw new Error(message)
  }
  return response.json() as Promise<IssueResponse>
}

export function fetchTimeline(key: string): Promise<TimelineResponse> {
  return getJson(`/api/issues/${encodeURIComponent(key)}/timeline`)
}

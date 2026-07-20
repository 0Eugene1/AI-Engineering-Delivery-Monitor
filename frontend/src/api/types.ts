/** Shared API types matching backend Jackson camelCase DTOs. */

export type WorkstreamTypeRef = {
  code: string
  displayName: string
}

export type ActorRef = {
  id: string
  name: string
}

export type WorkstreamType = {
  code: string
  displayName: string
  sortOrder: number
}

export type ActivityEvent = {
  id: string
  occurredAt: string
  type: string
  source: string
  issueKey: string | null
  workstreamType: WorkstreamTypeRef | null
  actor: ActorRef | null
  summary: string
  payload: Record<string, unknown> | null
}

export type ActivityFeedResponse = {
  events: ActivityEvent[]
}

export type RiskItem = {
  code: string
  severity: string
  issueKey: string
  workstreamType: WorkstreamTypeRef | null
  explanation: string
  detectedAt: string
  evidence: Record<string, unknown>
}

export type RisksResponse = {
  risks: RiskItem[]
}

export type WorkstreamProgressItem = {
  workstreamType: WorkstreamTypeRef
  total: number
  merged: number
  percent: number
}

export type WorkstreamProgressResponse = {
  items: WorkstreamProgressItem[]
}

export type IssueResponse = {
  issueKey: string
  summary: string
  status: string
  statusCategory: string
  assigneeUsername: string | null
  assigneeDisplayName: string | null
  issueType: string
  jiraCreated: string | null
  jiraUpdated: string | null
  fixVersions: string[]
  labels: string[]
}

export type TimelineEvent = {
  id: string
  occurredAt: string
  type: string
  workstreamType: WorkstreamTypeRef | null
  actor: ActorRef | null
  summary: string
  payload: Record<string, unknown> | null
}

export type TimelineResponse = {
  issueKey: string
  events: TimelineEvent[]
}

export type ApiError = {
  error: string
  code: string
}

import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import { Layout } from './components/Layout'
import { ActivityFeedPage } from './pages/ActivityFeedPage'
import { DashboardPage } from './pages/DashboardPage'
import { IssueTimelinePage } from './pages/IssueTimelinePage'

export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route element={<Layout />}>
          <Route index element={<DashboardPage />} />
          <Route path="activity" element={<ActivityFeedPage />} />
          <Route path="issues/:key" element={<IssueTimelinePage />} />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Route>
      </Routes>
    </BrowserRouter>
  )
}

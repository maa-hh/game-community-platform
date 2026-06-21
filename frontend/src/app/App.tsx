import { Navigate, Route, Routes } from "react-router-dom";
import { ProtectedRoute } from "../components/ProtectedRoute";
import { AuthPage } from "../pages/AuthPage";
import { AuditReportPage } from "../pages/AuditReportPage";
import { AiAgentModulePage } from "../pages/AiAgentModulePage";
import { ContentArticlePage } from "../pages/ContentArticlePage";
import { ContentEditorPage } from "../pages/ContentEditorPage";
import { ContentHubPage } from "../pages/ContentHubPage";
import { ContentMinePage } from "../pages/ContentMinePage";
import { DashboardPage } from "../pages/DashboardPage";
import { GameAccountModulePage } from "../pages/GameAccountModulePage";
import { HomePage } from "../pages/HomePage";
import { ModulePlanPage } from "../pages/ModulePlanPage";
import { NotificationPage } from "../pages/NotificationPage";
import { ProfilePage } from "../pages/ProfilePage";
import { ShopModulePage } from "../pages/ShopModulePage";
import { SocialModulePage } from "../pages/SocialModulePage";
import { UserHomePage } from "../pages/UserHomePage";
import { UserLookupPage } from "../pages/UserLookupPage";
import { AppShell } from "./AppShell";

export function App() {
  return (
    <Routes>
      <Route path="/" element={<HomePage />} />
      <Route path="/auth" element={<AuthPage />} />
      <Route element={<ProtectedRoute />}>
        <Route path="/app" element={<AppShell />}>
          <Route index element={<Navigate to="/app/dashboard" replace />} />
          <Route path="dashboard" element={<DashboardPage />} />
          <Route path="notifications" element={<NotificationPage />} />
          <Route path="profile" element={<ProfilePage />} />
          <Route path="users" element={<UserLookupPage />} />
          <Route path="users/:id" element={<UserHomePage />} />
          <Route path="users/id/:primaryId" element={<UserHomePage />} />
          <Route path="modules/ai-agent" element={<AiAgentModulePage />} />
          <Route path="modules/content" element={<ContentHubPage />} />
          <Route path="modules/content/new" element={<ContentEditorPage />} />
          <Route path="modules/content/mine" element={<ContentMinePage />} />
          <Route path="modules/content/:articleId/edit" element={<ContentEditorPage />} />
          <Route path="modules/content/:articleId" element={<ContentArticlePage />} />
          <Route path="modules/game-account" element={<GameAccountModulePage />} />
          <Route path="modules/social" element={<SocialModulePage />} />
          <Route path="modules/shop" element={<ShopModulePage />} />
          <Route path="audit/reports" element={<AuditReportPage />} />
          <Route path="modules/:moduleId" element={<ModulePlanPage />} />
        </Route>
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}

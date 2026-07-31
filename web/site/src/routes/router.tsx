import { lazy, Suspense } from 'react';
import { Route, Routes } from 'react-router-dom';
import { SkeletonText } from '../components/ui/Skeleton';
import { AdminLayout } from '../layouts/AdminLayout';
import { AppLayout } from '../layouts/AppLayout';
import { ProviderLayout } from '../layouts/ProviderLayout';
import { PublicLayout } from '../layouts/PublicLayout';
import { ProtectedRoute } from './ProtectedRoute';

// React.lazy + Suspense por rota (§10 — performance): cada página é o seu
// próprio chunk, nunca entra no bundle inicial.
const LandingPage = lazy(() => import('./pages/LandingPage').then((m) => ({ default: m.LandingPage })));
const HowItWorksPage = lazy(() => import('./pages/HowItWorksPage').then((m) => ({ default: m.HowItWorksPage })));
const CategoriesPage = lazy(() => import('./pages/CategoriesPage').then((m) => ({ default: m.CategoriesPage })));
const CategoryLandingPage = lazy(() => import('./pages/CategoryLandingPage').then((m) => ({ default: m.CategoryLandingPage })));
const ProviderSearchPage = lazy(() => import('./pages/ProviderSearchPage').then((m) => ({ default: m.ProviderSearchPage })));
const ProviderProfilePage = lazy(() => import('./pages/ProviderProfilePage').then((m) => ({ default: m.ProviderProfilePage })));
const PricingPage = lazy(() => import('./pages/PricingPage').then((m) => ({ default: m.PricingPage })));
const AboutPage = lazy(() => import('./pages/AboutPage').then((m) => ({ default: m.AboutPage })));
const ContactPage = lazy(() => import('./pages/ContactPage').then((m) => ({ default: m.ContactPage })));
const FaqPage = lazy(() => import('./pages/FaqPage').then((m) => ({ default: m.FaqPage })));
const TermsPage = lazy(() => import('./pages/LegalPage').then((m) => ({ default: m.TermsPage })));
const PrivacyPage = lazy(() => import('./pages/LegalPage').then((m) => ({ default: m.PrivacyPage })));
const LoginPage = lazy(() => import('./pages/LoginPage').then((m) => ({ default: m.LoginPage })));
const RegisterPage = lazy(() => import('./pages/RegisterPage').then((m) => ({ default: m.RegisterPage })));
const NotFoundPage = lazy(() => import('./pages/NotFoundPage').then((m) => ({ default: m.NotFoundPage })));

const NewRequestWizardPage = lazy(() => import('./pages/NewRequestWizardPage').then((m) => ({ default: m.NewRequestWizardPage })));
const CustomerDashboardPage = lazy(() => import('./pages/CustomerDashboardPage').then((m) => ({ default: m.CustomerDashboardPage })));
const MyRequestsPage = lazy(() => import('./pages/MyRequestsPage').then((m) => ({ default: m.MyRequestsPage })));
const RequestDetailPage = lazy(() => import('./pages/RequestDetailPage').then((m) => ({ default: m.RequestDetailPage })));
const ConversationsListPage = lazy(() => import('./pages/ConversationsListPage').then((m) => ({ default: m.ConversationsListPage })));
const ConversationThreadPage = lazy(() => import('./pages/ConversationThreadPage').then((m) => ({ default: m.ConversationThreadPage })));
const NewReviewPage = lazy(() => import('./pages/NewReviewPage').then((m) => ({ default: m.NewReviewPage })));

const ProviderDashboardPage = lazy(() => import('./pages/ProviderDashboardPage').then((m) => ({ default: m.ProviderDashboardPage })));
const ProviderInboxPage = lazy(() => import('./pages/ProviderInboxPage').then((m) => ({ default: m.ProviderInboxPage })));
const ProviderRequestDetailPage = lazy(() => import('./pages/ProviderRequestDetailPage').then((m) => ({ default: m.ProviderRequestDetailPage })));
const ProviderProposalsPage = lazy(() => import('./pages/ProviderProposalsPage').then((m) => ({ default: m.ProviderProposalsPage })));
const ProviderProfileEditPage = lazy(() => import('./pages/ProviderProfileEditPage').then((m) => ({ default: m.ProviderProfileEditPage })));
const ProviderSubscriptionPage = lazy(() => import('./pages/ProviderSubscriptionPage').then((m) => ({ default: m.ProviderSubscriptionPage })));

const AdminProviderApprovalsPage = lazy(() =>
  import('./pages/AdminProviderApprovalsPage').then((m) => ({ default: m.AdminProviderApprovalsPage })),
);

function RouteFallback() {
  return (
    <div className="mx-auto max-w-3xl px-5 py-24">
      <SkeletonText lines={5} />
    </div>
  );
}

export function AppRouter() {
  return (
    <Suspense fallback={<RouteFallback />}>
      <Routes>
        <Route element={<PublicLayout />}>
          <Route index element={<LandingPage />} />
          <Route path="como-funciona" element={<HowItWorksPage />} />
          <Route path="categorias" element={<CategoriesPage />} />
          <Route path="servicos/:slug" element={<CategoryLandingPage />} />
          <Route path="prestadores" element={<ProviderSearchPage />} />
          <Route path="prestadores/:id" element={<ProviderProfilePage />} />
          <Route path="precos" element={<PricingPage />} />
          <Route path="sobre" element={<AboutPage />} />
          <Route path="contactos" element={<ContactPage />} />
          <Route path="faq" element={<FaqPage />} />
          <Route path="termos" element={<TermsPage />} />
          <Route path="privacidade" element={<PrivacyPage />} />
          <Route path="entrar" element={<LoginPage />} />
          <Route path="registar" element={<RegisterPage />} />
          <Route path="pedidos/novo" element={<NewRequestWizardPage />} />
          <Route path="*" element={<NotFoundPage />} />
        </Route>

        <Route
          element={
            <ProtectedRoute roles={['CUSTOMER']}>
              <AppLayout />
            </ProtectedRoute>
          }
        >
          <Route path="painel" element={<CustomerDashboardPage />} />
          <Route path="pedidos" element={<MyRequestsPage />} />
          <Route path="pedidos/:requestId" element={<RequestDetailPage />} />
          <Route path="conversas" element={<ConversationsListPage />} />
          <Route path="conversas/:conversationId" element={<ConversationThreadPage />} />
          <Route path="avaliacoes/nova/:bookingId" element={<NewReviewPage />} />
        </Route>

        <Route
          element={
            <ProtectedRoute roles={['PROVIDER']}>
              <ProviderLayout />
            </ProtectedRoute>
          }
        >
          <Route path="pro" element={<ProviderDashboardPage />} />
          <Route path="pro/pedidos" element={<ProviderInboxPage />} />
          <Route path="pro/pedidos/:requestId" element={<ProviderRequestDetailPage />} />
          <Route path="pro/propostas" element={<ProviderProposalsPage />} />
          <Route path="pro/perfil" element={<ProviderProfileEditPage />} />
          <Route path="pro/subscricao" element={<ProviderSubscriptionPage />} />
        </Route>

        <Route
          element={
            <ProtectedRoute roles={['ADMIN']}>
              <AdminLayout />
            </ProtectedRoute>
          }
        >
          <Route path="admin" element={<AdminProviderApprovalsPage />} />
        </Route>
      </Routes>
    </Suspense>
  );
}

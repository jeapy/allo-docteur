import { Routes } from '@angular/router';
import { authGuard, guestGuard, levelGuard, roleGuard, twoFactorEnrolledGuard, twoFactorPendingGuard } from './core/guards';

export const routes: Routes = [
  {
    path: 'login',
    canActivate: [guestGuard],
    loadComponent: () => import('./features/login/login').then((m) => m.LoginComponent),
  },
  {
    path: 'security/2fa',
    canActivate: [authGuard, roleGuard('ADMIN'), twoFactorPendingGuard],
    loadComponent: () => import('./features/security/two-factor-setup').then((m) => m.TwoFactorSetupComponent),
  },
  {
    path: '',
    canActivate: [authGuard, roleGuard('ADMIN'), twoFactorEnrolledGuard],
    loadComponent: () => import('./layout/shell').then((m) => m.ShellComponent),
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
      {
        path: 'dashboard',
        loadComponent: () => import('./features/dashboard/dashboard').then((m) => m.DashboardComponent),
      },
      {
        path: 'professionals',
        loadComponent: () =>
          import('./features/professionals/professionals-list').then((m) => m.ProfessionalsListComponent),
      },
      {
        path: 'professionals/:id',
        loadComponent: () =>
          import('./features/professionals/professional-detail').then((m) => m.ProfessionalDetailComponent),
      },
      {
        path: 'appointments',
        loadComponent: () =>
          import('./features/appointments/appointments-list').then((m) => m.AppointmentsListComponent),
      },
      {
        path: 'sos',
        loadComponent: () => import('./features/sos/sos-list').then((m) => m.SosListComponent),
      },
      {
        path: 'pharmacies',
        loadComponent: () => import('./features/pharmacies/pharmacies-list').then((m) => m.PharmaciesListComponent),
      },
      {
        path: 'finance',
        canActivate: [levelGuard('SUPER', 'FINANCE')],
        loadComponent: () => import('./features/finance/finance').then((m) => m.FinanceComponent),
      },
      {
        path: 'subscriptions',
        loadComponent: () =>
          import('./features/subscriptions/subscriptions-list').then((m) => m.SubscriptionsListComponent),
      },
      {
        path: 'complaints',
        canActivate: [levelGuard('SUPER', 'SUPPORT')],
        loadComponent: () => import('./features/complaints/complaints-list').then((m) => m.ComplaintsListComponent),
      },
      {
        path: 'complaints/:id',
        canActivate: [levelGuard('SUPER', 'SUPPORT')],
        loadComponent: () => import('./features/complaints/complaint-detail').then((m) => m.ComplaintDetailComponent),
      },
      {
        path: 'reviews',
        canActivate: [levelGuard('SUPER', 'SUPPORT')],
        loadComponent: () => import('./features/reviews/reviews-list').then((m) => m.ReviewsListComponent),
      },
      {
        path: 'partners',
        canActivate: [levelGuard('SUPER', 'SUPPORT')],
        loadComponent: () => import('./features/partners/partners-list').then((m) => m.PartnersListComponent),
      },
      {
        path: 'access-logs',
        canActivate: [levelGuard('SUPER')],
        loadComponent: () => import('./features/access-logs/access-logs-list').then((m) => m.AccessLogsListComponent),
      },
      {
        path: 'users',
        loadComponent: () => import('./features/users/users-list').then((m) => m.UsersListComponent),
      },
    ],
  },
  { path: '**', redirectTo: '' },
];

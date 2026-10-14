import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';
import { AdminLevel, Role } from './models';

export const authGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  return auth.isAuthenticated() ? true : inject(Router).createUrlTree(['/login']);
};

export const guestGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  return auth.isAuthenticated() ? inject(Router).createUrlTree(['/']) : true;
};

export const roleGuard = (...roles: Role[]): CanActivateFn => () => {
  const auth = inject(AuthService);
  return auth.hasRole(...roles) ? true : inject(Router).createUrlTree(['/login']);
};

/** Réserve une route à certains niveaux d'administration (ex. SUPER). */
export const levelGuard = (...levels: AdminLevel[]): CanActivateFn => () => {
  const auth = inject(AuthService);
  const level = auth.user()?.adminLevel;
  return level && levels.includes(level) ? true : inject(Router).createUrlTree(['/']);
};

/** Un administrateur qui doit encore activer la 2FA n'accède qu'à l'écran d'enrôlement. */
export const twoFactorEnrolledGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  return auth.mustEnrollTwoFactor() ? inject(Router).createUrlTree(['/security/2fa']) : true;
};

/** L'écran d'enrôlement n'a de sens que tant que la 2FA manque. */
export const twoFactorPendingGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  return auth.mustEnrollTwoFactor() ? true : inject(Router).createUrlTree(['/']);
};

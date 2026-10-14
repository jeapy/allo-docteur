import {
  ApplicationConfig,
  inject,
  isDevMode,
  provideAppInitializer,
  provideBrowserGlobalErrorListeners,
} from '@angular/core';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideRouter, withComponentInputBinding, withViewTransitions } from '@angular/router';
import { provideIcons } from '@ng-icons/core';
import { provideTransloco } from '@jsverse/transloco';
import { firstValueFrom } from 'rxjs';
import { routes } from './app.routes';
import { AuthService } from './core/auth.service';
import { authInterceptor, errorInterceptor } from './core/interceptors';
import { TranslocoHttpLoader } from './core/transloco';
import { UI_ICONS } from './shared/ui/icons';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes, withComponentInputBinding(), withViewTransitions()),
    provideIcons(UI_ICONS),
    provideHttpClient(withInterceptors([authInterceptor, errorInterceptor])),
    provideTransloco({
      config: { availableLangs: ['fr'], defaultLang: 'fr', reRenderOnLangChange: true, prodMode: !isDevMode() },
      loader: TranslocoHttpLoader,
    }),
    provideAppInitializer(() => firstValueFrom(inject(AuthService).restoreSession())),
  ],
};

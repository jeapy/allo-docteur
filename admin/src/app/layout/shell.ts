import { BreakpointObserver } from '@angular/cdk/layout';
import { Component, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatListModule } from '@angular/material/list';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatToolbarModule } from '@angular/material/toolbar';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { map } from 'rxjs';
import { AuthService } from '../core/auth.service';
import { AdminLevel } from '../core/models';

@Component({
  selector: 'app-shell',
  imports: [
    RouterOutlet, RouterLink, RouterLinkActive, MatSidenavModule, MatToolbarModule,
    MatListModule, MatIconModule, MatButtonModule, TranslocoPipe,
  ],
  templateUrl: './shell.html',
  styleUrl: './shell.scss',
})
export class ShellComponent {
  protected readonly auth = inject(AuthService);
  private readonly bp = inject(BreakpointObserver);
  protected readonly isHandset = toSignal(this.bp.observe('(max-width: 900px)').pipe(map((s) => s.matches)), {
    initialValue: false,
  });
  protected readonly mode = computed(() => (this.isHandset() ? 'over' : 'side'));
  private readonly items: { path: string; icon: string; label: string; levels?: AdminLevel[] }[] = [
    { path: '/dashboard', icon: 'dashboard', label: 'nav.dashboard' },
    { path: '/users', icon: 'group', label: 'nav.users' },
    { path: '/professionals', icon: 'medical_services', label: 'nav.professionals' },
    { path: '/appointments', icon: 'event', label: 'nav.appointments' },
    { path: '/sos', icon: 'emergency', label: 'nav.sos' },
    { path: '/pharmacies', icon: 'local_pharmacy', label: 'nav.pharmacies' },
    { path: '/finance', icon: 'payments', label: 'nav.finance', levels: ['SUPER', 'FINANCE'] },
    { path: '/subscriptions', icon: 'card_membership', label: 'nav.subscriptions' },
    { path: '/access-logs', icon: 'policy', label: 'nav.accessLogs', levels: ['SUPER'] },
  ];
  /** Entrées de menu visibles pour le niveau d'administration connecté. */
  protected readonly menu = computed(() => {
    const level = this.auth.user()?.adminLevel;
    return this.items.filter((i) => !i.levels || (!!level && i.levels.includes(level)));
  });
}

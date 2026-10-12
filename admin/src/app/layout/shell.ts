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
  protected readonly nav = [
    { path: '/dashboard', icon: 'dashboard', label: 'nav.dashboard' },
    { path: '/users', icon: 'group', label: 'nav.users' },
  ];
}

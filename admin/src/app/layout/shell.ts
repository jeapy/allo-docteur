import { BreakpointObserver } from '@angular/cdk/layout';
import { Component, ElementRef, HostListener, afterNextRender, computed, effect, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { NgIcon } from '@ng-icons/core';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { filter, map } from 'rxjs';
import { AuthService } from '../core/auth.service';
import { AdminLevel } from '../core/models';
import { UiButton } from '../shared/ui';

interface MenuItem {
  path: string;
  icon: string;
  label: string;
  levels?: AdminLevel[];
}

const COLLAPSE_KEY = 'ad.sidebar.collapsed';

/**
 * Coquille de l'application : barre latérale rétractable (icônes seules sur bureau, tiroir sur mobile)
 * et en-tête avec recherche, notifications et menu profil. La recherche et les notifications sont
 * visuelles pour l'instant : aucune API admin ne les alimente (voir docs/design-system.md).
 */
@Component({
  selector: 'app-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, NgIcon, UiButton, TranslocoPipe],
  templateUrl: './shell.html',
  styleUrl: './shell.scss',
})
export class ShellComponent {
  protected readonly auth = inject(AuthService);
  private readonly bp = inject(BreakpointObserver);
  private readonly router = inject(Router);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);

  protected readonly isHandset = toSignal(this.bp.observe('(max-width: 899px)').pipe(map((s) => s.matches)), {
    initialValue: false,
  });
  /** Bureau : barre réduite aux icônes. Mobile : tiroir ouvert/fermé (jamais « réduit »). */
  protected readonly collapsed = signal(readCollapsed());
  protected readonly drawerOpen = signal(false);
  protected readonly profileOpen = signal(false);
  /** Les transitions de mise en page ne démarrent qu'après le premier rendu (pas d'animation au chargement). */
  protected readonly ready = signal(false);

  private readonly items: MenuItem[] = [
    { path: '/dashboard', icon: 'lucideLayoutDashboard', label: 'nav.dashboard' },
    { path: '/users', icon: 'lucideUsers', label: 'nav.users' },
    { path: '/professionals', icon: 'lucideStethoscope', label: 'nav.professionals' },
    { path: '/appointments', icon: 'lucideCalendarDays', label: 'nav.appointments' },
    { path: '/sos', icon: 'lucideSiren', label: 'nav.sos' },
    { path: '/facilities', icon: 'lucideHospital', label: 'nav.facilities' },
    { path: '/pharmacies', icon: 'lucidePill', label: 'nav.pharmacies' },
    { path: '/finance', icon: 'lucideWallet', label: 'nav.finance', levels: ['SUPER', 'FINANCE'] },
    { path: '/subscriptions', icon: 'lucideCreditCard', label: 'nav.subscriptions' },
    { path: '/complaints', icon: 'lucideHeadset', label: 'nav.complaints', levels: ['SUPER', 'SUPPORT'] },
    { path: '/reviews', icon: 'lucideStar', label: 'nav.reviews', levels: ['SUPER', 'SUPPORT'] },
    { path: '/partners', icon: 'lucideHandshake', label: 'nav.partners', levels: ['SUPER', 'SUPPORT'] },
    { path: '/access-logs', icon: 'lucideShieldCheck', label: 'nav.accessLogs', levels: ['SUPER'] },
    { path: '/audit', icon: 'lucideScrollText', label: 'nav.audit', levels: ['SUPER'] },
  ];

  /** Entrées de menu visibles pour le niveau d'administration connecté. */
  protected readonly menu = computed(() => {
    const level = this.auth.user()?.adminLevel;
    return this.items.filter((i) => !i.levels || (!!level && i.levels.includes(level)));
  });
  /** Réduite uniquement sur bureau. */
  protected readonly isCollapsed = computed(() => this.collapsed() && !this.isHandset());
  protected readonly initials = computed(() => (this.auth.user()?.email ?? '?').slice(0, 2).toUpperCase());

  constructor() {
    afterNextRender(() => this.ready.set(true));
    // Ferme le tiroir et le menu profil à chaque navigation.
    const navigated = toSignal(
      this.router.events.pipe(filter((e): e is NavigationEnd => e instanceof NavigationEnd)),
      { initialValue: null },
    );
    effect(() => {
      navigated();
      this.drawerOpen.set(false);
      this.profileOpen.set(false);
    });
  }

  protected toggleSidebar(): void {
    if (this.isHandset()) {
      this.drawerOpen.update((v) => !v);
      return;
    }
    this.collapsed.update((v) => !v);
    try {
      localStorage.setItem(COLLAPSE_KEY, String(this.collapsed()));
    } catch {
      /* stockage indisponible : la préférence n'est simplement pas gardée */
    }
  }

  protected toggleProfile(): void {
    this.profileOpen.update((v) => !v);
  }

  @HostListener('document:keydown.escape')
  protected onEscape(): void {
    this.profileOpen.set(false);
    this.drawerOpen.set(false);
  }

  @HostListener('document:click', ['$event'])
  protected onDocumentClick(e: MouseEvent): void {
    const menu = this.host.nativeElement.querySelector('[data-profile]');
    if (this.profileOpen() && menu && !menu.contains(e.target as Node)) this.profileOpen.set(false);
  }
}

function readCollapsed(): boolean {
  try {
    return localStorage.getItem(COLLAPSE_KEY) === 'true';
  } catch {
    return false;
  }
}

import { Component } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';

@Component({
  selector: 'app-dashboard',
  imports: [TranslocoPipe],
  template: `<h1>{{ 'dashboard.title' | transloco }}</h1><p>{{ 'dashboard.soon' | transloco }}</p>`,
})
export class DashboardComponent {}

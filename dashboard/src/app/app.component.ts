import { Component } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatButtonModule } from '@angular/material/button';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, MatToolbarModule, MatButtonModule],
  template: `<mat-toolbar color="primary">
  <span>{{ title }}</span>
  <nav>
    <a mat-button routerLink="/" routerLinkActive="active-link" [routerLinkActiveOptions]="{ exact: true }">Overview</a>
    <a mat-button routerLink="/batches" routerLinkActive="active-link">Batches</a>
  </nav>
</mat-toolbar>
<main class="content">
  <router-outlet />
</main>`,
})
export class App {
  protected readonly title = 'InspectIQ';
}

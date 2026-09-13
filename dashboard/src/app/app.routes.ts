import { Routes } from '@angular/router';
import { OverviewComponent } from './features/overview/overview.component';
import { BatchDetailComponent } from './features/batches/batch-detail.component';
import { BatchesComponent } from './features/batches/batches.component';

export const routes: Routes = [
  { path: '', component: OverviewComponent },
  { path: 'batches', component: BatchesComponent },
  { path: 'batches/:id', component: BatchDetailComponent },
  { path: '**', redirectTo: '' },
];


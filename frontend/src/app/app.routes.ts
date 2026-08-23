import { Routes } from '@angular/router';

export const routes: Routes = [
  {
    path: '',
    redirectTo: 'dashboard',
    pathMatch: 'full'
  },
  {
    path: 'dashboard',
    loadComponent: () =>
      import('./components/dashboard/dashboard.component').then(m => m.DashboardComponent)
  },
  {
    path: 'derogation',
    loadComponent: () =>
      import('./components/derogation-form/derogation-form.component').then(m => m.DerogationFormComponent)
  },
  {
    path: 'import',
    loadComponent: () =>
      import('./components/csv-upload/csv-upload.component').then(m => m.CsvUploadComponent)
  },
  {
    path: 'validation',
    loadComponent: () =>
      import('./components/derogation-validation/derogation-validation.component').then(m => m.DerogationValidationComponent)
  },
  {
    path: '**',
    redirectTo: 'dashboard'
  }
];

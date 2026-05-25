import { Routes } from '@angular/router';

export const routes: Routes = [
  {
    path: '',
    loadComponent: () =>
      import('./projects/project-list').then(m => m.ProjectList)
  },
  { path: '**', redirectTo: '' }
];

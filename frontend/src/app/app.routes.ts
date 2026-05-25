import { Routes } from '@angular/router';

export const routes: Routes = [
  {
    path: '',
    title: 'Projects',
    loadComponent: () =>
      import('./projects/project-list').then(m => m.ProjectList)
  },
  { path: '**', redirectTo: '' }
];

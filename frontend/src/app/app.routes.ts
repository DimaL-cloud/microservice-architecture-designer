import { Routes } from '@angular/router';

export const routes: Routes = [
  {
    path: '',
    title: 'Projects',
    loadComponent: () => import('./projects/project-list').then(m => m.ProjectList)
  },
  {
    path: 'projects/new',
    title: 'New project',
    loadComponent: () => import('./projects/project-create').then(m => m.ProjectCreate)
  },
  { path: '**', redirectTo: '' }
];

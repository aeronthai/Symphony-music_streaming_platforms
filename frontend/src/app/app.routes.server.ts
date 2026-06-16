import { RenderMode, ServerRoute } from '@angular/ssr';

export const serverRoutes: ServerRoute[] = [
  { path: 'singer/:id', renderMode: RenderMode.Server },
  { path: 'song/:id',   renderMode: RenderMode.Server },
  { path: '**',         renderMode: RenderMode.Server }
];
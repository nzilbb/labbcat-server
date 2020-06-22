import { NgModule } from '@angular/core';
import { Routes, RouterModule } from '@angular/router';

import { AdminCorporaComponent } from './admin-corpora/admin-corpora.component';
import { AdminProjectsComponent } from './admin-projects/admin-projects.component';
import { AboutComponent } from './about/about.component';

const routes: Routes = [
    { path: 'about', component: AboutComponent },
    { path: 'admin/corpora', component: AdminCorporaComponent },
    { path: 'admin/projects', component: AdminProjectsComponent },
];

@NgModule({
  imports: [RouterModule.forRoot(routes)],
  exports: [RouterModule]
})
export class AppRoutingModule { }

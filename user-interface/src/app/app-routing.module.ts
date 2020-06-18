import { NgModule } from '@angular/core';
import { Routes, RouterModule } from '@angular/router';

import { AdminCorporaComponent } from './admin-corpora/admin-corpora.component';
import { AboutComponent } from './about/about.component';

const routes: Routes = [
    { path: 'about', component: AboutComponent },
    { path: 'admin/corpuses', component: AdminCorporaComponent },
];

@NgModule({
  imports: [RouterModule.forRoot(routes)],
  exports: [RouterModule]
})
export class AppRoutingModule { }

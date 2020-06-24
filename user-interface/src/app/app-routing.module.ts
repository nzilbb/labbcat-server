import { NgModule } from '@angular/core';
import { Routes, RouterModule } from '@angular/router';

import { AdminCorporaComponent } from './admin-corpora/admin-corpora.component';
import { AdminProjectsComponent } from './admin-projects/admin-projects.component';
import { AdminTracksComponent } from './admin-tracks/admin-tracks.component';
import { AdminRolesComponent } from './admin-roles/admin-roles.component';
import { AdminRolePermissionsComponent } from './admin-role-permissions/admin-role-permissions.component';
import { AboutComponent } from './about/about.component';
import { LoginComponent } from './login/login.component';

const routes: Routes = [
    { path: 'about', component: AboutComponent },
    { path: 'login', component: LoginComponent },
    { path: 'admin/corpora', component: AdminCorporaComponent },
    { path: 'admin/projects', component: AdminProjectsComponent },
    { path: 'admin/tracks', component: AdminTracksComponent },
    { path: 'admin/roles', component: AdminRolesComponent },
    { path: 'admin/roles/:role_id/permissions', component: AdminRolePermissionsComponent },
];

@NgModule({
  imports: [RouterModule.forRoot(routes)],
  exports: [RouterModule]
})
export class AppRoutingModule { }

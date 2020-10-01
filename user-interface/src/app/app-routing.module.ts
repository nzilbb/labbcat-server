import { NgModule } from '@angular/core';
import { Routes, RouterModule } from '@angular/router';

import { AdminTranscriptTypesComponent } from './admin-transcript-types/admin-transcript-types.component';
import { AdminCorporaComponent } from './admin-corpora/admin-corpora.component';
import { AdminProjectsComponent } from './admin-projects/admin-projects.component';
import { AdminTracksComponent } from './admin-tracks/admin-tracks.component';
import { AdminRolesComponent } from './admin-roles/admin-roles.component';
import { AdminRolePermissionsComponent
       } from './admin-role-permissions/admin-role-permissions.component';
import { AdminSystemAttributesComponent
       } from './admin-system-attributes/admin-system-attributes.component';
import { AdminAnnotatorComponent } from './admin-annotator/admin-annotator.component';
import { AdminAnnotatorsComponent } from './admin-annotators/admin-annotators.component';
import { AdminAnnotatorTasksComponent } from './admin-annotator-tasks/admin-annotator-tasks.component';
import { AdminAnnotatorTaskParametersComponent } from './admin-annotator-task-parameters/admin-annotator-task-parameters.component';
import { AdminAnnotatorExtComponent } from './admin-annotator-ext/admin-annotator-ext.component';
import { AboutComponent } from './about/about.component';
import { LoginComponent } from './login/login.component';
import { MatchesComponent } from './matches/matches.component';
import { PendingChangesGuard } from './pending-changes.guard';

const routes: Routes = [
    { path: 'about', component: AboutComponent },
    { path: 'login', component: LoginComponent },
    { path: 'matches', component: MatchesComponent },
    
    { path: 'admin/transcriptTypes', component: AdminTranscriptTypesComponent,
      canDeactivate: [PendingChangesGuard] },
    { path: 'admin/corpora', component: AdminCorporaComponent,
      canDeactivate: [PendingChangesGuard] },
    { path: 'admin/projects', component: AdminProjectsComponent,
      canDeactivate: [PendingChangesGuard] },
    { path: 'admin/tracks', component: AdminTracksComponent,
      canDeactivate: [PendingChangesGuard] },
    { path: 'admin/roles', component: AdminRolesComponent,
      canDeactivate: [PendingChangesGuard] },
    { path: 'admin/roles/:role_id/permissions', component: AdminRolePermissionsComponent,
      canDeactivate: [PendingChangesGuard] },
    { path: 'admin/attributes', component: AdminSystemAttributesComponent,
      canDeactivate: [PendingChangesGuard] },
    { path: 'admin/annotator', component: AdminAnnotatorComponent },
    { path: 'admin/annotator/:annotatorId/tasks', component: AdminAnnotatorTasksComponent,
      canDeactivate: [PendingChangesGuard] },
    { path: 'admin/annotator/:annotatorId/tasks/:taskId', component: AdminAnnotatorTaskParametersComponent },
    { path: 'admin/annotator/:annotatorId/ext', component: AdminAnnotatorExtComponent },
    { path: 'admin/annotators', component: AdminAnnotatorsComponent },
];

@NgModule({
  imports: [RouterModule.forRoot(routes)],
  exports: [RouterModule]
})
export class AppRoutingModule { }

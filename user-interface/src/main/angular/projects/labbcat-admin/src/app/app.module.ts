import { BrowserModule } from '@angular/platform-browser';
import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';
import { HttpClientModule } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { CKEditorModule } from '@ckeditor/ckeditor5-angular';

import { environment } from '../environments/environment';

import { AppComponent } from './app.component';
import { LabbcatCommonModule, TaskComponent, PendingChangesGuard, AboutComponent, LoginComponent } from 'labbcat-common';
import { AdminCorporaComponent } from './admin-corpora/admin-corpora.component';
import { AdminMenuComponent } from './admin-menu/admin-menu.component';
import { AdminProjectsComponent } from './admin-projects/admin-projects.component';
import { AdminTracksComponent } from './admin-tracks/admin-tracks.component';
import { AdminRolesComponent } from './admin-roles/admin-roles.component';
import { AdminRolePermissionsComponent } from './admin-role-permissions/admin-role-permissions.component';
import { AdminRoleUsersComponent } from './admin-role-users/admin-role-users.component';
import { AdminSystemAttributesComponent } from './admin-system-attributes/admin-system-attributes.component';
import { AdminTranscriptTypesComponent } from './admin-transcript-types/admin-transcript-types.component';
import { AdminAnnotatorComponent } from './admin-annotator/admin-annotator.component';
import { AdminAnnotatorsComponent } from './admin-annotators/admin-annotators.component';
import { AdminAnnotatorTasksComponent } from './admin-annotator-tasks/admin-annotator-tasks.component';
import { AdminAnnotatorTaskParametersComponent } from './admin-annotator-task-parameters/admin-annotator-task-parameters.component';
import { AdminAnnotatorExtComponent } from './admin-annotator-ext/admin-annotator-ext.component';
import { AdminUsersComponent } from './admin-users/admin-users.component';
import { AdminChangePasswordComponent } from './admin-change-password/admin-change-password.component';
import { AdminLayersComponent } from './admin-layers/admin-layers.component';
import { AdminLayerLabelsComponent } from './admin-layer-labels/admin-layer-labels.component';
import { AdminAttributesComponent } from './admin-attributes/admin-attributes.component';
import { AdminCategoriesComponent } from './admin-categories/admin-categories.component';
import { AdminAgreementComponent } from './admin-agreement/admin-agreement.component';

@NgModule({
    declarations: [
        AppComponent,
        AdminCorporaComponent,
        AdminMenuComponent,
        AdminProjectsComponent,
        AdminTracksComponent,
        AdminRolesComponent,
        AdminRolePermissionsComponent,
        AdminSystemAttributesComponent,
        AdminTranscriptTypesComponent,
        AdminAnnotatorComponent,
        AdminAnnotatorsComponent,
        AdminAnnotatorTasksComponent,
        AdminAnnotatorTaskParametersComponent,
        AdminAnnotatorExtComponent,
        AdminRoleUsersComponent,
        AdminUsersComponent,
        AdminChangePasswordComponent,
        AdminLayersComponent,
        AdminLayerLabelsComponent,
        AdminAttributesComponent,
        AdminCategoriesComponent,
        AdminAgreementComponent
    ],
    imports: [
        BrowserModule,
        HttpClientModule,
        CKEditorModule,
        RouterModule.forRoot([
            { path: 'login', component: LoginComponent },
            { path: 'task', component: TaskComponent },
            
            { path: 'admin/transcriptTypes', component: AdminTranscriptTypesComponent,
              canDeactivate: [PendingChangesGuard] },
            { path: 'admin/corpora', component: AdminCorporaComponent,
              canDeactivate: [PendingChangesGuard] },
            { path: 'admin/projects', component: AdminProjectsComponent,
              canDeactivate: [PendingChangesGuard] },
            { path: 'admin/tracks', component: AdminTracksComponent,
              canDeactivate: [PendingChangesGuard] },
            { path: 'admin/users', component: AdminUsersComponent,
              canDeactivate: [PendingChangesGuard] },
            { path: 'admin/users/:user', component: AdminChangePasswordComponent,
              canDeactivate: [PendingChangesGuard] },
            { path: 'admin/roles', component: AdminRolesComponent,
              canDeactivate: [PendingChangesGuard] },
            { path: 'admin/roles/:role_id/permissions', component: AdminRolePermissionsComponent,
              canDeactivate: [PendingChangesGuard] },
            { path: 'admin/roles/:role_id/users', component: AdminRoleUsersComponent,
              canDeactivate: [PendingChangesGuard] },
            { path: 'admin/attributes', component: AdminSystemAttributesComponent,
              canDeactivate: [PendingChangesGuard] },
            { path: 'admin/annotator', component: AdminAnnotatorComponent },
            { path: 'admin/annotator/:annotatorId/tasks', component: AdminAnnotatorTasksComponent,
              canDeactivate: [PendingChangesGuard] },
            { path: 'admin/annotator/:annotatorId/tasks/:taskId', component: AdminAnnotatorTaskParametersComponent },
            { path: 'admin/annotator/:annotatorId/ext', component: AdminAnnotatorExtComponent },
            { path: 'admin/annotators', component: AdminAnnotatorsComponent },
            { path: 'admin/layers/validLabels/:layerId', component: AdminLayerLabelsComponent,
              canDeactivate: [PendingChangesGuard] },
            { path: 'admin/layers/validLabels', component: AdminLayerLabelsComponent,
              canDeactivate: [PendingChangesGuard] },
            { path: 'admin/layers/:scope', component: AdminLayersComponent},
            { path: 'admin/attributes/:scope', component: AdminAttributesComponent},
            { path: 'admin/categories/:scope', component: AdminCategoriesComponent},
            { path: 'admin/agreement', component: AdminAgreementComponent,
              canDeactivate: [PendingChangesGuard]},
        ]), // TODO add { path: '**', component: PageNotFoundComponent }
        FormsModule,
        LabbcatCommonModule.forRoot(environment)
    ],
    providers: [],
    bootstrap: [AppComponent]
})
export class AppModule { }

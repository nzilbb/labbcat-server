import { BrowserModule } from '@angular/platform-browser';
import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';
import { HttpClientModule } from '@angular/common/http';
import { FormsModule } from '@angular/forms';

import { PendingChangesGuard } from './pending-changes.guard';
import { AppComponent } from './app.component';
import { WaitComponent } from './wait/wait.component';
import { AdminCorporaComponent } from './admin-corpora/admin-corpora.component';
import { ViewMenuComponent } from './view-menu/view-menu.component';
import { EditMenuComponent } from './edit-menu/edit-menu.component';
import { AdminMenuComponent } from './admin-menu/admin-menu.component';
import { MessagesComponent } from './messages/messages.component';
import { AboutComponent } from './about/about.component';
import { AdminProjectsComponent } from './admin-projects/admin-projects.component';
import { ButtonComponent } from './button/button.component';
import { LoginComponent } from './login/login.component';
import { AdminTracksComponent } from './admin-tracks/admin-tracks.component';
import { AdminRolesComponent } from './admin-roles/admin-roles.component';
import { AdminRolePermissionsComponent } from './admin-role-permissions/admin-role-permissions.component';
import { AdminSystemAttributesComponent } from './admin-system-attributes/admin-system-attributes.component';
import { MatchesComponent } from './matches/matches.component';
import { KeepAliveComponent } from './keep-alive/keep-alive.component';
import { LayerCheckboxesComponent } from './layer-checkboxes/layer-checkboxes.component';
import { GroupedCheckboxComponent } from './grouped-checkbox/grouped-checkbox.component';
import { AdminTranscriptTypesComponent } from './admin-transcript-types/admin-transcript-types.component';
import { AdminAnnotatorComponent } from './admin-annotator/admin-annotator.component';
import { AdminAnnotatorsComponent } from './admin-annotators/admin-annotators.component';
import { AdminAnnotatorTasksComponent } from './admin-annotator-tasks/admin-annotator-tasks.component';
import { AdminAnnotatorTaskParametersComponent } from './admin-annotator-task-parameters/admin-annotator-task-parameters.component';
import { LinkComponent } from './link/link.component';
import { AdminAnnotatorExtComponent } from './admin-annotator-ext/admin-annotator-ext.component';
import { UrlEncodePipe } from './url-encode.pipe';
import { AdminInfoComponent } from './admin-info/admin-info.component';

@NgModule({
    declarations: [
        AppComponent,
        WaitComponent,
        AdminCorporaComponent,
        ViewMenuComponent,
        EditMenuComponent,
        AdminMenuComponent,
        MessagesComponent,
        AboutComponent,
        AdminProjectsComponent,
        ButtonComponent,
        LoginComponent,
        AdminTracksComponent,
        AdminRolesComponent,
        AdminRolePermissionsComponent,
        AdminSystemAttributesComponent,
        MatchesComponent,
        KeepAliveComponent,
        LayerCheckboxesComponent,
        GroupedCheckboxComponent,
        AdminTranscriptTypesComponent,
        AdminAnnotatorComponent,
        AdminAnnotatorsComponent,
        AdminAnnotatorTasksComponent,
        AdminAnnotatorTaskParametersComponent,
        LinkComponent,
        AdminAnnotatorExtComponent,
        UrlEncodePipe,
        AdminInfoComponent
    ],
    imports: [
        BrowserModule,
        HttpClientModule,
        RouterModule.forRoot([
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
        ]),
        FormsModule
    ],
    providers: [],
    bootstrap: [AppComponent]
})
export class AppModule { }

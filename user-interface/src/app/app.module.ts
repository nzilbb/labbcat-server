import { BrowserModule } from '@angular/platform-browser';
import { NgModule } from '@angular/core';
import { HttpClientModule } from '@angular/common/http';
import { FormsModule } from '@angular/forms';

import { AppRoutingModule } from './app-routing.module';
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
        AdminTranscriptTypesComponent
    ],
    imports: [
        BrowserModule,
        HttpClientModule,
        AppRoutingModule,
      FormsModule
    ],
    providers: [],
    bootstrap: [AppComponent]
})
export class AppModule { }

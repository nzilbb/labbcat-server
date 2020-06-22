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

@NgModule({
    declarations: [
        AppComponent,
        WaitComponent,
        AdminCorporaComponent,
        ViewMenuComponent,
        EditMenuComponent,
        AdminMenuComponent,
        MessagesComponent,
        AboutComponent
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
import { BrowserModule } from '@angular/platform-browser';
import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';
import { HttpClientModule } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { environment } from '../environments/environment';

import { AppComponent } from './app.component';
import { LabbcatCommonModule, TaskComponent, GroupedCheckboxComponent, PendingChangesGuard, AboutComponent } from 'labbcat-common';
import { MatchesComponent } from './matches/matches.component';
import { ParticipantsComponent } from './participants/participants.component';
import { PraatComponent } from './praat/praat.component';

@NgModule({
  declarations: [
      AppComponent,
      MatchesComponent,
      ParticipantsComponent,
      PraatComponent
  ],
  imports: [
      BrowserModule,
      HttpClientModule,
      RouterModule.forRoot([
          { path: 'about', component: AboutComponent },
          { path: 'matches', component: MatchesComponent },
          { path: 'task', component: TaskComponent },
          { path: 'praat', component: PraatComponent },
          { path: 'participants', component: ParticipantsComponent },
      ]), // TODO add { path: '**', component: PageNotFoundComponent }
      FormsModule,
      LabbcatCommonModule.forRoot(environment)
  ],
  providers: [],
  bootstrap: [AppComponent]
})
export class AppModule { }

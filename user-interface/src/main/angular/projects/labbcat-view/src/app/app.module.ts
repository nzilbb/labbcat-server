import { BrowserModule } from '@angular/platform-browser';
import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';
import { HttpClientModule } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { environment } from '../environments/environment';

import { AppComponent } from './app.component';
import { LabbcatCommonModule, TaskComponent, GroupedCheckboxComponent, PendingChangesGuard, AboutComponent } from 'labbcat-common';
import { MatchesComponent } from './matches/matches.component';
import { MatchesUploadComponent } from './matches-upload/matches-upload.component';
import { ParticipantsComponent } from './participants/participants.component';
import { PraatComponent } from './praat/praat.component';
import { AnnotationIntervalsComponent } from './annotation-intervals/annotation-intervals.component';
import { TranscriptsComponent } from './transcripts/transcripts.component';
import { ParticipantComponent } from './participant/participant.component';
import { TranscriptAttributesComponent } from './transcript-attributes/transcript-attributes.component';
import { SearchComponent } from './search/search.component';
import { SearchMatrixComponent } from './search-matrix/search-matrix.component';
import { AllUtterancesComponent } from './all-utterances/all-utterances.component';
import { TranscriptComponent } from './transcript/transcript.component';
import { LoginComponent } from './login/login.component';
import { PasswordComponent } from './password/password.component';
import { MenuExtractComponent } from './menu-extract/menu-extract.component';
import { VersionComponent } from './version/version.component';
import { CreditsComponent } from './credits/credits.component';
import { StatisticsComponent } from './statistics/statistics.component';
import { HomeComponent } from './home/home.component';
import { DictionariesComponent } from './dictionaries/dictionaries.component';
import { DictionaryComponent } from './dictionary/dictionary.component';
import { CorpusComponent } from './corpus/corpus.component';
import { TasksComponent } from './tasks/tasks.component';
import { TreeComponent } from './tree/tree.component';
import { EpisodeDocumentsComponent } from './episode-documents/episode-documents.component';

@NgModule({
  declarations: [
      AppComponent,
      MatchesComponent,
      MatchesUploadComponent,
      ParticipantsComponent,
      PraatComponent,
      AnnotationIntervalsComponent,
      TranscriptsComponent,
      ParticipantComponent,
      TranscriptAttributesComponent,
      SearchComponent,
      SearchMatrixComponent,
      AllUtterancesComponent,
      TranscriptComponent,
      LoginComponent,
      PasswordComponent,
      MenuExtractComponent,
      VersionComponent,
      CreditsComponent,
      StatisticsComponent,
      HomeComponent,
      DictionariesComponent,
      DictionaryComponent,
      CorpusComponent,
      TasksComponent,
      TreeComponent,
      EpisodeDocumentsComponent
  ],
  imports: [
      BrowserModule,
      HttpClientModule,
      RouterModule.forRoot([
          { path: '', component: HomeComponent },
          { path: 'login', component: LoginComponent },
          { path: 'login-error', component: LoginComponent },
          { path: 'version', component: VersionComponent },
          { path: 'credits', component: CreditsComponent },
          { path: 'statistics', component: StatisticsComponent },
          { path: 'password', component: PasswordComponent },
          { path: 'search', component: SearchComponent },
          { path: 'matches', component: MatchesComponent },
          { path: 'matches/upload', component: MatchesUploadComponent },
          { path: 'praat', component: PraatComponent },
          { path: 'annotation/intervals', component: AnnotationIntervalsComponent },
          { path: 'participants', component: ParticipantsComponent },
          { path: 'participant', component: ParticipantComponent },
          { path: 'transcripts', component: TranscriptsComponent },
          { path: 'transcript/attributes', component: TranscriptAttributesComponent },
          { path: 'allUtterances', component: AllUtterancesComponent },
          { path: 'all-utterances', component: AllUtterancesComponent },
          { path: 'transcript', component: TranscriptComponent },
          { path: 'text', component: TranscriptComponent },
          { path: 'extract', component: MenuExtractComponent },
          { path: 'dictionaries', component: DictionariesComponent },
          { path: 'dictionary', component: DictionaryComponent },
          { path: 'corpus', component: CorpusComponent },
          { path: 'corpus/:id', component: CorpusComponent },
          { path: 'tasks', component: TasksComponent },
          { path: 'task', component: TaskComponent },
          { path: 'task/:threadId', component: TaskComponent },
          { path: 'tree', component: TreeComponent },
          { path: 'episode/documents', component: EpisodeDocumentsComponent },
      ]), // TODO add { path: '**', component: PageNotFoundComponent }
      FormsModule,
      LabbcatCommonModule.forRoot(environment)
  ],
  providers: [],
  bootstrap: [AppComponent]
})
export class AppModule { }

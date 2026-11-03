import { BrowserModule } from '@angular/platform-browser';
import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';
import { HttpClientModule } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { environment } from '../environments/environment';

import { AppComponent } from './app.component';
import { LabbcatCommonModule, PendingChangesGuard } from 'labbcat-common';
import { MissingAnnotationsComponent } from './missing-annotations/missing-annotations.component';
import { ParticipantComponent } from './participant/participant.component';
import { TranscriptAttributesComponent } from './transcript-attributes/transcript-attributes.component';
import { TranscriptMediaComponent } from './transcript-media/transcript-media.component';
import { TranscriptUploadComponent } from './transcript-upload/transcript-upload.component';
import { FragmentUploadComponent } from './fragment-upload/fragment-upload.component';
import { EpisodeDocumentsComponent } from './episode-documents/episode-documents.component';
import { MenuUploadComponent } from './menu-upload/menu-upload.component';
import { ParticipantsAttributesUploadComponent } from './participants-attributes-upload/participants-attributes-upload.component';
import { TranscriptsAttributesUploadComponent } from './transcripts-attributes-upload/transcripts-attributes-upload.component';
import { AnnotationsUploadComponent } from './annotations-upload/annotations-upload.component';
import { TranscriptsLayersRegenerateComponent } from './transcripts-layers-regenerate/transcripts-layers-regenerate.component';
import { ParticipantsLayersRegenerateComponent } from './participants-layers-regenerate/participants-layers-regenerate.component';

@NgModule({
    declarations: [
        AppComponent,
        MissingAnnotationsComponent,
        ParticipantComponent,
        TranscriptAttributesComponent,
        TranscriptMediaComponent,
        TranscriptUploadComponent,
        FragmentUploadComponent,
        EpisodeDocumentsComponent,
        ParticipantsAttributesUploadComponent,
        TranscriptsAttributesUploadComponent,
        AnnotationsUploadComponent,
        TranscriptsLayersRegenerateComponent,
        ParticipantsLayersRegenerateComponent,
        MenuUploadComponent
    ],
    imports: [
        BrowserModule,
        HttpClientModule,
        RouterModule.forRoot([
            { path: 'edit/missingAnnotations', component: MissingAnnotationsComponent,
              canDeactivate: [PendingChangesGuard]},
            { path: 'edit/participant', component: ParticipantComponent,
              canDeactivate: [PendingChangesGuard]},
            { path: 'edit/transcript/attributes', component: TranscriptAttributesComponent,
              canDeactivate: [PendingChangesGuard]},
            { path: 'edit/transcript/media', component: TranscriptMediaComponent,
              canDeactivate: [PendingChangesGuard]},
            { path: 'edit/transcript/upload', component: TranscriptUploadComponent,
              canDeactivate: [PendingChangesGuard]},
            { path: 'edit/fragment/upload', component: FragmentUploadComponent,
              canDeactivate: [PendingChangesGuard]},
            { path: 'edit/episode/documents', component: EpisodeDocumentsComponent,
              canDeactivate: [PendingChangesGuard]},
            { path: 'edit/upload', component: MenuUploadComponent},
            { path: 'edit/participants/attributes/upload',
              component: ParticipantsAttributesUploadComponent},
            { path: 'edit/transcripts/attributes/upload',
              component: TranscriptsAttributesUploadComponent},
            { path: 'edit/annotations/upload',
              component: AnnotationsUploadComponent},
            { path: 'edit/transcripts/layers/regenerate',
              component: TranscriptsLayersRegenerateComponent},
            { path: 'edit/participants/layers/regenerate',
              component: ParticipantsLayersRegenerateComponent},
        ]), // TODO add { path: '**', component: PageNotFoundComponent }
        FormsModule,
        LabbcatCommonModule.forRoot(environment)
    ],
    providers: [],
    bootstrap: [AppComponent]
})
export class AppModule { }

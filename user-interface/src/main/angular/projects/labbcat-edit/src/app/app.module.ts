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

@NgModule({
    declarations: [
        AppComponent,
        MissingAnnotationsComponent,
        ParticipantComponent,
        TranscriptAttributesComponent,
        TranscriptMediaComponent
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
              canDeactivate: [PendingChangesGuard]}
        ]), // TODO add { path: '**', component: PageNotFoundComponent }
        FormsModule,
        LabbcatCommonModule.forRoot(environment)
    ],
    providers: [],
    bootstrap: [AppComponent]
})
export class AppModule { }

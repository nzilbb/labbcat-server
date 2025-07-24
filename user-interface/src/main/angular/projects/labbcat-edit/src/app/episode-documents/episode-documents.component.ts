import { Component, OnInit, Inject } from '@angular/core';
import { ActivatedRoute } from '@angular/router';

import { EditComponent } from '../edit-component';
import { MessageService, LabbcatService, MediaFile } from 'labbcat-common';

@Component({
  selector: 'app-episode-documents',
  templateUrl: './episode-documents.component.html',
  styleUrl: './episode-documents.component.css'
})
export class EpisodeDocumentsComponent extends EditComponent implements OnInit {

    loading = true;
    imagesLocation : string;
    id: string;
    documents: MediaFile[];
    trackUpload: "" | "show" | "uploading";
    fileDeleting: { [fileName: string] : boolean} = {};

    constructor(
        labbcatService : LabbcatService,
        messageService : MessageService,
        private route : ActivatedRoute,
        @Inject('environment') private environment
    ) {
        super(labbcatService, messageService);
        this.imagesLocation = this.environment.imagesLocation;
    }

    ngOnInit() : void {
        this.route.queryParams.subscribe((params) => {
            this.id = this.id || params["id"];
            this.readDocuments();
        });
    }

    readDocuments() : void {
        this.loading = true;
        this.labbcatService.labbcat.getEpisodeDocuments(
                this.id, (documents, errors, messages) => {
                    this.loading = false;
                    this.documents = documents;
                });
    }
    
    upload(file: any): void {
        this.trackUpload = "uploading";
        this.labbcatService.labbcat.saveEpisodeDocument(
            this.id, file, (result, errors, messages) => {
                this.trackUpload = "";
                if (errors) {
                    errors.forEach(m => this.messageService.error(m));
                }
                if (messages) {
                    messages.forEach(m => this.messageService.info(m));
                }
                this.readDocuments();
            });
    }
    deleteFile(fileName: string): void {
        if (confirm(`Are you sure you want to delete ${fileName}?`)) { // TODO i18n
            this.fileDeleting[fileName] = true;
            this.labbcatService.labbcat.deleteMedia(
                this.id, fileName, (result, errors, messages) => {
                    this.fileDeleting[fileName] = false;
                    if (errors) {
                        errors.forEach(m => this.messageService.error(m));
                    }
                    console.log(JSON.stringify(messages));
                    if (messages) {
                        messages.forEach(m => this.messageService.info(m));
                    }
                    this.readDocuments();
                });
        }
    }
}

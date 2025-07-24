import { Component, OnInit, Inject } from '@angular/core';
import { ActivatedRoute } from '@angular/router';

import { MessageService, LabbcatService, MediaFile } from 'labbcat-common';

@Component({
  selector: 'app-episode-documents',
  templateUrl: './episode-documents.component.html',
  styleUrl: './episode-documents.component.css'
})
export class EpisodeDocumentsComponent implements OnInit {

    loading = true;
    imagesLocation : string;
    id: string;
    documents: MediaFile[];

    constructor(
        private labbcatService : LabbcatService,
        private messageService : MessageService,
        private route : ActivatedRoute,
        @Inject('environment') private environment
    ) {
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
}

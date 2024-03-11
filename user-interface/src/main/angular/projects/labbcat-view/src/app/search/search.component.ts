import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';

import { Layer } from 'labbcat-common';
import { MessageService, LabbcatService } from 'labbcat-common';

import { Matrix } from '../matrix';

@Component({
  selector: 'app-search',
  templateUrl: './search.component.html',
  styleUrls: ['./search.component.css']
})
export class SearchComponent implements OnInit {
    
    schema: any;
    matrix: Matrix;
    participantDescription: string;
    participantIds: string[];
    transcriptDescription: string;    
    
    constructor(
        private labbcatService: LabbcatService,
        private messageService: MessageService,
        private route: ActivatedRoute,
        private router: Router
    ) { }
    
    ngOnInit(): void {
        this.matrix = {
            columns: [],
            participantQuery: "",
            transcriptQuery: ""
        }
        this.labbcatService.labbcat.getSchema((schema, errors, messages) => {
            this.schema = schema;
            
            // interpret URL parameters
            this.route.queryParams.subscribe((params) => {
                this.matrix.participantQuery = params["participant_expression"];
                if (this.matrix.participantQuery) {
                    this.participantDescription = params["participants"]
                        || "Selected participants"; // TODO i18n
                }
                this.matrix.transcriptQuery = params["transcript_expression"];
                if (this.matrix.transcriptQuery) {
                    this.transcriptDescription = params["transcripts"]
                        || "Selected transcripts"; // TODO i18n
                }
                this.listParticipants();
            });
        });
    }
    
    /** List participants that match the filters */
    listParticipants(): void {
        if (this.matrix.participantQuery) {
            this.labbcatService.labbcat.getMatchingParticipantIds(
                this.matrix.participantQuery, (participantIds, errors, messages) => {
                    if (errors) errors.forEach(m => this.messageService.error(m));
                    if (messages) messages.forEach(m => this.messageService.info(m));
                    this.participantIds = participantIds;
                });
        } // there is a participantExpression
    }
}

import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';

import { Annotation, Response, Layer, User } from 'labbcat-common';
import { MessageService, LabbcatService } from 'labbcat-common';

@Component({
  selector: 'app-participant',
  templateUrl: './participant.component.html',
  styleUrls: ['./participant.component.css']
})
export class ParticipantComponent implements OnInit {
    
    baseUrl: string;
    schema: any;
    id: string;
    attributes: string[];
    categoryLayers: object; // string->Layer
    categoryLabels: string[];
    currentCategory: string;
    participant: Annotation;
    transcriptIds: string[];
    
    constructor(
        private labbcatService: LabbcatService,
        private messageService: MessageService,
        private route: ActivatedRoute,
        private router: Router
    ) { }
    
    ngOnInit(): void {
        this.readBaseUrl();
        this.readSchema().then(()=> {
            this.route.queryParams.subscribe((params) => {
                this.id = params["id"]
                this.readParticipant();
                this.readTranscripts();
            });
        });
    }

    readSchema(): Promise<void> {
        return new Promise((resolve, reject) => {
            this.labbcatService.labbcat.getSchema((schema, errors, messages) => {
                this.schema = schema;
                this.attributes = [];
                this.categoryLayers = {};
                this.categoryLabels = [];
                // participant attributes
                for (let layerId in schema.layers) {
                    // main_participant this relates participants to transcripts, so ignore that
                    if (layerId == "main_participant") continue;
                    
                    const layer = schema.layers[layerId] as Layer;
                    if (layer.parentId == schema.participantLayerId
                        && layer.alignment == 0) {
                        this.attributes.push(layer.id);
                        if (!this.categoryLayers[layer.category]) {
                            this.categoryLayers[layer.category] = [];
                            this.categoryLabels.push(layer.category);
                            // select first category by default
                            if (!this.currentCategory) this.currentCategory = layer.category;
                        }
                        this.categoryLayers[layer.category].push(layer);
                    }
                }
                resolve();
            });
        });
    }

    readBaseUrl(): void {
        this.labbcatService.labbcat.getId((url, errors, messages) => {
            this.baseUrl = url;
        });
    }
    
    readParticipant(): void {
        this.labbcatService.labbcat.getParticipant(
            this.id, this.attributes, (participant, errors, messages) => {
                if (errors) errors.forEach(m => this.messageService.error(m));
                if (messages) messages.forEach(m => this.messageService.info(m));
                this.participant = participant;
            });       
    }

    readTranscripts(): void {
        this.labbcatService.labbcat.getTranscriptIdsWithParticipant(
            this.id, (transcriptIds, errors, messages) => {
                if (errors) errors.forEach(m => this.messageService.error(m));
                if (messages) messages.forEach(m => this.messageService.info(m));
                this.transcriptIds = transcriptIds;
            });       
    }
    
}

import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';

import { Annotation, Response, Layer, User } from 'labbcat-common';
import { MessageService, LabbcatService } from 'labbcat-common';

@Component({
  selector: 'app-transcript-attributes',
  templateUrl: './transcript-attributes.component.html',
  styleUrls: ['./transcript-attributes.component.css']
})
export class TranscriptAttributesComponent implements OnInit {
    
    baseUrl: string;
    user: User;
    schema: any;
    id: string;
    attributes: string[];
    categoryLayers: object; // string->Layer
    categoryLabels: string[];
    currentCategory: string;
    categories: object; // string->Category
    transcript: Annotation;
    
    constructor(
        private labbcatService: LabbcatService,
        private messageService: MessageService,
        private route: ActivatedRoute,
        private router: Router
    ) { }
    
    ngOnInit(): void {
        this.readBaseUrl();
        this.readCategories();
        this.readUserInfo();
        this.readSchema().then(()=> {
            this.route.queryParams.subscribe((params) => {
                this.id = params["id"];
                this.readTranscript();
            });
        });
    }
    
    readCategories(): Promise<void> {
        this.categories = {};
        return new Promise((resolve, reject) => {
            this.labbcatService.labbcat.readOnlyCategories(
                "transcript", (categories, errors, messages) => {
                    this.categoryLabels = [];
                    for (let category of categories) {
                        const layerCategory = "transcript_"+category.category;
                        this.categories[layerCategory] = category;
                        this.categoryLabels.push(layerCategory);
                        // select first category by default
                        if (!this.currentCategory) this.currentCategory = layerCategory;
                    }
                    resolve();
                });
        });
    }

    readUserInfo(): Promise<void> {
        return new Promise((resolve, reject) => {
            this.labbcatService.labbcat.getUserInfo((user, errors, messages) => {
                this.user = user as User;
                resolve();
            });
        });
    }
    
    readSchema(): Promise<void> {
        return new Promise((resolve, reject) => {
            this.labbcatService.labbcat.getSchema((schema, errors, messages) => {
                this.schema = schema;
                this.attributes = [];
                this.categoryLayers = {};
                // transcript attributes
                for (let layerId in schema.layers) {
                    const layer = schema.layers[layerId] as Layer;
                    if (layer.parentId == "transcript"
                        && layer.alignment == 0
                        && layer.id != schema.participantLayerId) {

                        // ensure we can iterate all layer IDs
                        this.attributes.push(layer.id);
                        
                        // ensure the transcript type layer has a category
                        if (layer.id == "transcript_type") layer.category = "transcript_General";
                        if (layer.category) {
                            if (!this.categoryLayers[layer.category]) {
                                this.categoryLayers[layer.category] = [];
                            }
                            this.categoryLayers[layer.category].push(layer);
                        }
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
    
    readTranscript(): void {
        this.labbcatService.labbcat.getTranscript(
            this.id, this.attributes, (transcript, errors, messages) => {
                if (errors) errors.forEach(m => this.messageService.error(m));
                if (messages) messages.forEach(m => this.messageService.info(m));
                this.transcript = transcript;
            });       
    }
}

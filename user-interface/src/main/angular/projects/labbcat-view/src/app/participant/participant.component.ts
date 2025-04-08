import { Injectable, Inject, LOCALE_ID } from '@angular/core';
import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';

import { Annotation, Response, Layer, User } from 'labbcat-common';
import { MessageService, LabbcatService } from 'labbcat-common';

@Injectable({
  providedIn: 'root'
})
@Component({
  selector: 'app-participant',
  templateUrl: './participant.component.html',
  styleUrls: ['./participant.component.css']
})
export class ParticipantComponent implements OnInit {
    
    baseUrl: string;
    user: User;
    schema: any;
    id: string;
    attributes: string[];
    categoryLayers: object; // string->Layer
    categoryLabels: string[];
    currentCategory: string;
    categories: object; // string->Category
    participant: Annotation;
    displayLayerIds: boolean;
    
    constructor(
        @Inject('environment') private environment,
        private labbcatService: LabbcatService,
        private messageService: MessageService,
        private route: ActivatedRoute,
        private router: Router
    ) { }
    
    ngOnInit(): void {
        this.readBaseUrl();
        this.readCategories();
        this.readUserInfo().then(()=> {
            this.readSchema().then(()=> {
                this.route.queryParams.subscribe((params) => {
                    this.id = params["id"];
                    if (this.user.roles.includes('edit')
                        && this.environment.production) {
                        // redirect to the edit page instead
                        document.location.href = "edit/participant?id="+this.id;
                    } else {
                        this.readParticipant();
                    }
                });
            });
        });
    }

    readCategories(): Promise<void> {
        this.categories = {};
        return new Promise((resolve, reject) => {
            this.labbcatService.labbcat.readOnlyCategories(
                "participant", (categories, errors, messages) => {
                    for (let category of categories) {
                        this.categories["participant_"+category.category] = category;
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
                this.categoryLabels = [];
                const displayLayerIds = sessionStorage.getItem("displayLayerIds");
                this.displayLayerIds = JSON.parse(displayLayerIds || 'true');
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

    ParticipantLayerLabel(id): string {
        return id.replace(/^participant_/,"");
    }
    toggleLayerIds(): void {
        this.displayLayerIds = !this.displayLayerIds;
        sessionStorage.setItem("displayLayerIds", JSON.stringify(this.displayLayerIds));
    }
}

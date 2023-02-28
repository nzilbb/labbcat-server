import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';

import { EditComponent } from '../edit-component';
import { Annotation, Response, Layer, User } from 'labbcat-common';
import { MessageService, LabbcatService } from 'labbcat-common';

@Component({
  selector: 'app-participant',
  templateUrl: './participant.component.html',
  styleUrls: ['./participant.component.css']
})
export class ParticipantComponent extends EditComponent implements OnInit {
    
    baseUrl: string;
    schema: any;
    id: string;
    attributes: string[];
    categoryLayers: object; // string->Layer
    categoryLabels: string[];
    currentCategory: string;
    participant: Annotation;
    updating = false;
    multiValueAttributes: object; //layerId->(value->ticked)
    otherValues: object; //layerId->value
    
    constructor(
        labbcatService: LabbcatService,
        messageService: MessageService,
        private route: ActivatedRoute,
        private router: Router
    ) {
        super(labbcatService, messageService);
    }
    
    ngOnInit(): void {
        this.readBaseUrl();
        this.readSchema().then(()=> {
            this.route.queryParams.subscribe((params) => {
                this.id = params["id"]
                this.readParticipant();
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
                this.multiValueAttributes = {};
                this.otherValues = {};
                // participant attributes
                for (let layerId in schema.layers) {
                    // main_participant this relates participants to transcripts, so ignore that
                    if (layerId == "main_participant") continue;
                    
                    const layer = schema.layers[layerId] as Layer;
                    if (layer.parentId == schema.participantLayerId
                        && layer.alignment == 0) {
                        // ensure we can iterate all layer IDs
                        this.attributes.push(layer.id);
                        // categorise layers by category
                        if (!this.categoryLayers[layer.category]) {
                            this.categoryLayers[layer.category] = [];
                            this.categoryLabels.push(layer.category);
                            // select first category by default
                            if (!this.currentCategory) this.currentCategory = layer.category;
                        }
                        this.categoryLayers[layer.category].push(layer);
                        // track multi-value attributes
                        if (layer.peers && this.definesValidLabels(layer)) {
                            this.multiValueAttributes[layer.id] = {};
                            for (let label of Object.keys(layer.validLabels)) {
                                this.multiValueAttributes[layer.id][label] = false; // unchecked
                            } // next valid label
                        } // multi-value attribute
                        this.otherValues[layer.id] = "";
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
        // ensure multiValueAttributes start all unchecked, and 'other' values blank
        // each time participant is loaded
        for (let layerId of Object.keys(this.multiValueAttributes)) {
            for (let label of Object.keys(this.multiValueAttributes[layerId])) {
                this.multiValueAttributes[layerId][label] = false;
            } // next possible value
            this.otherValues[layerId] = "";
        } // next multi-value attribute layer
        this.labbcatService.labbcat.getParticipant(
            this.id, this.attributes, (participant, errors, messages) => {
                if (errors) errors.forEach(m => this.messageService.error(m));
                if (messages) messages.forEach(m => this.messageService.info(m));
                // ensure all attributes have at lease one annotation
                for (let layerId of this.attributes) {
                    if (this.isMultiValue(layerId)) {
                        if (participant.annotations[layerId]) {
                            for (let annotation of participant.annotations[layerId]) {
                                this.multiValueAttributes[layerId][annotation.label] = true;
                            } // next annotation
                        }
                    } else {
                        // make sure all single-value attributes have an annotation
                        if (!participant.annotations[layerId]) {
                            // create dummary annotation to bind to
                            participant.annotations[layerId] = [{
                                layerId : layerId,
                                label: "",
                                _changed : true } as Annotation];
                        }
                    }
                } // next layer/attribute
                this.changed = false;
                this.participant = participant;
            });       
    }

    onChange(annotation: Annotation, clearOther?: boolean): void {
        console.log(`onChange ${annotation}`);
        if (annotation) {
            annotation._changed = true;
            if (clearOther) this.otherValues[annotation.layerId] = "";
        }
        this.changed = true;
    }

    updateParticipant(): void {
        this.updating = true;
        // compile single-value and multi-value attribute values
        const attributeValues = {};        
        for (let layerId of this.attributes) {
            if (this.isMultiValue(layerId)) {
                // the attribute value is sent as an array of values
                attributeValues[layerId] = [];
                for (let value of Object.keys(this.multiValueAttributes[layerId])) {
                    if (this.multiValueAttributes[layerId][value]) {
                        attributeValues[layerId].push(value);
                    }
                } // next possible value
                // 'other' values are added
                if (this.otherValues[layerId]) {
                    attributeValues[layerId].push(this.otherValues[layerId]);
                }
                if (attributeValues[layerId].length == 0) { // no value set
                    // if we don't pass a value, no http parameter is sent,
                    // so the attribute is ignored
                    // so we pass an empty value
                    attributeValues[layerId].push("");
                }
            } else { // single value
                // 'other' values override selected values
                if (this.otherValues[layerId]) {
                    attributeValues[layerId] = this.otherValues[layerId];
                    this.otherValues[layerId] = "";
                } else {
                    attributeValues[layerId] = this.participant.annotations[layerId][0].label;
                }
            }
        } // next attribute
        this.labbcatService.labbcat.saveParticipant(
            this.id, this.participant.label, attributeValues, (updated, errors, messages) => {
                this.updating = false;
                if (errors) errors.forEach(m => this.messageService.error(m));
                if (messages) messages.forEach(m => this.messageService.info(m));

                this.id = this.participant.label; // in case we changed the name/ID
                this.readParticipant();
            });
    }
    
    definesValidLabels(layer: Layer): boolean {
        return Object.keys(layer.validLabels).length > 0;
    }
    
    isMultiValue(layerId: string): boolean {
        return Object.keys(this.multiValueAttributes).includes(layerId);
    }
    
    optionValues(layer: Layer, annotations: Annotation[]): string[] {
        const values = Object.keys(layer.validLabels);
        if (annotations) {
            for (let annotation of annotations) {
                if (!values.includes(annotation.label)) {
                    values.push(annotation.label);
                }
            } // next annotation
        }
        return values;
    }

    labelPresent(annotations: Annotation[], label: string): boolean {
        if (annotations) {
            for (let annotation of annotations) {
                if (annotation.label == label) return true;
            } // next annotation
        }
        return false;
    }

    otherValueAllowed(layer: Layer): boolean {
        return /other/.test(layer.style);
    }
}

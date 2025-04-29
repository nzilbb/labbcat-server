import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';

import { EditComponent } from '../edit-component';
import { Category } from '../category';
import { Annotation, Response, Layer, User } from 'labbcat-common';
import { MessageService, LabbcatService } from 'labbcat-common';

@Component({
  selector: 'app-transcript-attributes',
  templateUrl: './transcript-attributes.component.html',
  styleUrls: ['./transcript-attributes.component.css']
})
export class TranscriptAttributesComponent extends EditComponent implements OnInit {

    baseUrl: string;
    schema: any;
    id: string;
    attributes: string[];
    categoryLayers: object; // string->Layer
    categoryLabels: string[];
    currentCategory: string;
    categories: object; // string->Category
    transcript: object;
    updating = false;
    multiValueAttributes: object; // layerId->(value->ticked)
    otherValues: object; // layerId->value
    textAreas: string[]; // track textareas for auto-resize
    loaded = false;
    
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
        this.readCategories();
        this.readSchema().then(()=> {
            this.route.queryParams.subscribe((params) => {
                this.id = params["id"]
                this.readTranscript();
            });
        });
    }

    readCategories(): Promise<void> {
        this.categories = {};
        return new Promise((resolve, reject) => {
            this.labbcatService.labbcat.readCategories(
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

    readSchema(): Promise<void> {
        return new Promise((resolve, reject) => {
            this.labbcatService.labbcat.getSchema((schema, errors, messages) => {
                this.schema = schema;
                this.attributes = [];
                this.textAreas = [];
                this.categoryLayers = {};
                this.multiValueAttributes = {};
                this.otherValues = {};
                let corpusLayer: Layer; // corpus layer - save it for last
                // transcript attributes
                for (let layerId in schema.layers) {
                    // main_transcript this relates transcripts to transcripts, so ignore that
                    if (layerId == "main_transcript") continue;
                    
                    const layer = schema.layers[layerId] as Layer;
                    if (layer.parentId == "transcript"
                        && layer.alignment == 0
                        && layer.id != schema.participantLayerId) {

                        // ensure we can iterate all layer IDs
                        this.attributes.push(layer.id);

                        // ensure the transcript type layer has a category
                        if (layer.id == "transcript_type") layer.category = "transcript_General";
                        
                        if (layer.category) {
                            
                            // categorise layers by category
                            if (!this.categoryLayers[layer.category]) {
                                this.categoryLayers[layer.category] = [];
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
                            if (layer.type == 'string' && layer.subtype == 'text') {
                                this.textAreas.push(layer.id); // track textareas for auto-resize
                            }
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
        // ensure multiValueAttributes start all unchecked, and 'other' values blank
        // each time transcript is loaded
        for (let layerId of Object.keys(this.multiValueAttributes)) {
            for (let label of Object.keys(this.multiValueAttributes[layerId])) {
                this.multiValueAttributes[layerId][label] = false;
            } // next possible value
            this.otherValues[layerId] = "";
        } // next multi-value attribute layer
        this.labbcatService.labbcat.getTranscript(
            this.id, this.attributes, (transcript, errors, messages) => {
                this.loaded = true;
                if (errors) errors.forEach(m => this.messageService.error(m));
                if (messages) messages.forEach(m => this.messageService.info(m));
                if (!transcript) {
                    console.error("Invalid transcript ID");
                    this.messageService.error("Invalid transcript ID"); // TODO i18n
                } else { // valid transcript
                    // ensure all attributes have at lease one annotation
                    for (let layerId of this.attributes) {
                        if (this.isMultiValue(layerId)) {
                            if (!transcript[layerId]) {
                                transcript[layerId] = [];
                            }
                            for (let annotation of transcript[layerId]) {
                                this.multiValueAttributes[layerId][annotation.label] = true;
                            } // next annotation
                        } else {
                            // make sure all single-value attributes have an annotation
                            if (!transcript[layerId]) {
                                // create dummary annotation to bind to
                                transcript[layerId] = [{
                                    layerId : layerId,
                                    label: "",
                                    _changed : true } as Annotation];
                            }
                        }
                    } // next layer/attribute
                    this.changed = false;
                    this.transcript = transcript;
                    
                    // resize textareas after the view has had a chance to render
                    setTimeout(()=>{
                        for (let layerId of this.textAreas) this.resizeTextArea(layerId);
                    }, 200);
                } // valid transcript
            });       
    }

    onChange(annotation: Annotation, clearOther?: boolean): void {
        if (annotation) {
            annotation._changed = true;
            if (clearOther) this.otherValues[annotation.layerId] = "";
            this.resizeTextArea(annotation.layerId);
        }
        this.changed = true;
    }

    resizeTextArea(id: string): boolean {
        // is it a textarea that needs resizing?
        const element = document.getElementById(id) as any;
        if (element != null && element.tagName == "TEXTAREA") {
            element.parentNode.dataset.replicatedValue = element.value;
            return true;
        }
        return false;
    }

    updateTranscript(): boolean {

        // validation
        let everythingValid = true;
        for (let category of Object.keys(this.categoryLayers)) {
            for (let l of this.categoryLayers[category]) {
                let validateLayer = false;
                if (this.transcript[l.id]) {
                    for (let annotation of this.transcript[l.id]) {
                        if (annotation._changed) {
                            const control = document.getElementById(l.id) as any;
                            if (control && control.checkValidity) {
                                if (!control.checkValidity()) {
                                    if (this.currentCategory == category) { // current category
                                        control.reportValidity();
                                    } else { // not current category, so show category first
                                        this.currentCategory = category;
                                        // show message after short delay, to give the category time to
                                        // become visible
                                        setTimeout(()=>control.reportValidity(), 200);
                                    }
                                    return false;
                                }
                            }
                            break;
                        }
                    } // next annotation
                } // there are annotations
            } // next attribute
        } // next category
        
        this.updating = true;
        // compile single-value and multi-value attribute values
        for (let layerId of this.attributes) {
            if (this.isMultiValue(layerId)) {
                // the attribute value is sent as an array of values
                this.transcript[layerId] = [];
                for (let value of Object.keys(this.multiValueAttributes[layerId])) {
                    if (this.multiValueAttributes[layerId][value]) {
                        this.transcript[layerId].push({ layerId: layerId, label: value });
                    }
                } // next possible value
                // 'other' values are added
                if (this.otherValues[layerId]) {
                    this.transcript[layerId].push(
                        { layerId: layerId, label: this.otherValues[layerId] });
                }
            } else { // single value
                // 'other' values override selected values
                if (this.otherValues[layerId]) {
                    this.transcript[layerId][0].label = this.otherValues[layerId];
                    this.otherValues[layerId] = "";
                }
            } // single value
        } // next attribute
        this.labbcatService.labbcat.saveTranscript(
            this.transcript, (updated, errors, messages) => {
                this.updating = false;
                if (errors) errors.forEach(m => this.messageService.error(m));
                if (messages) messages.forEach(m => this.messageService.info(m));
                this.readTranscript();
            });
        return true;
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

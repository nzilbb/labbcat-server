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
    categories: object; // string->Category
    participant: Annotation;
    updating = false;
    multiValueAttributes: object; // layerId->(value->ticked)
    otherValues: object; // layerId->value
    textAreas: string[]; // track textareas for auto-resize
    loaded = false;
    passwordForm = false;
    password = "";
    
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
                this.readParticipant();
            });
        });
    }

    readCategories(): Promise<void> {
        this.categories = {};
        return new Promise((resolve, reject) => {
            this.labbcatService.labbcat.readCategories(
                "participant", (categories, errors, messages) => {
                    for (let category of categories) {
                        this.categories[category.category] = category;
                    }
                    // extra pseudo category that allows administration of corpora
                    this.categories["Corpora"] = {
                        description: "The corpora the participant belongs to"};
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
                this.categoryLabels = [];
                this.multiValueAttributes = {};
                this.otherValues = {};
                let corpusLayer: Layer; // corpus layer - save it for last
                // participant attributes
                for (let layerId in schema.layers) {
                    // main_participant this relates participants to transcripts, so ignore that
                    if (layerId == "main_participant") continue;
                    
                    const layer = schema.layers[layerId] as Layer;
                    if ((layer.parentId == schema.participantLayerId
                        && layer.alignment == 0)) { // participant attribute layer
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
                        if (layer.type == 'string' && layer.subtype == 'text') {
                            this.textAreas.push(layer.id); // track textareas for auto-resize
                        }
                    } else if (layer.id == schema.corpusLayerId) {
                        corpusLayer = layer;
                    }
                }
                if (corpusLayer) { // make this an editable label on its own tab
                    corpusLayer.category = "Corpora"; // TODO i18n
                    corpusLayer.peers = true;
                    corpusLayer.subtype = "string";
                    this.multiValueAttributes[corpusLayer.id] = {};
                    for (let label of Object.keys(corpusLayer.validLabels)) {
                        this.multiValueAttributes[corpusLayer.id][label] = false; // unchecked
                    } // next valid label
                    this.attributes.push(corpusLayer.id);
                    this.categoryLayers[corpusLayer.category] = [];
                    this.categoryLabels.push(corpusLayer.category);
                    this.categoryLayers[corpusLayer.category].push(corpusLayer);
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
                this.loaded = true;
                if (errors) errors.forEach(m => this.messageService.error(m));
                if (messages) messages.forEach(m => this.messageService.info(m));
                if (!participant) {
                    console.error("Invalid participant ID");
                    this.messageService.error("Invalid participant ID"); // TODO i18n
                } else { // valid participant
                    // it might be a brand new participant with no attributes set
                    if (!participant.annotations) participant.annotations = {};
                    // ensure all attributes have at least one annotation
                    for (let layerId of this.attributes) {
                        if (this.isMultiValue(layerId)) {
                            if (!participant.annotations[layerId]) {
                                participant.annotations[layerId] = [];
                            }
                            for (let annotation of participant.annotations[layerId]) {
                                this.multiValueAttributes[layerId][annotation.label] = true;
                            } // next annotation
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
                    
                    // resize textareas after the view has had a chance to render
                    setTimeout(()=>{
                        for (let layerId of this.textAreas) this.resizeTextArea(layerId);
                    }, 200);
                } // valid participant
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

    updateParticipant(): boolean {

        // validation
        let everythingValid = true;
        if (this.participant._changed) { // check participant ID
            const control = document.getElementById("participant") as any;
            if (control && control.checkValidity) {
                if (!control.checkValidity()) {
                    control.reportValidity();
                    return false;
                }
            }
        }
        for (let category of Object.keys(this.categoryLayers)) {
            for (let l of this.categoryLayers[category]) {
                let validateLayer = false;
                if (this.participant.annotations[l.id]) {
                    for (let annotation of this.participant.annotations[l.id]) {
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

                if (updated) {
                    this.id = this.participant.label; // in case we changed the name/ID
                }
                this.readParticipant();
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

    setPassword(): void {
        if (!this.passwordForm) {
            this.passwordForm = true;
            setTimeout(()=>{
                const control = document.getElementById("password") as any;
                control.focus();
            }, 200);
        } else {
            // validate
            const control = document.getElementById("password") as any;
            if (control.reportValidity()) {
                this.updating = true;
                this.labbcatService.labbcat.saveParticipant(
                    this.id, null, { _password: this.password}, (updated, errors, messages) => {
                        this.updating = false;
                        if (errors) errors.forEach(m => this.messageService.error(m));
                        if (messages) messages.forEach(m => this.messageService.info(m));

                        if (updated) {
                            // reset form
                            this.password = "";
                            this.passwordForm = false;
                        }
                    });
            }
        }
    }
}

import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';

import { Layer, Annotation } from 'labbcat-common';
import { MessageService, LabbcatService } from 'labbcat-common';

@Component({
  selector: 'app-participants-merge',
  templateUrl: './participants-merge.component.html',
  styleUrl: './participants-merge.component.css'
})
export class ParticipantsMergeComponent {

    baseUrl: string;
    schema: any;
    participantAttributes: Layer[];
    participantIds: string[];
    participants: Annotation[];
    layerIdToLabels: { [layerId: string] : string[] };

    label: string;    
    // layerId->value
    singleValueAttributes: { [layerId: string] : string };
    // layerId->(value->ticked)
    multiValueAttributes: { [layerId: string] : { [value:string] : boolean } };
    
    merging = false;

    constructor(
        private labbcatService: LabbcatService,
        private messageService: MessageService,
        private route: ActivatedRoute,
        private router: Router
    ) { }
    
    ngOnInit(): void {
        // get baseUrl
        this.labbcatService.labbcat.getId((url, errors, messages) => {
            this.baseUrl = url;
        });
        this.readSchema().then(()=>{
            // interpret URL parameters
            this.route.queryParams.subscribe((params) => {
                this.listParticipants(params["id"]);
            });
        });
    }
    readSchema(): Promise<void> {
        this.participantAttributes = [];
        return new Promise((resolve, reject) => {
            this.labbcatService.labbcat.getSchema((schema, errors, messages) => {
                this.schema = schema;
                for (let layerId in this.schema.layers) {
                    const layer = this.schema.layers[layerId] as Layer;
                    if ((layer.parentId == this.schema.participantLayerId
                        && layer.alignment == 0)
                        || layer.id == schema.corpusLayerId) {
                        this.participantAttributes.push(layer);
                    } // participant attribute layer
                } // next layer
                resolve();
            });
        });
    }
    
    /** List participants that match the filters */
    listParticipants(ids: string[]): void {
        this.participantIds = ids;
        
        // set up attribute labels reconciliate
        this.layerIdToLabels = {};
        this.singleValueAttributes = {};
        this.multiValueAttributes = {};
        for (let layer of this.participantAttributes) {
            this.layerIdToLabels[layer.id] = [];
            if (this.reconciliationType(layer) == "any-possibility") {
                this.multiValueAttributes[layer.id] = {};
            }
        } // next attribute

        // load each participant
        for (let id of this.participantIds) {
            this.labbcatService.labbcat.getParticipant(
                id, this.participantAttributes.map(layer => layer.id),
                (participant, errors, messages) => {
                    if (!this.participants) this.participants = [];
                    if (errors) {
                        errors.filter(m => m != "cancelled")
                            .forEach(m => this.messageService.error(m));
                    }
                    if (messages) messages.forEach(m => this.messageService.info(m));
                    if (participant) {
                        if (!this.label) this.label = participant.label;
                        
                        // add participant to list
                        this.participants.push(participant);
                        
                        // for each attribute
                        for (let layer of this.participantAttributes) {
                            // for each value
                            if (participant.annotations[layer.id]) {
                                for (let annotation of participant.annotations[layer.id]) {
                                    if (annotation.label
                                        && !this.layerIdToLabels[layer.id]
                                            .includes(annotation.label)) {
                                        switch(this.reconciliationType(layer)) {
                                            case "any-possibility":
                                                this.multiValueAttributes[layer.id]
                                                [annotation.label] = true;
                                                break;
                                            case "all-possibilities":
                                                if (!this.singleValueAttributes[layer.id]) {
                                                    this.singleValueAttributes[layer.id]
                                                        = annotation.label;
                                                } else {
                                                    this.singleValueAttributes[layer.id]
                                                        += "\n"+annotation.label;
                                                }
                                                break;
                                            default:
                                                if (!this.singleValueAttributes[layer.id]) {
                                                    this.singleValueAttributes[layer.id]
                                                        = annotation.label;
                                                }
                                        }
                                        this.layerIdToLabels[layer.id].push(annotation.label);
                                    } // not already there
                                } // next value
                            } // there are values
                        } // next attribute
                    } // valid participant
                }); // getParticipant
        } // next participant ID
    }

    reconciliationType(layer: Layer): string {
        if (layer.type == 'string' && layer.subtype == 'text') {
            return "all-possibilities";
        } else if (layer.peers || layer.id == this.schema.corpusLayerId) {
            return "any-possibility";
        } else {
            return "one-possibility";
        }
    }

    merge(): void {
        const nameInput = document.getElementById("name") as HTMLInputElement;
        if (!nameInput.reportValidity()) {
            return;
        }
        if (!confirm("Are you sure you want to combine these participant records into one?")) {
            return;
        }
        this.merging = true;

        // merge single-value and multi-value attributes
        const attributes = Object.assign({}, this.singleValueAttributes) as any;
        for (let layerId in this.multiValueAttributes) {
            const labels = [];
            for (let label in this.multiValueAttributes[layerId]) {
                if (this.multiValueAttributes[layerId][label]) {
                    labels.push(label);
                }
            } // next possible label
            attributes[layerId] = labels;
        } // next multi-value attribute

        // create new participant...

        // this.label is probably the name of one of the existing participants,
        // but the name has to be unique in the database, so use a temporary unique name
        const temporaryId = "Merged "+new Date().getTime(); 
        this.labbcatService.labbcat.saveParticipant(
            temporaryId, // this ID shouldn't exist yet, a new participant will be created
            temporaryId,
            attributes, (success, errors, messages) => {
                if (errors) errors.forEach(m => this.messageService.error(m));
                
                if (success) {
                    // merge old participants into new participant
                    const merge = this.labbcatService.labbcat.createRequest(
                        "merge", {
                            destination: temporaryId,
                            source: this.participantIds},
                        (model, errors, messages) => {
                            if (errors) errors.forEach(m => this.messageService.error(m));
                            if (messages) messages.forEach(m => this.messageService.info(m));
                            // save new name, now that old participants are gone
                            this.labbcatService.labbcat.saveParticipant(
                                temporaryId,
                                this.label, // name they specified
                                {}, (success, errors, messages) => {
                                    if (errors) {
                                        errors.forEach(m => this.messageService.error(m));
                                    }
                                    // open the new participant's attributes page
                                    this.router.navigate(
                                        ['/','edit','participant'],
                                        { queryParams: {
                                            id: success?this.label:temporaryId } });
                                }); // saveParticipant
                        },
                        this.baseUrl+"api/edit/participants/merge", "POST");
                    try {
                        merge.send();
                    } catch (x) {
                        this.messageService.error(x);
                    }
                } // save successful
                this.merging = false;
                
            }); // saveParticipant
    }
}

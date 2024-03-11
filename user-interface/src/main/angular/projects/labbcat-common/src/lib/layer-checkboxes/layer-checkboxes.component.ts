import { Component, OnInit, Input, Output, EventEmitter } from '@angular/core';

import { LabbcatService } from '../labbcat.service';
import { Layer } from '../layer';

@Component({
  selector: 'lib-layer-checkboxes',
  templateUrl: './layer-checkboxes.component.html',
  styleUrls: ['./layer-checkboxes.component.css']
})
export class LayerCheckboxesComponent implements OnInit {
    @Input() name: string;
    @Input() includeCounts: boolean;
    @Input() includeAnchorSharing: boolean;
    @Input() includeRelationship: boolean;
    @Input() participant: boolean;
    @Input() excludeParticipant: boolean;
    @Input() excludeMainParticipant: boolean;
    participantAttributes: Layer[];
    @Input() transcript: boolean;
    @Input() excludeCorpus: boolean;
    transcriptAttributes: Layer[];
    @Input() excludeRoot: boolean;
    @Input() span: boolean;
    spanLayers: Layer[];
    @Input() phrase: boolean;
    @Input() excludeTurn: boolean;
    @Input() excludeUtterance: boolean;
    @Input() category: boolean;
    phraseLayers: Layer[];
    @Input() word: boolean;
    wordLayers: Layer[];
    @Input() segment: boolean;
    segmentLayers: Layer[];
    @Input() selected: string[];
    @Output() selectedChange = new EventEmitter<string[]>();
    schema;
    scopeCount = 0;
    categorySelections: any;

    constructor(
        private labbcatService: LabbcatService
    ) { }

    ngOnInit(): void {
        this.loadSchema();
        this.scopeCount = 0;
        if (this.participant) this.scopeCount++;
        if (this.transcript) this.scopeCount++;
        if (this.span) this.scopeCount++;
        if (this.phrase) this.scopeCount++;
        if (this.word) this.scopeCount++;
        if (this.segment) this.scopeCount++;
    }

    loadSchema(): void {
        this.labbcatService.labbcat.getSchema((schema, errors, messages) => {
            this.schema = schema;
            this.participantAttributes = [];
            this.transcriptAttributes = [];
            this.spanLayers = [];
            this.phraseLayers = [];
            this.wordLayers = [];
            this.segmentLayers = [];
            this.categorySelections = {};
            if (!this.selected) this.selected = [] as string[];
            for (let l in schema.layers) {
                let layer = schema.layers[l] as Layer;
                if (this.selected.includes(layer.id)) layer._selected = true;
                if (layer.id == schema.root.id) {
                    if (!this.excludeRoot) this.transcriptAttributes.push(layer);
                } else if (layer.id == "segment"
                    || layer.parentId == "segment") {
                    this.segmentLayers.push(layer);
                } else if (layer.id == schema.wordLayerId
                    || layer.parentId == schema.wordLayerId) {
                    this.wordLayers.push(layer);
                } else if (layer.id == schema.turnLayerId) {
                    if (!this.excludeTurn) this.phraseLayers.push(layer);
                } else if (layer.id == schema.utteranceLayerId) {
                    if (!this.excludeUtterance) this.phraseLayers.push(layer);
                } else if (layer.parentId == schema.turnLayerId) {
                    this.phraseLayers.push(layer);
                } else if (layer.id == schema.participantLayerId) {
                    if (!this.excludeParticipant) this.participantAttributes.push(layer);
                } else if (layer.id == "main_participant") {
                    if (!this.excludeMainParticipant) this.participantAttributes.push(layer);
                } else if (layer.parentId == schema.participantLayerId
                    && layer.alignment == 0
                    && layer.access == "1") { // only 'public' attributes
                    this.participantAttributes.push(layer);
                } else if (layer.id == schema.corpusLayerId) {
                    if (!this.excludeCorpus) this.transcriptAttributes.push(layer);
                } else if (layer.parentId == schema.root.id) {
                    if (layer.alignment == 0) {
                        this.transcriptAttributes.push(layer);
                    } else {
                        this.spanLayers.push(layer);
                    }
                }
            } // next layer

            // now list the categories that are present
            let allLayers = [];
            if (this.participant) allLayers = allLayers.concat(this.participantAttributes);
            if (this.transcript) allLayers = allLayers.concat(this.transcriptAttributes);
            if (this.span) allLayers = allLayers.concat(this.spanLayers);
            if (this.phrase) allLayers = allLayers.concat(this.phraseLayers);
            if (this.word) allLayers = allLayers.concat(this.wordLayers);
            for (let layer of allLayers) {
                if (layer.category && !this.categorySelections.hasOwnProperty(layer.category)) {
                    this.categorySelections[layer.category] = false;
                }
            }
        })
    }
    
    Categories(): string[] {
        return Object.keys(this.categorySelections || {});
    }

    ParticipantLayerLabel(id): string {
        if (id == this.schema.participantLayerId && this.scopeCount > 1) {
            return "Name"; // TODO i18n
        }
        else return id.replace(/^participant_/,"");
    }
    TranscriptLayerLabel(id): string {
        if (id == this.schema.root.id && this.scopeCount > 1) {
            return "Name"; // TODO i18n
        }
        return id.replace(/^transcript_/,"");
    }

    handleCheckbox(layerId:string): void {
        this.schema.layers[layerId]._selected = !this.schema.layers[layerId]._selected;
        if (this.schema.layers[layerId]._selected) { // is it now selected?
            this.selected.push(layerId); // add it
        } else { // no longer selected
            this.selected = this.selected.filter(l => l != layerId); // remove it
        }
        this.selectedChange.emit(this.selected);
    }
}

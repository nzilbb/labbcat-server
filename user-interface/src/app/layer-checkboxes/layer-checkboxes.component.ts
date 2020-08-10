import { Component, OnInit, Input } from '@angular/core';

import { LabbcatService } from '../labbcat.service';
import { Layer } from '../layer';

@Component({
  selector: 'app-layer-checkboxes',
  templateUrl: './layer-checkboxes.component.html',
  styleUrls: ['./layer-checkboxes.component.css']
})
export class LayerCheckboxesComponent implements OnInit {
    @Input() name: string;
    @Input() includeCounts: boolean;
    @Input() participant: boolean;
    @Input() excludeParticipant: boolean;
    @Input() excludeMainParticipant: boolean;
    participantAttributes: Layer[];
    @Input() transcript: boolean;
    @Input() excludeCorpus: boolean;
    transcriptAttributes: Layer[];
    @Input() excludeRoot: boolean;
    @Input() freeform: boolean;
    freeformLayers: Layer[];
    @Input() meta: boolean;
    @Input() excludeTurn: boolean;
    @Input() excludeUtterance: boolean;
    metaLayers: Layer[];
    @Input() word: boolean;
    wordLayers: Layer[];
    @Input() segment: boolean;
    segmentLayers: Layer[];
    @Input() selected: string[];
    schema;

    constructor(
        private labbcatService: LabbcatService
    ) { }

    ngOnInit(): void {
        this.loadSchema();
    }

    loadSchema(): void {
        this.labbcatService.labbcat.getSchema((schema, errors, messages) => {
            this.schema = schema;
            this.participantAttributes = [];
            this.transcriptAttributes = [];
            this.freeformLayers = [];
            this.metaLayers = [];
            this.wordLayers = [];
            this.segmentLayers = [];
            if (!this.selected) this.selected = [];
            for (let l in schema.layers) {
                let layer = schema.layers[l] as Layer;
                if (this.selected.includes(layer.id)) layer._selected = true;
                if (layer.id == schema.root.id) {
                    if (!this.excludeRoot) this.transcriptAttributes.push(layer);
                } else if (layer.id == "segments"
                    || layer.parentId == "segments") {
                    this.segmentLayers.push(layer);
                } else if (layer.id == schema.wordLayerId
                    || layer.parentId == schema.wordLayerId) {
                    this.wordLayers.push(layer);
                } else if (layer.id == schema.turnLayerId) {
                    if (!this.excludeTurn) this.metaLayers.push(layer);
                } else if (layer.id == schema.utteranceLayerId) {
                    if (!this.excludeUtterance) this.metaLayers.push(layer);
                } else if (layer.parentId == schema.turnLayerId) {
                    this.metaLayers.push(layer);
                } else if (layer.id == schema.participantLayerId) {
                    if (!this.excludeParticipant) this.participantAttributes.push(layer);
                } else if (layer.id == "main_participant") {
                    if (!this.excludeMainParticipant) this.participantAttributes.push(layer);
                } else if (layer.parentId == schema.participantLayerId
                    && layer.alignment == 0) {
                    this.participantAttributes.push(layer);
                } else if (layer.id == schema.corpusLayerId) {
                    if (!this.excludeCorpus) this.transcriptAttributes.push(layer);
                } else if (layer.parentId == schema.root.id) {
                    if (layer.alignment == 0) {
                        this.transcriptAttributes.push(layer);
                    } else {
                        this.freeformLayers.push(layer);
                    }
                }
            }
        })
    }

    ParticipantLayerLabel(id): string {
        if (id == this.schema.participantLayerId) return "Name";
        else return id.replace(/^participant_/,"");
    }
    TranscriptLayerLabel(id): string {
        if (id == this.schema.root.id) return "Name";
        else return id.replace(/^transcript_/,"");
    }

}

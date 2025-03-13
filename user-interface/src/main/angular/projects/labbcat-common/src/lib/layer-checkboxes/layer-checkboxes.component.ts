import { Component, OnInit, Input, Output, EventEmitter } from '@angular/core';
import { Inject } from '@angular/core';

import { LabbcatService } from '../labbcat.service';
import { Layer } from '../layer';

// TODO allow selective exclusion of layers by list

@Component({
  selector: 'lib-layer-checkboxes',
  templateUrl: './layer-checkboxes.component.html',
  styleUrls: ['./layer-checkboxes.component.css']
})
export class LayerCheckboxesComponent implements OnInit {
    /** optional already-loaded schema */
    @Input() schema: any;
    /** name attribute for checkboxes */
    @Input() name: string;
    /** Allow an annotation count to be specified when a layer is selected */
    @Input() includeCounts: boolean;
    /** Allow start/end anchoring to be specified when a layer is selected */
    @Input() includeAnchorSharing: boolean;
    /** Allow the relationship (containing or contained) to be specified when a spanning
        layer is selected */ 
    @Input() includeRelationship: boolean;
    /** Show the data type of each layer */
    @Input() includeDataType: boolean;
    /** Show the alignment of each layer */
    @Input() includeAlignment: boolean;
    /** Show alignment as 0 for turn/word/segment */
    @Input() spoofAlignment: boolean;
    /** Show whether each layer allows vertical peers */
    @Input() includeVerticalPeers: boolean;
    /** Allow participant attribute layers to be selected */
    @Input() participant: boolean;
    /** Don't allow the 'participant' layer to be selected */
    @Input() excludeParticipant: boolean;
    /** Don't allow the 'main_participant' layer to be selected */
    @Input() excludeMainParticipant: boolean;
    /** Allow transcript attributes to be selected */
    @Input() transcript: boolean;
    /** Don't allow the 'corpus' layer to be selected */
    @Input() excludeCorpus: boolean;
    /** Don't allow the 'transcript' top-level layer to be selected */
    @Input() excludeRoot: boolean;
    /** Allow span layers to be selected */
    @Input() span: boolean;
    /** Allow phrase layers to be selected */
    @Input() phrase: boolean;
    /** Don't allow the 'turn' layer to be selected */
    @Input() excludeTurn: boolean;
    /** Don't allow the 'utterance' layer to be selected */
    @Input() excludeUtterance: boolean;
    /** Don't allow the 'word' layer to be selected */
    @Input() excludeWord: boolean;
    /** Include a layer category selector, to hide/reveal layers */
    @Input() category: boolean;
    /** Allow word layers to be selected */
    @Input() word: boolean;
    /** Allow segment layers to be selected */
    @Input() segment: boolean;
    /** Layer styles - key is the layerId, value is the CSS style definition for the layer */
    @Input() styles: { [key: string] : any };
    /** A layer ID to exclude options (annotation count, anchoring, etc.) for */
    @Input() excludeOptionsForLayerId: string;
    /** Input list of IDs of selected (ticked) layers */
    @Input() selected: string[];
    /** Output list of IDs of selected (ticked) layers */
    @Output() selectedChange = new EventEmitter<string[]>();
    /** Input list of layers with interpreted (true) or raw (false) labels */
    @Input() interpretedRaw: { [key: string] : any };
    /** Output list of layers with interpreted (true) or raw (false) labels */
    @Output() interpretedRawChange = new EventEmitter<{ [key: string] : any }>();
    
    participantAttributes: Layer[];
    transcriptAttributes: Layer[];
    spanLayers: Layer[];
    phraseLayers: Layer[];
    wordLayers: Layer[];
    segmentLayers: Layer[];
    imagesLocation : string;
    scopeCount = 0;
    categorySelections: any;

    constructor(
        private labbcatService: LabbcatService,
        @Inject('environment') private environment
    ) {
        this.imagesLocation = this.environment.imagesLocation;
    }

    ngOnInit(): void {
        this.scopeCount = 0;
        if (this.schema) {
            this.processSchema();
        } else {
            this.loadSchema();
        }
        if (this.participant) this.scopeCount++;
        if (this.transcript) this.scopeCount++;
        if (this.span) this.scopeCount++;
        if (this.phrase) this.scopeCount++;
        if (this.word) this.scopeCount++;
        if (this.segment) this.scopeCount++;
        if (!this.styles) this.styles = {};
        if (!this.interpretedRaw) this.interpretedRaw = {};
    }

    loadSchema(): void {
        this.labbcatService.labbcat.getSchema((schema, errors, messages) => {
            this.schema = schema;
            this.processSchema();
        })
    }
    processSchema(): void {
        this.participantAttributes = [];
        this.transcriptAttributes = [];
        this.spanLayers = [];
        this.phraseLayers = [];
        this.wordLayers = [];
        this.segmentLayers = [];
        this.categorySelections = {};
        if (!this.selected) this.selected = [] as string[];

        // add category selectors in defined order
        for (let c in this.schema.categories) {
            if (c.startsWith("participant_")) { // participant attribute category
                if (this.participant) this.categorySelections[c] = false;
            } else if (c.startsWith("transcript_")) {  // transcript attribute category
                if (this.transcript) this.categorySelections[c] = false;
            } else  { // temporal layer category/project
                if (this.span || this.phrase || this.word) {
                     this.categorySelections[c] = false;
                }
            }

        } // next category
        
        for (let l in this.schema.layers) {
            let layer = this.schema.layers[l] as Layer;
            if (this.selected.includes(layer.id)) {
                layer._selected = true;
                if (layer.category) {
                    this.categorySelections[layer.category] = true;
                }
            }
            if (layer.id == this.schema.root.id) {
                if (!this.excludeRoot) this.transcriptAttributes.push(layer);
            } else if (layer.id == "segment"
                || layer.parentId == "segment") {
                this.segmentLayers.push(layer);
            } else if (layer.id == this.schema.wordLayerId) {
                if (!this.excludeWord) this.wordLayers.push(layer);
            } else if (layer.parentId == this.schema.wordLayerId) {
                this.wordLayers.push(layer);
            } else if (layer.id == this.schema.turnLayerId) {
                if (!this.excludeTurn) this.phraseLayers.push(layer);
            } else if (layer.id == this.schema.utteranceLayerId) {
                if (!this.excludeUtterance) this.phraseLayers.push(layer);
            } else if (layer.parentId == this.schema.turnLayerId) {
                this.phraseLayers.push(layer);
            } else if (layer.id == this.schema.participantLayerId) {
                if (!this.excludeParticipant) this.participantAttributes.push(layer);
            } else if (layer.id == "main_participant") {
                if (!this.excludeMainParticipant) this.participantAttributes.push(layer);
            } else if (layer.parentId == this.schema.participantLayerId
                && layer.alignment == 0
                && layer.access == "1") { // only 'public' attributes
                this.participantAttributes.push(layer);
            } else if (layer.id == this.schema.corpusLayerId) {
                if (!this.excludeCorpus) this.transcriptAttributes.push(layer);
            } else if (layer.parentId == this.schema.root.id) {
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
    IsAnchorable(layer: Layer): boolean {
        return this.includeAnchorSharing // only if enabled
            && layer.id != this.schema.turnLayerId // not system layers...
            && layer.id != this.schema.utteranceLayerId
            && layer.id != this.schema.wordLayerId
            && layer.peers // multiple child annotations
            && layer.alignment == 2; // inntervals
    }

    handleCheckbox(layerId:string): void {
        this.schema.layers[layerId]._selected = !this.schema.layers[layerId]._selected;
        if (this.schema.layers[layerId]._selected) { // is it now selected?
            // add it, but in hierarchy order
            const newSelected = [];
            for (let layer of this.spanLayers) {
                if (layer._selected) newSelected.push(layer.id);
            }
            for (let layer of this.phraseLayers) {
                if (layer._selected) newSelected.push(layer.id);
            }
            for (let layer of this.wordLayers) {
                if (layer._selected) newSelected.push(layer.id);
            }
            for (let layer of this.segmentLayers) {
                if (layer._selected) newSelected.push(layer.id);
            }
            this.selected = newSelected;
        } else { // no longer selected
            this.selected = this.selected.filter(l => l != layerId); // remove it
        }
        this.selectedChange.emit(this.selected);
    }
    handleInterpretedRaw(layerId:string): void {
        this.interpretedRaw[layerId] = !this.interpretedRaw[layerId];
        this.interpretedRawChange.emit(this.interpretedRaw);
    }
}

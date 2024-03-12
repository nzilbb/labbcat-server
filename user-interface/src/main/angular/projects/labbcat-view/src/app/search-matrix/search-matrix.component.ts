import { Component, OnInit, OnChanges, SimpleChanges, Input, EventEmitter, Output } from '@angular/core';

import { Layer } from 'labbcat-common';
import { MatrixColumn } from '../matrix-column';
import { MatrixLayerMatch } from '../matrix-layer-match';

@Component({
  selector: 'app-search-matrix',
  templateUrl: './search-matrix.component.html',
  styleUrls: ['./search-matrix.component.css']
})
export class SearchMatrixComponent implements OnInit, OnChanges {
    @Input() schema: any;
    @Input() selectedLayerIds: string[];
    @Output() selectedLayerIdsChange = new EventEmitter<string[]>();
    @Input() columns: MatrixColumn[];
    @Output() columnsChange = new EventEmitter<MatrixColumn[]>();

    helperMatch: MatrixLayerMatch;
    
    constructor() { }
    
    ngOnInit(): void {
    }

    ngOnChanges(changes: SimpleChanges): void {
        console.log("ngOnChanges " + Object.keys(changes));
        // if we haven't set selectedLayerIds yet
        if (this.schema && !this.selectedLayerIds) {
            // if there's no search matrix yet
            if (this.columns.length == 0) {
                // default to a word search (preferably orthography)
                const defaultLayerId = this.schema.layers["orthography"]?
                    "orthography":this.schema.wordLayerId;
                const layers = {};
                layers[defaultLayerId] = [this.newLayerMatch(defaultLayerId)];
                this.columns.push({
                    layers: layers,
                    adj: 1
                });
            }
            
            // reconstruct selectedLayerIds from the matrix
            this.selectedLayerIds = [];
            for (let column of this.columns) {
                for (let layerId in column.layers) {
                    if (!this.selectedLayerIds.includes(layerId)) {
                        this.selectedLayerIds.push(layerId);
                    }
                } // next layer in column
            } // next column
        }
    }

    // ensures that the matrix columns have the same layers as selectedLayerIds
    syncSelectedLayerIdsWithColumns(selectedLayerIds : string[]) : void {
        // do we have a target?
        let target = this.getTarget();
        
        for (let column of this.columns) { // each column

            // add newly selected layers
            for (let layerId of selectedLayerIds) { // each selected layer
                if (!column.layers[layerId]) { // the column doesn't include the layer
                    // create a match for this layer
                    const newMatch = this.newLayerMatch(layerId);
                    if (!target) { // if there's no target yet
                        // could this be the target?
                        if (this.schema.layers[layerId].alignment > 0) { // yep
                            newMatch.target = true;
                            target = newMatch;
                        }                        
                    }
                    // add the layer to the column
                    column.layers[layerId] = [newMatch];
                }
            } // next selected layer

            // remove newly unselected layers
            for (let layerId in column.layers) { // each column layer
                if (!selectedLayerIds.includes(layerId)) { // the layer isn't selected
                    // remove it from the matrix
                    delete column.layers[layerId];
                } // the layer isn't selected
            } // next column layer
            
        } // next column
    }

    setTarget(targetMatch: MatrixLayerMatch): void {
        // ensure only one match is marked as the target
        for (let column of this.columns) { // each column
            for (let layerId in column.layers) { // each layer
                for (let match of column.layers[layerId]) { // each match
                    if (match == targetMatch) {
                        match.target = true;
                    } else {
                        delete match.target;
                    }
                } // next match
            } // next layer
        } // next column
    }
    
    getTarget(): MatrixLayerMatch {
        for (let column of this.columns) { // each column
            for (let layerId in column.layers) { // each layer
                for (let match of column.layers[layerId]) { // each match
                    if (match.target) {
                        return match;
                    }
                } // next match
            } // next layer
        } // next column
        return null;
    }

    newLayerMatch(layerId: string): MatrixLayerMatch {
        return {
            id: layerId,
            pattern: "",
            not: false,
            min: null,
            max: null,
            anchorStart: false,
            anchorEnd: false,
            target: false 
        };
    }

    addColumn(): void {
        this.columns.push({
            layers: [],
            adj: 1
        });
        this.syncSelectedLayerIdsWithColumns(this.selectedLayerIds);
    }
    removeColumn(): void {
        this.columns.pop();
    }

    appendToPattern(match: MatrixLayerMatch, suffix: string): void {
        match.pattern += suffix;
    }

    hasValidLabels(layer: Layer): boolean {
        return layer.validLabels && Object.keys(layer.validLabels).length > 0;
    }

    isSpanningLayer(layer: Layer): boolean {
        return layer.alignment == 2
            && (layer.parentId == this.schema.root.id
                || layer.parentId == this.schema.participantLayerId
                || layer.parentId == this.schema.turnLayerId)
            && layer.id != this.schema.wordLayerId;
    }

}

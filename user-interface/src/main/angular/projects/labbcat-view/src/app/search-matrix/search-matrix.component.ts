import { Component, OnInit, OnChanges, SimpleChanges, Input, EventEmitter, Output } from '@angular/core';

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
        for (let column of this.columns) { // each column

            // add newly selected layers
            for (let layerId of selectedLayerIds) { // each selected layer
                if (!column.layers[layerId]) { // the column doesn't include the layer
                    // add the layer to the column
                    column.layers[layerId] = [this.newLayerMatch(layerId)];
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
            adj: 2
        });
        this.syncSelectedLayerIdsWithColumns(this.selectedLayerIds);
    }
    removeColumn(): void {
        this.columns.pop();
    }

}

import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';

import { MessageService, LabbcatService, Response, Layer } from 'labbcat-common';
import { AdminComponent } from '../admin-component';

@Component({
  selector: 'app-admin-layer-labels',
  templateUrl: './admin-layer-labels.component.html',
  styleUrls: ['./admin-layer-labels.component.css']
})
export class AdminLayerLabelsComponent extends AdminComponent implements OnInit {
    layerId: string;
    layer: Layer;
    labels: string[];
    
    constructor(
        labbcatService: LabbcatService,
        messageService: MessageService,
        private route: ActivatedRoute,
        private router: Router
    ) {
        super(labbcatService, messageService);
    }
    
    ngOnInit(): void {
        this.layerId = this.route.snapshot.paramMap.get('layerId');
        if (this.layerId) { // layerId is part of the URL
            this.readLayer();
        } else { // layerId is a request parameter
            this.route.queryParams.subscribe((params) => {
                this.layerId = params["layerId"];
                this.readLayer();
            });
        }
    }

    readLayer(): void {
        this.labbcatService.labbcat.getLayer(this.layerId, (layer, errors, messages) => {
            if (errors) errors.forEach(m => this.messageService.error(m));
            if (messages) messages.forEach(m => this.messageService.info(m));
            this.setLayer(layer as Layer);
        });
    }

    setLayer(layer: Layer): void {
        this.layer = layer as Layer;
        this.labels = [];
        for (let label in layer.validLabels) {
            this.labels.push(label);
        }
        this.changed = false;
    }

    createRow(newLabel: string): boolean {
        let labels = [ newLabel ];        
        // if the label is actually lots of labels, add them all
        let possibleLabels = newLabel.split("\n");
        if (possibleLabels.length > 1) {
            labels = possibleLabels;
        } else {
            possibleLabels = newLabel.split(" ");
            if (possibleLabels.length > 3) {
                labels = possibleLabels;
            }
        }
        let somethingAdded = false;
        for (let label of labels) {
            if (!label) continue; // skip blank labels
            if (this.labels.indexOf(label) >= 0) {
                this.messageService.error("Already exists: " + label); // TODO i18n
            } else {
                this.layer.validLabels[label] = label;
                this.labels.push(label);
                somethingAdded = true;
            }
        } // next label
        this.changed = somethingAdded;
        return somethingAdded;
    }
    
    deleteRow(toDelete: string): boolean {
        const t = this.labels.indexOf(toDelete);
        let found = false;
        if (t > -1) {
            this.labels.splice(t, 1);
            found = true;
        }
        this.changed = found;
        return found;
    }
    
    onChange() {
        this.changed = true;        
    }
    
    updating = false;
    updateChangedRows() {
        this.updating = true;
        const newValidLabels = {};
        for (let label of this.labels) {
            // for tanscript_type, label and description are the same
            newValidLabels[label] = this.layer.validLabels[label] || label;
        }
        this.layer.validLabels = newValidLabels;
        this.labbcatService.labbcat.saveLayer(
            this.layer, (layer, errors, messages) => {
                this.updating = false;
                this.changed = false;        
                if (errors) errors.forEach(m => this.messageService.error(m));
                if (messages) messages.forEach(m => this.messageService.info(m));
                if (layer) {
                    if (!messages) {
                        this.messageService.info("Updated valid labels"); // TODO i18n
                    }
                    this.setLayer(layer);
                } else {
                    this.readLayer();
                }
            });
    }
}

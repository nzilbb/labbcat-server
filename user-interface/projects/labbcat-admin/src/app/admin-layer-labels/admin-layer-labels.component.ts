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
        this.readLayer(this.route.snapshot.paramMap.get('layerId'));
    }

    readLayer(layerId: string): void {
        this.labbcatService.labbcat.getLayer(layerId, (layer, errors, messages) => {
            if (errors) errors.forEach(m => this.messageService.error(m));
            if (messages) messages.forEach(m => this.messageService.info(m));
            this.setLayer(layer as Layer);
        });
    }

    setLayer(layer: Layer): void {
        this.layer = layer as Layer;
        this.labels = [];
        for (let label in layer.validLabels) {
            // for tanscript_type, label and description are the same
            this.labels.push(label);
        }
        this.changed = false;
    }

    createRow(newLabel: string): boolean {
        let labels = [ newLabel ];        
        // if the label is actually lots of labels, add them all
        let possibleLabels = newLabel.split("\n");
        if (possibleLabels.length > 3) {
            labels = possibleLabels;
        } else {
            possibleLabels = newLabel.split(" ");
            if (possibleLabels.length > 3) {
                labels = possibleLabels;
            }
        }
        let somethingAdded = false;
        for (let label of labels) {
            if (this.labels.indexOf(label) >= 0) {
                this.messageService.error("Already exists: " + label); // TODO i18n
            } else {
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
        this.layer.validLabels = {};
        for (let label of this.labels) {
            // for tanscript_type, label and description are the same
            this.layer.validLabels[label] = label;
        }
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
                    this.readLayer(layer.id);
                }
            });
    }
}

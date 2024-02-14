import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, ParamMap } from '@angular/router';
import { environment } from '../../environments/environment';
import { switchMap } from 'rxjs/operators';

import { Category } from '../category';
import { MessageService, LabbcatService, Response, Layer } from 'labbcat-common';
import { AdminComponent } from '../admin-component';

@Component({
  selector: 'app-admin-layers',
  templateUrl: './admin-layers.component.html',
  styleUrls: ['./admin-layers.component.css']
})
export class AdminLayersComponent extends AdminComponent implements OnInit {
    scope: string;
    schema: any;
    rows: Layer[];
    layerManagers: any[];
    layerManagerLookup = {};
    categories: string[];

    newLayerId = "";
    managerFilter = "";
    categoryFilter = "";
    imagesLocation = environment.imagesLocation;
    
    constructor(
        labbcatService: LabbcatService,
        messageService: MessageService,
        private route: ActivatedRoute
    ) {
        super(labbcatService, messageService);
    }
    
    ngOnInit(): void {
        this.readCategories();
        this.readLayerManagers().then(()=>{
            this.readRows();
        });
        // need to subscribe to URL path changes, because the component will be re-used
        // from scope to scope
        this.route.paramMap.pipe(
            switchMap((params: ParamMap) => params.get('scope'))
        ).subscribe(scope => {
            this.readLayerManagers().then(()=>{
                this.readRows();
            })
        });
    }

    readCategories(): void {
        this.labbcatService.labbcat.readCategories("layer", (categories, errors, messages) => {
            if (errors) errors.forEach(m => this.messageService.error(m));
            if (messages) messages.forEach(m => this.messageService.info(m));
            this.categories = categories.map(c => c.category);
        });
    }
    
    readLayerManagers(): Promise<void> {
        return new Promise((resolve,reject) => {
            this.labbcatService.labbcat.getLayerManagers((managers, errors, messages) => {
                if (errors) errors.forEach(m => this.messageService.error(m));
                if (messages) messages.forEach(m => this.messageService.info(m));
                let matchesScope = m => /.*W.*/.test(m.layer_type);
                if (this.scope == "phrase") {
                    matchesScope = m => /.*M.*/.test(m.layer_type);
                } else if (this.scope == "span") {
                    matchesScope = m => /.*F.*/.test(m.layer_type);
                } else if (this.scope == "segment") {
                    matchesScope = m => /.*S.*/.test(m.layer_type);
                }
                this.layerManagers = managers
                    .filter(matchesScope);
                this.layerManagers.forEach(m=> {
                    this.layerManagerLookup[m.layer_manager_id] = m;
                });
                resolve();
            });
        });
    }
    
    readRows(): void {
        this.rows = null;
        this.scope = this.route.snapshot.paramMap.get('scope');
        this.labbcatService.labbcat.getSchema((schema, errors, messages) => {
            this.schema = schema;
            if (errors) errors.forEach(m => this.messageService.error(m));
            if (messages) messages.forEach(m => this.messageService.info(m));
            this.rows = [];
            if (schema) {
                let isLayerInScope = l => l.parentId == schema.wordLayerId && l.id != "segment";
                if (this.scope == "phrase") {
                    isLayerInScope = l => l.parentId == schema.turnLayerId
                        && l.id != schema.wordLayerId && l.id != schema.utteranceLayerId;
                } else if (this.scope == "span") {
                    isLayerInScope = l => l.parentId == schema.root.id
                        && l.alignment > 0;
                } else if (this.scope == "segment") {
                    isLayerInScope = l => l.parentId == "segment" || l.id == "segment";
                }
                for (let l in schema.layers) {
                    let layer = schema.layers[l] as Layer;
                    if (isLayerInScope(layer)) {
                        // make sure category is at least an empty string
                        layer.category = layer.category || "";
                        this.rows.push(layer);
                    }
                }
            }
        });
    }
    
    onChange(row: Layer) {
        row._changed = this.changed = true;        
    }

    creating = false;
    createRow(name: string, labelType: string, alignment: string, manager: string, enabled: string, category: string, description: string): boolean {
        this.creating = true;
        
        let layer = {
            id: name,
            description: description,
            parentId: this.schema.wordLayerId,
            alignment: Number(alignment),
            peers: true,
            peersOverlap: true,
            parentIncludes: true,
            saturated: alignment == "0",
            type: labelType,
            validLabels: {},
            category: category,
            enabled: enabled,
            layer_manager_id: manager
        };
        
        if (!manager) { // not managed, so no generation triggered
            layer.enabled = " ";
        } else { // managed
            if (!enabled) { // but no enablement specified
                // default to always
                layer.enabled = "WTL";
            }
        }
        switch (this.scope) {
            case "phrase":  { layer.parentId = this.schema.turnLayerId; break; }
            case "span":    { layer.parentId = this.schema.root.id; break; }
            case "segment": { layer.parentId = "segment"; break; }
        }
        this.labbcatService.labbcat.newLayer(layer, (row, errors, messages) => {
            this.creating = false;
            if (errors) errors.forEach(m => this.messageService.error(m));
            if (messages) messages.forEach(m => this.messageService.info(m));
            // update the model with the field returned
            if (row) this.rows.push(row as Layer);
            this.updateChangedFlag();

            // if it's a managed layer, go to configuration page
            if (layer.layer_manager_id) {
                document.location.href = 
                    `${environment.baseUrl}admin/layers/configure?id=${layer.id}`;
            }
        });
        return true;
    }

    deleteRow(row: Layer) {
        row._deleting = true;
        if (confirm(`Are you sure you want to delete ${row.id}`)) { // TODO i18n
            this.labbcatService.labbcat.deleteLayer(
                row.id, (model, errors, messages) => { 
                row._deleting = false;
                if (errors) errors.forEach(m => this.messageService.error(m));
                if (messages) messages.forEach(m => this.messageService.info(m));
                if (!errors) {
                    // remove from the model/view
                    this.rows = this.rows.filter(r => { return r !== row;});
                    this.updateChangedFlag();
                }});
        } else {
            row._deleting = false;
        }
    }
    
    updateChangedRows() {
        this.rows
            .filter(r => r._changed)
            .forEach(r => this.updateRow(r));
    }

    updating = 0;
    updateRow(row: Layer) {
        this.updating++;
        console.log(JSON.stringify(row));
        this.labbcatService.labbcat.saveLayer(row, (layer, errors, messages) => {
            this.updating--;
            if (errors) errors.forEach(m => this.messageService.error(m));
            if (messages) messages.forEach(m => this.messageService.info(m));
            // update the model with the field returned
            const updatedRow = layer as Layer;
            const i = this.rows.findIndex(r => {
                return r.id == updatedRow.id; })
            this.rows[i] = updatedRow;
            this.updateChangedFlag();
        });
    }
    
    updateChangedFlag() {
        this.changed = false;
        for (let row of this.rows) {
            if (row._changed) {
                this.changed = true;
                break; // only need to find one
            }
        } // next row
    }

    lookupLayerManager(id: string): string {
        if (!this.layerManagerLookup) return id;
        if (!this.layerManagerLookup[id]) return id;
        return this.layerManagerLookup[id].name;
    }
}

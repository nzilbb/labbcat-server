import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, ParamMap } from '@angular/router';
import { environment } from '../../environments/environment';
import { switchMap } from 'rxjs/operators';

import { MessageService, LabbcatService, Response, Layer } from 'labbcat-common';
import { AdminComponent } from '../admin-component';

@Component({
  selector: 'app-admin-attributes',
  templateUrl: './admin-attributes.component.html',
  styleUrls: ['./admin-attributes.component.css']
})
export class AdminAttributesComponent extends AdminComponent implements OnInit {
    
    baseUrl: string;
    scope: string;
    class_id: string;
    schema: any;
    rows: Layer[];
    layerManagers: any[];
    layerManagerLookup = {};
    categories: string[];
    
    newAttributeId = "";
    categoryFilter = "";
    searchabilityFilter = "";
    imagesLocation = environment.imagesLocation;
    
    constructor(
        labbcatService: LabbcatService,
        messageService: MessageService,
        private route: ActivatedRoute
    ) {
        super(labbcatService, messageService);
    }
    
    ngOnInit(): void {
        this.readBaseUrl();
        this.readRows();
        // need to subscribe to URL path changes, because the component will be re-used
        // from scope to scope
        this.route.paramMap.pipe(
            switchMap((params: ParamMap) => params.get('scope'))
        ).subscribe(scope => {
            this.readRows();
        });
    }

    readBaseUrl(): void {
        this.labbcatService.labbcat.getId((url, errors, messages) => {
            this.baseUrl = url;
        });
    }

    readRows(): void {
        this.rows = null;
        this.scope = this.route.snapshot.paramMap.get('scope');
        this.class_id = this.scope == "participant"?"speaker":this.scope;
        this.categories = [];
        this.labbcatService.labbcat.getSchema((schema, errors, messages) => {
            this.schema = schema;
            if (errors) errors.forEach(m => this.messageService.error(m));
            if (messages) messages.forEach(m => this.messageService.info(m));
            this.rows = [];
            if (schema) {
                let isLayerInScope = l => l.class_id == this.class_id;
                for (let l in schema.layers) {
                    let layer = schema.layers[l] as Layer;
                    if (isLayerInScope(layer)) {
                        // make sure category is set
                        layer.category = layer.category || "General";
                        if (!this.categories.includes(layer.category)) {
                            this.categories.push(layer.category);
                        }
                        layer = this.parseStyle(layer)
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
    createRow(name: string, labelType: string, label: string, access: string, filter: string, category: string, description: string): boolean {
        this.creating = true;
        let layer = {
            // all layers have these attributes:
            id: `${this.scope}_${name}`,
            description: label,
            parentId: this.scope,
            alignment: 0,
            peers: false,
            peersOverlap: true,
            parentIncludes: true,
            saturated: true,
            type: (labelType=="number"||labelType=="integer")?"number"
                :(labelType=="boolean")?"boolean"
                :"string",
            subtype: labelType,
            validLabels: {},
            category: category||this.categories[0],
            // attribute layers have these extra attributes:
            class_id: this.class_id,
            attribute: name,
            hint: description,
            searchable: filter||"0",
            access: access||"0",
            style: ""
        };
        this.labbcatService.labbcat.newLayer(layer, (layer, errors, messages) => {
            this.creating = false;
            if (errors) errors.forEach(m => this.messageService.error(m));
            if (messages) messages.forEach(m => this.messageService.info(m));
            // update the model with the field returned
            if (layer) {
                layer = this.parseStyle(layer)
                this.rows.push(layer as Layer);
            }
            this.updateChangedFlag();

            // if it's a select layer, go to validLabels page
            if (layer.type == "select") {
                document.location.href = 
                    `${environment.baseUrl}admin/layers/validLabels/${layer.id}`;
            }
        });
        return true;
    }

    deleteRow(row: Layer) {
        row._deleting = true;
        if (confirm(`Are you sure you want to delete ${row.attribute}`)) {
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
    
    move(direction: string, row: Layer) {
        let indexToMoveUp = this.rows.indexOf(row);
        let indexToMoveDown = indexToMoveUp - 1;
        if (direction == "down") {
            indexToMoveDown = indexToMoveUp;
            indexToMoveUp = indexToMoveUp + 1;
        }
        const toMoveUp = this.rows[indexToMoveUp];
        const toMoveDown = this.rows[indexToMoveDown];
        const newDisplayOrder = toMoveDown.display_order;
        toMoveDown.display_order = toMoveUp.display_order;
        toMoveUp.display_order = newDisplayOrder;
        this.rows[indexToMoveUp] = toMoveDown;
        this.rows[indexToMoveDown] = toMoveUp;
        this.onChange(toMoveUp);
        this.onChange(toMoveDown);
    }
    
    updateChangedRows() {
        this.rows
            .filter(r => r._changed)
            .forEach(r => this.updateRow(r));
    }

    updating = 0;
    updateRow(row: Layer) {
        this.updating++;
        row = this.compileStyle(row)
        this.labbcatService.labbcat.saveLayer(row, (layer, errors, messages) => {
            this.updating--;
            if (errors) errors.forEach(m => this.messageService.error(m));
            if (messages) messages.forEach(m => this.messageService.info(m));
            if (!messages || !messages.length) {
                this.messageService.info(`Updated ${row.id}`); // TODO i18n
            }
            // update the model with the field returned
            const updatedRow = this.parseStyle(layer as Layer);
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

    /**
     * Convert layer.style into sub-attributes:
     *  size, min, max, minLabel, maxLabel, slider, other, multiple, radio
     */
    parseStyle(layer: Layer): Layer {
        let style = layer.style;
        if (style) { // parse the style
            layer.other = /other/.test(style);
 	    style = style.replace("other","");
            layer.multiple = /multiple/.test(style);
 	    style = style.replace("multiple","");
            layer.radio = /radio/.test(style);
 	    style = style.replace("radio","");

            layer.slider = /slider/.test(style);
 	    style = style.replace("slider","");
 	    layer.size = null;
 	    layer.min = null;
 	    layer.max = null;
 	    layer.minLabel = null;
 	    layer.maxLabel = null;
            if (layer.subtype.startsWith("date")) { // yyyy-yyyy
                let years = style.trim().split("-");
 	        if (years.length == 1) { // try yyyy yyyy (legacy style)
                    years = style.trim().split(" ");
                }
 	        if (years.length == 2) {
 	            layer.min = Number(years[0]);
 	            layer.max = Number(years[1]);
 	        }
            } else { // not a date
 	        let space = style.indexOf(" ");
 	        if (space >= 0) {
 	            if (space >= 1) layer.size = Number(style.substring(0, space));
 	            style = style.substring(space+1);
 	        } else {
                    layer.size = Number(style);
                    style = "";
                }
 	        space = style.indexOf(" ");
 	        let range = (space >= 0?style.substring(0, space):style);
 	        if (range != "" && range != "-") {
 	            const hyphen = range.indexOf('-');
 	            if (hyphen >= 0) {
 	                layer.min = Number(range.substring(0, hyphen));
 	                layer.max = Number(range.substring(hyphen + 1));
 	            }
 	        }
 	        if (space >= 0) {
 	            style = style.substring(space+1);
 	            space = style.indexOf(" ");
 	            range = (space >= 0?style.substring(0, space):style);
 	            if (range != "") {
 	                const bar = range.indexOf('|');
 	                if (bar >= 0) {
 		            layer.minLabel = range.substring(0, bar);
 		            layer.maxLabel = range.substring(bar + 1);
 	                }
 	            }
 	        }
            } // not a date
        } // parse the style
        return layer;
    }
    
    /**
     * Convert style sub-attributes into value for label.style:
     *  size, min, max, minLabel, maxLabel, slider, other, multiple, radio
     */
    compileStyle(layer: Layer): Layer {
        layer.style = "";
        if (layer.size) layer.style += layer.size;
        if (layer.min || layer.max) {
            layer.style += " "
            if (layer.min) layer.style += layer.min;
            layer.style += "-"
            if (layer.max) layer.style += layer.max;
            if (layer.minLabel || layer.maxLabel) {
                layer.style += " "
                if (layer.minLabel) layer.style += layer.minLabel;
                layer.style += "|"
                if (layer.maxLabel) layer.style += layer.maxLabel;
            }
        }
        if (layer.slider) layer.style += " slider";
        if (layer.other) layer.style += " other";
        if (layer.multiple) layer.style += " multiple";
        if (layer.radio) layer.style += " radio";
        return layer;
    }
}

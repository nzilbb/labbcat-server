import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';

import { Response } from '../response';
import { Layer } from '../layer';
import { RolePermission } from '../role-permission';
import { MessageService } from '../message.service';
import { LabbcatService } from '../labbcat.service';

@Component({
  selector: 'app-admin-role-permissions',
  templateUrl: './admin-role-permissions.component.html',
  styleUrls: ['./admin-role-permissions.component.css']
})
export class AdminRolePermissionsComponent implements OnInit {
    attributes: Layer[];
    validEntities = {
        tiav : "Everything",
        t : "Transcript",
        iav : "Media",
        i : "Image",
        a : "Audio",
        v : "Video"
    };
    get availableEntityValues(): string[] {
        // try to ensure that only entities that are not already covered are available
        const existingEntities = this.rows.map(p=>p.entity);
        return Object.keys(this.validEntities).filter(
            // filter out matching entities
            validEntity=>!existingEntities.find(
                existingEntity=>{
                    // if the existing entity includes this valid entity
                    return new RegExp(existingEntity).test(validEntity)
                    // or the this valid entity includes the existing entity
                        || new RegExp(validEntity).test(existingEntity)
                }));
    }
    rows: RolePermission[];
    get existingEntities(): string[] { return this.rows.map(permission=>permission.entity); }
    role_id: string;
    changed = false;
    constructor(
        private labbcatService: LabbcatService,
        private messageService: MessageService,
        private route: ActivatedRoute
    ) { }
    
    ngOnInit(): void {
        this.readAttributes();
        this.readRows();
    }

    readAttributes(): void {
        this.labbcatService.labbcat.getLayers((layers, errors, messages) => {
            this.attributes = [];
            for (let layer of layers) {
                if (layer.id == "corpus" // corpus
                    || (layer.parentId == "graph" // or transcript tag
                        && layer.alignment == 0
                        && layer.saturated
                        && layer.id != "transcript_type" // (not transcript_type TODO fix this)
                        && layer.id != "episode" // (not episode)
                        && layer.id != "participant")) { // (but not participant layer)
                    layer.id = layer.id.replace(/^transcript_/,"");
                    this.attributes.push(layer as Layer);                        
                }
            } // next layer
        });
    }

    readRows(): void {
        this.role_id = this.route.snapshot.paramMap.get('role_id');
        this.labbcatService.labbcat.readRolePermissions(
            this.role_id, (permissions, errors, messages) => {
                this.rows = [];
                for (let permission of permissions) {
                    this.rows.push(permission as RolePermission);
                }
            });
    }
    
    onChange(row: RolePermission) {
        row._changed = this.changed = true;        
    }
    
    createRow(entity: string, layer: string, value_pattern: string) : boolean {
        this.labbcatService.labbcat.createRolePermission(
            this.role_id, entity, layer, value_pattern,
            (row, errors, messages) => {
                if (errors) for (let message of errors) this.messageService.error(message);
                if (messages) for (let message of messages) this.messageService.info(message);
                // update the model with the field returned
                if (row) this.rows.push(row as RolePermission);
                this.updateChangedFlag();
            });
        return true;
    }
    
    deleteRow(row: RolePermission) {
        if (confirm(`Are you sure you want to delete ${this.validEntities[row.entity]}`)) {
            this.labbcatService.labbcat.deleteRolePermission(
                this.role_id, row.entity, (model, errors, messages) => {
                    if (errors) for (let message of errors) this.messageService.error(message);
                    if (messages) for (let message of messages) this.messageService.info(message);
                    if (!errors) {
                        // remove from the model/view
                        this.rows = this.rows.filter(r => { return r !== row;});
                        this.updateChangedFlag();
                    }});
        }
    }
    
    updateChangedRows() {
        this.rows
            .filter(r => r._changed)
            .forEach(r => this.updateRow(r));
    }
    
    updateRow(row: RolePermission) {
        this.labbcatService.labbcat.updateRolePermission(
            this.role_id, row.entity, row.attribute_name, row.value_pattern,
            (permission, errors, messages) => {
                if (errors) for (let message of errors) this.messageService.error(message);
                if (messages) for (let message of messages) this.messageService.info(message);
                // update the model with the field returned
                const updatedRow = permission as RolePermission;
                const i = this.rows.findIndex(r => {
                    return r.entity == updatedRow.entity; })
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
}

import { Component, OnInit } from '@angular/core';

import { Role } from '../role';
import { MessageService, LabbcatService, Response } from 'labbcat-common';
import { AdminComponent } from '../admin-component';

@Component({
  selector: 'app-admin-roles',
  templateUrl: './admin-roles.component.html',
  styleUrls: ['./admin-roles.component.css']
})
export class AdminRolesComponent extends AdminComponent implements OnInit {
    rows: Role[];

    constructor(
        labbcatService: LabbcatService,
        messageService: MessageService
    ) {
        super(labbcatService, messageService);
    }
    
    ngOnInit(): void {
        this.readRows();
    }

    readRows(): void {
        this.labbcatService.labbcat.readRoles((roles, errors, messages) => {
            if (errors) errors.forEach(m => this.messageService.error(m));
            if (messages) messages.forEach(m => this.messageService.info(m));
            this.rows = [];
            for (let role of roles) {
                this.rows.push(role as Role);
            }
        });
    }

    onChange(row: Role) {
        row._changed = this.changed = true;        
    }

    creating = false;
    createRow(role_id: string, description: string) {
        this.creating = true;
        this.labbcatService.labbcat.createRole(
            role_id, description,
            (row, errors, messages) => {
                this.creating = false;
                if (errors) errors.forEach(m => this.messageService.error(m));
                if (messages) messages.forEach(m => this.messageService.info(m));
                // update the model with the field returned
                if (row) this.rows.push(row as Role);
                this.updateChangedFlag();
            });
    }
    
    deleteRow(row: Role) {
        row._deleting = true;
        if (confirm(`Are you sure you want to delete ${row.role_id}`)) {
            this.labbcatService.labbcat.deleteRole(row.role_id, (model, errors, messages) => {
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
    updateRow(row: Role) {
        this.updating++;
        this.labbcatService.labbcat.updateRole(
            row.role_id, row.description,
            (role, errors, messages) => {
                this.updating--;
                if (errors) errors.forEach(m => this.messageService.error(m));
                if (messages) messages.forEach(m => this.messageService.info(m));
                // update the model with the field returned
                const updatedRow = role as Role;
                const i = this.rows.findIndex(r => {
                    return r.role_id == updatedRow.role_id; })
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

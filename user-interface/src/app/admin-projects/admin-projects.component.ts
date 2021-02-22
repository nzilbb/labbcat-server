import { Component, OnInit } from '@angular/core';

import { Response } from '../response';
import { Project } from '../project';
import { MessageService, LabbcatService } from 'labbcat-common';
import { AdminComponent } from '../admin-component';

@Component({
  selector: 'app-admin-projects',
  templateUrl: './admin-projects.component.html',
  styleUrls: ['./admin-projects.component.css']
})
export class AdminProjectsComponent extends AdminComponent implements OnInit {
    rows: Project[];

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
        this.labbcatService.labbcat.readProjects((projects, errors, messages) => {
            this.rows = [];
            for (let project of projects) {
                this.rows.push(project as Project);
            }
        });
    }

    onChange(row: Project) {
        row._changed = this.changed = true;        
    }

    creating = false;
    createRow(project: string, description: string) {
        this.creating = true;
        this.labbcatService.labbcat.createProject(
            project, description,
            (row, errors, messages) => {
                this.creating = false;
                if (errors) errors.forEach(m => this.messageService.error(m));
                if (messages) messages.forEach(m => this.messageService.info(m));
                // update the model with the field returned
                if (row) this.rows.push(row as Project);
                this.updateChangedFlag();
            });
    }
    
    deleteRow(row: Project) {
        row._deleting = true;
        if (confirm(`Are you sure you want to delete ${row.project}`)) {
            this.labbcatService.labbcat.deleteProject(row.project, (model, errors, messages) => {
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
    updateRow(row: Project) {
        this.updating++;
        this.labbcatService.labbcat.updateProject(
            row.project, row.description,
            (project, errors, messages) => {
                this.updating--;
                if (errors) errors.forEach(m => this.messageService.error(m));
                if (messages) messages.forEach(m => this.messageService.info(m));
                // update the model with the field returned
                const updatedRow = project as Project;
                const i = this.rows.findIndex(r => {
                    return r.project == updatedRow.project; })
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

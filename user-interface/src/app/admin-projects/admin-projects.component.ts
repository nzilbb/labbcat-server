import { Component, OnInit } from '@angular/core';

import { Response } from '../response';
import { Project } from '../project';
import { MessageService } from '../message.service';
import { LabbcatService } from '../labbcat.service';

@Component({
  selector: 'app-admin-projects',
  templateUrl: './admin-projects.component.html',
  styleUrls: ['./admin-projects.component.css']
})
export class AdminProjectsComponent implements OnInit {
    rows: Project[];
    changed = false;
    constructor(
        private labbcatService: LabbcatService,
        private messageService: MessageService
    ) { }
    
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
    
    createRow(project: string, description: string) {
        this.labbcatService.labbcat.createProject(
            project, description,
            (row, errors, messages) => {
                if (errors) for (let message of errors) this.messageService.error(message);
                if (messages) for (let message of messages) this.messageService.info(message);
                // update the model with the field returned
                if (row) this.rows.push(row as Project);
                this.updateChangedFlag();
            });
    }
    
    deleteRow(row: Project) {
        if (confirm(`Are you sure you want to delete ${row.project}`)) {
            this.labbcatService.labbcat.deleteProject(row.project, (model, errors, messages) => {
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

    updateRow(row: Project) {
        this.labbcatService.labbcat.updateProject(
            row.project, row.description,
            (project, errors, messages) => {
                if (errors) for (let message of errors) this.messageService.error(message);
                if (messages) for (let message of messages) this.messageService.info(message);
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

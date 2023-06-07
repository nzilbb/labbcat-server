import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';

import { MessageService, LabbcatService } from 'labbcat-common';
import { AdminComponent } from '../admin-component';

@Component({
  selector: 'app-admin-annotator-tasks',
  templateUrl: './admin-annotator-tasks.component.html',
  styleUrls: ['./admin-annotator-tasks.component.css']
})
export class AdminAnnotatorTasksComponent extends AdminComponent implements OnInit {
    rows: any[]
    annotatorId: string;
    
    constructor(
        labbcatService: LabbcatService,
        messageService: MessageService,
        private route: ActivatedRoute
    ) {
        super(labbcatService, messageService);
    }
    
    ngOnInit(): void {
        this.readRows();
    }
    
    readRows(): void {
        this.annotatorId = this.route.snapshot.paramMap.get('annotatorId');
        console.log("annotatorId: " + this.annotatorId);
        this.labbcatService.labbcat.getAnnotatorTasks(
            this.annotatorId, (tasks, errors, messages) => {
                this.rows = [];
                for (let taskId in tasks) {
                    this.rows.push({ taskId : taskId, description : tasks[taskId] });
                }
            });
    }
    
    onChange(row) {
        row._changed = this.changed = true;        
    }
    
    creating = false;
    createRow(taskId: string, description: string): boolean {
        this.creating = true;
        this.labbcatService.labbcat.newAnnotatorTask(
            this.annotatorId, taskId, description,
            (row, errors, messages) => {
                this.creating = false;
                if (errors) errors.forEach(m => this.messageService.error(m));
                if (messages) messages.forEach(m => this.messageService.info(m));
                // update the model with the field returned
                if (!errors) this.rows.push({ taskId : taskId, description : description });
                this.updateChangedFlag();
            });
        return true;
    }
    
    deleteRow(row) {
        row._deleting = true;
        if (confirm(`Are you sure you want to delete ${row.taskId}`)) { // TODO i18n
            this.labbcatService.labbcat.deleteAnnotatorTask(row.taskId, (model, errors, messages) => {
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
    updateRow(row) {
        this.updating++;
        this.labbcatService.labbcat.saveAnnotatorTaskDescription(
            row.taskId, row.description,
            (task, errors, messages) => {
                this.updating--;
                if (errors) errors.forEach(m => this.messageService.error(m));
                if (messages) messages.forEach(m => this.messageService.info(m));
                // update the model with the field returned
                row._changed = false;
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

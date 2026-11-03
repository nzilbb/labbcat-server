import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, ParamMap } from '@angular/router';
import { switchMap } from 'rxjs/operators';

import { MessageService, LabbcatService, Response, Layer } from 'labbcat-common';
import { AdminComponent } from '../admin-component';

@Component({
  selector: 'app-admin-task-attribute-options',
  templateUrl: './admin-task-attribute-options.component.html',
  styleUrl: './admin-task-attribute-options.component.css'
})
export class AdminTaskAttributeOptionsComponent extends AdminComponent implements OnInit {
    task_id: number;
    scope: string;
    attribute: string;
    task_name: string;
    rows: any[];

    newValue = "";
    newDescription = "";

    layer: Layer;
    validLabels: string[] = [];
    existingOptions: string[] = [];

    constructor(
        labbcatService: LabbcatService,
        messageService: MessageService,
        private route: ActivatedRoute
    ) {
        super(labbcatService, messageService);
    }
    ngOnInit(): void {
        // need to subscribe to URL path changes, because the component will be re-used
        // from scope to scope
        this.route.paramMap.pipe(
            switchMap((params: ParamMap) => params.get('task_id'))
        ).subscribe(task_id => {
            this.task_id = parseInt(this.route.snapshot.paramMap.get('task_id'));
            this.scope = this.route.snapshot.paramMap.get('scope');
            this.attribute = this.route.snapshot.paramMap.get('attribute');
            this.readTask()
                .then(()=>{
                    this.readLayer().then(()=>{
                        this.readRows();
                    });
                });
        });
    }
    readTask(): Promise<number> {
        return new Promise<number>((accept,reject)=>{
            this.labbcatService.labbcat.readElicitationTasks((tasks, errors, messages) => {
                for (let task of tasks) {
                    if (task.task_id == this.task_id) {
                        this.task_name = task.task_name;
                        accept(this.task_id);
                        return;
                    }
                } // next task
                this.messageService.error(`Invalid task: ${this.task_id}`); // TODO i18n
                reject();
            });
        });
    }
    readLayer(): Promise<Layer> {
        return new Promise<Layer>((accept,reject)=>{
            this.labbcatService.labbcat.getLayer(
                `${this.scope}_${this.attribute}`, (layer, errors, messages) => {
                    if (errors) errors.forEach(m => this.messageService.error(m));
                    if (messages) messages.forEach(m => this.messageService.info(m));
                    this.layer = layer as Layer;
                    this.validLabels = Object.keys(layer.validLabels);
                    accept(this.layer);
                });
        });
    }
    readRows(): void {
        this.labbcatService.labbcat.readElicitationTaskAttributeOptions(
            this.task_id, this.scope, this.attribute, (rows, errors, messages) => {
                this.rows = [];
                this.existingOptions = [];
                for (let row of rows) {
                    this.rows.push(row);
                    this.existingOptions.push(row.value);
                }
            });
    }
    creating = false;
    createRow(value: string, description: string) : boolean {
        this.creating = true;
        this.labbcatService.labbcat.createElicitationTaskAttributeOption(
            this.task_id, this.scope, this.attribute, value, description,
            (row, errors, messages) => {
                this.creating = false;
                if (errors) errors.forEach(m => this.messageService.error(m));
                if (messages) messages.forEach(m => this.messageService.info(m));
                // update the model with the field returned
                if (row) {
                    this.rows.push(row);
                    this.existingOptions.push(row.value);
                }
                this.updateChangedFlag();
            });
        return true;
    }
    addAll(): void {
        this.creating = true;
        for (let value of this.validLabels) {
            if (!value) continue; // not blank values
            if (!this.existingOptions.includes(value)) {
                this.labbcatService.labbcat.createElicitationTaskAttributeOption(
                    this.task_id, this.scope, this.attribute,
                    value, this.layer.validLabels[value], (row, errors, messages) => {
                        if (errors) errors.forEach(m => this.messageService.error(m));
                        // update the model with the field returned
                        if (row) {
                            this.rows.push(row);
                            this.existingOptions.push(row.value);
                            // recursive call
                            this.addAll(); // add the next option
                        }
                    });
                return; // add one at a time, call allAll recursively...
            }
        } // next value
        this.updateChangedFlag();
        this.creating = false;
    }

    deleteRow(row: any) {
        row._deleting = true;
        if (confirm(`Are you sure you want to delete ${row.value}?`)) { // TODO i18n
            this.labbcatService.labbcat.deleteElicitationTaskAttributeOption(
                this.task_id, this.scope, this.attribute, row.value,
                (model, errors, messages) => {
                    row._deleting = false;
                    if (errors) errors.forEach(m => this.messageService.error(m));
                    if (messages) messages.forEach(m => this.messageService.info(m));
                    if (!errors) {
                        // remove from the model/view
                        this.rows = this.rows.filter(r => { return r !== row;});
                        this.existingOptions = this.existingOptions.filter(
                            r => { return r !== row.value;});
                        this.updateChangedFlag();
                    }});
        } else {
            row._deleting = false;
        }
    }
    
    selectNewValue() {
        if (this.layer) {
            this.newDescription = this.layer.validLabels[this.newValue];
        }
    }

    onChange(row: any) {
        row._changed = this.changed = true;        
    }

    updateChangedRows() {
        this.rows
            .filter(r => r._changed)
            .forEach(r => this.updateRow(r));
    }
    
    updating = 0;
    updateRow(row: any) {
        this.updating++;
        this.labbcatService.labbcat.updateElicitationTaskAttributeOption(
            this.task_id, this.scope, this.attribute, row.value, row.description,
            (option, errors, messages) => {
                this.updating--;
                if (errors) errors.forEach(m => this.messageService.error(m));
                if (messages) messages.forEach(m => this.messageService.info(m));
                // update the model with the field returned
                const i = this.rows.findIndex(r => {
                    return r.task_id == option.task_id
                        && r.scope == option.scope
                        && r.attribute == option.attribute
                        && r.value == option.value; })
                this.rows[i] = option;
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

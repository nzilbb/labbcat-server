import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, ParamMap } from '@angular/router';
import { switchMap } from 'rxjs/operators';
import { environment } from '../../environments/environment';

import { MessageService, LabbcatService, Response, Layer } from 'labbcat-common';
import { AdminComponent } from '../admin-component';

@Component({
    selector: 'app-admin-task-reminders',
    templateUrl: './admin-task-reminders.component.html',
    styleUrl: './admin-task-reminders.component.css'
})
export class AdminTaskRemindersComponent extends AdminComponent implements OnInit {
    task_id: number;
    task_name: string;
    rows: any[];
    
    baseUrl = environment.baseUrl;

    constructor(
        labbcatService: LabbcatService,
        messageService: MessageService,
        private route: ActivatedRoute
    ) {
        super(labbcatService, messageService);
    }
    ngOnInit(): void {
        this.route.paramMap.pipe(
            switchMap((params: ParamMap) => params.get('task_id'))
        ).subscribe(task_id => {
            this.task_id = parseInt(this.route.snapshot.paramMap.get('task_id'));
            this.readTask()
                .then(()=>{
                    this.readRows();
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
    readRows(): void {
        this.labbcatService.labbcat.createRequest(
            "reminders", null, (rows, errors, messages) => {
                this.rows = [];
                for (let row of rows) {
                    // strip seconds off time
                    row.reminder_time = row.reminder_time
                        .replace(/([0-9]+):([0-9]+):([0-9]+)/,"$1:$2");
                    this.rows.push(row);
                }
            },
            `${this.baseUrl}api/admin/elicit/reminders/${this.task_id}`)
            .send();
    }
    creating = false;
    createRow(
        label: string, participantPattern: string, reminderDay: string,
        reminderTime: string, fromDay: string, toDay: string) : boolean {
        this.creating = true;
        this.labbcatService.labbcat.createRequest(
            "reminders", null,
            (row, errors, messages) => {
                this.creating = false;
                if (errors) errors.forEach(m => this.messageService.error(m));
                if (messages) messages.forEach(m => this.messageService.info(m));
                // update the model with the field returned
                if (row) {
                    this.rows.push(row);
                }
                this.updateChangedFlag();
            },
            `${this.baseUrl}api/admin/elicit/reminders/${this.task_id}`, "POST",
            null, "application/json")
            .send(JSON.stringify({
                task_id : this.task_id,
                label: label,
                participant_pattern : participantPattern,
                reminder_day: reminderDay,
                reminder_time: reminderTime,
                from_day: parseInt(fromDay),
                to_day: parseInt(toDay)}));
        return true;
    }

    deleteRow(row: any) {
        row._deleting = true;
        if (confirm(`Are you sure you want to delete ${row.label}?`)) { // TODO i18n
            this.labbcatService.labbcat.createRequest(
                "reminders", null,
                (model, errors, messages) => {
                    row._deleting = false;
                    if (errors) errors.forEach(m => this.messageService.error(m));
                    if (messages) messages.forEach(m => this.messageService.info(m));
                    if (!errors) {
                        // remove from the model/view
                        this.rows = this.rows.filter(r => { return r !== row;});
                        this.updateChangedFlag();
                    }},
                `${this.baseUrl}api/admin/elicit/reminders/${this.task_id}/${row.reminder_id}`,
                "DELETE").send();
        } else {
            row._deleting = false;
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
        this.labbcatService.labbcat.createRequest(
            "reminders", null,
            (reminder, errors, messages) => {
                this.updating--;
                if (errors) errors.forEach(m => this.messageService.error(m));
                if (messages) messages.forEach(m => this.messageService.info(m));
                // update the model with the field returned
                const i = this.rows.findIndex(r => {
                    return r.reminder_id == reminder.reminder_id });
                this.rows[i] = reminder;
                this.updateChangedFlag();
            },
            `${this.baseUrl}api/admin/elicit/reminders/${this.task_id}/${row.reminder_id}`,
            "PUT").send(JSON.stringify({
                task_id : this.task_id,
                reminder_id : row.reminder_id,
                label: row.label,
                participant_pattern : row.participant_pattern,
                reminder_day: row.reminder_day,
                reminder_time: row.reminder_time,
                from_day: row.from_day,
                to_day: row.to_day}));
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

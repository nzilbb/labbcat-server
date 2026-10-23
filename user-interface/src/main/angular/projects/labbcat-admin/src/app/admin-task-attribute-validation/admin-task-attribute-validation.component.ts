import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, ParamMap } from '@angular/router';
import { switchMap } from 'rxjs/operators';

import { MessageService, LabbcatService, Response, Layer } from 'labbcat-common';
import { AdminComponent } from '../admin-component';

@Component({
  selector: 'app-admin-task-attribute-validation',
  templateUrl: './admin-task-attribute-validation.component.html',
  styleUrl: './admin-task-attribute-validation.component.css'
})
export class AdminTaskAttributeValidationComponent extends AdminComponent implements OnInit {
    task_id: number;
    scope: string;
    attribute: string;
    task_name: string;
    script: string;
    updating = false;

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
                    this.readScript();
                });
        });
    }
    readTask(): Promise<number> {
        this.updating = true;
        return new Promise<number>((accept,reject)=>{
            this.labbcatService.labbcat.readElicitationTasks((tasks, errors, messages) => {
                this.updating = false;
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
    readScript(): void {
        this.updating = true;
        this.labbcatService.labbcat.readElicitedAttributeValidation(
            this.task_id, this.scope, this.attribute, (script, errors, messages) => {
                if (errors) errors.forEach(m => this.messageService.error(m));
                if (messages) messages.forEach(m => this.messageService.info(m));
                this.updating = false;
                this.script = script;
            });
    }

    updateScript() {
        this.updating = true;
        this.labbcatService.labbcat.updateElicitedAttributeValidation(
            this.task_id, this.scope, this.attribute, this.script,
            (response, errors, messages) => {
                this.updating = false;
                if (errors) errors.forEach(m => this.messageService.error(m));
                if (messages) messages.forEach(m => this.messageService.info(m));
                // update the model with the field returned
                this.changed = false;
            });
    }

}

import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, ParamMap } from '@angular/router';
import { environment } from '../../environments/environment';
import { switchMap } from 'rxjs/operators';

import { MessageService, LabbcatService, Response, Layer } from 'labbcat-common';
import { AdminComponent } from '../admin-component';
import { Task } from '../task';
import { Step } from '../step';

@Component({
  selector: 'app-admin-task-steps',
  templateUrl: './admin-task-steps.component.html',
  styleUrl: './admin-task-steps.component.css'
})
export class AdminTaskStepsComponent extends AdminComponent implements OnInit {
    baseUrl = environment.baseUrl;
    schema: any;
    transcriptAttributes: Layer[];
    participantAttributes: Layer[];
    stimuli: string[];
    
    task_id: number;
    task_name: string;
    task: Task;
    root: Step; // dummy step to host top-level steps

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
            Promise.all([
                this.readStimuli(),
                this.readSchema(),
                this.readTask()
            ]).then(()=>{
                this.readSteps();
            });
        });
    }
    readSchema(): Promise<void> {
        return new Promise<void>((accept,reject)=>{
            this.labbcatService.labbcat.getSchema((schema, errors, messages) => {
                if (errors) errors.forEach(m => this.messageService.error(m));
                if (messages) messages.forEach(m => this.messageService.info(m));
                this.schema = schema;
                this.transcriptAttributes = [];
                this.participantAttributes = [];
                for (let l in schema.layers) {
                    let layer = schema.layers[l] as Layer;
                    if (layer.parentId == schema.root.id && layer.alignment == 0
                        && layer.id != schema.participantLayerId) {
                        this.transcriptAttributes.push(layer);
                    } else if (layer.parentId == schema.participantLayerId
                        && layer.alignment == 0) {
                        this.participantAttributes.push(layer);
                    }
                } // next layer
                accept();
            });
        });
    }
    readStimuli(): Promise<string[]> {
        return new Promise<string[]>((accept,reject)=>{
            const stimuli = this.labbcatService.labbcat.createRequest(
                "stimuli", null, (stimuli, errors, messages) => {
                    if (errors) errors.forEach(m => this.messageService.error(m));
                    if (messages) messages.forEach(m => this.messageService.info(m));
                    if (stimuli) {
                        this.stimuli = stimuli;
                        accept(this.stimuli);
                    } else {
                        reject("Failed to load stimuli"); // TODO i18n
                    }
                    
                },
                this.baseUrl+"api/admin/elicit/stimulus");
            try {
                stimuli.send();
            } catch (x) {
                this.messageService.error(x);
                reject(`Could not load stimuli: ${x}`); // TODO i18n
            }
        });
    }
    readTask(): Promise<string> {
        return new Promise<string>((accept,reject)=>{
            this.labbcatService.labbcat.readElicitationTasks((tasks, errors, messages) => {
                for (let task of tasks) {
                    if (task.task_id == this.task_id) {
                        this.task_name = task.task_name;
                        accept(this.task_name);
                        return;
                    }
                } // next task
                this.messageService.error(`Invalid task: ${this.task_id}`); // TODO i18n
                reject();
            });
        });
    }
    readSteps(): Promise<Task> {
        return new Promise<Task>((accept,reject)=>{
            this.labbcatService.labbcat.getElicitationTask(
                this.task_name, (task, errors, messages) => {
                    if (errors) errors.forEach(m => this.messageService.error(m));
                    if (messages) messages.forEach(m => this.messageService.info(m));
                    if (!task) {
                        this.messageService.error(`Invalid task: ${this.task_name}`); // TODO i18n
                        reject(`Invalid task: ${this.task_name}`);
                    } else {
                        this.task = task as Task;
                        // mark top level steps as 'open'
                        for (let step of task.steps) {
                            step._open = true;
                        }
                        this.root = {
                            step_id: -1, title: task.task_name, steps: task.steps
                        } as Step;
                        accept(this.task);
                    }
            });
        });
    }
    onChange(step: Step): void {
        this.changed = true;
    }
    saving = false;
    save(): void {
        this.saving = true;
        this.labbcatService.labbcat.updateElicitationTaskSteps(
            this.task_id, this.task.steps, (result, errors, messages) => {
                this.saving = false;
                if (messages) messages.forEach(m => this.messageService.info(m));
                if (errors) {
                    errors.forEach(m => this.messageService.error(m));
                } else {
                    this.changed = false;
                }
            });
    }
}

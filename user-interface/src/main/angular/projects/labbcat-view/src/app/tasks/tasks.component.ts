import { Component, OnInit } from '@angular/core';

import { MessageService, LabbcatService } from 'labbcat-common';
import { Task } from 'labbcat-common';

@Component({
  selector: 'app-tasks',
  templateUrl: './tasks.component.html',
  styleUrl: './tasks.component.css'
})
export class TasksComponent implements OnInit {

    tasks: Task[] = [];
    loading = true;
    refreshTimer: any;
    logName: string;
    threadLog: string;
    threadException: string;
    threadStackTrace: string;

    constructor(
        private labbcatService: LabbcatService,
        private messageService: MessageService,
    ) {
    }

    ngOnInit(): void {
        this.readTasks();        
    }
    readTasks(): void {
        this.loading = true;
        this.labbcatService.labbcat.getTasks((ids, errors, messages) => {
            this.loading = false;
            if (errors) errors.forEach(m => this.messageService.error(m));
            if (messages) messages.forEach(m => this.messageService.info(m));
            if (ids) {
                for (let id of ids) {                    
                    if (!this.tasks.find(task=>task.threadId == id)) {
                        // add new task to the list
                        this.labbcatService.labbcat.taskStatus(
                            id, {keepalive:false}, (task, errors, messages) => {
                                if (task) this.tasks.push(task);
                            });
                    }
                }
            }
            // now read all the task details
            for (let task of this.tasks) {
                if (task.threadId) { // only tasks that aren't gone
                    this.loadTask(task.threadId);
                } // not gone already
            }
            if (!this.refreshTimer) {
                this.refreshTimer = setInterval(()=>{
                    this.readTasks();
                }, 5000);
            }
        });
    }
    loadTask(id: string) {
        this.labbcatService.labbcat.taskStatus(
            id, {keepalive:false}, (task, errors, messages) => {
                const t = this.tasks.findIndex(t=>t.threadId==id);
                if (task) {
                    this.tasks[t] = task;
                } else { // mark it as gone
                    this.tasks[t].threadId = null;
                }
            });
    }
    cancelTask(task: Task): void {
        this.loading = true;
        this.labbcatService.labbcat.cancelTask(task.threadId, (cancelled, errors, messages) => {
            this.loading = false;
            if (errors) errors.forEach(m => this.messageService.error(m));
            if (messages) messages.forEach(m => this.messageService.info(m));
            if (cancelled) {
                this.tasks[this.tasks.findIndex(t=>t.threadId==task.threadId)] = cancelled;
            }
            // refresh status after a short delay
            setTimeout(()=>{
                this.loadTask(task.threadId);
            }, 1000);
        });
    }
    releaseTask(task: Task): void {
        this.loading = true;
        this.labbcatService.labbcat.releaseTask(task.threadId, (released, errors, messages) => {
            this.loading = false;
            if (errors) errors.forEach(m => this.messageService.error(m));
            if (messages) messages.forEach(m => this.messageService.info(m));
            if (released) {
                this.tasks[this.tasks.findIndex(t=>t.threadId==task.threadId)] = released;
            }
            // refresh status after a short delay
            setTimeout(()=>{
                this.loadTask(task.threadId);
            }, 1000);
        })
    }
    log(task: Task): void {
        this.logName = task.threadName;
        this.threadLog = null;
        this.labbcatService.labbcat.taskStatus(
            task.threadId, {log:true, keepalive:false}, (thread, errors, messages)=>{
                if (errors) errors.forEach(m => this.messageService.error(m));
                if (messages) messages.forEach(m => this.messageService.info(m));
                this.threadLog = thread.log;
                this.threadException = thread.lastException;
                this.threadStackTrace = thread.stackTrace;
            });
    }
}

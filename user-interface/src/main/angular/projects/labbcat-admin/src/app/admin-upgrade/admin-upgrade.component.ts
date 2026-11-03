import { Component, OnInit } from '@angular/core';
import { environment } from '../../environments/environment';

import { MessageService, LabbcatService, SerializationDescriptor, Task } from 'labbcat-common';
import { AdminComponent } from '../admin-component';

@Component({
  selector: 'app-admin-upgrade',
  templateUrl: './admin-upgrade.component.html',
  styleUrl: './admin-upgrade.component.css'
})
export class AdminUpgradeComponent extends AdminComponent implements OnInit {
    
    baseUrl: string;
    imagesLocation = environment.imagesLocation;
    war: File;
    uploading = false;
    percentComplete: number;
    id: string;
    newVersion: string;
    oldVersion: string;
    migration: boolean;
    downgrade: boolean;
    cancelling = false;
    confirming = false;
    monitoring = false;
    upgradeMessages: string[];
    lastException: string;
    stackTrace: string;
    upgradeComplete = false;

    // for showing other activity
    tasks: Task[] = [];
    loading = true;
    refreshTimer: any;

    constructor(
        labbcatService: LabbcatService,
        messageService: MessageService,
    ) {
        super(labbcatService, messageService);
    }
    
    ngOnInit(): void {
        this.labbcatService.labbcat.getId((url, errors, messages) => {
            if (errors) errors.forEach(m => this.messageService.error(m));
            if (messages) messages.forEach(m => this.messageService.info(m));
            this.baseUrl = url;
        });
        this.readTasks();        
    }
    readTasks(): void {
        if (this.monitoring || this.upgradeComplete) return;
        
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
                                if (task && task.threadId) this.tasks.push(task);
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
            // readTasks might take more than 5 seconds to respond,
            // so instead of setInterval to check regardless of whether the last
            // check returned, we create use setTimeout each time around
            // so that we're only ever waiting on one readTasks call at a time
            this.refreshTimer = setTimeout(()=>{
                this.readTasks();
            }, 5000);
        });
    }
    loadTask(id: string) {
        this.labbcatService.labbcat.taskStatus(
            id, {keepalive:false}, (task, errors, messages) => {
                const t = this.tasks.findIndex(t=>t.threadId==id);
                if (task) {
                    this.tasks[t] = task;
                } else { // remove it
                    this.tasks = this.tasks.filter(t=>t.threadId != id);
                }
            });
    }

    /** Called when an upgrader file is selected */
    selectFile(files: File[]): void {
        if (files.length == 0) {
            this.war = null;
            return;
        }
        this.war = files[0]
        if (!this.war.name.endsWith(".war")
            && !this.war.name.endsWith(".zip")) {
            this.messageService.error("File must be a LaBB-CAT installer file, e.g. labbcat.war"); // TODO i18n
            this.war = null;
            return;
        }
        this.upload();
    }
    
    upload() {
        this.uploading = true;

        // post file
        const fd = new FormData();
        fd.append("war", this.war);
        const upload = this.labbcatService.labbcat.createRequest(
            "upgrade", null, (model, errors, messages) => {
                this.uploading = false;
                if (errors) errors.forEach(m => this.messageService.error(m));
                if (messages) messages.forEach(m => this.messageService.info(m));
                if (model && model.id) {
                    this.id = model.id;
                    this.oldVersion = model.oldVersion;
                    this.newVersion = model.newVersion;
                    this.migration = model.migration;
                    this.downgrade = model.newVersion < model.oldVersion;
                }
            },
            this.baseUrl+"api/admin/upgrade", "POST");
        upload.upload.addEventListener("progress", (e)=> { // progress
            if (e.lengthComputable) {
                // upload goes up to 50% only
	        this.percentComplete = Math.round(e.loaded * 100 / e.total);
            }
        }, false);
        
        try {
            upload.send(fd);
        } catch (x) {
            this.messageService.error(x);
        }
    }
    
    confirm() {
        this.confirming = true;
        const confirm = this.labbcatService.labbcat.createRequest(
            "upgrade", null, (model, errors, messages) => {
                this.confirming = false;
                if (errors) errors.forEach(m => this.messageService.error(m));
                if (messages) messages.forEach(m => this.messageService.info(m));
                if (!errors) {
                    this.id = null;
                    this.monitorProgress();
                }
            },
            `${this.baseUrl}api/admin/upgrade/${this.id}`, "PUT");
        
        try {
            confirm.send();
        } catch (x) {
            this.messageService.error(x);
        }
    }
    
    cancel() {
        this.cancelling = true;
        const cancel = this.labbcatService.labbcat.createRequest(
            "upgrade", null, (model, errors, messages) => {
                this.cancelling = false;
                if (errors) errors.forEach(m => this.messageService.error(m));
                if (messages) messages.forEach(m => this.messageService.info(m));
                if (!errors) {
                    this.war = this.id = this.newVersion = this.oldVersion = null;
                }
            },
            `${this.baseUrl}api/admin/upgrade/${this.id}`, "DELETE");
        
        try {
            cancel.send();
        } catch (x) {
            this.messageService.error(x);
        }
    }
    
    monitorProgress() {
        this.monitoring = true;
        const monitor = this.labbcatService.labbcat.createRequest(
            "upgrade", null, (model, errors, messages) => {
                if (errors) {
                    // filter out transient restart errors 
                    errors.filter(m => !m.endsWith("upgrade_jsp")) 
                        .forEach(m => this.messageService.error(m));
                }
                if (messages) messages.forEach(m => this.messageService.info(m));
                if (model) {
                    this.percentComplete = model.percentComplete;
                    if (model.messages) {
                        this.upgradeMessages = model.messages;
                        window.setTimeout(()=>{
                            try { // scroll to the last message
                                document.getElementById(
                                    'message-'+(this.upgradeMessages.length-1))
                                    .scrollIntoView({
                                        behavior: "smooth",
                                        block: "center"
                                    });
                            } catch (x) {}
                        }, 100);
                    }
                    if (model.lastException) {
                        this.lastException = model.lastException;
                        this.stackTrace = model.stackTrace;
                        return; // stop monitoring
                    }
                    if (!model.running
                        && !this.lastException && model.version >= this.newVersion) {
                        this.id = this.newVersion = this.oldVersion = null;
                        this.monitoring = false;
                        this.upgradeComplete = true;
                        return; // stop monitoring
                    }
                } 
                window.setTimeout(()=>{
                    this.monitorProgress();
                }, 500);
            },
            `${this.baseUrl}api/admin/upgrade`, "GET");
        
        try {
            monitor.send();
        } catch (x) {
            this.messageService.error(x);
        }
    }
}

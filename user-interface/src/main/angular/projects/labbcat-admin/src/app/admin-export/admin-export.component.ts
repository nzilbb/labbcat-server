import { Component, OnInit } from '@angular/core';
import { environment } from '../../environments/environment';

import { MessageService, LabbcatService, Task } from 'labbcat-common';
import { AdminComponent } from '../admin-component';

@Component({
  selector: 'app-admin-export',
  templateUrl: './admin-export.component.html',
  styleUrl: './admin-export.component.css'
})
export class AdminExportComponent extends AdminComponent implements OnInit {
    baseUrl: string;
    media = true;
    exporting = false;
    exportTask: Task;
    
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
    }

    export(): void {
        this.exporting = true;
        this.findExportTask();
        document.location = this.baseUrl + "api/admin/export" + (this.media?"":"?nomedia");
    }

    findExportTask() {
        setTimeout(()=>{ // after a delay
            if (!this.exportTask) { // still haven't found the task
                this.labbcatService.labbcat.getTasks((taskIds, errors, messages)=>{
                    for (let taskId of taskIds) {
                        this.labbcatService.labbcat.taskStatus(
                            taskId, (task, errors, messages)=>{
                                if (task.threadName == "Export LaBB-CAT") {
                                    this.exportTask = task;
                                }
                            });
                    } // next task
                    
                    // keep trying until we find it
                    this.findExportTask();
                }); // getTasks
            } // still haven't found the task
        }, 2000);
    }

    finished(): void {
        this.exporting = false;
        this.labbcatService.labbcat.releaseTask(this.exportTask.threadId);
        this.exportTask = null;
    }
}

import { Component, Input, OnInit, OnChanges, SimpleChanges, OnDestroy, ViewChild, ElementRef, EventEmitter, Output } from '@angular/core';
import { ActivatedRoute } from '@angular/router';

import { Task } from '../task';
import { MessageService } from '../message.service';
import { LabbcatService } from '../labbcat.service';

@Component({
  selector: 'lib-task',
  templateUrl: './task.component.html',
  styleUrls: ['./task.component.css']
})
export class TaskComponent implements OnInit, OnChanges, OnDestroy {
    @Input() threadId: string;
    @Input() displayStartTime = false;
    @Input() cancelButton = true;
    @Input() showStatus = true;
    @Input() showLastException = true;
    @Input() showStackTrace = true;
    @Input() showName = true;
    @Input() autoOpenResults = true;
    @Output() finished = new EventEmitter<Task>();
    task: Task;
    timeout: number;
    cancelling = false;
    resultsOpened: boolean;
    @ViewChild('progress', {static: false}) progressBar: ElementRef;    
    @ViewChild('resultAnchor', {static: false}) taskResultAnchor: ElementRef;    
        
    constructor(
        private labbcatService: LabbcatService,
        private messageService: MessageService,
        private route: ActivatedRoute
    ) { }
    
    ngOnInit(): void {
        if (!this.threadId) { // not thread ID given, might come from URL
            this.getIdFromUrl();
        }
        window.setTimeout(()=>{
            if (this.progressBar) this.progressBar.nativeElement.scrollIntoView();
        }, 500);
    }
    getIdFromUrl() : void {
        this.threadId = this.route.snapshot.paramMap.get('threadId');
        if (this.threadId) {
            this.readTaskStatus();
        } else {
            this.route.queryParams.subscribe((params) => {
                this.threadId = params["threadId"];
                this.readTaskStatus();
            });
        }
    }
    ngOnChanges(changes: SimpleChanges): void {
        if (!this.threadId) {
            this.route.queryParams.subscribe((params) => {
                this.threadId = params["threadId"];
                this.readTaskStatus();
            });
        } else {
            this.readTaskStatus();
        }
        if (this.progressBar) this.progressBar.nativeElement.scrollIntoView();
    }
    ngOnDestroy(): void {
        clearTimeout(this.timeout);
    }

    readTaskStatus(): void {
        if (!this.cancelling) { // only update status if we're not cancelling...
            this.labbcatService.labbcat.taskStatus(this.threadId, (task, errors, messages) => {
                // show messages
                if (errors) errors.forEach(m => this.messageService.error(m));
                if (messages) messages.forEach(m => this.messageService.info(m));

                // update model
                this.task = task || this.task;

                // if still running, results haven't been opened
                if (this.task.running) this.resultsOpened = false;

                if (!this.task.running) {
                    this.finished.emit(this.task);
                }
                // if finished and there's a result URL, open the results
                if (!this.task.running && this.task.resultUrl) {
                    if (this.autoOpenResults) {
                        this.openResults();
                    }
                }
                
                // while this is visible, we keep checking the status of the thread.
                // this keeps the thread and any resources it's holding alive until nobody
                // is interested
                
                // set timeout for next check...
                this.timeout = setTimeout(()=>{
                    // has the thread we're monitoring changed?
                    if (task.threadId == this.threadId) { // the same thread
                        this.readTaskStatus();
                    }
                }, this.task.refreshSeconds*1000 || 5000);
            });
        }
    }

    openResults(): void {
        if (!this.task.running && this.task.resultUrl && !this.resultsOpened) {
            setTimeout(()=>{ // wait a second for the anchor component to come into being
                if (this.taskResultAnchor) {
                    this.taskResultAnchor.nativeElement.click();
                    this.resultsOpened = true;
                } else {
                    this.openResults(); // try again in a second...
                }
            }, 1000);
        }
    }
    
    cancelTask(): void {
        this.cancelling = true;
        this.labbcatService.labbcat.cancelTask(this.threadId, (result, errors, messages) => {

            // show messages
            if (errors) errors.forEach(m => this.messageService.error(m));
            if (messages) messages.forEach(m => this.messageService.info(m));

            // stop the regular timeout from firing
            clearTimeout(this.timeout);
            this.cancelling = false;

            // get the status again
            this.readTaskStatus();
        });
    }

}

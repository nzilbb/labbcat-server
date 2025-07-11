import { Component, OnInit } from '@angular/core';

import { MessageService, LabbcatService } from 'labbcat-common';
import { Layer, Task } from 'labbcat-common';

@Component({
  selector: 'app-matches-upload',
  templateUrl: './matches-upload.component.html',
  styleUrl: './matches-upload.component.css'
})
export class MatchesUploadComponent implements OnInit {

    csv: File;
    threadId: string;
    task: Task;
    
    constructor(
        private labbcatService: LabbcatService,
        private messageService: MessageService) { }

    ngOnInit(): void {
    }
    
    /** Called when a CSV file is selected. */
    selectFile(files: File[]): void {
        if (files.length == 0) return;
        this.csv = files[0]
        if (!this.csv.name.endsWith(".csv") && !this.csv.name.endsWith(".tsv")) {
            this.messageService.error("File must be a CSV search results file.")
            this.csv = null;
            return;
        }
    }
    
    processing = false;
    processingError = "";
    /** start processing */
    process(): void {
        this.processing = true;
        this.processingError = "";
        this.threadId = null;
        this.labbcatService.labbcat.resultsUpload(
            this.csv, "MatchId"/*TODO*/, (response, errors, messages) => {
                
                if (errors) errors.forEach(m => this.messageService.error(m));
                if (messages) messages.forEach(m => this.messageService.info(m));
                if (response) {
                    this.threadId = response.threadId;
                } else {
                    this.processing = false;
                    if (errors && errors[0]) this.processingError = errors[0];
                }
            }, (evt) => {
                if (evt.lengthComputable) {
  	            const percentComplete = Math.round(evt.loaded * 100 / evt.total);
	            console.log("Uploading: " + percentComplete + "%");
                }
            });
    }

    /** finished processing */
    finished(task: Task): void {
        this.processing = false;
        if (task.resultUrl) {
            document.location = task.resultUrl;
        }
    }
}

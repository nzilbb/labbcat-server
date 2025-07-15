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
    rowCount: number;
    headers: string[];
    csvFieldDelimiter: string
    targetColumn: string;

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

        // read the file to determine columns and row count
        const reader = new FileReader();
        const component = this;
        reader.onload = () => {  
            const csvData = reader.result;  
            let csvRecordsArray = (<string>csvData).split(/\r\n|\n/);
            // remove blank lines
            csvRecordsArray = csvRecordsArray.filter(l=>l.length>0);
            if (csvRecordsArray.length == 0) {
                component.messageService.error("File is empty: " + component.csv.name);
            } else {
                this.rowCount = csvRecordsArray.length - 1; // (don't count header line)
                
                // get headers...
                const firstLine = csvRecordsArray[0];
                // split the line into fields
                this.csvFieldDelimiter = ",";
                if (firstLine.match(/.*\t.*/)) this.csvFieldDelimiter = "\t";
                else if (firstLine.match(/.;.*/)) this.csvFieldDelimiter = ";";
                const fields = firstLine.split(this.csvFieldDelimiter);
                // the fields might be quoted, so remove quotes
                this.headers = fields.map(f=>f.replace(/^"(.*)"$/g, "$1"))

                // set default column selection...
                if (this.headers.includes("MatchId")) {
                    this.targetColumn = "MatchId";
                } else if (this.headers.includes("URL")) {
                    this.targetColumn = "URL";
                }                
            }
        };
        reader.onerror = function () {  
            component.messageService.error("Error reading " + component.csv.name);
        };
        reader.readAsText(this.csv);
        
    }
    
    processing = false;
    processingError = "";
    /** start processing */
    process(): void {
        this.processing = true;
        this.processingError = "";
        this.threadId = null;
        this.labbcatService.labbcat.resultsUpload(
            this.csv, this.targetColumn, (response, errors, messages) => {
                
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
            document.location = `matches?threadId=${task.threadId}`; //TODO task.resultUrl;
        }
    }
}

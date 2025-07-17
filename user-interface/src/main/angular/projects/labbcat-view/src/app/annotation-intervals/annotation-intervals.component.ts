import { Component, OnInit } from '@angular/core';

import { MessageService, LabbcatService } from 'labbcat-common';
import { Layer, Task } from 'labbcat-common';

@Component({
  selector: 'app-annotation-intervals',
  templateUrl: './annotation-intervals.component.html',
  styleUrl: './annotation-intervals.component.css'
})
export class AnnotationIntervalsComponent implements OnInit {
    schema: any;

    csv: File;
    rowCount: number;
    headers: string[];
    transcriptColumn: number;
    participantColumn: number;
    startTimeColumn: number;
    endTimeColumn: number;

    containment: "entire" | "partial" = "entire";
    labelDelimiter = " ";
    layerIds: string[];

    threadId: string;
    task: Task;

    constructor(
        private labbcatService: LabbcatService,
        private messageService: MessageService) { }
    
    ngOnInit(): void {
        // get layer schema so we can identify participant attributes
        this.labbcatService.labbcat.getSchema((schema, errors, messages) => {
            this.schema = schema;
        });
    }
    
    /** Called when a CSV file is selected; parses the file to determine CSV fields. */
    selectFile(files: File[]): void {
        if (files.length == 0) return;
        this.csv = files[0]
        if (!this.csv.name.endsWith(".csv") && !this.csv.name.endsWith(".tsv")) {
            this.messageService.error("File must be a CSV search results file.")
            this.csv = null;
            return;
        }
        
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
                let delimiter = ",";
                if (firstLine.match(/.*\t.*/)) delimiter = "\t";
                else if (firstLine.match(/.;.*/)) delimiter = ";";
                const fields = firstLine.split(delimiter);
                // the fields might be quoted, so remove quotes
                component.headers = fields.map(f=>f.replace(/^"(.*)"$/g, "$1"))

                // set default column selections...
                this.transcriptColumn = this.headers.findIndex(
                    h=>/^transcript$/i.test(h))
                this.participantColumn = this.headers.findIndex(
                    h=>/^(speaker|participant|who)$/i.test(h))
                this.startTimeColumn = this.headers.findIndex(h=>h == "Line")
                if (this.startTimeColumn < 0) { // nothing found
                    // anthing ending in ... start
                    this.startTimeColumn = this.headers.findIndex(
                        h=>/start$/i.test(h))
                }
                this.endTimeColumn = this.headers.findIndex(h=>h== "LineEnd")
                if (this.endTimeColumn < 0) { // nothing found
                    // anthing ending in ... end
                    this.endTimeColumn = this.headers.findIndex(
                        h=>/end$/i.test(h))
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
        this.labbcatService.labbcat.intervalAnnotations(
            this.csv, this.transcriptColumn, this.participantColumn, this.startTimeColumn,
            this.endTimeColumn, this.layerIds, true, this.labelDelimiter,
            this.containment, (response, errors, messages) => {
                
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
            console.log(task.resultUrl);
        }
    }

}


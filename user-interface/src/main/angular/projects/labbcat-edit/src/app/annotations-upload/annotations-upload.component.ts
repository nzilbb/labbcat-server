import { Component } from '@angular/core';

import { MessageService, LabbcatService, Layer, User, Task } from 'labbcat-common';

@Component({
  selector: 'app-annotations-upload',
  templateUrl: './annotations-upload.component.html',
  styleUrl: './annotations-upload.component.css'
})
export class AnnotationsUploadComponent {
    user: User;
    schema: any;
    tokenLayers: Layer[]; // layers that can tag word (or segment) tokens by MatchId
    intervalLayers: Layer[]; // layers that can tag intervals by start/end time
    layers: Layer[];
    categories: string[];

    updating: false;
    csv: File;
    rowCount: number;
    headers: string[];

    alignment = "0";
    idColumn: number;
    transcriptColumn: number;
    startTimeColumn: number;
    endTimeColumn: number;
    columnLayer: string[]; // parallel to headers, specifies the layer to map column to
    newLayerName: string[]; // parallel to headers, specifies the attribute name to add
    newLayerCategory: string[]; // parallel to headers, the new attribute category

    uploadPercentProgress: number;
    threadId: string;
    task: Task;
    processingError: string;
    taskLog: string;
    
    constructor(
        private labbcatService: LabbcatService,
        private messageService: MessageService) {
     }
    
    ngOnInit(): void {
        // get layer schema so we can identify different possible layers to add to
        this.labbcatService.labbcat.getSchema((schema, errors, messages) => {
            this.schema = schema;
            this.tokenLayers = [];
            this.intervalLayers = [];
            for (let layerId in schema.layers) {
                const layer = schema.layers[layerId] as Layer;
                if (layer.layer_manager_id) continue; // not managed layers
                if (layer.parentId == this.schema.root.id
                    && layer.alignment == 2) { // span layer
                    this.intervalLayers.push(layer)
                } else if ((
                    layer.parentId == this.schema.wordLayerId
                        && layer.alignment == 0) // word tag layer
                    || (layer.parentId == this.schema.turnLayerId 
                        && layer.id != this.schema.wordLayerId
                        && layer.id != this.schema.utteranceLayerId)
                    || (layer.parentId == "segment" 
                        && layer.alignment == 0)) { // segment tag layer
                    this.tokenLayers.push(layer)
                }
            } // next layer

            // annotating tokens by default
            this.layers = this.tokenLayers;
        });
    }

    /** Called when a CSV file is selected; parses the file to determine CSV fields. */
    selectFile(files: File[]): void {
        this.threadId = null;
        this.taskLog = null;
        if (files.length == 0) return;
        this.csv = files[0];
        if (!this.csv.name.endsWith(".csv") && !this.csv.name.endsWith(".tsv")) {
            this.messageService.error("File must be a CSV file.") // TODO i18n
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
                component.messageService.error(`File is empty: ${component.csv.name}`); // TODO i18n
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
                
                // try to find a good default value for idColumn
                const lowercaseHeaders = this.headers.map(h=>h.toLowerCase())
                this.idColumn = lowercaseHeaders.indexOf("matchid");
                if (this.idColumn < 0) {
                    this.idColumn = lowercaseHeaders.indexOf("wordid");
                }
                
                this.transcriptColumn = lowercaseHeaders.indexOf("transcript");
                if (this.transcriptColumn < 0) {
                    this.transcriptColumn = lowercaseHeaders.indexOf("id");
                }
                if (this.transcriptColumn < 0) {
                    this.transcriptColumn = lowercaseHeaders.indexOf("graph");
                }
                
                this.startTimeColumn = lowercaseHeaders.findIndex(f=>f.endsWith(" start"));
                if (this.startTimeColumn < 0) {
                    this.startTimeColumn = lowercaseHeaders.indexOf("line");
                }
                
                this.endTimeColumn = lowercaseHeaders.findIndex(f=>f.endsWith(" end"));
                if (this.endTimeColumn < 0) {
                    this.endTimeColumn = lowercaseHeaders.indexOf("lineend");
                }
                component.mapDefaultLayers();
            }
        };
        reader.onerror = function () {  
            component.messageService.error(`Error reading ${component.csv.name}`);
        };
        reader.readAsText(this.csv);
    }

    alignmentChanged() {
        if (this.alignment == "2") { // annotating intervals
            this.layers = this.intervalLayers;
        } else { // annotating tokens
            this.layers = this.tokenLayers;
        }
        this.mapDefaultLayers();
    }

    mapDefaultLayers() {
        const lowercaseHeaders = this.headers.map(h=>h.toLowerCase())
        
        // set default mappings
        this.columnLayer = [];
        this.newLayerName = [];
        this.newLayerCategory = [];
        for (let f in lowercaseHeaders) {
            const lowercaseHeader = lowercaseHeaders[f];
            this.columnLayer.push(""); // default to ignore
            this.newLayerName.push("");
            this.newLayerCategory.push("General");
            for (let layer of this.layers) {
                if (lowercaseHeader == layer.description.toLowerCase()
                    || lowercaseHeader == layer.id.toLowerCase()) {
                    this.columnLayer[f] = layer.id;
                    break;
                }
            } // next layer
        }
    }

    processing = false;
    /** Start processing */
    process() {
        this.processing = true;
        this.threadId = null;
        this.taskLog = null;
        if (this.alignment == "2") { // annotating intervals
            this.labbcatService.labbcat.uploadIntervalAnnotations(
                this.csv, this.transcriptColumn, this.startTimeColumn, this.endTimeColumn,
                this.columnLayer, (response, errors, messages)=>{
                    this.uploadPercentProgress = null;
                    if (errors) errors.forEach(m => this.messageService.error(m));
                    if (messages) messages.forEach(m => this.messageService.info(m));
                    if (response) {
                        this.threadId = response.threadId;
                    } else {
                        this.processing = false;
                        if (errors && errors[0]) this.processingError = errors[0];
                    }
                }, (e)=> { // progress
                    if (e.lengthComputable) {
                        // upload goes up to 50% only
	                this.uploadPercentProgress = Math.round(e.loaded * 100 / e.total);
                    }
                });
        } else { // annotating tokens
            this.labbcatService.labbcat.uploadTokenAnnotations(
                this.csv, this.idColumn, this.columnLayer, (response, errors, messages)=>{
                    this.uploadPercentProgress = null;
                    if (errors) errors.forEach(m => this.messageService.error(m));
                    if (messages) messages.forEach(m => this.messageService.info(m));
                    if (response) {
                        this.threadId = response.threadId;
                    } else {
                        this.processing = false;
                        if (errors && errors[0]) this.processingError = errors[0];
                    }
                }, (e)=> { // progress
                    if (e.lengthComputable) {
                        // upload goes up to 50% only
	                this.uploadPercentProgress = Math.round(e.loaded * 100 / e.total);
                    }
                });
        }
    }
    /** finished processing */
    finished(task: Task): void {
        this.processing = false;
        console.log(`task.lastException ${task.lastException}`);
        if (!task.lastException) { // no problems with import
            // hide mappings
            this.headers = null;
        } else { // there were errors, display the log
            this.labbcatService.labbcat.taskStatus(
                task.threadId, {log:true, keepalive:false},
                (thread, errors, messages)=>{
                    if (errors) errors.forEach(m => this.messageService.error(m));
                    if (messages) messages.forEach(m => this.messageService.info(m));
                    this.taskLog = thread.log;
                });
        }
    }

}

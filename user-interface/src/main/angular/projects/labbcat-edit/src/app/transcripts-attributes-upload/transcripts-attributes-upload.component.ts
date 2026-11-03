import { Component, OnInit } from '@angular/core';

import { MessageService, LabbcatService, Layer, User } from 'labbcat-common';

@Component({
  selector: 'app-transcripts-attributes-upload',
  templateUrl: './transcripts-attributes-upload.component.html',
  styleUrl: './transcripts-attributes-upload.component.css'
})
export class TranscriptsAttributesUploadComponent implements OnInit {
    user: User;
    schema: any;
    transcriptAttributeLayers: Layer[];
    categories: string[];
    
    updating: false;
    csv: File;
    rowCount: number;
    headers: string[];
    
    idColumn: number;
    columnLayer: string[]; // parallel to headers, specifies the layer to map column to
    newLayerName: string[]; // parallel to headers, specifies the attribute name to add
    newLayerCategory: string[]; // parallel to headers, the new attribute category
    
    uploadPercentProgress: number;
    
    updated: number;
    missing: number;
    
    constructor(
        private labbcatService: LabbcatService,
        private messageService: MessageService) {
    }
    
    ngOnInit(): void {
        this.readUserInfo();
        this.readCategories();
        // get layer schema so we can identify transcript attributes
        this.labbcatService.labbcat.getSchema((schema, errors, messages) => {
            this.schema = schema;
            this.transcriptAttributeLayers = [];
            for (let layerId in schema.layers) {
                const layer = schema.layers[layerId] as Layer;
                if (layer.parentId == this.schema.root.id
                    && layer.alignment == 0
                    && layer.id.startsWith("transcript_")
                    && layer.attribute) {  // transcript attribute
                    this.transcriptAttributeLayers.push(layer)
                } // transcript attribute
            } // next layer
        });
    }
    
    readUserInfo(): void {
        this.labbcatService.labbcat.getUserInfo((user, errors, messages) => {
            this.user = user as User;
        });
    }
    readCategories(): void {
        this.labbcatService.labbcat.readCategories(
            "transcript", (categories, errors, messages) => {
                this.categories = categories.map(c=>c.category);
            });
    }
    
    /** Called when a CSV file is selected; parses the file to determine CSV fields. */
    selectFile(files: File[]): void {
        if (files.length == 0) return;
        this.updated = null;
        this.missing = null;
        this.csv = files[0]
        if (!this.csv.name.endsWith(".csv") && !this.csv.name.endsWith(".tsv")) {
            this.messageService.error("File must be a CSV file."); // TODO i18n
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
                const lowercaseHeaders = component.headers.map(h=>h.toLowerCase())
                component.idColumn = lowercaseHeaders.indexOf("transcript");
                if (component.idColumn < 0) {
                    component.idColumn = lowercaseHeaders.indexOf("name");
                }
                if (component.idColumn < 0) {
                    component.idColumn = lowercaseHeaders.indexOf("id");
                }
                // set default mappings
                component.columnLayer = [];
                component.newLayerName = [];
                component.newLayerCategory = [];
                for (let f in lowercaseHeaders) {
                    const lowercaseHeader = lowercaseHeaders[f];
                    component.columnLayer.push(""); // default to ignore
                    component.newLayerName.push("");
                    component.newLayerCategory.push("General");
                    for (let layer of component.transcriptAttributeLayers) {
                        if (lowercaseHeader == layer.attribute.toLowerCase()
                            || lowercaseHeader == layer.description.toLowerCase()
                            || lowercaseHeader == layer.id.toLowerCase()) {
                            component.columnLayer[f] = layer.id;
                            break;
                        }
                    } // next transcript attribute
                }
            }
        };
        reader.onerror = function () {  
            component.messageService.error(`Error reading ${component.csv.name}`); // TODO i18n
        };
        reader.readAsText(this.csv);
    }

    processing = false;
    process() {
        this.processUpload();
    }
    
    /** Recursive function to first add new attributes, then upload the CSV file */
    processUpload() {
        // check for layers to create
        const nextNewAttributeIndex = this.columnLayer.indexOf("_create");
        if (nextNewAttributeIndex >= 0) { // need to add an attribute
            const newAttribute = this.newLayerName[nextNewAttributeIndex]
                || this.headers[nextNewAttributeIndex]; // default to column name
            const newLayerId = `transcript_${newAttribute}`;
            // check it doesn't already exist
            const newAttributeCategory = this.newLayerCategory[nextNewAttributeIndex];
            const existingLayer = this.transcriptAttributeLayers
                .findIndex(l=>l.id == newLayerId);
            if (existingLayer >= 0 ) { // existing layer
                if (confirm(`There is already an attribute called "${newAttribute}"\nDo you want to use it?`)) { // TODO i18n
                    this.columnLayer[nextNewAttributeIndex] = newLayerId;
                    // next step...
                    this.processUpload();
                } else { // cancel
                    document.getElementById(`column-{{nextNewAttributeIndex}}`).focus();
                }
            } else { // layer doesn't exist yet
                // create it
                this.processing = true;
                this.labbcatService.labbcat.newLayer(
                    newLayerId, this.schema.root.id,
                    newLayerId.replace("transcript_",""), 0,
                    false, false, true, true, "string", null, newAttributeCategory,
                    (newLayer, errors, messages) => {
                        this.processing = false;
                        if (errors) {
                            errors.forEach(m => this.messageService.error(m));
                        } else {
                            if (messages) messages.forEach(m => this.messageService.info(m));
                            // add new layer to list
                            this.transcriptAttributeLayers.push(newLayer as Layer);
                            // select it for this column
                            this.columnLayer[nextNewAttributeIndex] = newLayer.id;
                            
                            // next step...
                            this.processUpload();
                        }
                    });
            }
        } else { // upload data
            this.processing = true;
            this.labbcatService.labbcat.uploadTranscriptAttributes(
                this.csv, this.idColumn, this.columnLayer, (counts, errors, messages)=>{
                    this.processing = false;
                    this.uploadPercentProgress = null;
                    if (errors) {
                        errors.forEach(m => this.messageService.error(m));
                    } else {
                        if (messages) messages.forEach(m => this.messageService.info(m));
                        // hide mappings
                        this.headers = null;
                        // show results
                        this.updated = counts.updated;
                        this.missing = counts.missing;
                    }
                }, (e)=> { // progress
                    if (e.lengthComputable) {
                        // upload goes up to 50% only
	                this.uploadPercentProgress = Math.round(e.loaded * 100 / e.total);
                    }
                });
        } // upload data
    }
    
}

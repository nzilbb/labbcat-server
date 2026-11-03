import { Component, OnInit } from '@angular/core';

import { MessageService, LabbcatService, VersionInfo } from 'labbcat-common';
import { Layer, Task } from 'labbcat-common';

@Component({
  selector: 'app-transcripts-layers-regenerate',
  templateUrl: './transcripts-layers-regenerate.component.html',
  styleUrl: './transcripts-layers-regenerate.component.css'
})
export class TranscriptsLayersRegenerateComponent implements OnInit {
    schema: any;
    baseUrl: string;
    generableLayers: Layer[];
    idsFile: File;
    lineCount: number;
    preview: string;
    maybeCsv = false;
    layerId = "";

    threadId: string;

    constructor(
        private labbcatService: LabbcatService,
        private messageService: MessageService) { }
    
    ngOnInit(): void {
        // get baseUrl
        this.labbcatService.labbcat.getId((url, errors, messages) => {
            this.baseUrl = url;
        });
        // get layer schema so we can identify participant attributes
        this.labbcatService.labbcat.getSchema((schema, errors, messages) => {
            this.schema = schema;
            this.generableLayers = [];
            for (let layerId in schema.layers) {
                const layer = schema.layers[layerId] as Layer;
                if (layer.layer_manager_id && layer.id != this.schema.wordLayerId
                    && /T/.test(layer.enabled)) {
                    this.generableLayers.push(layer)
                } // participant attribute
            } // next layer
        });
    }

    previewLines = 10;
    /** Called when a file is selected; parses the file to determine CSV fields. */
    selectFile(files: File[]): void {
        if (files.length == 0) return;
        this.threadId = this.preview = null;
        this.processing = false;
        this.processingError = "";
        this.idsFile = files[0]
        if (!this.idsFile.name.endsWith(".txt")
            && !this.idsFile.name.endsWith(".csv") && !this.idsFile.name.endsWith(".tsv")) {
            this.messageService.error("File must be a plain text file."); // TODO i18n
            this.idsFile = null;
            return;
        }

        const reader = new FileReader();
        const component = this;
        reader.onload = () => {  
            const txtData = reader.result;  
            let transcriptList = (<string>txtData).split(/\r\n|\n/);
            // remove blank lines
            transcriptList = transcriptList.filter(l=>l.length>0);
            if (transcriptList.length == 0) {
                component.messageService.error("File is empty: " + component.idsFile.name);
            } else {
                this.lineCount = transcriptList.length;
                    
                // show preview of the file
                this.preview = transcriptList.slice(0,this.previewLines).join("\n");

                // have they picked a CSV file with multiple columns?
                this.maybeCsv = transcriptList[0].includes(",")
                    || transcriptList[0].includes("\t")
                    || transcriptList[0].includes(";");
            }
        };
        reader.onerror = function () {  
            component.messageService.error("Error reading " + component.idsFile.name);
        };
        reader.readAsText(this.idsFile);
    }
    
    processing = false;
    processingError = "";
    /** start processing */
    process(): void {
        
        this.processing = true;
        this.processingError = "";
        this.threadId = null;

        // post file
        const fd = new FormData();
        fd.append("layerId", this.layerId);
        fd.append("ids", this.idsFile);
        const regenerate = this.labbcatService.labbcat.createRequest(
            "regenerate", null, (model, errors, messages) => {
                if (errors) errors.forEach(m => this.messageService.error(m));
                if (messages) messages.forEach(m => this.messageService.info(m));
                if (model && model.threadId) {
                    this.threadId = model.threadId;
                } else {
                    this.processing = false;
                }
            },
            this.baseUrl+"api/edit/transcripts/layers/regenerate", "POST");
        try {
            regenerate.send(fd);
        } catch (x) {
            this.messageService.error(x);
        }
    }
    /** finished processing */
    finished(task: Task): void {
        this.processing = false;
    }
}

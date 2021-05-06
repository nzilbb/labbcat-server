import { Component, OnInit } from '@angular/core';

import { MessageService, LabbcatService } from 'labbcat-common';
import { Layer } from 'labbcat-common';

@Component({
    selector: 'app-praat',
    templateUrl: './praat.component.html',
    styleUrls: ['./praat.component.css']
})
export class PraatComponent implements OnInit {

    schema: any;
    participantAttributeLayers: Layer[];
    formantDifferentiationLayerId: string;
    pitchDifferentiationLayerId: string;
    
    csv: File;
    rowCount: number;
    headers: string[];
    transcriptColumn: number;
    participantColumn: number;
    startTimeColumn: number;
    endTimeColumn: number;
    
    windowOffset = 0.1;
    
    extractF1 = true;
    extractF2 = true;
    extractF3 = false;
    showAdvancedFormantSettings = false;
    samplePoints = "0.5";
    formantDifferentiateParticipants = true;
    formantCeilingDefault = 5500; // female
    formantOtherPattern = [ "M" ];
    formantCeilingOther = [ 5000 ]; // male
    scriptFormant = "To Formant (burg)... 0.0025 5 {max_formant} 0.025 50";
    
    extractMinimumPitch = false;
    extractMeanPitch = false;
    extractMaximumPitch = false;
    showAdvancedPitchSettings = false;
    pitchDifferentiateParticipants = true;
    pitchOtherPattern = [ "M" ];
    pitchFloorDefault = 60; // female
    pitchFloorOther = [ 30 ]; // male
    pitchCeilingDefault = 500; // female
    pitchCeilingOther = [ 250 ]; // male
    voicingThresholdDefault = 0.5; // female
    voicingThresholdOther = [ 0.4 ]; // male
    scriptPitch = "To Pitch (ac)...  0 {pitch_floor} 15 no 0.03 {voicing_threshold} 0.01 0.35 0.14 {pitch_ceiling}";
    
    extractMaximumIntensity = false;
    showAdvancedIntensitySettings = false;
    scriptIntensity = "To Intensity... {pitch_floor} 0 yes";
    extractCOG1 = false;
    extractCOG2 = false;
    extractCOG23 = false;

    showCustomScript = false;
    customScriptLayers = [];
    customScript = "";    
    
    constructor(
        private labbcatService: LabbcatService,
        private messageService: MessageService) { }
    
    ngOnInit(): void {
        // get layer schema so we can identify participant attributes
        this.labbcatService.labbcat.getSchema((schema, errors, messages) => {
            this.schema = schema;
            this.participantAttributeLayers = [];
            for (let layerId in schema.layers) {
                const layer = schema.layers[layerId] as Layer;
                if (layer.parentId == this.schema.participantLayerId
                    && layer.alignment == 0
                    && layer.id != "main_participant") {  // participant attribute
                    this.participantAttributeLayers.push(layer)
                } // participant attribute
            } // next layer
            let genderLayer = this.participantAttributeLayers.find(
                layer=>/(gender|sex)/i.test(layer.id));
            if (genderLayer) {
                this.formantDifferentiationLayerId = genderLayer.id;
                this.pitchDifferentiationLayerId = genderLayer.id;
            }
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
            const csvRecordsArray = (<string>csvData).split(/\r\n|\n/);
            if (csvRecordsArray.length == 0) {
                component.messageService.error("File is empty: " + component.csv.name);
            } else {
                this.rowCount = csvRecordsArray.length - 1; // (don't count header line)
                    
                // get headers...
                const firstLine = csvRecordsArray[0];
                // split the line into fields
                let delimiter = ",";
                if (firstLine.match(/.*\t.*/)) delimiter = "\t";
                const fields = firstLine.split(delimiter);
                // the fields might be quoted, so remove quotes
                component.headers = fields.map(f=>f.replace(/^"(.*)"$/g, "$1"))

                // set default column selections...
                this.transcriptColumn = this.headers.findIndex(
                    h=>/^transcript$/i.test(h))
                this.participantColumn = this.headers.findIndex(
                    h=>/^(speaker|participant|who)$/i.test(h))
                this.startTimeColumn = this.headers.findIndex(
                    h=>/^(segmentstart|segment start|target segment start)$/i.test(h))
                if (this.startTimeColumn < 0) { // nothing found
                    // anthing ending in ... start
                    this.startTimeColumn = this.headers.findIndex(
                        h=>/[ .]start$/i.test(h))
                }
                this.endTimeColumn = this.headers.findIndex(
                    h=>/^(segmentend|segment end|target segment end)$/i.test(h))
                if (this.endTimeColumn < 0) { // nothing found
                    // anthing ending in ... end
                    this.endTimeColumn = this.headers.findIndex(
                        h=>/[ .]end$/i.test(h))
                }
                
            }
        };
        reader.onerror = function () {  
            component.messageService.error("Error reading " + component.csv.name);
        };
        reader.readAsText(this.csv);
        
    }

    /** This function stops inputs bound to array elements losing focus when typing. */
    trackByFn(index, item) {
        return index;
    }
    
    /** Add another formant settings option */
    formantAddOther(): void {
        this.formantOtherPattern.push("");
        this.formantCeilingOther.push(this.formantCeilingDefault);
    }
    /** Remove last formant settings option */
    formantRemoveOther(): void {
        this.formantOtherPattern.pop();
        this.formantCeilingOther.pop();
    }
    
    /** Add another pitch settings option */
    pitchAddOther(): void {
        this.pitchOtherPattern.push("");
        this.pitchFloorOther.push(this.pitchFloorDefault);
        this.pitchCeilingOther.push(this.pitchCeilingDefault);
        this.voicingThresholdOther.push(this.voicingThresholdDefault);
    }
    /** Remove last pitch settings option */
    pitchRemoveOther(): void {
        this.pitchOtherPattern.pop();
        this.pitchFloorOther.pop();
        this.pitchCeilingOther.pop();
        this.voicingThresholdOther.pop();
    }

    /** Select participant attribute for inclusion in custom script */
    toggleCustomScriptLayer(id: string): void {
        if (this.customScriptLayers.includes(id)) { // id is in the list
            // remove id
            this.customScriptLayers = this.customScriptLayers.filter(l=>l!=id);
        } else {
            // add id
            this.customScriptLayers.push(id);
        }
    }

    scriptFile: File;
    /** Load script from local file */
    loadScript(files: File[]): void {
        if (files.length == 0) return;
        this.scriptFile = files[0];
        const reader = new FileReader();
        const component = this;
        // reader.readAsText(file);
        reader.onload = () => {  
            try {
                component.customScript = (<string>reader.result);
            } catch(exception) {
                component.messageService.error(
                    "Unable to parse " + component.scriptFile.name + ": " + exception); 
            }
        };
        reader.onerror = function () {  
            component.messageService.error("Error reading " + component.scriptFile.name);
        };
        reader.readAsText(component.scriptFile);
    }

    saveScript(): void {
        try {
            const scriptAsBlob = new Blob([this.customScript], {type:'text/plain'});
            const downloadLink = document.createElement("a");
            let fileName = "process.praat";
            try {
                fileName = this.scriptFile.name;
            } catch(x) {}
            downloadLink.download = fileName;
            downloadLink.style.display = "none";
            downloadLink.innerHTML = "Download File";
            downloadLink.href = (window.URL||window.webkitURL).createObjectURL(scriptAsBlob);
            document.body.appendChild(downloadLink);
            downloadLink.click();
        } catch(X) {
            this.messageService.error(X);
        }
    }
}

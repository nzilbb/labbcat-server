import { Component, OnInit, ViewChild, ElementRef } from '@angular/core';
import { ActivatedRoute } from '@angular/router';

import { SerializationDescriptor } from '../serialization-descriptor';
import { Response, Layer, User } from 'labbcat-common';
import { MessageService, LabbcatService } from 'labbcat-common';

@Component({
  selector: 'app-transcripts',
  templateUrl: './transcripts.component.html',
  styleUrls: ['./transcripts.component.css']
})
export class TranscriptsComponent implements OnInit {
    @ViewChild("form") form;
    
    schema: any;
    filterLayers: Layer[];
    transcriptAttributes: Layer[];
    generableLayers: Layer[];
    user: User;
    baseUrl: string;
    matchCount = -1;
    pageLength = 20;
    pageCount = 0;
    p = 1; // current page number
    pageLinks: string[];
    transcriptIds: string[];
    attributeValues = {};
    selectedIds: string[];
    filterValues = {};
    query = ""; // AGQL query string for matching transcripts
    
    serializers: SerializationDescriptor[];
    mimeTypeToSerializer = {};
    mimeType = "text/praat-textgrid";

    constructor(
        private labbcatService: LabbcatService,
        private messageService: MessageService,
        private route: ActivatedRoute
    ) { }
    
    ngOnInit(): void {
        this.filterLayers = [];
        this.selectedIds = [];
        this.readUserInfo();
        this.readBaseUrl();
        this.readSerializers();
        this.readSchema().then(()=> {
            this.route.queryParams.subscribe((params) => {
                this.p = parseInt(params["p"]) || 1;
                if (this.p < 1) this.p = 1;
                if (params["transcript"]) {
                    this.filterValues["transcript"] = [params["transcript"]];
                }
                this.listTranscripts();
            });
        });
    }
    
    readSchema(): Promise<void> {
        return new Promise((resolve, reject) => {
            this.labbcatService.labbcat.getSchema((schema, errors, messages) => {
                this.schema = schema;
                this.schema.root.description = "Transcript";
                this.transcriptAttributes = [];
                this.filterLayers = [];
                this.generableLayers = [];
                // allow filtering by transcript ID, corpus, and episode
                this.filterLayers.push(schema.root);
                this.filterValues[schema.root.id] = [];
                this.filterLayers.push(schema.layers[schema.corpusLayerId]);
                this.filterValues[schema.corpusLayerId] = [];
                //this.filterLayers.push(schema.layers[schema.episodeLayerId]);
                //this.filterValues[schema.episodeLayerId] = [];
                // and by selected transcript attributes
                for (let layerId in schema.layers) {
                    const layer = schema.layers[layerId] as Layer;
                    if (layer.parentId == schema.root.id
                        && layer.alignment == 0
                        && layer.id != schema.participantLayerId) {
                        this.transcriptAttributes.push(layer);
                        if (schema.layers[layerId].searchable == 1) {
                            this.filterValues[layer.id] = [];
                            this.filterLayers.push(layer);
                        }
                    }
                    if (layer.layer_manager_id && layer.id != schema.wordLayerId) {
                        this.generableLayers.push(layer);
                    }
                }
                resolve();
            });
        });
    }
    
    readUserInfo(): void {
        this.labbcatService.labbcat.getUserInfo((user, errors, messages) => {
            this.user = user as User;
        });
    }
    
    readBaseUrl(): void {
        this.labbcatService.labbcat.getId((url, errors, messages) => {
            this.baseUrl = url;
        });
    }
    
    readSerializers(): void {
        this.labbcatService.labbcat.getSerializerDescriptors((descriptors, errors, messages) => {
            if (errors) errors.forEach(m => this.messageService.error(m));
            if (messages) messages.forEach(m => this.messageService.info(m));
            this.serializers = [];
            for (let descriptor of descriptors) {
                this.serializers.push(descriptor as SerializationDescriptor);
                this.mimeTypeToSerializer[descriptor.mimeType]
                    = descriptor as SerializationDescriptor;
            }
        });
    }
    
    filterChange(layer: Layer, values: string[]): void {
        this.filterValues[layer.id] = values;
        this.deferredListTranscripts();
    }

    listTranscriptsTimer = -1;
    /** list transcripts after a short delay to give the user time to keep typing or
        change another filter */
    deferredListTranscripts(): void {
        if (this.listTranscriptsTimer >= 0) {
            window.clearTimeout(this.listTranscriptsTimer);
        }
        this.listTranscriptsTimer = window.setTimeout(()=>{
            this.p = 1;
            this.listTranscripts();
            this.listTranscriptsTimer = -1;
        }, 2000);
    }
    
    loadingList = false;
    /** List transcripts that match the filters */
    listTranscripts(): void {
        this.query = "";
        for (let layer of this.filterLayers) {

            if (layer.id == this.schema.root.id
                && this.filterValues[layer.id][0]) {
                // transcript layer
                if (this.query) this.query += " && ";
                
                this.query += "/"+this.esc(this.filterValues[layer.id][0])+"/.test(id)";
                
            } else if (layer.validLabels && Object.keys(layer.validLabels).length > 0
                && this.filterValues[layer.id].length > 0) {
                // select from possible values
                if (this.query) this.query += " && ";

                // the value "!" means "a label other than the labels in validLabels"...
                
                if (!this.filterValues[layer.id].includes("!")) {
                    // ordinary positive selection 
                    this.query += JSON.stringify(this.filterValues[layer.id])
                        +".includes(first('"+this.esc(layer.id)+"').label)";
                } else { // "!" 'other' selected
                    // so we *exclude* all values not selected
                    const labelsToExclude = Object.keys(layer.validLabels)
                        .filter(l=>!this.filterValues[layer.id].includes(l));
                    this.query += "!"+JSON.stringify(labelsToExclude)
                        +".includes(first('"+this.esc(layer.id)+"').label)";
                }
            } else if (layer.type == "number"
                && this.filterValues[layer.id].length > 1) {
                
                // from?
                if (this.filterValues[layer.id][0]) {
                    if (this.query) this.query += " && ";
                    const value = (layer.subtype == "integer"?
                        parseInt:parseFloat)(this.filterValues[layer.id][0])
                    this.query += "first('"+this.esc(layer.id)+"').label >= "+ value;
                }
                
                // to?
                if (this.filterValues[layer.id][1]) {
                    if (this.query) this.query += " && ";
                    
                    const value = (layer.subtype == "integer"?
                        parseInt:parseFloat)(this.filterValues[layer.id][1])
                    this.query += "first('"+this.esc(layer.id)+"').label <= "+ value;
                }
            } else if ((layer.subtype == "date" || layer.subtype == "datetime")
                && this.filterValues[layer.id].length > 1) {
                
                // from?
                if (this.filterValues[layer.id][0]) {
                    if (this.query) this.query += " && ";
                    const value = this.filterValues[layer.id][0];
                    this.query += "first('"+this.esc(layer.id)+"').label"
                    +" >= '"+this.esc(value)+"'";
                }
                
                // to?
                if (this.filterValues[layer.id][1]) {
                    if (this.query) this.query += " && ";
                    
                    const value = this.filterValues[layer.id][1];
                    this.query += "first('"+this.esc(layer.id)+"').label"
                    +" <= '"+value+" 23:59:59'";
                }
            } else if (layer.type == "boolean"
                && this.filterValues[layer.id][0]) {
                if (this.query) this.query += " && ";
                
                this.query += "first('"+this.esc(layer.id)+"').label = "
                    + this.filterValues[layer.id][0];
                
            } else if (this.filterValues[layer.id][0]) { // assume regexp match
                if (this.query) this.query += " && ";
                
                this.query += "/"+this.esc(this.filterValues[layer.id][0])+"/"
                    +".test(labels('" +this.esc(layer.id)+"'))";
                
            }
        } // next filter layer
        this.loadingList = true;
        // count matches
        this.labbcatService.labbcat.countMatchingTranscriptIds(
            this.query, (matchCount, errors, messages) => {
                this.matchCount = matchCount;
                if (errors) {
                    errors.forEach(m => this.messageService.error(m));
                    this.loadingList = false;
                }
                if (messages) messages.forEach(m => this.messageService.info(m));
                this.pageCount = parseInt(matchCount) / this.pageLength;
                if (matchCount % this.pageLength != 0)  this.pageCount++;
                if (this.p > this.pageCount) this.p = Math.max(1,this.pageCount);
                
                // now get the matches for this page
                this.labbcatService.labbcat.getMatchingTranscriptIds(
                    this.query, this.pageLength, this.p - 1 /* zero-based page numbers */,
                    (transcriptIds, errors, messages) => {
                        if (errors) errors.forEach(m => this.messageService.error(m));
                        if (messages) messages.forEach(m => this.messageService.info(m));
                        
                        this.loadingList = false;
                        this.transcriptIds = transcriptIds;
                        this.selectedIds = [];
                        
                        // attribute values
                        for (let id of this.transcriptIds) {
                            // if we don't already have their attributes
                            if (!this.attributeValues[id]) {
                                this.attributeValues[id] = {};
                                // get the attributes
                                this.getAttributeValues(id);
                            }
                        } // next transcript
                        
                        // create page links
                        this.pageLinks = [];
                        for (let pg = 1; pg <= this.pageCount; pg++) {
                            this.pageLinks.push(""+pg);
                            if (this.pageCount < 1000) { // not too many pages, so get a hint
                                const hintIndex = pg - 1;
                                // the hint for the page is the first ID on that page
	                        this.labbcatService.labbcat.getMatchingTranscriptIds(
                                    this.query, 1, this.pageLength * (pg-1),
                                    (pgIds, errors, messages) => {
                                        this.pageLinks[hintIndex] = pgIds[0];
	                            });
                            } // small number of pages
                        } // next page link
                    });
            });
    }

    getAttributeValues(id: string): void {
        this.labbcatService.labbcat.getTranscript(
            id, this.filterLayers.map(layer => layer.id),
            (transcript, errors, messages) => {
                if (errors) errors.forEach(m => this.messageService.error(m));
                if (messages) messages.forEach(m => this.messageService.info(m));
                this.attributeValues[id] = transcript;
            });
    }

    goToPage(p: number): void {
        this.p = p;
        this.listTranscripts();
    }
    
    deleting = false;
    /** Button action */
    deleteTranscripts(): void {
        if (confirm("Are you sure you want to delete selected transcript?")) { // TODO i18n
            const deletions = [];
            // delete each selected transcript
            for (let id of this.selectedIds) {
                const transcriptId = id;
                const component = this;
                deletions.push(new Promise<null>((accept, reject) => {
                    this.labbcatService.labbcat.deleteTranscript(
                        transcriptId, (nothing, errors, messages) => {
                            if (errors) errors.forEach(m => this.messageService.error(m));
                            if (messages) messages.forEach(m => this.messageService.info(m));
                            if (!errors) {
                                // remove the transcript from the model
                                component.transcriptIds = component.transcriptIds.filter(
                                    p => p != transcriptId);
                            }
                            // either way, deselect it
                            component.selectedIds = component.selectedIds.filter(
                                p => p != transcriptId);
                            accept();
                        });
                }));
            } // next selected transcript
            
            // once all the requests have finished
            Promise.all(deletions).then(ids => {
                // reload the page to fill up removed slots
                this.listTranscripts();
            });
        } // are you sure?
    }

    showGenerateLayerSelection = false;
    /** Button action */
    generate(): void {
        if (!this.showGenerateLayerSelection) { // show options
            this.showGenerateLayerSelection = true;
            this.showAttributesSelection = this.showSerializationOptions = false;
        } else { // options selected, so go ahead and do it
            this.form.nativeElement.action = this.baseUrl + "edit/layers/regenerate";
            this.form.nativeElement.submit();
        }
    }

    /** Button action */
    exportMedia(): void {
        this.showAttributesSelection = this.showSerializationOptions
            = this.showGenerateLayerSelection = false;
        if (this.selectedIds.length == 0 && this.matchCount > 10) {
            if (!confirm("This will export all "+this.matchCount+" matches.\nAre you sure?")) { // TODO i18n
                return;
            }
        }
        this.mimeType = "audio/wav";
        // give the form binding a chance to update
        setTimeout(()=>{
            this.form.nativeElement.action = this.baseUrl + "api/files";
            this.form.nativeElement.submit();
        }, 100);
    }

    /** Button action */
    exportTranscripts(): void {
        this.showAttributesSelection = this.showSerializationOptions
            = this.showGenerateLayerSelection = false;
        if (this.selectedIds.length == 0 && this.matchCount > 10) {
            if (!confirm("This will export all "+this.matchCount+" matches.\nAre you sure?")) { // TODO i18n
                return;
            }
        }
        this.mimeType = ""; // no media type = source transcript files
        // give the form binding a chance to update
        setTimeout(()=>{
            this.form.nativeElement.action = this.baseUrl + "api/files";
            this.form.nativeElement.submit();
        }, 100);
    }

    showAttributesSelection = false;
    /** Button action */
    exportAttributes(): void {
        if (!this.showAttributesSelection) { // show options
            this.showAttributesSelection = true;
            this.showSerializationOptions = this.showGenerateLayerSelection = false;
        } else { // options selected, so go ahead and do it            
            if (this.selectedIds.length == 0 && this.matchCount > 10) {
                if (!confirm("This will export all "+this.matchCount+" matches.\nAre you sure?")) { // TODO i18n
                    return;
                }
            }
            this.form.nativeElement.action = this.baseUrl + "api/attributes";
            this.form.nativeElement.submit();
        }
    }

    serializeImg = "cog.svg";
    showSerializationOptions = false;
    /** Button action */
    exportFormat(): void {
        if (!this.showSerializationOptions) { // show options
            this.showSerializationOptions = true;
            this.showAttributesSelection = this.showGenerateLayerSelection = false;
            // default to Praat TextGrid
            this.mimeType = "text/praat-textgrid";
            // display format icon on button:
            this.onChangeMimeType();
        } else { // options selected, so go ahead and do it
            if (this.selectedIds.length == 0 && this.matchCount > 10) {
                if (!confirm("This will export all "+this.matchCount+" matches.\nAre you sure?")) { // TODO i18n
                    return;
                }
            }
            this.form.nativeElement.action = this.baseUrl + "api/serialize/graphs";
            this.form.nativeElement.submit();
        }
    }
    onChangeMimeType(): void {
        this.serializeImg = this.mimeTypeToSerializer[this.mimeType].icon;
    }

    /** Query to append to href for links to other pages */
    queryString(): string {
        let q = "";
        if (this.filterValues["transcript"][0]) {
            q += "&transcript="+this.filterValues["transcript"][0];
        }
        return q;
    }

    /** Add escapes for query string values */
    esc(value: string): string {
        return value.replace(/\\/,"\\\\").replace(/'/,"\\'");
    }
}

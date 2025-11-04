import { Component, OnInit, ViewChild, ElementRef, Inject } from '@angular/core';
import { ActivatedRoute, Router, Params } from '@angular/router';

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
    queryDescription = ""; // Human-readable version of the query
    participantQuery = ""; // AGQL query string for matching participants
    participantDescription = ""; // Human readable description of participant query
    transcriptQuery = ""; // AGQL query string for pre-matching transcripts
    transcriptDescription = ""; // Human readable description of transcript query
    defaultParticipantFilter = "";
    // track how many queries we're up to, to avoid old long queries updating the UI when
    // new short queries already have.
    querySerial = 0; 
    nextPage: string;
    searchJson: string;
    imagesLocation: string;
    
    serializers: SerializationDescriptor[];
    mimeTypeToSerializer = {};
    mimeType = "text/praat-textgrid";

    constructor(
        private labbcatService: LabbcatService,
        private messageService: MessageService,
        private route: ActivatedRoute,
        private router: Router,
        @Inject('environment') private environment
    ) {
        this.imagesLocation = this.environment.imagesLocation;
    }
    
    ngOnInit(): void {        
        this.filterLayers = [];
        this.selectedIds = [];
        this.readUserInfo();
        this.readBaseUrl();
        this.readSerializers();
        this.readSchema().then(() => {
            this.initializeFilters().then(() => {
                this.determineQueryParameters().then(() => {
                    this.parseQueryParameters();
                });
            });
        });
    }

    readSchema(): Promise<void> {
        return new Promise((resolve, reject) => {
            this.labbcatService.labbcat.getSchema((schema, errors, messages) => {
                this.schema = schema;
                this.schema.root.description = "Transcript name";
                resolve();
            });
        });
    }
    
    initializeFilters(): Promise<void> {
        return new Promise((resolve, reject) => {
            this.transcriptAttributes = [];
            this.filterLayers = [];
            this.generableLayers = [];
            // allow filtering by transcript ID, corpus, episode, and type
            this.filterLayers.push(this.schema.root);
            this.filterValues[this.schema.root.id] = [];
            this.filterLayers.push(this.schema.layers[this.schema.corpusLayerId]);
            this.filterValues[this.schema.corpusLayerId] = [];
            //TODO this.filterLayers.push(this.schema.layers[this.schema.episodeLayerId]);
            //TODO this.filterValues[this.schema.episodeLayerId] = [];
            this.filterLayers.push(this.schema.layers["transcript_type"]);
            this.filterValues["transcript_type"] = [];
            // and by selected transcript attributes
            for (let layerId in this.schema.layers) {
                const layer = this.schema.layers[layerId] as Layer;
                if (layer.parentId == this.schema.root.id
                    && layer.alignment == 0
                    && layer.id != this.schema.participantLayerId) {
                    this.transcriptAttributes.push(layer);
                    if (this.schema.layers[layerId].searchable == 1) {
                        this.filterValues[layer.id] = [];
                        this.filterLayers.push(layer);
                    }
                }
                if (layer.layer_manager_id && layer.id != this.schema.wordLayerId
                    && /T/.test(layer.enabled)) {
                    this.generableLayers.push(layer);
                }
            }
            // read default participant filter
            this.labbcatService.labbcat.getSystemAttribute("defaultParticipantFilter",
                (attribute, errors, messages) => {
                    this.defaultParticipantFilter = attribute.value;
                }
            );
            resolve();
        });
    }
    // we try to remember search parameters during the session, but there are some exceptions:
    passthroughPatterns = [
        /[?&](p)=([^&]*)/,
        /[?&](to)=([^&]*)/,
        /[?&](participant_expression)=([^&]*)/,
        /[?&](participants)=([^&]*)/,
        /[?&](transcript_expression)=([^&]*)/,
        /[?&](transcripts)=([^&]*)/,
        /[?&](searchJson)=([^&]*)/
    ];
    
    /** if no query parameters are passed, load the default from system settings */
    determineQueryParameters(): Promise<void> {
        return new Promise((resolve, reject) => {
            let queryString = window.location.search;
            // ensure parameters that are ignored for default query purposes are passed through
            let passthroughParameters = "";
            for (let pattern of this.passthroughPatterns) {
                const match = queryString.match(pattern);
                if (match) {
                    passthroughParameters += `&${match[1]}=${match[2]}`;
                    // remove the parameter from the comparison string
                    queryString = queryString.replace(pattern, "");
                }
            }
            if (queryString // there is a query string
                || (window.location.search||"") // or list of transcripts for a participant
                       .startsWith("?transcript_expression=labels(%22participant%22)")
                || (window.location.search||"") // ...or several participants
                       .startsWith("?participant_expression=%5B")) {
                // nothing further to do
                resolve();
            } else {
                // use the last query string for this session
                queryString = sessionStorage.getItem("lastQueryTranscripts");
                if (queryString) { // they've previously made a query
                    // use that one
                    const queryParams: Params = {};
                    this.changeUrlParameters(`${queryString}${passthroughParameters}`);        
                    resolve();
                } else { // they haven't previously made a query
                    // load the default from system settings
                    this.labbcatService.labbcat.getSystemAttribute(
                        "defaultTranscriptFilter", (attribute, errors, messages) => {
                            if (attribute.value) {
                                queryString = attribute.value;
                                this.changeUrlParameters(`${queryString}${passthroughParameters}`);        
                            }
                            resolve();
                        });
                }
            }
        });        
    }

    changeUrlParameters(newParameters: string) {
        const queryParams: Params = {};
        for (let param of newParameters.split("&")) {
            const parts = param.split("=", 2);
            queryParams[parts[0]] = decodeURIComponent(parts[1]);
        }
        this.router.navigate([], {
            relativeTo: this.route,
            replaceUrl: true,
            queryParams
        });        
    }

    /** load config from query parameters */
    parseQueryParameters(): void {
        this.route.queryParams.subscribe((params) => {
            // remember the query for next time
            let queryString = window.location.search;
            for (let pattern of this.passthroughPatterns) { // but not the passthrough parameters
                queryString = queryString.replace(pattern, "");
            }
            // strip any leading/trailing parameter delimiters
            queryString = queryString.replace(/^[?&]/,"").replace(/[?&]$/,"");
            if (queryString) {
                // save the query in session storage
                sessionStorage.setItem("lastQueryTranscripts", queryString);
            }
            // page number
            this.p = parseInt(params["p"]) || 1;
            if (this.p < 1) this.p = 1;
            // set any layer parameter values to their corresponding filters
            for (let layerId in params) {
                if (params[layerId]) { // there's a parameter for this filter layer
                    this.filterValues[layerId] = params[layerId].split(",");
                }
            }
            if (params["participant_expression"]) {
                this.participantQuery = params["participant_expression"];
                if (params["participants"]) {
                    this.participantDescription = params["participants"];
                } else {
                    this.participantDescription = "Selected participants";
                }
            }
            if (params["transcript_expression"]) {
                this.transcriptQuery = params["transcript_expression"];
                if (params["transcripts"]) {
                    this.transcriptDescription = params["transcripts"];
                } else {
                    this.transcriptDescription = "Selected transcripts";
                }
            }
            if (params["to"]) {
                this.nextPage = params["to"];
                if (this.nextPage=="search" && params["searchJson"]) {
                    this.searchJson = params["searchJson"];
                }
            }
            this.listTranscripts();
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
            // alphabetical order
            this.serializers.sort((a,b) => a.name.charCodeAt(0) - b.name.charCodeAt(0));
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

    /** the user can hit enter to skip the deferral */
    enterKeyPressed(): void {
        if (this.listTranscriptsTimer >= 0) {
            window.clearTimeout(this.listTranscriptsTimer);
            this.listTranscriptsTimer = -1;
        }
        this.listTranscripts();
    }
    
    loadingList = false;
    /** List transcripts that match the filters */
    listTranscripts(): void {
        this.query = this.transcriptQuery; // if any
        this.queryDescription = this.transcriptDescription;
        for (let layer of this.filterLayers) {

            if (layer.id == this.schema.root.id
                && this.filterValues[layer.id][0]) {
                // transcript layer
                if (this.query) {
                    this.query += " && ";
                    this.queryDescription += ", ";
                }
                
                this.query += "/"+this.esc(this.filterValues[layer.id][0])+"/.test(id)";
                this.queryDescription += "ID matches " +this.filterValues[layer.id][0];
                
            } else if (layer.validLabels && Object.keys(layer.validLabels).length > 0
                && this.filterValues[layer.id].length > 0) {
                // select from possible values
                if (this.query) {
                    this.query += " && ";
                    this.queryDescription += ", ";
                }
                this.filterValues[layer.id].sort();

                // the value "!" means "a label other than the labels in validLabels"...
                
                if (!this.filterValues[layer.id].includes("!")) {
                    // ordinary positive selection 
                    this.query += JSON.stringify(this.filterValues[layer.id])
                        +".includesAny(labels('"+this.esc(layer.id)+"'))";
                    if (this.filterValues[layer.id].length == 1) {
                        this.queryDescription += layer.description
                            + " = " + this.filterValues[layer.id][0];
                    } else {
                        this.queryDescription += layer.description
                            + " in (" + this.filterValues[layer.id].join(",") + ")";
                    }
                } else { // "!" 'other' selected
                    // so we *exclude* all values not selected
                    const labelsToExclude = Object.keys(layer.validLabels)
                        .filter(l=>!this.filterValues[layer.id].includes(l));
                    this.query += "!"+JSON.stringify(labelsToExclude)
                        +".includesAny(labels('"+this.esc(layer.id)+"'))";
                    if (labelsToExclude.length == 1) {
                        this.queryDescription += layer.description
                            + " ≠ " + this.filterValues[layer.id][0];
                    } else {
                        this.queryDescription += layer.description
                            + " not in " + labelsToExclude.join(",").replace(/^,/,"");
                    }
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
                
                if (this.filterValues[layer.id][0] && this.filterValues[layer.id][1]) {
                    if (this.queryDescription) this.queryDescription += ", ";
                    this.queryDescription += layer.description
                        +" " + this.filterValues[layer.id][0]
                        + "–" + this.filterValues[layer.id][1];
                } else if (this.filterValues[layer.id][0]) {
                    if (this.queryDescription) this.queryDescription += ", ";
                    this.queryDescription += layer.description
                        +" ≥ " + this.filterValues[layer.id][0];
                } else if (this.filterValues[layer.id][1]) {
                    if (this.queryDescription) this.queryDescription += ", ";
                    this.queryDescription += layer.description
                        +" ≤ " + this.filterValues[layer.id][1];
                }

            } else if ((layer.subtype == "date" || layer.subtype == "datetime")
                && this.filterValues[layer.id].length > 1) {
                
                // from?
                if (this.filterValues[layer.id][0]) {
                    if (this.query) {
                    this.query += " && ";
                    this.queryDescription += ", ";
                }
                    const value = this.filterValues[layer.id][0];
                    this.query += "first('"+this.esc(layer.id)+"').label"
                    +" >= '"+this.esc(value)+"'";
                }
                
                // to?
                if (this.filterValues[layer.id][1]) {
                    if (this.query) {
                    this.query += " && ";
                    this.queryDescription += ", ";
                }
                    
                    const value = this.filterValues[layer.id][1];
                    this.query += "first('"+this.esc(layer.id)+"').label"
                    +" <= '"+value+" 23:59:59'";
                }
                
                if (this.filterValues[layer.id][0] && this.filterValues[layer.id][1]) {
                    if (this.queryDescription) this.queryDescription += ", ";
                    this.queryDescription += layer.description
                        +" " + this.filterValues[layer.id][0]
                        + "–" + this.filterValues[layer.id][1];
                } else if (this.filterValues[layer.id][0]) {
                    if (this.queryDescription) this.queryDescription += ", ";
                    this.queryDescription += layer.description
                        +" ≥ " + this.filterValues[layer.id][0];
                } else if (this.filterValues[layer.id][1]) {
                    if (this.queryDescription) this.queryDescription += ", ";
                    this.queryDescription += layer.description
                        +" ≤ " + this.filterValues[layer.id][1];
                }
            } else if (layer.type == "boolean"
                && this.filterValues[layer.id][0]) {
                if (this.query) {
                    this.query += " && ";
                    this.queryDescription += ", ";
                }
                
                this.query += "first('"+this.esc(layer.id)+"').label = "
                    + this.filterValues[layer.id][0];
                this.queryDescription += (this.filterValues[layer.id][0]=="1"?"":"NOT ")
                    +layer.description;
                
            } else if (this.filterValues[layer.id][0]) { // assume regexp match
                if (this.query) {
                    this.query += " && ";
                    this.queryDescription += ", ";
                }
                
                this.query += "/"+this.esc(this.filterValues[layer.id][0])+"/"
                    +".test(labels('" +this.esc(layer.id)+"'))";
                this.queryDescription += layer.description
                    +" matches " + this.filterValues[layer.id][0]
                
            }
        } // next filter layer
        // change the query string so the user can easily replicate this filter
        const queryParams: Params = {};
        if (this.nextPage) queryParams.to = this.nextPage; // pass through context parameters...
        if (this.participantQuery) queryParams.participant_expression = this.participantQuery;
        if (this.participantDescription) queryParams.participants = this.participantDescription;
        if (this.transcriptQuery) queryParams.transcript_expression = this.transcriptQuery;
        if (this.transcriptDescription) queryParams.transcripts = this.transcriptDescription;
        if (this.searchJson) queryParams.searchJson = this.searchJson;
        for (let layer of this.filterLayers) { // for each filter layer
            if (this.filterValues[layer.id].length > 0) { // there's at least one value
                // add it to the query parameters
                queryParams[layer.id] = this.filterValues[layer.id].join(",");
            }
        } // next filter layer
        this.router.navigate([], {
            relativeTo: this.route,
            replaceUrl: true,
            queryParams
        });        

        this.loadingList = true;
        const thisQuery = ++this.querySerial;
        let queryExpression = this.query;
        if (this.participantQuery) {
            if (queryExpression) queryExpression += " && ";
            queryExpression += this.participantQuery;
        }
        // count matches
        this.labbcatService.labbcat.countMatchingTranscriptIds(
            queryExpression, (matchCount, errors, messages) => {
                if (thisQuery != this.querySerial) return; // new query already sent
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
                    queryExpression, this.pageLength, this.p - 1 /* zero-based page numbers */,
                    (transcriptIds, errors, messages) => {
                        if (thisQuery != this.querySerial) return; // new query already sent
                        if (errors) errors.forEach(m => this.messageService.error(m));
                        if (messages) messages.forEach(m => this.messageService.info(m));
                        
                        this.loadingList = false;
                        // selected IDs first ...
                        this.transcriptIds = this.selectedIds
                        // ... then listed IDs ...
                            .concat(transcriptIds
                                    // ... that aren't already selected
                            .filter(id => !this.selectedIds.includes(id)));
                        
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
                        } // next page link
                        if (this.pageCount < 1000) { // not too many pages, so get hints
                            this.retrievePageHints(queryExpression);
                        }

                        // revert to 20 per page for next time (in case we listed all this time)
                        this.pageLength = 20;
                    });
            });
    }

    pageHintTimer: number;
    nextHintPage: number;
    hintQueryExpression: string;
    /** start getting page hints, but not all at once, so other higher priority queries
     * don't get tied up waiting */
    retrievePageHints(queryExpression: string): void {
        if (this.hintQueryExpression != queryExpression) { // query has changed
            // ... so start again from the first page
            this.nextHintPage = 1;
            this.hintQueryExpression = queryExpression;
            if (!this.pageHintTimer) {
                this.pageHintTimer = setInterval(()=>this.nextPageHint(), 300);
            }
        }
    }
    /** called by timer to get hint for next page */
    nextPageHint(): void {
        if (this.nextHintPage > this.pageCount) { // no more pages
            clearInterval(this.pageHintTimer);
            this.pageHintTimer = null;
        } else { // get the next page hint
            const hintIndex = this.nextHintPage - 1;
	    this.labbcatService.labbcat.getMatchingTranscriptIds(
                this.hintQueryExpression, 1, this.pageLength * hintIndex,
                (pgIds, errors, messages) => {
                    this.pageLinks[hintIndex] = pgIds[0];
	        });
            this.nextHintPage++;
        }
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
    
    listAll(): void {
        if (this.matchCount > 200) {
            if (!confirm("There are " + this.matchCount + " matches." // TODO i18n
                         +"\nAre you sure you want to list them all?")) {
                return;
            }
        }
        this.pageLength = this.matchCount;
        this.p = 1;
        this.listTranscripts();
    }

    /** Button action */
    clearParticipantFilter() : void {
        this.participantQuery = "";
        this.participantDescription = "";
        this.router.navigate([], {
            queryParams: {
                participant_expression: null,
                participants: null
            },
            queryParamsHandling: 'merge'
        });
    }

    /** Button action */
    clearFilters() : void {
        sessionStorage.setItem("lastQueryTranscripts", "");
        this.initializeFilters().then(()=>{
            this.listTranscripts();
        });
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
                deletions.push(new Promise<void>((accept, reject) => {
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
            this.serializeImg = "cog.svg";
        } else { // options selected, so go ahead and do it
            this.form.nativeElement.action = this.baseUrl + "edit/layers/regenerate";
            this.form.nativeElement.submit();
        }
    }
    collapseGenerate(): void {
        this.showGenerateLayerSelection = false;
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
            this.serializeImg = "cog.svg";
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
    collapseExportAttributes(): void {
        this.showAttributesSelection = false;
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
    collapseExportFormat(): void {
        this.showSerializationOptions = false;
        this.serializeImg = "cog.svg";
    }

    /** Button action */
    participants(): void {
        let params = {};
        if (this.selectedIds.length > 0) { // individual check-boxes selected
            // check if the user has selected all check-boxes corresponding to a filter
            // TODO handle the case where all transcripts' check-boxes are selected
            const allFilteredSelected = this.selectedIds.length == this.matchCount &&
                this.selectedIds.length == this.transcriptIds.length &&
                this.selectedIds.every((x, i) => x == this.transcriptIds[i]);
            // participants page will throw an error if passed transcript query attributes
            //   "Can only get labels list for participant or transcript attributes: labels('transcript_type')"
            // so we just pass the transcripts as a list and dress up the description
            params = {
                transcript_expression: "["
                    + this.selectedIds.map(id=>"'"+id.replace(/'/,"\\'")+"'").join(",")
                    + "].includesAny(labels('transcript'))"
            };
            if (allFilteredSelected) { // user has selected all check-boxes corresponding to a filter
                params["transcripts"] = this.queryDescription;
            } else { // typical check-box use case: a proper subset of filtered check-boxes are selected
                params["transcripts"] = this.selectedIds.length + " selected transcript" + (this.selectedIds.length > 1 ? "s" : "");
            }
        } else if (this.query) { // no check-boxes selected but some filter applied
            // participants page will throw an error if passed transcript attributes
            //   "Can only get labels list for participant or transcript attributes: labels('transcript_type')"
            // so we just pass the transcripts as a list and dress up the description
            const ids = this.transcriptIds.map(id=>"'"+this.esc(id)+"'").join(",");
            // this won't include transcripts that were selected prior to the filter then deselected - but that's consistent with other button behaviors
            params = {
                transcript_expression: `[${ids}].includesAny(labels('transcript'))`,
                transcripts: this.queryDescription
            }
        } else { // no check-boxes selected or filter applied
            params = {
                transcript_expression: "/.+/.test(id)",
                transcripts: "all transcripts"
            };
        }
        // if there's a default participant filter and no remembered participant filter, override the default
        if (this.defaultParticipantFilter && !sessionStorage.getItem("lastQueryParticipants")) {
            for (let param of this.defaultParticipantFilter.split("&").map(a => a.replace(/=.+/, ''))) {
                params[param] = "";
            }
        }
        this.router.navigate(["participants"], { queryParams: params });
    }

    /** Button action */
    layeredSearch(): void {
        let params = this.selectedTranscriptsQueryParameters("participant_id");
        if (this.searchJson) params["searchJson"] = this.searchJson;
        if (this.participantDescription) params["participants"] = this.participantDescription;
        this.router.navigate(["search"], { queryParams: params });
    }

    /** Button action */
    setDefaultFilter(): void {
        if (confirm("Are you sure you want this to be the default filter for everyone?")) { // TODO i18n
            this.labbcatService.labbcat.updateSystemAttribute(
                "defaultTranscriptFilter",
                window.location.search.replace(/^\?/,""),
                (result, errors, messages) => {
                    if (errors) {
                        errors.forEach(m => this.messageService.error(m));
                        this.loadingList = false;
                    }
                    if (messages) messages.forEach(m => this.messageService.info(m));
                });
        }
    }

    /** Query string for selected transcripts */
    selectedTranscriptsQueryParameters(transcriptIdParameter: string): Params {
        let params = {};
        if (this.selectedIds.length > 0) { // individual check-boxes selected
            // check if the user has selected all check-boxes corresponding to a filter
            const allFilteredSelected = this.selectedIds.length == this.matchCount &&
                this.selectedIds.length == this.transcriptIds.length &&
                this.selectedIds.every((x, i) => x == this.transcriptIds[i]);
            // TODO handle the case where all transcripts' check-boxes are selected
            if (allFilteredSelected) { // user has selected all check-boxes corresponding to a filter
                params = {
                    transcript_expression: this.query,
                    transcripts: this.queryDescription
                };
            } else { // typical check-box use case: a proper subset of filtered check-boxes are selected
                // don't send a transcripts param (transcript count is visible in tab title)
                params = {
                    transcript_expression: "["
                        + this.selectedIds.map(id=>"'"+id.replace(/'/,"\\'")+"'").join(",")
                        + "].includes(id)"
                };
            }
        } else if (this.query) { // no check-boxes selected but some filter applied
            params = {
                transcript_expression: this.query,
                transcripts: this.queryDescription
            };
        } else { // no check-boxes selected or filter applied
            params = {
                transcript_expression: "/.+/.test(id)",
                transcripts: "all transcripts"
            };
        }
        if (this.participantQuery) {
            // expressions like:
            //  labels("participant").includes(["AP511_MikeThorpe"])
            // or from participantso page:
            //  ['AP511_MikeThorpe'].includesAny(labels('participant'))
            // have to be replaced with:
            //  ['AP511_MikeThorpe'].includes(id)
            params["participant_expression"] = 
                this.participantQuery
                    .replace(/labels\('participant'\).includesAny\((\[.*\])\)/,
                             "$1\.includes\(id\)")
            // from participants page:
                    .replace(".includesAny(labels('participant'))",
                             ".includes(id)");
            if (this.selectedIds.length==0 && !this.query) {
                params["transcripts"] = "all transcripts with selected participants"
            };
        }
        return params;
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
        return value.replaceAll(/\\/g,"\\\\").replaceAll(/'/g,"\\'");
    }
}

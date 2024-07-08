import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router, Params } from '@angular/router';

import { Response, Layer, User } from 'labbcat-common';
import { MessageService, LabbcatService } from 'labbcat-common';

@Component({
  selector: 'app-participants',
  templateUrl: './participants.component.html',
  styleUrls: ['./participants.component.css']
})
export class ParticipantsComponent implements OnInit {

    schema: any;
    filterLayers: Layer[];
    participantAttributes: Layer[];
    user: User;
    baseUrl: string;
    matchCount: -1;
    pageLength = 20;
    pageCount = 0;
    p = 1; // current page number
    pageLinks: string[];
    participantIds: string[];
    attributeValues = {};
    selectedIds: string[];
    filterValues = {};
    query = ""; // AGQL query string for matching participants
    queryDescription = ""; // Human-readable version of the query
    participantQuery = ""; // AGQL query string for pre-matching participants
    participantDescription = ""; // Human readable description of participant query
    // track how many queries we're up to, to avoid old long queries updating the UI when
    // new short queries already have.
    querySerial = 0;
    nextPage: string;
    
    constructor(
        private labbcatService: LabbcatService,
        private messageService: MessageService,
        private route: ActivatedRoute,
        private router: Router
    ) { }
    
    ngOnInit(): void {
        this.filterLayers = [];
        this.selectedIds = [];
        this.readUserInfo();
        this.readBaseUrl();
        this.readSchema().then(()=> {
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
                resolve();
            });
        });
    }
    initializeFilters(): Promise<void> {
        return new Promise((resolve, reject) => {
            this.participantAttributes = [];
            this.filterLayers = [];
            // allow filtering by participant ID, corpus, and episode
            this.filterLayers.push(this.schema.layers[this.schema.participantLayerId]);
            this.filterValues[this.schema.participantLayerId] = [];
            this.filterLayers.push(this.schema.layers[this.schema.corpusLayerId]);
            this.filterValues[this.schema.corpusLayerId] = [];
            this.filterLayers.push(this.schema.layers[this.schema.episodeLayerId]);
            this.filterValues[this.schema.episodeLayerId] = [];
            // and transcript count - we use a dummy layer to fool the layer-filter
            this.schema.layers["--transcript-count"] = {
                id: "--transcript-count", description: "Transcript count", // TODO i18n
                parentId: this.schema.participantLayerId,                    
                alignment: 0,
                peers: false, peersOverlap: false, parentIncludes: true, saturated: true,
                type: "number", subtype: "integer"
            }
            this.filterLayers.push(this.schema.layers["--transcript-count"]);
            this.filterValues["--transcript-count"] = [];
            // and by selected participant attributes
            for (let layerId in this.schema.layers) {
                const layer = this.schema.layers[layerId] as Layer;
                if (layer.parentId == this.schema.participantLayerId
                    && layer.alignment == 0) {
                    this.participantAttributes.push(layer);
                    if (this.schema.layers[layerId].searchable == 1) {
                        this.filterValues[layer.id] = [];
                        this.filterLayers.push(layer);
                    }
                }
            }
            resolve();
        });
    }
    
    // we try to remember search parameters during the session, but there are some exceptions:
    passthroughPatterns = [
        /[?&](p)=([^&]*)/,
        /[?&](to)=([^&]*)/,
        /[?&](transcript_expression)=([^&]*)/,
        /[?&](transcripts)=([^&]*)/,
        /[?&](participant_expression)=([^&]*)/,
        /[?&](participants)=([^&]*)/
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
            if (queryString && queryString != "?") { // there is a query string
                // nothing further to do
                resolve();
            } else {
                // use the last query string for this session
                queryString = sessionStorage.getItem("lastQueryParticipants");
                if (queryString) { // they've previously made a query
                    // use that one
                    const queryParams: Params = {};
                    for (let param of `${queryString}${passthroughParameters}`.split("&")) {
                        const parts = param.split("=");
                        queryParams[parts[0]] = decodeURIComponent(parts[1]);
                    }
                    this.router.navigate([], {
                        relativeTo: this.route,
                        replaceUrl: true,
                        queryParams
                    });        
                    resolve();
                } else { // they haven't previously made a query
                    // load the default from system settings
                    this.labbcatService.labbcat.getSystemAttribute(
                        "defaultParticipantFilter", (attribute, errors, messages) => {
                            if (attribute.value) {
                                queryString = attribute.value;
                                const queryParams: Params = {};
                                for (let param of `${queryString}${passthroughParameters}`.split("&")) {
                                    const parts = param.split("=");
                                    queryParams[parts[0]] = decodeURIComponent(parts[1]);
                                }
                                this.router.navigate([], {
                                    relativeTo: this.route,
                                    replaceUrl: true,
                                    queryParams
                                });        
                            }
                            resolve();
                        });
                }
            }
        });        
    }

    /** load config from query parameters */
    parseQueryParameters(): void {
        this.route.queryParams.subscribe((params) => {
            // remember the query for next time
            // remember the query for next time
            let queryString = window.location.search;
            for (let pattern of this.passthroughPatterns) { // but not the passthrough parameters
                queryString = queryString.replace(pattern, "");
            }
            // strip ay leading/trailing parameter delimiters
            queryString = queryString.replace(/^[?&]/,"").replace(/[?&]$/,"");
            // save the query in session storage
            sessionStorage.setItem("lastQueryParticipants", queryString); 
            
            // page number
            this.p = parseInt(params["p"]) || 1;
            if (this.p < 1) this.p = 1;
            // set any layer parameter values to their corresponding filters
            for (let layerId in params) {
                if (params[layerId]) { // there's a parameter for this filter layer
                    this.filterValues[layerId] = params[layerId].split(",");
                }
            }
            if (params["to"]) {
                this.nextPage = params["to"];
                }
            if (params["participant_expression"]) {
                this.participantQuery = params["participant_expression"];
                if (params["participants"]) {
                    this.participantDescription = params["participants"];
                } else {
                    this.participantDescription = "Selected participants";
                }
            }
            this.listParticipants();
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
    
    filterChange(layer: Layer, values: string[]): void {
        this.filterValues[layer.id] = values;
        this.deferredListParticipants();
    }

    listParticipantsTimer = -1;
    /** list participants after a short delay to give the user time to keep typing or
        change another filter */
    deferredListParticipants(): void {
        if (this.listParticipantsTimer >= 0) {
            window.clearTimeout(this.listParticipantsTimer);
        }
        this.listParticipantsTimer = window.setTimeout(()=>{
            this.p = 1;
            this.listParticipants();
            this.listParticipantsTimer = -1;
        }, 2000);
    }

    /** the user can hit enter to skip the deferral */
    enterKeyPressed(): void {
        if (this.listParticipantsTimer >= 0) {
            window.clearTimeout(this.listParticipantsTimer);
            this.listParticipantsTimer = -1;
        }
        this.listParticipants();
    }
    
    loadingList = false;
    /** List participants that match the filters */
    listParticipants(): void {
        this.query = this.participantQuery; // if any
        this.queryDescription = this.participantDescription;
        for (let layer of this.filterLayers) {

            if (layer.id == this.schema.participantLayerId
                && this.filterValues[layer.id][0]) {
                // participant layer
                if (this.query) {
                    this.query += " && ";
                    this.queryDescription += ", ";
                }
                
                this.query += "/"+this.esc(this.filterValues[this.schema.participantLayerId][0])
                    +"/.test(id)";
                this.queryDescription += "ID matches "
                    +this.filterValues[this.schema.participantLayerId][0];
                
            } else if (layer.id == "--transcript-count"
                && this.filterValues[layer.id].length > 0) {
                
                // from?
                if (this.filterValues[layer.id][0]) {
                    if (this.query) this.query += " && ";
                    const value = (layer.subtype == "integer"?
                        parseInt:parseFloat)(this.filterValues[layer.id][0])
                    this.query += "all('transcript').length >= "+ value;
                }
                
                // to?
                if (this.filterValues[layer.id][1]) {
                    if (this.query) this.query += " && ";
                    
                    const value = (layer.subtype == "integer"?
                        parseInt:parseFloat)(this.filterValues[layer.id][1])
                    this.query += "all('transcript').length <= "+ value;
                }
                
                if (this.filterValues[layer.id][0] && this.filterValues[layer.id][1]) {
                    if (this.queryDescription) this.queryDescription += ", ";
                    this.queryDescription += 
                        "Transcript count " + this.filterValues[layer.id][0] // TODO i18n
                        + "-" + this.filterValues[layer.id][1];
                } else if (this.filterValues[layer.id][0]) {
                    if (this.queryDescription) this.queryDescription += ", ";
                    this.queryDescription +=
                        "Transcript count ≥ " + this.filterValues[layer.id][0]; // TODO i18n
                } else if (this.filterValues[layer.id][1]) {
                    if (this.queryDescription) this.queryDescription += ", ";
                    this.queryDescription += 
                        "Transcript count ≤ " + this.filterValues[layer.id][1]; // TODO i18n
                }
                
            } else if (layer.validLabels && Object.keys(layer.validLabels).length > 0
                && this.filterValues[layer.id].length > 0) {
                // select from possible values
                if (this.query) {
                    this.query += " && ";
                    this.queryDescription += ", ";
                }

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
                            + " in " + this.filterValues[layer.id].join(",");
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
                        + "-" + this.filterValues[layer.id][1];
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
                    if (this.query) this.query += " && ";
                    const value = this.filterValues[layer.id][0];
                    this.query += "first('"+this.esc(layer.id)+"').label"
                        +" >= '"+this.esc(value)+"'";
                    this.queryDescription += layer.description
                }
                
                // to?
                if (this.filterValues[layer.id][1]) {
                    if (this.query) this.query += " && ";
                    
                    const value = this.filterValues[layer.id][1];
                    this.query += "first('"+this.esc(layer.id)+"').label"
                        +" <= '"+value+" 23:59:59'";
                    this.queryDescription += layer.description+" ≤ " + value
                }
                
                if (this.filterValues[layer.id][0] && this.filterValues[layer.id][1]) {
                    if (this.queryDescription) this.queryDescription += ", ";
                    this.queryDescription += layer.description
                        +" " + this.filterValues[layer.id][0]
                        + "-" + this.filterValues[layer.id][1];
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
        for (let layer of this.filterLayers) { // for each filter layer
            if (this.filterValues[layer.id].length > 0) { // there's at least one value
                // add it to the query parameters
                queryParams[layer.id] = this.filterValues[layer.id].join(",");
            }
        } // next filter layer
        this.router.navigate([], {
            relativeTo: this.route,
            queryParams
        });
        
        this.loadingList = true;
        const thisQuery = ++this.querySerial;
        // count matches
        this.labbcatService.labbcat.countMatchingParticipantIds(
            this.query, (matchCount, errors, messages) => {
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
                this.labbcatService.labbcat.getMatchingParticipantIds(
                    this.query, this.pageLength, this.p - 1 /* zero-based page numbers */,
                    (participantIds, errors, messages) => {
                        if (thisQuery != this.querySerial) return; // new query already sent
                        if (errors) errors.forEach(m => this.messageService.error(m));
                        if (messages) messages.forEach(m => this.messageService.info(m));
                        
                        this.loadingList = false;
                        // selected IDs first ...
                        this.participantIds = this.selectedIds
                        // ... then listed IDs ...
                            .concat(participantIds
                                    // ... that aren't already selected
                            .filter(id => !this.selectedIds.includes(id)));
                        
                        // attribute values
                        for (let id of this.participantIds) {
                            // if we don't already have their attributes
                            if (!this.attributeValues[id]) {
                                this.attributeValues[id] = {};
                                // get the attributes
                                this.getAttributeValues(id);
                            }
                        } // next participant

                        // create page links
                        this.pageLinks = [];
                        for (let pg = 1; pg <= this.pageCount; pg++) {
                            this.pageLinks.push(""+pg);
                            if (this.pageCount < 1000) { // not too many pages, so get a hint
                                const hintIndex = pg - 1;
                                // the hint for the page is the first ID on that page
	                        this.labbcatService.labbcat.getMatchingParticipantIds(
                                    this.query, 1, this.pageLength * (pg-1),
                                    (pgIds, errors, messages) => {
                                        this.pageLinks[hintIndex] = pgIds[0];
	                            });
                            } // small number of pages
                        } // next page link

                        // revert to 20 per page for next time (in case we listed all this time)
                        this.pageLength = 20;
                    });
            });
    }

    getAttributeValues(id: string): void {
        this.labbcatService.labbcat.getParticipant(
            id, this.filterLayers.map(layer => layer.id),
            (participant, errors, messages) => {
                if (errors) errors.forEach(m => this.messageService.error(m));
                if (messages) messages.forEach(m => this.messageService.info(m));
                this.attributeValues[id] = participant;
                
                // get the number of transcripts the participant is in
                this.labbcatService.labbcat.countMatchingTranscriptIds(
                    "labels('"+this.esc(this.schema.participantLayerId)+"')"
                    +".includes('"+this.esc(id)+"')",
                    (count, errors, messages) => {
                        if (this.attributeValues[id].annotations) {
                            this.attributeValues[id].annotations["--transcript-count"] = [{
                                label : count
                            }];
                        }
                    });
            });
    }

    goToPage(p: number): void {
        this.p = p;
        this.listParticipants();
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
        this.listParticipants();
    }

    /** Button action */
    newParticipant(): void {
        var name = prompt(
            "Please enter the new participant's name\nor leave this blank to generate a name automatically", ""); // TODO i18n
        if (name != null) {
            this.labbcatService.labbcat.saveParticipant(
                name, name, {}, (nothing, errors, messages) => {
                    if (errors) errors.forEach(m => this.messageService.error(m));
                    if (messages) messages.forEach(m => this.messageService.info(m));
                    if (!errors) {
                        window.location.href = `${this.baseUrl}edit/participant?id=${name}`;
                    }
                });
        }
    }
    
    /** Button action */
    clearFilters() : void {
        this.initializeFilters().then(()=>{
            this.listParticipants();
        });
    }

    deleting = false;
    /** Button action */
    deleteParticipants(): void {
        if (confirm("Are you sure you want to delete selected participants?")) { // TODO i18n
            const deletions = [];
            // delete each selected participant
            for (let id of this.selectedIds) {
                const participantId = id;
                const component = this;
                deletions.push(new Promise<void>((accept, reject) => {
                    this.labbcatService.labbcat.deleteParticipant(
                        participantId, (nothing, errors, messages) => {
                            if (errors) errors.forEach(m => this.messageService.error(m));
                            if (messages) messages.forEach(m => this.messageService.info(m));
                            if (!errors) {
                                // remove the participant from the model
                                component.participantIds = component.participantIds.filter(
                                    p => p != participantId);
                            }
                            // either way, deselect it
                            component.selectedIds = component.selectedIds.filter(
                                p => p != participantId);
                            accept();
                        });
                }));
            } // next selected participant
            
            // once all the requests have finished
            Promise.all(deletions).then(ids => {
                // reload the page to fill up removed slots
                this.listParticipants();
            });
        } // are you sure?
    }

    /** Button action */
    mergeParticipants(): void {
        window.location.href = this.baseUrl
            + "edit/participants/merge?"
            + this.selectedIds.map(id=> {
                // needs speaker_number, which we retrieve from the participant.id
                return "speaker_number="+this.attributeValues[id].id.replace(/m_-2_/,"")
            }).join("&");
    }

    /** Button action */
    exportAttributes(): void {
        window.location.href = this.baseUrl
            + "participantsExport?"
            + this.selectedParticipantsQueryString("participantId");
    }

    /** Button action */
    allUtterances(): void {
        window.location.href = this.baseUrl
            + "allUtterances?"
            + this.selectedParticipantsQueryString("id");
    }
    
    /** Button action */
    transcripts(): void {
        let participantQuery = this.query
        // if we're matching participant ID, it's "id" here
        // but needs to be "first('participant').label" on the transcripts page 
            .replace(/\.test\(id\)/, ".test(first('participant').label)"); // TODO .test(labels('participant'))
        let participantDescription = this.queryDescription;
        if (this.selectedIds.length > 0) {
            // query of the form [...].includes(first('participant').label)
            const ids = this.selectedIds.map(id=>"'"+this.esc(id)+"'").join(",");
            participantQuery = `[${ids}].includesAny(labels('participant'))`;
            if (this.selectedIds.length == 1) {
                participantDescription = this.selectedIds[0];
            } else if (this.selectedIds.length <= 5) {
                participantDescription = this.selectedIds.join(", ");
            } else {
                participantDescription = ""+this.selectedIds.length + " selected participants"; // TODO i18n
            }
        }
        this.router.navigate(["transcripts"], { queryParams: {
            participant_expression: participantQuery,
            participants: participantDescription
        } });
    }
    
    /** Button action */
    layeredSearch(): void {
        this.router.navigate(["search"], {
            queryParams: this.selectedParticipantsQueryParameters("participant_id")
        });
    }

    /** Button action */
    setDefaultFilter(): void {
        if (confirm("Are you sure you want this to be the default filter for everyone?")) { // TODO i18n
            this.labbcatService.labbcat.updateSystemAttribute(
                "defaultParticipantFilter",
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

    /** Query string for selected participants */
    selectedParticipantsQueryString(participantIdParameter: string): string {
        let participantDescription = this.queryDescription;
        if (this.selectedIds.length > 0) {
            if (this.selectedIds.length == 1) {
                participantDescription = this.selectedIds[0];
            } else if (this.selectedIds.length <= 5) {
                participantDescription = this.selectedIds.join(", ");
            } else {
                participantDescription = ""+this.selectedIds.length + " selected participants"; // TODO i18n
            }
            return "participant_expression="+encodeURIComponent("["
                + this.selectedIds.map(id=>"'"+id.replace(/'/,"\\'")+"'").join(",")
                + "].includes(id)")
                + "&participants="+encodeURIComponent(participantDescription);
        } else if (this.query) {
            return "participant_expression="+encodeURIComponent(this.query)
                + "&participants="+encodeURIComponent(participantDescription);
        }
        return "";
    }
    /** Query parameters for selected participants */
    selectedParticipantsQueryParameters(participantIdParameter: string): Params {
        let participantDescription = this.queryDescription;
        if (this.selectedIds.length > 0) {
            if (this.selectedIds.length == 1) {
                participantDescription = this.selectedIds[0];
            } else if (this.selectedIds.length <= 5) {
                participantDescription = this.selectedIds.join(", ");
            } else {
                participantDescription = ""+this.selectedIds.length + " selected participants"; // TODO i18n
            }
            return {
                participant_expression: "["
                    + this.selectedIds.map(id=>"'"+id.replace(/'/,"\\'")+"'").join(",")
                    + "].includes(id)",
                participants: participantDescription
            };
        } else if (this.query) {
            return {
                participant_expression: this.query,
                participants: participantDescription
            };
        }
        return {};
    }

    /** Query to append to href for links to other pages */
    queryString(): string {
        let q = "";
        if (this.filterValues[this.schema.participantLayerId][0]) {
            q += "&"+this.schema.participantLayerId
                +"="+this.filterValues[this.schema.participantLayerId][0];
        }
        return q;
    }

    /** Add escapes for query string values */
    esc(value: string): string {
        return value.replace(/\\/,"\\\\").replace(/'/,"\\'");
    }
}

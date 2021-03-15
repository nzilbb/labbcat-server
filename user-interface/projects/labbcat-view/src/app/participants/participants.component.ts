import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';

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
    pageLength = 20;
    pageCount = 0;
    p = 1; // current page number
    pageLinks: string[];
    participantIds: string[];
    attributeValues = {};
    selectedIds: string[];
    filterValues = {};
    query = ""; // AGQL query string for matching participants
    
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
        this.readSchema().then(()=> {
            this.route.queryParams.subscribe((params) => {
                this.p = parseInt(params["p"]) || 1;
                if (this.p < 1) this.p = 1;
                if (params["participant"]) {
                    this.filterValues["participant"] = [params["participant"]];
                }
                this.listParticipants();
            });
        });
    }

    readSchema(): Promise<void> {
        return new Promise((resolve, reject) => {
            this.labbcatService.labbcat.getSchema((schema, errors, messages) => {
                this.schema = schema;
                this.participantAttributes = [];
                this.filterLayers = [];
                // allow filtering by participant ID, corpus, and episode
                this.filterLayers.push(schema.layers[schema.participantLayerId]);
                this.filterValues[schema.participantLayerId] = [];
                this.filterLayers.push(schema.layers[schema.corpusLayerId]);
                this.filterValues[schema.corpusLayerId] = [];
                this.filterLayers.push(schema.layers[schema.episodeLayerId]);
                this.filterValues[schema.episodeLayerId] = [];
                // and transcript count - we use a dummy layer to fool the layer-filter
                schema.layers[" transcript-count"] = {
                    id: " transcript-count", description: "Transcript count", // TODO i18n
                    parentId: schema.participantLayerId,                    
                    alignment: 0,
                    peers: false, peersOverlap: false, parentIncludes: true, saturated: true,
                    type: "number", subtype: "integer"
                }
                this.filterLayers.push(schema.layers[" transcript-count"]);
                this.filterValues[" transcript-count"] = [];
                // and by selected participant attributes
                for (let layerId in schema.layers) {
                    const layer = schema.layers[layerId] as Layer;
                    if (layer.parentId == schema.participantLayerId
                        && layer.alignment == 0) {
                        this.participantAttributes.push(layer);
                        if (schema.layers[layerId].searchable == 1) {
                            this.filterValues[layer.id] = [];
                            this.filterLayers.push(layer);
                        }
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
            this.listParticipants();
            this.listParticipantsTimer = -1;
        }, 2000);
    }

    loadingList = false;
    /** List participants that match the filters */
    listParticipants(): void {
        this.query = "";
        for (let layer of this.filterLayers) {

            if (layer.id == this.schema.participantLayerId
                && this.filterValues[layer.id][0]) {
                // participant layer
                if (this.query) this.query += " && ";
                
                this.query += "/"+this.esc(this.filterValues["participant"][0])+"/.test(id)";
                
            } else if (layer.id == " transcript-count"
                && this.filterValues[layer.id].length > 0) {
                // participant layer
                if (this.query) this.query += " && ";
                
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
            // TODO date/datetime
        } // next filter layer
        this.loadingList = true;
        // count matches
        this.labbcatService.labbcat.countMatchingParticipantIds(
            this.query, (matchCount, errors, messages) => {
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
                        if (errors) errors.forEach(m => this.messageService.error(m));
                        if (messages) messages.forEach(m => this.messageService.info(m));
                        
                        this.loadingList = false;
                        this.participantIds = participantIds;
                        this.selectedIds = [];

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
                    "labels('"+this.schema.participantLayerId+"').includes('"+id.replace(/'/,"\\'")+"')",
                    (count, errors, messages) => {
                        if (errors) errors.forEach(m => this.messageService.error(m));
                        if (messages) messages.forEach(m => this.messageService.info(m));
                        this.attributeValues[id].annotations[" transcript-count"] = [{
                            label : count
                        }];
                    });
            });
    }

    goToPage(p: number): boolean {
        this.p = p;
        this.listParticipants();
        return false;
    }

    /** Button action */
    newParticipant(): void {
        var name = prompt(
            "Please enter the new participant's name\nor leave this blank to generate a name automatically", ""); // TODO i18n
        if (name != null) { 
            window.location.href = this.baseUrl + "edit/participants/new?newSpeakerName="+name;
        }
    }
    
    deleting = false;
    /** Button action */
    deleteParticipants(): void {
        alert("TODO delete participant");
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
            + this.selectedIds.map(id=>"participantId="+id).join("&");
    }

    /** Button action */
    allUtterances(): void {
        window.location.href = this.baseUrl
            + "allUtterances?"
            + this.selectedIds.map(id=>"id="+id).join("&");
    }
    
    /** Button action */
    layeredSearch(): void {
        window.location.href = this.baseUrl
            + "search?"
            + this.selectedIds.map(id=>"participant_id="+id).join("&");
    }

    /** Query to append to href for links to other pages */
    queryString(): string {
        let q = "";
        if (this.filterValues["participant"][0]) {
            q += "&participant="+this.filterValues["participant"][0];
        }
        return q;
    }

    /** Add escapes for query string values */
    esc(value: string): string {
        return value.replace(/'/,"\\'");
    }
}

import { Component, OnInit, Inject } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';

import { Layer } from 'labbcat-common';
import { User } from 'labbcat-common';
import { MessageService, LabbcatService } from 'labbcat-common';

import { Matrix } from '../matrix';
import { MatrixLayerMatch } from '../matrix-layer-match';

@Component({
  selector: 'app-search',
  templateUrl: './search.component.html',
  styleUrls: ['./search.component.css']
})
export class SearchComponent implements OnInit {
    
    user: User;
    schema: any;
    imagesLocation : string;
    tabLabels: string[];
    currentTab: string;
    tabs: object;
    matrix: Matrix;
    participantDescription: string;
    participantIds: string[];
    participantsFile: File;
    transcriptDescription: string;
    transcriptIds: string[];
    mainParticipantOnly: boolean;
    onlyAligned: boolean;
    firstMatchOnly: boolean;
    excludeSimultaneousSpeech: boolean;
    overlapThreshold: number;
    suppressResults: boolean;
    threadId:string;
    
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
        this.matrix = {
            columns: [],
            participantQuery: "",
            transcriptQuery: ""
        }
        this.participantIds = [];
        this.transcriptIds = [];
        this.readUserInfo();
        this.setupTabs();
        this.labbcatService.labbcat.getSchema((schema, errors, messages) => {
            this.schema = schema;
            
            // interpret URL parameters
            this.route.queryParams.subscribe((params) => {
                if (params["searchJson"]) {
                    this.matrix = this.standardizeMatrix(
                        JSON.parse(params["searchJson"]) as Matrix);
                } else {
                    if (params["search"]) {
                        let search = params["search"];
                        if (search.indexOf(":") < 0) {
                            search = `orthography:${search}`;
                        }
                        const parts = search.split(":");
                        const layers = {};
                        layers[parts[0]] = [{
                            id: parts[0],
                            pattern: parts[1]
                        }];
                        console.log("layers " + JSON.stringify(layers));
                        this.matrix = this.standardizeMatrix({
                            columns: [{
                                layers: layers
                            }]
                        } as Matrix);
                    }
                    
                    this.matrix.participantQuery = params["participant_expression"];
                    if (this.matrix.participantQuery) {
                        this.participantDescription = params["participants"]
                            || "Selected participants"; // TODO i18n
                    }
                    
                    this.matrix.transcriptQuery = params["transcript_expression"];
                    if (this.matrix.transcriptQuery) {
                        this.transcriptDescription = params["transcripts"]
                            || "Selected transcripts"; // TODO i18n
                    }
                }
                this.listParticipants(false);
                this.listTranscripts();
            });
        });
    }
    readUserInfo(): void {
        this.labbcatService.labbcat.getUserInfo((user, errors, messages) => {
            this.user = user as User;
        });
    }
    setupTabs(): void {
        this.tabs = {};
        this.tabs["Participants"] = { // TODO i18n
            label: "Participants", // TODO i18n
            description: "Narrow down the participants to search",
            icon: "filter.svg"
        }; // TODO i18n
        this.tabs["Transcripts"] = { // TODO i18n
            label: "Transcripts", // TODO i18n
            description: "Narrow down the transcripts to search",
            icon: "filter.svg"
        }; // TODO i18n
        this.tabs["Matrix"] = { // TODO i18n
            label: "Matrix", // TODO i18n
            description: "Specify layers and patterns for searching the corpus",
            icon: "layers.svg"
        };
        this.tabs["Options"] = { // TODO i18n
            label: "Options", // TODO i18n
            description: "Specify search options",
            icon: "cog.svg"
        };
        this.tabLabels = Object.keys(this.tabs);
    }

    /** Ensure fields are filled in correctly, the value may have been passed in */
    standardizeMatrix(matrix: Matrix): Matrix {
        if (!matrix.hasOwnProperty("participantQuery")) matrix.participantQuery = "";
        if (!matrix.hasOwnProperty("transcriptQuery")) matrix.transcriptQuery = "";
        if (!matrix.hasOwnProperty("columns")) matrix.columns = [];
        for (let column of matrix.columns) { // each column
            if (!column.hasOwnProperty("adj")) column.adj = 1;
            if (!column.hasOwnProperty("layers")) column.layers = {};
            for (let layerId in column.layers) { // each column layer
                const matches = column.layers[layerId] as MatrixLayerMatch[];
                for (let match of matches) {
                    if (!match.hasOwnProperty("id")) match.id = layerId;
                    if (!match.hasOwnProperty("not")) match.not = false;
                    if (!match.hasOwnProperty("anchorStart")) match.anchorStart = false;
                    if (!match.hasOwnProperty("anchorEnd")) match.anchorEnd = false;
                    if (!match.hasOwnProperty("target")) match.target = false;
                } // next match
            } // next column layer
        } // next column
        return matrix;
    }

    participantCount = 0;
    loadingParticipants = false;
    /** List participants that match the filters */
    listParticipants(fromFile: boolean): void {
        this.participantIds = [];
        if (this.matrix.participantQuery) {
            this.labbcatService.labbcat.countMatchingParticipantIds(
                this.matrix.participantQuery, (participantCount, errors, messages) => {
                    if (errors) errors.forEach(m => this.messageService.error(m));
                    if (messages) messages.forEach(m => this.messageService.info(m));
                    this.participantCount = participantCount;
                    if (this.participantCount) {
                        this.loadMoreParticipants();
                    } else if (fromFile) { // list loaded from file
                        this.messageService.error("No valid participants in file."); // TODO i18n
                    }

                });
        } // there is a participantExpression
    }

    loadMoreParticipants() : void {
        const pageLength = 10;
        this.loadingParticipants = true;
        this.labbcatService.labbcat.getMatchingParticipantIds(
            this.matrix.participantQuery, pageLength, this.participantIds.length / pageLength,
            (participantIds, errors, messages) => {
                if (errors) errors.forEach(m => this.messageService.error(m));
                if (messages) messages.forEach(m => this.messageService.info(m));
                this.participantIds = this.participantIds.concat(participantIds);
                this.loadingParticipants = false;
            });
    }

    transcriptCount = 0;
    loadingTranscripts = false;
    /** List transcripts that match the filters */
    listTranscripts(): void {
        this.transcriptIds = [];
        if (this.matrix.transcriptQuery) {
            this.labbcatService.labbcat.countMatchingTranscriptIds(
                this.transcriptQueryIncludingParticipantConditions(),
                (transcriptCount, errors, messages) => {
                    if (errors) errors.forEach(m => this.messageService.error(m));
                    if (messages) messages.forEach(m => this.messageService.info(m));
                    this.transcriptCount = transcriptCount;
                    if (this.transcriptCount) {
                        this.loadMoreTranscripts();
                    }
                });
        } // there is a transcriptExpression
    }

    loadMoreTranscripts() : void {
        const pageLength = 10;
        this.loadingTranscripts = true;
        this.labbcatService.labbcat.getMatchingTranscriptIds(
            this.transcriptQueryIncludingParticipantConditions(),
            pageLength, this.transcriptIds.length / pageLength,
            (transcriptIds, errors, messages) => {
                if (errors) errors.forEach(m => this.messageService.error(m));
                if (messages) messages.forEach(m => this.messageService.info(m));
                this.transcriptIds = this.transcriptIds.concat(transcriptIds);
                this.loadingTranscripts = false;
            });
    }

    search(): void {
        if (this.threadId) { // there was a previous search
            const lastThreadId = this.threadId;
            // is it still running?
            this.labbcatService.labbcat.taskStatus(
                lastThreadId, (task, errors, messages) => {
                    if (task && task.running) { // last search is still running
                        // cancel it
                        this.labbcatService.labbcat.cancelTask(
                            lastThreadId, (task, errors, messages) => {
                                if (errors) errors.forEach(m => this.messageService.error(m));
                                if (messages) messages.forEach(m => this.messageService.info(m));
                                // release its resources
                                this.labbcatService.labbcat.releaseTask(
                                    lastThreadId, (task, errors, messages) => {
                                    });
                            });
                    } // last search is still running
                });
        } // there was a previous search
        
        this.labbcatService.labbcat.search(
            this.matrix, null, null,
            this.mainParticipantOnly,
            this.onlyAligned?15:null,
            this.firstMatchOnly?1:null,
            this.excludeSimultaneousSpeech?this.overlapThreshold:null,
            (result, errors, messages) => {
                if (errors) errors.forEach(m => this.messageService.error(m));
                if (messages) messages.forEach(m => this.messageService.info(m));
                this.threadId = result.threadId;
        });
    }

    transcriptQueryIncludingParticipantConditions(): string {
        let queryExpression = this.matrix.transcriptQuery;
        if (this.matrix.participantQuery) {
            if (queryExpression) queryExpression += " && ";
            queryExpression += this.participantQueryForTranscripts();
        }
        return queryExpression;
    }

    /**
     * participantQuery, but with expressions like:
     * ['AP511_MikeThorpe'].includes(id)
     * replaced with:
     * labels("participant").includes(["AP511_MikeThorpe"])
     */
    participantQueryForTranscripts(): string {
        if (!this.matrix || !this.matrix.participantQuery) return "";
        return this.matrix.participantQuery
            .replace(/(\[.*\])\.includes\(id\)/,
                    "labels('participant').includesAny($1)");
    }
    
    /** Called when a CSV file is selected; parses the file to determine CSV fields. */
    selectFile(files: File[]): void {
        console.log("selectFile " + files.length);
        if (files.length == 0) return;
        this.participantsFile = files[0]
        if (!this.participantsFile.name.endsWith(".csv")
            && !this.participantsFile.name.endsWith(".tsv")
            && !this.participantsFile.name.endsWith(".txt")) {
            this.messageService.error("You must select a text file (.txt, .csv, or .tsv)"); // TODO i18n
            this.participantsFile = null;
            return;
        }
        
        const reader = new FileReader();
        const component = this;
        reader.onload = () => {
            console.log("onload " + files.length);
            const csvData = reader.result;  
            let lines = (<string>csvData).split(/\r\n|\n/);
            console.log("lines " + lines.length);
            // remove blank lines
            lines = lines.filter(l=>l.length>0);
            // if the file has fields/columns, use the first field/column
            if (/.*,.*/.test(lines[0])) { // CSV
                lines = lines.map(l=>l.split(",")[0]);
            } else if (/.*;.*/.test(lines[0])) { // Non-English CSV
                lines = lines.map(l=>l.split(";")[0]);
            } else if (/.*\t.*/.test(lines[0])) { // TSV
                lines = lines.map(l=>l.split("\t")[0]);
            }
            console.log("non-blank lines " + lines.length);
            if (lines.length == 0) {
                component.messageService.error(
                    "File is empty: " + component.participantsFile.name); // TODO i18n
            } else {
                let idList = lines.map(
                    l=>"'"+l.replace(/\\/g, "\\\\").replace(/'/g, "\\'")+"'");
                if (idList.length == 0) {
                    component.messageService.error(
                        "File is empty: " + component.participantsFile.name); // TODO i18n
                } else {
                    component.matrix.participantQuery = "["+idList.join(",")+"].includes(id)";
                    console.log("component.matrix.participantQuery " + component.matrix.participantQuery);
                    this.listParticipants(true);
                    this.listTranscripts();
                }                
            }
        };
        reader.onerror = function () {  
            component.messageService.error("Error reading " + component.participantsFile.name);
        };
        reader.readAsText(this.participantsFile);
        
    }
}

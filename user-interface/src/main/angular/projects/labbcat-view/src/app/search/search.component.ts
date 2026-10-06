import { Component, OnInit, Inject, ViewChild, ElementRef, SecurityContext } from '@angular/core';
import { ActivatedRoute, Router, Params } from '@angular/router';
import { DomSanitizer } from '@angular/platform-browser';

import { Layer } from 'labbcat-common';
import { User, Task } from 'labbcat-common';
import { MessageService, LabbcatService, VersionInfo } from 'labbcat-common';

import { Matrix } from '../matrix';
import { MatrixColumn } from '../matrix-column';
import { MatrixLayerMatch } from '../matrix-layer-match';
import { SearchHistoryItem } from '../search-history-item';

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
    totalParticipants: number;
    transcriptDescription: string;
    transcriptIds: string[];
    transcriptsFile: File;
    totalTranscripts: number;
    historyFile: File;
    mainParticipantOnly: boolean;
    onlyAligned: boolean;
    firstMatchOnly: boolean;
    excludeSimultaneousSpeech: boolean;
    overlapThreshold = 5;
    suppressResults: boolean;
    threadId:string;
    searchRunning: boolean;
    history: SearchHistoryItem[];
    exportUrl: string;
    exportName: string;
    @ViewChild('exportAnchor', {static: false}) exportAnchor: ElementRef;
    labbcatTitle: string;
    versions: VersionInfo;
    
    constructor(
        private labbcatService: LabbcatService,
        private messageService: MessageService,
        private route: ActivatedRoute,
        private router: Router,
        private sanitizer: DomSanitizer,
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
        this.history = JSON.parse(sessionStorage.getItem("searchHistory")) ?? [];
        this.history = this.history.filter(x => x.task);
        this.readUserInfo();
        this.setupTabs();
        this.readTitle();
        this.labbcatService.labbcat.getParticipantIds((result, errors, messages) => {
            if (errors) errors.forEach(m => this.messageService.error(m));
            if (messages) messages.forEach(m => this.messageService.info(m));
            this.totalParticipants = result.length;
        });
        this.labbcatService.labbcat.getTranscriptIds((result, errors, messages) => {
            if (errors) errors.forEach(m => this.messageService.error(m));
            if (messages) messages.forEach(m => this.messageService.info(m));
            this.totalTranscripts = result.length;
        });
        this.readVersions().then(() => {
            // TODO indenting
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
                }
                if (!this.matrix.participantQuery) { // don't override participantQuery specified in searchJson, if any
                    this.matrix.participantQuery = params["participant_expression"];
                    if (this.matrix.participantQuery) {
                        this.participantDescription = params["participants"];
                        this.currentTab = "Participants";
                    }
                }
                if (!this.matrix.transcriptQuery) { // don't override transcriptQuery specified in searchJson, if any
                    this.matrix.transcriptQuery = params["transcript_expression"];
                    if (this.matrix.transcriptQuery) {
                        this.transcriptDescription = params["transcripts"];
                        this.currentTab = "Transcripts";
                    }
                    if (params["current_tab"]) {
                        this.currentTab = params["current_tab"];
                    }
                }
                if (params["mainParticipantOnly"] === "true") this.mainParticipantOnly = params["mainParticipantOnly"];
                if (params["onlyAligned"] === "true") this.onlyAligned = params["onlyAligned"];
                if (params["firstMatchOnly"] === "true") this.firstMatchOnly = params["firstMatchOnly"];
                if (params["excludeSimultaneousSpeech"] === "true") this.excludeSimultaneousSpeech = params["excludeSimultaneousSpeech"];
                if (!isNaN(parseFloat(params["overlapThreshold"]))) this.overlapThreshold = params["overlapThreshold"];
                if (params["suppressResults"] === "true") this.suppressResults = params["suppressResults"];
                this.listParticipants();
                this.listTranscripts();
            });
        });
            // end TODO indenting
        });
    }
    readTitle(): void {
        if (!this.labbcatTitle) {
            setTimeout(()=>{ // wait for the corpus title to come in
                if (!this.labbcatTitle) this.labbcatTitle = this.labbcatService.title;
                // then try again
                this.readTitle();
            }, 100);
        }
    }
    readVersions(): Promise<void> {
        return new Promise((resolve, reject) => {
            this.labbcatService.labbcat.versionInfo((versions, errors, messages) => {
                this.versions = versions;
                resolve();
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
        this.tabs["Participants"] = {
            label: "Participants", // TODO i18n
            description: "Narrow down the participants to search", // TODO i18n
            icon: "filter.svg"
        }; // TODO i18n
        this.tabs["Transcripts"] = {
            label: "Transcripts", // TODO i18n
            description: "Narrow down the transcripts to search", // TODO i18n
            icon: "filter.svg"
        }; // TODO i18n
        this.tabs["Options"] = {
            label: "Options", // TODO i18n
            description: "Configure options for matching and displaying search results", // TODO i18n
            icon: "cog.svg"
        };
        this.tabs["History"] = {
            label: "History", // TODO i18n
            description: "View history of searches on this browser tab", // TODO i18n
            icon: "history.svg"
        };
        this.tabLabels = Object.keys(this.tabs);
    }
    loadParameters(): Params {
        const searchJson = this.buildSearchJsonParam(this.matrix);
        let params = {};
        if (searchJson.length) params["searchJson"] = searchJson;
        if (this.mainParticipantOnly) params["mainParticipantOnly"] = true;
        if (this.onlyAligned) params["onlyAligned"] = true;
        if (this.firstMatchOnly) params["firstMatchOnly"] = true;
        if (this.excludeSimultaneousSpeech) params["excludeSimultaneousSpeech"] = true;
        if (this.overlapThreshold && this.overlapThreshold != 5) {
            params["overlapThreshold"] = this.overlapThreshold;
        }
        if (this.suppressResults) params["suppressResults"] = true;
        return params;
    }
    selectParticipants(): void {
        if (this.transcriptIds && this.transcriptIds.length) {
            if (!confirm("This will clear the transcript filter.\nAre you sure you want to select participants?")) { // TODO i18n
                return;
            }
        }
        let params = this.loadParameters();
        params["to"] = "search";
        if (this.matrix.participantQuery) {
            params["participant_expression"] = this.matrix.participantQuery;
        }
        if (this.participantDescription) {
            params["participants"] = this.participantDescription;
        }
        this.router.navigate(["participants"], { queryParams: params });
    }
    clearParticipantFilter(): void {
        this.participantDescription = "";
        this.participantIds = [];
        this.participantCount = 0;
        this.matrix.participantQuery = "";
        sessionStorage.removeItem("lastQueryParticipants");
        let params = {
            searchJson: null,
            participant_expression: null,
            participants: null,
            current_tab: 'Participants'
        };
        if (this.transcriptDescription == "all transcripts with selected participants") {
            this.transcriptDescription = "all transcripts";
            params["transcripts"] = "all transcripts";
        }
        this.router.navigate([], {
            queryParams: params,
            queryParamsHandling: 'merge'
        });
    }
    selectTranscripts(): void {
        let params = this.loadParameters();
        params["to"] = "search";
        params["participant_expression"] = this.participantQueryForTranscripts();
        params["participants"] = this.participantDescription;
        if (this.matrix.transcriptQuery) {
            params["transcript_expression"] = this.transcriptQueryIncludingParticipantConditions();
        }
        if (this.transcriptDescription) {
            params["transcripts"] = this.transcriptDescription;
        }
        this.router.navigate(["transcripts"], { queryParams: params });
    }
    clearTranscriptFilter(): void {
        this.transcriptDescription = "";
        this.transcriptIds = [];
        this.transcriptCount = 0;
        this.matrix.transcriptQuery = "";
        sessionStorage.removeItem("lastQueryTranscripts");
        this.router.navigate([], {
            queryParams: {
                searchJson: null,
                transcript_expression: null,
                transcripts: null,
                current_tab: 'Transcripts'
            },
            queryParamsHandling: 'merge'
        });
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
                    if (!match.hasOwnProperty("pattern")) match.pattern = "";
                    if (!match.hasOwnProperty("min")) match.min = null;
                    if (!match.hasOwnProperty("max")) match.max = null;
                } // next match
            } // next column layer
        } // next column
        return matrix;
    }
    /** Remove defaults that standardizeMatrix() restores, to minimize the size of requests */
    condenseMatrix(matrix: Matrix): Matrix {
        if (matrix.hasOwnProperty("participantQuery")
            && matrix.participantQuery !== undefined
            && !matrix.participantQuery.length) {
            delete matrix.participantQuery;
        }
        if (matrix.hasOwnProperty("transcriptQuery")
            && matrix.transcriptQuery !== undefined
            && !matrix.transcriptQuery.length) {
            delete matrix.transcriptQuery;
        }
        for (let column of matrix.columns) { // each column
            if (column.hasOwnProperty("adj") && column.adj == 1) {
                delete column.adj;
            }
            if (column.hasOwnProperty("layers") && !Object.keys(column.layers).length) {
                delete column.layers;
            }
            for (let layerId in column.layers) { // each column layer
                const matches = column.layers[layerId] as MatrixLayerMatch[];
                for (let match of matches) {
                    if (match.hasOwnProperty("id") && match.id == layerId) {
                        delete match.id;
                    }
                    if (match.hasOwnProperty("not") && !match.not) {
                        delete match.not;
                    }
                    if (match.hasOwnProperty("anchorStart") && !match.anchorStart) {
                        delete match.anchorStart;
                    }
                    if (match.hasOwnProperty("anchorEnd") && !match.anchorEnd) {
                        delete match.anchorEnd;
                    }
                    if (match.hasOwnProperty("target") && !match.target) {
                        delete match.target;
                    }
                    if (match.hasOwnProperty("pattern") && match.pattern == "") {
                        delete match.pattern;
                    }
                    if (match.hasOwnProperty("min") && match.min == null) {
                        delete match.min;
                    }
                    if (match.hasOwnProperty("max") && match.max == null) {
                        delete match.max;
                    }
                } // next match
            } // next column layer
        } // next column
        return matrix;
    }
    /** Build searchJson parameter (or return empty string if the default) */
    buildSearchJsonParam(matrix: Matrix): string {
        const searchColumns = JSON.stringify({ columns: this.condenseMatrix(matrix).columns });
        const defaultColumns = JSON.stringify({columns:[{layers:{orthography:[{pattern:"",min:null,max:null}]}}]});
        let searchJson = "";
        if (searchColumns != defaultColumns) { // search columns aren't the default
            searchJson = searchColumns;
        }
        return searchJson;
    }

    participantCount = 0;
    loadingParticipants = false;
    /** List participants that match the filters */
    listParticipants(): void {
        this.participantIds = [];
        if (this.matrix.participantQuery) {
            this.labbcatService.labbcat.countMatchingParticipantIds(
                this.matrix.participantQuery, (participantCount, errors, messages) => {
                    if (errors) errors.forEach(m => this.messageService.error(m));
                    if (messages) messages.forEach(m => this.messageService.info(m));
                    this.participantCount = participantCount;
                    if (this.participantCount) {
                        this.loadMoreParticipants();
                    } else if (this.participantsFile) { // list loaded from file
                        this.messageService.error("No valid participants in file."); // TODO i18n
                    }

                });
        } // there is a participantExpression
    }

    loadMoreParticipants() : void {
        const pageLength = 50;
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
                    } else if (this.transcriptsFile) { // list loaded from file
                        this.messageService.error("No valid transcripts in file."); // TODO i18n
                    }
                });
        } // there is a transcriptExpression
    }

    loadMoreTranscripts() : void {
        const pageLength = 50;
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
                this.searchRunning = true;
                this.historyItem();
        });
    }

    updateTask(threadId: string): Promise<SearchHistoryItem> {
        return new Promise((resolve, reject) => {
            let historyItem = this.history.filter(x => x.task && x.task.threadId == threadId)[0];
            if (!historyItem) {
                historyItem = {} as SearchHistoryItem;
            }
            if (historyItem.sourceFile) {
                resolve(historyItem);
                return;
            }
            this.labbcatService.labbcat.taskStatus(threadId, (task, errors, messages) => {
                if (errors) errors.forEach(m => this.messageService.error(m));
                if (messages) messages.forEach(m => this.messageService.info(m));
                historyItem.task = task;
                historyItem.cancelled = task.status.includes("cancelled");
                if (task.running || task.lastException || historyItem.cancelled) {
                    delete historyItem.task.size;
                }
                if (!task.running) {
                    this.searchRunning = false;
                }
                sessionStorage.setItem("searchHistory", JSON.stringify(this.history));
                resolve(historyItem);
            });
        });
    }

    historyItem(): void {
        this.updateTask(this.threadId).then(historyItem => {
            historyItem.metadata = {
                labbcat_title: this.labbcatTitle,
                labbcat_version: this.versions.System["LaBB-CAT"]
            };
            if (this.versions.Data && this.versions.Data["dataVersion"]) {
                historyItem.metadata.data_version = this.versions.Data["dataVersion"];
            }
            historyItem.matrix = structuredClone(this.matrix);
            // participantCount/transcriptCount logic:
            // - if both are unfiltered or trivially filtered (i.e. "all participants"),
            //   store the corpus total. (This doesn't cover "all transcripts with
            //   selected participants", which may be a trivial filter depending on
            //   the participant filter.)
            // - if only the other filter is applied, we don't know this filter's
            //   count, so store undefined
            // - if a nontrivial filter is applied, store the reported count
            historyItem.filters = {
                participantDescription: this.participantDescription,
                participantCount: !this.participantCount && !this.participantDescription ?
                                      [0, this.totalTranscripts].includes(this.transcriptCount) ?
                                          this.totalParticipants :
                                          undefined :
                                      this.participantCount,
                transcriptDescription: this.transcriptDescription,
                transcriptCount: !this.transcriptCount && !this.transcriptDescription ?
                                      [0, this.totalParticipants].includes(this.participantCount) ?
                                          this.totalTranscripts :
                                          undefined :
                                      this.transcriptCount
            };
            historyItem.matchOptions = {
                mainParticipantOnly: this.mainParticipantOnly,
                onlyAligned: this.onlyAligned,
                firstMatchOnly: this.firstMatchOnly,
                excludeSimultaneousSpeech: this.excludeSimultaneousSpeech,
                overlapThreshold: this.overlapThreshold
            };

            this.history.push(historyItem);
            console.log("this.history", this.history);
        });
    }

    /** Convenience functions for display */
    matrixColumnsEquals(columnsA: MatrixColumn[], columnsB: MatrixColumn[]): boolean {
        // different numbers of columns
        if (columnsA.length != columnsB.length) {
            return false;
        }

        // check each column
        for (let col in columnsA) {
            // different layers
            if (Object.keys(columnsA[col].layers).sort().join(' ') !=
                Object.keys(columnsB[col].layers).sort().join(' ')) {
                return false;
            }
            // different adjacency
            if (columnsA[col].adj != columnsB[col].adj) {
                return false;
            }

            // check each layer
            for (let l of Object.keys(columnsA[col].layers)) {
                // different numbers of word-internal columns
                if (columnsA[col].layers[l].length !=
                    columnsB[col].layers[l].length) {
                    return false;
                }

                // check each word-internal column
                for (let wcol in columnsA[col].layers[l]) {
                    const colA = columnsA[col].layers[l][wcol];
                    const colB = columnsB[col].layers[l][wcol];
                    // different layer parameters
                    if (colA.id != colB.id ||
                        colA.pattern != colB.pattern ||
                        colA.not != colB.not ||
                        colA.min != colB.min ||
                        colA.max != colB.max ||
                        colA.anchorStart != colB.anchorStart ||
                        colA.anchorEnd != colB.anchorEnd ||
                        colA.target != colB.target) {
                            return false;
                        }
                }
            }
        }

        // passed all checks
        return true;
    }
    sameMatrixAsPrevious(index: number): boolean {
        return index > 0 &&
            index <= this.history.length - 1 &&
            this.matrixColumnsEquals(this.history[index].matrix.columns, this.history[index - 1].matrix.columns);
    }
    sameFiltersAsPrevious(index: number): boolean {
        if (index == 0 || index > this.history.length - 1) {
            return false;
        }
        const curr = this.history[index].filters;
        const prev = this.history[index - 1].filters;
        if ((curr.participantDescription ?? "").replace("all participants", "") !=
                (prev.participantDescription ?? "").replace("all participants", "") ||
                curr.participantCount != prev.participantCount ||
                curr.transcriptCount != prev.transcriptCount) {
            return false;
        }
        // both items' transcripts are unfiltered or de facto unfiltered
        if (((curr.participantDescription == "all participants" &&
              curr.transcriptDescription == "all transcripts with selected participants") ||
             curr.transcriptDescription == "all transcripts" ||
             !curr.transcriptDescription) &&
             ((prev.participantDescription == "all participants" &&
              prev.transcriptDescription == "all transcripts with selected participants") ||
             prev.transcriptDescription == "all transcripts" ||
             !prev.transcriptDescription)) {
             return true;
         }
         // same transcript filters
         if (curr.transcriptDescription == prev.transcriptDescription) {
             return true;
         }
         return false;
    }
    sameOptionsAsPrevious(index: number): boolean {
        return index > 0 &&
            index <= this.history.length - 1 &&
            this.history[index].matchOptions.mainParticipantOnly == this.history[index - 1].matchOptions.mainParticipantOnly &&
            this.history[index].matchOptions.onlyAligned == this.history[index - 1].matchOptions.onlyAligned &&
            this.history[index].matchOptions.firstMatchOnly == this.history[index - 1].matchOptions.firstMatchOnly &&
            this.history[index].matchOptions.excludeSimultaneousSpeech == this.history[index - 1].matchOptions.excludeSimultaneousSpeech &&
            this.history[index].matchOptions.overlapThreshold == this.history[index - 1].matchOptions.overlapThreshold;
    }
    anyImported(history: SearchHistoryItem[]): boolean {
        return history.length && history.filter(x => x.sourceFile).length > 0;
    }
    hasFilters(historyItem: SearchHistoryItem): boolean {
        return historyItem.matrix.participantQuery !== undefined ||
            historyItem.matrix.transcriptQuery !== undefined;
    }
    anyFilters(history: SearchHistoryItem[]): boolean {
        return history.length && history.map(x => this.hasFilters(x)).reduce((x, y) => x || y);
    }

    repeat(historyItem: SearchHistoryItem): void {
        let params = { current_tab: "History" };
        this.matrix = structuredClone(historyItem.matrix);
        if (historyItem.matrix.participantQuery) {
            this.participantDescription = historyItem.filters.participantDescription;
            params["participant_expression"] = historyItem.matrix.participantQuery;
            params["participants"] = historyItem.filters.participantDescription;
            sessionStorage.removeItem("lastQueryParticipants"); // just in case
        } else {
            this.participantDescription = "";
            this.participantIds = [];
            this.participantCount = 0;
            params["participant_expression"] = null;
            params["participants"] = null;
        }
        if (historyItem.matrix.transcriptQuery) {
            this.transcriptDescription = historyItem.filters.transcriptDescription;
            params["transcript_expression"] = historyItem.matrix.transcriptQuery;
            params["transcripts"] = historyItem.filters.transcriptDescription;
            sessionStorage.removeItem("lastQueryTranscripts"); // just in case
        } else {
            this.transcriptDescription = "";
            this.transcriptIds = [];
            this.transcriptCount = 0;
            params["transcript_expression"] = null;
            params["transcripts"] = null;
        }
        this.mainParticipantOnly = historyItem.matchOptions.mainParticipantOnly;
        this.onlyAligned = historyItem.matchOptions.onlyAligned;
        this.firstMatchOnly = historyItem.matchOptions.firstMatchOnly;
        this.excludeSimultaneousSpeech = historyItem.matchOptions.excludeSimultaneousSpeech;
        this.overlapThreshold = historyItem.matchOptions.overlapThreshold;
        this.threadId = null;
        this.router.navigate([], { queryParams: params });
    }

    replacer(key: string, value: string): any {
        if (["cancelled", "csv", "csvColumns", "percentComplete",
             "refreshSeconds", "resultTarget", "resultText", "resultUrl",
             "resultsName", "running", "seriesId", "sourceFile", "targetLayer",
             "totalUtteranceDuration", "who"].includes(key)) {
            return undefined;
        }
        if (key == "layers" && Array.isArray(value)) {
            return undefined;
        }
        if (["participantQuery", "transcriptQuery"].includes(key) && value) {
            if (value == "/.+/.test(id)") {
                return undefined;
            } else {
                return value.replaceAll(/"/g, "'");
            }
        }
        if (["participantDescription", "participantCount",
             "transcriptDescription", "transcriptCount"].includes(key) && !value) {
            return undefined;
        }
        if (["participantDescription", "transcriptDescription"].includes(key) &&
            ["all participants", "all transcripts"].includes(value)) {
            return undefined;
        }
        if (typeof value === "undefined") {
            if (["mainParticipantOnly","onlyAligned","firstMatchOnly","excludeSimultaneousSpeech"].includes(key)) {
                return false;
            } else {
                return "";
            }
        } else {
            return value;
        }
    }

    exportHistoryItem(historyItem: SearchHistoryItem): void {
        this.updateTask(historyItem.task.threadId).then(() => {
            const jsonString = JSON.stringify(historyItem, this.replacer, 2);
            this.exportUrl = this.sanitizer.sanitize(SecurityContext.HTML, 'data:application/json;charset=UTF-8,' + encodeURIComponent(jsonString));
            this.exportName = historyItem.task.threadName + '.json';
            setTimeout(() => this.exportAnchor.nativeElement.click(), 50);
        });
    }

    exportHistory(): void {
        const lastHistoryItem = this.history.filter(x => !x.sourceFile).slice(-1)[0];
        this.updateTask(lastHistoryItem.task.threadId).then(() => {
            const jsonString = JSON.stringify(this.history, this.replacer, 2);
            this.exportUrl = this.sanitizer.sanitize(SecurityContext.HTML, 'data:application/json;charset=UTF-8,' + encodeURIComponent(jsonString));
            let now = new Date();
            this.exportName = 'search-history-' + [now.getFullYear(), now.getMonth() + 1, now.getDate()].join("-") + '.json';
            setTimeout(() => this.exportAnchor.nativeElement.click(), 50);
        });
    }

    /** Button actions */
    deleteHistoryItem(historyItem: SearchHistoryItem): void {
        this.history = this.history.filter(x => x.task.threadId !== historyItem.task.threadId);
        sessionStorage.setItem("searchHistory", JSON.stringify(this.history));
    }
    deleteHistory(): void {
        this.history = this.history.filter(x => false);
        sessionStorage.removeItem("searchHistory");
    }
    /** Triggered by task */
    purgeHistory(threadId: string): void {
        this.history = this.history.filter(x => x.task.threadId != threadId);
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
    
    /** Called when a participant CSV file is selected; parses the file to determine CSV fields. */
    selectParticipantFile(files: File[]): void {
        console.log("selectParticipantFile " + files.length);
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
            // remove possible 'participant' header line (e.g. exported from participants page)
            lines = lines.filter(l => l != 'participant');
            console.log("non-blank, non-header lines " + lines.length);
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
                    this.router.navigate([], {
                        queryParams: {
                            participant_expression: "["+idList.join(",")+"].includes(id)",
                            participants: "From uploaded file" // TODO i18n
                        },
                        queryParamsHandling: 'merge'
                    });
                }                
            }
        };
        reader.onerror = function () {  
            component.messageService.error("Error reading " + component.participantsFile.name);
        };
        reader.readAsText(this.participantsFile);
        
    }
    
    /** Called when a transcript CSV file is selected; parses the file to determine CSV fields. */
    selectTranscriptFile(files: File[]): void {
        console.log("selectTranscriptFile " + files.length);
        if (files.length == 0) return;
        this.transcriptsFile = files[0]
        if (!this.transcriptsFile.name.endsWith(".csv")
            && !this.transcriptsFile.name.endsWith(".tsv")
            && !this.transcriptsFile.name.endsWith(".txt")) {
            this.messageService.error("You must select a text file (.txt, .csv, or .tsv)"); // TODO i18n
            this.transcriptsFile = null;
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
            // remove possible 'transcript' header line (e.g. exported from transcripts page)
            lines = lines.filter(l => l != 'transcript');
            console.log("non-blank, non-header lines " + lines.length);
            if (lines.length == 0) {
                component.messageService.error(
                    "File is empty: " + component.transcriptsFile.name); // TODO i18n
            } else {
                let idList = lines.map(
                    l=>"'"+l.replace(/\\/g, "\\\\").replace(/'/g, "\\'")+"'");
                if (idList.length == 0) {
                    component.messageService.error(
                        "File is empty: " + component.transcriptsFile.name); // TODO i18n
                } else {
                    this.router.navigate([], {
                        queryParams: {
                            transcript_expression: "["+idList.join(",")+"].includes(id)",
                            transcripts: "From uploaded file" // TODO i18n
                        },
                        queryParamsHandling: 'merge'
                    });
                }                
            }
        };
        reader.onerror = function () {  
            component.messageService.error("Error reading " + component.transcriptsFile.name); // TODO i18n
        };
        reader.readAsText(this.transcriptsFile);
    }

    /** Called when a history JSON file is selected; parses the file to determine fields. */
    selectHistoryFile(files: File[]): void {
        if (files.length == 0) return;
        this.historyFile = files[0]
        if (!this.historyFile.name.endsWith(".json")) {
            this.messageService.error("File must be a JSON search history file.")
            this.historyFile = null;
            return;
        }

        // read the file to determine fields
        const reader = new FileReader();
        const component = this;
        reader.onload = () => {
            let jsonData;
            try {
                jsonData = JSON.parse(<string>reader.result);
            } catch(error) {
                component.messageService.error(
                    "Error parsing file: " + // TODO i18n
                    component.historyFile.name + "\n" + error.message);
                return;
            }
            // if a single item, put into array
            if (!Array.isArray(jsonData)) {
                jsonData = [jsonData]
            }
            // ensure it's an array of historyItems
            const historyKeys = "filters matchOptions matrix metadata task"
            if (jsonData.filter(x => Object.keys(x).sort().join(' ') != historyKeys).length) {
                component.messageService.error(
                    "File does not contain any valid history item(s): " + component.historyFile.name); // TODO i18n
                return;
            }
            // add tracking fields
            for (let item of jsonData) {
                item.cancelled = false;
                item.sourceFile = "Imported from " + this.historyFile.name; // TODO i18n
            }
            // handle false matchOptions
            for (let item of jsonData) {
                for (let option in item.matchOptions) {
                    if (!item.matchOptions[option]) {
                        delete item.matchOptions[option];
                    }
                }
            }
            // handle empty participantQuery/transcriptQuery
            for (let item of jsonData) {
                if (item.matrix.hasOwnProperty("participantQuery") && item.matrix.participantQuery == "") {
                    delete item.matrix.participantQuery;
                }
                if (item.matrix.hasOwnProperty("transcriptQuery") && item.matrix.transcriptQuery == "") {
                    delete item.matrix.transcriptQuery;
                }
            }
            // handle nonexistent layers
            for (let item of jsonData) {
                let threadId = item.task.threadId;
                for (let [i, column] of item.matrix.columns.entries()) {
                    for (let l in column.layers) {
                        if (!Object.keys(this.schema.layers).includes(l)) {
                            delete column.layers[l];
                            component.messageService.info("Imported thread " + threadId + ": Removed nonexistent layer " + l + " from search matrix"); // TODO i18n
                        }
                    }
                    if (!Object.keys(column.layers).length) {
                        column.empty = true;
                        component.messageService.info("Imported thread " + threadId + ": Removed column " + (i + 1) + " from search matrix because there were no existent layers"); // TODO i18n
                    }
                }
                item.matrix.columns = item.matrix.columns.filter(x => !x.empty);
                // TODO figure out how to make this appear below info messages
                if (!item.matrix.columns.length) {
                    item.empty = true;
                    component.messageService.error("Failed to import thread " + threadId + " - no existent layers in search matrix"); // TODO i18n
                }
            }
            jsonData = jsonData.filter(x => !x.empty);

            // add to history
            if (!jsonData.length) {
                // TODO figure out how to make this appear below info messages
                component.messageService.error("Failed to import search history - no threads with existent layers"); // TODO i18n
            } else {
                this.history = this.history.concat(jsonData);
                sessionStorage.setItem("searchHistory", JSON.stringify(this.history));
            }
        };
        reader.onerror = function () {
            component.messageService.error("Error reading " + component.historyFile.name); // TODO i18n
        };
        reader.readAsText(this.historyFile);
    }
}

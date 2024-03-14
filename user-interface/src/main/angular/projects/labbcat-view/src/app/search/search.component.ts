import { Component, OnInit } from '@angular/core';
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
    matrix: Matrix;
    participantDescription: string;
    participantIds: string[];
    transcriptDescription: string;
    mainParticipantOnly: boolean;
    onlyAligned: boolean;
    firstMatchOnly: boolean;
    excludeSimultaneousSpeech: boolean;
    overlapThreshold: number;
    threadId:string;
    
    constructor(
        private labbcatService: LabbcatService,
        private messageService: MessageService,
        private route: ActivatedRoute,
        private router: Router
    ) { }
    
    ngOnInit(): void {
        this.matrix = {
            columns: [],
            participantQuery: "",
            transcriptQuery: ""
        }
        this.readUserInfo();
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
                this.listParticipants();
            });
        });
    }
    readUserInfo(): void {
        this.labbcatService.labbcat.getUserInfo((user, errors, messages) => {
            this.user = user as User;
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
                } // next match
            } // next column layer
        } // next column
        return matrix;
    }
    
    /** List participants that match the filters */
    listParticipants(): void {
        if (this.matrix.participantQuery) {
            this.labbcatService.labbcat.getMatchingParticipantIds(
                this.matrix.participantQuery, (participantIds, errors, messages) => {
                    if (errors) errors.forEach(m => this.messageService.error(m));
                    if (messages) messages.forEach(m => this.messageService.info(m));
                    this.participantIds = participantIds;
                });
        } // there is a participantExpression
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
}

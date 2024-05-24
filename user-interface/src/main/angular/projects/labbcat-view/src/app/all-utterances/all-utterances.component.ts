import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';

import { Layer } from 'labbcat-common';
import { User } from 'labbcat-common';
import { MessageService, LabbcatService } from 'labbcat-common';

@Component({
  selector: 'app-all-utterances',
  templateUrl: './all-utterances.component.html',
  styleUrl: './all-utterances.component.css'
})
export class AllUtterancesComponent {

    user: User;
    transcriptTypes: string[];
    selectedTranscriptTypes: string[];
    
    participantDescription: string;
    participantQuery: string;
    participantIds: string[];
    participantsFile: File;
    mainParticipantOnly = true;
    threadId: string;

    constructor(
        private labbcatService: LabbcatService,
        private messageService: MessageService,
        private route: ActivatedRoute,
        private router: Router
    ) { }
    
    ngOnInit(): void {
        this.readUserInfo();
        this.readTranscriptTypes();
        // interpret URL parameters
        this.route.queryParams.subscribe((params) => {
            this.participantQuery = params["participant_expression"];
            if (!this.participantQuery) {
                const id = params["id"];
                if (id) {
                    this.participantQuery = "id = '"+id.replace(/'/g,"\\'")+"'";
                }
            }
            if (this.participantQuery) {
                this.participantDescription = params["participants"]
                    || "Selected participants"; // TODO i18n
            }
            this.listParticipants(false);
        });
    }
    
    readUserInfo(): void {
        this.labbcatService.labbcat.getUserInfo((user, errors, messages) => {
            this.user = user as User;
        });
    }
    readTranscriptTypes(): void {
        this.labbcatService.labbcat.getLayer("transcript_type", (layer, errors, messages) => {
            this.transcriptTypes = Object.keys(layer.validLabels);
            this.selectedTranscriptTypes = Object.keys(layer.validLabels);
        });
    }

    participantCount = 0;
    loadingParticipants = false;
    /** List participants that match the filters */
    listParticipants(fromFile: boolean): void {
        this.participantIds = [];
        if (this.participantQuery) {
            this.labbcatService.labbcat.countMatchingParticipantIds(
                this.participantQuery, (participantCount, errors, messages) => {
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
            this.participantQuery, pageLength, this.participantIds.length / pageLength,
            (participantIds, errors, messages) => {
                if (errors) errors.forEach(m => this.messageService.error(m));
                if (messages) messages.forEach(m => this.messageService.info(m));
                this.participantIds = this.participantIds.concat(participantIds);
                this.loadingParticipants = false;
            });
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
                    component.participantQuery = "["+idList.join(",")+"].includes(id)";
                    console.log("component.participantQuery " + component.participantQuery);
                    this.listParticipants(true);
                }                
            }
        };
        reader.onerror = function () {  
            component.messageService.error("Error reading " + component.participantsFile.name);
        };
        reader.readAsText(this.participantsFile);
        
    }
    /** Handler for transcript type checkboxes */
    onTranscriptTypeChanged(event): void {
        if(event.target.checked){
            this.selectedTranscriptTypes.push(event.target.value);
        } else {
            this.selectedTranscriptTypes = this.selectedTranscriptTypes
                .filter(t => t != event.target.value);
        }
    }
    /** List button handler */
    allUtterances(): void {
        if (this.threadId) { // there was a previous task
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

        // ensure we have a full list of participant IDs
        this.labbcatService.labbcat.getMatchingParticipantIds(
            this.participantQuery, (participantIds, errors, messages) => {
                if (errors) errors.forEach(m => this.messageService.error(m));
                if (messages) messages.forEach(m => this.messageService.info(m));

                // start the all-utterances task
                this.labbcatService.labbcat.allUtterances(
                    participantIds, this.selectedTranscriptTypes, this.mainParticipantOnly,
                    (result, errors, messages) => {
                        if (errors) errors.forEach(m => this.messageService.error(m));
                        if (messages) messages.forEach(m => this.messageService.info(m));
                        this.threadId = result.threadId;
                    });
            });
    }
}

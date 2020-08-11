import { Component, OnInit, ViewChild, ElementRef } from '@angular/core';
import { ActivatedRoute } from '@angular/router';

import { Response } from '../response';
import { Match } from '../match';
import { Task } from '../task';
import { SerializationDescriptor } from '../serialization-descriptor';
import { MessageService } from '../message.service';
import { LabbcatService } from '../labbcat.service';

@Component({
  selector: 'app-matches',
  templateUrl: './matches.component.html',
  styleUrls: ['./matches.component.css']
})
export class MatchesComponent implements OnInit { 
    @ViewChild("form") form;
    @ViewChild("todo") todo;
    @ViewChild("generate_layer") generateLayer;
    name: string;
    status: string;
    totalMatches: number;
    zeroPad: number;
    matches: Match[];
    threadId: string;
    wordsContext: number;
    transcriptUrl: string;
    pageLength = 20;
    pageNumber = 0;
    selectAll = true;
    serializers: SerializationDescriptor[];
    mimeTypeToSerializer = {};
    mimeType = "text/praat-textgrid";
    serializeImg = "zip.png";
    showCsvOptions = false;
    selectedLayers = [ "transcript", "participant", "graph", "corpus" ]; // TODO load this from server
    showEmuOptions = false;
    emuLayers = [ "transcript", "segments" ];
    htkLayer = false; // TODO handle IUtteranceDataGenerator annotators better
    
    constructor(
        private labbcatService: LabbcatService,
        private messageService: MessageService,
        private route: ActivatedRoute
    ) { }
    
    ngOnInit(): void {
        this.matches = [];
        this.transcriptUrl = this.labbcatService.labbcat.baseUrl + "transcript";
        this.route.queryParams.subscribe((params) => {
            this.threadId = params["threadId"];
            this.wordsContext = params["wordsContext"] || 1;
            this.readTaskStatus();
        });
        this.readSerializers();
        this.readGenerableLayers();
    }

    readTaskStatus(): void {
        this.labbcatService.labbcat.taskStatus(this.threadId, (task, errors, messages) => {
            if (errors) for (let message of errors) this.messageService.error(message);
            if (messages) for (let message of messages) this.messageService.info(message);
            this.status = task.status;
            this.totalMatches = parseInt(task.status.replace(/\D/g,"")); // TODO need something more formal
            this.zeroPad = task.status.replace(/\D/g,"").length;
            
            this.readMatches();
        });
    }
    
    readSerializers(): void {
        this.labbcatService.labbcat.getSerializerDescriptors((descriptors, errors, messages) => {
            if (errors) for (let message of errors) this.messageService.error(message);
            if (messages) for (let message of messages) this.messageService.info(message);
            this.serializers = [];
            for (let descriptor of descriptors) {
                this.serializers.push(descriptor as SerializationDescriptor);
                this.mimeTypeToSerializer[descriptor.mimeType]
                    = descriptor as SerializationDescriptor;
            }
            // set initial icon
            this.onChangeMimeType();
        });
    }
    
    readMatches(): void {
        this.labbcatService.labbcat.getMatches(
            this.threadId, this.wordsContext, this.pageLength, this.pageNumber,
            (results, errors, messages) => {
                if (errors) for (let message of errors) this.messageService.error(message);
                if (messages) for (let message of messages) this.messageService.info(message);
                this.name = results.name;
                for (let match of results.matches) {
                    match._selected = true;
                    this.matches.push(match as Match);
                }
            });
    }

    readGenerableLayers(): void {
        this.labbcatService.labbcat.getLayer("htk", (layer, errors, messages) => {
            this.htkLayer == layer != null;
        });
    }
    
    moreMatches(): void {
        this.pageNumber++;
        this.readMatches();
    }
    
    allMatches(): void { // TODO find a way to only load the rest
        // load all the results
        this.matches = [];
        this.pageLength = null;
        this.pageNumber = null;
        this.readMatches();
    }
    
    all(): void {
        this.selectAll = !this.selectAll;
        for (let match of this.matches) {
            match._selected = this.selectAll;
        }
    }

    onChangeWordsContext(): void {
        // reload the first page
        this.matches = [];
        this.pageLength = 20;
        this.pageNumber = 0;
        this.readMatches();
    }

    exportAudio(): void {
        this.form.nativeElement.action = this.labbcatService.labbcat.baseUrl + "soundfragment";
        this.form.nativeElement.submit();
    }

    serialize(): void {
        this.todo.nativeElement.value = "convert";
        this.form.nativeElement.action = this.labbcatService.labbcat.baseUrl + "results";
        this.form.nativeElement.submit();
    }

    csvOptions(): void {
        this.showCsvOptions = !this.showCsvOptions;
    }
    exportCsv(): void {
        this.todo.nativeElement.value = "csv";
        this.form.nativeElement.action = this.labbcatService.labbcat.baseUrl + "resultsStream";
        this.form.nativeElement.submit();
    }

    emuOptions(): void {
        this.showEmuOptions = !this.showEmuOptions;
    }
    emuWebapp(): void {
        let serverUrl = this.labbcatService.labbcat.baseUrl.replace(/^http/,"ws") + "emu";

        if (this.selectAll) {
            serverUrl+= "?threadId="+this.threadId;
        } else {
            let sep = "?";
            for (let match of this.matches) {
                if (match._selected) {
                    serverUrl+= sep+"id="+match.MatchId.replace(/;[^;=]*=[^;=]*/g,"");
                    sep = "&";
                }
            }
        }
        for (let id of this.emuLayers) {
            serverUrl+= "&layer="+id;
        }
        window.open(
            this.labbcatService.labbcat.baseUrl+"EMU-webApp/app?autoConnect=true&serverUrl="
                +encodeURIComponent(serverUrl),
            "EMU-webApp").focus();
    }

    onChangeMimeType(): void {
        this.serializeImg = this.mimeTypeToSerializer[this.mimeType].icon;
    }

    runAnnotator(layerId: string): void {
        this.form.nativeElement.action = this.labbcatService.labbcat.baseUrl + "generateLayerUtterances";
        this.generateLayer.nativeElement.value = layerId;
        this.todo.nativeElement.value = "generate";
        this.form.nativeElement.submit();
    }

    toggleMatch(match: Match): void {
        match._selected = !match._selected;
    }

    // extract the transcript URL # from the MatchId
    matchHash(match: Match): string {
        return match.MatchId
            .replace(/.*#=/,"")  // remove everything up to #=
            .replace(/;.*/,""); // remove everything after ;
    }
    // Math.min
    min(n1: number, n2: number): number {
        return Math.min(n1, n2);
    }
}

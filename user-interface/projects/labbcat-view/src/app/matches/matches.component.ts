import { Component, OnInit, ViewChild, ElementRef } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';

import { Response } from 'labbcat-common';
import { Match } from '../match';
import { Task } from 'labbcat-common';
import { Layer } from 'labbcat-common';
import { User } from 'labbcat-common';
import { SerializationDescriptor } from '../serialization-descriptor';
import { MessageService, LabbcatService } from 'labbcat-common';

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
    searchedLayers: string[];
    matches: Match[];
    threadId: string;
    task: Task;
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
    selectedLayers: string[];
    showEmuOptions = false;
    emuLayers = [ "word", "segment" ];
    schema: any;
    htkLayer: Layer; // TODO handle IUtteranceDataGenerator annotators better
    baseUrl: string;
    emuWebApp = false;
    user: User;

    moreLoading = false;
    allLoading = false;
    readingMatches = false;

    // for HTK/layer generation
    generateLayerId: string;
    tokenLayerId: string;
    annotationLayerId: string;
    
    constructor(
        private labbcatService: LabbcatService,
        private messageService: MessageService,
        private route: ActivatedRoute,
        private router: Router
    ) { }
    
    ngOnInit(): void {
        this.matches = [];
        this.transcriptUrl = this.labbcatService.labbcat.baseUrl + "transcript";
        this.readingMatches = true;
        this.route.queryParams.subscribe((params) => {
            this.threadId = params["threadId"];
            this.wordsContext = params["wordsContext"] || 1;
            this.readTaskStatus();
        });
        this.readBaseUrl();
        this.readUserInfo();
        this.readSerializers();
        this.readGenerableLayers();
        this.readEmuWebappSetting();
    }

    readBaseUrl(): void {
        this.labbcatService.labbcat.getId((url, errors, messages) => {
            this.baseUrl = url;
        });
    }
    readUserInfo(): void {
        this.labbcatService.labbcat.getUserInfo((user, errors, messages) => {
            this.user = user as User;
        });
    }
    readTaskStatus(): void {
        this.labbcatService.labbcat.taskStatus(this.threadId, (task, errors, messages) => {
            if (errors) errors.forEach(m => this.messageService.error(m));
            if (messages) messages.forEach(m => this.messageService.info(m));
            this.task = task;
            this.status = task.status;
            this.totalMatches = task.size; // TODO need something more formal
            this.zeroPad = (""+task.size).length;
            this.searchedLayers = task.layers || [];
            this.selectedLayers = this.searchedLayers
                .concat([ "word", "participant", "transcript", "corpus" ]);
            
            this.readMatches();
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
            // set initial icon
            this.onChangeMimeType();
        });
    }
    
    readMatches(): void {
        this.readingMatches = true;
        this.labbcatService.labbcat.getMatches(
            this.threadId, this.wordsContext, this.pageLength, this.pageNumber,
            (results, errors, messages) => {
            if (errors) errors.forEach(m => this.messageService.error(m));
            if (messages) messages.forEach(m => this.messageService.info(m));
                this.name = results.name;
                for (let match of results.matches) {
                    match._selected = true;
                    this.matches.push(match as Match);
                }
                this.moreLoading = this.allLoading = this.readingMatches = false;
            });
    }

    readGenerableLayers(): void {
        this.labbcatService.labbcat.getSchema((schema, errors, messages) => {
            this.schema = schema;
            for (let layerId in schema.layers) {
                const layer = schema.layers[layerId] as Layer;
                if (layer.layer_manager_id == "HTK") {
                    this.htkLayer = layer;
                    break; // TODO looks for other layers that are generable from here
                }
            }
        });
    }
    
    readEmuWebappSetting(): void {
        this.labbcatService.labbcat.getSystemAttribute("EMU-webApp", (attribute, errors, messages) => {
          this.emuWebApp = attribute && attribute["value"] == "1";
        });
    }
    
    moreMatches(): void {
        this.moreLoading = true;
        this.pageNumber++;
        this.readMatches();
    }
    
    allMatches(): void { // TODO find a way to only load the rest
        this.allLoading = true;
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
        this.form.nativeElement.action = this.baseUrl + "soundfragment";
        this.form.nativeElement.submit();
    }

    serialize(): void {
        this.todo.nativeElement.value = "convert";
        this.form.nativeElement.action = this.baseUrl + "results";
        this.form.nativeElement.submit();
    }

    csvOptions(): void {
        this.showCsvOptions = !this.showCsvOptions;
    }
    exportCsv(): void {
        this.todo.nativeElement.value = "csv";
        this.form.nativeElement.action = this.baseUrl + "resultsStream";
        this.form.nativeElement.submit();
    }

    emuOptions(): void {
        this.showEmuOptions = !this.showEmuOptions;
    }
    emuWebapp(): void {
        let serverUrl = this.baseUrl.replace(/^http/,"ws") + "emu";

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
            this.baseUrl+"EMU-webApp/app?autoConnect=true&serverUrl="
                +encodeURIComponent(serverUrl),
            "EMU-webApp").focus();
    }

    onChangeMimeType(): void {
        this.serializeImg = this.mimeTypeToSerializer[this.mimeType].icon;
    }

    runAnnotator(layerId: string): void {
        const layer = this.schema.layers[layerId] as Layer;
        let formAction = this.baseUrl + "generateLayerUtterances";
        let formMethod = "POST";
        let formTarget = "_blank";
        if (layer && layer.layer_manager_id == "HTK") {
            try {
                // TODO api/missingAnnotations -> api/suggest -> api/lookup -> api/edit/dictionary/addentry -> generateLayerUtterances
                this.tokenLayerId = this.schema.layers["orthography"]?"orthography":this.schema.wordLayerId;
                // the annotationLayerId is currently specified in the htk layer configuration
                const PronunciationLayerId = /PronunciationLayerId=([0-9]+)/.exec(
                    this.htkLayer.extra)[1];
                // PronunciationLayerId is currently a layer_id number, so convert it to a layerId
                for (let layerId in this.schema.layers) {
                    const layer = this.schema.layers[layerId] as Layer;
                    if (layer.layer_id == PronunciationLayerId) {
                        this.annotationLayerId = layerId;
                        break;
                    }
                } // next layer
                if (this.annotationLayerId) {
                    // this.router.navigate(["missingAnnotations"], {
                    //     queryParams : {
                    //         generateLayerId : layerId,
                    //         sourceThreadId : this.threadId,
                    //         seriesId : seriesId,
                    //         tokenLayerId : tokenLayerId,
                    //         annotationLayerId : annotationLayerId }});

                    // return;
                    this.generateLayerId = layerId;
                    formAction = "edit/missingAnnotations";
                    formMethod = "GET"; // TODO debug mode only
                    formTarget = "";
                }
            } catch (x) {
                console.log("Could not process HTK layer: "+x);
                // just fall through...
            }
        } // HTK layer

        setTimeout(()=>{ // give a chance for the HTML form to update, then submit...
            this.form.nativeElement.method = formMethod;
            this.form.nativeElement.action = formAction;
            this.form.nativeElement.target = formTarget;
            this.generateLayer.nativeElement.value = layerId;
            this.todo.nativeElement.value = "generate";
            this.form.nativeElement.submit();
        }, 500);
    }

    toggleMatch(match: Match): void {
        match._selected = !match._selected;
    }

    // extract the transcript URL # from the MatchId
    matchHash(match: Match): string {
        // match.MatchId for word-based searches is something like:
        // g_571;em_12_123;n_234-n_345;p_456;#=ew_0_567;prefix=16-;[0]=ew_0_567;[1]=ew_0_678
        // and for segment-based searches:
        // g_571;em_12_123;n_234-n_345;p_456;#=es_1_567;prefix=16-;[0]=ew_0_678;[1]=ew_0_789
        
        // for the transcript page hash, we want the ID of the first word, i.e. [0]=...
        let hash = match.MatchId
            .replace(/.*\[0\]=/,"")  // remove everything up to [0]=
            .replace(/;.*/,""); // remove everything after ;
        if (!hash) { // fall back to #=...
            hash =  match.MatchId
                .replace(/.*#=/,"")  // remove everything up to #=
                .replace(/;.*/,""); // remove everything after ;
        }
        return hash;
    }
    // Math.min
    min(n1: number, n2: number): number {
        return Math.min(n1, n2);
    }
}

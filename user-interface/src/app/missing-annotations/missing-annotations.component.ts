import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';

import { Response } from '../response';
import { Task } from '../task';
import { Layer } from '../layer';
import { MessageService } from '../message.service';
import { LabbcatService } from '../labbcat.service';
import { AdminComponent } from '../admin-component';

@Component({
    selector: 'app-missing-annotations',
    templateUrl: './missing-annotations.component.html',
    styleUrls: ['./missing-annotations.component.css']
})
export class MissingAnnotationsComponent extends AdminComponent implements OnInit {

    sourceThreadId: string; // threadId of original search/allUtterances result    
    seriesId: string;          // parameter for missingAnnotations
    tokenLayerId: string;      // parameter for missingAnnotations
    annotationLayerId: string; // parameter for missingAnnotations

    annotationLayer: Layer;
    seriesName: string;
    missingAnnotationsThreadId: string;
    Object = Object; // so we can call Object.keys in the template
    missing: object;
    labels: object;
    suggestionsPending: object;
    ipaHelperWord: string;
    updating: false;
    
    constructor(
        labbcatService: LabbcatService,
        messageService: MessageService,
        private route: ActivatedRoute
    ) {
        super(labbcatService, messageService);
    }
    
    ngOnInit(): void {
        this.route.queryParams.subscribe((params) => {
            this.sourceThreadId = params["sourceThreadId"];
            this.seriesId = params["seriesId"];
            this.tokenLayerId = params["tokenLayerId"];
            this.annotationLayerId = params["annotationLayerId"];

            this.getAnnotationLayer();
            this.getSourceTaskName();
            this.startMissingAnnotationsTask();
        });
    }

    getAnnotationLayer(): void {
        this.labbcatService.labbcat.getLayer(this.annotationLayerId, (layer, errors, messages) => {
            if (errors) errors.forEach(m => this.messageService.error(m));
            if (messages) messages.forEach(m => this.messageService.info(m));
            this.annotationLayer = layer;
        });
    }

    getSourceTaskName(): void {
        this.labbcatService.labbcat.taskStatus(this.sourceThreadId, (task, errors, messages) => {
            if (errors) errors.forEach(m => this.messageService.error(m));
            if (messages) messages.forEach(m => this.messageService.info(m));
            this.seriesName = task.resultsName;
        });
    }
    
    startMissingAnnotationsTask(): void {
        this.labbcatService.labbcat.missingAnnotations(
            this.seriesId, this.tokenLayerId, this.annotationLayerId,
            (threadId, errors, messages) => {
                if (errors) errors.forEach(m => this.messageService.error(m));
                if (messages) messages.forEach(m => this.messageService.info(m));
                console.log("missingAnnotations: " + JSON.stringify(threadId));
                this.missingAnnotationsThreadId = threadId;
            });
        
    }

    taskFinished(task : Task): void {
        this.missingAnnotationsThreadId = null;
        
        if (task.resultUrl) {
            console.log(task.resultUrl);
            this.labbcatService.labbcat.createRequest(
                "missingAnnotations", null, (missing, errors, messages) => {
                    if (errors) errors.forEach(m => this.messageService.error(m));
                    if (messages) messages.forEach(m => this.messageService.info(m));
                    // labels is the map of word->pronunciation
                    this.labels = {};
                    for (let word in missing) {
                        this.labels[word] = "";
                    }
                    // suggestionsPending is the map of word->boolean (true=pending)
                    this.suggestionsPending = {};
                    for (let word in missing) {
                        const w = word;
                        this.suggestionsPending[w] = true;
                        // ask dictionary for a suggested pronunciation
                        this.labbcatService.labbcat.suggest(
                            this.annotationLayerId, w, (suggestion, errors, messages) => {
                                this.suggestionsPending[w] = false;
                                if (suggestion.words[w] != "") {
                                    this.labels[w] = suggestion.words[w];
                                    this.changed = true;
                                }
                            });                        
                    }
                    // missing is the map of word->first-occurrence                    
                    this.missing = missing;
                }, task.resultUrl)
                .send();
        }
    }

    lookupWord: string;
    lookupWords: string;
    lookupPending = false;
    lookupCombined: string;
    lookupWordsResult: object;
    lastLookup: string;
    lookup(word : string): void {
        this.lookupWord = word;
        this.lookupWordsResult = null;
        this.lookupWords = prompt("Lookup dictionary", word); // TODO bad!
        if (this.lookupWords) {
            this.lookupPending = true;
            this.labbcatService.labbcat.lookup(
                this.annotationLayerId, this.lookupWords, (result, errors, messages) => {
                    this.lookupPending = false;
                    this.lastLookup = word;
                    if (errors) errors.forEach(m => this.messageService.error(m));
                    if (messages) messages.forEach(m => this.messageService.info(m));
                    this.lookupCombined = result.combined;
                    this.lookupWordsResult = result.words;
                });
        }
    }

    useLabel(label: string, append: boolean): void {
        if (!this.labels[this.lastLookup] || !append) {
            this.labels[this.lastLookup] = label;
        } else { // append to the current one
            this.labels[this.lastLookup] += "-" + label;
        }
        // set focus to the input so they can immediately eit the label
        document.getElementById("pron-"+this.lastLookup).focus();
        this.changed = true;
    }

    symbolSelected(word: string, symbol: string) {
        this.labels[word] += symbol;
        document.getElementById("pron-"+word).focus();
        this.changed = true;
    }

    addEntries(): void {
        this.changed = false; //TODO
    }
}

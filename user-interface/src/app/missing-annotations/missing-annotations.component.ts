import { Component, OnInit, ViewChild, ElementRef } from '@angular/core';
import { ActivatedRoute } from '@angular/router';

import { Response } from '../response';
import { Task } from 'labbcat-common';
import { Layer } from '../layer';
import { MessageService, LabbcatService } from 'labbcat-common';
import { AdminComponent } from '../admin-component';

@Component({
    selector: 'app-missing-annotations',
    templateUrl: './missing-annotations.component.html',
    styleUrls: ['./missing-annotations.component.css']
})
export class MissingAnnotationsComponent extends AdminComponent implements OnInit {
    @ViewChild("form") form;

    generateLayerId: string; // layerId for the layer to generate afterwards
    sourceThreadId: string; // threadId of original search/allUtterances result    
    seriesId: string;          // parameter for missingAnnotations
    tokenLayerId: string;      // parameter for missingAnnotations
    annotationLayerId: string; // parameter for missingAnnotations
    utterances: string[];      // parameter for selected utterances, if any
    
    baseUrl: string;
    generateLayer: Layer;
    annotationLayer: Layer;
    seriesName: string;
    missingAnnotationsThreadId: string;
    Object = Object; // so we can call Object.keys in the template
    missing: object;      // word -> url of first mention
    labels: object;       // word -> entry
    wordsPending: object; // word -> pending status (spinner for suggestions etc.)
    alreadyAdded: object;   // word -> whether or not we've already added an entry
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
            this.generateLayerId = params["generateLayerId"];
            this.sourceThreadId = params["threadId"];
            this.seriesId = params["seriesId"];
            this.tokenLayerId = params["tokenLayerId"];
            this.annotationLayerId = params["annotationLayerId"];
            this.utterances = params["utterance"];

            this.getBaseUrl().then(()=>{
                this.getGenerateLayer().then(()=>{
                    this.getAnnotationLayer().then(()=>{
                        this.getSourceTaskName().then(()=>{
                            this.startMissingAnnotationsTask();
                        });
                    });
                });
            });
            
        });
    }
    
    getBaseUrl(): Promise<String> {
        return new Promise((resolve, reject) => {
            this.labbcatService.labbcat.getId((url, errors, messages) => {
                this.baseUrl = url;
                resolve(url);
            });
        });
    }

    getGenerateLayer(): Promise<Layer> {
        return new Promise((resolve, reject) => {
            this.labbcatService.labbcat.getLayer(
                this.generateLayerId, (layer, errors, messages) => {
                    if (errors) errors.forEach(m => this.messageService.error(m));
                    if (messages) messages.forEach(m => this.messageService.info(m));
                    this.generateLayer = layer;
                    resolve(this.generateLayer);
                });
        });
    }

    getAnnotationLayer(): Promise<Layer> {
        return new Promise((resolve, reject) => {
            this.labbcatService.labbcat.getLayer(
                this.annotationLayerId, (layer, errors, messages) => {
                    if (errors) errors.forEach(m => this.messageService.error(m));
                    if (messages) messages.forEach(m => this.messageService.info(m));
                    this.annotationLayer = layer;
                    resolve(this.annotationLayer);
                });
        });
    }

    getSourceTaskName(): Promise<String> {
        return new Promise((resolve, reject) => {
            this.labbcatService.labbcat.taskStatus(
                this.sourceThreadId, (task, errors, messages) => {
                    if (errors) errors.forEach(m => this.messageService.error(m));
                    if (messages) messages.forEach(m => this.messageService.info(m));
                    this.seriesName = task.resultsName;
                    resolve(this.seriesName);
                });
        });
    }
    
    startMissingAnnotationsTask(): void {
        this.missing = {};
        this.labels = {};
        this.wordsPending = {};
        this.alreadyAdded = {};
        
        this.labbcatService.labbcat.missingAnnotations(
            this.seriesId, this.tokenLayerId, this.annotationLayerId, this.utterances,
            (threadId, errors, messages) => {
                if (errors) errors.forEach(m => this.messageService.error(m));
                if (messages) messages.forEach(m => this.messageService.info(m));
                this.missingAnnotationsThreadId = threadId;
                
                // start asking for interim results
                this.getMissingAnnotations();
            });
        
    }

    getMissingAnnotations(): void {

        if (this.missingAnnotationsThreadId) {
            // get an interim list of missing words        
            const url
                = `${this.baseUrl}api/missingAnnotations?threadId=${this.missingAnnotationsThreadId}`;
            this.labbcatService.labbcat.createRequest(
                "missingAnnotations", null, (missing, errors, messages) => {
                    if (errors) errors.forEach(m => this.messageService.error(m));
                    if (messages) messages.forEach(m => this.messageService.info(m));
                    this.updateMissingAnnotationsList(missing);
                    this.labbcatService.labbcat.taskStatus(
                        this.missingAnnotationsThreadId, (task, errors, messages) => {
                            if (errors) errors.forEach(m => this.messageService.error(m));
                            if (messages) messages.forEach(m => this.messageService.info(m));
                            if (task.running) { // still running, so ask for an update again soon
                                setTimeout(() => this.getMissingAnnotations(), 20000);
                            }
                        });
                }, url)
                .send();
        }
    }

    updateMissingAnnotationsList(missing: object): void {
        for (let word in missing) {
            if (!(word in this.labels)) {
                this.missing[word] = missing[word];
                this.labels[word] = "";
                this.wordsPending[word] = false;
                this.alreadyAdded[word] = false;
            }
        } // next missing word
        
        // give the UX a second to create elements, then check missing words
        setTimeout(()=>this.checkMissing(), 1000);
    }

    taskFinished(task : Task): void {
        this.missingAnnotationsThreadId = null;
        
        if (task.resultUrl) {
            console.log(task.resultUrl);
            this.labbcatService.labbcat.createRequest(
                "missingAnnotations", null, (missing, errors, messages) => {
                    if (errors) errors.forEach(m => this.messageService.error(m));
                    if (messages) messages.forEach(m => this.messageService.info(m));
                    this.updateMissingAnnotationsList(missing);
                }, task.resultUrl)
                .send();
        }
    }

    checkMissing(): void {
        let missingWords = false;
        for (let word in this.labels) {
            if (this.alreadyAdded[word]) continue; // skip already-added words
            missingWords = true;
            if (this.labels[word] == "") { // only suggest for words that don't have an entry
                const w = word;
                this.wordsPending[w] = true;
                // ask dictionary for a suggested pronunciation
                this.labbcatService.labbcat.dictionarySuggest(
                    this.annotationLayerId, w, (suggestion, errors, messages) => {
                        this.wordsPending[w] = false;
                        if (suggestion.words[w] != "") {
                            this.labels[w] = suggestion.words[w];
                            this.changed = true;
                        }
                    });
            } // no entry yet
        } // next word
        if (!missingWords && !this.missingAnnotationsThreadId) {
            this.messageService.info("No missing entries.");
            // go straight to generating the layer
            this.form.nativeElement.submit();
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
            this.labbcatService.labbcat.dictionaryLookup(
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

    // we add entries recursively one at a time, so that:
    // 1. the user isn't bombarded with a huge list of messages
    // 2. synchronization problems with adding two words at once are avoid (looking at you CELEX)
    // 3. it's easy to run suggestions again once we're finished
    addNextEntry(): void {
        this.lookupWord = null;
        for (let word in this.labels) {
            if (this.labels[word]               // there is a pronunciation
                && !this.alreadyAdded[word]) { // and we haven' already added it
                // save it
                const label = word;
                const entry = this.labels[word];
                this.wordsPending[label] = true;
                this.labbcatService.labbcat.dictionaryAdd(
                    this.annotationLayerId, label, entry, (result, errors, messages) => {
                        this.wordsPending[label] = false;
                        if (errors) errors.forEach(m => this.messageService.error(m));
                        if (messages) messages.forEach(m => this.messageService.info(m));
                        if (!errors) { // add ok
                            // remove the word from the visible list
                            this.alreadyAdded[label] = true;
                            // process next entry
                            this.addNextEntry();
                        }
                    });
                // drop out of the function - i.e. only process one entry
                return;
            }
        } // next word
        
        // if we got this far, there were no more entries to save
        this.changed = false;

        // some of the new entries may lead to new suggestions
        this.checkMissing();
    }

    start(): void {
        if (this.missingAnnotationsThreadId) { // still looking for missing annotations
            // cancel that first
            this.labbcatService.labbcat.cancelTask(
                this.missingAnnotationsThreadId, (result, errors, messages) => {
                    if (errors) errors.forEach(m => this.messageService.error(m));
                    if (messages) messages.forEach(m => this.messageService.info(m));
                    // now start...
                    this.form.nativeElement.submit();
                });            
        } else {
            // start now...
            this.form.nativeElement.submit();
        }
    }
}

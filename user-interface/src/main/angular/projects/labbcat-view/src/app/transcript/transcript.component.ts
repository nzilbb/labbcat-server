import { Component, OnInit, Inject } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';

import { SerializationDescriptor } from '../serialization-descriptor';
import { PraatService } from '../praat.service';
import { ProgressUpdate } from '../progress-update';
import { Response, Layer, User, Annotation, Anchor, MediaFile } from 'labbcat-common';
import { MessageService, LabbcatService } from 'labbcat-common';

@Component({
  selector: 'app-transcript',
  templateUrl: './transcript.component.html',
  styleUrl: './transcript.component.css'
})
export class TranscriptComponent implements OnInit {
    
    schema : any;
    layerStyles : { [key: string] : any };
    layerCounts : { [key: string] : any };
    user : User;
    baseUrl : string;
    imagesLocation : string;
    id : string;
    originalFile : string;
    loading = true;
    transcript : any;
    correctionEnabled = false;

    // temporal annotations
    anchors : { [key: string] : Anchor };
    annotations : { [key: string] : Annotation };
    participants : Annotation[];
    utterances : Annotation[];
    words : Annotation[];
    generableLayers: Layer[];

    // transcript attributes
    attributes: string[];
    categoryLayers: object; // string->Layer
    categoryLabels: string[];
    currentCategory: string;
    categories: object; // string->Category

    selectedLayerIds : string[];
    interpretedRaw: { [key: string] : boolean };

    temporalBlocks : { consecutive : boolean, utterances : Annotation[] }[];
    
    serializers : SerializationDescriptor[];
    mimeTypeToSerializer = {};

    hasWAV: boolean;
    praatIntegration: string; // version number, or "" if installable
    authorization: string;
    praatProgress: ProgressUpdate;
    textGridUrl: string;
    praatUtteranceName: string;
    praatUtterance: Annotation;
    
    availableMedia: MediaFile[];
    media : { [key: string] : { [key: string] : MediaFile[] } }; // type->trackSuffix->file
    selectableMediaCount = 0;
    videoZoomed = false;

    menuId: string;

    threadId : string;
    matchTokens = {} as { [key: string] : number };
    
    constructor(
        private labbcatService : LabbcatService,
        private messageService : MessageService,
        private praatService : PraatService,
        private route : ActivatedRoute,
        private router : Router,
        @Inject('environment') private environment
    ) {
        this.imagesLocation = this.environment.imagesLocation;
        this.selectedLayerIds = [];
        this.interpretedRaw = {};
        this.layerStyles = {};
        this.layerCounts = {};
        this.playingId = [];
        this.previousPlayingId = [];
    }
    
    ngOnInit() : void {        
        this.route.queryParams.subscribe((params) => {
            this.id = params["id"]||params["transcript"]||params["ag_id"];
            this.threadId = params["threadId"];
            this.readUserInfo().then(()=>{
                this.readBaseUrl().then(()=>{
                    this.setCorrectionsEnabled();
                });
            });
            this.readSerializers();
            this.readSchema().then(() => {
                this.readTranscript().then(()=>{ // some have to wait until transcript is loaded
                    this.readAvailableMedia().then(()=>{
                        this.praatService.initialize().then((version: string)=>{
                            console.log(`Praat integration version ${version}`);
                            this.praatIntegration = version;
                            this.praatProgress = {
                                message: `Praat Integration ${this.praatIntegration}`,
                                value: 0,
                                maximum: 100
                            };
                            this.praatService.progressUpdates().subscribe((progress) => {
                                this.praatProgress = progress;
                            });
                        }, (canInstall: boolean)=>{
                            if (canInstall) {
                                console.log("Praat integration not installed but it could be");
                                this.praatIntegration = "";
                            } else {
                                console.log("Praat integration: Incompatible browser");
                                this.praatIntegration = null;
                            }
                        });
                    });
                    
                    // preselect layers?
                    let layerIds = params["layerId"]||params["l"]
                    if (!layerIds && sessionStorage.getItem("selectedLayerIds")) {
                        layerIds = [...new Set(JSON.parse(sessionStorage.getItem("selectedLayerIds")))];
                    }
                    if (!layerIds) layerIds = ["noise","comment"]; // noise and comment by default
                    if (layerIds) {
                        if (Array.isArray(layerIds)) {
                            this.layersChanged(layerIds);
                        } else {
                            this.layersChanged([ layerIds ]);
                        }
                    }
                    if (this.threadId) this.loadThread();
                    this.setOriginalFile();
                }); // transcript read
            }); // subscribed to queryParams
        }); // after readSchema
        addEventListener("hashchange", (event) => {
            this.highlight(window.location.hash.substring(1));
        });
        this.readCategories();
    }
    
    readSchema() : Promise<void> {
        return new Promise((resolve, reject) => {
            this.labbcatService.labbcat.getSchema((schema, errors, messages) => {
                this.schema = schema;
                this.schema.root.description = "Transcript";
                this.generableLayers = [];
                this.attributes = [];
                this.categoryLayers = {};
                for (let layerId in this.schema.layers) {
                    const layer = this.schema.layers[layerId] as Layer;
                    // detemine which layers can be regenerated
                    if (layer.layer_manager_id && layer.id != this.schema.wordLayerId
                        && /T/.test(layer.enabled)) {
                        this.generableLayers.push(layer);
                    }
                    // determine which layers have interpreted/raw selectors
                    if (layer.validLabelsDefinition && layer.validLabelsDefinition.length) {
                        // are there keys that are different from labels?
                        for (let definition of layer.validLabelsDefinition) {
                            if (definition.display && definition.display != definition.label) {
                                this.interpretedRaw[layer.id] = true; // interpreted by default
                                break; // only need one
                            }
                        } // next label
                    }
                    // identify transcript attribute layers
                    if (layer.parentId == "transcript"
                        && layer.alignment == 0
                        && layer.id != schema.participantLayerId
                        && layer.id != schema.episodeLayerId
                        && layer.id != schema.corpusLayerId) {

                        // ensure we can iterate all layer IDs
                        this.attributes.push(layer.id);
                        
                        // ensure the transcript type layer has a category
                        if (layer.id == "transcript_type") layer.category = "transcript_General";
                        
                        if (!this.categoryLayers[layer.category]) {
                            this.categoryLayers[layer.category] = [];
                        }
                        this.categoryLayers[layer.category].push(layer);
                    }
                } // next layer
                resolve();
            });
        });
    }

    readUserInfo() : Promise<void> {
        return new Promise((resolve, reject) => {
            this.labbcatService.labbcat.getUserInfo((user, errors, messages) => {
                this.user = user as User;
                resolve();
            });
        });
    }
    
    readBaseUrl(): Promise<void> {
        return new Promise((resolve, reject) => {
            this.labbcatService.labbcat.getId((url, errors, messages) => {
                this.baseUrl = url;
                resolve();
            });
        });
    }
    
    readSerializers() : void {
        this.labbcatService.labbcat.getSerializerDescriptors((descriptors, errors, messages) => {
            if (errors) errors.forEach(m => this.messageService.error(m));
            if (messages) messages.forEach(m => this.messageService.info(m));
            this.serializers = [];
            for (let descriptor of descriptors) {
                this.serializers.push(descriptor as SerializationDescriptor);
                this.mimeTypeToSerializer[descriptor.mimeType]
                    = descriptor as SerializationDescriptor;
            }
            // alphabetical order
            this.serializers.sort((a,b) => a.name.charCodeAt(0) - b.name.charCodeAt(0));
        });
    }

    readCategories(): Promise<void> {
        this.categories = {};
        return new Promise((resolve, reject) => {
            this.labbcatService.labbcat.readOnlyCategories(
                "transcript", (categories, errors, messages) => {
                    this.categoryLabels = ["Participants", "Layers", "Formats"]; // TODO i18n
                    // extra pseudo categories
                    this.categories["Layers"] = { // TODO i18n
                        label: "Layers", // TODO i18n
                        description: "Annotation layers for display",
                        icon: "layers.svg"
                    }; // TODO i18n
                    this.categories["Participants"] = { // TODO i18n
                        label: "Participants", // TODO i18n
                        description: "The participants in the transcript",
                        icon: "people.svg"
                    }; // TODO i18n
                    this.categories["Formats"] = { // TODO i18n
                        label: "Formats", // TODO i18n
                        description: "Export the transcript in a selected format",
                        icon: "document.svg"
                    }; // TODO i18n
                    
                    for (let category of categories) {
                        const layerCategory = "transcript_"+category.category;
                        category.label = category.category;
                        if (!category.description) {
                            category.description = `Attributes: ${category.category}`; // TODO i18n
                        }
                        category.icon = "attributes.svg";
                        this.categories[layerCategory] = category;
                        this.categoryLabels.push(layerCategory);
                    }
                    if (this.categoryLabels.length == 4) { // only one actual category
                        // just label the tab 'attributes'
                        this.categories[this.categoryLabels[3]].label = "Attributes" // TODO i18n
                    }
                    resolve();
                });
        });
    }
    readTranscript() : Promise<void> {
        const structuralLayerIds = [
            this.schema.corpusLayerId,
            this.schema.episodeLayerId,
            this.schema.participantLayerId,
            "main_participant",
            this.schema.turnLayerId,
            this.schema.utteranceLayerId,
            this.schema.wordLayerId,
            // unnofficial layers:
            "previous-transcript", "next-transcript", // link to neighbors
            "audio_prompt", // 'Insert CD99' or whatever
            "divergent" // has the transcript changed since upload?
        ];
        return new Promise((resolve, reject) => {
            this.labbcatService.labbcat.getTranscript(
                this.id, structuralLayerIds.concat(this.attributes),
                (transcript, errors, messages) => {
                    this.loading = false;
                    if (errors) errors.forEach(m => this.messageService.error(m));
                    if (messages) messages.forEach(m => this.messageService.info(m));
                    if (!transcript) {
                        console.error("Invalid transcript ID");
                        this.messageService.error("Invalid transcript ID"); // TODO i18n
                        reject();
                    } else { // valid transcript
                        this.transcript = this.labbcatService.annotationGraph(transcript);
                        // id might have been ag_id or something else non-canonical, correct it:
                        this.id = this.transcript.id;
                        this.parseTranscript();
                        
                        // grey out empty layers
                        for (let l in this.schema.layers) {
                            const layer = this.schema.layers[l];
                            if (layer.parentId == this.schema.root.id
                                && layer.alignment == 0) continue;
                            if (layer.parentId == this.schema.participantLayerId) continue;
                            if (layer.id == this.schema.root.id) continue;
                            if (layer.id == this.schema.corpusLayerId) continue;
                            if (layer.id == this.schema.episodeLayerId) continue;
                            if (layer.id == this.schema.participantLayerId) continue;
                            if (layer.id == this.schema.utteranceLayerId) continue;
                            if (layer.id == this.schema.wordLayerId) continue;
                            // a temporal layer
                            this.layerStyles[l] = { color: "silver" };
                            this.labbcatService.labbcat.countAnnotations(
                                this.transcript.id, l, (count, errors, messages) => {
                                    this.layerCounts[l] = count;
                                    if (count) { // annotations in this layer
                                        if (count == 1) {
                                            this.schema.layers[l].description += ` (${count} annotation)`; // TODO i18n
                                        } else {
                                            this.schema.layers[l].description += ` (${count} annotations)`; // TODO i18n
                                        }
                                        this.layerStyles[l] = {};
                                    } else {
                                        this.schema.layers[l].description += ' (0 annotations)'; // TODO i18n
                                    }
                                });
                        } // next temporal layer
                        resolve();
                    } // valid transcript
                });
        });
    }

    parseTranscript() : void {
        const participantLayerId = this.schema.participantLayerId;
        const turnLayerId = this.schema.turnLayerId;
        const utteranceLayerId = this.schema.utteranceLayerId;
        const wordLayerId = this.schema.wordLayerId;

        // index anchors
        this.anchors = {};
        for (let id in this.transcript.anchors) {
            const anchor = this.transcript.anchors[id] as Anchor;
            anchor.id = id; // ensure ID is set
            this.anchors[id] = anchor;
        }

        // parse transcript structure
        this.annotations = {};
        this.participants = [];
        this.utterances = [];
        this.words = [];
        // for each participant
        for (let participant of this.transcript.all(participantLayerId)) {
            this.annotations[participant.id] = participant as Annotation;
            this.participants.push(participant as Annotation);
            // for each turn
            for (let turn of participant.all(turnLayerId)) {
                // for each utterance
                for (let utterance of turn.all(utteranceLayerId)) {
                    this.annotations[utterance.id] = utterance as Annotation;
                    this.utterances.push(utterance as Annotation);
                    // we're going to link words to utterances
                    utterance[wordLayerId] = [];
                    
                } // next utterance

                // parse words and distribute them into utterances
                let u = 0;
                const utterances = turn.all(utteranceLayerId) as Annotation[];
                // for each word
                for (let word of turn.all(wordLayerId)) {
                    this.annotations[word.id] = word as Annotation;                    
                    this.words.push(word as Annotation);

                    // add to the current utterance
                    // if the words starts after utterance u ends, increment
                    while (word.start.offset >= utterances[u].end.offset
                        && u < utterances.length-1) {
                        u++;
                    }
                    utterances[u][wordLayerId].push(word);
                } // next word
            } // next turn
        } // next participant

        // now sort utterances by start offset, across participants
        this.utterances.sort((a,b) => a.start.offset - b.start.offset);

        // now divide utterances into 'temporal blocks'
        this.temporalBlocks = [];

        // a temporal block is a list of utterances
        // usually a block is (speaker) turn, so the utterances in it are consecutive
        // but a block may contain a list of utterances by different speakers,
        // in which case the block represents simultaneous speech
        if (this.utterances.length > 0) {
            let currentTurnId = this.utterances[0].parentId;
            let currentBlock = { consecutive : true, utterances : [] };
            let lastUtterance = null;
            
            for (let u in this.utterances) {
                let newBlock = false;
                let consecutive = true; // as opposed to simultaneous
                const utterance = this.utterances[u];
                if (utterance.parentId != currentTurnId) { // turn change
                    newBlock = true;
                }
                const nextUtterance = this.utterances[parseInt(u)+1]; // why is parseInt required?
                if (nextUtterance // the next utterance is during this one
                    && nextUtterance.start.offset < utterance.end.offset) {
                    newBlock = true;
                    consecutive = false;
                }
                // but if this is during the last utterance
                if (lastUtterance && utterance.start.offset < lastUtterance.end.offset
                    // and we it's  not a participant we've already seen
                    && !currentBlock.utterances.find(u=>u.label == utterance.label)) {
                    // this is a simultaneous speech block, so don't start a new one
                    newBlock = false;
                    currentTurnId = "";
                }
                if (newBlock) {
                    currentTurnId = utterance.parentId;
                    if (currentBlock.utterances.length) { // add last block
                        this.temporalBlocks.push(currentBlock);
                    }
                    currentBlock = { consecutive : consecutive, utterances : [] };
                }
                currentBlock.utterances.push(utterance);
                
                lastUtterance = utterance;
            } // next utterance
            if (currentBlock.utterances.length) { // add last block
                this.temporalBlocks.push(currentBlock);
            }
        } // there are utterances

        if (window.location.hash) {
            window.setTimeout(()=>{ // give time for the page to render
                this.highlight(window.location.hash.substring(1));
            }, 500);
        }
        this.showMediaPrompt();
    }

    loadThread(): void {
        this.labbcatService.labbcat.taskStatus(this.threadId, (task, errors, messages) => {
            if (task) {
                let taskLayers = task.layers.filter(l=>l!="orthography");
                if (task.layers) this.layersChanged(taskLayers);
                this.highlightSearchResults(0);
            }
        });
    }
    
    highlightSearchResults(pageNumber: number) : void {
        const transcriptId = this.transcript.id;
        this.labbcatService.labbcat.getMatches(
            this.threadId, 0, 100, pageNumber,
            (results, errors, messages) => {
                if (!results) return;
                for (let match of results.matches) {                    
                    if (match.Transcript == transcriptId) {
                        // for now just highlight the first word                        
                        const firstWordId = match.MatchId.replace(/.*\[0\]=(ew_0_[0-9]*).*/,"$1");
                        const lastWordId = match.MatchId.replace(/.*\[1\]=(ew_0_[0-9]*).*/,"$1");
                        const resultNumber = parseInt(match.MatchId
                            .replace(/.*prefix=([0-9]*)-.*/,"$1"));
                        this.matchTokens[firstWordId] = resultNumber;
                        if (lastWordId && lastWordId != firstWordId) {
                            // tag subsequent tokens
                            const firstWord = this.transcript.annotations[firstWordId];
                            if (firstWord) {
                                let nextWord = firstWord.next;
                                let c = 1;
                                while (nextWord && nextWord.id != lastWordId
                                      && ++c < 20) { // not for thn 20 words for safety
                                    this.matchTokens[nextWord.id] = resultNumber;
                                    nextWord = nextWord.next;
                                }
                                this.matchTokens[lastWordId] = resultNumber;
                            }
                        }
                    }
                } // next match
                if (results.matches.length) {
                    this.highlightSearchResults(pageNumber+1);
                }
            });
    }

    setOriginalFile(): void {
        // only set origial file URL if we don't censor transcripts/media
        this.labbcatService.labbcat.getSystemAttribute(
            "censorshipRegexp", (attribute, errors, messages) => {
                if (!attribute.value
	            // or this is a super user
	            || this.user.roles.includes("admin")) {
	            this.originalFile = this.baseUrl
                        + "files"
	                + "/" + encodeURIComponent(
                            this.transcript.first(this.schema.corpusLayerId).label)
	                + "/" + encodeURIComponent(
                            this.transcript.first(this.schema.episodeLayerId).label)
	                + "/trs/"+encodeURIComponent(this.transcript.id);
                }
            });
    }

    setCorrectionsEnabled(): void {        
        // correctionEnabled if they're not an edit user
        // and requests to the corrections URL return 200 status (not 400 error)
        if (!this.user.roles.includes("edit")) {
            this.labbcatService.labbcat.createRequest(
                "correction", null, (r, errors, messages) => {
                    if (!errors) {
                        this.correctionEnabled = true;
                    }
                },
                this.baseUrl+"correction")
                .send();        
        }
    }

    readAvailableMedia() : Promise<void> {
        return new Promise((resolve, reject) => {
            this.labbcatService.labbcat.getAvailableMedia(
                this.id, (mediaTracks, errors, messages) => {
                    if (errors) errors.forEach(m => this.messageService.error(m));
                    if (messages) messages.forEach(m => this.messageService.info(m));
                    this.availableMedia = mediaTracks;
                    this.hasWAV = this.availableMedia.find(file=>file.mimeType == "audio/wav") != null;
                    this.media = {};
                    for (let file of this.availableMedia) {
                        if (!this.media[file.type]) {
                            this.media[file.type] = {};
                        }
                        if (!this.media[file.type][file.trackSuffix]) {
                            this.media[file.type][file.trackSuffix] = [];
                        }
                        this.media[file.type][file.trackSuffix].push(file);
                    } // next file
                    // remove any tracks that have only generated content
                    for (let t in this.media) {
                        const mediaType = this.media[t];
                        for (let s in mediaType) {
                            const trackSuffix = mediaType[s];
                            if (!trackSuffix.find(file=>!file.generateFrom)) {
                                delete mediaType[s];
                            }
                        } // next media type
                    }
                    // how many media visualizations are possible?
                    this.selectableMediaCount = 0;
                    const mediaPeckingOrder = ["video", "audio", "image"];
                    for (let t of mediaPeckingOrder) {
                        if (this.media[t]) {
                            const mediaType = this.media[t];
                            for (let s in mediaType) {
                                this.selectableMediaCount++;
                                if (this.selectableMediaCount == 1) { // first media found
                                    // select it
                                    this.showMedia(mediaType[s][0]);
                                }
                            } // next media type
                        } // has this type
                    } // next media type
                    resolve();
                });
        });
    }
    
    layersChanged(selectedLayerIds : string[]) : void {
        const addedLayerIds = selectedLayerIds.filter((x)=>this.selectedLayerIds.indexOf(x) < 0);
        const loadingLayers = [] as Promise<string>[];
        const deferredLayerIds = [] as string[]; // for deferred visualization
        this.loading = true;

        // remove unticked layers
        this.selectedLayerIds = selectedLayerIds.filter((x)=>addedLayerIds.indexOf(x) < 0);
        // remember the deselections for next time
        sessionStorage.setItem("selectedLayerIds", JSON.stringify(this.selectedLayerIds));

        // load new layers one at a time
        for (let layerId of addedLayerIds) {
            const layer = this.schema.layers[layerId];
            loadingLayers.push(this.loadLayerIncrementally(layerId, 0));
            if (this.isSpanningLayer(layer)) { // spanning layer
                // defer visualization until all annotations are loaded and indexed
                deferredLayerIds.push(layerId);
            } else { // immediate incremental vizualization is ok
                this.selectedLayerIds.push(layerId);
            }
        } // next newly selected layer
        
        // once everything's finished loading
        Promise.all(loadingLayers).then(()=>{
            this.loading = false;

            // visualize deferred layers
            for (let layerId of deferredLayerIds) {
                this.selectedLayerIds.push(layerId);
            }
            
            // remember the selections for next time
            sessionStorage.setItem("selectedLayerIds", JSON.stringify(this.selectedLayerIds));

            // if there's a highlight, make sure it scrolls back into view after the layer changes
            if (this.highlitId) {
                setTimeout(()=>{ // give the UI a chance to update
                    this.highlight(this.highlitId);
                }, 200);
            }
        });
    }

    /** Load the given (zero-based) page of annotations on the given layer.
     * The promise is resolved once annotations and anchors on the given page and all subsequent
     * pages have been added to the annotation graph. */
    loadLayerIncrementally(layerId: string, page: number) : Promise<string> {
        return new Promise((resolve, reject) => {
            if (this.transcript.layers[layerId] && page == 0) { // have already loaded this layer
                resolve(`${page}:${layerId}`);
            } else { // load the layer definition
                const layer = this.schema.layers[layerId];
                this.transcript.schema.layers[layerId] = layer;
                layer.parent = this.transcript.schema.layers[layer.parentId];
                layer.colour = this.stringToColour(layerId);
                this.layerStyles[layerId] = {
                    color : layer.colour, borderColour: layer.colour };

                // load annotations
                this.labbcatService.labbcat.getAnnotations(
                    this.transcript.id, layerId, 1000, page, (annotations, errors, messages) => {
                        if (page == 0) {
                            if (errors) errors.forEach(m => 
                                this.messageService.error(`${layerId}: ${m}`));
                        }
                        if (annotations.length) {
                            const unknownAnchorIds = new Set<string>();
                            for (let a of annotations) {
                                if (!this.transcript.anchors[a.startId]) {
                                    unknownAnchorIds.add(a.startId);
                                }
                                if (!this.transcript.anchors[a.endId]) {
                                    unknownAnchorIds.add(a.endId);
                                }
                            } // next annotation
                            
                            if (unknownAnchorIds.size) {
                                // there might be a lot of anchors to load,
                                // making one request too large
                                // so we break the anchor list into chunks
                                this.loadAnchorsIncrementally(unknownAnchorIds).then(()=>{
                                    // add annotations to graph once we've got all the anchors
                                    for (let a of annotations) {
                                        const annotation = new this.labbcatService.ag.Annotation(
                                            layerId, a.label, this.transcript,
                                            a.startId, a.endId,
                                            a.id, a.parentId);
                                        if (a.dataUrl) annotation.dataUrl = a.dataUrl;
                                        this.transcript.addAnnotation(annotation);
                                    }
                                    
                                    // next page
                                    this.loadLayerIncrementally(layerId, page + 1).then(()=>{
                                        resolve(`${page}:${layerId}`);
                                    });
                                });
                            } else { // all anchors are already loaded
                                // we've got all the anchors, so add the annotations to the graph
                                for (let a of annotations) {
                                    const annotation = new this.labbcatService.ag.Annotation(
                                        layerId, a.label, this.transcript, a.startId, a.endId,
                                        a.id, a.parentId);
                                    if (a.dataUrl) annotation.dataUrl = a.dataUrl;
                                    this.transcript.addAnnotation(annotation);
                                }
                                
                                // next page
                                this.loadLayerIncrementally(layerId, page + 1).then(()=>{
                                    resolve(`${page}:${layerId}`);
                                });
                            }
                        } else { // there were no more annotations
                            // once phrase/span layers are fully loaded,
                            // index the token words they contain
                            if (this.isSpanningLayer(layer)) {
                                this.indexTokensOnLayer(layer);
                            } // phrase/spanning layer
                                    
                            resolve(`${page}:${layerId}`);
                        }
                    });
            }
        });
    }

    /** recursive anchor loading, to prevent requests from becoming too large */
    loadAnchorsIncrementally(unknownAnchorIds : Set<string>) : Promise<void> {
        const maxIds = 150;
        return new Promise<void>((resolve, reject) => {
            let idsToLoadNow = new Set<string>(Array.from(unknownAnchorIds).slice(0, maxIds));
            let idsToLoadLater = new Set<string>(Array.from(unknownAnchorIds).slice(maxIds));
            this.labbcatService.labbcat.getAnchors(
                this.transcript.id, Array.from(idsToLoadNow), (anchors, errors, messages) => {
                    if (errors) errors.forEach(m => 
                        this.messageService.error(`Load anchors: ${m}`));
                    if (messages) messages.forEach(m =>
                        this.messageService.info(`Load anchors: ${m}`));
                    for (let a of anchors) {
                        const anchor = new this.labbcatService.ag.Anchor(
                            a.offset, this.transcript);
                        Object.assign(anchor, a);
                        this.transcript.addAnchor(anchor);
                    } // next anchor
                    if (idsToLoadLater.size) {
                        this.loadAnchorsIncrementally(idsToLoadLater).then(resolve);
                    } else { // finished!
                        resolve();
                    }
                });
        });
    }
    
    indexTokensOnLayer(layer : Layer) : void {
        const wordLayerId = this.schema.wordLayerId;
        const utteranceLayerId = this.schema.utteranceLayerId;
        // spans can overlap (e.g. n-gram annotations, syntactic parses)
        // we want
        //  a) each span to be visualised at the same height across all tokens, and
        //  b) the visualisation to be visually compact
        // so, we have each words' span array use the array index to determine the height,
        // and compress the indices so that spans fit into available gaps

        // process them by parent, so that phrases from one speaker can't interfere with
        // those of another speaker
        for (let parent of this.transcript.all(layer.parentId)) {
            const spans = (parent.all(layer.id)||[])
            // first, order all annotations so that
            // i) annotations with earlier starts are earlier
            // ii) where starts are equal, longer annotations are earlier
                .toSorted((a,b)=>{
                    if (a==b) return 0; // just in case
                    // earlier starts are earlier
                    return a.start.offset - b.start.offset
                    // longer durations are earlier
                        || b.duration()-a.duration()
                    // if same start and duration, reverse sort by id
                        || (""+b.id).localeCompare(""+a.id);
                });
            // set depth of each span to the minimum possible value without overlap
            const maxOffset = []; // register the highest offset at a given depth
            let maxDepth = -1;
            for (let s in spans) {
                const span = spans[s];
                // check each height to see if this span will fit
                let shallowestDepth = maxOffset.findIndex(
                    endOffset=>endOffset <= span.start.offset);
                if (shallowestDepth < 0) { // if not, plumb new depths
                    shallowestDepth = maxDepth+1;
                }
                span._depth = shallowestDepth;
                maxOffset[span._depth] = span.end.offset;
                maxDepth = Math.max(maxDepth, span._depth);
            } // next span
    
            // link words to spans that contain them
            // first create levels for every word token
            for (let token of parent.all(wordLayerId)) token[layer.id] = new Array(maxDepth+1);
            // now link 'included' spans to word tokens
            for (let span of spans) {
                if (!span[wordLayerId]) {
                    const tokens = [];
                    // link it to all the words it contains
                    for (let token of span.all(wordLayerId)) {
                        tokens.push(token);
                        if (!token[layer.id]) token[layer.id] = new Array(maxDepth+1);
                        token[layer.id][span._depth] = span;
                    } // next contained token
                    span[wordLayerId] = tokens;
                    // were there any?
                    let linkedUtterance = null;
                    
                    if (span.start.startOf[utteranceLayerId] // starts with utterance
                        && span.start.startOf[utteranceLayerId].length) {
                        const utterance = span.start.startOf[utteranceLayerId][0];
                        if (utterance.end.offset == span.end.offset) {
                            linkedUtterance = utterance;
                            if (!linkedUtterance[layer.id]) linkedUtterance[layer.id] = [];
                            linkedUtterance[layer.id].push(span);
                            span.tagsUtterance = linkedUtterance;
                        }
                    }
                    if (!linkedUtterance // not linked to an utterance
                        && span[wordLayerId].length == 0) { // no tokens included
                        let nearestWord = null;
                        if (span.start.endOf[wordLayerId] // immediately follows a word?
                            && span.start.endOf[wordLayerId].length) {
                            nearestWord = span.start.endOf[wordLayerId][0];
                            // is it strung from the word end to the utterance end?
                            const utterance = nearestWord.first(utteranceLayerId);
                            if (utterance && utterance.endId == span.endId) {
                                linkedUtterance = utterance
                                // tag the utterance so the visualization knows to prepend a column
                                utterance.appendDummyToken = true;
                                // ensure the span doesn't also get visualized with the word
                                nearestWord = null;
                            } else {
                                // tag the span to 'jump' ahead, so it offset to the right to 
                                // represent that it's between this word and the next
                                span.jump = true;
                            }
                        } else if (span.end.startOf[wordLayerId] // immediately precedes a word?
                            && span.end.startOf[wordLayerId].length) {
                            nearestWord = span.end.startOf[wordLayerId][0];
                            // is it strung from the utterance start to the word start?
                            const utterance = nearestWord.first(utteranceLayerId);
                            if (utterance && utterance.startId == span.startId) {
                                linkedUtterance = utterance
                                // tag the utterance so the visualization knows to prepend a column
                                utterance.prependDummyToken = true;
                                // ensure the span doesn't also get visualized with the word
                                nearestWord = null;
                            }
                        } else if (span.start.startOf[wordLayerId] // starts with word?
                            && span.start.startOf[wordLayerId].length) {
                            nearestWord = span.start.startOf[wordLayerId][0];
                        } else if (span.end.endOf[wordLayerId] // ends with a word?
                            && span.end.endOf[wordLayerId].length) {
                            nearestWord = span.end.endOf[wordLayerId][0];
                        } else { // pick the nearest word
                            const allWords = this.transcript.all(wordLayerId);
                            // overlap?
                            for (let word of allWords) {
                                if (span.overlaps(word)) {
                                    nearestWord = word;
                                    break;
                                }
                            } // next word
                            if (!nearestWord) {
                                // first word that starts after the span start
                                for (let word of allWords) {
                                    if (span.start.offset < word.start.offset) {
                                        nearestWord = word;
                                        break;
                                    }
                                } // next word
                            }
                            if (!nearestWord) {
                                // no following word, so select last word
                                nearestWord = allWords[allWords.length-1];
                            }
                        } // pick nearest word
                        if (nearestWord) {
                            // link it to the span
                            tokens.push(nearestWord);
                            if (!nearestWord[layer.id]) {
                                nearestWord[layer.id] = new Array(maxDepth+1);
                            }
                            nearestWord[layer.id][span._depth] = span;
                        } else if (!linkedUtterance) {
                            console.error(`Could not visualize: ${span.label}#${span.id} (${span.start}-${span.end})`);
                        }
                    } // no tokens included
                } // not already done
            } // next span
        } // next parent

        // for each utterance
        for (let utterance of this.transcript.all(utteranceLayerId)) {
            // drop spans so that they're as near as possible to their tokens
            const utteranceWords = utterance.all(wordLayerId);
            const maxSpanIndexDuringUtterance = utteranceWords.reduce((maxSoFar, word) => {
                const wordSpans = word.all(layer.id);
                const firstNonSpanIndexForWord = wordSpans.findIndex(span=>!span);
                return Math.max(
                    maxSoFar,
                    firstNonSpanIndexForWord == -1?wordSpans.length - 1
                        :firstNonSpanIndexForWord - 1);
            }, -1);
            utteranceWords.forEach((word)=>{
                word.all(layer.id).length = maxSpanIndexDuringUtterance + 1;
            });
            
        }

    }
    
    // https://stackoverflow.com/questions/3426404/create-a-hexadecimal-colour-based-on-a-string-with-javascript#16348977
    stringToColour(str : string) : string {
        var hash = 0;
        for (var i = 0; i < str.length; i++) {
            hash = str.charCodeAt(i) + ((hash << 5) - hash);
        }
        var colour = '#';
        for (var i = 0; i < 3; i++) {
            var value = (hash >> (i * 8)) & 0xFF;
            colour += ('00' + value.toString(16)).substr(-2);
        }
        return colour;
    }

    renderLabel(annotation: Annotation) : string {
        let display = annotation.label;
        if (this.interpretedRaw[annotation.layer.id]) {
            for (let definition of annotation.layer.validLabelsDefinition) {
                if (definition.display
                    && (display == definition.label // replace whole labels only
                        || annotation.layer.type == "ipa") // unless it's a phonological layer
                   ) { // there is a display version of this label
                    display = display.replace(definition.label, definition.display);
                    if (annotation.layer.type != "ipa") { // whole label replaced
                        break;
                    }
                }
            } // next definition
        } // a conversion is required
        return display;
    }

    /** Test whether the layer is phrase/span layer */
    isSpanningLayer(layer : Layer) : boolean {
        return (layer.parentId == this.schema.turnLayerId
            && layer.id != this.schema.utteranceLayerId 
            && layer.id != this.schema.wordLayerId)
            || (layer.parentId == this.schema.root.id
                && layer.alignment > 0);
    }
    /** Test whether the layer is is empty */
    isEmpty(layer : any) : boolean {
        return !layer.annotations || layer.annotations.length == 0;
    }
    /** Test whether the layer is word scope layer */
    isWordLayer(layer : Layer) : boolean {
        return layer.parentId == this.schema.wordLayerId && layer.id != "segment";
    }
    /** Test whether the layer is segment scope layer */
    isSegmentLayer(layer : Layer) : boolean {
        return layer.parentId == "segment" || layer.id == "segment";
    }

    /** Highlight the annotation with the given ID */
    highlitId: string;
    highlight(id: string): void {
        this.highlitId = id;
        try {
            document.getElementById(id).scrollIntoView({
                behavior: "smooth",
                block: "center"
            });
        } catch (x) {}
    }

    /* convert selectedLayerIds array into a series of URL parameters with the given name */
    selectedLayerIdParameters(parameterName: string): string {
        return this.selectedLayerIds
        // noise and comment layers are displayed by default but we don't want them here
            .filter(layerId => layerId != "comment" && layerId != "noise")
            .map(layerId => "&"+parameterName+"="+encodeURIComponent(layerId))
            .join("");
    }

    canSelectMultipleVisualizations = false;
    multipleVisualizationsTimer = null;
    visibleVideoCount = 0;
    visibleAudioCount = 0;
    /** Select media for visualization */
    showMedia(file: MediaFile):void {
        const originallySelected = file._selected;
        if (!originallySelected && !this.canSelectMultipleVisualizations) {
            // unselect all others
            for (let file of this.availableMedia) file._selected = false;
        }
        // toggle rather than select, so that all media can be hidden
        file._selected = !originallySelected;
        if (this.selectableMediaCount > 1 // if there's more than one selection available
            && file._selected) { // and we're ticking not unticking
            // for a short while, multiple visualizations can be selected
            this.canSelectMultipleVisualizations = true;
            this.loading = true;
            if (this.multipleVisualizationsTimer) {
                window.clearTimeout(this.multipleVisualizationsTimer)
            }
            this.multipleVisualizationsTimer = window.setTimeout(()=>{
                this.canSelectMultipleVisualizations = false;
                this.loading = false;
                this.multipleVisualizationsTimer = null;
            }, 2000);
        }
        if (!file._selected) { // removing player
            const elementId = file.type+"-"+file.nameWithoutSuffix;
            if (this.player && this.player.id == elementId) {
                // removing main player, so enable other players to become main player
                const audios = document.getElementsByTagName('audio');
                for (let p = 0; p < audios.length; p++) {
                    const player = audios.item(p);
                    player.setAttribute("controls","");
                    player.pause();
                } // next player
                const videos = document.getElementsByTagName('video');
                for (let p = 0; p < videos.length; p++) {
                    const player = videos.item(p);
                    player.setAttribute("controls","");
                    player.pause();
                } // next player
                this.player = null;
            } // player is main player
        } // removing player
        window.setTimeout(()=>{ // give the visualization a chance to update before counting videos
            this.visibleVideoCount = document.getElementsByTagName("video").length;
            this.visibleAudioCount = document.getElementsByTagName("audio").length;
        }, 100);
    }

    playingId : string[]; // IDs of currently playing utterances
    previousPlayingId : string[]; // keep a buffer of old IDs, so we can fade them out
    player: HTMLMediaElement;
    stopAfter : number; // stop time for playing a selection
    /** Event handler for when the time of a media player is updated */
    mediaTimeUpdate(event: Event): void {
        // only pay attention to the main player
        if (this.player == event.target) {
            // safari: player.controller.currentTime

            // identify utterance(s) that is/are currently playing
            const lastPlayingId = this.playingId || [];
            const newPlayingId = (this.transcript.annotationsAt(
                this.player.currentTime, this.schema.utteranceLayerId)||[])
                                     .map(annotation=>annotation.id);
            // fade in IDs that are now playing
            this.playingId = newPlayingId;            
            // fade out the IDs that are no longer playing
            this.previousPlayingId = lastPlayingId.filter(id=>!this.playingId.includes(id));
            if (this.playingId.length) {
                document.getElementById(this.playingId[0]).scrollIntoView({
                    behavior: "smooth",
                    block: "center"
                });
            }

            const audios = document.getElementsByTagName('audio');
            const videos = document.getElementsByTagName('video');
            if (this.stopAfter && this.stopAfter <= this.player.currentTime
                // try to pre-empt the stop time, so it doesn't play into the next utterance:
                + 0.3) {
                // arrived at stop time
                this.stopAfter = null;
                // stop all media
                for (let p = 0; p < audios.length; p++) {
                    audios.item(p).pause();
                } // next player
                for (let p = 0; p < videos.length; p++) {
                    videos.item(p).pause();
                } // next player
            } else { // haven't reached stop time
                // keep all other media elements synchronized
                for (let p = 0; p < audios.length; p++) {
                    const player = audios.item(p);
                    if (player != this.player) {
                        player.currentTime = this.player.currentTime;
                    }
                } // next player
                for (let p = 0; p < videos.length; p++) {
                    const player = videos.item(p);
                    if (player != this.player) {
                        player.currentTime = this.player.currentTime;
                    }
                } // next player
            } // haven't reached stop time
        }
    }
    /** Event handler for when a media player is paused */
    mediaPause(event: Event): void {
        // only pay attention to the main player
        if (this.player == event.target) {
            // tell all other media elements to play
            const audios = document.getElementsByTagName('audio');
            for (let p = 0; p < audios.length; p++) {
                const player = audios.item(p);
                if (player != this.player) {
                    player.pause();
                }
            } // next player
            const videos = document.getElementsByTagName('video');
            for (let p = 0; p < videos.length; p++) {
                const player = videos.item(p);
                if (player != this.player) {
                    player.pause();
                }
            } // next player
            this.previousPlayingId = this.playingId;
            this.playingId = [];
        } // main player event
    }
    /** Event handler for when a media player starts playing */
    mediaPlay(event: Event): void {
        const player = event.target as HTMLMediaElement;
        if (!this.player || this.player != document.getElementById(this.player.id)) {
            this.player = player;
            // if it's a video, it may have been previosly muted
            this.player.muted = false;
        }
        // only pay attention to the main player
        if (this.player == event.target) {
            // tell all other media elements to play
            const audios = document.getElementsByTagName('audio');
            for (let p = 0; p < audios.length; p++) {
                const player = audios.item(p);
                if (player != this.player) {
                    player.play();
                    player.removeAttribute("controls");
                }
            } // next player
            const videos = document.getElementsByTagName('video');
            for (let p = 0; p < videos.length; p++) {
                const player = videos.item(p);
                // mute videos if there is audio
                player.muted = audios.length > 0
                // and all but the first video
                    || p > 0;
                if (player != this.player) {
                    player.play();
                    player.removeAttribute("controls");
                }
            } // next player
        } // main player event
    }
    /** Event handler for when a media player encounters an error */
    mediaError(event: Event): void {
        const player = event.target as HTMLMediaElement;
        this.messageService.error(`Media error: ${player.id} - ${player.error.code}:${player.error.message}`);
    }
    /** Show the media prompt if there is one (e.g. 'Insert CD 99' for local media) */
    showMediaPrompt() {
        if (this.transcript.first("audio_prompt")) {
            this.messageService.info(this.transcript.first("audio_prompt").label);
        }
    }
    /** Rewind all media by a second */
    mediaRepeat(): void {
        const audios = document.getElementsByTagName('audio');
        for (let p = 0; p < audios.length; p++) {
            audios.item(p).currentTime -= 1;
        } // next player
        const videos = document.getElementsByTagName('video');
        for (let p = 0; p < videos.length; p++) {
            videos.item(p).currentTime -= 1;
        } // next player
    }
    /** Play a selected utterance */
    playSpan(annotation: Annotation): void {
        if (!annotation || !annotation.anchored()) return;
        // is there a player yet?
        if (!this.player || this.player != document.getElementById(this.player.id)) { // no player
            const audios = document.getElementsByTagName('audio');
            if (audios.length > 0) {
                this.player = audios.item(0);
            } else { // no audio elements
                const videos = document.getElementsByTagName('video');
                if (videos.length > 0) {
                    this.player = videos.item(0);
                }
            }
        }
        if (this.player) {
            // set time of all first
            const audios = document.getElementsByTagName('audio');
            const videos = document.getElementsByTagName('video');
            for (let p = 0; p < audios.length; p++) {
                audios.item(p).currentTime = annotation.start.offset;
            }
            for (let p = 0; p < videos.length; p++) {
                videos.item(p).currentTime = annotation.start.offset;
            }
            // then play all
            for (let p = 0; p < audios.length; p++) {
                audios.item(p).play();
            }
            for (let p = 0; p < videos.length; p++) {
                videos.item(p).play();
            }
            // then stop after the end time
            this.stopAfter = annotation.end.offset;
        }
    }
    localMediaUrl: string;
    localMediaType: string;
    /** User has selected a local media file */    
    useLocalMedia(event: Event): void {
        const input = event.target as HTMLInputElement;
        const file = input.files[0];
        this.localMediaUrl = URL.createObjectURL(file);
        this.localMediaType = file.type.substring(0,5);
    }
    
    /** Visualize a given tree */
    showTree(annotation : Annotation) : boolean {
        const turn = annotation.first(this.schema.turnLayerId);
        const turn_id = turn.id.replace("em_11_","");
        window.open(
            `${this.baseUrl}tree?layer_id=${this.schema.layers[annotation.layerId].layer_id}&start_uid=${annotation.start.id}&end_uid=${annotation.end.id}&turn=${turn_id}`, 
            "tree",
            "height=600,width=700,toolbar=no,menubar=no,scrollbars=yes,resizable=yes,location=no,directories=no,status=yes"
        ).focus();
        return false;
    }
    
    editUtterance(utterance : Annotation) : boolean {
        const url = this.baseUrl
            +"edit/correction"
            +"?id="+encodeURIComponent(this.transcript.id)
            +"&annotation_uid="+utterance.id;
        window.open(
            url, "correction",
            "height=350,width=600,toolbar=no,menubar=no,scrollbars=yes,resizable=yes,location=no,directories=no,status=yes"
        ).focus();
        return false;
    }
    
    suggestCorrection(utterance : Annotation) : boolean {
        const url = this.baseUrl
            +"correction"
            +"?id="+encodeURIComponent(this.transcript.id)
            +"&annotation_uid="+utterance.id;
        window.open(
            url, "correction",
            "height=350,width=600,toolbar=no,menubar=no,scrollbars=yes,resizable=yes,location=no,directories=no,status=yes"
        ).focus();
        return false;
    }
    
    editWord(word : Annotation) : boolean {
        const url = `${this.baseUrl}edit/annotation?annotation_uid=${word.id}`;
        window.open(
            url, "word",
            "height=300,width=400,toolbar=no,menubar=no,scrollbars=yes,resizable=yes,location=no,directories=no,status=yes"
        ).focus();
        return false;
    }
    
    showGenerateLayerSelection = false;
    generateLayerId = "";
    /** Generate annotation layers */
    generate(): void {
        if (!this.showGenerateLayerSelection) { // show options
            this.showGenerateLayerSelection = true;
        } else { // options selected, so go ahead and do it
            const url = `${this.baseUrl}edit/layers/regenerate?id=${this.transcript.id}&layer_id=${this.generateLayerId}`;
            document.location = url;
        }
    }
    
    /** Export utterance audio */
    utteranceAudio(utterance : Annotation) : boolean {
        const url = `${this.baseUrl}soundfragment?id=${this.transcript.id}&start=${utterance.start.offset}&end=${utterance.end.offset}`;
        document.location = url;
        return false;
    }    

    /** Install Praat integration */
    installPraatIntegration(): void {
        // start plugin installer
        if(navigator.userAgent.indexOf("Firefox") != -1 ) {
            // Firefox can get the extension directly from LaBB-CAT
            if (confirm("You need to install the 'Praat Integration' browser extension." // TODO i18n
                +"\nWhen you are asked to install the extension, click 'Allow' and then 'Install'.")) {
                this.downloadURI(`${this.baseUrl}/utilities/jsendpraat.xpi?3.0`);
                window.setTimeout(function() { 
                    window.onfocus = function() {
                        // do this once only
                        window.onfocus = null; 
                        // offer to reload the page
                        if (confirm("Once the 'Praat Integration' extension is installed, you must refresh this page to activate it.\nWould you like to refresh the page now?")) { // TODO i18n
                            window.location.reload();
                        }
                    }
                }, 5000);
            }
        } else  if (confirm("You need to install the 'Praat Integration' browser extension." // TODO i18n
            +"\nYou will be taken to the Chrome Web Store now."
            +"\nOnce the store opens, click the 'Add to Chrome' button.")) {
            window.open("https://chrome.google.com/webstore/detail/praat-integration/hmmnebkieionilgpepijmfabdickmnig", "chromewebstore");
            window.setTimeout(function() { 
                window.onfocus = function() {
                    // do this once only
                    window.onfocus = null; 
                    // offer to reload the page
                    if (confirm("Once the 'Praat Integration' extension is installed, you must refresh this page to activate it.\nWould you like to refresh the page now?")) { // TODO i18n
                        window.location.reload();
                    }
                }
            }, 2000);
        } // not Firefox, assume Chrome-like browser
    }

    divergentCheck(): boolean {
        if (this.transcript.first('divergent')) {
            return confirm(
                "⚠ The text of this transcript may have changed since the original file was uploaded,"
                    +" and these more recent changes are not in the original file."
                    +"\n\nAre you sure you want to download the original file?");
        } else {
            return true;
        }
    }

    /** Opens a download for the given URI */
    downloadURI(uri: string): void {
        const link = document.createElement("a");
        link.href = uri;
        document.body.appendChild(link);
        link.click();
    }

    /** Determines how the Praat browser extension will authenticate itself with the server */
    getAuthorization(): Promise<string> {
        // make asking for authorization as late and infrequent as possible
        if (this.authorization != null) {
            return Promise.resolve(this.authorization);
        } else {
            return new Promise<string>((resolve, reject) => {
                // ask for the current authorization
                const a = this.labbcatService.labbcat.createRequest(
                    "a", null, (a, errors, messages) => {
                        if (!errors) {
                            if (typeof a === 'string') {
                                this.authorization = a;
                            } else {
                                this.authorization = "";
                            }
                            resolve(this.authorization);
                        } else { 
                            // no authorization might be fine?
                            errors.forEach(m => this.messageService.error("Authorization error: " + m));
                            this.authorization = "";
                            resolve(this.authorization);
                        }                        
                    },
                    this.baseUrl+"a");
                const mainScript = document.querySelector("script[src*=main]");
                if (mainScript && mainScript.getAttribute("src")) {
                    // ensure the server knows it's us asking...
                    const nonce = mainScript.getAttribute("src").replace(/.*main(.*)\.js/,"$1");
                    a.setRequestHeader("If-Match", nonce);
                }
                try {
                    a.send();
                } catch (x) {
                    // no authorization might be fine?
                    // this is most likely a cross-origin browser error from working
                    // in the development environment, where there's not auth anyway
                    this.messageService.error("Authorization request error: " + x);
                    this.authorization = "";
                    resolve(this.authorization);
                }
            });
        }
    }

    /** Open utterance audio in Praat */
    praatUtteranceAudio(utterance: Annotation): void {
        this.getAuthorization().catch((errors: string[])=>{
            errors.forEach(m => this.messageService.error("Authorization error: " + m));
            this.praatProgress = {
                message: "",
                value: 100,
                maximum: 100,
                code: errors.join(", "),
                error: "Authorization error: "+errors.join(", ")
            }
        }).then((authorization: string)=>{ // getAuthorization...
            const transcriptIdForUrl = this.transcript.id.replace(/ /g, "%20");
            const audioUrl = this.baseUrl+"soundfragment"
                +"?id="+transcriptIdForUrl
                +"&start="+utterance.start.offset
                +"&end="+utterance.end.offset;
            this.praatService.sendPraat([
                "Read from file... "+audioUrl,
                "Edit"
            ], authorization).catch((errors: string[])=>{
                this.praatProgress = {
                    message: "",
                    value: 100,
                    maximum: 100,
                    code: errors.join(", "),
                    error: errors.join(", ")
                }
            }).then((code: string)=>{ // sendPraat...
                this.praatProgress = {
                    message: `Opened: ${transcriptIdForUrl} (${utterance.start}-${utterance.end})`, // TODO i18n
                    value: 100,
                    maximum: 100,
                    code: code
                }
            });
        });
    }

    /** Open utterance audio and TextGrid in Praat */
    praatUtteranceTextGrid(utterance: Annotation): void {
        this.getAuthorization().then((authorization: string)=>{
            const transcriptIdForUrl = this.transcript.id.replace(/ /g, "%20");
            this.praatUtteranceName = this.transcript.id
                .replace(/\.[a-zA-Z][^.]*$/,"") // remove extension
                .replace(/[^a-zA-Z0-9]+/g,"_") // Praat isn't inclusive about object names
                +("__"+utterance.start.offset).replace(".","_")
                +("_"+utterance.end.offset).replace(".","_");
            const audioUrl = this.baseUrl+"soundfragment"
                +"?id="+transcriptIdForUrl
                +"&start="+utterance.start.offset
                +"&end="+utterance.end.offset;
            this.textGridUrl = this.baseUrl
                +"serialize/fragment?mimeType=text/praat-textgrid"
                +"&id="+transcriptIdForUrl
                +"&layerId="+this.schema.utteranceLayerId
                +"&layerId="+this.schema.wordLayerId
                +this.selectedLayerIds
            // noise and comment layers are displayed by default but we don't want them here
                    .filter(l=>l != "comment" && l != "noise")
                    .map(l=>"&layerId="+l.replace(/ /g, "%20")).join("")
                +"&start="+utterance.start.offset
                +"&end="+utterance.end.offset
                +"&filter="+utterance.parentId
                +"&nonce="+Math.random();
            this.praatService.sendPraat([
                "Read from file... "+audioUrl,
                "Rename... "+this.praatUtteranceName,
                "Read from file... "+this.textGridUrl,
                "Rename... "+this.praatUtteranceName,
                "plus Sound "+this.praatUtteranceName,
                "Edit"
            ], authorization).then((code: string)=>{
                if (code == "0") {
                    this.praatUtterance = utterance;
                }
                this.praatProgress = {
                    message: `Opened: ${this.praatUtteranceName}`, // TODO i18n
                    value: 100,
                    maximum: 100,
                    code: code
                }
            }).catch((error: string)=>{
                this.praatProgress = {
                    message: "",
                    value: 100,
                    maximum: 100,
                    code: error,
                    error: error
                }
            });
        });
    }

    /** Open utterance and context audio and TextGrid in Praat */
    praatUtteranceContextTextGrid(utterance: Annotation): void {
        this.getAuthorization().then((authorization: string)=>{
            const firstUtterance = utterance.previous||utterance;
            const lastUtterance = utterance.next||utterance;
            const transcriptIdForUrl = this.transcript.id.replace(/ /g, "%20");
            this.praatUtteranceName = this.transcript.id
                .replace(/\.[a-zA-Z][^.]*$/,"") // remove extension
                .replace(/[^a-zA-Z0-9]+/g,"_") // Praat isn't inclusive about object names
                .replace(" ","_")
                +("__"+firstUtterance.start.offset).replace(".","_")
                +("_"+lastUtterance.end.offset).replace(".","_");
            const audioUrl = this.baseUrl+"soundfragment"
                +"?id="+transcriptIdForUrl
                +"&start="+firstUtterance.start.offset
                +"&end="+lastUtterance.end.offset;
            this.textGridUrl = this.baseUrl
                +"serialize/fragment?mimeType=text/praat-textgrid"
                +"&id="+transcriptIdForUrl
                +"&layerId="+this.schema.utteranceLayerId
                +"&layerId="+this.schema.wordLayerId
                +this.selectedLayerIds.map(l=>"&layerId="+l.replace(/ /g, "%20")).join("")
                +"&start="+firstUtterance.start.offset
                +"&end="+lastUtterance.end.offset
                +"&filter="+utterance.parentId
                +"&nonce="+Math.random();
            const zoomStart = utterance.start.offset - firstUtterance.start.offset;
            const zoomEnd = utterance.end.offset - firstUtterance.start.offset;
            this.praatService.sendPraat([
                "Read from file... "+audioUrl,
                "Rename... "+this.praatUtteranceName,
                "Read from file... "+this.textGridUrl,
                "Rename... "+this.praatUtteranceName,
                "plus Sound "+this.praatUtteranceName,
                "Edit",
                "editor TextGrid "+this.praatUtteranceName,
                "Zoom... " + zoomStart + " " + zoomEnd,
                "endeditor"
            ], authorization).then((code: string)=>{
                if (code == "0") {
                    this.praatUtterance = utterance;
                }
                    this.praatProgress = {
                        message: `Opened: ${this.praatUtteranceName}`, // TODO i18n
                        value: 100,
                        maximum: 100,
                        code: code
                    }
            }).catch((error: string)=>{
                this.praatProgress = {
                    message: "",
                    value: 100,
                    maximum: 100,
                    code: error,
                    error: error
                }
            });
        });
    }

    /** Import changes from Praat */
    praatImportChanges(): void {
        if (this.user.roles.includes("edit")) {
            this.getAuthorization().then((authorization: string)=>{
                const uploadUrl = this.baseUrl+"edit/uploadFragment";
                this.praatService.upload(
                    [ // script
                        "select TextGrid "+this.praatUtteranceName,
                        "Write to text file... "+this.textGridUrl
                    ], uploadUrl, // URL to upload to
                    "uploadfile", // name of file HTTP parameter
                    this.textGridUrl, // original URL for the file to upload
                    { automaticMapping: "true", todo: "upload" }, // extra HTTP request parameters
                    authorization).then((code: string)=>{
                        this.praatUtterance = null;
                        this.praatProgress = {
                            message: "",
                            value: 100,
                            maximum: 100,
                            code: code
                        }
                    }).catch((error: string)=>{
                        this.praatProgress = {
                            message: "",
                            value: 100,
                            maximum: 100,
                            code: error,
                            error: error
                        }
                    });
            });
        } // 'edit' user
    }

    hideWordMenu(): void {
        this.menuId = null;
    }
}

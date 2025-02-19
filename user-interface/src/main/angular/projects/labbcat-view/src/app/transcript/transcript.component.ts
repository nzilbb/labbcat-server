import { Component, OnInit, Inject } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';

import { SerializationDescriptor } from '../serialization-descriptor';
import { Response, Layer, User, Annotation, Anchor } from 'labbcat-common';
import { MessageService, LabbcatService } from 'labbcat-common';

// TODO layer selection from URL parameters
// TODO optionally hide empty layers
// TODO word menu
// TODO media
// TODO participant list with links
// TODO meta-data
// TODO format conversion
// TODO remember layer selections
// TODO ensure incoming URLs with hashes work properly

@Component({
  selector: 'app-transcript',
  templateUrl: './transcript.component.html',
  styleUrl: './transcript.component.css'
})
export class TranscriptComponent implements OnInit {
    
    schema : any;
    layerStyles : { [key: string] : any };
    user : User;
    baseUrl : string;
    imagesLocation : string;
    id : string;
    loading = true;
    transcript : any;
    anchors : { [key: string] : Anchor };
    annotations : { [key: string] : Annotation };
    participants : Annotation[];
    utterances : Annotation[];
    words : Annotation[];

    selectedLayerIds : string[];
    interpretedRaw: { [key: string] : boolean };

    temporalBlocks : { consecutive : boolean, utterances : Annotation[] }[];
    
    serializers : SerializationDescriptor[];
    mimeTypeToSerializer = {};

    constructor(
        private labbcatService : LabbcatService,
        private messageService : MessageService,
        private route : ActivatedRoute,
        private router : Router,
        @Inject('environment') private environment
    ) {
        this.imagesLocation = this.environment.imagesLocation;
        this.selectedLayerIds = [];
        this.interpretedRaw = {};
        this.layerStyles = {};
    }
    
    ngOnInit() : void {        
        this.readUserInfo();
        this.readBaseUrl();
        this.readSerializers();
        this.readSchema().then(() => {
            this.route.queryParams.subscribe((params) => {
                this.id = params["id"]
                this.readTranscript();
            });
        });
    }
    
    readSchema() : Promise<void> {
        return new Promise((resolve, reject) => {
            this.labbcatService.labbcat.getSchema((schema, errors, messages) => {
                this.schema = schema;
                this.schema.root.description = "Transcript";
                // determine which layers have interpreted/raw selectors
                for (let l in this.schema.layers) {
                    const layer = this.schema.layers[l];
                    if (layer.validLabelsDefinition && layer.validLabelsDefinition.length) {
                        // are there keys that are different from labels?
                        for (let definition of layer.validLabelsDefinition) {
                            if (definition.display && definition.display != definition.label) {
                                this.interpretedRaw[layer.id] = true; // interpreted by default
                                break; // only need one
                            }
                        } // next label
                    }
                } // next layer
                resolve();
            });
        });
    }
    
    readUserInfo() : void {
        this.labbcatService.labbcat.getUserInfo((user, errors, messages) => {
            this.user = user as User;
        });
    }
    
    readBaseUrl() : void {
        this.labbcatService.labbcat.getId((url, errors, messages) => {
            this.baseUrl = url;
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
        });
    }

    readTranscript() : void {
        const structuralLayerIds = [
            this.schema.participantLayerId,
            "main_participant",
            this.schema.turnLayerId,
            this.schema.utteranceLayerId,
            this.schema.wordLayerId
        ];
        this.labbcatService.labbcat.getTranscript(
            this.id, structuralLayerIds, (transcript, errors, messages) => {
                this.loading = false;
                if (errors) errors.forEach(m => this.messageService.error(m));
                if (messages) messages.forEach(m => this.messageService.info(m));
                if (!transcript) {
                    console.error("Invalid transcript ID");
                    this.messageService.error("Invalid transcript ID"); // TODO i18n
                } else { // valid transcript
                    this.transcript = this.labbcatService.annotationGraph(transcript);
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
                        this.labbcatService.labbcat.countAnnotations(
                            this.transcript.id, l, (count, errors, messages) => {
                                if (!count) { // no annotations in this layer
                                    this.layerStyles[l] = { color: "silver" };
                                }
                            });
                    } // next temporal layer
                } // valid transcript
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
            // initialise links to annotations
            anchor.startOf = {};
            anchor.startOf[turnLayerId] = [];
            anchor.startOf[utteranceLayerId] = [];
            anchor.startOf[wordLayerId] = [];
            anchor.endOf = {};
            anchor.endOf[turnLayerId] = [];
            anchor.endOf[utteranceLayerId] = [];
            anchor.endOf[wordLayerId] = [];
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
                        && u < utterances.length) {
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
            let lastUtterance = this.utterances[0];
            this.temporalBlocks.push(currentBlock);
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
                if (utterance.start.offset < lastUtterance.end.offset) {
                    // this is a simultaneous speech block, so don't start a new one
                    newBlock = false;
                }
                if (newBlock) {
                    currentTurnId = utterance.parentId;
                    currentBlock = { consecutive : consecutive, utterances : [] };
                    this.temporalBlocks.push(currentBlock);
                }
                currentBlock.utterances.push(utterance);
                
                lastUtterance = utterance;
            } // next utterance
        } // there are utterances
    }

    layersChanged(selectedLayerIds : string[]) : void {
        const addedLayerIds = selectedLayerIds.filter((x)=>this.selectedLayerIds.indexOf(x) < 0);
        const loadingLayers = [] as Promise<string>[];
        const wordLayerId = this.schema.wordLayerId;
        this.loading = true;
        // load layers one at a time
        for (let layerId of addedLayerIds) {
            loadingLayers.push(new Promise((resolve, reject) => {

                if (this.transcript.layers[layerId]) { // have already loaded this layer
                    resolve(layerId);
                } else {
                    // load the layer definition
                    const layer = this.schema.layers[layerId];
                    this.transcript.schema.layers[layerId] = layer;
                    layer.parent = this.transcript.schema.layers[layer.parentId];
                    layer.colour = this.stringToColour(layerId);
                    this.layerStyles[layerId] = {
                        color : layer.colour, borderColour: layer.colour };
                    
                    // load annotations
                    this.labbcatService.labbcat.getAnnotations(
                        this.transcript.id, layerId, (annotations, errors, messages) => {
                            if (errors) errors.forEach(m => 
                                this.messageService.error(`${layerId}: ${m}`));
                            if (messages) messages.forEach(m =>
                                this.messageService.info(`${layerId}: ${m}`));
                            const unknownAnchorIds = [] as string[];
                            for (let a of annotations) {
                                const annotation = new this.labbcatService.ag.Annotation(
                                    layerId, a.label, this.transcript, a.startId, a.endId,
                                    a.id, a.parentId)
                                if (!this.transcript.anchors[a.startId]) {
                                    unknownAnchorIds.push(a.startId);
                                }
                                if (!this.transcript.anchors[a.endId]) {
                                    unknownAnchorIds.push(a.endId);
                                }
                                this.transcript.addAnnotation(annotation);
                            } // next annotation
                            
                            if (unknownAnchorIds.length) {
                                // there might be a lot of anchors to load,
                                // making one request too large
                                // so we break the anchor list into chunks
                                this.loadAnchorsIncrementally(unknownAnchorIds).then(()=>{
                                    // phrase/span layers index the token words they contain
                                    if (this.isSpanningLayer(layer)) {
                                        this.indexTokensOnLayer(layer);
                                    } // phrase/spanning layer
                                    resolve(layerId);
                                });
                            } else { // all anchors are already loaded
                                // phrase/span layers index the token words they contain
                                if (layer.parentId == this.schema.turnLayerId
                                    || (layer.parentId == this.schema.root.id
                                        && layer.alignment > 0)) {
                                    this.indexTokensOnLayer(layer);
                                } // phrase/spanning layer
                                
                                resolve(layerId);
                            }                                                        
                        });
                }
            }));
        } // next newly selected layer
        Promise.all(loadingLayers).then(()=>{
            this.loading = false;
            // set attribute to refresh visualization
            this.selectedLayerIds = selectedLayerIds;
        });
    }

    /** recursive anchor loading, to prevent requests from becoming too large */
    loadAnchorsIncrementally(unknownAnchorIds : string[]) : Promise<void> {
        const maxIds = 150;
        return new Promise<void>((resolve, reject) => {
            let idsToLoadNow = unknownAnchorIds.slice(0, maxIds);
            let idsToLoadLater = unknownAnchorIds.slice(maxIds);
            this.labbcatService.labbcat.getAnchors(
                this.transcript.id, idsToLoadNow, (anchors, errors, messages) => {
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
                    if (idsToLoadLater.length) {
                        this.loadAnchorsIncrementally(idsToLoadLater).then(resolve);
                    } else { // finished!
                        resolve();
                    }
                });
        });
    }
    
    indexTokensOnLayer(layer : Layer) : void {
        const wordLayerId = this.schema.wordLayerId;
        for (let span of this.transcript.all(layer.id)) {
            if (!span[wordLayerId]) {
                const tokens = [];
                // link it to all the words it contains
                for (let token of span.all(wordLayerId)) {
                    tokens.push(token);
                    // and linkeach word with the span
                    if (!token[layer.id]) token[layer.id] = [];
                    token[layer.id].push(span);
                } // next contained token
                span[wordLayerId] = tokens;
                // were there any?
                if (span[wordLayerId].length == 0) { // no tokens
                    // TODO make sure it appears somewhere
                    console.log("could not visualize: " +span+" ("+span.start+"-"+span.end+")");
                }
            } // not already done
        } // next span
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
    /** Test whether the layer is word scope layer */
    isWordLayer(layer : Layer) : boolean {
        return layer.parentId == this.schema.wordLayerId && layer.id != "segment";
    }
    /** Test whether the layer is segment scope layer */
    isSegmentLayer(layer : Layer) : boolean {
        return layer.parentId == "segment" || layer.id == "segment";
    }
}

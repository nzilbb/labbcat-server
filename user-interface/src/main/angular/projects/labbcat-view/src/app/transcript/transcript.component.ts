import { Component, OnInit, Inject } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';

import { SerializationDescriptor } from '../serialization-descriptor';
import { Response, Layer, User, Annotation, Anchor } from 'labbcat-common';
import { MessageService, LabbcatService } from 'labbcat-common';

// TODO optionally hide empty layers
// TODO word menu
// TODO media
// TODO remember layer selections

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
    originalFile : string;
    loading = true;
    transcript : any;

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
                this.id = params["id"]||params["transcript"]||params["ag_id"];
                this.readTranscript().then(()=>{ // some have to wait until transcript is loaded
                    // preselect layers?
                    let layerIds = params["layerId"]||params["l"]
                    if (!layerIds && sessionStorage.getItem("selectedLayerIds")) {
                        layerIds = JSON.parse(sessionStorage.getItem("selectedLayerIds"));
                    }
                    if (layerIds) {
                        if (Array.isArray(layerIds)) {
                            this.layersChanged(layerIds);
                        } else {
                            this.layersChanged([ layerIds ]);
                        }
                    }
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
                this.categoryLabels = ["Participants", "Layers", "Formats"]; // TODO i18n
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
                        if (layer.id == "transcript_type") layer.category = "General";
                        
                        if (!this.categoryLayers[layer.category]) {
                            this.categoryLayers[layer.category] = [];
                            this.categoryLabels.push(layer.category);
                        }
                        this.categoryLayers[layer.category].push(layer);
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

    readCategories(): Promise<void> {
        this.categories = {};
        return new Promise((resolve, reject) => {
            this.labbcatService.labbcat.readOnlyCategories(
                "transcript", (categories, errors, messages) => {
                    for (let category of categories) {
                        if (!category.description) {
                            category.description = `Attributes: ${category.category}`; // TODO i18n
                        }
                        this.categories[category.category] = category;
                    }
                    // extra pseudo categories
                    this.categories["Layers"] = { // TODO i18n
                        description: "Annotation layers for display"}; // TODO i18n
                    this.categories["Participants"] = { // TODO i18n
                        description: "The participants in the transcript"}; // TODO i18n
                    this.categories["Formats"] = { // TODO i18n
                        description: "Export the transcript in a selected format"}; // TODO i18n
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
            this.schema.wordLayerId
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
                                    if (count) { // annotations in this layer
                                        // remove grey-out style
                                        this.schema.layers[l].description += ` (${count})`;
                                        this.layerStyles[l] = {};
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

        if (window.location.hash) {
            window.setTimeout(()=>{ // give time for the page to render
                this.highlight(window.location.hash.substring(1));
            }, 500);
        }
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
            // and remember the selections for next time
            sessionStorage.setItem("selectedLayerIds", JSON.stringify(this.selectedLayerIds));
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
        // spans can overlap (e.g. n-gram annotations, syntactic parses)
        // we want
        //  a) each span to be visualised at the same height across all tokens, and
        //  b) the visualisation to be visually compact
        // so, we have each words' span array use the array index to determine the height,
        // and compress the indices so that spans fit into available gaps

        // process them by parent, so that phrases from one speaker can't interfere with
        // those of another speaker
        for (let parent of this.transcript.all(layer.parentId)) {
            const spans = parent.all(layer.id)
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
            // now drop spans so that they're as near as possible to the tokens they annotate
            if (spans.length < 1000) { // but not for lots of number of spans, coz it'll be slow
                let keepScanning = true;
                while (keepScanning) {
                    keepScanning = false;
                    for (let span of spans) {
                        let newDepth = maxDepth;
                        for (let otherSpan of spans) {
                            if (otherSpan != span // not the same span
                                && span.overlaps(otherSpan) // overlapping
                                && otherSpan._depth > span._depth) { // deeper than this one
                                // maybe drop to above that span
                                newDepth = Math.min(newDepth, otherSpan._depth - 1);
                            }                    
                        } // next other span
                        if (newDepth > span._depth) {
                            span._depth = newDepth;
                            keepScanning = true;
                        } // changed depth
                    } // next span
                } // next scan
            }
    
            // link words to spans that contain them
            const wordLayerId = this.schema.wordLayerId;
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
                    if (span[wordLayerId].length == 0) { // no tokens included
                        let nearestWord = null;
                        
                        if (span.end.startOf[wordLayerId] // immediately precedes a word?
                            && span.end.startOf[wordLayerId].length) {
                            nearestWord = span.end.startOf[wordLayerId][0];
                        } else if (span.start.endOf[wordLayerId] // immediately follows a word?
                            && span.start.endOf[wordLayerId].length) {
                            nearestWord = span.start.endOf[wordLayerId][0];
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
                        } else {
                            console.error(`Could not visualize: ${span.label}#${span.id} (${span.start}-${span.end})`);
                        }
                    } // no tokens included
                } // not already done
            } // next span
        } // next parent
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

    /** Highlight the annotation with the given ID */
    highlitId: string;
    highlight(id: string): void {
        this.highlitId = id;
        document.getElementById(id).scrollIntoView();
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

    /* convert selectedLayerIds array into a series of URL parameters with the given name */
    selectedLayerIdParameters(parameterName: string): string {
        return this.selectedLayerIds
            .map(layerId => "&"+parameterName+"="+encodeURIComponent(layerId))
            .join("");
    }
}

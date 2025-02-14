import { Component, OnInit, Inject } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';

import { SerializationDescriptor } from '../serialization-descriptor';
import { Response, Layer, User, Annotation, Anchor } from 'labbcat-common';
import { MessageService, LabbcatService } from 'labbcat-common';

@Component({
  selector: 'app-transcript',
  templateUrl: './transcript.component.html',
  styleUrl: './transcript.component.css'
})
export class TranscriptComponent implements OnInit {
    
    schema: any;
    user: User;
    baseUrl: string;
    imagesLocation: string;
    id: string;
    loaded = false;
    transcript : any;
    anchors : { [key: string] : Anchor };
    annotations : { [key: string] : Annotation };
    participants : Annotation[];
    utterances : Annotation[];
    words : Annotation[];

    temporalBlocks : { consecutive : boolean, utterances : Annotation[] }[];
    
    serializers: SerializationDescriptor[];
    mimeTypeToSerializer = {};

    constructor(
        private labbcatService: LabbcatService,
        private messageService: MessageService,
        private route: ActivatedRoute,
        private router: Router,
        @Inject('environment') private environment
    ) {
        this.imagesLocation = this.environment.imagesLocation;
    }
    
    ngOnInit(): void {        
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
    
    readSchema(): Promise<void> {
        return new Promise((resolve, reject) => {
            this.labbcatService.labbcat.getSchema((schema, errors, messages) => {
                this.schema = schema;
                this.schema.root.description = "Transcript";
                resolve();
            });
        });
    }
    
    readUserInfo(): void {
        this.labbcatService.labbcat.getUserInfo((user, errors, messages) => {
            this.user = user as User;
        });
    }
    
    readBaseUrl(): void {
        this.labbcatService.labbcat.getId((url, errors, messages) => {
            this.baseUrl = url;
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
        });
    }

    readTranscript(): void {
        const structuralLayerIds = [
            this.schema.participantLayerId,
            "main_participant",
            this.schema.turnLayerId,
            this.schema.utteranceLayerId,
            this.schema.wordLayerId
        ];
        this.labbcatService.labbcat.getTranscript(
            this.id, structuralLayerIds, (transcript, errors, messages) => {
                this.loaded = true;
                if (errors) errors.forEach(m => this.messageService.error(m));
                if (messages) messages.forEach(m => this.messageService.info(m));
                if (!transcript) {
                    console.error("Invalid transcript ID");
                    this.messageService.error("Invalid transcript ID"); // TODO i18n
                } else { // valid transcript
                    this.transcript = transcript;
                    this.parseTranscript();
                } // valid transcript
            });       
    }

    parseTranscript(): void {
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
        for (let p in this.transcript.participant) {
            const participant = this.transcript.participant[p];
            participant.layerId = participantLayerId;
            participant.ordinal = p+1; // set ordinal
            participant.annotations = []
            participant.annotations[turnLayerId] = participant.turn;
            participant.annotations["main_participant"] = participant.main_participant;
            this.annotations[participant.id] = participant as Annotation;
            this.participants.push(participant as Annotation);            
            // for each turn
            for (let t in participant.turn) {
                const turn = participant.turn[t];
                turn.layerId = turnLayerId;
                turn.parentId = participant.id;
                turn.ordinal = t+1; // set ordinal
                turn.annotations = {};
                turn.annotations[utteranceLayerId] = turn.utterance;
                turn.annotations[wordLayerId] = turn.word;
                this.annotations[turn.id] = turn as Annotation;
                // link to anchors
                this.anchors[turn.startId].startOf[turnLayerId].push(turn);
                this.anchors[turn.endId].endOf[turnLayerId].push(turn);
                
                // for each utterance
                for (let u in turn.utterance) {
                    const utterance = turn.utterance[u];
                    utterance.layerId = utteranceLayerId;
                    utterance.parentId = turn.id;
                    utterance.ordinal = u+1; // set ordinal
                    this.annotations[utterance.id] = utterance as Annotation;
                    // we're going to link words to utterances
                    utterance.annotations = {};
                    utterance.annotations[wordLayerId] = [];
                    // link to anchors
                    this.anchors[utterance.startId].startOf[utteranceLayerId].push(utterance);
                    this.anchors[utterance.endId].endOf[utteranceLayerId].push(utterance);
                    
                    this.utterances.push(utterance as Annotation);
                } // next utterance

                // parse words and distribute them into utterances
                let u = 0;
                const utterances = turn.utterance as Annotation[];
                // for each word
                for (let w in turn.word) {
                    const word = turn.word[w];
                    word.layerId = wordLayerId;
                    word.parentId = turn.id;
                    word.ordinal = w+1; // set ordinal
                    this.annotations[word.id] = word as Annotation;
                    // link to anchors
                    this.anchors[word.startId].startOf[wordLayerId].push(word);
                    this.anchors[word.endId].endOf[wordLayerId].push(word);
                    
                    this.words.push(word as Annotation);

                    // add to the current utterance
                    const wordStart = this.startOffset(word);
                    // if the words starts after utterance u ends, increment
                    while (wordStart >= this.endOffset(utterances[u])
                        && u < utterances.length) {
                        u++;
                    }
                    utterances[u].annotations[wordLayerId].push(word);
                } // next word
            } // next turn
        } // next participant

        // now sort utterances by start offset, across participants
        this.utterances.sort((a,b) => this.startOffset(a) - this.startOffset(b));

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
                    && this.startOffset(nextUtterance) < this.endOffset(utterance)) {
                    newBlock = true;
                    consecutive = false;
                }
                // but if this is during the last utterance
                if (this.startOffset(utterance) < this.endOffset(lastUtterance)) {
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

    /** Get start offset of the given annotation. */
    startOffset(annotation : Annotation) : number {
        if (!annotation) return null;
        const anchor = this.anchors[annotation.startId];
        if (!anchor) return null;
        return anchor.offset;
    }
    /** Get end offset of the given annotation. */
    endOffset(annotation : Annotation) : number {
        if (!annotation) return null;
        const anchor = this.anchors[annotation.endId];
        if (!anchor) return null;
        return anchor.offset;
    }

    /** Infer whether this temporal block is simultaneous or consecutive */ 
}

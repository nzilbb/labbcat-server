import { Component, OnInit, Inject } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';

import { EditComponent } from '../edit-component';
import { MessageService, LabbcatService, MediaFile, Annotation } from 'labbcat-common';

@Component({
    selector: 'app-transcript-media',
    templateUrl: './transcript-media.component.html',
    styleUrls: ['./transcript-media.component.css']
})
export class TranscriptMediaComponent extends EditComponent implements OnInit {
    
    baseUrl: string;
    imagesLocation : string;
    id: string;
    loaded = false;
    tracks: object[]; // suffix, description
    trackUpload: object; // trackSuffix -> upload state ("", "show", "upload")
    fileDeleting = {} // fileName -> boolean
    mediaFiles: MediaFile[];
    mediaFilesByTrack: object; // string->MediaFile[]
    // for editing the audio prompt
    graph: any;
    audioPrompt: Annotation;
    savingPrompt = false;

    constructor(
        labbcatService: LabbcatService,
        messageService: MessageService,
        private route: ActivatedRoute,
        private router: Router,
        @Inject('environment') private environment
    ) {
        super(labbcatService, messageService);
        this.imagesLocation = this.environment.imagesLocation;
    }
    
    ngOnInit(): void {
        this.route.queryParams.subscribe((params) => {
            this.id = params["id"]
            if (!this.id) this.id = "(No id specified)"
            Promise.all([
                this.readBaseUrl(),
                this.readTracks(),
                this.readMedia(),
                this.readAudioPrompt()
            ]).then(()=>{
                this.loaded = true;
            })
        });
    }
    readBaseUrl(): Promise<void> {
        return new Promise<void>((resolve, reject) => {
            this.labbcatService.labbcat.getId((url, errors, messages) => {
                if (errors) {
                    errors.forEach(m => this.messageService.error(m));
                    reject();
                    return;
                }
                if (messages) {
                    messages.forEach(m => this.messageService.info(m));
                }
                this.baseUrl = url;
                resolve();
            });
        });
    }
    readTracks(): Promise<void> {
        return new Promise<void>((resolve, reject) => {
            this.labbcatService.labbcat.getMediaTracks((tracks, errors, messages) => {
                if (errors) {
                    errors.forEach(m => this.messageService.error(m));
                    reject();
                    return;
                }
                if (messages) {
                    messages.forEach(m => this.messageService.info(m));
                }
                this.tracks = tracks;
                this.trackUpload = {};
                resolve();
            });
        });
    }
    readAudioPrompt(): Promise<void> {
        return new Promise<void>((resolve, reject) => {
            this.labbcatService.labbcat.getTranscript(
                this.id, ["audio_prompt"], (graph, errors, messages) => {
                    if (errors) {
                        errors.forEach(m => this.messageService.error(m));
                        reject();
                        return;
                    }
                    if (messages) {
                        messages.forEach(m => this.messageService.info(m));
                    }
                    this.graph = graph;
                    if (!this.graph.audio_prompt) { // no audio_prompt values
                        this.graph.audio_prompt = [];
                    }
                    if (!this.graph.audio_prompt[0]) { // no audio_prompt annotation
                        // create a dummy audio_prompt setting for binding to
                        this.graph.audio_prompt[0] = {
                            id: "audio_prompt", 
                            layerId: "audio_prompt", 
                            label: "" 
                        };
                    }
                    this.audioPrompt = this.graph.audio_prompt[0];
                    resolve();
                });
        });
    }
    readMedia(): Promise<void> {
        return new Promise<void>((resolve, reject) => {
            this.labbcatService.labbcat.getAvailableMedia(
                this.id, (availableMedia: MediaFile[], errors, messages) => {
                    if (errors) {
                        errors.forEach(m => this.messageService.error(m));
                        reject();
                        return;
                    }
                    if (messages) {
                        messages.forEach(m => this.messageService.info(m));
                    }
                    this.mediaFiles = [];
                    this.mediaFilesByTrack = {};
                    for (let media of availableMedia) {
                        if (!media.generateFrom) {
                            this.mediaFiles.push(media);
                            if (!this.mediaFilesByTrack[media.trackSuffix]) {
                                this.mediaFilesByTrack[media.trackSuffix] = [];
                            }
                            this.mediaFilesByTrack[media.trackSuffix].push(media);
                        } // file already exists
                    } // next media
                    resolve();
                });
        });
    }
    trackSuffixes(): string[] {
        return Object.keys(this.mediaFilesByTrack);
    }
    upload(file: any, suffix: string): void {
        this.trackUpload[suffix] = "uploading";
        this.labbcatService.labbcat.saveMedia(
            this.id, file, suffix, (result, errors, messages) => {
                this.trackUpload[suffix] = "";
                if (errors) {
                    errors.forEach(m => this.messageService.error(m));
                }
                if (messages) {
                    messages.forEach(m => this.messageService.info(m));
                }
                this.readMedia();
            });
    }
    deleteFile(fileName: string): void {
        if (confirm(`Are you sure you want to delete ${fileName}?`)) { // TODO i18n
            this.fileDeleting[fileName] = true;
            this.labbcatService.labbcat.deleteMedia(
                this.id, fileName, (result, errors, messages) => {
                    this.fileDeleting[fileName] = false;
                    if (errors) {
                        errors.forEach(m => this.messageService.error(m));
                    }
                    if (messages) {
                        messages.forEach(m => this.messageService.info(m));
                    }
                    this.readMedia();
                });
        }
    }
    savePrompt(): void {
        this.savingPrompt = true;
        this.labbcatService.labbcat.saveTranscript(
            this.graph, (result, errors, messages) => {
                if (errors) {
                    errors.forEach(m => this.messageService.error(m));
                }
                if (messages) {
                    messages.forEach(m => this.messageService.info(m));
                }
                this.audioPrompt._changed = false;
                this.savingPrompt = false;
            });
    }
}

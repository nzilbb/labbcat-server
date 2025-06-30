import { Component, OnInit, Inject } from '@angular/core';

import { EditComponent } from '../edit-component';
import { UploadEntry } from '../upload-entry';
import { MessageService, LabbcatService, MediaFile, Annotation, Layer,
         SerializationDescriptor } from 'labbcat-common';

// TODO batch change of corpus/episode/type
@Component({
  selector: 'app-transcript-upload',
  templateUrl: './transcript-upload.component.html',
  styleUrl: './transcript-upload.component.css'
})
export class TranscriptUploadComponent extends EditComponent implements OnInit {
    baseUrl: string;
    imagesLocation : string;
    tracks: object[]; // suffix, description
    corpora: string[];
    defaultCorpus: string;
    transcriptTypes: string[];
    defaultTranscriptType: string;
    generateLayers = true;
    useDefaultParameterValues = false;
    deserializers: { [extension: string] : SerializationDescriptor };
    fileSelector: FileList;
    entries: UploadEntry[];
    existingEntries = false;
    newEntries = false;
    hovering = false;
    processing = false;
    followProgress = true;
    
    constructor(
        labbcatService: LabbcatService,
        messageService: MessageService,
        @Inject('environment') private environment
    ) {
        super(labbcatService, messageService);
        this.imagesLocation = this.environment.imagesLocation;
        this.entries = [];
    }

    ngOnInit(): void {
        this.readBaseUrl();
        this.readTracks();
        this.readCorpora();
        this.readTranscriptTypes();
        this.readDeserializers();
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
                resolve();
            });
        });
    }
    readCorpora(): Promise<void> {
        return new Promise<void>((resolve, reject) => {
            this.labbcatService.labbcat.getLayer("corpus", (layer, errors, messages) => {
                if (errors) {
                    errors.forEach(m => this.messageService.error(m));
                    reject();
                    return;
                }
                if (messages) {
                    messages.forEach(m => this.messageService.info(m));
                }
                this.corpora = Object.keys(layer.validLabels);
                this.defaultCorpus = this.corpora[0];
                resolve();
            });
        });
    }
    readTranscriptTypes(): Promise<void> {
        return new Promise<void>((resolve, reject) => {
            this.labbcatService.labbcat.getLayer("transcript_type", (layer, errors, messages) => {
                if (errors) {
                    errors.forEach(m => this.messageService.error(m));
                    reject();
                    return;
                }
                if (messages) {
                    messages.forEach(m => this.messageService.info(m));
                }
                this.transcriptTypes = Object.keys(layer.validLabels);
                this.defaultTranscriptType = this.transcriptTypes[0];
                resolve();
            });
        });
    }
    readDeserializers(): Promise<void> {
        return new Promise<void>((resolve, reject) => {
            this.labbcatService.labbcat.getDeserializerDescriptors((descriptors: SerializationDescriptor[], errors, messages) => {
                if (errors) {
                    errors.forEach(m => this.messageService.error(m));
                    reject();
                    return;
                }
                if (messages) {
                    messages.forEach(m => this.messageService.info(m));
                }
                this.deserializers = {};
                for (let descriptor of descriptors) {
                    for (let extension of descriptor.fileSuffixes) {
                        this.deserializers[extension.toLowerCase()] = descriptor;
                    }
                } 
                resolve();
            });
        });
    }

    updateTranscriptExistence(): void {
        this.existingEntries = this.entries.find(e=>e.exists) != null;
        this.newEntries = this.entries.find(e=>!e.exists) != null;
    }

    processingEntries = false;
    entriesForExistenceCheck: UploadEntry[] = [];
    // start checking for the existence of transcript entries
    checkTranscriptExistence(): void {
        this.entriesForExistenceCheck = this.entries.filter(e=>e.exists == null);
        setTimeout(()=>this.checkNextTranscript(), 1000);
    }
    // recursively check the next entry for existence
    checkNextTranscript() : void {
        if (this.entriesForExistenceCheck.length > 0) {
            this.processingEntries = true;
            const entry = this.entriesForExistenceCheck.shift();
            // check whether it exists TODO this doesn't work well with hundreds of files at once
            this.labbcatService.labbcat.getTranscript(
                entry.transcript.name, ["transcript", "corpus", "episode", "transcript_type"],
                (transcript, errors, messages) => {
                    if (!errors) {
                        entry.exists = true;
                        entry.status = "Already exists"; // TODO i18n
                        entry.transcriptId = transcript.id;
                        entry.corpus = transcript.corpus[0].label;
                        entry.episode = transcript.episode[0].label;
                        entry.transcriptType = transcript.transcript_type[0].label;
                    } else {
                        entry.exists = false;
                    }
                    this.checkNextTranscript();
                });
        } else { // no more entries to check
            this.processingEntries = false;
            this.updateTranscriptExistence();
        }
    }
    
    chooseFile(event): void {
        this.processingEntries = true;
        const processItems = [] as Promise<void>[];
        for (let file of event.target.files) {
            processItems.push(
                this.parseFile(file, null));
        } // next file
        // unset file selector
        event.target.value = null;

        Promise.all(processItems).then(()=>{
            // check for existence after all files handled
            setTimeout(() => { // let the UI update first
                this.checkTranscriptExistence(); },
                       500);
        });
    }

    // file drag hover
    fileDragHover(e: Event): void {
        e.stopPropagation();
        e.preventDefault();
        if (this.processing) return;
        this.hovering = e.type == "dragover";
    }

    // file selection
    fileSelectHandler(e: DragEvent): void {
        // cancel event and hover styling
        this.fileDragHover(e);

        if (this.processing) return;

        this.processingEntries = true;
        
        if (e.dataTransfer && e.dataTransfer.items) { // items including directories (i.e. chrome)  
            const items = e.dataTransfer.items;
            const processItems = [] as Promise<void>[];
            for (let i=0; i<items.length; i++) {
                const item = items[i].webkitGetAsEntry();
                if (item) {
                    processItems.push(
                        this.parseItem(item, null));
                }
            } // next item
            Promise.all(processItems).then(()=>{
                // check for existence after all files handled
                setTimeout(() => { // let the UI update first
                    this.checkTranscriptExistence(); },
                           500);
            });
        }
        //  document.getElementById("fileselect").value = null;
        // provoke the UI to refresh to show the entries...
        setTimeout(() => { this.entries = this.entries; }, 100);
        
    }
    
    parseItem(item: FileSystemEntry, path: string): Promise<void> {
        return new Promise<void>((resolve, reject) => {
            const subitemPromises = [] as Promise<void>[];
            path = path || "";
            if (item.isFile) {
                const fileEntry = item as FileSystemFileEntry;
                // Get file
                fileEntry.file((file: File) => {                
                    subitemPromises.push(
                        this.parseFile(file, path))
                    ;                        
                });
            } else if (item.isDirectory) {
                const dirEntry = item as FileSystemDirectoryEntry;
                // Get folder contents
                const dirReader = dirEntry.createReader();
                const component = this;
                dirReader.readEntries(function(entries) {
                    entries.sort((i1, i2) => {
                        if ( i1.name < i2.name ) return -1;
                        if ( i1.name > i2.name ) return 1;
                        return 0;});
                    for (let i in entries) {
                        subitemPromises.push(
                            component.parseItem(entries[i], path + item.name + "/"));
                    }
                });
            }
            Promise.all(subitemPromises).then(()=>resolve());
        }); // Promise
    }

    parseFile(file: File, path: string): Promise<void> {
        return new Promise<void>((resolve, reject) => {
            const extension = file.name.replace(/^.*(\.[^.]+)$/, "$1").toLowerCase();
            if (extension == ".csv") { // usually uploading csv files as transcripts is a mistake
                // if there are other transcripts that are not csv, then ignore this file
                const nonCsvTranscript = this.entries.find(
                    e=>e.transcript && !e.transcript.name.endsWith(".csv"));
                if (nonCsvTranscript) {
                    resolve();
                    return;
                }
            }
            const descriptor = this.deserializers[extension];
            if (descriptor) {
                this.addTranscript(file, descriptor, path);
            } else if (file.type) {
                if (file.type.startsWith("audio") 
                    || file.type.startsWith("video") 
                    || file.type.startsWith("image")) {
                    this.addMedia(file);
                }
            } else if (file.name.endsWith(".wav")
                || file.name.endsWith(".mp3")
                || file.name.endsWith(".jpg")
                || file.name.endsWith(".gif")
                || file.name.endsWith(".png")
                || file.name.endsWith(".mp4")
                || file.name.endsWith(".mpeg")
                || file.name.endsWith(".avi")) {
                this.addMedia(file);
            }
            resolve();
        }); // Promise
    }

    addTranscript(file: File, deserializer: SerializationDescriptor, path: string) {
        const id = this.stripExtension(file.name);
        
        const entry = this.getEntry(id);
        // set the transcript file
        entry.transcript = file;
        entry.descriptor = deserializer;
        // assume (for now) the file name is the graph ID
        entry.transcriptId = file.name;
        // default values to start
        entry.corpus = this.defaultCorpus;
        entry.transcriptType = this.defaultTranscriptType;
        entry.episode = entry.id;
        // if there's a path
        if (path) {
            // set the series to the folder name
            var dirs = path.split("/");
            var s = dirs.length - 1;
            if (s >= 0) {
                if (dirs[s] == "") s--;    
                if (s >= 0) {
                    if (dirs[s] == "trs") s--;
                    if (s >= 0) {
                        if (this.corpora.indexOf(dirs[s]) >= 0) {
                            // it's a corpus name, so set the corpus to that
                            entry.corpus = dirs[s];
                        } else {
                            // assume the containing directory should be the episode name
                            entry.episode = dirs[s];
                            s--;
                            if (s >= 0) {
                                // and the corpus is the episode's parent
                                if (this.corpora.indexOf(dirs[s]) >= 0) {
                                    entry.corpus = dirs[s];
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    
    addMedia(file: File): void {
        const id = this.stripExtension(file.name);
        const entry = this.getEntry(id);
        // add the media file file under its name
        entry.addMedia(file, "");
    }
    
    stripExtension(fileName: string): string {
        if (!fileName) return "";
        return fileName.replace(/\.[^.]*$/, "");
    }

    getEntry(id: string): UploadEntry {
        let entry = this.entries.find(e => e.id == id);
        if (!entry) {
            entry = new UploadEntry(id);
            entry.corpus = this.defaultCorpus;
            entry.transcriptType = this.defaultTranscriptType;
            entry.episode = entry.id;
            this.entries.push(entry);
        }
        return entry;
    }

    // button handlers
    removeEntry(id: string) {
        this.entries = this.entries.filter(e=>e.id != id);
        this.updateTranscriptExistence();
    }
    
    uploading = false;
    onUpload(): void {
        if (!this.entries.find(e => !e.uploadId || e.errors)) { // (can retry errored uploads)
            this.messageService.info(
                "All transcripts have already been uploaded."); // TODO i18n
        } else {
            if (this.existingEntries && this.newEntries
                && !confirm("Some transcripts already exist in LaBB-CAT.\nDo you want to re-upload them?")) { // TODO i18n
                if (this.newEntries
                    && confirm("Do you want to ignore the existing transcripts, and upload the others?")) { // TODO i18n
                    this.onClear(true, false);
                } else { // don't want to upload others either
                    return;
                }
            } // there are existing transcripts we don't want to upload
            
            // reset errors/statuses
            for (let entry of this.entries) entry.resetState();
            
            // start upload
            this.uploading = true;
            this.uploadNextTranscript();
        }
    }
    uploadNextTranscript(): void {
        // are there any entries that can be deleted?
        const uploadableEntry = this.entries.find(e => e.transcript && !e.uploadId);
        if (!this.uploading // cancelled
            || !uploadableEntry) { // there are no entries left to delete
            this.processing = this.uploading = false;
        } else {
            this.processing = this.uploading = true;
            uploadableEntry.progress = 0;
            const media = {};
            for (let trackSuffix in uploadableEntry.media) {
                media[trackSuffix] = [];
                for (let file of uploadableEntry.media[trackSuffix]) {
                    media[trackSuffix].push(file);
                } // next file
            } // next track

            if (this.followProgress) {                
                try { // scroll to the current entry
                    document.getElementById(uploadableEntry.transcript.name).scrollIntoView({
                        behavior: "smooth",
                        block: "center"
                    });
                } catch (x) {}
            }
            // start upload
            this.labbcatService.labbcat.transcriptUpload(
                uploadableEntry.transcript, media, uploadableEntry.exists,
                (result, errors, messages) => {
                    if (!this.uploading) { // cancelled
                        this.processing = false;
                        return;
                    }
                    if (errors) {
                        uploadableEntry.errors = uploadableEntry.errors.concat(errors);
                        if (this.useDefaultParameterValues) { // batch mode
                            // continue with next transcript
                            this.uploadNextTranscript();
                        } else { // not in batch mode, so stop here
                            this.processing = this.uploading = false;
                            return;
                        }
                    }
                    if (messages) {
                        uploadableEntry.status = messages.join("\n");
                    }
                    uploadableEntry.uploadId = result.id;
                    uploadableEntry.parameters = result.parameters || [];
                    uploadableEntry.parametersVisible = true;
                    // look for parameters we already know the answer to
                    let parameter = uploadableEntry.parameters.find(p=>p.name == "labbcat_corpus");
                    if (parameter) parameter.value = uploadableEntry.corpus;
                    parameter = uploadableEntry.parameters.find(p=>p.name == "labbcat_episode");
                    if (parameter) parameter.value = uploadableEntry.episode;
                    parameter = uploadableEntry.parameters.find(
                        p=>p.name == "labbcat_transcript_type");
                    if (parameter) {
                        parameter.value = uploadableEntry.transcriptType;
                    }
                    parameter = uploadableEntry.parameters.find(p=>p.name == "labbcat_generate");
                    if (parameter) parameter.value = this.generateLayers;

                    // are there any other parameters to show?
                    parameter = uploadableEntry.parameters.find(p=>
                        p.name != "labbcat_corpus" && p.name != "labbcat_episode"
                        && p.name != "labbcat_transcript_type"
                        && p.name != "labbcat_generate");
                    if (!parameter // no more parameters to set
                        || this.useDefaultParameterValues) { // or we're in batch mode
                        // so just keep going
                        this.uploadParameters(uploadableEntry);
                    }
                    
                }, (e)=> { // progress
                    if (e.lengthComputable) {
                        // upload goes up to 50% only
	                uploadableEntry.progress = Math.round(e.loaded * 50 / e.total);
	                uploadableEntry.status = "Uploading..."; // TODO i18n
                    }
                }); // transcriptUpload
        } // there are uploadable transcripts
    }
    // the user clicked save parameters, or we could continue without asking the user
    uploadParameters(uploadableEntry: UploadEntry): void {
        if (!this.uploading) { // cancelled
            this.processing = false;
            return;
        }
        uploadableEntry.parametersVisible = false;
        // immmediately disable the parameters button
        uploadableEntry.transcriptThreads = {};
        // send the parameter to the server
        const parameterValues = {};
        for (let parameter of uploadableEntry.parameters) {
        }
        this.labbcatService.labbcat.transcriptUploadParameters(
            uploadableEntry.uploadId, uploadableEntry.parameters, (result, errors, messages) => {
                if (messages) {
                    uploadableEntry.status = messages.join("\n");
                }
                if (errors) {
                    uploadableEntry.errors = uploadableEntry.errors.concat(errors);
                    if (!this.useDefaultParameterValues) { // not in batch mode, so stop here
                        this.processing = this.uploading = false;
                        return;
                    }
                }
                // try next transcript
                this.uploadNextTranscript();
                
                if (result) {
                    uploadableEntry.transcriptThreads = result.transcripts;
                    if (uploadableEntry.generating()) {
                        this.monitorThreads();
                    } else {
                        uploadableEntry.progress = 100;
                    }
                    // it's theoretically possible that parameters were returned
                    if (result.parameters) {
                        uploadableEntry.uploadId = result.id || uploadableEntry.uploadId;
                        uploadableEntry.parameters = result.parameters;
                    }
                }
            });
    }
    // the user clicked save parameters, or we could continue without asking the user
    skipEntry(uploadableEntry: UploadEntry, cancelling: boolean): void {
        if (!this.uploading) { // cancelled
            this.processing = false;
            return;
        }
        uploadableEntry.parametersVisible = false;
        // immmediately disable the parameters button
        uploadableEntry.transcriptThreads = {};
        // send the parameter to the server
        // cancel the upload on the server
        this.labbcatService.labbcat.transcriptUploadDelete(
            uploadableEntry.uploadId, (result, errors, messages) => {
                uploadableEntry.status = cancelling?"Cancelled.":"Skipped." // TODO i18n
                // try next transcript
                this.uploadNextTranscript();
            });
    }
    threadMonitor: number;
    monitorThreads(): void { // TODO slow when there are hunders of uploads
        if (this.threadMonitor) return; // already monitoring
        this.threadMonitor = setInterval(()=>{
            // get all task statuses
            this.labbcatService.labbcat.getTasks((tasks, errors, messages) => {
                for (let entry of this.entries) {
                    if (entry.transcriptThreads) {
                        for (let transcriptId in entry.transcriptThreads) {
                            const taskId = entry.transcriptThreads[transcriptId];
                            const task = tasks[taskId];
                            if (task) {
                                entry.progress = 50 + (task.percentComplete/2);
                                entry.status = task.status || entry.status;
                            }
                            if (!task // task is gone TODO this isn't working, polling continues
                                || !task.running) { // or not running any more
                                entry.exists = true;
                                entry.transcriptThreads = null;
                                // if all the uploads are complete
                                if (!this.processing
                                    && this.useDefaultParameterValues
                                    && !this.entries.find(e=>e.transcriptThreads)) {
                                    clearInterval(this.threadMonitor);
                                    this.threadMonitor = null;
                                    setTimeout(()=>{ // give the UI a chance to update
                                        if (confirm("Do you want an upload report?")) { // TODO i18n
                                            this.onReport();
                                        }
                                    }, 1000);
                                }
                            } // task finished
                        } // next thread
                    } // there are threads
                } // next entry
            }); // getTasks
        }, 1000); // setInterval
    }

    cancelThreads(uploadableEntry: UploadEntry): void {
        if (uploadableEntry.transcriptThreads) {
            for (let transcriptId in uploadableEntry.transcriptThreads) {
                this.labbcatService.labbcat.cancelTask(
                    uploadableEntry.transcriptThreads[transcriptId],
                    (task, errors, messages) => {
                        if (task) {
                            uploadableEntry.progress = 50 + (task.percentComplete/2);
                            uploadableEntry.status = task.status || uploadableEntry.status;
                        }
                    });
            } // next transcript (there's probably only one)
        }
    }

    // returns a status string that's 40 characters or shorter
    statusLabel(status: string): string {
        return status.length < 40?
            status
            :status.substr(0, 5)+"..."+status.substr(status.length-35,35);
    }
    
    deleting = false;
    onDelete(): void {
        if (!this.entries.find(e => e.exists)) {
            this.messageService.info(
                "None of the transcripts are currently in LaBB-CAT."); // TODO i18n
        } else if (confirm(
            "Are you sure you want to delete these transcripts from LaBB-CAT?")) { // TODO i18n
            this.deleting = true;
            this.deleteNextTranscript();
        }
    }
    
    deleteNextTranscript(): void {
        // are there any entries that can be deleted?
        const deletableEntry = this.entries.find(e => e.exists);
        if (!this.deleting // cancelled
            || !deletableEntry) { // there are no entries left to delete
            this.processing = this.deleting = false;
            this.updateTranscriptExistence();
        } else {
            this.processing = this.deleting = true;
            // clear any previous errors
            deletableEntry.errors = [];
            deletableEntry.progress = 50;
            if (this.followProgress) {
                try { // scroll to the current entry
                    document.getElementById(deletableEntry.transcript.name).scrollIntoView({
                        behavior: "smooth",
                        block: "center"
                    });
                } catch (x) {}
            }
            // delete it
            this.labbcatService.labbcat.deleteTranscript(
                deletableEntry.transcriptId, (result, errors, messages) => {
                    if (errors) {
                        deletableEntry.errors = errors;
                    }
                    if (messages) {
                        deletableEntry.status = messages.join("\n")
                        // remove transcript name from message, we already know it
                            .replace(": "+deletableEntry.transcript.name,"");
                    }
                    // mark it as no longer existing
                    deletableEntry.exists = false;
                    deletableEntry.progress = 100;
                    deletableEntry.uploadId = null;
                    deletableEntry.parameters = null;
                    deletableEntry.transcriptThreads = null;
                    // try for another one
                    this.deleteNextTranscript();
                });
        }
    }
    onCancel(): void {
        const currentlyUploadingEntry = this.entries.find(e => e.uploadId && !e.transcriptThreads);
        if (currentlyUploadingEntry) {
            this.skipEntry(currentlyUploadingEntry, true);
        }
        this.uploading = this.deleting = false;
        if (!this.useDefaultParameterValues) this.processing = false;
    }
    onReport(): void {        
        let csv = "data:text/csv;charset=utf-8,";
        csv += "Transcript,Media,Corpus,Episode,Type,Parameters,Status,Errors";
        for (let entry of this.entries) {
            csv += "\n" + (entry.transcript?entry.transcript.name:"")+","
                +"\""+entry.mediaFileNames().join("\n")+"\","
                +entry.corpus+","
                +entry.episode+","
                +entry.transcriptType+","
                +"\""+(entry.parameters?entry.parameters
                    .map(p=>p.name+"="+p.value)
                    .join("\n"):"").replace(/"/g,"'")+"\","
                +"\""+entry.status.replace(/"/g,"'")+"\","
                +"\""+(entry.errors?entry.errors.join("\n"):"").replace(/"/g,"'")+"\"";
        } // next transcript
        const encodedUri = encodeURI(csv);
        const link = document.createElement("a");
        link.setAttribute("href", encodedUri);
        const now = new Date();
        link.setAttribute("download", "batch-"+now
            .toISOString().substring(0,16).replace(/[-:]/g,"").replace("T","-")
            +".csv");
        link.style.display = "none";
        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link);
    }
    onClear(existingTranscripts: boolean, newTranscripts: boolean): void { // TODO also add clear entries with no media, entries with no transcript, selected entries
        if (existingTranscripts && newTranscripts) {
            this.entries = [];
        } else if (existingTranscripts) {
            this.entries = this.entries.filter(e=>!e.exists);
        } else if (newTranscripts) {
            this.entries = this.entries.filter(e=>e.exists);
        }
        this.updateTranscriptExistence();
    }
}

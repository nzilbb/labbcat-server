import { Component, OnInit, Inject } from '@angular/core';

import { EditComponent } from '../edit-component';
import { UploadEntry } from '../upload-entry';
import { MessageService, LabbcatService, MediaFile, Annotation, Layer,
         SerializationDescriptor } from 'labbcat-common';

@Component({
  selector: 'app-fragment-upload',
  templateUrl: './fragment-upload.component.html',
  styleUrl: './fragment-upload.component.css'
})
export class FragmentUploadComponent extends EditComponent implements OnInit {
    baseUrl: string;
    imagesLocation : string;
    generateLayers = true;
    useDefaultParameterValues = false;
    deserializers: { [extension: string] : SerializationDescriptor };
    fileSelector: FileList;
    entries: UploadEntry[];
    hovering = false;
    processing = false;
    processingEntries = false;
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
            this.processingEntries = false;
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
                this.processingEntries = false;
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
            if (extension == ".csv") { // usually uploading csv files as fragments is a mistake
                // if there are other fragments that are not csv, then ignore this file
                const nonCsvFragment = this.entries.find(
                    e=>e.transcript && !e.transcript.name.endsWith(".csv"));
                if (nonCsvFragment) {
                    resolve();
                    return;
                }
            }
            const descriptor = this.deserializers[extension];
            if (descriptor) {
                this.addFragment(file, descriptor, path);
            }
            resolve();
        }); // Promise
    }

    addFragment(file: File, deserializer: SerializationDescriptor, path: string) {
        const id = this.stripExtension(file.name);
        
        const entry = this.getEntry(id);
        // set the fragment file
        entry.transcript = file;
        entry.descriptor = deserializer;
        // assume (for now) the file name is the graph ID
        entry.transcriptId = file.name;
        // default values to start
        entry.episode = entry.id;
    }
        
    stripExtension(fileName: string): string {
        if (!fileName) return "";
        return fileName.replace(/\.[^.]*$/, "");
    }

    getEntry(id: string): UploadEntry {
        let entry = this.entries.find(e => e.id == id);
        if (!entry) {
            entry = new UploadEntry(id);
            entry.episode = entry.id;
            this.entries.push(entry);
        }
        return entry;
    }

    // button handlers
    removeEntry(id: string) {
        this.entries = this.entries.filter(e=>e.id != id);
    }
    
    uploading = false;
    onUpload(): void {
        if (!this.entries.find(e => !e.uploadId || e.errors)) { // (can retry errored uploads)
            this.messageService.info(
                "All fragments have already been uploaded."); // TODO i18n
        } else {
            // reset errors/statuses
            for (let entry of this.entries) entry.resetState();
            
            // start upload
            this.uploading = true;
            this.uploadNextFragment();
        }
    }
    uploadNextFragment(): void {
        // are there any entries that can be uploaded?
        const uploadableEntry = this.entries.find(e => e.transcript && !e.uploadId);
        if (!this.uploading // cancelled
            || !uploadableEntry) { // there are no entries left to upload
            this.processing = this.uploading = false;
        } else {
            this.processing = this.uploading = true;
            uploadableEntry.progress = 0;

            if (this.followProgress) {                
                try { // scroll to the current entry
                    document.getElementById(uploadableEntry.transcript.name).scrollIntoView({
                        behavior: "smooth",
                        block: "center"
                    });
                } catch (x) {}
            }
            // start upload
            this.labbcatService.labbcat.fragmentUpload(
                uploadableEntry.transcript, false,
                (result, errors, messages) => {
                    if (!this.uploading) { // cancelled
                        this.processing = false;
                        return;
                    }
                    if (errors) {
                        uploadableEntry.errors = uploadableEntry.errors.concat(errors);
                        if (this.useDefaultParameterValues) { // batch mode
                            // continue with next fragment
                            this.uploadNextFragment();
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
                    // are there parameters to show?
                    if (uploadableEntry.parameters.length == 0 // no parameters to set
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
                }); // fragmentUpload
        } // there are uploadable fragments
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
        this.labbcatService.labbcat.fragmentUploadParameters(
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
                this.uploadNextFragment();
                
                if (result) {
                    uploadableEntry.progress = 100;
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
        this.labbcatService.labbcat.fragmentUploadDelete(
            uploadableEntry.uploadId, (result, errors, messages) => {
                uploadableEntry.status = cancelling?"Cancelled.":"Skipped." // TODO i18n
                // try next fragment
                this.uploadNextFragment();
            });
    }
    // returns a status string that's 40 characters or shorter
    statusLabel(status: string): string {
        return status.length < 40?
            status
            :status.substr(0, 5)+"..."+status.substr(status.length-35,35);
    }
    
    onCancel(): void {
        const currentlyUploadingEntry = this.entries.find(e => e.uploadId && !e.transcriptThreads);
        if (currentlyUploadingEntry) {
            this.skipEntry(currentlyUploadingEntry, true);
        }
        this.uploading = false;
        if (!this.useDefaultParameterValues) this.processing = false;
    }
    onReport(): void {        
        let csv = "Fragment,Parameters,Status,Errors";
        for (let entry of this.entries) {
            csv += "\n" + (entry.transcript?entry.transcript.name:"")+","
                +"\""+(entry.parameters?entry.parameters
                    .map(p=>p.name+"="+p.value)
                    .join("\n"):"").replace(/"/g,"'")+"\","
                +"\""+entry.status.replace(/"/g,"'")+"\","
                +"\""+(entry.errors?entry.errors.join("\n"):"").replace(/"/g,"'")+"\"";
        } // next fragment
        const encodedUri = encodeURI("data:text/csv;charset=utf-8;base64,"+btoa(csv));
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
    onClear(): void { // TODO also add clear entries with no media, entries with no fragment, selected entries
        this.entries = [];
    }
}

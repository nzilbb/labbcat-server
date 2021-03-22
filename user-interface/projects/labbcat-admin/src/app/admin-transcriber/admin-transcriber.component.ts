import { Component, OnInit, OnDestroy, ViewChild, ElementRef } from '@angular/core';
import { Router, ActivatedRoute } from '@angular/router';

import { Transcriber } from '../transcriber';
import { MessageService, LabbcatService, Response } from 'labbcat-common';

@Component({
  selector: 'app-admin-transcriber',
  templateUrl: './admin-transcriber.component.html',
  styleUrls: ['./admin-transcriber.component.css']
})
export class AdminTranscriberComponent implements OnInit, OnDestroy {
    transcriber: Transcriber;
    uploading = false;
    configuring = false;
    percentCompleted = 0;
    
    @ViewChild('configWebapp', {static: false}) configWebapp: ElementRef;    
    constructor(
        protected labbcatService: LabbcatService,
        protected messageService: MessageService,
        private router: Router,
        private route: ActivatedRoute
    ) { }

    boundOnSetConfig: EventListener;
    ngOnInit(): void {
        this.boundOnSetConfig = this.onSetConfig.bind(this);
        // listen for 'configuration finished' events
        addEventListener("message", this.boundOnSetConfig, true);
        this.route.queryParams.subscribe((params) => {
            const transcriberId = params["transcriberId"];
            if (transcriberId) { // we've been given a transcriber ID, so configure it
                this.labbcatService.labbcat.getId((baseUrl)=>{
                    this.transcriber = { transcriberId : transcriberId } as Transcriber;
                    this.configuring = true;
                    this.configWebapp.nativeElement.src
                        = baseUrl + "admin/transcriber/config/"+transcriberId+"/";
                });
            }
        });
    }   
    ngOnDestroy(): void {
        // deregister our listener
        console.log("admin-transcriber destroy");
        removeEventListener("message", this.boundOnSetConfig, true);        
    }

    onSetConfig(event) {
        console.log("admin-transcriber message received: " + JSON.stringify(event.data))
        if (event.data.resource == "setConfig") {
            if (event.data.error) {
                this.messageService.error(event.data.error);
            }
            if (event.data.message) {
                this.messageService.info(event.data.message);
            }
            this.messageService.info($localize `Configuration set`); // TODO i18n
            this.uploading = false;
            this.configuring = false;
            this.transcriber = null;
            // return to previous page
            this.router.navigate(["admin","transcribers"]);
        }
    }

    upload(files: File[]) {
        const file = files[0];
        this.labbcatService.labbcat.uploadTranscriber(file, (transcriber, errors, messages) => {
            if (errors) {
                for (let message of errors) {
                    this.messageService.error(message);
                }
            }
            if (messages) {
                for (let message of messages) {
                    this.messageService.info(message);
                }
            }
            if (transcriber) {
                this.transcriber = transcriber as Transcriber;
                // transcriber.info is a full HTML document, so remove the title
                this.transcriber.info = this.transcriber.info.replace(/<title>.*<\/title>/,"");
            }
        }, (event) => {
            this.percentCompleted = Math.round(100 * event.loaded / event.total);
        });
    }
    
    install(install: boolean) {
        this.labbcatService.labbcat.installTranscriber(
            this.transcriber.jar, install, (response, errors, messages) => {
                if (errors) {
                    for (let message of errors) {
                        this.messageService.error(message);
                    }
                }
                if (messages) {
                    for (let message of messages) {
                        this.messageService.info(message);
                    }
                }
                if (response.url) {
                    this.configuring = true;                    
                    // open the given config URL
                    this.configWebapp.nativeElement.src = response.url;
                } else {
                    this.transcriber = null;
                }
            });
    }
}

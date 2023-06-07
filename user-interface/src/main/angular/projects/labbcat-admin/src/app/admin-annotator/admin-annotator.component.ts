import { Component, OnInit, OnDestroy, ViewChild, ElementRef } from '@angular/core';
import { Router } from '@angular/router';

import { Annotator } from '../annotator';
import { MessageService, LabbcatService, Response } from 'labbcat-common';

@Component({
  selector: 'app-admin-annotator',
  templateUrl: './admin-annotator.component.html',
  styleUrls: ['./admin-annotator.component.css']
})
export class AdminAnnotatorComponent implements OnInit, OnDestroy {
    annotator: Annotator;
    uploading = false;
    configuring = false;
    percentCompleted = 0;
    
    @ViewChild('configWebapp', {static: false}) configWebapp: ElementRef;    
    constructor(
        protected labbcatService: LabbcatService,
        protected messageService: MessageService,
        private router: Router
    ) { }

    boundOnSetConfig: EventListener;
    ngOnInit(): void {
        this.boundOnSetConfig = this.onSetConfig.bind(this);
        // listen for 'configuration finished' events
        addEventListener("message", this.boundOnSetConfig, true);
    }   
    ngOnDestroy(): void {
        // deregister our listener
        console.log("admin-annotator destroy");
        removeEventListener("message", this.boundOnSetConfig, true);        
    }

    onSetConfig(event) {
        console.log("admin-annotator message received: " + JSON.stringify(event.data))
        if (event.data.resource == "setConfig") {
            if (event.data.error) {
                this.messageService.error(event.data.error);
            }
            if (event.data.message) {
                this.messageService.info(event.data.message);
            }
            this.messageService.info($localize `Configuration set`);
            this.uploading = false;
            this.configuring = false;
            this.annotator = null;
            // return to previous page
            this.router.navigate(["admin","annotators"]);
        }
    }

    upload(files: File[]) {
        const file = files[0];
        this.labbcatService.labbcat.uploadAnnotator(file, (annotator, errors, messages) => {
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
            if (annotator) {
                this.annotator = annotator as Annotator;
                // annotator.info is a full HTML document, so remove the title
                this.annotator.info = this.annotator.info.replace(/<title>.*<\/title>/,"");
            }
        }, (event) => {
            this.percentCompleted = Math.round(100 * event.loaded / event.total);
        });
    }
    
    install(install: boolean) {
        this.labbcatService.labbcat.installAnnotator(
            this.annotator.jar, install, (response, errors, messages) => {
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
                    this.annotator = null;
                }
            });
    }
}

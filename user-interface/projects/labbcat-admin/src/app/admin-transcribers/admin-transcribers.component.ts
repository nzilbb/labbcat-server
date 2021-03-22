import { Component, OnInit } from '@angular/core';

import { Transcriber } from '../transcriber';
import { MessageService, LabbcatService, Response } from 'labbcat-common';

@Component({
  selector: 'app-admin-transcribers',
  templateUrl: './admin-transcribers.component.html',
  styleUrls: ['./admin-transcribers.component.css']
})
export class AdminTranscribersComponent implements OnInit {
    rows: Transcriber[];
    
    constructor(
        protected labbcatService: LabbcatService,
        protected messageService: MessageService,
    ) { }
    
    ngOnInit(): void {
        this.readRows();
    }
    
    readRows(): void {
        this.labbcatService.labbcat.getTranscriberDescriptors((descriptors, errors, messages) => {
            this.rows = [];
            for (let descriptor of descriptors) {
                // transcriber.info is a full HTML document, so remove the title
                descriptor.info = descriptor.info.replace(/<title>.*<\/title>/,"");
                this.rows.push(descriptor as Transcriber);
            }
        });
    }
    
    uninstall(transcriber: Transcriber) {
        transcriber._deleting = true;
        if (confirm(`Are you sure you want to delete ${transcriber.transcriberId}`)) { // TODO i18n
            this.labbcatService.labbcat.uninstallTranscriber(
                transcriber.transcriberId, (response, errors, messages) => {
                    transcriber._deleting = false;
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
                    if (!errors) { // remove from view
                        this.rows.splice(this.rows.findIndex(
                            a=>a.transcriberId == transcriber.transcriberId), 1);
                    }
                });
        } // confirm
    }
}

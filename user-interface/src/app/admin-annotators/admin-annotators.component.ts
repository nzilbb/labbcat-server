import { Component, OnInit } from '@angular/core';

import { Response } from '../response';
import { Annotator } from '../annotator';
import { MessageService, LabbcatService } from 'labbcat-common';

@Component({
  selector: 'app-admin-annotators',
  templateUrl: './admin-annotators.component.html',
  styleUrls: ['./admin-annotators.component.css']
})
export class AdminAnnotatorsComponent implements OnInit {
    rows: Annotator[];
    
    constructor(
        protected labbcatService: LabbcatService,
        protected messageService: MessageService,
    ) { }
    
    ngOnInit(): void {
        this.readRows();
    }
    
    readRows(): void {
        this.labbcatService.labbcat.getAnnotatorDescriptors((descriptors, errors, messages) => {
            this.rows = [];
            for (let descriptor of descriptors) {
                // annotator.info is a full HTML document, so remove the title
                descriptor.info = descriptor.info.replace(/<title>.*<\/title>/,"");
                this.rows.push(descriptor as Annotator);
            }
        });
    }

    uninstall(annotator: Annotator) {
        annotator._deleting = true;
        if (confirm(`Are you sure you want to delete ${annotator.annotatorId}`)) { // TODO i18n
            this.labbcatService.labbcat.uninstallAnnotator(
                annotator.annotatorId, (response, errors, messages) => {
                    annotator._deleting = false;
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
                            a=>a.annotatorId == annotator.annotatorId), 1);
                    }
                });
        } // confirm
    }

}

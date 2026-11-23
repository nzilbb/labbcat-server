import { Component, Input, OnInit, OnChanges, SimpleChanges, EventEmitter, Output } from '@angular/core';

import { MessageService, LabbcatService, Layer } from 'labbcat-common';

import { Step } from '../step';

@Component({
  selector: 'app-admin-task-step',
  templateUrl: './admin-task-step.component.html',
  styleUrl: './admin-task-step.component.css'
})
export class AdminTaskStepComponent implements OnInit {
    @Input() step: Step;
    @Input() transcriptAttributes: Layer[];
    @Input() participantAttributes: Layer[];
    @Input() stimuli: string[];
    @Input() baseUrl: string;
    @Input() open: boolean;
    @Output() stepChanged = new EventEmitter<Step>();

    tag0Attribute: string;
    tag0Value: string;
    tag1Attribute: string;
    tag1Value: string;
    
    constructor(
        private labbcatService: LabbcatService,
        private messageService: MessageService,
    ) {
    }
    ngOnInit(): void {
        // setup tags
        const tags = this.step.tags.split("\n");
        const tag0 = tags[0];
        const tag1 = tags.length > 1?tags[1]:"";
        if (tag0) {
            const kv = tag0.split("=");
            this.tag0Attribute = kv[0];
            this.tag0Value = kv[1];
        }
        if (tag1) {
            const kv = tag1.split("=");
            this.tag1Attribute = kv[0];
            this.tag1Value = kv[1];
        }
    }
    setTag(): void {
        window.setTimeout(()=>{
            if (this.tag0Attribute) {
                this.step.tags = `${this.tag0Attribute}=${this.tag0Value}`;
            }
            if (this.tag1Attribute) {
                if (this.tag0Attribute) {
                    this.step.tags += `\n${this.tag1Attribute}=${this.tag1Value}`;
                } else {
                    this.step.tags = `${this.tag1Attribute}=${this.tag1Value}`;
                }
            } else if (!this.tag0Attribute) {
                this.step.tags = "";
            }
        }, 100);
    }
    selectFile(files: File[]): void {
        if (files.length == 0) return;
        const stimulusFile = files[0]
        if (!stimulusFile.name.endsWith(".svg")
            && !stimulusFile.name.endsWith(".png")
            && !stimulusFile.name.endsWith(".jpg")
            && !stimulusFile.name.endsWith(".jpeg")
            && !stimulusFile.name.endsWith(".mp4")
            && !stimulusFile.name.endsWith(".gif")) {
            this.messageService.error("File must be an image file or mp4 video."); // TODO i18n
            return;
        }
        
        // post file
        const fd = new FormData();
        fd.append("image", stimulusFile);
        const uploadRequest = this.labbcatService.labbcat.createRequest(
            "icon", null, (model, errors, messages) => {
                if (errors && errors.length) {
                    errors.forEach(m => this.messageService.error(m));
                } else {
                    if (messages) messages.forEach(m => this.messageService.info(m));
                    this.stimuli.push(stimulusFile.name);
                    this.step.image = stimulusFile.name;
                }
            },
            `${this.baseUrl}api/admin/elicit/stimulus`, "POST");
        try {
            uploadRequest.send(fd);
        } catch (x) {
            this.messageService.error(x);
        }
    }
}

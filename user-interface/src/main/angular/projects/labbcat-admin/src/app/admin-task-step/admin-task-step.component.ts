import { Component, Input, OnInit, OnChanges, SimpleChanges, EventEmitter, Output } from '@angular/core';
import { environment } from '../../environments/environment';
import { ClassicEditor, UploadAdapter, FileRepository, 
         Essentials, Heading, Bold, Italic, Code, Strikethrough, Superscript,
         Link, List, Alignment, HorizontalLine, Indent,
         AutoImage, Image, ImageResize, ImageCaption, ImageStyle, ImageToolbar, ImageInsert,
         ImageBlock, ImageResizeEditing, ImageResizeHandles,
         BlockQuote, Table, TableCellProperties, TableProperties, TableToolbar, TableCaption,
         Mention, Paragraph, Undo
       } from 'ckeditor5';

import { MessageService, LabbcatService, Layer } from 'labbcat-common';

import { LabbcatUploadAdapterPlugin } from '../labbcat-upload-adapter';
import { Step } from '../step';

@Component({
  selector: 'app-admin-task-step',
  templateUrl: './admin-task-step.component.html',
  styleUrl: './admin-task-step.component.css'
})
export class AdminTaskStepComponent implements OnInit {
    @Input() step: Step;
    @Input() root: Step;
    @Input() transcriptAttributes: Layer[];
    @Input() participantAttributes: Layer[];
    @Input() stimuli: string[];
    @Input() baseUrl: string;
    @Input() open: boolean;
    @Output() stepChanged = new EventEmitter<Step>();

    public Editor = ClassicEditor;
    public config = {
        toolbar: [ 'heading', 'bold', 'italic', 'code', 'strikethrough', 'superscript',
                   'link', 'bulletedList', 'numberedList', 'alignment', 'horizontalLine',
                   '|', 'outdent', 'indent',
                   '|', 'insertImage','blockQuote', 'insertTable',
                   '|', 'undo', 'redo' ],
        image: {
            resizeOptions: [
                {
                    name: 'resizeImage:original',
                    value: null,
                    icon: 'original'
                },
                {
                    name: 'resizeImage:custom',
                    value: 'custom',
                    icon: 'custom'
                },
                {
                    name: 'resizeImage:50',
                    value: '50',
                    icon: 'medium'
                },
                {
                    name: 'resizeImage:75',
                    value: '75',
                    icon: 'large'
                }
            ],
            toolbar: [
	        'imageStyle:inline',
	        'imageStyle:block',
	        'imageStyle:side',
	        '|',
	        'toggleImageCaption',
	        'imageTextAlternative',
                '|',
                'resizeImage:50',
                'resizeImage:75',
                'resizeImage:original',
                'resizeImage:custom'
	    ]
        },
        table: {
	    contentToolbar: [
	        'tableColumn',
	        'tableRow',
	        'mergeTableCells'
	    ]
        },
        plugins: [
            Essentials, FileRepository, 
            Heading, Bold, Italic, Code, Strikethrough, Superscript,
            Link, List, Alignment, HorizontalLine, Indent,
            AutoImage, Image, ImageResize, ImageCaption, ImageStyle, ImageToolbar, ImageInsert,
            BlockQuote, Table, TableCellProperties, TableProperties, TableToolbar, TableCaption,
            Mention, Paragraph, Undo
        ],
        extraPlugins: [ LabbcatUploadAdapterPlugin ],
        LabbcatUploadAdapterPlugin: {
            uploadBaseUrl: environment.baseUrl + "api/admin/elicit/stimulus/"
        }
        // mention: {
        //     Mention configuration
        // }
    }
    
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
        if (this.step.tags) {
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
            this.onChange(this.step);
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
                    files = null;
                }
            },
            `${this.baseUrl}api/admin/elicit/stimulus`, "POST");
        try {
            uploadRequest.send(fd);
        } catch (x) {
            this.messageService.error(x);
        }
    }
    onChange(step: Step): void {
        this.stepChanged.emit(step);
    }
    getParent(step: Step, step_id : string): Step {
        let parent = null;
        if (step.steps) {
            for (let substep of step.steps) {
                if (`${substep.step_id}` == step_id) return step;
                parent = this.getParent(substep, step_id);
                if (parent) return parent;
            }
        }
        return parent;
    }
    getStep(step: Step, step_id : string): Step {
        if (`${step.step_id}` == step_id) return step;
        if (step.steps) {
            for (let substep of step.steps) {
                let found = this.getStep(substep, step_id);
                if (found) return found;
            }
        }
        return null;
    }
    getAncestors(step: Step): Step[] {
        const ancestors = [] as Step[];
        let parent = this.getParent(this.root, `${step.step_id}`);
        if (parent) {
            while (parent.step_id >= 0) {
                ancestors.push(parent);
                parent = this.getParent(this.root, `${parent.step_id}`);
            }
        }
        return ancestors;
    }
    deleteStep(): void {
        const parent = this.getParent(this.root, `${this.step.step_id}`);
        if (parent && parent.steps) {
            const description = this.step.title||this.step.transcript||"[blank step]"; // TODO i18n
            if (confirm(
                this.step.steps && this.step.steps.length?
                    `Delete this step ('${description}') and all sub-steps?` // TODO i18n
                    :`Delete this step ('${description}')?` // TODO i18n
            )) {
                parent.steps = parent.steps.filter(s=>s != this.step);
                this.onChange(parent);
            }
        } // it's possible to delete this step
    }
    dragStart(e: DragEvent): void {
        e.dataTransfer.setData("text/plain", `${this.step.step_id}`);
    }
    draggingOver = false;
    dragOver(e: DragEvent): void {
        const id = e.dataTransfer.getData("text/plain");
        if (id == `${this.step.step_id}`) return; // don't drop on ourselves
        let parent = this.getParent(this.root, id) || this.root;
        //if (parent.step_id == this.step.step_id) return; // don't drop on our parent
        for (let ancestor of this.getAncestors(this.step)) {
            if (`${ancestor.step_id}` == id) return; // don't drag on to descendants
        } // next ancestor
        e.preventDefault();
        this.draggingOver = true;
        this.draggingOverBefore = false;
    }
    dragLeave(e: DragEvent): void {
        const id = e.dataTransfer.getData("text/plain");
        this.draggingOverBefore = false;
        this.draggingOver = false;
    }
    dragEnd(e: DragEvent): void {
        const id = e.dataTransfer.getData("text/plain");
    }
    drop(e: DragEvent): void {
        this.draggingOverBefore = false;
        this.draggingOver = false;
        const id = e.dataTransfer.getData("text/plain");
        const newParent = this.step;
        const oldParent = this.getParent(this.root, id);
        const moving = this.getStep(this.root, id);
        console.log(`Move ${moving.title} into ${newParent.title}`);
        if (newParent && oldParent && oldParent.steps && moving) {
            // remove from old parent
            oldParent.steps = oldParent.steps.filter(s=>s != moving);
            
            // add to new parent
            if (newParent.steps) {
                newParent.steps.push(moving);
            } else {
                newParent.steps = [ moving ];
            }
            this.onChange(moving);
        } // can move
    }
    draggingOverBefore = false;
    dragOverBefore(e: DragEvent): void {
        const id = e.dataTransfer.getData("text/plain");
        if (id == `${this.step.step_id}`) return; // don't drop on ourselves
        let parent = this.getParent(this.root, id) || this.root;
        // if (parent.step_id == this.step.step_id) return; // don't drop on our parent
        for (let ancestor of this.getAncestors(this.step)) {
            if (`${ancestor.step_id}` == id) return; // don't drag on to descendants
        } // next ancestor
        e.preventDefault();
        this.draggingOverBefore = true;
        this.draggingOver = false;
    }
    dragLeaveBefore(e: DragEvent): void {
        const id = e.dataTransfer.getData("text/plain");
        this.draggingOverBefore = false;
        this.draggingOver = false;
    }
    dropBefore(e: DragEvent): void {
        this.draggingOverBefore = false;
        this.draggingOver = false;
        const id = e.dataTransfer.getData("text/plain");
        const before = this.step;
        const newParent = this.getParent(this.root, `${before.step_id}`);
        const oldParent = this.getParent(this.root, id);
        const moving = this.getStep(this.root, id);
        console.log(`Move ${moving.title} to before ${before.title} in ${newParent.step_id}:${newParent.title}`);
        if (newParent && newParent.steps && oldParent && oldParent.steps && moving) {
            // remove from old parent
            oldParent.steps = oldParent.steps.filter(s=>s != moving);
            
            // add to new parent
            if (newParent.steps) {
                const newIndex = newParent.steps.indexOf(before);
                newParent.steps.splice(newIndex, 0, moving);
                this.onChange(moving);
            }
        } // can move
    }
}

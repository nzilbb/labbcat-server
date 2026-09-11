import { Component, ViewEncapsulation, OnInit } from '@angular/core';
import { environment } from '../../environments/environment';
import { ClassicEditor, UploadAdapter, FileRepository,
         Essentials, Heading, Bold, Italic, Code, Strikethrough, Superscript,
         Link, List, Alignment, HorizontalLine, Indent,
         AutoImage, Image, ImageResize, ImageCaption, ImageStyle, ImageToolbar, ImageInsert,
         ImageBlock, ImageResizeEditing, ImageResizeHandles,
         BlockQuote, Table, TableCellProperties, TableProperties, TableToolbar, TableCaption,
         Mention, Paragraph, Undo
       } from 'ckeditor5';

import { ElicitationTask } from '../elicitation-task';
import { Corpus } from '../corpus';
import { LabbcatUploadAdapterPlugin } from '../labbcat-upload-adapter';
import { MessageService, LabbcatService, Response } from 'labbcat-common';
import { AdminComponent } from '../admin-component';

@Component({
  selector: 'app-admin-tasks',
  templateUrl: './admin-tasks.component.html',
  styleUrl: './admin-tasks.component.css',
  encapsulation: ViewEncapsulation.None
})
export class AdminTasksComponent extends AdminComponent implements OnInit {
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

    rows: ElicitationTask[];
    corpora: Corpus[];
    transcriptTypes: any[];
    
    newTaskName = "";

    baseUrl = environment.baseUrl;
    
    fileSelector = false;
    taskFile: File;
    uploading = false;
    percentCompleted: number;

    constructor(
        labbcatService: LabbcatService,
        messageService: MessageService
    ) {
        super(labbcatService, messageService);
    }
    
    ngOnInit(): void {
        this.readCorpora();
        this.readTranscriptTypes();
        this.readRows();
    }
    readCorpora(): void {
        this.labbcatService.labbcat.readCorpora((corpora, errors, messages) => {
            this.corpora = [];
            for (let corpus of corpora) {
                this.corpora.push(corpus as Corpus);
            }
        });
    }
    readTranscriptTypes(): void {
        this.labbcatService.labbcat.getLayer(
            "transcript_type", (layer, errors, messages) => {
            this.transcriptTypes = [];
            for (let label in layer.validLabels) {
                if (label) {
                    this.transcriptTypes.push({
                        label: label,
                        description: layer.validLabels[label]});
                }
            }
        });
    }    
    readRows(): void {
        this.labbcatService.labbcat.readElicitationTasks((tasks, errors, messages) => {
            this.rows = [];
            for (let task of tasks) {
                this.rows.push(task as ElicitationTask);
            }
        });
    }
    
    onChange(row: ElicitationTask) {
        row._changed = this.changed = true;        
    }

    creating = false;
    createRow(
        taskName: string, description: string, corpusName: string, transcriptType: string)
    : boolean {
        this.creating = true;
        this.labbcatService.labbcat.createElicitationTask(
            taskName, description, corpusName, transcriptType,
            "<p>Your voice will be recorded and uploaded to our server.</p>", "", "",
            (row, errors, messages) => {
                this.creating = false;
                if (errors) errors.forEach(m => this.messageService.error(m));
                if (messages) messages.forEach(m => this.messageService.info(m));
                // update the model with the field returned
                if (row) this.rows.push(row as ElicitationTask);
                this.updateChangedFlag();
            });
        return true;
    }

    deleteRow(row: ElicitationTask) {
        row._deleting = true;
        if (confirm(`Are you sure you want to delete ${row.task_name}?`)) { // TODO i18n
            this.labbcatService.labbcat.deleteElicitationTask(
                row.task_id, (model, errors, messages) => {
                    row._deleting = false;
                    if (errors) errors.forEach(m => this.messageService.error(m));
                    if (messages) messages.forEach(m => this.messageService.info(m));
                    if (!errors) {
                        // remove from the model/view
                        this.rows = this.rows.filter(r => { return r !== row;});
                        this.updateChangedFlag();
                    }});
        } else {
            row._deleting = false;
        }
    }
    
    updateChangedRows() {
        this.rows
            .filter(r => r._changed)
            .forEach(r => this.updateRow(r));
    }
    
    updating = 0;
    updateRow(row: ElicitationTask) {
        this.updating++;
        this.labbcatService.labbcat.updateElicitationTask(
            row.task_id, row.task_name, row.description, row.corpus_name,
            row.transcript_type, row.preamble, row.consent, row.endUrl,
            (task, errors, messages) => {
                this.updating--;
                if (errors) errors.forEach(m => this.messageService.error(m));
                if (messages) messages.forEach(m => this.messageService.info(m));
                // update the model with the field returned
                const updatedRow = task as ElicitationTask;
                const i = this.rows.findIndex(r => {
                    return r.task_id == updatedRow.task_id; })
                this.rows[i] = updatedRow;
                this.updateChangedFlag();
            });
    }
    
    updateChangedFlag() {
        this.changed = false;
        for (let row of this.rows) {
            if (row._changed) {
                this.changed = true;
                break; // only need to find one
            }
        } // next row
    }
    
    startUpload() {
        this.fileSelector = true;
        window.setTimeout(()=>{
            document.getElementById("taskFile").click();
        }, 100);
    }
    /** Called when a task file is selected; parses the file to determine CSV fields. */
    selectFile(files: File[]): void {
        if (files.length == 0) {
            this.fileSelector = false;
            return;
        }
        this.taskFile = files[0]
        if (!this.taskFile.name.endsWith(".json")) {
            this.messageService.error("File must be a .json file exported from LaBB-CAT"); // TODO i18n
            this.taskFile = null;
            this.fileSelector = false;
            return;
        }
        const fileTaskName = this.taskFile.name.replace(/\.json$/,"");
        let existingTask = null;
        for (let task of this.rows) {
            if (task.task_name == fileTaskName) {
                existingTask = task;
                break;
            }
        }
        if (existingTask) {
            if (!confirm("There is an existing task with this name, would you like to replace it?")) {
                this.messageService.info(
                    "If you would like to upload this task with a new name, rename the file, and then upload it.");
                this.taskFile = null;
                this.fileSelector = false;
                return;
            }
        }
        this.upload();
    }
    
    upload() {
        this.uploading = true;
        this.labbcatService.labbcat.elicitatationTaskUpload(
            this.taskFile, (result, errors, messages) => {
                this.uploading = false;
                this.taskFile = null;
                this.fileSelector = false;
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
                if (result && result.task_id) {
                    this.readRows();
                }
            }, (event) => {
                this.percentCompleted = Math.round(100 * event.loaded / event.total);
            });
    }
}

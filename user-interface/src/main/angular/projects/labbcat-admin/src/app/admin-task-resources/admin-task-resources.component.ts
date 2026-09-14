import { Component, ViewEncapsulation, OnInit } from '@angular/core';
import { ActivatedRoute, ParamMap } from '@angular/router';
import { switchMap } from 'rxjs/operators';
import { environment } from '../../environments/environment';
import { ClassicEditor, UploadAdapter, FileRepository,
         Essentials, Heading, Bold, Italic, Code, Strikethrough, Superscript,
         Link, List, Alignment, HorizontalLine, Indent,
         AutoImage, Image, ImageResize, ImageCaption, ImageStyle, ImageToolbar, ImageInsert,
         ImageBlock, ImageResizeEditing, ImageResizeHandles,
         BlockQuote, Table, TableCellProperties, TableProperties, TableToolbar, TableCaption,
         Mention, Paragraph, Undo
       } from 'ckeditor5';
import { MessageService, LabbcatService, Response } from 'labbcat-common';
import { AdminComponent } from '../admin-component';

import { TaskResource } from '../task-resource';

@Component({
  selector: 'app-admin-task-resources',
  templateUrl: './admin-task-resources.component.html',
  styleUrl: './admin-task-resources.component.css',
  encapsulation: ViewEncapsulation.None
})
export class AdminTaskResourcesComponent extends AdminComponent implements OnInit {
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
        // mention: {
        //     Mention configuration
        // }
    }

    task_id: number;
    task_name: string;
    rows: TaskResource[];
    baseUrl = environment.baseUrl;

    constructor(
        labbcatService: LabbcatService,
        messageService: MessageService,
        private route: ActivatedRoute
    ) {
        super(labbcatService, messageService);
    }
    
    ngOnInit(): void {
        // need to subscribe to URL path changes, because the component will be re-used
        // from scope to scope
        this.route.paramMap.pipe(
            switchMap((params: ParamMap) => params.get('task_id'))
        ).subscribe(task_id => {
            this.task_id = parseInt(this.route.snapshot.paramMap.get('task_id'));
            this.readTask();
            this.readRows();
        });
    }
    readTask(): Promise<number> {
        return new Promise<number>((accept,reject)=>{
            this.labbcatService.labbcat.readElicitationTasks((tasks, errors, messages) => {
                for (let task of tasks) {
                    if (task.task_id == this.task_id) {
                        this.task_name = task.task_name;
                        accept(this.task_id);
                        return;
                    }
                } // next task
                this.messageService.error(`Invalid task: ${this.task_id}`); // TODO i18n
                reject();
            });
        });
    }
    readRows(): void {
        this.labbcatService.labbcat.readElicitationTaskResources(
            this.task_id, (resources, errors, messages) => {
            this.rows = [];
            for (let resource of resources) {
                this.rows.push(resource as TaskResource);
            }
        });
    }
    onChange(row: TaskResource) {
        row._changed = this.changed = true;        
    }

    updateChangedRows() {
        this.rows
            .filter(r => r._changed)
            .forEach(r => this.updateRow(r));
    }
    
    updating = 0;
    updateRow(row: TaskResource) {
        this.updating++;
        this.labbcatService.labbcat.updateElicitationTaskResource(
            row.task_id, row.resource_id, row.help, row.message,
            (resource, errors, messages) => {
                this.updating--;
                if (errors) errors.forEach(m => this.messageService.error(m));
                if (messages) messages.forEach(m => this.messageService.info(m));
                // update the model with the field returned
                const updatedRow = resource as TaskResource;
                const i = this.rows.findIndex(r => {
                    return r.task_id == updatedRow.task_id
                        && r.resource_id == updatedRow.resource_id; })
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

}

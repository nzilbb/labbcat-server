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
import { MessageService, LabbcatService, Response, Layer } from 'labbcat-common';
import { AdminComponent } from '../admin-component';

@Component({
  selector: 'app-admin-task-attributes',
  templateUrl: './admin-task-attributes.component.html',
  styleUrl: './admin-task-attributes.component.css'
})
export class AdminTaskAttributesComponent extends AdminComponent implements OnInit {
    task_id: number;
    scope: string;
    linkScope: string;
    task_name: string;
    rows: Layer[];
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
            this.scope = this.route.snapshot.paramMap.get('scope');
            this.linkScope = this.scope.substring(0,1).toUpperCase()+this.scope.substring(1);
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
        const attributes = this.labbcatService.labbcat.createRequest(
            "task-attributes", null, (attributes, errors, messages) => {
                if (errors) errors.forEach(m => this.messageService.error(m));
                if (messages) messages.forEach(m => this.messageService.info(m));
                if (attributes) {
                    this.rows = attributes as Layer[];
                }
            },
            `${this.baseUrl}api/admin/elicit/attributes/${this.scope}/${this.task_id}`, "GET");
        try {
            attributes.send();
        } catch (x) {
            this.messageService.error(x);
        }
    }

}

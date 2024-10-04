import { Component, ViewEncapsulation, OnInit } from '@angular/core';
import { ClassicEditor, UploadAdapter, FileRepository,
         Essentials, Heading, Bold, Italic, Code, Strikethrough, Superscript,
         Link, List, Alignment, HorizontalLine, Indent,
         AutoImage, Image, ImageResize, ImageCaption, ImageStyle, ImageToolbar, ImageInsert,
         ImageBlock, ImageResizeEditing, ImageResizeHandles,
         BlockQuote, Table, TableCellProperties, TableProperties, TableToolbar, TableCaption,
         Mention, Paragraph, Undo
       } from 'ckeditor5';

import { LabbcatUploadAdapterPlugin } from '../labbcat-upload-adapter';

import { MessageService, LabbcatService } from 'labbcat-common';

@Component({
    selector: 'app-admin-agreement',
    templateUrl: './admin-agreement.component.html',
    styleUrl: './admin-agreement.component.css',
    encapsulation: ViewEncapsulation.None
})
export class AdminAgreementComponent implements OnInit {

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
        extraPlugins: [ LabbcatUploadAdapterPlugin ]
        // mention: {
        //     Mention configuration
        // }
    }

    agreementExists = false;
    changed = false;
    updating = false;
    deleting = false;
    agreementHtml: string;
    
    constructor(
        private labbcatService: LabbcatService,
        private messageService: MessageService,
    ) {
    }

    ngOnInit(): void {
        this.readAgreement();
    }
    
    readAgreement(): void {
        this.labbcatService.labbcat.readAgreement((agreementHtml, errors, messages) => {
            if (errors) errors.forEach(m => this.messageService.error(m));
            if (messages) messages.forEach(m => this.messageService.info(m));
            this.agreementHtml = agreementHtml;
            setTimeout(() => {
                this.changed = this.updating = this.deleting = false;
            }, 10);
            this.agreementExists = this.agreementHtml?true:false;
        });
    }
    onChange(): void {
        this.changed = true;
    }
    updateAgreement(): void {
        this.updating = true;
        this.labbcatService.labbcat.updateAgreement(
            this.agreementHtml, (result, errors, messages) => {
                if (errors) errors.forEach(m => this.messageService.error(m));
                if (messages) messages.forEach(m => this.messageService.info(m));
                this.readAgreement();
            });
    }
    deleteAgreement(): void {
        this.deleting = true;
        this.labbcatService.labbcat.deleteAgreement((result, errors, messages) => {
            if (errors) errors.forEach(m => this.messageService.error(m));
            if (messages) messages.forEach(m => this.messageService.info(m));
            this.readAgreement();
        });
    }

}

import { Component, OnInit, Input } from '@angular/core';
import * as ClassicEditor from '@ckeditor/ckeditor5-build-classic';
import { ChangeEvent } from '@ckeditor/ckeditor5-angular/ckeditor.component';

import { MessageService } from '../message.service';
import { LabbcatService } from '../labbcat.service';
import { AdminComponent } from '../admin-component';
import { CkUploadAdapter } from '../ck-upload-adapter';

@Component({
  selector: 'app-admin-info',
  templateUrl: './admin-info.component.html',
  styleUrls: ['./admin-info.component.css']
})
export class AdminInfoComponent extends AdminComponent implements OnInit {
    loading = false;
    updating = false;
    info: string;
    Editor = ClassicEditor;
    
    constructor(
        labbcatService: LabbcatService,
        messageService: MessageService
    ) {
        super(labbcatService, messageService);
        console.log("ClassicEditor.builtinPlugins[1] " + ClassicEditor.builtinPlugins[1]);
        for (let p in ClassicEditor.builtinPlugins)
            console.log(" " + p + " : " + ClassicEditor.builtinPlugins[p].pluginName );
    }
    
    ngOnInit(): void {
        this.getInfo();
    }
    
    onReady(editor: ClassicEditor): void {
        console.log("onReady " + editor);
        editor.plugins.get( 'FileRepository' ).createUploadAdapter = ( loader ) => {
            return new CkUploadAdapter( loader );
        };
    }
    
    getInfo(): void {
        this.loading = true;
        this.labbcatService.labbcat.getInfo((html, errors, messages) => {
            this.loading = false;
            if (errors) for (let message of errors) this.messageService.error(message);
            if (messages) for (let message of messages) this.messageService.info(message);
            this.info = html;
        });
    }
    
    public onChange( { editor }: ChangeEvent ) {
        this.changed = true;
    }
    
    updateInfo(): void {
        this.updating = true;
        this.labbcatService.labbcat.updateInfo(this.info, (result, errors, messages) => {
            this.updating = false;
            if (errors) for (let message of errors) this.messageService.error(message);
            if (messages) for (let message of messages) this.messageService.info(message);
            this.changed = errors;
        });
    }
    
}

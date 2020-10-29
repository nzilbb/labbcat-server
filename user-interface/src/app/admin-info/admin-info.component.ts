import { Component, OnInit, Input } from '@angular/core';
import * as ClassicEditor from '@ckeditor/ckeditor5-build-classic';
import { ChangeEvent } from '@ckeditor/ckeditor5-angular/ckeditor.component';

import { MessageService } from '../message.service';
import { LabbcatService } from '../labbcat.service';
import { AdminComponent } from '../admin-component';

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
    }
    
    ngOnInit(): void {
        this.getInfo();
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

import { Component, OnInit } from '@angular/core';
import { MessageService } from '../message.service';

@Component({
    selector: 'lib-messages',
    templateUrl: './messages.component.html',
    styleUrls: ['./messages.component.css']
})
export class MessagesComponent implements OnInit {
    
    constructor(
        public messageService: MessageService
    ) { }
    
    ngOnInit(): void {
    }

    removeMessage(message: string): void {
        this.messageService.messages.splice(
            this.messageService.messages.indexOf(message), 1);
    }
    removeError(message: string): void {
        this.messageService.errors.splice(
            this.messageService.errors.indexOf(message), 1);
    }
}

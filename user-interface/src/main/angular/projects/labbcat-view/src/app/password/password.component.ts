import { Component } from '@angular/core';
import { MessageService, LabbcatService, Response } from 'labbcat-common';

@Component({
  selector: 'app-password',
  templateUrl: './password.component.html',
  styleUrl: './password.component.css'
})
export class PasswordComponent {
    currentPassword: string;
    newPassword: string;
    repeatPassword: string;

    changed = false;
    processing = false;

    constructor(
        private labbcatService: LabbcatService,
        private messageService: MessageService
    ) {
    }
    
    checkForm(): void {
        this.changed = this.currentPassword.length > 0
            && this.newPassword.length > 0
            && this.newPassword == this.repeatPassword;
    }
    
    changePassword(): void {
        if (this.changed) {
            this.processing = true;
            this.labbcatService.labbcat.changePassword(
                this.currentPassword, this.newPassword, (result, errors, messages)=>{
                    this.processing = false;
                    if (messages) messages.forEach(m => this.messageService.info(m));
                    if (errors) {
                        errors.forEach(m => this.messageService.error(m));
                    } else {
                        this.currentPassword = this.newPassword = this.repeatPassword = "";
                        this.changed = false;
                    }
                });
        }
    }
}

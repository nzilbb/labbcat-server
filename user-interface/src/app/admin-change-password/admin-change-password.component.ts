import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';

import { Response } from '../response';
import { MessageService } from '../message.service';
import { LabbcatService } from '../labbcat.service';
import { AdminComponent } from '../admin-component';

@Component({
  selector: 'app-admin-change-password',
  templateUrl: './admin-change-password.component.html',
  styleUrls: ['./admin-change-password.component.css']
})
export class AdminChangePasswordComponent extends AdminComponent implements OnInit {
    user: string;
    p: string;
    p2: string;
    resetPassword = true;
    
    processing = false;

    constructor(
        labbcatService: LabbcatService,
        messageService: MessageService,
        private route: ActivatedRoute,
        private router: Router
    ) {
        super(labbcatService, messageService);
    }

    checkForm(): void {
        this.changed = this.p.length > 0
            && this.p == this.p2;
    }

    ngOnInit(): void {
        this.user = this.route.snapshot.paramMap.get('user');
    }

    changePassword(): void {
        if (this.changed) {
            this.processing = true;
            this.labbcatService.labbcat.setPassword(
                this.user, this.p, this.resetPassword, (result, errors, messages)=>{
                    this.processing = false;
                    if (errors) for (let message of errors) this.messageService.error(message);
                    if (messages) for (let message of messages) this.messageService.info(message);
                    this.p = this.p2 = "";
                    this.changed = false;
                    if (!errors) {
                        // return to previous page
                        this.router.navigate(["admin","users"]);
                    }
                });
        }
    }
    
}

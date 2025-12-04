import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';

import { MessageService, LabbcatService } from 'labbcat-common';

@Component({
  selector: 'app-login',
  templateUrl: './login.component.html',
  styleUrls: ['./login.component.css']
})
export class LoginComponent implements OnInit {

    username: string;
    password: string;
    error = false;

    constructor(
        private labbcatService: LabbcatService,
        private messageService: MessageService,
        private route: ActivatedRoute,
        private router: Router
    ) { }
    
    ngOnInit(): void {
    }

    login(): void {
        this.labbcatService.login(this.username, this.password)
            .then((url)=>{
                window.location.assign(
                    url + (window.location.hash || "") || ".");
            })
            .catch((errors)=>{
                errors.forEach(m => this.messageService.error(m));
                this.error = true;
            });
    }
}

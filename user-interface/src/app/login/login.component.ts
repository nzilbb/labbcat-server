import { Component, OnInit } from '@angular/core';

import { LabbcatService } from 'labbcat-common';

@Component({
  selector: 'app-login',
  templateUrl: './login.component.html',
  styleUrls: ['./login.component.css']
})
export class LoginComponent implements OnInit {

    constructor(
        private labbcatService: LabbcatService
    ) { }
    
    ngOnInit(): void {
    }
    
    login(username: string, password: string): void {
        this.labbcatService.login(username, password);
    }
}

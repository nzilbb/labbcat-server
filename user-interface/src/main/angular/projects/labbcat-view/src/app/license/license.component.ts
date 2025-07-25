import { Component, OnInit, Inject, ViewChild, ElementRef } from '@angular/core';
import { ActivatedRoute } from '@angular/router';

import { LabbcatService, User } from 'labbcat-common';

@Component({
  selector: 'app-license',
  templateUrl: './license.component.html',
  styleUrl: './license.component.css'
})
export class LicenseComponent implements OnInit {

    @ViewChild('form', {static: false}) form: ElementRef;
    baseUrl: string;
    title: string;
    next: string;
    agreementHtml: string;
    user : User;

    constructor(
        private labbcatService: LabbcatService,
        private route: ActivatedRoute,
        @Inject('environment') private environment
    ) {
        this.baseUrl = this.environment.baseUrl;
    }
    
    ngOnInit(): void {
        this.route.queryParams.subscribe((params) => {
            this.next = params["next"];
            if (this.next) { // they haven't agreed yet, so ensure they don't have a menu
                this.disableMenus();
            }
            this.readAgreement();
        });
        this.readUserInfo();
    }

    disableMenus(): void {
        const navs = document.getElementsByTagName("nav");
        for (let n = 0; n < navs.length; n++) {
            navs.item(n).style.display = "none";
        }
    }
    
    readUserInfo() : Promise<void> {
        return new Promise((resolve, reject) => {
            this.labbcatService.labbcat.getUserInfo((user, errors, messages) => {
                this.title = this.labbcatService.title;
                this.user = user as User;
                resolve();
            });
        });
    }
    readAgreement(): Promise<void> {
        return new Promise((resolve, reject) => {
            this.labbcatService.labbcat.readAgreement((agreement) => {
                if (agreement) {
                    this.agreementHtml = agreement;
                    resolve();
                } else { // no agreement, fall back to software license
                    this.labbcatService.labbcat.createRequest(
                        "agpl", null, (agpl) => {
                            this.agreementHtml = `<pre>${agpl}</pre>`;
                            resolve();
                        },
                        `${this.baseUrl}agpl.txt`, "GET", null, null, true).send();
                } // fall back agreement
            });
        });
    }

    agree(): void {
        this.form.nativeElement.submit();
    }

    edit(): void {
        this.labbcatService.labbcat.getId((baseUrl, errors, messages) => {
            document.location = `${baseUrl}/admin/agreement`;
        });
    }
    
}

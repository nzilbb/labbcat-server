import { Component, OnInit, Inject } from '@angular/core';

import { MessageService, LabbcatService } from 'labbcat-common';
import { User } from 'labbcat-common';
import { DashboardItem } from '../dashboard-item';

@Component({
  selector: 'app-home',
  templateUrl: './home.component.html',
    styleUrl: './home.component.css'
})
export class HomeComponent implements OnInit {
    
    user: User;
    baseUrl: string;
    title: string;
    emptyDatabase = false;
    info: string;
    infoLink: string;
    link: DashboardItem[];
    sql: DashboardItem[];
    exec: DashboardItem[];
    
    constructor(
        private labbcatService: LabbcatService,
        private messageService: MessageService,
        @Inject('environment') private environment
    ) {
        this.baseUrl = this.environment.baseUrl;
    }
    
    ngOnInit(): void {
        this.readUserInfo();
        this.readItems();
        this.checkForEmptyDatabase();
        this.readInfo();
    }
    
    readUserInfo(): void {
        this.labbcatService.labbcat.getUserInfo((user, errors, messages) => {
            this.user = user as User;
        });
    }
    
    readItems(): void {
        this.labbcatService.labbcat.getDashboardItems(
            "home", (items, errors, messages) => {
                this.title = this.labbcatService.title;
                
                this.link = [];
                this.sql = [];
                this.exec = [];
                for (let i of items) {
                    const item = i as DashboardItem
                    switch (item.type) {
                        case "link":
                            this.link.push(item);
                            break;
                        case "sql":
                            this.sql.push(item);
                            break;
                        case "exec":
                            this.exec.push(item);
                            break;                            
                    }
                    this.readItem(item);
                } // next item
            });
    }
    
    readItem(item: DashboardItem): void {
        this.labbcatService.labbcat.getDashboardItem(
            item.item_id, (value, errors, messages) => {
                if (errors && errors.length) {
                    item.error = errors.join("\n");
                } 
                item.value = value;
            });
    }

    checkForEmptyDatabase(): void {
        this.labbcatService.labbcat.countMatchingTranscriptIds(
            "/.*/.test(id)", (transcriptCount, errors, messages) => {
                // if there are no transcripts, it's an empty database
                this.emptyDatabase = (transcriptCount == 0);
            });
    }
    
    readInfo(): void {
        this.labbcatService.labbcat.getInfo((info, errors, messages) => {
            const docTitle = info.replace(/.*<title>/ms,"").replace(/<\/title>.*/ms,"")
            const doc = info                
            // we only want the body of the document
                .replace(/.*<article>/ms,"").replace(/<\/article>.*/ms,"")
            // fix relative img src URLs
                .replaceAll(/ src=".\//g, ` src="${this.baseUrl}/doc/`)
            // fix relative a href URLs by fixing all hrefs
                .replaceAll(/ href="([^"]*)"/g, ` href="${this.baseUrl}doc/$1"`)
            // and then un-fixing absolute or internal ones
                .replaceAll(
                    new RegExp(` href="${this.baseUrl}doc/(http:|https:|mailto:|#)`, "g"),
                    " href=\"$1");
            if (doc) { // do we show the whole document, or just a link?
                this.labbcatService.labbcat.getSystemAttribute(
                    "docOnHomePage", (attribute, errors, messages) => {
                        if (attribute.value == "0") {
                            this.infoLink = docTitle;
                        } else {
                            this.info = doc;
                        }
                });
            }
        });
    }

    // user action"
    help(): boolean {
        window.open(
            "start.help", "help",
            "height=600,width=800,toolbar=yes,menubar=no,scrollbars=yes,resizable=yes,location=no,directories=no,status=no").focus();
        return false;
    }
}


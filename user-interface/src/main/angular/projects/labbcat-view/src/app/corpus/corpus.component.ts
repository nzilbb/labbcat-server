import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';

import { MessageService, LabbcatService } from 'labbcat-common';

@Component({
  selector: 'app-corpus',
  templateUrl: './corpus.component.html',
  styleUrl: './corpus.component.css'
})
export class CorpusComponent implements OnInit {

    id: string;
    fields: string[];
    info: { [field: string]: string };

    constructor(
        private labbcatService: LabbcatService,
        private messageService: MessageService,
        private route: ActivatedRoute
    ) {
    }
    
    ngOnInit(): void {
        this.getId();
    }

    getId() : void {
        this.id = this.route.snapshot.paramMap.get('id');
        if (this.id) {
            this.readCorpusInfo();
        } else {
            this.route.queryParams.subscribe((params) => {
                this.id = params["id"];
                this.readCorpusInfo();
            });
        }
    }
    readCorpusInfo(): void {
        this.labbcatService.labbcat.getCorpusInfo(
            this.id, (info, errors, messages) => {
                if (errors) errors.forEach(m => this.messageService.error(m));
                if (messages) messages.forEach(m => this.messageService.info(m));
                if (info) {
                    this.info = info;
                    this.fields = Object.keys(info);
                }
            });
    }
}

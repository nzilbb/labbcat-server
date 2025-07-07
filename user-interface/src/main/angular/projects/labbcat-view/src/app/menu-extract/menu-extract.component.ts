import { Component, OnInit } from '@angular/core';

import { MessageService, LabbcatService } from 'labbcat-common';

@Component({
  selector: 'app-menu-extract',
  templateUrl: './menu-extract.component.html',
  styleUrl: './menu-extract.component.css'
})
export class MenuExtractComponent implements OnInit {
    processWithPraatEnabled = false;
    
    constructor(
        private labbcatService: LabbcatService
    ) { }
    
    ngOnInit(): void {
        this.readPraatSetting();
    }
    
    readPraatSetting(): void {
        this.labbcatService.labbcat.getSystemAttribute(
            "praatPath", (attribute, errors, messages) => {
                this.processWithPraatEnabled = attribute && attribute["value"];
        });
    }

}

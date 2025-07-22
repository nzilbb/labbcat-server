import { Component, OnInit } from '@angular/core';

import { MessageService, LabbcatService } from 'labbcat-common';

@Component({
  selector: 'app-dictionaries',
  templateUrl: './dictionaries.component.html',
  styleUrl: './dictionaries.component.css'
})
export class DictionariesComponent implements OnInit {

    managerIds:string[];
    dictionaries: { [managerId: string]: string[]; }

    constructor(
        private labbcatService: LabbcatService,
        private messageService: MessageService,
    ) {
    }

    ngOnInit(): void {
        this.readDictonaries();
    }

    readDictonaries(): void {
        this.labbcatService.labbcat.getDictionaries((dictionaries, errors, messages) => {
            this.dictionaries = {};
            this.managerIds = [];
            for (let managerId of Object.keys(dictionaries)) {
                if (dictionaries[managerId].length) { // don't add empty lists
                    this.dictionaries[managerId] = dictionaries[managerId];
                    this.managerIds.push(managerId);
                }
            }
        });
    }
}

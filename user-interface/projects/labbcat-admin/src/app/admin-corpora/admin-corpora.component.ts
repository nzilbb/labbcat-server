import { Component, OnInit } from '@angular/core';

import { Corpus } from '../corpus';
import { MessageService, LabbcatService, Response } from 'labbcat-common';
import { AdminComponent } from '../admin-component';

@Component({
  selector: 'app-admin-corpora',
  templateUrl: './admin-corpora.component.html',
  styleUrls: ['./admin-corpora.component.css']
})
export class AdminCorporaComponent extends AdminComponent implements OnInit {
    languages: any[];
    rows: Corpus[];
    
    constructor(
        labbcatService: LabbcatService,
        messageService: MessageService
    ) {
        super(labbcatService, messageService);
    }
    
    ngOnInit(): void {
        this.readLanguages();
        this.readRows();
    }

    readRows(): void {
        this.labbcatService.labbcat.readCorpora((corpora, errors, messages) => {
            this.rows = [];
            for (let corpus of corpora) {
                this.rows.push(corpus as Corpus);
            }
        });
    }

    readLanguages(): void {
        this.labbcatService.labbcat.getLayer("transcript_language", (layer, errors, messages) => {
            this.languages = [];
            for (let label in layer.validLabels) {
                if (label) {
                    this.languages.push({
                        label: label,
                        description: layer.validLabels[label]});
                }
            }
        });
    }

    onChange(row: Corpus) {
        row._changed = this.changed = true;        
    }

    creating = false;
    createRow(name: string, language: string, description: string): boolean {
        this.creating = true;
        this.labbcatService.labbcat.createCorpus(
            name, language, description,
            (row, errors, messages) => {
                this.creating = false;
                if (errors) errors.forEach(m => this.messageService.error(m));
                if (messages) messages.forEach(m => this.messageService.info(m));
                // update the model with the field returned
                if (row) this.rows.push(row as Corpus);
                this.updateChangedFlag();
            });
        return true;
    }

    deleteRow(row: Corpus) {
        row._deleting = true;
        if (confirm(`Are you sure you want to delete ${row.corpus_name}`)) {
            this.labbcatService.labbcat.deleteCorpus(row.corpus_name, (model, errors, messages) => {
                row._deleting = false;
                if (errors) errors.forEach(m => this.messageService.error(m));
                if (messages) messages.forEach(m => this.messageService.info(m));
                if (!errors) {
                    // remove from the model/view
                    this.rows = this.rows.filter(r => { return r !== row;});
                    this.updateChangedFlag();
                }});
        } else {
            row._deleting = false;
        }
    }
    
    updateChangedRows() {
        this.rows
            .filter(r => r._changed)
            .forEach(r => this.updateRow(r));
    }

    updating = 0;
    updateRow(row: Corpus) {
        this.updating++;
        this.labbcatService.labbcat.updateCorpus(
            row.corpus_name, row.corpus_language, row.corpus_description,
            (corpus, errors, messages) => {
                this.updating--;
                if (errors) errors.forEach(m => this.messageService.error(m));
                if (messages) messages.forEach(m => this.messageService.info(m));
                // update the model with the field returned
                const updatedRow = corpus as Corpus;
                const i = this.rows.findIndex(r => {
                    return r.corpus_name == updatedRow.corpus_name; })
                this.rows[i] = updatedRow;
                this.updateChangedFlag();
            });
    }
    
    updateChangedFlag() {
        this.changed = false;
        for (let row of this.rows) {
            if (row._changed) {
                this.changed = true;
                break; // only need to find one
            }
        } // next row
    }
}

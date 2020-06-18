import { Component, OnInit } from '@angular/core';

import { Response } from '../response';
import { Corpus } from '../corpus';
import { AdminCorporaService } from '../admin-corpora.service';

@Component({
  selector: 'app-admin-corpora',
  templateUrl: './admin-corpora.component.html',
  styleUrls: ['./admin-corpora.component.css']
})
export class AdminCorporaComponent implements OnInit {
    rows: Corpus[];
    changed = false;
    
    constructor(
        private adminCorporaService: AdminCorporaService
    ) { }
    
    ngOnInit(): void {
        this.readRows();
    }

    readRows(): void {
        this.adminCorporaService.readCorpora()
            .subscribe(response => this.rows = response.model);
    }

    onChange(row: Corpus) {
        row._changed = this.changed = true;        
    }
    
    deleteRow(row: Corpus) {
        this.adminCorporaService.deleteCorpus(row)
            .subscribe(response => {
                // when removal is successful, returnedRow == null
                if (!response.model) {
                    // remove from the model/view
                    this.rows = this.rows.filter(r => { return r !== row;});
                    this.updateChangedFlag();
                }});
    }

    updateChangedRows() {
        this.rows
            .filter(r => r._changed)
            .forEach(r => this.updateRow(r));
    }

    updateRow(row: Corpus) {
        this.adminCorporaService.updateCorpus(row)
            .subscribe(response => {
                const updatedRow = response.model as Corpus;
                // update the model with the field returned
                const i = this.rows.findIndex(r => {
                    return r.corpus_id == updatedRow.corpus_id; })
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

import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, ParamMap } from '@angular/router';
import { switchMap } from 'rxjs/operators';

import { Category } from '../category';
import { MessageService, LabbcatService, Response } from 'labbcat-common';
import { AdminComponent } from '../admin-component';

@Component({
  selector: 'app-admin-categories',
  templateUrl: './admin-categories.component.html',
  styleUrls: ['./admin-categories.component.css']
})
export class AdminCategoriesComponent extends AdminComponent implements OnInit {
    rows: Category[];
    scope: string;
    class_id: string;
    max_display_order = 0;
    
    constructor(
        labbcatService: LabbcatService,
        messageService: MessageService,
        private route: ActivatedRoute
    ) {
        super(labbcatService, messageService);
    }
    
    ngOnInit(): void {
        // need to subscribe to URL path changes, because the component will be re-used
        // from scope to scope
        this.route.paramMap.pipe(
            switchMap((params: ParamMap) => params.get('scope'))
        ).subscribe(scope => {
            this.readRows();
        });
    }
    
    readRows(): void {
        this.rows = null;
        this.scope = this.route.snapshot.paramMap.get('scope');
        this.class_id = this.scope == "participant"?"speaker":this.scope;
        this.labbcatService.labbcat.readCategories(
            this.class_id, (categories, errors, messages) => {
                this.rows = [];
                for (let category of categories) {
                    this.rows.push(category as Category);
                    this.max_display_order = Math.max(
                        this.max_display_order, category.display_order);
                }
            });
    }
    
    onChange(row: Category) {
        row._changed = this.changed = true;        
    }

    creating = false;
    createRow(category: string, description: string) {
        this.creating = true;
        this.labbcatService.labbcat.createCategory(
            this.class_id, category, description, this.max_display_order + 1,
            (row, errors, messages) => {
                this.creating = false;
                if (errors) errors.forEach(m => this.messageService.error(m));
                if (messages) messages.forEach(m => this.messageService.info(m));
                // update the model with the field returned
                if (row) {
                    this.rows.push(row as Category);
                    this.max_display_order = Math.max(
                        this.max_display_order, row.display_order);
                }
                this.updateChangedFlag();
            });
    }
    
    deleteRow(row: Category) {
        row._deleting = true;
        if (confirm(`Are you sure you want to delete ${row.category}`)) {
            this.labbcatService.labbcat.deleteCategory(
                this.class_id, row.category, (model, errors, messages) => {
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

    move(direction: string, row: Category) {
        let indexToMoveUp = this.rows.indexOf(row);
        let indexToMoveDown = indexToMoveUp - 1;
        if (direction == "down") {
            indexToMoveDown = indexToMoveUp;
            indexToMoveUp = indexToMoveUp + 1;
        }
        const toMoveUp = this.rows[indexToMoveUp];
        const toMoveDown = this.rows[indexToMoveDown];
        const newDisplayOrder = toMoveDown.display_order;
        toMoveDown.display_order = toMoveUp.display_order;
        toMoveUp.display_order = newDisplayOrder;
        this.rows[indexToMoveUp] = toMoveDown;
        this.rows[indexToMoveDown] = toMoveUp;
        this.onChange(toMoveUp);
        this.onChange(toMoveDown);
    }
    
    updateChangedRows() {
        this.rows
            .filter(r => r._changed)
            .forEach(r => this.updateRow(r));
    }
    
    updating = 0;
    updateRow(row: Category) {
        this.updating++;
        this.labbcatService.labbcat.updateCategory(
            this.class_id, row.category, row.description, row.display_order,
            (category, errors, messages) => {
                this.updating--;
                if (errors) errors.forEach(m => this.messageService.error(m));
                if (messages) messages.forEach(m => this.messageService.info(m));
                // update the model with the field returned
                const updatedRow = category as Category;
                const i = this.rows.findIndex(r => {
                    return r.category == updatedRow.category; })
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

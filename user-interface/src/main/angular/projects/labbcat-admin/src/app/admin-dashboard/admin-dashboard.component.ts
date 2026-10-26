import { Component, OnInit } from '@angular/core';
import { Router, ActivatedRoute, ParamMap } from '@angular/router';
import { environment } from '../../environments/environment';
import { switchMap } from 'rxjs/operators';
import {EditorView, basicSetup} from "codemirror";
import {sql} from "@codemirror/lang-sql";

import { MessageService, LabbcatService } from 'labbcat-common';
import { DashboardItem } from '../dashboard-item';
import { AdminComponent } from '../admin-component';

@Component({
  selector: 'app-admin-dashboard',
  templateUrl: './admin-dashboard.component.html',
  styleUrl: './admin-dashboard.component.css'
})
export class AdminDashboardComponent extends AdminComponent implements OnInit {
    scope: string;
    rows: DashboardItem[];
    imagesLocation = environment.imagesLocation;
    baseUrl: string;
    iconBaseUrl: string;
    icons: string[];
    changingIconId: string;
    newItemEditor: EditorView;
    newItemValue = " ";
    newItemError: string;
    sqlEditors = {};

    constructor(
        labbcatService: LabbcatService,
        messageService: MessageService,
        private route: ActivatedRoute,
        private router: Router
    ) {
        super(labbcatService, messageService);
        this.baseUrl = labbcatService.labbcat.baseUrl;
        this.iconBaseUrl = `${this.baseUrl}images/dashboard/`
    }

    ngOnInit(): void {
        // need to subscribe to URL path changes, because the component will be re-used
        // from scope to scope
        this.readBaseUrl();
        this.readIcons();
        this.route.paramMap.subscribe((params) => {
            this.scope = this.route.snapshot.paramMap.get('scope');
            this.readRows();
        });
    }
    changeScope(newScope) {
        if (newScope != this.scope) {
            this.router.navigate(['admin','dashboard',newScope]);
        }
    }
    
    readBaseUrl(): void {
        this.labbcatService.labbcat.getId((url, errors, messages) => {
            if (url) this.baseUrl = url;
        });
    }
    readIcons(): void {
        this.icons = null;
        this.labbcatService.labbcat.createRequest(
            "icon", null, (icons, errors, messages) => {
                if (errors) errors.forEach(m => this.messageService.error(m));
                if (messages) messages.forEach(m => this.messageService.info(m));
                if (icons) this.icons = icons;
            },
            `${this.baseUrl}api/admin/icon`)
            .send();
    }
    readRows(): void {
        this.rows = null;
        this.labbcatService.labbcat.readDashboardItems(
            this.scope, (items, errors, messages) => {
                if (errors) errors.forEach(m => this.messageService.error(m));
                if (messages) messages.forEach(m => this.messageService.info(m));
                this.rows = [];
                this.sqlEditors = {};
                for (let item of items) {
                    this.rows.push(item as DashboardItem);
                }
                if (this.scope == "sql") {
                    setTimeout(()=> {
                        // create editor
                        this.newItemEditor = new EditorView({
                            doc: "",
                            extensions: [
                                basicSetup,
                                EditorView.lineWrapping,
                                sql(),
                                EditorView.updateListener.of((e)=>{
                                    if (e.docChanged) {
                                        this.testDefinition(
                                            null, e.state.doc.toString());
                                    } 
                                })
                            ],
                            parent: document.getElementById("sql-new")
                        });
                        for (let item of this.rows) {
                            // create editor
                            this.sqlEditors[item.item_id] = new EditorView({
                                doc: item.definition,                                
                                extensions: [
                                    basicSetup,
                                    EditorView.lineWrapping,
                                    sql(),
                                    EditorView.updateListener.of((e)=>{
                                        if (e.docChanged) {
                                            this.onChange(item);
                                            this.testDefinition(
                                                item, e.state.doc.toString());
                                        } 
                                    })],
                                parent: document.getElementById(`sql-${item.item_id}`)
                            });
                            this.getResult(item);
                        } // next item
                    }, 200);
                } // scope==sql
            });
    }

    selectFile(item: DashboardItem, files: File[]): void {
        if (files.length == 0) return;
        const iconFile = files[0]
        if (!iconFile.name.endsWith(".svg")
            && !iconFile.name.endsWith(".png")
            && !iconFile.name.endsWith(".jpg")
            && !iconFile.name.endsWith(".gif")) {
            this.messageService.error("File must be an image file."); // TODO i18n
            return;
        }
        
        // post file
        const fd = new FormData();
        fd.append("icon", iconFile);
        const uploadRequest = this.labbcatService.labbcat.createRequest(
            "icon", null, (model, errors, messages) => {
                if (errors && errors.length) {
                    errors.forEach(m => this.messageService.error(m));
                } else {
                    if (messages) messages.forEach(m => this.messageService.info(m));                
                    this.readIcons();
                    item.icon = iconFile.name;
                    this.changingIconId = null;
                    this.onChange(item);
                }
            },
            `${this.baseUrl}api/admin/icon`, "POST");
        try {
            uploadRequest.send(fd);
        } catch (x) {
            this.messageService.error(x);
        }
    }
    
    onChange(row: DashboardItem) {
        row._changed = this.changed = true;        
    }
    
    creating = false;
    createRow(
        label: string, icon: string, definition: string,
        dashboard: boolean, express: boolean, statistics: boolean,
        display_order: number): boolean {

        if (this.scope == "sql") {
            // get the SQL content from the editor
            definition = this.newItemEditor.state.doc.toString();
            if (definition == "") {
                this.messageService.error("Please enter an SQL query for the item."); // TODO i18n
                return;
            }
        } else {
            if (definition == "") {
                this.messageService.error("Please enter a page to link to"); // TODO i18n
                return;
            }
        }
        
        this.creating = true;
        this.labbcatService.labbcat.createDashboardItem(
            this.scope, label, icon, definition, dashboard, express, statistics,
            display_order, (row, errors, messages) => {
                this.creating = false;
                if (errors) errors.forEach(m => this.messageService.error(m));
                if (messages) messages.forEach(m => this.messageService.info(m));
                // update the model with the field returned
                if (row && (!errors || errors.lenth == 0)) {
                    this.rows.push(row as DashboardItem);
                }
                this.updateChangedFlag();
                if (this.scope == "sql") {
                    setTimeout(()=> {
                        // create editor
                        this.sqlEditors[row.item_id] = new EditorView({
                            doc: row.definition,                                
                            extensions: [
                                basicSetup,
                                EditorView.lineWrapping,
                                sql(),
                                EditorView.updateListener.of((e)=>{
                                    if (e.docChanged) {
                                        this.onChange(row);
                                    } 
                                })],
                            parent: document.getElementById(`sql-${row.item_id}`)
                        });
                        this.getResult(row);
                    }, 200);
                } // scope==sql
            });
        return true;
    }

    deleteRow(row: DashboardItem) {
        row._deleting = true;
        if (confirm(`Are you sure you want to delete ${row.label}`)) { // TODO i18n
            this.labbcatService.labbcat.deleteDashboardItem(
                this.scope, row.item_id, (model, errors, messages) => { 
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
    updateRow(row: DashboardItem) {
        this.updating++;
        if (this.scope == "sql") {
            // get the SQL content from the editor
            row.definition = this.sqlEditors[row.item_id].state.doc.toString();
        }
        this.labbcatService.labbcat.updateDashboardItem(
            this.scope, row.item_id, row.label, row.icon, row.definition,
            row.dashboard, row.express, row.statistics, row.display_order,
            (item, errors, messages) => {
                this.updating--;
                if (errors) errors.forEach(m => this.messageService.error(m));
                if (messages) messages.forEach(m => this.messageService.info(m));
                // update the model with the field returned
                const updatedRow = item as DashboardItem;
                const i = this.rows.findIndex(r => {
                    return r.item_id == updatedRow.item_id; })
                this.rows[i] = updatedRow;
                this.updateChangedFlag();
                if (this.scope == "sql") {
                    setTimeout(()=> {
                        // recreate editor
                        this.sqlEditors[row.item_id] = new EditorView({
                            doc: updatedRow.definition,                                
                            extensions: [
                                basicSetup,
                                EditorView.lineWrapping,
                                sql(),
                                EditorView.updateListener.of((e)=>{
                                    if (e.docChanged) {
                                        this.onChange(updatedRow);
                                    } 
                                })],
                            parent: document.getElementById(`sql-${updatedRow.item_id}`)
                        });
                        this.getResult(updatedRow);
                    }, 200);
                } // scope==sql
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

    getResult(item: DashboardItem) {
        this.labbcatService.labbcat.getDashboardItem(
            item.item_id, (value, errors, messages) => {
                if (errors && errors.length) {
                    item.error = errors.join("\n");
                } 
                item.value = value;
            });
    }

    testDelay = null;
    testDefinition(item: DashboardItem, definition: string) {
        if (this.testDelay) {
            clearTimeout(this.testDelay);
        }
        if (item) {
            item.value = item.error = "";
        } else {
            this.newItemValue = this.newItemError = "";
        }
        this.testDelay = setTimeout(()=>{
            this.labbcatService.labbcat.createRequest(
                "definition", {definition: definition}, (value, errors, messages) => {
                    if (errors && errors.length) {
                        if (item) {
                            item.error = errors.join("\n");
                        } else {
                            this.newItemError = errors.join("\n");
                        }
                    } 
                    if (item) {
                        item.value = value;
                    } else {
                        this.newItemValue = value;
                    }
                },
                `${this.baseUrl}api/admin/dashboard/test`)
                .send();
        }, 2000);
    }
}

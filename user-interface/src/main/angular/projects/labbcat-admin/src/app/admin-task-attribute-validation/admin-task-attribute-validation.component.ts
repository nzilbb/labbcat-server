import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, ParamMap } from '@angular/router';
import { switchMap } from 'rxjs/operators';
import {EditorView, basicSetup} from "codemirror";
import {javascript, javascriptLanguage} from "@codemirror/lang-javascript";
import {CompletionContext} from "@codemirror/autocomplete"

import { MessageService, LabbcatService } from 'labbcat-common';
import { AdminComponent } from '../admin-component';

@Component({
  selector: 'app-admin-task-attribute-validation',
  templateUrl: './admin-task-attribute-validation.component.html',
  styleUrl: './admin-task-attribute-validation.component.css'
})
export class AdminTaskAttributeValidationComponent extends AdminComponent implements OnInit {
    task_id: number;
    scope: string;
    attribute: string;
    task_name: string;
    editor: EditorView;
    updating = false;

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
            switchMap((params: ParamMap) => params.get('task_id'))
        ).subscribe(task_id => {
            this.task_id = parseInt(this.route.snapshot.paramMap.get('task_id'));
            this.scope = this.route.snapshot.paramMap.get('scope');
            this.attribute = this.route.snapshot.paramMap.get('attribute');
            this.readTask()
                .then(()=>{
                    this.readScript();
                });
        });
    }
    readTask(): Promise<number> {
        this.updating = true;
        return new Promise<number>((accept,reject)=>{
            this.labbcatService.labbcat.readElicitationTasks((tasks, errors, messages) => {
                this.updating = false;
                for (let task of tasks) {
                    if (task.task_id == this.task_id) {
                        this.task_name = task.task_name;
                        accept(this.task_id);
                        return;
                    }
                } // next task
                this.messageService.error(`Invalid task: ${this.task_id}`); // TODO i18n
                reject();
            });
        });
    }
    readScript(): void {
        this.updating = true;
        const tagOptions = [
            "constructor", "deprecated", "link", "param", "returns", "type"
        ].map(tag => ({label: "@" + tag, type: "keyword"}))
        this.labbcatService.labbcat.readElicitedAttributeValidation(
            this.task_id, this.scope, this.attribute, (script, errors, messages) => {
                if (errors) errors.forEach(m => this.messageService.error(m));
                if (messages) messages.forEach(m => this.messageService.info(m));
                this.updating = false;
                this.editor = new EditorView({
                    doc: script,
                    extensions: [
                        basicSetup,
                        javascript(),
                        // add autocomplete for isValid() and isInvalid(message)
                        javascriptLanguage.data.of({
                            autocomplete: (context: CompletionContext)=>{
                                const word = context.matchBefore(/\w*/)
                                if (word.from == word.to && !context.explicit) return null;
                                return {
                                    from: word.from,
                                    options: [{
                                        label: "isValid()", type: "function",
                                        apply: "isValid()",
                                        info: "...if the value is valid."
                                    },{
                                        label: "isInvalid(message)", type: "function",
                                        apply: "isInvalid(`${value} is invalid`);",
                                        info: "...if the value is not valid."
                                    }]
                                }
                            }
                        }),
                        EditorView.updateListener.of((e)=>{
                            if (e.docChanged) {
                                this.changed = true;
                            } 
                        })],
                    parent: document.getElementById("script")
                });
            });
    }

    updateScript() {
        const script = this.editor.state.doc.toString();
        // check they call isValid() somewhere
        if (script && !/isValid\(\)/.test(script)) {
            alert("If you never call isValid() anywhere in the script,"
                  +" the participant will not be able to continue.");
        } else {
            this.updating = true;
            this.labbcatService.labbcat.updateElicitedAttributeValidation(
                this.task_id, this.scope, this.attribute, script,
                (response, errors, messages) => {
                    this.updating = false;
                    if (errors) errors.forEach(m => this.messageService.error(m));
                    if (messages) messages.forEach(m => this.messageService.info(m));
                    // update the model with the field returned
                    this.changed = false;
                });
        }
    }

}

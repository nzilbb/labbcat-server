import { Component, OnInit } from '@angular/core';

import { SystemAttribute } from '../system-attribute';
import { MessageService, LabbcatService, Response } from 'labbcat-common';
import { AdminComponent } from '../admin-component';

@Component({
  selector: 'app-admin-system-attributes',
  templateUrl: './admin-system-attributes.component.html',
  styleUrls: ['./admin-system-attributes.component.css']
})
export class AdminSystemAttributesComponent extends AdminComponent implements OnInit {
    attributes: SystemAttribute[];

    constructor(
        labbcatService: LabbcatService,
        messageService: MessageService
    ) {
        super(labbcatService, messageService);
    }
    
    ngOnInit(): void {
        this.readAttributes();
    }
    
    readAttributes(): void {
        this.labbcatService.labbcat.readSystemAttributes((attributes, errors, messages) => {
            this.attributes = [];
            for (let attribute of attributes) {
                this.attributes.push(attribute as SystemAttribute);
            }
        });
    }
    
    onChange(attribute: SystemAttribute) {
        attribute._changed = this.changed = true;        
    }
    
    updateChangedAttributes() {
        this.attributes
            .filter(r => r._changed)
            .forEach(r => this.updateAttribute(r));
    }

    updating = 0;
    updateAttribute(attribute: SystemAttribute) {
        this.updating++;
        this.labbcatService.labbcat.updateSystemAttribute(
            attribute.attribute, ""+attribute.value, // ensure value sent as a string
            (attribute, errors, messages) => {
                this.updating--;
                if (errors) errors.forEach(m => this.messageService.error(m));
                if (messages) messages.forEach(m => this.messageService.info(m));
                // if there were errors, reload to ensure the model is correct
                if (errors) {
                    this.readAttributes();
                } else {
                    const updatedRow = attribute as SystemAttribute;                    
                    const i = this.attributes.findIndex(r => {
                        return r.attribute == updatedRow.attribute; })
                    this.attributes[i]._changed = false;
                    this.updateChangedFlag();
                }                    
            });
    }
    
    updateChangedFlag() {
        this.changed = false;
        for (let attribute of this.attributes) {
            if (attribute._changed) {
                this.changed = true;
                break; // only need to find one
            }
        } // next attribute
    }

    optionValues(attribute: SystemAttribute): string[] {
        return Object.keys(attribute.options);
    }
}

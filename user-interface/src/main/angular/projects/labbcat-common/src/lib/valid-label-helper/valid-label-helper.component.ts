import { Component, OnInit, EventEmitter, Output, Input } from '@angular/core';

import { Layer } from '../layer';
import { ValidLabelDefinition } from '../valid-label-definition';

@Component({
  selector: 'lib-valid-label-helper',
  templateUrl: './valid-label-helper.component.html',
  styleUrls: ['./valid-label-helper.component.css']
})
export class ValidLabelHelperComponent implements OnInit {
    @Input() layer: Layer;
    @Input() regularExpression: boolean;
    @Input() nbText: string;
    @Input() nbCategory: string;
    @Output() symbolSelected = new EventEmitter<string>();

    categories: any;
    maxLabelLength = 0;
    nbLabels = [];
    
    Object = Object; // so we can call Object.keys in the template

    constructor() { }
    
    ngOnInit(): void {
        if (this.layer.validLabelsDefinition) { // enable hierarchical picker layout
            this.categories = {};
            for (let definition of this.layer.validLabelsDefinition) { // for each label
                this.maxLabelLength = Math.max(definition.label.length, this.maxLabelLength);
                definition.description = definition.description || definition.label;
                definition.category = definition.category || "";
                definition.subcategory = definition.subcategory || "";
                if (!this.categories[definition.category]) {
                    this.categories[definition.category] = {};
                }
                if (!this.categories[definition.category][definition.subcategory]) {
                    this.categories[definition.category][definition.subcategory] = [];
                }
                this.categories[definition.category][definition.subcategory].push(definition);
                if (!definition.display && !definition.selector) {
                    this.nbLabels.push(definition.label);
                }
            } // next label
        }
    }
    
    select(event: Event, symbol: string): boolean {
        if (this.regularExpression) {
            // escape for regular expression
            symbol = symbol.replace(/([\?\.\*\|\^\$\(\)\{\}])/g,"\\$1");
        }
        this.symbolSelected.emit(symbol);
        if (event) event.stopPropagation();
        return false;
    }
    
    selectLabels(labels: ValidLabelDefinition[]): void {
        let pattern = ""
        if (this.maxLabelLength == 1) { // can use [...]
            for (let label of labels) {
                if (!pattern) pattern = "[";
                let symbol = label.label;
                // escape for regular expression
                if (symbol == "]" || symbol == "^" || symbol == "-") {
                    symbol = "\\" + symbol;
                }
                pattern += symbol;
            }
            if (pattern) pattern += "]";
        } else { // multi-character symbols - have to use (...|...|...)
            for (let label of labels) {
                if (!pattern) {
                    pattern = "(";
                } else {
                    pattern += "|";
                }
                let symbol = label.label;
                // escape for regular expression
                if (symbol == "|" || symbol == "(" || symbol == ")"
                    || symbol == "^" || symbol == "$") {
                    symbol = "\\" + symbol;
                }
                pattern += symbol;
            }
            if (pattern) pattern += ")";
        }
        if (pattern) {
            this.symbolSelected.emit(pattern);
        }
    }
    selectCategory(event: Event, category: string): boolean {
        const labels = [] as ValidLabelDefinition[];
        for (let subcategory of Object.keys(this.categories[category])) {
            for (let label of this.categories[category][subcategory]) {
                labels.push(label);
            } // next label
        } // next subcategory
        if (labels.length) {
            this.selectLabels(labels);
        }
        if (event) event.stopPropagation();
        return false;
    }
    selectSubcategory(event: Event, category: string, subcategory: string): boolean {
        const labels = [] as ValidLabelDefinition[];
        for (let label of this.categories[category][subcategory]) {
            labels.push(label);
        } // next label
        if (labels.length) {
            this.selectLabels(labels);
        }
        if (event) event.stopPropagation();
        return false;
    }
    
}

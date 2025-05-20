import { Component, OnInit, Input, EventEmitter, Output } from '@angular/core';
import { Inject } from '@angular/core';

@Component({
  selector: 'lib-button',
  templateUrl: './button.component.html',
  styleUrls: ['./button.component.css']
})
export class ButtonComponent implements OnInit {
    @Input() action: string;
    @Input() title: string;
    @Input() label: string;
    @Input() icon: string;
    @Input() img: string;
    @Input() imgStyle: string;
    @Input() disabled: boolean;
    @Output() press = new EventEmitter();
    @Input() processing: boolean;
    @Input() error: string;
    @Input() autofocus: boolean;

    imagesLocation : string;
    classes = "btn";
    
    constructor(@Inject('environment') private environment) {
        this.imagesLocation = this.environment.imagesLocation;
    }
    
    ngOnInit(): void {
        switch (this.action) {
            case "create":
                this.label = "New"; // TODO l10n
                this.icon = "➕";
                this.img = "add.svg"; // TODO replace with svg, or maybe just use icon
                break;
            case "delete":
                this.label = "Delete"; // TODO l10n
                this.icon = "➖"; 
                this.img = "trash.svg"; // TODO replace with svg
                break;
            case "save":
                this.label = "Save"; // TODO l10n
                this.icon = "💾";
                this.img = "save.svg"; // TODO replace with svg
                break;
        }
        this.title = this.title || this.label;
        if (this.action) this.classes = this.action + "-btn";
        if (!this.label) this.classes += " icon-only";
    }

    handlePress(event: Event): void {
        this.press.emit(event);
    }
}

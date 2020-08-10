import { Component, OnInit, Input, EventEmitter, Output } from '@angular/core';
import { environment } from '../../environments/environment';

@Component({
  selector: 'app-button',
  templateUrl: './button.component.html',
  styleUrls: ['./button.component.css']
})
export class ButtonComponent implements OnInit {
    @Input() action: string;
    @Input() title: string;
    @Input() label: string;
    @Input() icon: string;
    @Input() img: string;
    @Input() disabled: boolean;
    @Output() click = new EventEmitter();

    imagesLocation = environment.imagesLocation;
    classes = "btn";
    
    constructor() { }
    
    ngOnInit(): void {
        switch (this.action) {
            case "create":
                this.label = "New"; // TODO l10n
                this.icon = "➕";
                this.img = "new.png"; // TODO replace with svg, or maybe just use icon
                break;
            case "delete":
                this.label = "Delete"; // TODO l10n
                this.icon = "➖"; 
                this.img = "delete.png"; // TODO replace with svg
                break;
            case "save":
                this.label = "Save"; // TODO l10n
                this.icon = "💾";
                this.img = "save.png"; // TODO replace with svg
                break;
        }
        this.title = this.title || this.label;
        if (this.action) this.classes = this.action + "-btn";
        if (!this.label) this.classes += " icon-only";
    }

    handleClick(): void {
        this.click.emit();
    }
}

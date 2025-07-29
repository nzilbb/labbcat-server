import { Component, OnInit, Input, EventEmitter, Output } from '@angular/core';
import { Inject } from '@angular/core';

@Component({
  selector: 'lib-small-button',
  templateUrl: './small-button.component.html',
  styleUrl: './small-button.component.css'
})
export class SmallButtonComponent implements OnInit {
    @Input() action: string;
    @Input() title: string;
    @Input() icon: string;
    @Input() img: string;
    @Input() imgStyle: string;
    @Input() disabled: boolean;
    @Input() selected: boolean;
    @Output() press = new EventEmitter();

    imagesLocation : string;
    classes = "btn";

    constructor(@Inject('environment') private environment) {
        this.imagesLocation = this.environment.imagesLocation;
    }
    
    ngOnInit(): void {
        switch (this.action) {
            case "create":
                this.icon = "➕";
                this.img = "add.svg";
                break;
            case "delete":
                this.icon = "➖"; 
                this.img = "trash.svg";
                break;
            case "edit":
                this.icon = "✏️";
                this.img = "edit.svg";
                break;
            case "save":
                this.icon = "💾";
                this.img = "save.svg";
                break;
        }
        this.title = this.title;
        if (this.action) this.classes = this.action + "-btn";
    }
    
    handlePress(event: Event): void {
        if (!this.disabled) {
            this.press.emit(event);
        }
    }
}

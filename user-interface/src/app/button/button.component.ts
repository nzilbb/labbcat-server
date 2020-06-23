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
    @Input() disabled: boolean;
    @Output() click = new EventEmitter();

    imagesLocation = environment.baseUrl + environment.imagesLocation;
    label: string;
    icon: string;
    img: string;
    
    constructor() { }
    
    ngOnInit(): void {
        switch (this.action) {
            case "create":
                this.label = "New";
                this.icon = "➕";
                this.img = "new.png"; // TODO replace with svg, or maybe just use icon
                break;
            case "delete":
                this.label = "Delete";
                this.icon = "➖"; 
                this.img = "delete.png"; // TODO replace with svg
                break;
            default:
                this.label = "Save";
                this.icon = "💾";
                this.img = "save.png"; // TODO replace with svg
                break;
        }
    }

    handleClick(): void {
        this.click.emit();
    }
}

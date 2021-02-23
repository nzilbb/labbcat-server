import { Component, OnInit } from '@angular/core';
import { environment } from '../../environments/environment';

@Component({
  selector: 'app-admin-menu',
  templateUrl: './admin-menu.component.html',
  styleUrls: ['./admin-menu.component.css']
})
export class AdminMenuComponent implements OnInit {
    // TODO for now the production environment uses individual components, and so we don't
    // TODO want menus
    visible = !environment.production;
    
    constructor() { }
    
    ngOnInit(): void {
    }
    
}

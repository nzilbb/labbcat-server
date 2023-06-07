import { Component } from '@angular/core';
import { environment } from '../environments/environment';

@Component({
  selector: 'app-root',
  templateUrl: './app.component.html',
  styleUrls: ['./app.component.css']
})
export class AppComponent {
    // TODO for now the production environment uses individual components hosted inside
    // TODO the classic, and so we don't want heading/footer, because the host page
    // TODO already has them 
    title = "LaBB-CAT";
    production = environment.production;
}

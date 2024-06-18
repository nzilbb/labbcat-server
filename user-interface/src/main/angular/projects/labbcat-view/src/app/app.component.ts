import { Component } from '@angular/core';
import { environment } from '../environments/environment';
import { Router, NavigationEnd } from '@angular/router';

@Component({
  selector: 'app-root',
  templateUrl: './app.component.html',
  styleUrls: ['./app.component.css']
})
export class AppComponent {
    // TODO for now the production environment uses individual components hosted inside
    // TODO the classic, and so we don't want header/footer, because the host page
    // TODO already has them 
    title = "LaBB-CAT";
    production = environment.production;
    constructor(private router: Router) {
        router.events.subscribe((event) => {
            if (event instanceof NavigationEnd) {
                // publish page changes, so the page header can update help links 
                window.postMessage({
                    app: "labbcat-view",
                    url: event.url
                });
            }
        });
    }
}

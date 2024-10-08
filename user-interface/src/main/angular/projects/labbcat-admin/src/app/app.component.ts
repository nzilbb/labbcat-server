import { Component } from '@angular/core';
import { environment } from '../environments/environment';
import { Router, NavigationEnd } from '@angular/router';
import { Title } from '@angular/platform-browser';
import { LabbcatService } from 'labbcat-common';

@Component({
  selector: 'app-root',
  templateUrl: './app.component.html',
  styleUrls: ['./app.component.css']
})
export class AppComponent {
    title = "";
    production = environment.production;
    constructor(
        private router: Router,
        private titleService: Title,
        private labbcatService: LabbcatService
    ) {
        router.events.subscribe((event) => {
            if (event instanceof NavigationEnd) {
                // publish page changes, so the page header can update help links 
                window.postMessage({
                    app: "labbcat-view",
                    url: event.url
                });
                const h1 = document.getElementById("title");
                if (h1) {
                    this.setPageTitle(h1.textContent);
                } else { // no title element, so use URL
                    this.setPageTitle(event.url
                        .replace(/^\//,"") // strip initial slash
                        .replace(/\//g," » ")); // transform remaining slashes
                }
            }
        });
    }

    setPageTitle(pageTitle: string) {
        if (this.title) {
            this.titleService.setTitle(`${pageTitle} - ${this.title}`);
        } else {
            this.labbcatService.labbcat.getSystemAttribute("title", (attribute) => {
                this.title = attribute.value;
                this.titleService.setTitle(`${pageTitle} - ${this.title}`);
            });
        }
    }
}

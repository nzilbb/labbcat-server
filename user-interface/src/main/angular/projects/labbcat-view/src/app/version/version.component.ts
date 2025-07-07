import { Component, OnInit } from '@angular/core';

import { PraatService } from '../praat.service';
import { MessageService, LabbcatService, VersionInfo } from 'labbcat-common';

@Component({
  selector: 'app-version',
  templateUrl: './version.component.html',
  styleUrl: './version.component.css'
})
export class VersionComponent implements OnInit {

    versions : VersionInfo;
    praatExtensionVersion: string;
    praatNativeMessagingVersion: string;
    praatService : PraatService
    
    constructor(
        private labbcatService: LabbcatService,
        private messageService: MessageService,
        praatService : PraatService
    ) {
        this.praatService = praatService;
    }
    
    ngOnInit(): void {
        this.readServerVersions();
        this.readClientVersions();
    }
    
    readServerVersions(): void {
        this.labbcatService.labbcat.versionInfo((versions, errors, messages) => {
            this.versions = versions;
        });
    }

    readClientVersions(): void {
        window.setTimeout(()=>{ // give time for the extension to activate
            this.praatService.initialize().then((version: string)=>{
                console.log(`Praat integration version ${version}`);
                this.praatExtensionVersion = version;
            }, (canInstall: boolean)=>{
                if (canInstall) {
                    this.praatExtensionVersion = "Installable"; // TODO i18n
                } else {
                    this.praatExtensionVersion = "Incompatible"; // TODO i18n
                }
            });
        }, 1000);
    }

    ids(section: { [id: string]: string; } ): string[] {
        return Object.keys(section);
    }
}

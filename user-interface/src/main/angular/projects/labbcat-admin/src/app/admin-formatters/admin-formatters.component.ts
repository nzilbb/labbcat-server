import { Component, OnInit } from '@angular/core';
import { environment } from '../../environments/environment';

import { MessageService, LabbcatService, SerializationDescriptor } from 'labbcat-common';
import { AdminComponent } from '../admin-component';

@Component({
  selector: 'app-admin-formatters',
  templateUrl: './admin-formatters.component.html',
  styleUrl: './admin-formatters.component.css'
})
export class AdminFormattersComponent extends AdminComponent implements OnInit {
    imagesLocation = environment.imagesLocation;

    rows: { [mimeType: string]: SerializationDescriptor; }

    fileSelector = false;
    formatterFile: File;
    creating = false;
    percentCompleted: number;
    uploadedFormatter: SerializationDescriptor;
    jar: string;
    previousVersion: string;
    
    constructor(
        labbcatService: LabbcatService,
        messageService: MessageService,
    ) {
        super(labbcatService, messageService);
    }

    ngOnInit(): void {
        this.readRows();
    }    

    readRows(): void {
        this.rows = {};
        this.labbcatService.labbcat.getSerializerDescriptors(
            (descriptors, errors, messages) => {
                for (let descriptor of descriptors) {
                    this.rows[descriptor.mimeType] = descriptor as SerializationDescriptor;
                    this.rows[descriptor.mimeType].serializer = true;
                } // next serializer
                
                this.labbcatService.labbcat.getDeserializerDescriptors(
                    (descriptors, errors, messages) => {
                        for (let descriptor of descriptors) {
                            if (!this.rows[descriptor.mimeType]) {
                                this.rows[descriptor.mimeType] = descriptor as SerializationDescriptor;
                            }
                            this.rows[descriptor.mimeType].deserializer = true;
                        } // next serializer
                    }); // getDeserializerDescriptors
            }); // getSerializerDescriptors
    }

    startInstallation() {
        this.fileSelector = true;
        window.setTimeout(()=>{
            document.getElementById("formatterFile").click();
        }, 100);
    }
    /** Called when a formatter file is selected; parses the file to determine CSV fields. */
    selectFile(files: File[]): void {
        if (files.length == 0) {
            this.fileSelector = false;
            return;
        }
        this.formatterFile = files[0]
        if (!this.formatterFile.name.endsWith(".jar")) {
            this.messageService.error("File must be a formatter file, e.g. nzilbb.formatter.xxx.jar"); // TODO i18n
            this.formatterFile = null;
            this.fileSelector = false;
            return;
        }
        this.upload();
    }
    
    upload() {
        this.labbcatService.labbcat.uploadSerialization(
            this.formatterFile, (serialization, errors, messages) => {
                if (errors) {
                    for (let message of errors) {
                        this.messageService.error(message);
                    }
                }
                if (messages) {
                    for (let message of messages) {
                        this.messageService.info(message);
                    }
                }
                if (serialization) {
                    this.uploadedFormatter = serialization as SerializationDescriptor;
                    this.jar = serialization.jar;
                    this.previousVersion = serialization.installedVersion;
                }
            }, (event) => {
                this.percentCompleted = Math.round(100 * event.loaded / event.total);
            });
    }
    
    install(install: boolean) {
        this.labbcatService.labbcat.installSerialization(
            this.jar, install, (response, errors, messages) => {
                this.creating = false;
                if (errors) {
                    for (let message of errors) {
                        this.messageService.error(message);
                    }
                }
                if (messages) {
                    for (let message of messages) {
                        this.messageService.info(message);
                    }
                }
                this.percentCompleted = this.uploadedFormatter = this.jar
                    = this.previousVersion = null;
                // navigate to settings page
                document.location = environment.baseUrl
                    +"admin/serialization?mimetype="
                    +encodeURIComponent(response.mimeType).replace("+", "%2B");
            });
    }
    
    uninstall(mimeType: string) {
        const descriptor = this.rows[mimeType];
        if (confirm(`Are you sure you want to delete ${descriptor.name}`)) { // TODO i18n
            this.labbcatService.labbcat.uninstallSerialization(
                descriptor.mimeType, (response, errors, messages) => {
                    if (errors) {
                        for (let message of errors) {
                            this.messageService.error(message);
                        }
                    }
                    if (messages) {
                        for (let message of messages) {
                            this.messageService.info(message);
                        }
                    }
                    // refresh the list
                    this.readRows();
                });
        } // are you sure?
    }
}

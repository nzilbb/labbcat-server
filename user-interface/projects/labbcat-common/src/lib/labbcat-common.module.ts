import { NgModule, ModuleWithProviders } from '@angular/core';
import { MessagesComponent } from './messages/messages.component';
import { KeepAliveComponent } from './keep-alive/keep-alive.component';
import { ButtonComponent } from './button/button.component';
import { TaskComponent } from './task/task.component';

@NgModule({
    declarations: [
        MessagesComponent,
        ButtonComponent,
        KeepAliveComponent,
        TaskComponent
    ],
    imports: [
    ],
    exports: [
        MessagesComponent,
        ButtonComponent,
        KeepAliveComponent,
        TaskComponent
    ]
})
export class LabbcatCommonModule {
    public static forRoot(environment: any): ModuleWithProviders {
        return {
            ngModule: LabbcatCommonModule,
            providers: [
                {
                    provide: 'environment',
                    useValue: environment
                }
            ]
        };
    }
}

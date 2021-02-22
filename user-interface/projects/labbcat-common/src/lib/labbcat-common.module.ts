import { NgModule, ModuleWithProviders } from '@angular/core';
import { UrlEncodePipe } from './url-encode.pipe';
import { MessagesComponent } from './messages/messages.component';
import { KeepAliveComponent } from './keep-alive/keep-alive.component';
import { ButtonComponent } from './button/button.component';
import { TaskComponent } from './task/task.component';
import { WaitComponent } from './wait/wait.component';
import { GroupedCheckboxComponent } from './grouped-checkbox/grouped-checkbox.component';
import { LayerCheckboxesComponent } from './layer-checkboxes/layer-checkboxes.component';

@NgModule({
    declarations: [
        UrlEncodePipe,
        MessagesComponent,
        ButtonComponent,
        KeepAliveComponent,
        WaitComponent,
        TaskComponent,
        GroupedCheckboxComponent,
        LayerCheckboxesComponent
    ],
    imports: [
    ],
    exports: [
        UrlEncodePipe,
        MessagesComponent,
        ButtonComponent,
        KeepAliveComponent,
        WaitComponent,
        TaskComponent,
        GroupedCheckboxComponent,
        LayerCheckboxesComponent
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

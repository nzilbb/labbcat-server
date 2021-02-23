import { NgModule, ModuleWithProviders } from '@angular/core';
import { RouterModule } from '@angular/router';
import { UrlEncodePipe } from './url-encode.pipe';
import { MessagesComponent } from './messages/messages.component';
import { KeepAliveComponent } from './keep-alive/keep-alive.component';
import { ButtonComponent } from './button/button.component';
import { TaskComponent } from './task/task.component';
import { WaitComponent } from './wait/wait.component';
import { GroupedCheckboxComponent } from './grouped-checkbox/grouped-checkbox.component';
import { LayerCheckboxesComponent } from './layer-checkboxes/layer-checkboxes.component';
import { DiscHelperComponent } from './disc-helper/disc-helper.component';
import { IpaHelperComponent } from './ipa-helper/ipa-helper.component';
import { AboutComponent } from './about/about.component';
import { LinkComponent } from './link/link.component';
import { LoginComponent } from './login/login.component';
import { AutofocusDirective } from './autofocus.directive';

@NgModule({
    declarations: [
        UrlEncodePipe,
        MessagesComponent,
        ButtonComponent,
        KeepAliveComponent,
        WaitComponent,
        TaskComponent,
        GroupedCheckboxComponent,
        LayerCheckboxesComponent,
        DiscHelperComponent,
        IpaHelperComponent,
        AboutComponent,
        LinkComponent,
        LoginComponent,
        AutofocusDirective
    ],
    imports: [
        RouterModule
    ],
    exports: [
        UrlEncodePipe,
        MessagesComponent,
        ButtonComponent,
        KeepAliveComponent,
        WaitComponent,
        TaskComponent,
        GroupedCheckboxComponent,
        LayerCheckboxesComponent,
        DiscHelperComponent,
        IpaHelperComponent,
        AboutComponent,
        LinkComponent,
        LoginComponent,
        AutofocusDirective
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

import { Component, OnInit, Inject } from '@angular/core';

import { MessageService, LabbcatService } from 'labbcat-common';
import { DashboardItem } from '../dashboard-item';

@Component({
  selector: 'app-statistics',
  templateUrl: './statistics.component.html',
  styleUrl: './statistics.component.css'
})
export class StatisticsComponent implements OnInit {

    baseUrl: string;
    title: string;
    link: DashboardItem[];
    sql: DashboardItem[];
    exec: DashboardItem[];
    
    constructor(
        private labbcatService: LabbcatService,
        private messageService: MessageService,
        @Inject('environment') private environment
    ) {
        this.baseUrl = this.environment.baseUrl;
    }
    
    ngOnInit(): void {
        this.readItems();
    }

    readItems(): void {
        this.labbcatService.labbcat.getDashboardItems(
            "statistics", (items, errors, messages) => {
                this.title = this.labbcatService.title;
                
                this.link = [];
                this.sql = [];
                this.exec = [];
                for (let i of items) {
                    const item = i as DashboardItem
                    switch (item.type) {
                        case "link":
                            this.link.push(item);
                            break;
                        case "sql":
                            this.sql.push(item);
                            break;
                        case "exec":
                            this.exec.push(item);
                            break;                            
                    }
                    this.readItem(item);
                } // next item
            });
    }
    
    readItem(item: DashboardItem): void {
        this.labbcatService.labbcat.getDashboardItem(
            item.item_id, (value, errors, messages) => {
                if (errors && errors.length) {
                    item.error = errors.join("\n");
                } 
                item.value = value;
            });
    }
}

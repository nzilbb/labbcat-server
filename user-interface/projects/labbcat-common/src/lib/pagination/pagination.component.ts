import { Component, OnInit, Input, EventEmitter, Output } from '@angular/core';

@Component({
  selector: 'lib-pagination',
  templateUrl: './pagination.component.html',
  styleUrls: ['./pagination.component.css']
})
export class PaginationComponent implements OnInit {

    @Input() currentPage: number;
    @Input() pageLinks: string[];
    @Output() goToPage = new EventEmitter<number>();
    showAll = false;
    
    constructor() { }
    
    ngOnInit(): void {
        this.showAll = this.currentPage > 4;
    }
    
    go(page: number): boolean {
        this.goToPage.emit(page);
        return false;
    }
    
    toggleShowAll(): boolean {
        this.showAll = !this.showAll;
        return false;
    }
    
}

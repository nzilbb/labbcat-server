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
    @Output() showAllPages = new EventEmitter<number>();
    showAllLinks = false;
    
    constructor() { }
    
    ngOnInit(): void {
        this.showAllLinks = this.currentPage > 4;
    }
    
    go(page: number): boolean {
        this.goToPage.emit(page);
        this.currentPage = page;
        if (page > 3 && page < this.pageLinks.length) this.showAllLinks = true;
        return false;
    }
    
    all(): boolean {
        this.showAllPages.emit();
        return false;
    }
    
    toggleShowAllLinks(): boolean {
        this.showAllLinks = !this.showAllLinks;
        return false;
    }
    
}

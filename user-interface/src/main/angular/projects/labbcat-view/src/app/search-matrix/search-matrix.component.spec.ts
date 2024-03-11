import { ComponentFixture, TestBed } from '@angular/core/testing';

import { SearchMatrixComponent } from './search-matrix.component';

describe('SearchMatrixComponent', () => {
  let component: SearchMatrixComponent;
  let fixture: ComponentFixture<SearchMatrixComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [ SearchMatrixComponent ]
    })
    .compileComponents();
  });

  beforeEach(() => {
    fixture = TestBed.createComponent(SearchMatrixComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});

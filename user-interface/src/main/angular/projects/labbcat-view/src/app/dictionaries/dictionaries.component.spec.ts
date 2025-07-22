import { ComponentFixture, TestBed } from '@angular/core/testing';

import { DictionariesComponent } from './dictionaries.component';

describe('DictionariesComponent', () => {
  let component: DictionariesComponent;
  let fixture: ComponentFixture<DictionariesComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [DictionariesComponent]
    })
    .compileComponents();
    
    fixture = TestBed.createComponent(DictionariesComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});

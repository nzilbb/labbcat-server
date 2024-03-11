import { ComponentFixture, TestBed } from '@angular/core/testing';

import { InputRegexpComponent } from './input-regexp.component';

describe('InputRegexpComponent', () => {
  let component: InputRegexpComponent;
  let fixture: ComponentFixture<InputRegexpComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [ InputRegexpComponent ]
    })
    .compileComponents();
  });

  beforeEach(() => {
    fixture = TestBed.createComponent(InputRegexpComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});

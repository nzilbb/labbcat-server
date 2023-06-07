import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { PraatComponent } from './praat.component';

describe('PraatComponent', () => {
  let component: PraatComponent;
  let fixture: ComponentFixture<PraatComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ PraatComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(PraatComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});

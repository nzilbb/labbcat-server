import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ValidLabelHelperComponent } from './valid-label-helper.component';

describe('ValidLabelHelperComponent', () => {
  let component: ValidLabelHelperComponent;
  let fixture: ComponentFixture<ValidLabelHelperComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [ ValidLabelHelperComponent ]
    })
    .compileComponents();
  });

  beforeEach(() => {
    fixture = TestBed.createComponent(ValidLabelHelperComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});

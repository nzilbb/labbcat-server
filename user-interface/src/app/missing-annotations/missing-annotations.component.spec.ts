import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { MissingAnnotationsComponent } from './missing-annotations.component';

describe('MissingAnnotationsComponent', () => {
  let component: MissingAnnotationsComponent;
  let fixture: ComponentFixture<MissingAnnotationsComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ MissingAnnotationsComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(MissingAnnotationsComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
